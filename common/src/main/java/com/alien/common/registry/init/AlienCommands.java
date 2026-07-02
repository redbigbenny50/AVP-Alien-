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

    public static void initialize() {
        REGISTRY.register(
            LiteralArgumentBuilder.<CommandSourceStack>literal(Alien.MOD.id())
                .then(
                    Commands.literal("debug")
                        .requires(
                            commandSourceStack -> commandSourceStack.hasPermission(Commands.LEVEL_GAMEMASTERS)
                        )
                        .then(CountCommand.create())
                        .then(
                            Commands.literal("hive")
                                .then(NearestHiveCommand.create())
                        )
                        .then(HiveDebugCommands.create())
                )
        );
    }
}
