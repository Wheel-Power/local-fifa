package com.senegas.kickoff.utils;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

import static com.senegas.kickoff.pitches.FootballDimensionConstants.OUTER_BOTTOM_EDGE_X;
import static com.senegas.kickoff.pitches.FootballDimensionConstants.PITCH_HEIGHT_IN_PX;
import static com.senegas.kickoff.pitches.FootballDimensionConstants.PITCH_WIDTH_IN_PX;

/**
 * Pitch geometry rules in world (global map) coordinates, pure math with no
 * engine dependencies so it can be unit-tested headless.
 *
 * World mapping (see PitchUtils): pitch(0,0) -> global(176, 80).
 * The NORTH team (home) defends the top goal.
 */
public final class RuleBook {
    public static final float FIELD_LEFT_X = OUTER_BOTTOM_EDGE_X;                       // 176
    public static final float FIELD_RIGHT_X = (float) (FIELD_LEFT_X + PITCH_WIDTH_IN_PX);   // ~1090
    public static final float FIELD_TOP_Y = 80f;
    public static final float FIELD_BOTTOM_Y = (float) (FIELD_TOP_Y + PITCH_HEIGHT_IN_PX);  // ~1472

    /** Goal posts in world x (pitch-space 404..508 shifted by 176). */
    public static final float GOAL_LEFT_X = 404f + OUTER_BOTTOM_EDGE_X;
    public static final float GOAL_RIGHT_X = 508f + OUTER_BOTTOM_EDGE_X;
    /** Crossbar height 2.44 m in world z pixels. */
    public static final float CROSSBAR_Z = 32f;

    public static final float CENTER_X = (FIELD_LEFT_X + FIELD_RIGHT_X) / 2f;
    public static final float CENTER_Y = (FIELD_TOP_Y + FIELD_BOTTOM_Y) / 2f;

    private static final float RESTART_MARGIN = 14f;

    private RuleBook() {
    }

    /**
     * @param last ball position last frame
     * @param cur  ball position this frame
     * @return 0 no goal, 1 ball crossed the TOP goal line (away scores),
     *         2 ball crossed the BOTTOM goal line (home scores)
     */
    public static int goalScored(Vector3 last, Vector3 cur) {
        if (last.y >= FIELD_TOP_Y && cur.y < FIELD_TOP_Y) {
            float t = (FIELD_TOP_Y - last.y) / (cur.y - last.y);
            float ix = last.x + t * (cur.x - last.x);
            float iz = last.z + t * (cur.z - last.z);
            if (ix > GOAL_LEFT_X && ix < GOAL_RIGHT_X && iz < CROSSBAR_Z) return 1;
        }
        if (last.y <= FIELD_BOTTOM_Y && cur.y > FIELD_BOTTOM_Y) {
            float t = (FIELD_BOTTOM_Y - last.y) / (cur.y - last.y);
            float ix = last.x + t * (cur.x - last.x);
            float iz = last.z + t * (cur.z - last.z);
            if (ix > GOAL_LEFT_X && ix < GOAL_RIGHT_X && iz < CROSSBAR_Z) return 2;
        }
        return 0;
    }

    /**
     * Out of play over any boundary (side or end line). The intersection
     * point (clamped back inside the field) is written to {@code out} so the
     * restart can be taken from there.
     */
    public static boolean outOfPlay(Vector3 last, Vector3 cur, Vector2 out) {
        // already stuck behind a line without a crossing this frame
        if ((cur.y < FIELD_TOP_Y - 2f && last.y < FIELD_TOP_Y - 2f)
                || (cur.y > FIELD_BOTTOM_Y + 2f && last.y > FIELD_BOTTOM_Y + 2f)
                || (cur.x < FIELD_LEFT_X - 2f && last.x < FIELD_LEFT_X - 2f)
                || (cur.x > FIELD_RIGHT_X + 2f && last.x > FIELD_RIGHT_X + 2f)) {
            out.set(clampX(cur.x), clampY(cur.y));
            return true;
        }

        // left / right side lines
        if (last.x >= FIELD_LEFT_X && cur.x < FIELD_LEFT_X) {
            float t = (FIELD_LEFT_X - last.x) / (cur.x - last.x);
            float iy = last.y + t * (cur.y - last.y);
            out.set(FIELD_LEFT_X + RESTART_MARGIN, clampY(iy));
            return true;
        }
        if (last.x <= FIELD_RIGHT_X && cur.x > FIELD_RIGHT_X) {
            float t = (FIELD_RIGHT_X - last.x) / (cur.x - last.x);
            float iy = last.y + t * (cur.y - last.y);
            out.set(FIELD_RIGHT_X - RESTART_MARGIN, clampY(iy));
            return true;
        }

        // end lines (goal already checked before calling this)
        if (last.y >= FIELD_TOP_Y && cur.y < FIELD_TOP_Y) {
            float t = (FIELD_TOP_Y - last.y) / (cur.y - last.y);
            float ix = last.x + t * (cur.x - last.x);
            out.set(clampX(ix), FIELD_TOP_Y + RESTART_MARGIN);
            return true;
        }
        if (last.y <= FIELD_BOTTOM_Y && cur.y > FIELD_BOTTOM_Y) {
            float t = (FIELD_BOTTOM_Y - last.y) / (cur.y - last.y);
            float ix = last.x + t * (cur.x - last.x);
            out.set(clampX(ix), FIELD_BOTTOM_Y - RESTART_MARGIN);
            return true;
        }
        return false;
    }

    /** Restarts kick the ball gently towards the pitch centre. */
    public static Vector3 restartKickVelocity(Vector2 restartPoint) {
        Vector3 v = new Vector3(CENTER_X - restartPoint.x, CENTER_Y - restartPoint.y, 0);
        if (v.len() < 1f) {
            v.set(0, 1, 0);
        }
        return v.nor().scl(170f).add(0, 0, 60f);
    }

    private static float clampX(float x) {
        return Math.max(FIELD_LEFT_X + RESTART_MARGIN, Math.min(FIELD_RIGHT_X - RESTART_MARGIN, x));
    }

    private static float clampY(float y) {
        return Math.max(FIELD_TOP_Y + RESTART_MARGIN, Math.min(FIELD_BOTTOM_Y - RESTART_MARGIN, y));
    }
}
