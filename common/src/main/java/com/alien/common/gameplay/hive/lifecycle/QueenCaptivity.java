package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.gameplay.block.entity.capture.anchor.AnchorBlockEntity;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.faction.LocationMembership;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * ⭐⭐⭐ THE ONE PLACE THAT DECIDES WHETHER A QUEEN MAY TOUCH THE HIVE/FACTION SYSTEM. Oct 3.
 * <p>
 * [stated] "when capturing a queen she on a chain or with an inhibitor she can no longer make a claim using the faction
 * system. So she cant start a hive, create territory, try to carve anything." The goal is to "further seperate
 * captured, chained, and inhibited queens from hives and territory".
 * </p>
 * <h2>Captive</h2>
 * <p>
 * Any chain, or the inhibitor. A captive queen holds no claim of any kind (the inhibitor's personal one-chunk claim is
 * gone), joins nothing, founds nothing, carves and digs nothing. Capture SEVERS her from whatever hive she belonged to
 * - [stated] "chaining should severe her like the inhibitor does" - which leaves that hive queenless and records a
 * rescue campaign on it if she was its founder.
 * </p>
 * <h2>The release grace</h2>
 * <p>
 * [stated] "there should be a cooldown of 5 minutes between a chained queen that is freed or was inhibited or freshly
 * molted/spawned to get adopted or try to form a hive or dig. This will cover the gap of time for someone trying to
 * either recapture her, move her, or have a queen freshly appear in overlapping hive territory."
 * </p>
 * <p>
 * ⚠⚠ ONLY OUTSIDE A HIVE'S SLAB - [stated] "this is for outside of the hive slab. any new queens born or summoned in a
 * hive slab follow the same rules currently. this is for situations where someones lab might be above a natural hive
 * but outside of the slab." The slab is {@link HiveLocation#withinSlab}: the hive's floor-to-ceiling band in its
 * claimed chunks. The test is LIVE, not taken once: walk her down into the slab mid-grace and the ordinary rules take
 * her immediately.
 * </p>
 * <p>
 * ⭐ THE KIN-RESCUE GRACE IS DIFFERENT: [stated] "if she is being freed by the hive then she should rejoin it quicker
 * ... shorten it to 30seconds so its not a constant back and forth while actively fighting." That one applies
 * EVERYWHERE, slab included, because a kin rescue happens inside the slab by definition.
 * </p>
 */
public final class QueenCaptivity {

    /** [stated] "a cooldown of 5 minutes". */
    public static final int RELEASE_GRACE_TICKS = 5 * 60 * 20;

    /** [stated] "shorten it to 30seconds" - when her own hive freed her. */
    public static final int RESCUE_GRACE_TICKS = 30 * 20;

    /** Per strike a rescuer makes on a chain. Rescuers call this about once a second each. */
    public static final double RESCUE_CHAIN_BREAK_CHANCE = 0.10;

    private QueenCaptivity() {}

    // ---- the states ----

    /**
     * Chained (any number of chains), wearing the inhibitor, or held on a capture chain by a player. [stated] "When
     * held it counts as 1 chain" - so a queen being walked on a hand chain is a captive in every rule here.
     */
    public static boolean isCaptive(Queen queen) {
        return queen.isInhibited()
            || queen.getBindManager().hasAnyChain()
            || com.alien.common.gameplay.capture.CaptureHoldManager.isHeld(queen);
    }

    /** Captive, or downed - the three states in which she never founds. */
    public static boolean isHeld(Queen queen) {
        return isCaptive(queen) || queen.isIncapacitated();
    }

    public static boolean isGraceActive(Queen queen) {
        return queen.level().getGameTime() < queen.getCaptiveGraceUntil();
    }

    /**
     * Whether her grace is stopping her RIGHT NOW. The 5-minute release/arrival grace only bites outside every hive's
     * slab; the 30-second kin-rescue grace bites everywhere.
     */
    public static boolean isGraceBlocking(Queen queen) {
        if (!isGraceActive(queen)) {
            return false;
        }
        if (queen.captiveGraceIgnoresSlab()) {
            return true;
        }
        return slabLocationAt(queen.level(), queen.blockPosition()) == null;
    }

    /**
     * Captive or grace-blocked: her LOCATION/dig front-end and her carving stand still. A downed queen is not in this
     * set on purpose - the incapacitation manager owns her and she is not trying to do anything.
     */
    public static boolean blocksFrontEnd(Queen queen) {
        return isCaptive(queen) || isGraceBlocking(queen);
    }

    /**
     * Whether {@code entity} may be added to a hive's factions. Anything that is not a queen is always allowed; a queen
     * is refused while captive or grace-blocked. Asked by {@link LocationMembership#join}, the choke point every hive
     * join flows through (spawn auto-join, stray adoption, wild adoption, rescue reconciliation).
     */
    public static boolean mayJoinHive(Entity entity) {
        if (!(entity instanceof Queen queen)) {
            return true;
        }
        return !isCaptive(queen) && !isGraceBlocking(queen);
    }

    /**
     * Whether {@code entity} may found a hive or a hive location. Asked by {@link HiveLocationFoundingService}, which
     * every founding route funnels through - settlement, the royal-jelly block, forced settlement, rescue relocation.
     * Gating the callers instead would miss the next route someone adds.
     */
    public static boolean mayFound(Entity entity) {
        if (!(entity instanceof Queen queen)) {
            return true;
        }
        return !isHeld(queen) && !isGraceBlocking(queen);
    }

    // ---- the slab ----

    /** The live hive whose slab contains {@code pos}, or null. */
    public static @Nullable HiveLocation slabLocationAt(Level level, BlockPos pos) {
        var location = HiveLocationRegistry.INSTANCE.getByChunk(level.dimension(), new ChunkPos(pos));
        if (location == null || !location.isAlive() || !location.withinSlab(pos.getY())) {
            return null;
        }
        return location;
    }

    /** The live hive OF HER OWN STRAIN whose slab she stands in, or null - where her kin will come for her. */
    public static @Nullable HiveLocation kinSlabLocation(Queen queen) {
        var location = slabLocationAt(queen.level(), queen.blockPosition());
        if (location == null || !java.util.Objects.equals(location.lineageVariantOrNull(), queen.getVariant())) {
            return null;
        }
        return location;
    }

    // ---- events ----

    /** The first chain went on. Sever her from every hive, exactly as the inhibitor does. */
    public static void onCaptured(ServerLevel level, Queen queen) {
        QueenInhibitionService.severFromHives(queen);
    }

    /**
     * A queen came into the world by a player's hand (egg, command, bucket, dispenser), or molted into a queen.
     * <p>
     * \u26a0 ONLY IF SHE ARRIVES OUTSIDE EVERY HIVE'S SLAB. [stated] "any new queens born or summoned in a hive slab
     * follow the same rules currently." The grace is checked live, so stamping it on a queen born INSIDE a slab was not
     * harmless: a hive-raised daughter would carry it out of her mother's slab and be stopped from founding her own
     * hive for the rest of the five minutes. Born in a slab, she gets none at all - and neither does any queen that is
     * a member of a hive location when she arrives (a hive-raised daughter molting outside the slab), any queen when
     * build-free {@code queenFoundsWherePlaced} is on, or any queen in an End-style dimension.
     * </p>
     */
    public static void startArrivalGrace(Queen queen) {
        if (slabLocationAt(queen.level(), queen.blockPosition()) != null) {
            return;
        }
        // \u2b50 NOT WHERE PLAYERS PLACE QUEENS ON PURPOSE. [stated] "i guess you can remove that 5 min wait thing in
        // those situations": build-free with queenFoundsWherePlaced (a mapmaker puts her where the map wants her hive),
        // and End-style dimensions (every End hive is player-brought). Only the ARRIVAL grace - a queen let out of
        // captivity in either still gets the release grace.
        if (com.alien.common.gameplay.hive.config.BuildFreeMode.queenFoundsWherePlaced()) {
            return;
        }
        if (
            queen.level() instanceof ServerLevel serverLevel
                && com.alien.common.gameplay.hive.dimension.EndStyleHiveRules.isEndStyle(serverLevel)
        ) {
            return;
        }
        // \u2b50 A HIVE-RAISED DAUGHTER NEVER GETS IT, wherever she molts. [stated] "nothing stops daughters from
        // founding
        // hives fully except for the daughter hive cap." A heavy promoted while out on the surface of her hive's claim
        // is outside the slab but is still the hive's daughter. She carries her hive-location membership across the
        // molt (it is what the lifecycle later reads to mark her raisedByHive), so that membership is the signal.
        for (var factionId : Alien.MOD.factions().getFactionIds(queen.getUUID())) {
            if (com.alien.common.gameplay.hive.id.HiveLocationIds.isHiveLocationId(factionId)) {
                return;
            }
        }
        queen.startCaptiveGrace(RELEASE_GRACE_TICKS, false);
    }

    /**
     * Per-tick bookkeeping, owned by the queen. Starts her grace the tick she goes from captive to free, and clears out
     * the personal claim an old save may still be carrying.
     */
    public static void tick(Queen queen) {
        if (!(queen.level() instanceof ServerLevel level)) {
            return;
        }

        var captive = isCaptive(queen);
        if (queen.wasCaptive() && !captive) {
            var byKin = queen.consumeFreedByKin();
            queen.startCaptiveGrace(byKin ? RESCUE_GRACE_TICKS : RELEASE_GRACE_TICKS, byKin);
            Alien.LOGGER.info(
                "Captivity: queen {} is free ({}) - {}s before she may join, found or dig",
                queen.getUUID(),
                byKin ? "freed by her kin" : "released",
                (byKin ? RESCUE_GRACE_TICKS : RELEASE_GRACE_TICKS) / 20
            );
        }
        queen.setWasCaptive(captive);

        // Old worlds: an inhibited queen still owns the personal one-chunk claim, and a queen chained before this
        // change was never severed. Once per load is enough - nothing re-creates either.
        if (captive && !queen.isLegacyCaptivityChecked() && queen.tickCount % 20 == 0) {
            queen.setLegacyCaptivityChecked(true);
            QueenInhibitionService.tearDownLegacyPersonalClaim(level, queen);
            QueenInhibitionService.severFromHives(queen);
        }
    }

    // ---- shackles ----

    /** Players within this distance hear about it when she breaks a chain herself. */
    private static final double SHACKLE_ALERT_RANGE = 64.0;

    public static final String SHACKLES_BROKEN_KEY = "message.avp_alien.capture_chain.shackles_broken";

    /**
     * [stated] "it will have a message whenever she breaks a chain 'The queen breaks her shackles' so it will alert
     * someone if she has actually broken one." Chat rather than the action bar so it is still there when they look.
     * Only for chains SHE breaks (straining, attacking, an impossible rig on load) - not when her kin free her.
     */
    public static void announceShacklesBroken(Queen queen) {
        if (!(queen.level() instanceof ServerLevel level)) {
            return;
        }
        var message = net.minecraft.network.chat.Component.translatableWithFallback(
            SHACKLES_BROKEN_KEY,
            "The queen breaks her shackles"
        );
        var rangeSqr = SHACKLE_ALERT_RANGE * SHACKLE_ALERT_RANGE;
        for (var player : level.players()) {
            if (player.distanceToSqr(queen) <= rangeSqr) {
                player.sendSystemMessage(message);
            }
        }
        level.playSound(null, queen.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.2F, 0.7F);
    }

    // ---- rescue ----

    /**
     * \u2b50 [stated] "they should break the inhibitor if they reach her." A rescuer that has reached her tears the
     * inhibitor off. It drops where she stands, recoverable, exactly as a player's pry-off does. Credits her kin, so
     * she gets the 30-second grace rather than five minutes.
     */
    public static void tearOffInhibitor(LivingEntity rescuer, Queen queen) {
        if (!queen.isInhibited() || !(queen.level() instanceof ServerLevel level)) {
            return;
        }
        rescuer.swing(InteractionHand.MAIN_HAND);
        queen.markFreedByKin();
        queen.setInhibited(false);
        QueenInhibitionService.onReleased(level, queen);
        queen.spawnAtLocation(com.alien.common.registry.init.item.AlienItems.INHIBITOR.get());
        level.playSound(null, queen.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.0F, 1.2F);
        Alien.LOGGER.info("Captivity: kin tore the inhibitor off queen {}", queen.getUUID());
    }

    /** Close enough to her body to tear at her. Measured against her hitbox, so her size does not matter. */
    public static boolean canReach(LivingEntity rescuer, Queen queen) {
        return queen.getBoundingBox().inflate(2.0).intersects(rescuer.getBoundingBox());
    }

    /**
     * ⭐ [stated] "the rescueers would be trying to break the chain not the anchors. so when they attack the anchor they
     * are breaking it they are doing a roll against the chain ... an easier to hit target without it actually breaking
     * the anchor so it can still be used for another attempt."
     * <p>
     * One strike: the rescuer swings at the anchor and rolls {@code chance} against the CHAIN on it. On success the
     * chain comes off and the anchor stays standing, empty, for the captor's next attempt. Credits the freed queen to
     * her kin, which is what earns her the short grace instead of the long one.
     * </p>
     *
     * @return true if the chain broke
     */
    public static boolean strikeChain(LivingEntity rescuer, BlockPos anchorPos, double chance) {
        var level = rescuer.level();
        if (level.isClientSide || !(level.getBlockEntity(anchorPos) instanceof AnchorBlockEntity anchor)) {
            return false;
        }
        if (!anchor.hasChain()) {
            return false;
        }
        rescuer.swing(InteractionHand.MAIN_HAND);
        if (rescuer instanceof Mob mob) {
            mob.getLookControl().setLookAt(anchorPos.getX() + 0.5, anchorPos.getY() + 0.5, anchorPos.getZ() + 0.5);
        }
        level.playSound(null, anchorPos, SoundEvents.CHAIN_HIT, SoundSource.HOSTILE, 0.8F, 0.8F);
        if (rescuer.getRandom().nextDouble() >= chance) {
            return false;
        }
        var boundId = anchor.getBoundMobId();
        if (boundId != null && level instanceof ServerLevel serverLevel && serverLevel.getEntity(boundId) instanceof Queen queen) {
            queen.markFreedByKin();
        }
        anchor.release();
        return true;
    }

    /**
     * ⭐ [stated] "if she is being freed by the hive then she should rejoin it". If {@code here} is the hive she was
     * taken from - its rescue campaign names her and nobody has taken her seat - she is put back on its throne as its
     * founder and the campaign closes.
     *
     * @return true if she was restored
     */
    public static boolean tryRestoreToOriginalHive(Queen queen, @Nullable HiveLocation here) {
        if (here == null || !here.isAlive() || here.founderId() != null) {
            return false;
        }
        var campaign = here.rescueCampaign();
        // An End-style hive records no campaign (no convoys there), so in the End any empty throne of her own strain
        // takes her back - the same rule as a summoned queen taking over an End hive.
        var endTakeover = campaign == null
            && here.isEndStyleHive()
            && java.util.Objects.equals(here.lineageVariantOrNull(), queen.getVariant());
        if (!endTakeover && (campaign == null || !queen.getUUID().equals(campaign.queenUuid()))) {
            return false;
        }
        if (!mayJoinHive(queen)) {
            return false;
        }

        LocationMembership.join(here, queen);
        here.setFounderId(queen.getUUID());
        here.setRescueCampaign(null);
        var faction = Alien.MOD.factions().get(here.lineageFactionId());
        if (faction != null && faction.data() instanceof LineageFactionData lineage) {
            lineage.markDirty();
        }
        Alien.LOGGER.info("Captivity: queen {} is back on the throne of {}", queen.getUUID(), here.id());
        return true;
    }
}
