package com.alien.mixin;

import com.alien.common.gameplay.effect.ChitinousAuraStatusEffect;
import com.alien.common.registry.init.AlienMobEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The bite behind {@link ChitinousAuraStatusEffect}. A {@link net.minecraft.world.effect.MobEffect} gets no callback
 * when its holder is struck, so the retaliation has to be injected into {@code LivingEntity.hurt}.
 * <p>
 * Injected at RETURN and gated on the return value, so only blows that actually LANDED provoke it - a hit stopped by
 * invulnerability frames, a shield, or the wrong difficulty draws no blood on the spines.
 * <p>
 * Thorns damage is deliberately excluded from provoking more thorns. Two coated fighters trading blows would otherwise
 * volley retaliation back and forth until one of them died of it, and each volley would nest a {@code hurt} call inside
 * the last.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_ChitinousAura {

    @Inject(
        method = "hurt",
        at = @At("RETURN")
    )
    private void avp_alien$retaliateWithChitinousAura(
        DamageSource damageSource,
        float damageAmount,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (!Boolean.TRUE.equals(callbackInfo.getReturnValue())) {
            return;
        }

        var self = (LivingEntity) (Object) this;
        if (self.level().isClientSide || !self.hasEffect(AlienMobEffects.getChitinousAuraHolder())) {
            return;
        }

        // Never let spines answer spines.
        if (damageSource.is(DamageTypes.THORNS)) {
            return;
        }

        if (!(damageSource.getEntity() instanceof LivingEntity attacker) || attacker == self) {
            return;
        }

        var random = self.getRandom();
        if (random.nextFloat() >= ChitinousAuraStatusEffect.RETALIATION_CHANCE) {
            return;
        }

        attacker.hurt(
            self.damageSources().thorns(self),
            1.0F + random.nextInt(ChitinousAuraStatusEffect.RETALIATION_DAMAGE_BOUND)
        );

        if (self.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                ParticleTypes.DAMAGE_INDICATOR,
                attacker.getX(),
                attacker.getY(0.5),
                attacker.getZ(),
                6,
                0.25,
                0.25,
                0.25,
                0.0
            );
        }
    }
}
