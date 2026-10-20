package com.senegas.kickoff.net;

public interface LinkListener {
    void onReceive(byte[] packet);

    void onClosed();
}
