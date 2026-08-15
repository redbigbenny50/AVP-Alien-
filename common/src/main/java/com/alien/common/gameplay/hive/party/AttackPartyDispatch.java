package com.alien.common.gameplay.hive.party;

import com.alien.Alien;
import com.alien.common.gameplay.hive.config.HiveConfig;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.spawning.ReserveSpawnUtil;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.entity.v1.EntityReserves;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Dispatch for {@link HiveParty.AttackParty} — retribution against a player who breached this hive location's claim and
 * lingered while fighting (the territorial-intrusion model in {@code AttackCampaign}). Vent-dependent like
 * {@link BiomassHuntingPartyDispatch}. Fires the campaign's scheduled waves: wave 1 one MC day after the intrusion,
 * wave 2 one cooldown period after wave 1. Wave sequencing lives on the per-player {@code AttackCampaign} in
 * {@code HiveLocation#attackCampaigns}.
 */
public final class AttackPartyDispatch {

    private AttackPartyDispatch() {}

    /** Never closer than this - the party should arrive, not appear mid-swing. */
    private static final int MIN_AMBUSH_CHUNKS = 2;

    /** Beyond this the walk is long enough that the player has moved on anyway. */
    private static final int MAX_AMBUSH_CHUNKS = 6;

    public static void tryRun(MinecraftServer server, HiveLocation location, HiveConfig config) {
        var serverLevel = server.getLevel(location.dimension());
        if (serverLevel == null) {
            return;
        }

        var currentTick = serverLevel.getGameTime();
        var target = pickTarget(location, config, currentTick);
        if (target == null) {
            return;
        }
        var campaign = location.attackCampaigns().get(target);

        // A vent is the REQUIREMENT, not the destination. The hive still needs a door onto the world - surface or
        // frontier - to send anyone through, but the party is hunting a specific player and materializes out near
        // THEM rather than trudging from the doorstep.
        if (PartyVentUtil.findPartyVents(serverLevel, location).isEmpty()) {
            return;
        }

        // Needed up front now: the party materializes near the QUARRY, so no player means no ambush point and no
        // party. It is reused further down to hand every member its intruder target.
        var targetPlayer = serverLevel.getPlayerByUUID(target);
        if (targetPlayer == null) {
            return;
        }

        // A target currently in creative or spectator gets a POSTPONEMENT, exactly like having no legal ambush
        // spot: nothing is spent and the wave stays due, so switching modes delays the reckoning but never voids
        // it. Ambushing an untouchable player would burn the wave's members on a fight that cannot happen.
        if (targetPlayer.isCreative() || targetPlayer.isSpectator()) {
            return;
        }

        var spawnPos = findAmbushPos(serverLevel, targetPlayer);
        if (spawnPos == null) {
            return;
        }

        // Size scales with claims but is CAPPED - unbounded scaling put ~20 members on a large hive.
        var attackCap = com.alien.common.gameplay.hive.empress.EmpressCaps.scale(location, config.attackPartyMaxSize());
        var desiredSize = Math.min(
            attackCap,
            Math.max(
                1,
                Math.round(
                    config.attackPartyBaseSize() + config.attackPartySizePerClaimedChunk() * location.claimedChunks().size()
                )
            )
        );

        var composition = drainComposition(location, (int) desiredSize);
        if (composition.getCount() <= 0) {
            return;
        }

        var party = new HiveParty.AttackParty(
            HivePartyId.fresh(),
            location.id(),
            location.dimension(),
            composition,
            currentTick,
            target
        );

        var spawnedCount = materialize(serverLevel, party, spawnPos);
        if (spawnedCount <= 0) {
            refund(location, composition);
            return;
        }

        announce(serverLevel, targetPlayer, spawnPos, campaign.wavesSent());

        // Advance the campaign's wave counter — wave 1 (a day after intrusion) or wave 2 (a cooldown after wave 1).
        campaign.recordWaveSent(currentTick);

        if (targetPlayer != null) {
            for (var memberId : party.materializedMembers().keySet()) {
                var entity = serverLevel.getEntity(memberId);
                if (entity instanceof com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph xenomorph) {
                    xenomorph.setHiveIntruderTarget(targetPlayer);
                }
            }
        }

        location.parties().add(party);
        Alien.LOGGER.info(
            "Hive: dispatched attack party (wave {}) for location {} — {} members targeting player {}",
            campaign.wavesSent(),
            location.id(),
            spawnedCount,
            target
        );
    }

    /**
     * First player whose retribution campaign has a wave due right now: campaign active (intruded, not yet cleared),
     * the scheduled delay for its next wave has elapsed (wave 1 = 1 MC day after intrusion; wave 2 = one cooldown
     * period after wave 1), fewer than 2 waves sent, and no AttackParty already out against them.
     */
    /**
     * The moment the party lands: a scream, and a sentence telling the player why.
     * <p>
     * The second wave says something different - a declaration the first time, an unfinished sentence the second. It is
     * deliberately shorter and flatter: she has already explained herself.
     * <p>
     * The scream is played at the AMBUSH POINT rather than at the player, so it carries direction - the first thing
     * they learn is which way it came from. Volume is raised well past default because the party materializes a couple
     * of chunks out; at normal volume the warning would arrive after the party did.
     */
    private static void announce(
        ServerLevel level,
        net.minecraft.world.entity.player.Player target,
        BlockPos spawnPos,
        int wavesAlreadySent
    ) {
        level.playSound(
            null,
            spawnPos.getX() + 0.5,
            spawnPos.getY(),
            spawnPos.getZ() + 0.5,
            com.alien.common.registry.init.AlienSoundEvents.ENTITY_QUEEN_SCREAM.get(),
            net.minecraft.sounds.SoundSource.HOSTILE,
            8.0F,
            1.0F
        );

        // Read BEFORE recordWaveSent, so 0 means this is the first wave landing and 1 means the second.
        var line = wavesAlreadySent == 0
            ? "A queen has determined you're a threat to her rule..."
            : "The queen still seeks to remove you";

        target.displayClientMessage(
            net.minecraft.network.chat.Component.literal(line).withStyle(net.minecraft.ChatFormatting.DARK_RED),
            false
        );
    }

    /**
     * Somewhere to come out of the dark, near the quarry but not in their living room.
     * <h2>The rules, and what each is actually for</h2>
     * <ul>
     * <li><b>Not lit.</b> Block light 8+ is refused, the same ceiling vent planting uses. This is the base guard: a
     * player's home is lit, so this keeps parties out of it without needing to know what a base looks like. An unlit
     * base is the player's own problem, exactly as it is with vanilla mobs.</li>
     * <li><b>Open to the world.</b> {@code isOpenToWorld} is the party system's dimension-aware {@code canSeeSky} -
     * literal sky in normal worlds, standing room under the ceiling in Nether-likes. This is the DARK BOX guard: a mob
     * grinder is a roofed room, and so is a basement, so both fail here even though both are pitch dark. It is also
     * what keeps parties on the surface rather than materializing in a cave.</li>
     * <li><b>Not on top of them.</b> A minimum distance, so the party arrives as something approaching rather than
     * something already in your face.</li>
     * </ul>
     * <p>
     * Returning nothing is NOT a failure - it is a postponement. Nothing is spent getting here and the campaign's wave
     * is still due, so the next pass tries again. A player who never leaves a lit, roofed base delays the attack
     * indefinitely; they never cancel it. Only the hive dying does that. Searched in rings outward from the player,
     * nearest legal spot wins, so the party comes from as close as the terrain honestly allows.
     */
    private static @Nullable BlockPos findAmbushPos(ServerLevel level, net.minecraft.world.entity.player.Player target) {
        var profile = com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.get(level);
        var origin = target.blockPosition();

        for (var radius = MIN_AMBUSH_CHUNKS; radius <= MAX_AMBUSH_CHUNKS; radius++) {
            for (var dx = -radius; dx <= radius; dx++) {
                for (var dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }

                    var x = origin.getX() + dx * 16 + level.random.nextInt(16);
                    var z = origin.getZ() + dz * 16 + level.random.nextInt(16);
                    var surfaceY = com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles
                        .surfaceY(level, profile, x, z, origin.getY());

                    if (surfaceY == com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.NO_SURFACE) {
                        continue;
                    }

                    var pos = new BlockPos(x, surfaceY, z);
                    if (isValidAmbushPos(level, profile, pos)) {
                        return pos;
                    }
                }
            }
        }

        return null;
    }

    private static boolean isValidAmbushPos(
        ServerLevel level,
        com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.Profile profile,
        BlockPos pos
    ) {
        if (!level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }

        var ground = pos.below();
        if (!level.getBlockState(ground).isFaceSturdy(level, ground, net.minecraft.core.Direction.UP)) {
            return false;
        }

        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
            return false;
        }

        if (
            level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos) > SurfacePartyLifecycleTask.MAX_VENT_BLOCK_LIGHT
        ) {
            return false;
        }

        return com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.isOpenToWorld(level, profile, pos);
    }

    private static UUID pickTarget(HiveLocation location, HiveConfig config, long currentTick) {
        UUID worst = null;
        var worstScore = Long.MIN_VALUE;

        for (var entry : location.attackCampaigns().entrySet()) {
            var playerId = entry.getKey();
            var campaign = entry.getValue();

            if (!campaign.campaignActive() || campaign.wavesSent() >= 2) {
                continue;
            }

            var waveDue = campaign.wavesSent() == 0
                ? currentTick - campaign.intrusionTick() >= config.attackPartyWave1DelayTicks()
                : currentTick - campaign.lastWaveTick() >= config.attackPartyCooldownTicks();
            if (!waveDue) {
                continue;
            }

            var alreadyTargeted = false;
            for (var party : location.parties()) {
                if (party instanceof HiveParty.AttackParty attackParty && attackParty.targetPlayerId().equals(playerId)) {
                    alreadyTargeted = true;
                    break;
                }
            }
            if (alreadyTargeted) {
                continue;
            }

            // Eligible. Keep the WORST of them rather than the first one found - see hostilityScore.
            var score = hostilityScore(location, playerId, campaign, currentTick);
            if (score > worstScore) {
                worstScore = score;
                worst = playerId;
            }
        }

        return worst;
    }

    /**
     * How much of a problem this player is, so a hive that can only field one party sends it after the right person.
     * <p>
     * Two ingredients. DWELL is how long they have spent inside while fighting - the direct measure of how much trouble
     * they have been. FAMILIARITY is how long ago the territory first saw them at all, from the visit ledger: a face
     * the hive has known for weeks is a standing threat, while someone who turned up an hour ago is a nuisance. A
     * constant intruder therefore outranks a passer-by even when the passer-by is currently doing more damage.
     * <p>
     * Familiarity is weighted at a quarter so it colours the decision without overwhelming it - a player actively
     * tearing the place apart should still be answered first.
     */
    private static long hostilityScore(HiveLocation location, UUID playerId, AttackCampaign campaign, long currentTick) {
        var firstSeen = location.territoryVisits().get(playerId);
        var familiarity = firstSeen == null ? 0L : Math.max(0L, currentTick - firstSeen);

        return campaign.dwellTicks() + familiarity / 4L;
    }

    private static EntityReserves drainComposition(HiveLocation location, int desiredCount) {
        var reserves = location.localReserves();
        var composition = new EntityReserves();

        var candidateTypes = new ArrayList<EntityType<?>>();
        for (var type : reserves.getAvailableEntityTypes()) {
            if (
                type.is(AlienEntityTypeTags.WARRIORS)
                    || type.is(AlienEntityTypeTags.PROWLERS)
                    || type.is(AlienEntityTypeTags.CRUSHERS)
                    || type.is(AlienEntityTypeTags.PRAETORIANS)
                    || type.is(AlienEntityTypeTags.PREDALIENS)
            ) {
                candidateTypes.add(type);
            }
        }
        if (candidateTypes.isEmpty()) {
            return composition;
        }

        var drained = 0;
        while (drained < desiredCount) {
            var progressedThisPass = false;
            for (var type : candidateTypes) {
                if (drained >= desiredCount) {
                    break;
                }
                if (reserves.trySpawn(type)) {
                    composition.add(type, 1);
                    drained++;
                    progressedThisPass = true;
                }
            }
            if (!progressedThisPass) {
                break;
            }
        }

        return composition;
    }

    private static void refund(HiveLocation location, EntityReserves composition) {
        for (var type : new ArrayList<>(composition.getAvailableEntityTypes())) {
            var count = composition.getCount(type);
            if (count <= 0) {
                continue;
            }
            location.localReserves().addReturningMember(type, count);
            composition.add(type, -count);
        }
    }

    private static int materialize(ServerLevel level, HiveParty.AttackParty party, BlockPos spawnPos) {
        var spawnedCount = 0;
        for (var type : new ArrayList<>(party.composition().getAvailableEntityTypes())) {
            var count = party.composition().getCount(type);
            for (var i = 0; i < count; i++) {
                var entity = type.create(level);
                if (entity == null) {
                    continue;
                }
                // The vent is a BEACON for its chunk, not a doorway. Surface anywhere standable in that chunk so it
                // no longer matters that the vent itself is buried: members used to materialise INSIDE SOLID
                // GROUND at the vent's own Y and could never path a single step.
                var emergePos = PartyVentUtil.surfaceEmergeSpot(level, spawnPos);
                if (emergePos == null) {
                    // Nowhere dry to surface (an ocean vent). Do NOT fall back to the vent block - that buries
                    // them again. Skip: the member stays in the composition and is refunded at resolution.
                    entity.discard();
                    continue;
                }
                var jitterX = emergePos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 2.0;
                var jitterZ = emergePos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 2.0;
                entity.moveTo(jitterX, emergePos.getY(), jitterZ, level.random.nextFloat() * 360.0F, 0.0F);
                if (entity instanceof Mob mob) {
                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.MOB_SUMMONED, null);
                    mob.setPersistenceRequired();
                }
                level.addFreshEntityWithPassengers(entity);
                ReserveSpawnUtil.markSpawnedFromReserves(entity);
                if (entity instanceof com.alien.common.gameplay.entity.living.alien.Alien alien) {
                    alien.setPartyMembership(new PartyMembership(party.sourceLocationId(), party.id()));
                }
                party.trackMaterializedMember(entity.getUUID(), type);
                party.composition().add(type, -1);
                spawnedCount++;
            }
        }
        return spawnedCount;
    }
}
