package com.alien.common.gameplay.entity.living.alien.royal_cocoon;

import com.alien.common.gameplay.entity.living.alien.xenomorph.CocoonState;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.block.AlienResinBlocks;
import com.alien.common.registry.tag.AlienDamageTypesTags;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * The resin "royal cocoon" a praetorian or crusher forms inside while metamorphosing into a queen. A placed, stationary
 * shell — it mirrors {@code Ovipositor} (non-pushable, no knockback, doesn't drown) but is <em>attackable</em>: the
 * metamorphosing creature plays its molt animation at this position, visually inside the cage.
 * <p>
 * Lifecycle (Stage B wiring): spawned when the metamorphosis begins and removed via {@code discard()} when she emerges;
 * if a player destroys it first it dies normally, which interrupts the transformation and drops resin. The strain
 * (regular / aberrant / nether) is encoded by the entity type, which also selects the texture in the renderer.
 */
public class RoyalCocoon extends Mob {

    /**
     * A nether cocoon does not burn.
     * <p>
     * {@code Alien.fireImmune()} grants this to every nether xenomorph, but a cocoon is a {@link Mob} rather than an
     * {@code Alien}, so it inherited nothing and a nether royal molting in her own biome cooked inside her shell.
     * Driven off the NETHER_ALIENS tag rather than a type comparison, so any nether type added later is covered by the
     * data alone.
     */
    @Override
    public boolean fireImmune() {
        return getType().is(AlienEntityTypeTags.NETHER_ALIENS) || super.fireImmune();
    }

    public static AttributeSupplier.Builder createRoyalCocoonAttributes() {
        return createMobAttributes()
            .add(Attributes.MAX_HEALTH, 60)
            .add(Attributes.MOVEMENT_SPEED, 0);
    }

    public RoyalCocoon(EntityType<? extends RoyalCocoon> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * The strain this cocoon belongs to, derived from its entity type (see class doc). Drives strain-aware targeting: a
     * xenomorph ignores its own strain's forming royal but a rival strain attacks it.
     */
    public AlienVariant getVariant() {
        var type = getType();
        if (type == AlienEntityTypes.ABERRANT_ROYAL_COCOON.get()) {
            return AlienVariant.ABERRANT;
        }
        if (type == AlienEntityTypes.NETHER_ROYAL_COCOON.get()) {
            return AlienVariant.NETHER;
        }
        return AlienVariant.NORMAL;
    }

    @Override
    public boolean hurt(@NotNull DamageSource damageSource, float amount) {
        if (damageSource.is(AlienDamageTypesTags.DOES_NOT_HURT_ALIENS)) {
            return false;
        }
        return super.hurt(damageSource, amount);
    }

    @Override
    public void knockback(double strength, double x, double z) {}

    @Override
    protected final boolean canAddPassenger(@NotNull Entity passenger) {
        return false;
    }

    @Override
    protected void doPush(@NotNull Entity entity) {}

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public int getAirSupply() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void die(@NotNull DamageSource damageSource) {
        // Destroyed before she emerged: interrupt the transformation (the vulnerable forming queen is killed) and
        // scatter resin from the broken cage. A successful emergence removes the cocoon via discard(), which does not
        // route through die(), so neither of these fire on a normal birth.
        interruptMetamorphosis();
        dropResin();
        super.die(damageSource);
    }

    private void interruptMetamorphosis() {
        if (!(level() instanceof ServerLevel)) {
            return;
        }
        // Kill whoever is currently cocooning inside this cage (the source praetorian/crusher early on, or the
        // queen herself once she has formed) -- proximity rather than a stored link, so it survives the swap.
        for (
            Xenomorph cocooning : level().getEntitiesOfClass(
                Xenomorph.class,
                getBoundingBox().inflate(1.0),
                xeno -> xeno.cocoonState.get() != CocoonState.NONE
            )
        ) {
            cocooning.discard();
        }
    }

    private void dropResin() {
        if (!(level() instanceof ServerLevel)) {
            return;
        }
        spawnAtLocation(AlienResinBlocks.RESIN.get(), 3);
    }
}
