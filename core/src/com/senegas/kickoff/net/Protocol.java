package com.senegas.kickoff.net;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Compact little-endian binary protocol.
 *
 * INPUT  (client -> host): [type:1][seq:2][destX:4][destY:4]
 * SNAP   (host -> client): [type:1][tick:4][stateId:1][scoreH:1][scoreA:1]
 *                           [timeLeft:2][kickoffAttack:1][ball xyz:12][ball v:12]
 *                           [home xy:80][away xy:80][restart xy:8]
 */
public final class Protocol {
    public static final byte TYPE_INPUT = 1;
    public static final byte TYPE_SNAP = 2;
    public static final byte TYPE_BYE = 3;

    private Protocol() {
    }

    public static byte[] encodeInput(int seq, float destX, float destY) {
        ByteBuffer b = ByteBuffer.allocate(11).order(ByteOrder.LITTLE_ENDIAN);
        b.put(TYPE_INPUT).putShort((short) seq).putFloat(destX).putFloat(destY);
        return b.array();
    }

    public static byte[] encodeSnapshot(Snapshot s) {
        ByteBuffer b = ByteBuffer.allocate(204).order(ByteOrder.LITTLE_ENDIAN);
        b.put(TYPE_SNAP).putInt(s.tick).put(s.stateId).put(s.scoreHome).put(s.scoreAway)
                .putShort(s.timeLeftSeconds).put(s.kickoffAttack)
                .putFloat(s.ballX).putFloat(s.ballY).putFloat(s.ballZ)
                .putFloat(s.ballVx).putFloat(s.ballVy).putFloat(s.ballVz);
        for (int i = 0; i < 20; i++) b.putFloat(s.homeXY[i]);
        for (int i = 0; i < 20; i++) b.putFloat(s.awayXY[i]);
        b.putFloat(s.restartX).putFloat(s.restartY);
        return b.array();
    }

    /** Dispatches a decoded packet to the matching session. */
    public static void dispatch(byte[] packet, HostSession host, ClientSession client) {
        if (packet == null || packet.length < 1) return;
        ByteBuffer b = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
        byte type = b.get();
        if (type == TYPE_INPUT && host != null && packet.length >= 11) {
            b.getShort(); // seq unused for now
            host.onRemoteInput(b.getFloat(), b.getFloat());
        } else if (type == TYPE_SNAP && client != null && packet.length >= 204) {
            Snapshot s = new Snapshot();
            s.tick = b.getInt();
            s.stateId = b.get();
            s.scoreHome = b.get();
            s.scoreAway = b.get();
            s.timeLeftSeconds = b.getShort();
            s.kickoffAttack = b.get();
            s.ballX = b.getFloat();
            s.ballY = b.getFloat();
            s.ballZ = b.getFloat();
            s.ballVx = b.getFloat();
            s.ballVy = b.getFloat();
            s.ballVz = b.getFloat();
            for (int i = 0; i < 20; i++) s.homeXY[i] = b.getFloat();
            for (int i = 0; i < 20; i++) s.awayXY[i] = b.getFloat();
            s.restartX = b.getFloat();
            s.restartY = b.getFloat();
            client.onSnapshot(s);
        } else if (type == TYPE_BYE) {
            if (host != null) host.onPeerBye();
            if (client != null) client.onPeerBye();
        }
    }
}
