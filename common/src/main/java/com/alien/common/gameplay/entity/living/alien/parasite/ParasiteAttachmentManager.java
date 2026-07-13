package com.alien.common.gameplay.entity.living.alien.parasite;

import com.alien.common.model.alien.FreeMob;
import com.alien.common.model.alien.Host;
import com.alien.common.registry.init.AlienDataSyncKeys;
import com.alien.common.registry.key.AlienDamageTypeKeys;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.alien.common.util.AlienPredicates;
import com.blib.api.common.data_sync.v1.DataAccessor;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ParasiteAttachmentManager {

    private final Parasite parasite;

    private final DataAccessor<Integer> ticksAttachedToHost;

    public ParasiteAttachmentManager(Parasite parasite) {
        this.parasite = parasite;
        this.ticksAttachedToHost = new DataAccessor<>(parasite, AlienDataSyncKeys.PARASITE_TICKS_ATTACHED_TO_HOST.get());
    }

    public void tick() {
        if (parasite.level().isClientSide) {
            return;
        }

        var host = getHost();

        if (host != null && host.getType().is(AlienEntityTypeTags.ALIENS)) {
            ticksAttachedToHost.reset();
            return;
        }

        if (!isAttachedToHost()) {
            ticksAttachedToHost.reset();

            if (host instanceof Mob mob) {
                ((FreeMob) mob).restoreFreedom();
            }

            return;
        }

        Objects.requireNonNull(host);

        if (!AlienPredicates.isHost(host)) {
            parasite.unRide();

            if (host instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(new ClientboundSetPassengersPacket(host));
            }
            return;
        }

        if (parasite.isDeadOrDying()) {
            parasite.unRide();
            return;
        }

        var effectTimeInTicks = 20 * 4;
        host.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, effectTimeInTicks, 3, true, false, true));
        host.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, effectTimeInTicks, 3, true, false, true));
        host.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, effectTimeInTicks, 3, true, false, true));

        if (host instanceof Player) {
            // A hugged player is pinned from the first tick, not from the ten-second mark: they cannot run, only fight
            // (see HuggerStruggle). The absurd amplifier zeroes movement speed outright; the jump is killed client-side
            // by MixinKeyboardInput_HuggerLock, since slowness alone would still let them hop in place while mashing.
            host.addEffect(
                new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, effectTimeInTicks, 100, true, false, true)
            );
        }

        var falloffTimeInTicks = (host instanceof ServerPlayer ? 1.5 : 2.5) * 20 * 60;

        // TODO: Make time configurable
        if (ticksAttachedToHost() < 20 * 10) {
            host.hurt(parasite.damageSources().source(AlienDamageTypeKeys.SMOTHERING), 0.01F);
        } else if (ticksAttachedToHost() > falloffTimeInTicks) {
            parasite.stopRiding();

            if (host instanceof ServerPlayer player) {
                player.connection.send(new ClientboundSetPassengersPacket(host));
            }
        } else {
            host.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, effectTimeInTicks, 3, true, false, true));
            host.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, effectTimeInTicks, 100, true, false, true));

            if (host instanceof Mob mob) {
                ((FreeMob) mob).removeFreedom();
            }

            // TODO: Make time configurable
            if (ticksAttachedToHost() >= 20 * 20) {

                if (parasite.isFertile.get()) {
                    ((Host) host).implantEmbryo(parasite);
                    parasite.isFertile.set(false);
                    // TODO: Play nasty toob sound
                }
            }
        }

        host.setAirSupply(host.getMaxAirSupply());
        ticksAttachedToHost.set(ticksAttachedToHost.get() + 1);
    }

    public void restore() {
        parasite.isFertile.set(true);
    }

    public @Nullable LivingEntity getHost() {
        return parasite.getVehicle() instanceof LivingEntity livingEntity ? livingEntity : null;
    }

    public boolean isAttachedToHost() {
        return getHost() != null && parasite.isAlive();
    }

    public int ticksAttachedToHost() {
        return ticksAttachedToHost.get();
    }
}
