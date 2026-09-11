package cn.kynarain.legacy_tripwire;

import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * {@code /legacytripwire [<shearsBreakWire>]} - flips the "shearing a tripwire breaks it" switch at
 * runtime and persists it to the config file.
 *
 * <p>Without an argument the command reports the current value.
 */
public final class LegacyTripwireCommand {

    /** The second spelling is a common typo and is accepted as an alias on purpose. */
    private static final String[] NAMES = {"legacytripwire", "legacytripware"};

    private LegacyTripwireCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        for (String name : NAMES) {
            event.getDispatcher().register(
                    Commands.literal(name)
                            // 1.21.11 replaced the old numeric levels with named permission checks;
                            // LEVEL_GAMEMASTERS is the old "permission level 2" (same as /gamerule).
                            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                            .executes(context -> report(context.getSource()))
                            .then(Commands.argument("shearsBreakWire", BoolArgumentType.bool())
                                    .executes(context -> apply(context.getSource(),
                                            BoolArgumentType.getBool(context, "shearsBreakWire")))));
        }
    }

    private static int report(CommandSourceStack source) {
        boolean value = Config.playerBreakBreaksWire;
        source.sendSuccess(() -> Component.literal("legacy_tripwire: playerBreakBreaksWire = " + value
                + (value
                        ? " - a sheared or broken tripwire is never written back, so shears always break it"
                        : " - a sheared tripwire may be written back by the hook, which is what keeps sheared string dupers running")), false);
        return 1;
    }

    private static int apply(CommandSourceStack source, boolean value) {
        Config.setPlayerBreakBreaksWire(value);
        source.sendSuccess(() -> Component.literal("legacy_tripwire: playerBreakBreaksWire = " + value
                + (value
                        ? " - shears now break tripwire for good (machines that rely on shearing will stop)"
                        : " - sheared tripwire can be written back again (string dupers work)")), true);
        return 1;
    }
}
