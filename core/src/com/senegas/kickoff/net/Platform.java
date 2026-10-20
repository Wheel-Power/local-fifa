package com.senegas.kickoff.net;

import java.util.List;

/**
 * Platform bridge: the core game asks the platform to provide links.
 * Android implements this over Bluetooth SPP; desktop over TCP loopback
 * so the same multiplayer code can be tested without a phone.
 */
public interface Platform {
    class Peer {
        public final String id;
        public final String name;

        public Peer(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    interface Callback<T> {
        void onReady(T result);

        void onError(Exception e);
    }

    /** Start hosting and call back with a link once a peer connects. */
    void host(Callback<Link> callback);

    /** Connect to a peer obtained from {@link #peers()}. */
    void connect(Peer peer, Callback<Link> callback);

    /** Cancel a pending host/connect attempt. */
    void cancel();

    /** List connectable peers (bonded Bluetooth devices / loopback). */
    List<Peer> peers();
}
