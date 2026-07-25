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

    /** Server truth, synced: the host's entity id while attached, -1 when not. Clients self-heal from it. */
    private final DataAccessor<Integer> attachedHostId;

    public ParasiteAttachmentManager(Parasite parasite) {
        this.parasite = parasite;
        this.ticksAttachedToHost = new DataAccessor<>(parasite, AlienDataSyncKeys.PARASITE_TICKS_ATTACHED_TO_HOST.get());
        this.attachedHostId = new DataAccessor<>(parasite, AlienDataSyncKeys.PARASITE_ATTACHED_HOST_ID.get());
    }

    /** The synced server-truth host id (-1 = detached). Readable on both sides. */
    public int attachedHostId() {
        return attachedHostId.get();
    }

    public void tick() {
        if (parasite.level().isClientSide) {
            return;
        }

        var host = getHost();

        // Publish the server's attachment truth so stale client passenger lists can self-correct.
        var truthfulHostId = host != null && parasite.isAlive() ? host.getId() : -1;
        if (attachedHostId.get() != truthfulHostId) {
            attachedHostId.set(truthfulHostId);
        }

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
            parasite.detach();

            if (host instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(new ClientboundSetPassengersPacket(host));
            }
            return;
        }

        if (parasite.isDeadOrDying()) {
            parasite.detach();
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

        // Players shed a spent hugger at 1.5 minutes; ANIMALS shed theirs ~12 seconds after the 20-second implant
        // (a zebra wearing a face ornament for 2.5 minutes read as a bug in the field - the short linger still sells
        // "it did its job" without looking stuck).
        var falloffTimeInTicks = host instanceof ServerPlayer ? 1.5 * 20 * 60 : (20 * 20) + (20 * 12);

        // TODO: Make time configurable
        if (ticksAttachedToHost() < 20 * 10) {
            host.hurt(parasite.damageSources().source(AlienDamageTypeKeys.SMOTHERING), 0.01F);
        } else if (ticksAttachedToHost() > falloffTimeInTicks) {
            parasite.detach();

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
