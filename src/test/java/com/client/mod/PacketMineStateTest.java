package com.client.mod;

/**
 * Pure-Java verification of PacketMineState background breaking, Instant Rebreak cycling,
 * target switching, and safety cleanup transitions.
 */
public class PacketMineStateTest {

    public static void main(String[] args) {
        testBackgroundMiningAndInstantRebreak();
        testSingleShotWithoutRebreak();
        testVanillaBreakTransitionsIntoReadyRebreak();
        testSafetyClearsOnDisableReachAndBedrock();
        System.out.println("ALL PacketMineStateTest ASSERTIONS PASSED!");
    }

    private static void testBackgroundMiningAndInstantRebreak() {
        PacketMineState state = new PacketMineState();
        state.startTarget(10, 64, -5, 1, 0.25f);
        assert state.getPhase() == PacketMineState.Phase.MINING;
        assert state.isActiveAt(10, 64, -5);
        assert !state.isRebreakAt(10, 64, -5);

        // Two background ticks at 0.25 each -> 0.75 total
        assert state.stepTick(true, false, false, true, 0.25f, true, 1) == PacketMineState.TickAction.NONE;
        assert state.stepTick(true, false, false, true, 0.25f, true, 1) == PacketMineState.TickAction.NONE;
        assert state.getDestroyStage() == 6;

        // Fourth step hits 1.0f -> sends STOP_DESTROY_BLOCK and locks READY_REBREAK with 1t cooldown
        assert state.stepTick(true, false, false, true, 0.25f, true, 1) == PacketMineState.TickAction.SEND_STOP_AND_BREAK;
        assert state.getPhase() == PacketMineState.Phase.READY_REBREAK;
        assert state.isRebreakAt(10, 64, -5);

        // Air tick decrements cooldown from 1 -> 0 without sending extra STOP packets
        assert state.stepTick(true, true, false, true, 0.0f, true, 1) == PacketMineState.TickAction.NONE;

        // New block placed at same coords -> instant STOP_DESTROY_BLOCK after 1t cooldown expired
        // This firing passes delay=2 for the NEXT cycle
        assert state.stepTick(true, false, false, true, 0.0f, true, 2) == PacketMineState.TickAction.SEND_STOP_AND_BREAK;
        assert state.getPhase() == PacketMineState.Phase.READY_REBREAK;

        // Delay=2 skips exactly 2 ticks before firing again on the 3rd tick
        assert state.stepTick(true, false, false, true, 0.0f, true, 2) == PacketMineState.TickAction.NONE;
        assert state.stepTick(true, false, false, true, 0.0f, true, 2) == PacketMineState.TickAction.NONE;
        assert state.stepTick(true, false, false, true, 0.0f, true, 2) == PacketMineState.TickAction.SEND_STOP_AND_BREAK;
    }

    private static void testSingleShotWithoutRebreak() {
        PacketMineState state = new PacketMineState();
        state.startTarget(1, 2, 3, 0, 0.6f);
        assert state.stepTick(true, false, false, true, 0.4f, false, 1) == PacketMineState.TickAction.SEND_STOP_AND_BREAK;
        assert state.getPhase() == PacketMineState.Phase.IDLE;
        assert !state.hasTarget();
    }

    private static void testVanillaBreakTransitionsIntoReadyRebreak() {
        PacketMineState state = new PacketMineState();
        state.startTarget(5, 70, 5, 2, 0.0f);
        state.syncExternalProgress(0.8f);
        assert Math.abs(state.getProgress() - 0.8f) < 0.0001f;

        // Block became air while holding vanilla break
        assert state.stepTick(true, true, false, true, 0.0f, true, 0) == PacketMineState.TickAction.NONE;
        assert state.getPhase() == PacketMineState.Phase.READY_REBREAK;
        assert state.isRebreakAt(5, 70, 5);

        // Next block placed at (5,70,5) breaks with 0t delay
        assert state.stepTick(true, false, false, true, 0.0f, true, 0) == PacketMineState.TickAction.SEND_STOP_AND_BREAK;
    }

    private static void testSafetyClearsOnDisableReachAndBedrock() {
        PacketMineState state = new PacketMineState();
        state.startTarget(0, 64, 0, 1, 0.5f);
        assert state.stepTick(true, false, true, true, 0.1f, true, 1) == PacketMineState.TickAction.CLEAR;
        assert !state.hasTarget();

        state.startTarget(0, 64, 0, 1, 0.5f);
        assert state.stepTick(true, false, false, false, 0.1f, true, 1) == PacketMineState.TickAction.CLEAR;
        assert !state.hasTarget();

        state.startTarget(0, 64, 0, 1, 0.5f);
        assert state.stepTick(false, false, false, true, 0.1f, true, 1) == PacketMineState.TickAction.CLEAR;
        assert !state.hasTarget();
    }
}
