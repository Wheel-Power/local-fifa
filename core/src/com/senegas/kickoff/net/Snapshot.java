package com.senegas.kickoff.net;

/**
 * Authoritative world state broadcast by the host at ~30Hz.
 * Player positions are flat [x0,y0,x1,y1,...] in world pixels.
 */
public class Snapshot {
    public int tick;
    public byte stateId;
    public byte scoreHome;
    public byte scoreAway;
    public short timeLeftSeconds;
    /** 0 = home kicks off next, 1 = away. */
    public byte kickoffAttack;
    public float ballX, ballY, ballZ;
    public float ballVx, ballVy, ballVz;
    public final float[] homeXY = new float[20];
    public final float[] awayXY = new float[20];
    public float restartX, restartY;
}
