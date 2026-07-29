package com.alien.mixin;

import com.alien.common.gameplay.effect.RadiationSicknessStatusEffect;
import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienMobEffects;
import com.alien.compatibility.avp_human.AVPHuman;
import com.alien.compatibility.avp_human.RadiationCompat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The bite behind Glowing Talons: everything the holder strikes is dosed with radiation.
 * <p>
 * A MobEffect gets no callback when its holder hits something, so the retaliation-shaped hook has to be injected into
 * {@code LivingEntity.hurt} - the same door {@code MixinLivingEntity_ChitinousAura} uses, except this one reads the
 * ATTACKER's effects rather than the victim's.
 * <p>
 * Injected at RETURN and gated on the return value, so only blows that actually LANDED dose anything: a hit stopped by
 * invulnerability frames or a shield leaves the target clean.
 * <p>
 * ALIENS ARE NEVER DOSED. The species is radiation-immune by decree everywhere else in this mod - an irradiated
 * xenomorph does not poison its own kin on a landed hit either - so this cannot be turned into a way to gas a hive from
 * inside it.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_GlowingTalons {

    /**
     * AVP: Human's own armour tag, referenced BY ID so no avp_human class is touched - empty when the mod is absent.
     */
    private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> RADIATION_RESISTANT_ARMORS =
        net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ITEM,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("avp_human", "radiation_resistant_armors")
        );

    /** The strain's own eligibility rule, so talons and claws agree on who can be dosed. */
    private static boolean avp_alien$canBeIrradiatedByTouch(LivingEntity victim) {
        // Aliens are radiation-immune AS A SPECIES - except the aberrant strain, which is not. Aberrants are the
        // weak line: it is why they cannot convert to irradiated the way normal and nether do, and it is why they
        // burn instead. The avp_human:radiation_resistant tag we contribute lists every alien EXCEPT them, and this
        // mirrors it so claws and talons agree with the environment.
        if (victim instanceof Alien irradiatedAlien && irradiatedAlien.getVariant() != AlienVariant.ABERRANT) {
            return false;
        }

        return !com.blib.api.common.entity.v1.BLibEntityPredicates.hasFullArmorSetMatching(
            victim,
            stack -> stack.is(RADIATION_RESISTANT_ARMORS)
        );
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void avp_alien$doseWithGlowingTalons(
        DamageSource damageSource,
        float damageAmount,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (!Boolean.TRUE.equals(callbackInfo.getReturnValue())) {
            return;
        }

        var victim = (LivingEntity) (Object) this;
        if (victim.level().isClientSide) {
            return;
        }

        // CHEAP CHECKS FIRST. This runs on EVERY LivingEntity.hurt on the server - every arrow, every fall, every mob
        // swing - so the common case has to leave immediately. Asking whether the attacker even has the effect is a
        // map lookup; canBeIrradiatedByTouch walks the victim's full armour set. That was the wrong way round.
        if (!(damageSource.getEntity() instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }

        if (!attacker.hasEffect(AlienMobEffects.getGlowingTalonsHolder())) {
            return;
        }

        if (!avp_alien$canBeIrradiatedByTouch(victim)) {
            return;
        }

        // The same bridge the irradiated strain uses, so a player under this doses exactly as its aliens do.
        if (AVPHuman.MOD.isLoaded()) {
            RadiationCompat.irradiateOnHit(victim);
            return;
        }

        victim.addEffect(
            new MobEffectInstance(
                AlienMobEffects.getRadiationSicknessHolder(),
                RadiationSicknessStatusEffect.JELLY_DOSE_DURATION_TICKS,
                0
            )
        );
    }
}
