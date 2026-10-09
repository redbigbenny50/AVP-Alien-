package com.alien.mixin;

import com.alien.common.gameplay.entity.living.alien.ovipositor.Ovipositor;
import com.alien.common.gameplay.entity.living.alien.royal_cocoon.RoyalCocoon;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg_laying.EggLayer;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops a fishing rod dragging a seated royal, her ovipositor, or a royal cocoon.
 * <p>
 * ⚠⚠ A SEATED ROYAL IS FURNITURE, NOT A FISH. She is anchored to her ovipositor and the whole hive is built around
 * where she sits - egg laying, the nursery anchor, the boss bar, the claim centre. A player reeling her across the room
 * moves all of it, and a cocoon or an eggsack being towed away is worse still.
 * </p>
 * <p>
 * ⭐ WHY {@code pullEntity} AND NOT {@code canBeHitByProjectile}. The obvious hook would be canBeHitByProjectile, since
 * FishingHook.canHitEntity gates on it - but Projectile.canHitEntity uses the SAME method, so overriding it would make
 * a queen immune to ARROWS as well. Cancelling the pull leaves her perfectly shootable and hookable; the line simply
 * cannot move her.
 * </p>
 * <p>
 * ⚠ THE HOOK STILL ATTACHES on purpose - the rod behaves normally, it just accomplishes nothing, which reads as "she is
 * too heavy" rather than as a bug.
 * </p>
 */
@Mixin(FishingHook.class)
public abstract class MixinFishingHook_NoRoyalPull {

    @Inject(at = @At("HEAD"), method = "pullEntity", cancellable = true)
    private void avp_alien$refuseToPullAnchoredHiveEntities(Entity entity, CallbackInfo callbackInfo) {
        // \u2b50 Oct 3 - THE CHAINED-SACK QUEEN IS THE ONE EXCEPTION. [stated] "we made it not possible to move a queen
        // on
        // the sack with a fishing pole this restriction is lifted for the chained sack queen only." A captive on her
        // chained eggsack holds no claim and anchors no hive, so none of the reasons above apply to her.
        var captive = avp_alien$chainedSackQueen(entity);
        if (captive != null) {
            if (entity != captive) {
                // Hooked the SACK: it rides her, so a pull on it moves nothing. Pull HER with the same vector vanilla
                // would have used, and cancel the pull on the passenger.
                var owner = ((FishingHook) (Object) this).getOwner();
                if (owner != null) {
                    var hook = (FishingHook) (Object) this;
                    captive.setDeltaMovement(
                        captive.getDeltaMovement()
                            .add(
                                new Vec3(owner.getX() - hook.getX(), owner.getY() - hook.getY(), owner.getZ() - hook.getZ())
                                    .scale(0.1)
                            )
                    );
                }
                callbackInfo.cancel();
            }
            // \u26a0 Queen.travel freezes a seated captive's horizontal motion every tick, which would throw the pull
            // away. Open a short window for it; vanilla's own pullEntity (or the sack branch above) supplies the
            // motion.
            captive.allowExternalPull();
            return;
        }
        if (avp_alien$isAnchored(entity)) {
            callbackInfo.cancel();
        }
    }

    /** The queen if {@code entity} is a captive seated on her CHAINED eggsack, or that eggsack itself; else null. */
    private static Queen avp_alien$chainedSackQueen(Entity entity) {
        if (entity instanceof Ovipositor sack && sack.isChainedEggsack() && sack.getVehicle() instanceof Queen queen) {
            return queen;
        }
        if (entity instanceof Queen queen) {
            var sack = queen.getOvipositorManager().getOvipositorOrNull();
            if (sack != null && sack.isChainedEggsack()) {
                return queen;
            }
        }
        return null;
    }

    /**
     * Whether this entity is anchored hive furniture that a line must never shift.
     * <p>
     * ⚠ A royal is only protected while she actually HAS an ovipositor. A wandering queen with no sack is an ordinary
     * mob and can be reeled about like anything else - it is the seating that makes her immovable, not her rank.
     * </p>
     */
    private static boolean avp_alien$isAnchored(Entity entity) {
        if (entity instanceof Ovipositor || entity instanceof RoyalCocoon) {
            return true;
        }

        // A royal riding her sack is dragged by the sack, so cancel on her too - otherwise the pull would fight the
        // seating every tick.
        if (entity instanceof EggLayer royal && royal.hasOvipositor()) {
            return true;
        }

        return entity.getVehicle() instanceof Ovipositor;
    }
}
