package com.alien.common.gameplay.hive.war;

import com.alien.Alien;
import com.alien.AlienResources;
import com.alien.common.gameplay.hive.economy.CastePopulation;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.growth.HiveLocationClaims;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.alien.common.gameplay.hive.id.HiveLocationIds;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.tick.HiveLocationLoadedTickTask;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.faction.v1.RelationshipState;
import com.blib.api.common.territory.v1.TerritoryContest;
import com.blib.api.common.territory.v1.TerritoryContestListener;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

/**
 * Alien-side territory war behavior layered on top of BLib's generic contest system. Hives are territorial rivals
 * unless their lineages have submitted to the same empress.
 */
public final class AlienTerritoryWarSystem implements TerritoryContestListener {

    private static final AlienTerritoryWarSystem LISTENER = new AlienTerritoryWarSystem();

    private static final ResourceLocation REASON = AlienResources.location("territory_war");

    private static final int[][] CARDINAL_OFFSETS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    /** [stated] "there is a period of 3 mc days before hostilities start" when two hives share a slab band. */
    private static final long SLAB_GRACE_TICKS = 3L * 24000L;

    /** [stated] "each hive must have a minimum of 50 members to start the hostilities" when only territory overlaps. */
    private static final int MINIMUM_MEMBERS_FOR_LEVELLED_WAR = 50;

    /** [stated] "after two extensions let the war commence" - a levelled pact waits nine days at the very most. */
    private static final int MAX_LEVELLED_WAR_EXTENSIONS = 2;

    /** How far behind a hive must fall before it downs tools - the gap that stops the freeze flapping. */
    private static final int BUILD_FREEZE_MARGIN = 3;

    private AlienTerritoryWarSystem() {}

    public static void initialize() {
        Alien.MOD.territory().contests().registerPowerProvider(AlienTerritoryWarSystem::contestPower);
        Alien.MOD.territory().contests().registerListener(LISTENER);
    }

    public static void scanAndApply(MinecraftServer server) {
        var config = HiveLocationRegistry.INSTANCE.config();
        var maxStarts = Math.max(1, Math.min(8, config.maxClaimsPerScan()));
        var started = 0;

        for (var location : new ArrayList<>(HiveLocationRegistry.INSTANCE.all())) {
            if (started >= maxStarts) {
                return;
            }
            if (!canProjectWar(location)) {
                continue;
            }

            var level = server.getLevel(location.dimension());
            if (level == null) {
                continue;
            }

            if (tryStartBorderContest(level, location)) {
                started++;
            }
        }
    }

    public static boolean areAlienLineagesEnemies(
        com.alien.common.gameplay.entity.living.alien.Alien first,
        com.alien.common.gameplay.entity.living.alien.Alien second
    ) {
        var firstLineage = lineageFor(first.getUUID());
        var secondLineage = lineageFor(second.getUUID());

        if (firstLineage == null || secondLineage == null || firstLineage.equals(secondLineage)) {
            return false;
        }

        // ⚠⚠ PERF: RESOLVE EACH LineageFactionData EXACTLY ONCE. This predicate runs PER CANDIDATE PER SENSE SCAN -
        // it is one of the hottest paths in the mod. shareEmpressAuthority used to look both lineages up itself, and
        // the remembrance check then looked them BOTH up again (once per direction), so a single rival pair cost four
        // faction lookups where two will do. Hoisted here and passed down; the exemptions are unchanged.
        var firstData = lineageData(firstLineage);
        var secondData = lineageData(secondLineage);

        return !shareEmpressAuthority(firstData, secondData)
            && !withinRemembrance(first.level(), firstLineage, firstData, secondLineage, secondData);
    }

    /**
     * ⭐⭐ THE PERIOD OF REMEMBRANCE. [stated] "i would say theres a period of rememberance where they are nuetral to
     * allow the daughter to leave and found".
     * <p>
     * A separating daughter mints her OWN lineage, and the ordinary rule is that same-strain hives of different
     * lineages are at war. So the instant she broke away, her mother's hive read her - and her escort - as rivals and
     * attacked. That is what produced "the members freed her from the chains but then went to attacking her": the
     * damage drove the mother past her rouse threshold and unbound her from her own ovipositor.
     * </p>
     * <p>
     * ⚠ MUTUAL AND DIRECTIONLESS. Checked BOTH ways round, because the pair arrives in whatever order the two aliens
     * happened to be passed in, and a truce that only held in one direction would let one side beat on the other.
     * </p>
     * <p>
     * ⚠ IT EXPIRES ON PURPOSE. He asked for a PERIOD, not an alliance - the daughter gets time to walk out and dig in,
     * and after that the hive-war rule resumes and they are rivals like any other pair of lineages. The permanent
     * version of this already exists and is deliberately harder to get: {@link #shareEmpressAuthority}, where an
     * EMPRESS unifies lineages under one authority.
     * </p>
     */
    private static boolean withinRemembrance(
        net.minecraft.world.level.Level level,
        ResourceLocation firstLineage,
        @org.jetbrains.annotations.Nullable LineageFactionData firstData,
        ResourceLocation secondLineage,
        @org.jetbrains.annotations.Nullable LineageFactionData secondData
    ) {
        var now = level.getGameTime();
        return isChildWithinRemembrance(firstData, secondLineage, now)
            || isChildWithinRemembrance(secondData, firstLineage, now);
    }

    private static boolean isChildWithinRemembrance(
        @org.jetbrains.annotations.Nullable LineageFactionData child,
        ResourceLocation parentLineage,
        long now
    ) {
        return child != null
            && parentLineage.equals(child.parentLineageId())
            && now < child.separationTick() + REMEMBRANCE_TICKS;
    }

    public static boolean areRivalLineages(ResourceLocation firstLineage, ResourceLocation secondLineage) {
        return !firstLineage.equals(secondLineage)
            && !shareEmpressAuthority(lineageData(firstLineage), lineageData(secondLineage));
    }

    private static boolean tryStartBorderContest(ServerLevel level, HiveLocation attacker) {
        for (var ownedChunk : attacker.claimedChunks()) {
            for (var offset : CARDINAL_OFFSETS) {
                var targetChunk = new ChunkPos(ownedChunk.x + offset[0], ownedChunk.z + offset[1]);

                if (attacker.claimedChunks().contains(targetChunk)) {
                    continue;
                }

                var defender = findDefender(level, attacker, targetChunk);
                if (defender == null) {
                    continue;
                }

                var attackerId = attacker.id().value();
                if (Alien.MOD.territory().contests().getContest(level, targetChunk, attackerId, defender).isPresent()) {
                    return false;
                }

                return Alien.MOD.territory().contests().startContest(level, targetChunk, attackerId, defender, REASON);
            }
        }

        return false;
    }

    private static ResourceLocation findDefender(ServerLevel level, HiveLocation attacker, ChunkPos targetChunk) {
        var playerOwner = Alien.MOD.territory().getPlayerClaimOwner(level, targetChunk);
        if (playerOwner != null) {
            var playerClaim = Alien.MOD.territory().getPlayerClaimFactionId(playerOwner);
            makeHostile(attacker.id().value(), playerClaim);
            makeHostile(attacker.lineageFactionId(), playerClaim);
            return playerClaim;
        }

        for (var claimantId : Alien.MOD.territory().getClaimants(level, targetChunk)) {
            if (!HiveLocationIds.isHiveLocationId(claimantId) || claimantId.equals(attacker.id().value())) {
                continue;
            }

            var defender = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(claimantId));
            if (defender == null || !defender.isAlive() || defender.isInhibited()) {
                continue;
            }

            var relationship = areRivalLineages(attacker.lineageFactionId(), defender.lineageFactionId())
                ? RelationshipState.HOSTILE
                : RelationshipState.ALLIED;
            Alien.MOD.factions().setRelationship(attacker.lineageFactionId(), defender.lineageFactionId(), relationship);
            Alien.MOD.factions().setRelationship(attacker.id().value(), defender.id().value(), relationship);

            if (relationship == RelationshipState.HOSTILE) {
                return defender.id().value();
            }
        }

        return null;
    }

    private static boolean canProjectWar(HiveLocation location) {
        if (!location.isAlive() || location.isInhibited() || location.claimedChunks().isEmpty()) {
            return false;
        }

        var faction = Alien.MOD.factions().get(location.lineageFactionId());
        if (faction == null || !(faction.data() instanceof LineageFactionData lineage) || !lineage.isAlive()) {
            return false;
        }

        return lineage.empressId() != null
            || countTaggedMembers(location, AlienEntityTypeTags.QUEENS) > 0
            || location.localReserves().getReliableCount() >= HiveLocationRegistry.INSTANCE.config().hiveSpawnerMinimumLoadedXenomorphs();
    }

    private static int contestPower(ServerLevel level, ChunkPos chunk, ResourceLocation factionId) {
        if (HiveLocationIds.isHiveLocationId(factionId)) {
            return hivePower(level, chunk, factionId);
        }

        var owner = Alien.MOD.territory().getPlayerClaimOwner(level, chunk);
        if (owner != null && Alien.MOD.territory().getPlayerClaimFactionId(owner).equals(factionId)) {
            return playerClaimPower(level, chunk, owner);
        }

        return 0;
    }

    private static int hivePower(ServerLevel level, ChunkPos chunk, ResourceLocation locationFactionId) {
        var location = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(locationFactionId));
        if (location == null || !location.isAlive() || location.isInhibited() || !location.dimension().equals(level.dimension())) {
            return 0;
        }

        var loadedPresence = countXenomorphsIn(level, chunk, location.lineageFactionId()) * 6;
        var canProject = location.claimedChunks().contains(chunk) || hasCardinalNeighborClaim(location, chunk);
        if (!canProject) {
            return loadedPresence;
        }

        var reservePressure = Math.max(1, location.localReserves().getReliableCount() / 8);
        var territoryPressure = Math.max(1, location.claimedChunks().size() / 16);
        var queenPressure = countTaggedMembers(location, AlienEntityTypeTags.QUEENS) > 0 ? 4 : 0;

        return loadedPresence + reservePressure + territoryPressure + queenPressure;
    }

    private static int playerClaimPower(ServerLevel level, ChunkPos chunk, UUID owner) {
        var power = 2;

        for (var player : level.players()) {
            if (!owner.equals(player.getUUID())) {
                continue;
            }

            var playerChunk = player.chunkPosition();
            var distance = Math.max(Math.abs(playerChunk.x - chunk.x), Math.abs(playerChunk.z - chunk.z));
            if (distance == 0) {
                power += 14;
            } else if (distance <= 1) {
                power += 6;
            }
        }

        return power;
    }

    private static int countXenomorphsIn(ServerLevel level, ChunkPos chunk, ResourceLocation lineageId) {
        var minX = chunk.getMinBlockX();
        var minZ = chunk.getMinBlockZ();
        var maxX = chunk.getMaxBlockX();
        var maxZ = chunk.getMaxBlockZ();
        var box = new AABB(minX, level.getMinBuildHeight(), minZ, maxX + 1, level.getMaxBuildHeight(), maxZ + 1);
        var count = 0;

        for (var entity : level.getEntitiesOfClass(com.alien.common.gameplay.entity.living.alien.Alien.class, box)) {
            if (!entity.getType().is(AlienEntityTypeTags.XENOMORPHS)) {
                continue;
            }
            if (Alien.MOD.factions().getFactionIds(entity.getUUID()).contains(lineageId)) {
                count++;
            }
        }

        return count;
    }

    private static int countTaggedMembers(HiveLocation location, net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> tag) {
        var count = 0;

        for (var entry : location.loadedMembersByType().entrySet()) {
            if (entry.getKey().is(tag)) {
                count += entry.getValue().size();
            }
        }

        count += location.localReserves().getReliableCountMatching(type -> type.is(tag));
        return count;
    }

    private static boolean hasCardinalNeighborClaim(HiveLocation location, ChunkPos chunk) {
        for (var offset : CARDINAL_OFFSETS) {
            if (location.claimedChunks().contains(new ChunkPos(chunk.x + offset[0], chunk.z + offset[1]))) {
                return true;
            }
        }
        return false;
    }

    private static void makeHostile(ResourceLocation first, ResourceLocation second) {
        Alien.MOD.factions().setRelationship(first, second, RelationshipState.HOSTILE);
    }

    /**
     * ⭐ THE DIAL: how long a mother and her freshly separated daughter stay neutral. THREE MINECRAFT DAYS (72,000
     * ticks, about an hour of real time) - long enough for the daughter to travel clear, carve her core and get a first
     * brood standing, which is what the truce is FOR. <b>Raise it</b> if daughters are still being cut down
     * mid-migration; <b>lower it</b> if rival hives feel too slow to turn on each other.
     */
    private static final long REMEMBRANCE_TICKS = 3L * 24_000L;

    private static boolean shareEmpressAuthority(
        @org.jetbrains.annotations.Nullable LineageFactionData first,
        @org.jetbrains.annotations.Nullable LineageFactionData second
    ) {
        return first != null
            && second != null
            && first.empressId() != null
            && Objects.equals(first.empressId(), second.empressId());
    }

    private static LineageFactionData lineageData(ResourceLocation lineageId) {
        var faction = Alien.MOD.factions().get(lineageId);
        if (faction == null || !(faction.data() instanceof LineageFactionData lineage) || !lineage.isAlive()) {
            return null;
        }
        return lineage;
    }

    private static ResourceLocation lineageFor(UUID entityId) {
        // Prefer the lineage the entity's LOCATION membership names. A membership set is unordered, so an entity
        // that ended up in two lineages (a founder who didn't shed her old one, a worker mid-migration) would
        // otherwise resolve by iteration order and could read as an enemy of its own hive. A location membership
        // is unambiguous - it points at exactly one lineage - so it wins over a bare lineage-faction membership.
        ResourceLocation lineageFallback = null;
        for (var factionId : Alien.MOD.factions().getFactionIds(entityId)) {
            if (HiveLocationIds.isHiveLocationId(factionId)) {
                var location = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(factionId));
                if (location != null) {
                    return location.lineageFactionId();
                }
            } else if (lineageFallback == null && LineageIds.isLineageId(factionId)) {
                lineageFallback = factionId; // remember, but keep looking for a location membership
            }
        }

        return lineageFallback;
    }

    /** True when both sides are hive locations locked in the same war - the chunk is a warzone, not a scoreboard. */
    private static boolean isFrozenByWar(ResourceLocation winner, ResourceLocation loser) {
        var winnerLocation = hiveLocation(winner);
        var loserLocation = hiveLocation(loser);
        // PENDING counts too: ground the two have already committed to fight over should not change hands on a power
        // score during the grace period either. It is disputed from the moment the pact forms.
        return winnerLocation != null
            && loserLocation != null
            && (winnerLocation.isAtWarWith(loser) || winnerLocation.pendingWars().containsKey(loser));
    }

    /**
     * Flags this location's lineage for saving.
     * <p>
     * REQUIRED for everything in this class. The loaded tick marks a lineage dirty once per tick, which covers every
     * mutation made while somebody is standing near the hive - but war state is driven from the REGISTRY sweep, which
     * reaches hives nobody has loaded. Those mutations must mark themselves or they are never written, and a war
     * declared, extended, concluded or lost while the hive was unloaded would silently vanish on restart.
     */
    private static void markLineageDirty(HiveLocation location) {
        var faction = Alien.MOD.factions().get(location.lineageFactionId());
        if (faction != null && faction.data() instanceof LineageFactionData lineage) {
            lineage.markDirty();
        }
    }

    /** The live location behind a faction id, or null for a player claim / dead location. */
    @org.jetbrains.annotations.Nullable
    private static HiveLocation hiveLocation(ResourceLocation factionId) {
        if (!HiveLocationIds.isHiveLocationId(factionId)) {
            return null;
        }
        var location = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(factionId));
        return location != null && location.isAlive() ? location : null;
    }

    /**
     * Resolves open wars. [stated] "The war ends when all members are dead excluding banked eggs those just vanish when
     * the hive has no more members." A side is SPENT when it holds no loaded members and its reserves are empty; a side
     * whose location has died is spent by definition. Runs on the registry scan, so a war between two hives nobody is
     * watching still concludes.
     */
    public static void tickWars(MinecraftServer server) {
        tickPendingWars(server);

        // One simulated round per sweep at most, claimed here rather than tested per pair - otherwise the first
        // pair would consume the round and every other war would sit still.
        boolean simulationRoundDue = WarSimulation.claimRound(server.overworld().getGameTime());

        for (var location : new ArrayList<>(HiveLocationRegistry.INSTANCE.all())) {
            if (!location.isAtWar()) {
                continue;
            }
            // The preparation truce is over the moment the fighting starts - a hive at war builds if it wants to.
            if (location.isBuildFrozenForWarPrep()) {
                location.setBuildFrozenForWarPrep(false);
            }
            var level = server.getLevel(location.dimension());
            for (var enemyId : new ArrayList<>(location.warEnemies())) {
                var enemy = hiveLocation(enemyId);
                if (enemy == null) {
                    // The enemy died (or was removed) while the war ran - this side simply won.
                    concludeWar(level, location, enemyId, location.id().value());
                    continue;
                }
                // [stated] the queen falling is the trigger for the last push, not the end of the war.
                raiseLastStandIfQueenless(server, location);
                raiseLastStandIfQueenless(server, enemy);

                boolean weAreSpent = isSpent(server, location);
                boolean theyAreSpent = isSpent(server, enemy);

                // [stated] "when the chunks unload the war continues simulated until a victor is decided." If either
                // side is not loaded the fight cannot play out in the world, so it plays out on paper instead - and
                // only THEN can an unloaded hive be judged spent.
                if (!weAreSpent && !theyAreSpent && !bothLoaded(server, location, enemy)) {
                    if (
                        simulationRoundDue
                            && WarSimulation.fightRound(server.overworld().getRandom(), location, enemy)
                    ) {
                        weAreSpent = isSimulationSpent(location);
                        theyAreSpent = isSimulationSpent(enemy);
                    }
                }

                if (!weAreSpent && !theyAreSpent) {
                    continue;
                }
                // Both spent at once is a mutual bleed-out; nobody is named the victor.
                var victor = weAreSpent == theyAreSpent ? null : (weAreSpent ? enemyId : location.id().value());
                // [stated] the beaten hive is excluded from replacement too - a successor crowned into a hive with
                // nothing left just dies and wastes the slot. Both sides are marked in a mutual bleed-out.
                if (weAreSpent) {
                    location.markLostAWar();
                    markLineageDirty(location);
                }
                if (theyAreSpent) {
                    enemy.markLostAWar();
                    markLineageDirty(enemy);
                }
                concludeWar(level, location, enemyId, victor);
            }
        }
    }

    /**
     * A hive with nothing left to send. Loaded members and the reserve bank both count - banked EGGS deliberately do
     * not, per [stated] "excluding banked eggs those just vanish when the hive has no more members".
     */
    /** Both sides actually present in the world - the only case the loaded end condition may judge. */
    private static boolean bothLoaded(MinecraftServer server, HiveLocation first, HiveLocation second) {
        return isLoaded(server, first) && isLoaded(server, second);
    }

    private static boolean isLoaded(MinecraftServer server, HiveLocation location) {
        var level = server.getLevel(location.dimension());
        return level != null && HiveLocationLoadedTickTask.hasLoadedClaimedChunk(level, location);
    }

    /** The paper version of spent: nothing tracked and nothing banked. Used only after a simulated round. */
    private static boolean isSimulationSpent(HiveLocation location) {
        return !location.isAlive()
            || (CastePopulation.totalTrackedPopulation(location) <= 0
                && location.localReserves().getReliableCount() <= 0);
    }

    /**
     * [stated] "When the queen dies the hive gets a boost 'last stand'... its the hives last push." Raised the first
     * time a hive at war is seen queenless, and only while it is loaded enough to know - a hive nobody can see is not
     * declared queenless on the strength of an empty entity list.
     */
    private static void raiseLastStandIfQueenless(MinecraftServer server, HiveLocation location) {
        if (location.isInLastStand() || !isLoaded(server, location)) {
            return;
        }
        if (CastePopulation.countCaste(location, AlienEntityTypeTags.QUEENS) > 0) {
            return;
        }
        location.setLastStand(true);
        markLineageDirty(location);
        Alien.LOGGER.info("War: hive {} has lost its queen — LAST STAND.", location.id());
    }

    private static boolean isSpent(MinecraftServer server, HiveLocation location) {
        if (!location.isAlive()) {
            return true;
        }
        // AN UNLOADED HIVE IS NOT A DEFEATED HIVE. loadedMembersByType only holds members whose entities are
        // currently ticking, so a hive nobody is standing near reports ZERO of them - and a hive that has just put
        // its whole garrison in the field has an empty bank as well. Judged on those two numbers alone, an unwatched
        // hive would lose its war by accident and be marked a permanent loss, barred from ever crowning again. Until
        // the abstract wargame lands, an unloaded side simply is not judged.
        var level = server.getLevel(location.dimension());
        if (level == null || !HiveLocationLoadedTickTask.hasLoadedClaimedChunk(level, location)) {
            return false;
        }
        for (var members : location.loadedMembersByType().values()) {
            if (!members.isEmpty()) {
                return false;
            }
        }
        return location.localReserves().getReliableCount() <= 0;
    }

    /** Clears the pairing on both sides and announces the outcome. The frozen chunks go back to ordinary contests. */
    private static void concludeWar(
        @org.jetbrains.annotations.Nullable ServerLevel level,
        HiveLocation location,
        ResourceLocation enemyId,
        @org.jetbrains.annotations.Nullable ResourceLocation victorId
    ) {
        location.removeWarEnemy(enemyId);
        // A survivor does not stay buffed once the fighting stops.
        if (!location.isAtWar()) {
            location.setLastStand(false);
        }
        markLineageDirty(location);
        var enemy = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(enemyId));
        if (enemy != null) {
            enemy.removeWarEnemy(location.id().value());
            markLineageDirty(enemy);
        }

        // [stated] nobody is left standing in a hive that no longer has a war to fight - both sides pull their
        // survivors out of the other's ground before the pairing is forgotten. Done HERE because this is the last
        // moment either side still knows who the enemy was.
        if (level != null) {
            WarOffensive.recallFrom(level, location, enemy);
            if (enemy != null) {
                WarOffensive.recallFrom(level, enemy, location);
            }
        }

        if (level != null) {
            broadcast(
                level,
                "<<Attention>> Hostilities at " + coordsOf(warAnchorPos(location, enemy)) + " have concluded",
                net.minecraft.ChatFormatting.GREEN,
                AlienSoundEvents.BROADCAST_WAR_ALL_CLEAR.get()
            );
        }
        Alien.LOGGER.info(
            "Alien hive war ENDED between {} and {} — victor: {}",
            location.id(),
            enemyId,
            victorId == null ? "none (mutual)" : victorId
        );
    }

    /**
     * A hive war is world news, not local news: every player on the server gets the line and the morse broadcast,
     * wherever they are. Range-limiting it would have hidden exactly the warning it exists to give - the point is that
     * you learn the coordinates BEFORE you wander into a warzone.
     */
    private static void broadcast(
        ServerLevel level,
        String text,
        net.minecraft.ChatFormatting colour,
        net.minecraft.sounds.SoundEvent sound
    ) {
        var line = net.minecraft.network.chat.Component
            .literal(text)
            .withStyle(colour, net.minecraft.ChatFormatting.ITALIC);
        for (var player : level.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(line);
            player.playNotifySound(sound, net.minecraft.sounds.SoundSource.MASTER, 1F, 1F);
        }
    }

    /**
     * The hive whose coordinates BOTH announcements quote, chosen the same way at declaration and at conclusion so the
     * all-clear names the place the warning named.
     * <p>
     * It cannot simply be "the location that noticed", because a war is symmetric and either side may be the one that
     * ticks the conclusion. Ordering the two ids gives both ends the same answer with nothing persisted. If the chosen
     * side has already left the registry by conclusion time, the survivor stands in - the war is over either way, and a
     * slightly different coordinate beats no all-clear at all.
     */
    private static BlockPos warAnchorPos(
        @org.jetbrains.annotations.Nullable HiveLocation first,
        @org.jetbrains.annotations.Nullable HiveLocation second
    ) {
        if (first == null) {
            return second == null ? BlockPos.ZERO : second.centerPos();
        }
        if (second == null) {
            return first.centerPos();
        }
        return first.id().value().toString().compareTo(second.id().value().toString()) <= 0
            ? first.centerPos()
            : second.centerPos();
    }

    private static String coordsOf(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    @Override
    public void onContestStarted(ServerLevel level, TerritoryContest contest) {
        if (!REASON.equals(contest.reason())) {
            return;
        }

        // The other half of the frozen-border silence: a re-opened contest is the SAME dispute, not a new one.
        if (isFrozenByWar(contest.attacker(), contest.defender())) {
            Alien.LOGGER.debug(
                "Alien territory contest re-opened (frozen warzone): chunk=[{}, {}], dimension={}",
                contest.chunkX(),
                contest.chunkZ(),
                contest.dimension()
            );
            return;
        }

        Alien.LOGGER.info(
            "Alien territory war started: attacker={}, defender={}, chunk=[{}, {}], dimension={}, reason={}",
            contest.attacker(),
            contest.defender(),
            contest.chunkX(),
            contest.chunkZ(),
            contest.dimension(),
            contest.reason()
        );

        // A contested border between rivals commits them to war - but NOT to fighting yet. [stated] hives that build
        // into the same band get three days to prepare; hives sharing ground at different levels wait until both can
        // field fifty. The pact is recorded here and matures in tickWars.
        openPendingWar(level, contest.attacker(), contest.defender());
    }

    /**
     * Records the pact and the terms of the wait. Which rule applies is decided ONCE, when the pairing forms: if the
     * two hives' slab bands overlap in Y where their ground overlaps in XZ they are building into each other and the
     * three-day clock starts; if they only share ground at different levels there is no clock, just the fifty-member
     * threshold. Already-pending and already-fighting pairs are left alone.
     */
    private static void openPendingWar(ServerLevel level, ResourceLocation firstId, ResourceLocation secondId) {
        var first = hiveLocation(firstId);
        var second = hiveLocation(secondId);
        if (first == null || second == null) {
            return; // player claims and dead locations never enter a hive war
        }
        if (first.isAtWarWith(secondId) || first.pendingWars().containsKey(secondId)) {
            return;
        }
        if (empressRestraintHolds(first, second)) {
            return;
        }

        long now = level.getGameTime();
        boolean slabsMeet = slabsIntersect(first, second);
        first.pendingWars().put(secondId, new HiveLocation.PendingWar(now, slabsMeet, 0));
        second.pendingWars().put(firstId, new HiveLocation.PendingWar(now, slabsMeet, 0));
        markLineageDirty(first);
        markLineageDirty(second);
        Alien.LOGGER.info(
            "Alien hive war PENDING between {} and {} — {}",
            firstId,
            secondId,
            slabsMeet
                ? "slabs intersect: " + (SLAB_GRACE_TICKS / 24000L) + " day grace, weaker side stops building"
                : "territory overlaps at different levels: both sides must field " + MINIMUM_MEMBERS_FOR_LEVELLED_WAR
        );
    }

    /**
     * [stated] "In terms of same strain if the hive is empress influenced it follows the same adoption rules. Only if
     * the cap of 8 hives is full would an empress influenced hive engage in the war. If its a different strain then it
     * activates as normal."
     * <p>
     * An empress does not spend her own strain on a fight she could end by taking them in - while her empire still has
     * a seat free, a same-strain rival is a lineage to absorb, not an enemy. The restraint dies with the last free
     * seat, and it never applies across strains: a foreign strain is a war whatever the seat count says.
     */
    private static boolean empressRestraintHolds(HiveLocation first, HiveLocation second) {
        if (!java.util.Objects.equals(first.lineageVariantOrNull(), second.lineageVariantOrNull())) {
            return false; // different strain - normal activation
        }
        return hasRoomUnderEmpress(first) || hasRoomUnderEmpress(second);
    }

    /**
     * Is this hive fighting ANOTHER empire - two crowns, not one?
     * <p>
     * [stated] "If the war is between two different empress hive territories... They also will not do the usual hive
     * queen replacement if the hive dies", clarified as: "the empress can rejuvenate a hive with no pending queen
     * twice... just want to make sure in the case the firewall hive dies the empress will count it as a loss and not
     * try to replace." The rescue transfer is that rejuvenation, and it is exactly what would otherwise undo a war loss
     * - so in an empire war a dead hive stays dead and counts against her.
     */
    /**
     * No hive crowns a queen while a war is on, and none crowns one after losing it.
     * <p>
     * [stated] "crowning a new queen during the war wouldn't make much sense and costs resources so dont let it", and
     * [stated] "if a hive loses a war then it would exclude it as well otherwise the new smaller hive would die and
     * waste a slot." Both halves apply to ORDINARY queen-vs-queen wars, not only empire ones: the firewall fund and the
     * jelly are needed for the fighting, and a successor seated mid-war only inherits a war already going badly. A
     * PENDING pact is deliberately NOT covered - the grace period is exactly when a hive should be putting itself back
     * together.
     */
    public static boolean isExcludedFromQueenReplacement(HiveLocation location) {
        return location.hasLostAWar() || location.isAtWar();
    }

    public static boolean isInEmpressWar(HiveLocation location) {
        var ourEmpress = empressIdOf(location);
        if (ourEmpress == null || !location.isAtWar()) {
            return false;
        }
        for (var enemyId : location.warEnemies()) {
            var enemy = hiveLocation(enemyId);
            if (enemy == null) {
                continue;
            }
            var theirEmpress = empressIdOf(enemy);
            if (theirEmpress != null && !theirEmpress.equals(ourEmpress)) {
                return true;
            }
        }
        return false;
    }

    /** The empress this hive answers to, or null when its lineage has none. */
    @org.jetbrains.annotations.Nullable
    private static java.util.UUID empressIdOf(HiveLocation location) {
        var faction = Alien.MOD.factions().get(location.lineageFactionId());
        if (
            faction == null
                || !(faction.data() instanceof com.alien.common.gameplay.hive.faction.LineageFactionData lineage)
        ) {
            return null;
        }
        return lineage.empressId();
    }

    /** True when this hive answers to an empress whose empire is still under its hive cap. */
    private static boolean hasRoomUnderEmpress(HiveLocation location) {
        var faction = Alien.MOD.factions().get(location.lineageFactionId());
        if (
            faction == null
                || !(faction.data() instanceof com.alien.common.gameplay.hive.faction.LineageFactionData lineage)
                || lineage.empressId() == null
        ) {
            return false;
        }
        var config = HiveLocationRegistry.INSTANCE.config();
        int cap = Math.min(config.maxLocationsPerLineage(), config.maxLocationsUnderEmpress());
        return lineage.activeLocationCount() < cap;
    }

    /**
     * Is there still something to fight over? True when the two hives share a claimed chunk or hold chunks that touch.
     * Cheap enough for the contest cadence: one set lookup per neighbour of the smaller claim set.
     */
    private static boolean stillDisputed(HiveLocation first, HiveLocation second) {
        if (!first.dimension().equals(second.dimension())) {
            return false;
        }
        var smaller = first.claimedChunks().size() <= second.claimedChunks().size()
            ? first.claimedChunks()
            : second.claimedChunks();
        var larger = smaller == first.claimedChunks() ? second.claimedChunks() : first.claimedChunks();
        for (var chunk : smaller) {
            for (var dx = -1; dx <= 1; dx++) {
                for (var dz = -1; dz <= 1; dz++) {
                    if (larger.contains(new ChunkPos(chunk.x + dx, chunk.z + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Do these two hives occupy the same space, or merely the same map? [stated] "contested chunks aren't always
     * directly horizontal with hive slabs" - two hives can share claimed ground with a hundred blocks of stone between
     * their floors. Slabs meet only when a chunk is claimed by BOTH and their bands overlap in Y.
     */
    private static boolean slabsIntersect(HiveLocation first, HiveLocation second) {
        if (!first.dimension().equals(second.dimension())) {
            return false;
        }
        boolean bandsOverlap = first.hiveFloorY() < second.hiveCeilingY()
            && second.hiveFloorY() < first.hiveCeilingY();
        if (!bandsOverlap) {
            return false;
        }
        for (var chunk : first.claimedChunks()) {
            if (second.claimedChunks().contains(chunk)) {
                return true;
            }
        }
        // No shared claim yet, but a shared BUILD BOX is the same collision one piece later.
        for (var chunk : first.structurePieceByChunk().keySet()) {
            if (second.structurePieceByChunk().containsKey(chunk)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Advances every pending pact and applies its terms. Slab pacts: the weaker hive downs tools for the duration, and
     * hostilities open when the grace expires. Levelled pacts: both hives build on, and hostilities open the moment
     * both can field the minimum. A pact whose other side has died is dropped.
     */
    private static void tickPendingWars(MinecraftServer server) {
        for (var location : new ArrayList<>(HiveLocationRegistry.INSTANCE.all())) {
            if (location.pendingWars().isEmpty()) {
                continue;
            }
            var level = server.getLevel(location.dimension());
            boolean holdingBack = false;

            for (var entry : new ArrayList<>(location.pendingWars().entrySet())) {
                var enemyId = entry.getKey();
                var pact = entry.getValue();
                var enemy = hiveLocation(enemyId);
                if (enemy == null) {
                    location.pendingWars().remove(enemyId);
                    continue;
                }

                // The quarrel can end before the war starts - a claim released, a frontier that moved. Without this
                // the pact (and the loser's building freeze, and the frozen border) would outlive the dispute that
                // created it, forever.
                if (!stillDisputed(location, enemy)) {
                    location.pendingWars().remove(enemyId);
                    enemy.pendingWars().remove(location.id().value());
                    markLineageDirty(location);
                    markLineageDirty(enemy);
                    Alien.LOGGER.info(
                        "Alien hive war pact between {} and {} DISSOLVED — their ground no longer touches.",
                        location.id(),
                        enemyId
                    );
                    continue;
                }

                if (pact.slabsMeet()) {
                    // [stated] the weaker hive stops building and spends the grace growing instead.
                    int ours = CastePopulation.totalTrackedPopulation(location);
                    int theirs = CastePopulation.totalTrackedPopulation(enemy);
                    // Hysteresis: a hive already down tools keeps them down until it has actually CAUGHT UP, and a
                    // building hive only stops once it is clearly behind. Without the gap, two hives within a member
                    // of each other would halt and resume every cycle and log a line each time.
                    holdingBack |= location.isBuildFrozenForWarPrep()
                        ? ours < theirs
                        : ours < theirs - BUILD_FREEZE_MARGIN;

                    if (level != null && level.getGameTime() - pact.sinceTick() >= SLAB_GRACE_TICKS) {
                        location.pendingWars().remove(enemyId);
                        enemy.pendingWars().remove(location.id().value());
                        declareWar(level, location.id().value(), enemyId);
                    }
                    continue;
                }

                // [stated] levelled overlap: build all you like, but nobody starts until both can field fifty.
                if (level == null) {
                    continue;
                }
                boolean bothReady =
                    CastePopulation.totalTrackedPopulation(location) >= MINIMUM_MEMBERS_FOR_LEVELLED_WAR
                        && CastePopulation.totalTrackedPopulation(enemy) >= MINIMUM_MEMBERS_FOR_LEVELLED_WAR;
                if (bothReady) {
                    location.pendingWars().remove(enemyId);
                    enemy.pendingWars().remove(location.id().value());
                    declareWar(level, location.id().value(), enemyId);
                    continue;
                }

                // Still short of fifty when the window runs out. [stated] "extend the pact timeframe until 50 is
                // reached extend it by 3 more days after two extensions let the war commence" - so the threshold is
                // a target, not a veto. Two reprieves of three days each, then they fight with whatever they have,
                // which stops a hive that can never reach fifty from freezing that border forever.
                if (level.getGameTime() - pact.sinceTick() < SLAB_GRACE_TICKS) {
                    continue;
                }
                if (pact.extensions() >= MAX_LEVELLED_WAR_EXTENSIONS) {
                    location.pendingWars().remove(enemyId);
                    enemy.pendingWars().remove(location.id().value());
                    Alien.LOGGER.info(
                        "Alien hive war between {} and {} commences UNDER STRENGTH — {} extensions spent and neither "
                            + "side reached {}.",
                        location.id(),
                        enemyId,
                        MAX_LEVELLED_WAR_EXTENSIONS,
                        MINIMUM_MEMBERS_FOR_LEVELLED_WAR
                    );
                    declareWar(level, location.id().value(), enemyId);
                    continue;
                }

                // Another three days to muster. Written to BOTH sides so the pair's clocks never drift apart.
                var extended = new HiveLocation.PendingWar(
                    level.getGameTime(),
                    pact.slabsMeet(),
                    pact.extensions() + 1
                );
                location.pendingWars().put(enemyId, extended);
                enemy.pendingWars().put(location.id().value(), extended);
                markLineageDirty(location);
                markLineageDirty(enemy);
                Alien.LOGGER.info(
                    "Alien hive war between {} and {} POSTPONED — extension {}/{}, still mustering toward {} members.",
                    location.id(),
                    enemyId,
                    extended.extensions(),
                    MAX_LEVELLED_WAR_EXTENSIONS,
                    MINIMUM_MEMBERS_FOR_LEVELLED_WAR
                );
            }

            if (location.isBuildFrozenForWarPrep() != holdingBack) {
                location.setBuildFrozenForWarPrep(holdingBack);
                markLineageDirty(location);
                Alien.LOGGER.info(
                    "Hive {}: construction {} — {}",
                    location.id(),
                    holdingBack ? "HALTED for war preparation" : "resumed",
                    holdingBack ? "outnumbered by a hive it shares a slab with" : "no longer the weaker side"
                );
            }
        }
    }

    /**
     * Locks two hives into a war: each records the other, and the pairing is announced. Symmetric on purpose - a war
     * has no aggressor once it is running, and either side reaching zero members ends it for both.
     */
    private static void declareWar(ServerLevel level, ResourceLocation firstId, ResourceLocation secondId) {
        var first = hiveLocation(firstId);
        var second = hiveLocation(secondId);
        if (first == null || second == null) {
            return; // player claims and dead locations never enter a hive war
        }

        long now = level.getGameTime();
        boolean opened = first.addWarEnemy(secondId, now);
        opened |= second.addWarEnemy(firstId, now);
        if (!opened) {
            return; // already at war - this is just another chunk of the same fight
        }

        markLineageDirty(first);
        markLineageDirty(second);
        makeHostile(first.lineageFactionId(), second.lineageFactionId());
        makeHostile(firstId, secondId);
        // Two empires meeting: each crown pays its own hive up to half strength before the first blow.
        EmpressWarBolster.tryBolster(level, first, second);
        EmpressWarBolster.tryBolster(level, second, first);
        broadcast(
            level,
            "<<Warning>> Alien lifeform hostilities reported, avoid " + coordsOf(warAnchorPos(first, second)),
            net.minecraft.ChatFormatting.DARK_RED,
            AlienSoundEvents.BROADCAST_WAR_SOS.get()
        );
        Alien.LOGGER.info(
            "Alien hive war DECLARED between {} and {} — contested ground is frozen until one side is spent.",
            firstId,
            secondId
        );
    }

    @Override
    public void onContestResolved(ServerLevel level, TerritoryContest contest, ResourceLocation winner, ResourceLocation loser) {
        // A frozen border re-opens its contest every cycle by design; logging each of those at INFO would bury the
        // log in the same two lines forever. Only genuine transfers are announced.
        if (REASON.equals(contest.reason()) && !isFrozenByWar(winner, loser)) {
            Alien.LOGGER.info(
                "Alien territory war resolved: winner={}, loser={}, chunk=[{}, {}], dimension={}",
                winner,
                loser,
                contest.chunkX(),
                contest.chunkZ(),
                contest.dimension()
            );
        }

        var chunk = contest.chunkPos();

        // [stated] "the chunks freeze at the contested areas." While the two hives are at war the ground does not
        // change hands on a power score - it is decided by the fighting, so the contest is simply re-opened and the
        // claim transfer below is skipped. That is what stops the endless flip-flop testers were seeing.
        if (isFrozenByWar(winner, loser)) {
            Alien.MOD.territory().contests().startContest(level, chunk, contest.attacker(), contest.defender(), REASON);
            return;
        }

        if (HiveLocationIds.isHiveLocationId(loser)) {
            var losingLocation = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(loser));
            if (losingLocation != null && losingLocation.claimedChunks().contains(chunk)) {
                HiveLocationClaims.release(level, losingLocation, chunk);
            }
        }

        if (HiveLocationIds.isHiveLocationId(winner)) {
            var winningLocation = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(winner));
            if (winningLocation != null && winningLocation.isAlive() && !winningLocation.claimedChunks().contains(chunk)) {
                HiveLocationClaims.claim(level, winningLocation, chunk, level.getGameTime());
            }
        }
    }
}
