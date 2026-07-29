package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.gameplay.hive.economy.IrradiatedHiveRules;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.spawning.ReserveSpawnUtil;
import com.alien.common.registry.init.AlienSoundEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What a newborn irradiated hive does two days after it wakes up.
 * <h2>Not the same thing as the aberrant retribution</h2> {@link NukeRetribution} is an EMPRESS avenging a hive she
 * LOST, a day later, from a surviving hive of her lineage, against ONE culprit. This is the hive that was CREATED
 * coming for EVERYONE involved, two days later, free and at full strength - and it can be called off by killing it
 * first. Keep them separate.
 * <h2>The rules</h2>
 * <ul>
 * <li>[stated] "a FREE max member raid party goes out and attacks the person or persons involved with the nuking" -
 * free of biomass and jelly, at the hive's maximum party size.</li>
 * <li>[stated] multiple culprits SPLIT BY DISTANCE: close together, one raid; "a good distance apart then its 2 raids
 * etc." One party per cluster.</li>
 * <li>[stated] "its on a 2 days mc time from when the hives created" - measured from CONVERSION.</li>
 * <li>[stated] "if the hive dies before then it gets cleared" - so finishing what crawled out of the crater cancels it
 * entirely. The two days are a deadline, not a countdown.</li>
 * </ul>
 */
public final class IrradiatedBirthRaid extends SavedData {

    private static final String DATA_NAME = "avp_alien_irradiated_birth_raid";

    private static final String NBT_PENDING = "Pending";

    private static final String NBT_DUE = "Due";

    private static final String NBT_LOCATION = "Location";

    private static final String NBT_TARGETS = "Targets";

    /** Two Minecraft days. */
    public static final long BIRTH_RAID_DELAY_TICKS = 48000L;

    /** Culprits closer together than this share one raid; further apart and each gets their own. */
    private static final double CLUSTER_RADIUS_BLOCKS = 64.0;

    private static final double CLUSTER_RADIUS_SQUARED = CLUSTER_RADIUS_BLOCKS * CLUSTER_RADIUS_BLOCKS;

    /** How far out a party materializes from its quarry. */
    private static final double SPAWN_SPREAD_BLOCKS = 12.0;

    private final List<Pending> pending;

    private record Pending(
        long dueTick,
        String locationId,
        List<UUID> targets
    ) {}

    public IrradiatedBirthRaid() {
        this.pending = new ArrayList<>();
    }

    private IrradiatedBirthRaid(List<Pending> pending) {
        this.pending = pending;
    }

    /** Called at conversion. Tags every player the hive holds responsible; one shot, never repeated. */
    public static void schedule(ServerLevel level, HiveLocation location, List<UUID> culprits) {
        if (culprits.isEmpty()) {
            return;
        }

        var data = getOrCreate(level);
        data.pending.add(
            new Pending(level.getGameTime() + BIRTH_RAID_DELAY_TICKS, location.id().value().toString(), List.copyOf(culprits))
        );
        data.setDirty();

        Alien.LOGGER.info(
            "Nuke: hive {} will answer for its own birth in {} ticks, against {} player(s)",
            location.id().value(),
            BIRTH_RAID_DELAY_TICKS,
            culprits.size()
        );
    }

    public static void tick(MinecraftServer server) {
        for (var level : server.getAllLevels()) {
            var data = level.getDataStorage().get(factory(), DATA_NAME);
            if (data == null || data.pending.isEmpty()) {
                continue;
            }

            var now = level.getGameTime();
            var changed = false;

            // [stated] "lets make it persist so player cant escape by logging out or disconnecting." So a due entry is
            // only DISCHARGED when it is actually answered - if every target is offline it stays pending and is
            // retried next tick, for as long as it takes them to come back. Logging out delays the reckoning; it does
            // not cancel it. Only killing the hive does that, which answer() still handles.
            for (var entry : new ArrayList<>(data.pending)) {
                if (entry.dueTick() > now) {
                    continue;
                }

                if (answer(level, entry)) {
                    data.pending.remove(entry);
                    changed = true;
                }
            }

            if (changed) {
                data.setDirty();
            }
        }
    }

    /** @return true when this debt is settled and may be dropped; false to keep waiting. */
    private static boolean answer(ServerLevel level, Pending entry) {
        var location = findLocation(level, entry.locationId());

        // [stated] "if the hive dies before then it gets cleared." Killing it inside the two days calls this off - and
        // that IS a discharge, so it returns true and the entry goes.
        if (location == null || !location.isAlive() || !IrradiatedHiveRules.isIrradiated(location)) {
            Alien.LOGGER.info("Nuke: birth raid for {} cancelled - the hive did not survive to send it", entry.locationId());
            return true;
        }

        var present = new ArrayList<ServerPlayer>();
        for (var targetId : entry.targets()) {
            var player = level.getServer().getPlayerList().getPlayer(targetId);
            if (player != null && player.level() == level) {
                present.add(player);
            }
        }

        // Nobody home. The hive is still standing and still owed, so keep the entry and try again.
        if (present.isEmpty()) {
            return false;
        }

        for (var cluster : clusterByDistance(present)) {
            sendRaid(level, location, cluster);
        }

        return true;
    }

    /**
     * Groups culprits standing near each other so they share one party.
     * <p>
     * [stated] "if multiple players were involved and they are close to each other one raid attacks them. if its
     * multiple and they are a good distant apart then its 2 raids etc." Simple greedy grouping - the first ungrouped
     * player seeds a cluster and anyone within range joins it.
     */
    private static List<List<ServerPlayer>> clusterByDistance(List<ServerPlayer> players) {
        var clusters = new ArrayList<List<ServerPlayer>>();
        var remaining = new ArrayList<>(players);

        while (!remaining.isEmpty()) {
            var seed = remaining.remove(0);
            var cluster = new ArrayList<ServerPlayer>();
            cluster.add(seed);

            remaining.removeIf(other -> {
                if (other.position().distanceToSqr(seed.position()) > CLUSTER_RADIUS_SQUARED) {
                    return false;
                }
                cluster.add(other);
                return true;
            });

            clusters.add(cluster);
        }

        return clusters;
    }

    /**
     * One free party at full strength, on the cluster's seed.
     * <p>
     * FREE means no biomass and no jelly - the newborn hive is not buying this, it is reacting. The members still come
     * out of its own reserves, so it is spending PEOPLE rather than currency, which is the only thing it has left to
     * spend.
     */
    private static void sendRaid(ServerLevel level, HiveLocation location, List<ServerPlayer> cluster) {
        var quarry = cluster.get(0);
        var cap = com.alien.common.gameplay.hive.structure.HiveRouter.isEmpressInfluenced(location)
            ? HiveLocationRegistry.INSTANCE.config().attackPartyMaxSizeEmpress()
            : HiveLocationRegistry.INSTANCE.config().attackPartyMaxSize();

        var sent = 0;
        var types = new ArrayList<>(location.localReserves().getAvailableEntityTypes());
        var progressed = true;

        while (progressed && sent < cap) {
            progressed = false;

            for (var type : types) {
                if (sent >= cap) {
                    break;
                }

                if (spawnRaider(level, location, type, quarry)) {
                    sent++;
                    progressed = true;
                }
            }
        }

        if (sent <= 0) {
            return;
        }

        announce(level, cluster, quarry);
        Alien.LOGGER.info(
            "Nuke: birth raid from {} - {} raider(s) against {} player(s)",
            location.id().value(),
            sent,
            cluster.size()
        );
    }

    private static boolean spawnRaider(ServerLevel level, HiveLocation location, EntityType<?> type, ServerPlayer quarry) {
        if (!location.localReserves().trySpawn(type)) {
            return false;
        }

        var entity = type.create(level);
        if (entity == null) {
            return false;
        }

        var x = quarry.getX() + (level.random.nextDouble() - 0.5) * SPAWN_SPREAD_BLOCKS * 2.0;
        var z = quarry.getZ() + (level.random.nextDouble() - 0.5) * SPAWN_SPREAD_BLOCKS * 2.0;
        entity.moveTo(x, quarry.getY(), z, level.random.nextFloat() * 360.0F, 0.0F);

        if (entity instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(quarry.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            mob.setPersistenceRequired();
            mob.setTarget(quarry);
        }

        level.addFreshEntityWithPassengers(entity);
        ReserveSpawnUtil.markSpawnedFromReserves(entity);
        return true;
    }

    /** The roar carries from where they land; the line lands in everyone's chat. */
    private static void announce(ServerLevel level, List<ServerPlayer> cluster, ServerPlayer quarry) {
        level.playSound(
            null,
            quarry.getX(),
            quarry.getY(),
            quarry.getZ(),
            AlienSoundEvents.ENTITY_HARBINGER_ROAR_3.get(),
            SoundSource.HOSTILE,
            8.0F,
            1.0F
        );

        var line = Component
            .literal("Your nuclear fire shattered their home, but baptized something worse. Now, you face its irradiated fury.")
            .withStyle(ChatFormatting.DARK_RED);

        cluster.forEach(player -> player.displayClientMessage(line, false));
    }

    private static @org.jetbrains.annotations.Nullable HiveLocation findLocation(ServerLevel level, String locationId) {
        for (var factionId : Alien.MOD.factions().getAllIds()) {
            if (!com.alien.common.gameplay.hive.id.LineageIds.isLineageId(factionId)) {
                continue;
            }

            var faction = Alien.MOD.factions().get(factionId);
            if (
                faction == null
                    || !(faction.data() instanceof com.alien.common.gameplay.hive.faction.LineageFactionData lineage)
            ) {
                continue;
            }

            for (var location : lineage.locationsById().values()) {
                if (location.id().value().toString().equals(locationId)) {
                    return location;
                }
            }
        }

        return null;
    }

    public static IrradiatedBirthRaid getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    /**
     * Cached. This is asked for on EVERY tick, per level, and a fresh Factory record each time was pure garbage - forty
     * pointless allocations a second doing nothing. The factory is immutable, so one instance serves forever.
     */
    private static final Factory<IrradiatedBirthRaid> FACTORY = new Factory<>(IrradiatedBirthRaid::new, IrradiatedBirthRaid::load, null);

    private static Factory<IrradiatedBirthRaid> factory() {
        return FACTORY;
    }

    private static IrradiatedBirthRaid load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        var pending = new ArrayList<Pending>();
        var list = tag.getList(NBT_PENDING, Tag.TAG_COMPOUND);

        for (var index = 0; index < list.size(); index++) {
            var entry = list.getCompound(index);
            var targets = new ArrayList<UUID>();
            var targetList = entry.getList(NBT_TARGETS, Tag.TAG_INT_ARRAY);

            for (var t = 0; t < targetList.size(); t++) {
                targets.add(net.minecraft.nbt.NbtUtils.loadUUID(targetList.get(t)));
            }

            pending.add(new Pending(entry.getLong(NBT_DUE), entry.getString(NBT_LOCATION), targets));
        }

        return new IrradiatedBirthRaid(pending);
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        var list = new ListTag();

        for (var entry : pending) {
            var entryTag = new CompoundTag();
            entryTag.putLong(NBT_DUE, entry.dueTick());
            entryTag.putString(NBT_LOCATION, entry.locationId());

            var targets = new ListTag();
            entry.targets().forEach(target -> targets.add(net.minecraft.nbt.NbtUtils.createUUID(target)));
            entryTag.put(NBT_TARGETS, targets);

            list.add(entryTag);
        }

        tag.put(NBT_PENDING, list);
        return tag;
    }
}
