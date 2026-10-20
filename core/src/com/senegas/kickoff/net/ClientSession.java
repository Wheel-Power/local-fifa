package com.senegas.kickoff.net;

/** Client side of a match: ships local input upstream, keeps latest snapshot. */
public class ClientSession implements LinkListener {
    private final Link link;

    private volatile Snapshot current;
    private volatile Snapshot previous;
    private volatile long lastSnapshotAt;
    private volatile boolean peerBye;

    public ClientSession(Link link) {
        this.link = link;
        link.setListener(this);
        if (link instanceof StreamLink) {
            ((StreamLink) link).start();
        }
    }

    @Override
    public void onReceive(byte[] packet) {
        Protocol.dispatch(packet, null, this);
    }

    @Override
    public void onClosed() {
    }

    public void onSnapshot(Snapshot s) {
        this.previous = this.current;
        this.current = s;
        this.lastSnapshotAt = System.nanoTime();
    }

    public void onPeerBye() {
        this.peerBye = true;
    }

    public Snapshot latest() {
        return current;
    }

    public Snapshot previous() {
        return previous;
    }

    public long sinceLastSnapshotMs() {
        return (System.nanoTime() - lastSnapshotAt) / 1_000_000L;
    }

    public boolean peerGone() {
        return peerBye || !link.isOpen();
    }

    /** True when no snapshot arrived for a suspiciously long time. */
    public boolean stalled() {
        return current != null && sinceLastSnapshotMs() > 8000L;
    }

    public void sendInput(int seq, float destX, float destY) {
        link.send(Protocol.encodeInput(seq, destX, destY));
    }

    public void sendBye() {
        link.send(new byte[]{Protocol.TYPE_BYE});
    }

    public void close() {
        link.close();
    }
}
