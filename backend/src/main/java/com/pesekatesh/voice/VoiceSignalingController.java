package com.pesekatesh.voice;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * Rele "signaling" për audio WebRTC mes lojtarëve — thjesht dërgon çdo mesazh (ofertë/përgjigje/ICE)
 * te lojtari cak, i pavarur nga loja (Pesëkatësh apo Peseqindsh); lidhja e vërtetë audio bëhet
 * direkt browser-me-browser (peer-to-peer), backend-i s'e prek fare zërin.
 */
@Controller
public class VoiceSignalingController {

    private final SimpMessagingTemplate messagingTemplate;

    public VoiceSignalingController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/voice/signal")
    public void relaySignal(@Payload VoiceSignalMessage msg) {
        messagingTemplate.convertAndSendToUser(msg.getTargetPlayerId(), "/queue/voice-signal", msg);
    }
}
