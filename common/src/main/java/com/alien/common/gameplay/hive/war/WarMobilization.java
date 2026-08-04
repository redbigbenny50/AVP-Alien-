package com.alien.common.gameplay.hive.war;

import com.alien.Alien;
import com.alien.common.gameplay.hive.economy.CasteResolver;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.spawning.HiveLoadedSpawner;
import com.alien.common.gameplay.hive.spawning.ReserveSpawnUtil;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Feeding a war. [stated] "the hive is at war it will be sending its members to fight and emptying the reserves to do
 * so even raid xenos will try to enter the fight."
 * <p>
 * THE BANK DRIPS, IT DOES NOT DUMP. [stated] "the bank doesnt fully empty it releases waves of members to fight as
 * members are lost in groups new waves come out. we dont want to crash or lag a world by having hundreds of xenos spawn
 * at once." So the hive holds a FIELD TARGET rather than a spend-everything order: each cycle tops the garrison back up
 * by at most one wave, which makes losses pull replacements out naturally without a single big materialisation.
 * <p>
 * THE QUEEN'S BANK IS HELD BACK. [stated] "They still have the queen replacement bank stored. only after no one else is
 * left to send does the bank unload these last members." A floor of reserves is untouchable while the hive still has
 * anyone in the field; it is released only in the last stand, when there is nobody else to send.
 */
public final class WarMobilization {

    /** Cadence. Three seconds is fast enough to feel responsive and slow enough to never be the tick cost. */
    private static final long INTERVAL_TICKS = 60L;

    /** How many members a hive at war tries to keep standing. */
    private static final int FIELD_TARGET = 12;

    /** The most that may materialise in one cycle - the anti-lag rule made concrete. */
    private static final int WAVE_SIZE = 4;

    /** Reserves kept for the queen's own guard while anyone else is still fighting. */
    private static final int QUEEN_BANK_FLOOR = 8;

    /** [stated] last stand: "increases their stats by 25% its the hives last push". */
    private static final double LAST_STAND_BONUS = 0.25D;

    private static final ResourceLocation LAST_STAND_MODIFIER =
        ResourceLocation.fromNamespaceAndPath(Alien.MOD_ID, "last_stand");

    /**
     * Heaviest first. [stated] "even raid xenos will try to enter the fight" and "they bring their heaviest hitter to
     * hit the hardest enemies" - the raid castes are in the draw, so a war spends the raid garrison too. HARBINGERS ARE
     * ABSENT ON PURPOSE: [stated] they are "too large to engage in hallway combat" and appear only when an enemy
     * reaches the raid chamber or the queen's chamber, which is throne-defence work, not mobilisation.
     */
    private static final List<TagKey<EntityType<?>>> DRAW_ORDER = List.of(
        AlienEntityTypeTags.PRAETORIANS,
        AlienEntityTypeTags.CRUSHERS,
        AlienEntityTypeTags.PREDALIENS,
        AlienEntityTypeTags.WARRIORS,
        AlienEntityTypeTags.SPITTERS,
        AlienEntityTypeTags.PROWLERS,
        AlienEntityTypeTags.RUNNERS,
        AlienEntityTypeTags.DRONES
    );

    private WarMobilization() {}

    public static boolean shouldFire(long currentTick) {
        return currentTick % INTERVAL_TICKS == 0L;
    }

    public static void run(ServerLevel level, HiveLocation location) {
        if (!location.isAtWar() || !location.isAlive()) {
            return;
        }

        int standing = countHomeGarrison(level, location);
        boolean lastStand = location.isInLastStand();

        // Everyone already out gets the last-stand bonus, not just the newly spawned - the buff is the hive's fury,
        // not a property of how a member arrived.
        if (lastStand) {
            applyLastStandToField(level, location);
        }

        if (standing >= FIELD_TARGET) {
            return;
        }

        // The floor stands until the hive has nothing else. In the last stand with an empty field, it opens.
        int floor = lastStand && standing == 0 ? 0 : QUEEN_BANK_FLOOR;
        int wanted = Math.min(WAVE_SIZE, FIELD_TARGET - standing);
        int sent = 0;

        for (var caste : DRAW_ORDER) {
            if (sent >= wanted) {
                break;
            }
            var variant = location.lineageVariantOrNull();
            var type = variant == null ? null : CasteResolver.entityTypeForCaste(variant, caste);
            if (type == null) {
                continue;
            }
            while (sent < wanted && location.localReserves().getReliableCount() > floor) {
                if (location.localReserves().getReliableCount(type) <= 0) {
                    break;
                }
                var spawned = HiveLoadedSpawner.trySpawnIdentityReserve(level, location, type, musterPos(location));
                if (!(spawned instanceof Mob fighter)) {
                    break;
                }
                ReserveSpawnUtil.markSpawnedFromReserves(fighter);
                if (lastStand) {
                    applyLastStand(fighter);
                }
                sent++;
            }
        }

        if (sent > 0) {
            markLineageDirty(location);
            Alien.LOGGER.debug(
                "War: hive {} sent a wave of {} ({} standing, floor {}{}).",
                location.id(),
                sent,
                standing,
                floor,
                lastStand ? ", LAST STAND" : ""
            );
        }
    }

    /**
     * The hive's fury when the queen falls. [stated] "When the queen dies the hive gets a boost 'last stand' this
     * increases their stats by 25% its the hives last push." Health, damage and speed together - a push, not a damage
     * buff. Transient modifiers, so nothing has to be cleaned up if the war ends or the entity reloads.
     */
    public static void applyLastStand(LivingEntity fighter) {
        bump(fighter.getAttribute(Attributes.ATTACK_DAMAGE));
        bump(fighter.getAttribute(Attributes.MOVEMENT_SPEED));
        var health = fighter.getAttribute(Attributes.MAX_HEALTH);
        if (bump(health)) {
            fighter.setHealth(fighter.getHealth() * (float) (1.0D + LAST_STAND_BONUS));
        }
    }

    private static boolean bump(AttributeInstance attribute) {
        if (attribute == null || attribute.getModifier(LAST_STAND_MODIFIER) != null) {
            return false;
        }
        attribute.addTransientModifier(
            new AttributeModifier(LAST_STAND_MODIFIER, LAST_STAND_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
        );
        return true;
    }

    private static void applyLastStandToField(ServerLevel level, HiveLocation location) {
        for (var members : location.loadedMembersByType().values()) {
            for (var uuid : members) {
                if (level.getEntity(uuid) instanceof LivingEntity member && member.isAlive()) {
                    applyLastStand(member);
                }
            }
        }
    }

    /**
     * The HOME garrison - members standing on our own claimed ground.
     * <p>
     * Counting every loaded member instead would have quietly broken the promise that the mobilizer refills home behind
     * an offensive: a wave that warped into the enemy hive is still a loaded member of this location, so the hive would
     * see a full garrison while its ground stood empty and never replace anyone.
     */
    private static int countHomeGarrison(ServerLevel level, HiveLocation location) {
        int standing = 0;
        for (var entry : location.loadedMembersByType().entrySet()) {
            if (!entry.getKey().is(AlienEntityTypeTags.XENOMORPHS)) {
                continue;
            }
            for (var uuid : entry.getValue()) {
                if (
                    level.getEntity(uuid) instanceof LivingEntity member
                        && member.isAlive()
                        && location.claimedChunks().contains(member.chunkPosition())
                ) {
                    standing++;
                }
            }
        }
        return standing;
    }

    /** Where a wave forms up: the hive floor at its centre, the same anchor the rest of the hive spawns against. */
    private static BlockPos musterPos(HiveLocation location) {
        var centre = location.centerPos();
        return new BlockPos(centre.getX(), location.hiveFloorY() + 1, centre.getZ());
    }

    /** Registry-reachable mutation: mark it or an unloaded hive's spend is never written. */
    private static void markLineageDirty(HiveLocation location) {
        var faction = Alien.MOD.factions().get(location.lineageFactionId());
        if (
            faction != null
                && faction.data() instanceof com.alien.common.gameplay.hive.faction.LineageFactionData lineage
        ) {
            lineage.markDirty();
        }
    }
}
