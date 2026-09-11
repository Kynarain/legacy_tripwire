package cn.kynarain.legacy_tripwire;

import com.mojang.logging.LogUtils;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Restores the pre-1.21.2 tripwire behaviour so classic string duper machines work again.
 *
 * <p>Minecraft 1.21.2 (snapshot 24w33a) fixed MC-129055 / MC-59471 by making {@code TripWireHookBlock}
 * verify that the block it is about to write is still a tripwire. That check is exactly what killed the
 * classic duplication loop, in which flowing water destroys the string, the tripwire hook writes the
 * (stale, cached) string state back into the position the water just emptied, and the water destroys it
 * again - dropping one extra string every cycle.
 *
 * <p>{@code TripWireHookBlockMixin} puts the 1.20.6 write-back rule back. {@code TripWireBlockMixin} and
 * {@code PlayerBreakTracker} additionally remember when a player sheared or broke a tripwire, which is
 * what {@code /legacytripwire} toggles between "shears break the wire" and "sheared wires may come back"
 * (the latter is what sheared string dupers need).
 */
@Mod(LegacyTripwire.MODID)
public final class LegacyTripwire {

    public static final String MODID = "legacy_tripwire";

    private static final Logger LOGGER = LogUtils.getLogger();

    public LegacyTripwire(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // /legacytripwire <true|false> - toggles the shears behaviour at runtime.
        RegisterCommandsEvent.BUS.addListener(LegacyTripwireCommand::register);

        LOGGER.info("Legacy Tripwire loaded - tripwire hooks behave like they did in 1.20.6 again.");
    }
}
