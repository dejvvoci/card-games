package com.pesekatesh.voice;

/**
 * Mesazh gjenerik "signaling" për WebRTC (ofertë SDP / përgjigje / ICE candidate).
 * Përmbajtja (payload) mbetet e papërpunuar (Object) — backend-i thjesht e rele-on te
 * lojtari cak, pa e kuptuar apo validuar strukturën e saj (kjo është pune e browser-it).
 */
public class VoiceSignalMessage {
    private String fromPlayerId;
    private String targetPlayerId;
    private String type; // "offer" | "answer" | "ice-candidate"
    private Object payload;

    public String getFromPlayerId() { return fromPlayerId; }
    public void setFromPlayerId(String fromPlayerId) { this.fromPlayerId = fromPlayerId; }
    public String getTargetPlayerId() { return targetPlayerId; }
    public void setTargetPlayerId(String targetPlayerId) { this.targetPlayerId = targetPlayerId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }
}
