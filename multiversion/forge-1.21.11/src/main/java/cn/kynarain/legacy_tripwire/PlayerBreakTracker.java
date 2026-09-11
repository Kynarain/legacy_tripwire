package cn.kynarain.legacy_tripwire;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Records tripwire blocks a player just broke (with or without shears), so the tripwire hook refuses to
 * write them back.
 *
 * <p>This is the one case the world cannot tell us about on its own: a wire that a player sheared or broke
 * leaves exactly the same air behind as a wire that flowing water removed in a machine, and in a machine
 * there is obviously water right next to the wire. The player path is precise, though - every player break
 * runs through {@code TripWireBlock.playerWillDestroy} before the block is removed - so marking it there
 * lets the hook keep its pre-1.21.2 write-back for water while still letting a sheared or broken wire break
 * for good.
 *
 * <p>Everything happens on the level's tick thread, so plain static fields are enough. The marker is
 * position-scoped and expires after a few ticks so a cancelled break can never affect anything later.
 */
public final class PlayerBreakTracker {

    /** The removal follows playerWillDestroy immediately, but keep a small window for safety. */
    private static final int VALID_TICKS = 10;

    private static Level brokenLevel;
    private static BlockPos brokenPos;
    private static long expiresAt;

    private PlayerBreakTracker() {
    }

    public static void notePlayerBreak(Level level, BlockPos pos) {
        brokenLevel = level;
        brokenPos = pos.immutable();
        expiresAt = level.getGameTime() + VALID_TICKS;
    }

    public static boolean wasBrokenByPlayer(Level level, BlockPos pos) {
        if (brokenPos == null || level != brokenLevel || pos == null || !pos.equals(brokenPos)) {
            return false;
        }

        if (level.getGameTime() > expiresAt) {
            brokenLevel = null;
            brokenPos = null;
            return false;
        }

        return true;
    }
}
