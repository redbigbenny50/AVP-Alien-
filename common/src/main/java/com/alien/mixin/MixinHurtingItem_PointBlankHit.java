package com.alien.mixin;

import com.alien.compatibility.pointblank.PointBlankBulletResistance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks a Point Blank round while it is landing, so {@code GunDamageParity} can tell it from a sword swing.
 * <h2>Why a mixin</h2> Point Blank has no damage type of its own - its bullets use vanilla {@code player_attack} - so
 * nothing in the {@code DamageSource} identifies them. Every non-explosive Point Blank hit goes through
 * {@code HurtingItem.hurtEntity}; while that method runs, the entity it is hitting is marked. See
 * {@link PointBlankBulletResistance}.
 * <h2>{@code @Pseudo}</h2> The target class only exists when Point Blank is installed. {@code @Pseudo} makes Mixin skip
 * this instead of failing, exactly as {@code MixinNuclearExplosionUtil_HiveStrike} does for AVP: Human.
 * <h2>⚠ Targeted by NAME, with remap off</h2> {@code hurtEntity} is Point Blank's own method name and is the same in
 * both jars, but its descriptor is not: Mojang names on NeoForge ({@code LivingEntity, EntityHitResult, Entity,
 * ItemStack}), intermediary on Fabric ({@code class_1309, class_3966, class_1297, class_1799}). A name-only target
 * matches both. The handler parameters below are Minecraft types, which the Fabric build remaps with the rest of this
 * class, so they line up with the Fabric descriptor too. Verified: it is the only declaration of {@code hurtEntity} in
 * either jar, so the name is unambiguous.
 * <h2>⚠ HEAD and RETURN</h2> RETURN covers every normal exit, including the early "protected entity" and explosive
 * branches. The mark is keyed to the target, so a throw that skipped RETURN could only leave a mark on that one entity.
 */
@Pseudo
@Mixin(targets = "com.vicmatskiv.pointblank.item.HurtingItem", remap = false)
public abstract class MixinHurtingItem_PointBlankHit {

    @Inject(method = "hurtEntity", at = @At("HEAD"), remap = false)
    private void avp_alien$markPointBlankHit(
        LivingEntity shooter,
        EntityHitResult entityHitResult,
        Entity projectile,
        ItemStack gunStack,
        CallbackInfoReturnable<Float> callbackInfo
    ) {
        PointBlankBulletResistance.beginHit(entityHitResult.getEntity());
    }

    @Inject(method = "hurtEntity", at = @At("RETURN"), remap = false)
    private void avp_alien$clearPointBlankHit(
        LivingEntity shooter,
        EntityHitResult entityHitResult,
        Entity projectile,
        ItemStack gunStack,
        CallbackInfoReturnable<Float> callbackInfo
    ) {
        PointBlankBulletResistance.endHit();
    }
}
