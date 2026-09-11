package cn.kynarain.legacy_tripwire;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

/**
 * Switches that control how faithfully the pre-1.21.2 write-back is reproduced.
 */
@Mod.EventBusSubscriber(modid = LegacyTripwire.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Config {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ALLOW_RESTORING_INTO_AIR = BUILDER
            .comment(
                    "false (default) = exact 1.20.6 rule. 1.20.6 only wrote the cached tripwire state back when the",
                    "  position still held something (it literally checked !isAir()). Flowing water counts, which is",
                    "  why the classic water string duper works; air does not, so a broken tripwire stays broken.",
                    "true = also write back into air. This is stronger than ANY vanilla version ever was: a sheared",
                    "  tripwire pops straight back instead of breaking, which can turn observers/redstone loops into",
                    "  an infinite string source. Only enable this if you know you want it.")
            .define("allowRestoringIntoAir", false);

    private static final ForgeConfigSpec.BooleanValue RESTORE_AIR_NEXT_TO_FLUID = BUILDER
            .comment(
                    "true (default) = also write back when the position is air but a fluid sits right next to it.",
                    "  Some machine layouts let the water leave plain air behind by the time the hook is notified;",
                    "  without this the duplication would silently stop there.",
                    "false = strictly the 1.20.6 rule (!isAir()), nothing more.")
            .define("restoreAirNextToFluid", true);

    private static final ForgeConfigSpec.BooleanValue PLAYER_BREAK_BREAKS_WIRE = BUILDER
            .comment(
                    "false (default) = a tripwire that a player breaks or shears is treated like any other removal, so the",
                    "  hook may write it straight back (as a disarmed wire) when water is involved.",
                    "  This is what makes sheared string-duper machines work: shearing is how the wire gets disarmed, and",
                    "  the hook putting it back is what keeps the machine supplied with a disarmed wire. That is also how",
                    "  1.20.6 behaved, so a sheared wire pops back instead of breaking.",
                    "true = a tripwire a player broke or sheared is NEVER written back, so shearing always breaks the wire",
                    "  for good. Tidier in the open, but any machine that relies on shearing its wire (most classic string",
                    "  dupers do) will stop working, because it loses its disarmed wire.",
                    "Can also be toggled in game with: /legacytripwire <true|false>")
            .define("playerBreakBreaksWire", false);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    /** Read by the mixin on every tripwire hook update. Stays at the config default until the config loads. */
    public static boolean allowRestoringIntoAir;

    /** @see #RESTORE_AIR_NEXT_TO_FLUID */
    public static boolean restoreAirNextToFluid = true;

    /** @see #PLAYER_BREAK_BREAKS_WIRE - also changeable with /legacytripwire */
    public static boolean playerBreakBreaksWire;

    private Config() {
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        allowRestoringIntoAir = ALLOW_RESTORING_INTO_AIR.get();
        restoreAirNextToFluid = RESTORE_AIR_NEXT_TO_FLUID.get();
        playerBreakBreaksWire = PLAYER_BREAK_BREAKS_WIRE.get();
    }

    /**
     * Runtime + persistent change of {@link #playerBreakBreaksWire}, used by {@code /legacytripwire}.
     * The in-memory value is always updated; persisting is best effort so a read-only config file cannot
     * break the command.
     */
    public static void setPlayerBreakBreaksWire(boolean value) {
        playerBreakBreaksWire = value;

        try {
            PLAYER_BREAK_BREAKS_WIRE.set(value);
            SPEC.save();
        } catch (Exception e) {
            LOGGER.warn("legacy_tripwire: could not persist playerBreakBreaksWire to the config file", e);
        }
    }
}
