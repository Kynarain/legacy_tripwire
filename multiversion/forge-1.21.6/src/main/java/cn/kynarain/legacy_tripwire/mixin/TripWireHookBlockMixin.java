package cn.kynarain.legacy_tripwire.mixin;

import com.google.common.base.MoreObjects;
import cn.kynarain.legacy_tripwire.Config;
import cn.kynarain.legacy_tripwire.PlayerBreakTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import java.util.Optional;

/**
 * Reverts MC-129055 / MC-59471 (fixed in 24w33a, shipped in Minecraft 1.21.2) inside
 * {@code TripWireHookBlock.calculateState}.
 *
 * <p>The body below is a copy of the vanilla 1.21.11 implementation with readable parameter names and
 * exactly one behavioural change, marked with a comment in the final loop. Everything else - the state
 * machine, the sounds, the neighbour updates, the scheduled re-check - is intentionally identical, so
 * that this mod changes nothing about tripwire except the duplication exploit Mojang removed.
 *
 * <p>{@code @Overwrite} is used instead of a targeted injection on purpose: the vanilla guard sits inside
 * a loop whose instruction order is not stable across mappings/patches, whereas replacing the whole method
 * cannot land on the wrong instruction.
 */
@Mixin(TripWireHookBlock.class)
public abstract class TripWireHookBlockMixin {

    /**
     * The 1.20.6 algorithm, with the pre-1.21.2 write-back restored.
     *
     * @author Kynarain
     * @reason Restore MC-59471 (the pre-1.21.2 tripwire write-back) so classic string dupers work again.
     */
    @Overwrite(remap = false)
    public static void calculateState(Level level, BlockPos hookPos, BlockState hookState, boolean beingRemoved,
                                      boolean updateNeighbours, int brokenWireDistance, BlockState staleState) {
        Optional<Direction> optionalFacing = hookState.getOptionalValue(TripWireHookBlock.FACING);
        if (optionalFacing.isEmpty()) {
            return;
        }

        Direction direction = optionalFacing.get();
        boolean wasAttached = hookState.getOptionalValue(TripWireHookBlock.ATTACHED).orElse(false);
        boolean wasPowered = hookState.getOptionalValue(TripWireHookBlock.POWERED).orElse(false);
        Block hook = hookState.getBlock();
        boolean stayAttached = !beingRemoved;
        boolean stayPowered = false;
        int otherHookDistance = 0;
        // TripWireHookBlock.WIRE_DIST_MAX is protected, so the bounds are repeated here.
        BlockState[] cachedWires = new BlockState[42];

        for (int distance = 1; distance < 42; distance++) {
            BlockPos wirePos = hookPos.relative(direction, distance);
            BlockState wireState = level.getBlockState(wirePos);
            if (wireState.is(Blocks.TRIPWIRE_HOOK)) {
                if (wireState.getValue(TripWireHookBlock.FACING) == direction.getOpposite()) {
                    otherHookDistance = distance;
                }
                break;
            }

            if (!wireState.is(Blocks.TRIPWIRE) && distance != brokenWireDistance) {
                cachedWires[distance] = null;
                stayAttached = false;
            } else {
                if (distance == brokenWireDistance) {
                    // Use the state the tripwire had before it was removed, not what replaced it.
                    wireState = MoreObjects.firstNonNull(staleState, wireState);
                }

                boolean armed = !wireState.getValue(TripWireBlock.DISARMED);
                boolean powered = wireState.getValue(TripWireBlock.POWERED);
                stayPowered |= armed && powered;
                cachedWires[distance] = wireState;
                if (distance == brokenWireDistance) {
                    level.scheduleTick(hookPos, hook, 10);
                    stayAttached &= armed;
                }
            }
        }

        stayAttached &= otherHookDistance > 1;
        stayPowered &= stayAttached;
        BlockState newHookState = hook.defaultBlockState()
                .trySetValue(TripWireHookBlock.ATTACHED, stayAttached)
                .trySetValue(TripWireHookBlock.POWERED, stayPowered);

        if (otherHookDistance > 0) {
            BlockPos otherHookPos = hookPos.relative(direction, otherHookDistance);
            Direction otherHookFacing = direction.getOpposite();
            level.setBlock(otherHookPos, newHookState.setValue(TripWireHookBlock.FACING, otherHookFacing), 3);
            legacyNotifyNeighbours(hook, level, otherHookPos, otherHookFacing);
            legacyEmitState(level, otherHookPos, stayAttached, stayPowered, wasAttached, wasPowered);
        }

        legacyEmitState(level, hookPos, stayAttached, stayPowered, wasAttached, wasPowered);
        if (!beingRemoved) {
            level.setBlock(hookPos, newHookState.setValue(TripWireHookBlock.FACING, direction), 3);
            if (updateNeighbours) {
                legacyNotifyNeighbours(hook, level, hookPos, direction);
            }
        }

        if (wasAttached != stayAttached) {
            for (int distance = 1; distance < otherHookDistance; distance++) {
                BlockPos wirePos = hookPos.relative(direction, distance);
                BlockState cachedWire = cachedWires[distance];
                if (cachedWire == null) {
                    continue;
                }

                BlockState currentState = level.getBlockState(wirePos);

                // =====================================================================================
                // THE ONLY DEVIATION FROM VANILLA 1.21.11.
                //
                // Vanilla 1.21.11 (since 24w36a) reads:
                //     if (currentState.is(Blocks.TRIPWIRE) || currentState.is(Blocks.TRIPWIRE_HOOK))
                //
                // Vanilla 1.20.6 read (verified against the decompiled 1.20.6 class):
                //     if (blockstate2 != null && !level.getBlockState(blockpos2).isAir())
                //
                // So 1.20.6 only required the position to still hold *something*. Two consequences:
                //   * water counts as something, so the hook keeps re-placing the string that flowing water
                //     just washed out - that is MC-59471, the basis of the classic water string duper.
                //   * a position that became AIR is not written back, so a sheared, broken or pushed
                //     tripwire really does break - exactly like 1.20.6 and like 1.21.11 vanilla.
                //
                // Two extra guards, neither of which vanilla 1.21.11 has, both controlled by config:
                //   1. A tripwire the player just broke or sheared can never be written back
                //      (Config.playerBreakBreaksWire). Off by default, because shearing is exactly how a
                //      classic machine gets its disarmed wire: the hook writing it back is what keeps the
                //      machine running. Turn it on if you would rather have shearing always break the wire.
                //   2. Some layouts (water running north-south) let the fluid leave plain air behind by the
                //      time the hook is notified, which would silently stop the duplication. In that case a
                //      fluid sitting right next to the position still counts as a wash
                //      (Config.restoreAirNextToFluid).
                // =====================================================================================
                boolean playerBroke = Config.playerBreakBreaksWire && PlayerBreakTracker.wasBrokenByPlayer(level, wirePos);
                boolean waterLeftAirBehind = !currentState.isAir() || Config.allowRestoringIntoAir
                        || (Config.restoreAirNextToFluid && isNextToFluid(level, wirePos));

                if (!playerBroke && waterLeftAirBehind) {
                    level.setBlock(wirePos, cachedWire.trySetValue(TripWireHookBlock.ATTACHED, stayAttached), 3);
                }
            }
        }
    }

    /** Copy of {@code TripWireHookBlock.emitState}, which is private and therefore not reachable. */
    @Unique
    private static void legacyEmitState(Level level, BlockPos pos, boolean attached, boolean powered,
                                        boolean wasAttached, boolean wasPowered) {
        if (powered && !wasPowered) {
            level.playSound(null, pos, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 0.4F, 0.6F);
            level.gameEvent(null, GameEvent.BLOCK_ACTIVATE, pos);
        } else if (!powered && wasPowered) {
            level.playSound(null, pos, SoundEvents.TRIPWIRE_CLICK_OFF, SoundSource.BLOCKS, 0.4F, 0.5F);
            level.gameEvent(null, GameEvent.BLOCK_DEACTIVATE, pos);
        } else if (attached && !wasAttached) {
            level.playSound(null, pos, SoundEvents.TRIPWIRE_ATTACH, SoundSource.BLOCKS, 0.4F, 0.7F);
            level.gameEvent(null, GameEvent.BLOCK_ATTACH, pos);
        } else if (!attached && wasAttached) {
            level.playSound(null, pos, SoundEvents.TRIPWIRE_DETACH, SoundSource.BLOCKS, 0.4F,
                    1.2F / (level.random.nextFloat() * 0.2F + 0.9F));
            level.gameEvent(null, GameEvent.BLOCK_DETACH, pos);
        }
    }

    /** Copy of {@code TripWireHookBlock.notifyNeighbors}, which is private and therefore not reachable. */
    @Unique
    private static void legacyNotifyNeighbours(Block hook, Level level, BlockPos pos, Direction direction) {
        Direction opposite = direction.getOpposite();
        Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, opposite, Direction.UP);
        level.updateNeighborsAt(pos, hook, orientation);
        level.updateNeighborsAt(pos.relative(opposite), hook, orientation);
    }

    /** True when any of the six neighbours of {@code pos} still holds a fluid. */
    @Unique
    private static boolean isNextToFluid(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!level.getFluidState(pos.relative(direction)).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
