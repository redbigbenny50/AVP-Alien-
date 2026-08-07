package com.alien.mixin;

import com.alien.common.gameplay.armor.ArmorSetEffects;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A full nether chitin set makes its wearer fireproof.
 * <p>
 * [stated] "i want to make sure that any of our armors like nether chitin and nether royal chitin make the wearer
 * fireproof." Both armour classes already TELL the player this in their tooltip
 * ({@code WHEN_FULL_ARMOR_SET_EQUIPPED -> EFFECT_FIRE_RESISTANCE}) but nothing delivered it: the promise rode on
 * {@code BLibItemTags.FIRE_RESISTANT_ARMORS}, which no BLib class reads and which 0.3.0-alpha.419 deprecated.
 * <p>
 * Damage is refused rather than a fire-resistance EFFECT being applied, because an effect would show a potion icon,
 * count against the effect list, and could be cleared by milk - none of which suits a property of the armour itself.
 * {@code DamageTypeTags.IS_FIRE} is vanilla's own definition and covers standing in fire, lava, magma blocks, hot
 * floors and fireballs alike, so the cover matches what a player would call "fireproof".
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_FireproofArmor {

    @Inject(at = @At("HEAD"), method = "hurt", cancellable = true)
    private void avp_alien$netherChitinSetIsFireproof(
        DamageSource source,
        float amount,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (source.is(DamageTypeTags.IS_FIRE) && ArmorSetEffects.isWearingFireproofSet(LivingEntity.class.cast(this))) {
            callbackInfo.setReturnValue(false);
        }
    }

    /**
     * Put the flames out as well as the damage.
     * <p>
     * Cancelling the damage alone leaves the wearer visibly ablaze forever - burning but never hurt, which reads as a
     * bug rather than as protection. Clearing the timer each tick makes them look fireproof too.
     */
    @Inject(at = @At("HEAD"), method = "tick")
    private void avp_alien$netherChitinSetSmothersFlames(CallbackInfo callbackInfo) {
        var self = LivingEntity.class.cast(this);

        if (self.getRemainingFireTicks() > 0 && ArmorSetEffects.isWearingFireproofSet(self)) {
            self.clearFire();
        }
    }
}
