package com.alien.mixin;

import com.alien.compatibility.AlienModGates;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tells whoever typed {@code /summon} why nothing appeared. {@link AlienModGates} explains which entities are gated.
 * <p>
 * The level-side block in {@code MixinServerLevel_GatedEntitySpawnDeny} would already stop the spawn, but it stops it
 * at {@code addFreshEntity}, which leaves vanilla reporting a duplicate-UUID failure - a genuinely misleading error for
 * someone who has simply not installed AVP: Human. Failing here instead, before the entity is even built, produces the
 * ordinary red command error naming the missing mod.
 */
@Mixin(SummonCommand.class)
public abstract class MixinSummonCommand_GatedEntityDeny {

    @Inject(method = "createEntity", at = @At("HEAD"))
    private static void avp_alien$denySummonsNeedingAMissingMod(
        CommandSourceStack source,
        Holder.Reference<EntityType<?>> type,
        Vec3 pos,
        CompoundTag tag,
        boolean randomizeProperties,
        CallbackInfoReturnable<Entity> cir
    ) throws CommandSyntaxException {
        var missingMod = AlienModGates.missingModFor(type.value());
        if (missingMod != null) {
            throw new SimpleCommandExceptionType(AlienModGates.refusalMessage(type.value(), missingMod)).create();
        }
    }
}
