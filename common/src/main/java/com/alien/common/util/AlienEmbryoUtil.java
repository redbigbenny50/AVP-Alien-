package com.alien.common.util;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.model.alien.Host;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.AlienGameRules;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.key.AlienDamageTypeKeys;
import com.alien.compatibility.avp_human.AVPHuman;
import com.alien.compatibility.avp_human.GeneContainerProxy;
import com.human.common.model.GeneCarrier;
import com.human.common.util.EmbryoUtil;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class AlienEmbryoUtil {

    /**
     * Gestation length: when {@code embryoGrowthTimeInTicks} passes this, the chest-bursting phase begins. Public so
     * the Metamorphosis effect can slam the clock here (burst now) and the Growth Suppression effect can rewind
     * relative to it (five days out).
     */
    // TODO: Use data pack values here.
    public static final int BURST_TIME_IN_TICKS = (int) (TimeUnit.MINUTES.toSeconds(5) * 20);

    public static void runAlienEmbryoRoutines(LivingEntity hostEntity) {
        var host = (Host) hostEntity;
        var level = hostEntity.level();

        if (level.isClientSide) {
            return;
        }

        if (hostEntity instanceof Player player && (player.isCreative() || player.isSpectator() || player.isInvulnerable())) {
            host.removeEmbryo();
            return;
        }

        if (host.getEmbryoType().isSome()) {
            tickAlienEmbryoGrowth(hostEntity);
        } else {
            host.removeEmbryo();
        }
    }

    private static void tickAlienEmbryoGrowth(LivingEntity hostEntity) {
        var host = (Host) hostEntity;

        if (hostEntity.level().getDifficulty() == Difficulty.PEACEFUL) {
            host.removeEmbryo();
            return;
        }

        host.incrementEmbryoGrowthTimeInTicks();

        var burstTimeInTicks = BURST_TIME_IN_TICKS;
        if (host.getEmbryoGrowthTimeInTicks() <= burstTimeInTicks) {

            if (hostEntity instanceof Player player) {
                // TODO: Use data pack values here.
                if (host.getEmbryoGrowthTimeInTicks() > TimeUnit.MINUTES.toSeconds(4) * 20 + 30 * 20) {
                    hostEntity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20 * 4, 1, true, false, true));

                    if (player.tickCount % 10 == 0) {
                        player.playNotifySound(AlienSoundEvents.EFFECT_HEARTBEAT_3.get(), SoundSource.MASTER, 1, 1);
                    }
                    // TODO: Use data pack values here.
                } else if (host.getEmbryoGrowthTimeInTicks() > TimeUnit.MINUTES.toSeconds(4) * 20) {
                    if (player.tickCount % 20 == 0) {
                        player.playNotifySound(AlienSoundEvents.EFFECT_HEARTBEAT_2.get(), SoundSource.MASTER, 0.75F, 1);
                    }
                    // TODO: Use data pack values here.
                } else if (host.getEmbryoGrowthTimeInTicks() > TimeUnit.MINUTES.toSeconds(3) * 20 + 30 * 20) {
                    if (player.tickCount % 30 == 0) {
                        player.playNotifySound(AlienSoundEvents.EFFECT_HEARTBEAT_1.get(), SoundSource.MASTER, 0.5F, 1);
                    }
                    // TODO: Use data pack values here.
                } else if (host.getEmbryoGrowthTimeInTicks() > TimeUnit.MINUTES.toSeconds(3) * 20) {
                    if (player.tickCount % 40 == 0) {
                        player.playNotifySound(AlienSoundEvents.EFFECT_HEARTBEAT_0.get(), SoundSource.MASTER, 0.25F, 1);
                    }
                }
            }

            if (host.getEmbryoGrowthTimeInTicks() >= burstTimeInTicks - 8 * 20) {
                if (hostEntity.tickCount % 10 == 0) {
                    hostEntity.level()
                        .playSound(null, hostEntity, AlienSoundEvents.EFFECT_BONE_CRUNCH.get(), SoundSource.HOSTILE, 0.2F, 1);
                    hostEntity.hurt(hostEntity.damageSources().source(AlienDamageTypeKeys.CHESTBURSTING), 0.01F);
                }
            }

            return;
        }

        var totemPlayer = hostEntity instanceof Player player
            && hostEntity.level().getGameRules().getBoolean(AlienGameRules.AVP_ALIEN_TOTEMS_PREVENT_CHESTBURSTER_DEATH)
            && findTotem(player) != null
                ? player
                : null;
        var embryos = AlienEmbryoUtil.birthEmbryos(hostEntity, totemPlayer != null);

        embryos.forEach(embryo -> {});

        hostEntity.level().playSound(null, hostEntity, AlienSoundEvents.ENTITY_CHESTBURSTER_BURST.get(), SoundSource.HOSTILE, 0.25F, 1);

        if (totemPlayer == null) {
            hostEntity.hurt(hostEntity.damageSources().source(AlienDamageTypeKeys.CHESTBURSTING), Float.MAX_VALUE);
        } else {
            consumeTotem(totemPlayer);
        }

        // Remove the embryo no matter what.
        host.removeEmbryo();
    }

    public static List<Entity> birthEmbryos(LivingEntity parentEntity) {
        return birthEmbryos(parentEntity, false);
    }

    private static List<Entity> birthEmbryos(LivingEntity parentEntity, boolean preferAberrantEmbryo) {
        if (AVPHuman.MOD.isLoaded()) {
            if (((Host) parentEntity).getOrCreateParasiteGeneContainer() instanceof GeneContainerProxy.Wrapper(var geneContainer)) {
                return EmbryoUtil.birthEmbryos(
                    parentEntity,
                    geneContainer,
                    host -> AlienEmbryoUtil.alienEmbryoFactory(host, preferAberrantEmbryo),
                    1
                );
            }
        }

        var alienEmbryo = AlienEmbryoUtil.alienEmbryoFactory(parentEntity, preferAberrantEmbryo);

        return alienEmbryo == null
            ? List.of()
            : List.of(alienEmbryo);
    }

    public static @Nullable Entity alienEmbryoFactory(@NotNull LivingEntity hostEntity) {
        return alienEmbryoFactory(hostEntity, false);
    }

    private static @Nullable Entity alienEmbryoFactory(@NotNull LivingEntity hostEntity, boolean preferAberrantEmbryo) {
        var level = hostEntity.level();
        var host = (Host) hostEntity;
        var embryoTypeOption = host.getEmbryoType();

        if (embryoTypeOption.isNone()) {
            return null;
        }

        var embryo = aberrantEquivalent(embryoTypeOption.unwrap(), preferAberrantEmbryo).create(level);

        if (embryo == null) {
            return null;
        }

        if (embryo instanceof Mob mob) {
            mob.setPersistenceRequired();
        }

        if (embryo instanceof Alien hostBornAlien) {
            // Born of a host, not simulated out of a reserve bank. The flag is NBT-persisted and rides every growth
            // transition, so the eventual ADULT still knows - which is what feeds the brood bank (hosts are real
            // gains the hive earned, banked separately, uncapped, and drawn on before the main reserves).
            hostBornAlien.setHostBorn(true);
        }

        if (embryo instanceof Alien witheredCandidate && host.isEmbryoWithered()) {
            // The death sentence marked this embryo: it emerges withered - the player played god and made a demon.
            witheredCandidate.setWithered(true);
        }

        if (embryo instanceof Alien alien) {
            if (AVPHuman.MOD.isLoaded()) {
                if (host.getOrCreateParasiteGeneContainer() instanceof GeneContainerProxy.Wrapper(var geneContainer)) {
                    EmbryoUtil.applyGenesToEmbryo(
                        hostEntity.getType(),
                        geneContainer,
                        (GeneCarrier) alien,
                        true
                    );
                }
            }

            alien.setHostType(hostEntity.getType());
        }

        embryo.moveTo(hostEntity.position(), hostEntity.getYRot(), hostEntity.getXRot());
        embryo.setYRot(hostEntity.getYRot());
        embryo.setXRot(hostEntity.getXRot());

        if (embryo instanceof LivingEntity livingEmbryo) {
            // TODO: The genes are assigned once here, but if they're removed they don't appear on the embryo again.
            // Copies effects from previous entity to the next
            for (var effect : hostEntity.getActiveEffects()) {
                livingEmbryo.addEffect(new MobEffectInstance(effect.getEffect(), Integer.MAX_VALUE, effect.getAmplifier(), false, false));
            }
        }

        level.addFreshEntity(embryo);

        return embryo;
    }

    private static EntityType<?> aberrantEquivalent(EntityType<?> original, boolean preferAberrantEmbryo) {
        if (!preferAberrantEmbryo) {
            return original;
        }
        if (original == AlienEntityTypes.CHESTBURSTER.get()) {
            return AlienEntityTypes.ABERRANT_CHESTBURSTER.get();
        }
        if (original == AlienEntityTypes.ROYAL_CHESTBURSTER.get()) {
            return AlienEntityTypes.ROYAL_ABERRANT_CHESTBURSTER.get();
        }
        if (original == AlienEntityTypes.PREDALIEN_CHESTBURSTER.get()) {
            return AlienEntityTypes.ABERRANT_PREDALIEN_CHESTBURSTER.get();
        }
        return original;
    }

    private static @Nullable ItemStack findTotem(Player player) {
        if (player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            return player.getOffhandItem();
        }
        return null;
    }

    private static void consumeTotem(Player player) {
        var totem = findTotem(player);
        if (totem != null) {
            totem.shrink(1);
        }

        player.setHealth(1.0F);
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
        player.level().broadcastEntityEvent(player, (byte) 35);
    }
}
