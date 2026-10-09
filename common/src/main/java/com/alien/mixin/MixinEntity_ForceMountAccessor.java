package com.alien.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The two private vanilla steps that {@code Entity.startRiding} performs once every gate has passed - setting the
 * passenger's vehicle and registering it on the vehicle. {@code HostCaptureTask.forceMount} uses them to mount a
 * captured host on a captor when the host's OWN {@code startRiding} refuses.
 * <p>
 * Why this exists: MineColonies' {@code AbstractCivilianEntity.startRiding(entity, force)} (read from the 1.1.1396 jar)
 * returns false for any vehicle that is not a {@code SittingEntity}, a {@code MinecoloniesMinecart} or an
 * {@code ICitizenJobMount} - {@code force} is never consulted - so a citizen could never be carried, only stood over.
 * Implementing their marker interface would need MineColonies at compile time and a gated mixin; this touches vanilla
 * only and covers every other mod that overrides {@code startRiding} the same way.
 */
@Mixin(Entity.class)
public interface MixinEntity_ForceMountAccessor {

    @Accessor("vehicle")
    void avp_alien$setVehicle(Entity vehicle);

    @Invoker("addPassenger")
    void avp_alien$addPassenger(Entity passenger);

    @Invoker("couldAcceptPassenger")
    boolean avp_alien$couldAcceptPassenger();

    /** Virtual, so an override such as {@code Alien.canAddPassenger} is what actually answers. */
    @Invoker("canAddPassenger")
    boolean avp_alien$canAddPassenger(Entity passenger);
}
