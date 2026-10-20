package com.senegas.kickoff.net;

/**
 * Transport-agnostic bidirectional byte pipe (Bluetooth SPP on Android,
 * TCP loopback on desktop). Packets are delivered framed and in order.
 */
public interface Link {
    boolean isOpen();

    /** Async best-effort send of one framed packet. */
    void send(byte[] packet);

    void setListener(LinkListener listener);

    void close();
}
