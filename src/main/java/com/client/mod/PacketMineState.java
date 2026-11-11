package com.client.mod;

/**
 * Pure-Java state machine for Packet Mine background mining and Instant Rebreak cycling.
 */
public final class PacketMineState {

    public enum Phase {
        IDLE,
        MINING,
        READY_REBREAK
    }

    public enum TickAction {
        NONE,
        SEND_STOP_AND_BREAK,
        CLEAR
    }

    private Phase phase = Phase.IDLE;
    private int targetX;
    private int targetY;
    private int targetZ;
    private int dirOrdinal;
    private float progress;
    private int cooldownTicks;

    public Phase getPhase() {
        return phase;
    }

    public int getTargetX() {
        return targetX;
    }

    public int getTargetY() {
        return targetY;
    }

    public int getTargetZ() {
        return targetZ;
    }

    public int getDirOrdinal() {
        return dirOrdinal;
    }

    public float getProgress() {
        return progress;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public boolean hasTarget() {
        return phase != Phase.IDLE;
    }

    public boolean isActiveAt(int x, int y, int z) {
        return phase != Phase.IDLE && targetX == x && targetY == y && targetZ == z;
    }

    public boolean isRebreakAt(int x, int y, int z) {
        return phase == Phase.READY_REBREAK && targetX == x && targetY == y && targetZ == z;
    }

    /**
     * Locks onto a mining target block after START_DESTROY_BLOCK was dispatched.
     */
    public void startTarget(int x, int y, int z, int dirOrdinal, float initialProgress) {
        this.phase = Phase.MINING;
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.dirOrdinal = dirOrdinal;
        this.progress = Math.max(0.0f, Math.min(1.0f, initialProgress));
        this.cooldownTicks = 0;
    }

    /**
     * Keeps progress in sync while the player actively holds vanilla continueDestroyBlock.
     */
    public void syncExternalProgress(float externalProgress) {
        if (phase == Phase.MINING) {
            this.progress = Math.max(this.progress, Math.min(1.0f, externalProgress));
        }
    }

    /**
     * Marks an immediate manual rebreak click at the locked READY_REBREAK position.
     */
    public void triggerManualRebreak(int dirOrdinal, int rebreakDelayTicks) {
        if (phase == Phase.READY_REBREAK) {
            this.dirOrdinal = dirOrdinal;
            this.cooldownTicks = clampDelay(rebreakDelayTicks);
        }
    }

    public TickAction stepTick(
            boolean enabled,
            boolean isAir,
            boolean unbreakable,
            boolean withinReach,
            float destroyProgressPerTick,
            boolean allowRebreak,
            int rebreakDelayTicks) {
        if (!enabled || !withinReach) {
            clear();
            return TickAction.CLEAR;
        }
        if (phase == Phase.IDLE) {
            return TickAction.NONE;
        }
        if (phase == Phase.MINING) {
            if (isAir) {
                if (allowRebreak) {
                    this.phase = Phase.READY_REBREAK;
                    this.progress = 1.0f;
                    this.cooldownTicks = clampDelay(rebreakDelayTicks);
                    return TickAction.NONE;
                }
                clear();
                return TickAction.CLEAR;
            }
            if (unbreakable) {
                clear();
                return TickAction.CLEAR;
            }
            if (destroyProgressPerTick > 0.0f) {
                this.progress = Math.min(1.0f, this.progress + destroyProgressPerTick);
            }
            if (this.progress >= 1.0f) {
                if (allowRebreak) {
                    this.phase = Phase.READY_REBREAK;
                    this.cooldownTicks = clampDelay(rebreakDelayTicks);
                } else {
                    clear();
                }
                return TickAction.SEND_STOP_AND_BREAK;
            }
            return TickAction.NONE;
        }

        // READY_REBREAK phase
        if (!allowRebreak) {
            clear();
            return TickAction.CLEAR;
        }
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return TickAction.NONE;
        }
        if (isAir) {
            return TickAction.NONE;
        }
        if (unbreakable) {
            clear();
            return TickAction.CLEAR;
        }
        if (cooldownTicks <= 0) {
            this.cooldownTicks = clampDelay(rebreakDelayTicks);
            return TickAction.SEND_STOP_AND_BREAK;
        }
        return TickAction.NONE;
    }

    public int getDestroyStage() {
        if (phase != Phase.MINING || progress <= 0.0f) {
            return -1;
        }
        return Math.max(0, Math.min(9, (int) (progress * 10.0f) - 1));
    }

    public void clear() {
        this.phase = Phase.IDLE;
        this.progress = 0.0f;
        this.cooldownTicks = 0;
    }

    private static int clampDelay(int delayTicks) {
        return Math.max(0, Math.min(5, delayTicks));
    }
}
