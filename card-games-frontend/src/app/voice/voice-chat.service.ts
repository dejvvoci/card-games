import { Injectable, signal } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { environment } from '../../environments/environment';

export type PeerAudioState = 'connecting' | 'connected' | 'disconnected';

interface PeerEntry {
  pc: RTCPeerConnection;
  audioEl: HTMLAudioElement;
  pendingCandidates: RTCIceCandidateInit[];
}

interface VoiceSignal {
  fromPlayerId: string;
  targetPlayerId: string;
  type: 'offer' | 'answer' | 'ice-candidate';
  payload: any;
}

const ICE_SERVERS: RTCIceServer[] = [{ urls: 'stun:stun.l.google.com:19302' }];

/**
 * Audio zë-mbi-zë (WebRTC, peer-to-peer, mesh) mes lojtarëve realë brenda një dhome.
 * Signaling-u (shkëmbimi i ofertave SDP/ICE) kalon nëpër WebSocket-in ekzistues të lojës
 * (shih VoiceSignalingController në backend) — backend-i s'e prek fare vetë zërin.
 * E përbashkët mes Pesëkatësh dhe Peseqindsh, e pavarur nga loja.
 */
@Injectable({ providedIn: 'root' })
export class VoiceChatService {

  readonly peerStates = signal<Record<string, PeerAudioState>>({});
  readonly muted = signal(false);
  readonly active = signal(false);

  private client: Client | null = null;
  private myPlayerId: string | null = null;
  private localStream: MediaStream | null = null;
  private peers = new Map<string, PeerEntry>();

  /** Kërkon mikrofonin dhe hap kanalin e signaling-ut. Duhet thirrur nga një veprim i qartë i përdoruesit. */
  async join(playerId: string): Promise<void> {
    if (this.active()) return;
    this.myPlayerId = playerId;
    this.localStream = await navigator.mediaDevices.getUserMedia({ audio: true });

    await new Promise<void>((resolve, reject) => {
      this.client = new Client({
        brokerURL: `${environment.wsEndpoint}?playerId=${encodeURIComponent(playerId)}`,
        reconnectDelay: 3000,
        onConnect: () => {
          this.client!.subscribe('/user/queue/voice-signal', (msg: IMessage) => {
            this.handleSignal(JSON.parse(msg.body));
          });
          resolve();
        },
        onStompError: (frame) => reject(frame),
      });
      this.client.activate();
    });
    this.active.set(true);
  }

  /** Thirret sa herë ndryshon lista e lojtarëve realë (jo BOT, jo vetja) në dhomë */
  syncPeers(peerIds: string[]): void {
    if (!this.active() || !this.myPlayerId) return;
    const wanted = new Set(peerIds.filter((id) => id !== this.myPlayerId));

    for (const id of [...this.peers.keys()]) {
      if (!wanted.has(id)) this.closePeer(id);
    }

    for (const id of wanted) {
      if (this.peers.has(id)) continue;
      // Rregull deterministik: vetëm ID më e vogël (leksikografikisht) e nis ofertën,
      // kështu shmanget "glare" (të dy anët të nisin ofertë njëkohësisht).
      if (this.myPlayerId! < id) {
        this.callPeer(id);
      }
    }
  }

  toggleMute(): void {
    if (!this.localStream) return;
    const next = !this.muted();
    this.localStream.getAudioTracks().forEach((t) => (t.enabled = !next));
    this.muted.set(next);
  }

  getPeerState(peerId: string): PeerAudioState | undefined {
    return this.peerStates()[peerId];
  }

  leave(): void {
    for (const id of [...this.peers.keys()]) this.closePeer(id);
    this.localStream?.getTracks().forEach((t) => t.stop());
    this.localStream = null;
    this.client?.deactivate();
    this.client = null;
    this.myPlayerId = null;
    this.active.set(false);
    this.muted.set(false);
  }

  // ============================================================
  //  WEBRTC PEER-TO-PEER
  // ============================================================

  private ensurePeerConnection(peerId: string): RTCPeerConnection {
    const existing = this.peers.get(peerId);
    if (existing) return existing.pc;

    const pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });
    const audioEl = document.createElement('audio');
    audioEl.autoplay = true;
    document.body.appendChild(audioEl);

    this.localStream?.getTracks().forEach((track) => pc.addTrack(track, this.localStream!));

    pc.onicecandidate = (event) => {
      if (event.candidate) this.sendSignal(peerId, 'ice-candidate', event.candidate.toJSON());
    };
    pc.ontrack = (event) => { audioEl.srcObject = event.streams[0]; };
    pc.onconnectionstatechange = () => {
      const state: PeerAudioState = pc.connectionState === 'connected' ? 'connected'
        : (pc.connectionState === 'failed' || pc.connectionState === 'closed') ? 'disconnected' : 'connecting';
      this.setPeerState(peerId, state);
    };

    this.peers.set(peerId, { pc, audioEl, pendingCandidates: [] });
    this.setPeerState(peerId, 'connecting');
    return pc;
  }

  private async callPeer(peerId: string): Promise<void> {
    const pc = this.ensurePeerConnection(peerId);
    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    this.sendSignal(peerId, 'offer', offer);
  }

  private async handleSignal(msg: VoiceSignal): Promise<void> {
    const peerId = msg.fromPlayerId;
    const pc = this.ensurePeerConnection(peerId);

    if (msg.type === 'offer') {
      await pc.setRemoteDescription(new RTCSessionDescription(msg.payload));
      await this.flushPendingCandidates(peerId);
      const answer = await pc.createAnswer();
      await pc.setLocalDescription(answer);
      this.sendSignal(peerId, 'answer', answer);
    } else if (msg.type === 'answer') {
      await pc.setRemoteDescription(new RTCSessionDescription(msg.payload));
      await this.flushPendingCandidates(peerId);
    } else if (msg.type === 'ice-candidate') {
      if (pc.remoteDescription) {
        try { await pc.addIceCandidate(new RTCIceCandidate(msg.payload)); } catch { /* injorohet */ }
      } else {
        this.peers.get(peerId)?.pendingCandidates.push(msg.payload);
      }
    }
  }

  private async flushPendingCandidates(peerId: string): Promise<void> {
    const entry = this.peers.get(peerId);
    if (!entry) return;
    for (const candidate of entry.pendingCandidates) {
      try { await entry.pc.addIceCandidate(new RTCIceCandidate(candidate)); } catch { /* injorohet */ }
    }
    entry.pendingCandidates = [];
  }

  private sendSignal(targetPlayerId: string, type: VoiceSignal['type'], payload: any): void {
    if (!this.client || !this.myPlayerId) return;
    this.client.publish({
      destination: '/app/voice/signal',
      body: JSON.stringify({ fromPlayerId: this.myPlayerId, targetPlayerId, type, payload }),
    });
  }

  private closePeer(peerId: string): void {
    const entry = this.peers.get(peerId);
    if (!entry) return;
    entry.pc.close();
    entry.audioEl?.remove();
    this.peers.delete(peerId);
    this.peerStates.update((s) => {
      const copy = { ...s };
      delete copy[peerId];
      return copy;
    });
  }

  private setPeerState(peerId: string, state: PeerAudioState): void {
    this.peerStates.update((s) => ({ ...s, [peerId]: state }));
  }
}
