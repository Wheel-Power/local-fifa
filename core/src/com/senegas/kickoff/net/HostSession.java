package com.senegas.kickoff.net;

/** Host side of a match: collects remote input, broadcasts snapshots. */
public class HostSession implements LinkListener {
    private final Link link;

    private volatile boolean hasRemoteInput;
    private volatile float remoteX, remoteY;
    private volatile boolean peerBye;

    public HostSession(Link link) {
        this.link = link;
        link.setListener(this);
        if (link instanceof StreamLink) {
            ((StreamLink) link).start();
        }
    }

    @Override
    public void onReceive(byte[] packet) {
        Protocol.dispatch(packet, this, null);
    }

    @Override
    public void onClosed() {
    }

    public void onRemoteInput(float x, float y) {
        this.remoteX = x;
        this.remoteY = y;
        this.hasRemoteInput = true;
    }

    public void onPeerBye() {
        this.peerBye = true;
    }

    public boolean hasRemoteInput() {
        return hasRemoteInput;
    }

    public float remoteX() {
        return remoteX;
    }

    public float remoteY() {
        return remoteY;
    }

    public boolean peerGone() {
        return peerBye || !link.isOpen();
    }

    public void sendSnapshot(Snapshot s) {
        link.send(Protocol.encodeSnapshot(s));
    }

    public void sendBye() {
        link.send(new byte[]{Protocol.TYPE_BYE});
    }

    public void close() {
        link.close();
    }
}
