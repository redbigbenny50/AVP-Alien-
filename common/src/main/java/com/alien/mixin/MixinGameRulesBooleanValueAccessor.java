package com.alien.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.BiConsumer;

@Mixin(GameRules.BooleanValue.class)
public interface MixinGameRulesBooleanValueAccessor {

    @Invoker("create")
    static GameRules.Type<GameRules.BooleanValue> avp_alien$create(
        boolean defaultValue,
        BiConsumer<MinecraftServer, GameRules.BooleanValue> callback
    ) {
        throw new AssertionError();
    }
}
