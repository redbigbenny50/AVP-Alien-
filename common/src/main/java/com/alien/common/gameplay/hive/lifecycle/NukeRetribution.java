package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.party.AttackCampaign;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An empress remembers.
 * <p>
 * Destroying an aberrant hive that sat under an empress's influence does not end there: a Minecraft day later she opens
 * a campaign against whoever did it, from whichever of her surviving hives can field the most. The delay is the whole
 * point - the player walks away from the crater thinking it is over.
 * <p>
 * It is persisted rather than held in memory because a day is long enough to log out and come back, and a retribution
 * that quietly evaporates over a server restart is worse than one that never existed.
 * <p>
 * Rather than inventing a parallel raid dispatcher this drives the campaign machinery that already exists: opening an
 * {@link AttackCampaign} against that player on that hive is exactly what an intrusion does, so the waves, the party
 * composition and the lifecycle all behave as they normally would - the hive simply decided the player was hostile
 * without them having set foot in it.
 */
public final class NukeRetribution extends SavedData {

    private static final String DATA_NAME = "avp_alien_nuke_retribution";

    private static final String NBT_PENDING = "Pending";

    private static final String NBT_DUE = "Due";

    private static final String NBT_TARGET = "Target";

    private static final String NBT_LINEAGE = "Lineage";

    /** One Minecraft day. */
    public static final long RETRIBUTION_DELAY_TICKS = 24000L;

    private final List<Pending> pending;

    private record Pending(
            long dueTick,
            UUID target,
            String lineageFactionId
    ) {}

    public NukeRetribution() {
        this.pending = new ArrayList<>();
    }

    private NukeRetribution(List<Pending> pending) {
        this.pending = pending;
    }

    /** Queues a reckoning against {@code target}, to be answered by {@code lineageFactionId} a day from now. */
    public static void schedule(ServerLevel level, String lineageFactionId, UUID target) {
        var data = getOrCreate(level);
        data.pending.add(new Pending(level.getGameTime() + RETRIBUTION_DELAY_TICKS, target, lineageFactionId));
        data.setDirty();

        Alien.LOGGER.info(
                "Nuke: lineage {} will answer for its lost hive in {} ticks",
                lineageFactionId,
                RETRIBUTION_DELAY_TICKS
        );
    }

    /** Fires anything due. Cheap when nothing is pending, which is almost always. */
    public static void tick(MinecraftServer server) {
        for (var level : server.getAllLevels()) {
            var data = level.getDataStorage().get(factory(), DATA_NAME);
            if (data == null || data.pending.isEmpty()) {
                continue;
            }

            var now = level.getGameTime();
            var due = new ArrayList<Pending>();
            data.pending.removeIf(entry -> {
                if (entry.dueTick() > now) {
                    return false;
                }
                // OFFLINE TARGETS ARE RE-QUEUED, not dropped - [stated] Aug 1: "re-queue until they log in." The
                // entry stays in the pending list untouched (already persisted, already past due) and this check
                // simply runs again next tick; the moment they appear on the player list the debt fires. Logging
                // out therefore delays the reckoning but never voids it - the same rule the attack parties follow.
                if (level.getServer().getPlayerList().getPlayer(entry.target()) == null) {
                    return false;
                }
                due.add(entry);
                return true;
            });

            if (due.isEmpty()) {
                continue;
            }

            data.setDirty();
            for (var entry : due) {
                answer(level, entry);
            }
        }
    }

    private static void answer(ServerLevel level, Pending entry) {
        var faction = Alien.MOD.factions().get(net.minecraft.resources.ResourceLocation.parse(entry.lineageFactionId()));
        if (faction == null || !(faction.data() instanceof LineageFactionData lineage) || !lineage.isAlive()) {
            // She did not survive to collect. Nothing to send, and nothing to say.
            return;
        }

        var staging = strongestHive(lineage);
        if (staging == null) {
            return;
        }

        var player = level.getServer().getPlayerList().getPlayer(entry.target());
        if (player == null) {
            // Should not happen - tick() only removes an entry from the queue once the target is online. If they
            // vanished between the check and this call, the entry has already been consumed; losing this one edge
            // case is acceptable rather than re-inserting mid-iteration.
            return;
        }

        var campaign = staging.attackCampaigns().computeIfAbsent(entry.target(), ignored -> new AttackCampaign());
        campaign.setLastHostileTick(level.getGameTime());
        campaign.beginCampaign(level.getGameTime());

        player.displayClientMessage(
                Component.literal("A scream pierces your mind calling for retribution").withStyle(ChatFormatting.DARK_RED),
                false
        );

        Alien.LOGGER.info(
                "Nuke: lineage {} opened a retribution campaign against {} from location {}",
                entry.lineageFactionId(),
                entry.target(),
                staging.id().value()
        );
    }

    /** Whichever of her hives can field the most - "the most raid members available". */
    private static HiveLocation strongestHive(LineageFactionData lineage) {
        HiveLocation best = null;
        var bestCount = -1;

        for (var location : lineage.locationsById().values()) {
            if (!location.isAlive()) {
                continue;
            }

            var count = location.localReserves().getCount();
            if (count > bestCount) {
                bestCount = count;
                best = location;
            }
        }

        return bestCount <= 0 ? null : best;
    }

    public static NukeRetribution getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    /**
     * Cached. This is asked for on EVERY tick, per level, and a fresh Factory record each time was pure garbage - forty
     * pointless allocations a second doing nothing. The factory is immutable, so one instance serves forever.
     */
    private static final Factory<NukeRetribution> FACTORY = new Factory<>(NukeRetribution::new, NukeRetribution::load, null);

    private static Factory<NukeRetribution> factory() {
        return FACTORY;
    }

    private static NukeRetribution load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        var pending = new ArrayList<Pending>();
        var list = tag.getList(NBT_PENDING, Tag.TAG_COMPOUND);

        for (var index = 0; index < list.size(); index++) {
            var entry = list.getCompound(index);
            pending.add(new Pending(entry.getLong(NBT_DUE), entry.getUUID(NBT_TARGET), entry.getString(NBT_LINEAGE)));
        }

        return new NukeRetribution(pending);
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        var list = new ListTag();

        for (var entry : pending) {
            var entryTag = new CompoundTag();
            entryTag.putLong(NBT_DUE, entry.dueTick());
            entryTag.putUUID(NBT_TARGET, entry.target());
            entryTag.putString(NBT_LINEAGE, entry.lineageFactionId());
            list.add(entryTag);
        }

        tag.put(NBT_PENDING, list);
        return tag;
    }
}