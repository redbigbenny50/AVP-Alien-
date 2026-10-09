package com.alien.common.registry.init;

import com.alien.Alien;
import com.alien.common.gameplay.command.count.CountCommand;
import com.alien.common.gameplay.command.hive.NearestHiveCommand;
import com.alien.common.gameplay.hive.command.HiveDebugCommands;
import com.blib.api.common.registry.v1.impl.BLibCommandRegistry;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class AlienCommands {

    private static final BLibCommandRegistry REGISTRY = Alien.MOD.registries().createCommandRegistry();

    /** [stated] "can we further reduce it to just /avp hive". */
    private static final String SHORT_ROOT = "avp";

    public static void initialize() {
        // ⭐⭐ THE WHOLE TREE MOVED UP TWO WORDS: /avp_alien debug hive inspect_queen -> /avp hive here queen.
        //
        // ⚠⚠ AND THE WIRING BUG IS GONE. This used to register Commands.literal("hive") TWICE under debug -
        // once wrapping NearestHiveCommand and once as HiveDebugCommands.create(), which is itself literal("hive").
        // Brigadier merges same-named literal siblings so it happened to work, but two declarations of one node is a
        // trap for whoever edits it next: adding a .requires() or a .executes() to either copy silently applies to
        // only half the tree. There is now exactly ONE hive node, and NearestHiveCommand hangs off it.
        REGISTRY.register(root(SHORT_ROOT));

        // ⚠ The long root is KEPT as a full duplicate rather than a redirect. A Brigadier redirect makes the
        // alias behave like a different node for suggestions and for anything that walks the tree, and existing
        // macros, command blocks and datapacks are all still typing the old path. Rebuilding it costs one call.
        REGISTRY.register(root(Alien.MOD.id()));
    }

    /**
     * The command tree, built fresh per root.
     * <p>
     * ⚠ A LiteralArgumentBuilder CANNOT BE SHARED BETWEEN TWO REGISTRATIONS - building it twice is required, not
     * wasteful, because {@code build()} produces a node that would otherwise be re-parented rather than copied.
     * </p>
     */
    private static LiteralArgumentBuilder<CommandSourceStack> root(String rootName) {
        return LiteralArgumentBuilder.<CommandSourceStack>literal(rootName)
            .requires(commandSourceStack -> commandSourceStack.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(CountCommand.create())
            .then(HiveDebugCommands.create().then(NearestHiveCommand.create()));
    }
}
