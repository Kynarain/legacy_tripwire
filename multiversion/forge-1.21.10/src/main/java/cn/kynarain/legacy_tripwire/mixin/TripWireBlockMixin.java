package cn.kynarain.legacy_tripwire.mixin;

import cn.kynarain.legacy_tripwire.PlayerBreakTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks tripwire blocks that a player is breaking, so {@code TripWireHookBlock.calculateState} can tell a
 * player's break apart from a water wash.
 *
 * <p>Vanilla calls {@code playerWillDestroy} for every player break attempt on the block, before the block
 * is actually removed, which is exactly the information the hook is missing: the removal itself looks the
 * same whether the player sheared the wire or flowing water washed it away.
 */
@Mixin(TripWireBlock.class)
public abstract class TripWireBlockMixin {

    @Inject(method = "playerWillDestroy", at = @At("HEAD"), remap = false)
    private void legacyTripwire$notePlayerBreak(Level level, BlockPos pos, BlockState state, Player player,
                                                CallbackInfoReturnable<BlockState> cir) {
        if (state.is(Blocks.TRIPWIRE)) {
            PlayerBreakTracker.notePlayerBreak(level, pos);
        }
    }
}
