package com.alien.common.gameplay.hive.empress;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.lifecycle.SpreadZoneCheck;
import com.alien.common.gameplay.hive.lifecycle.SpreadZoneResult;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * ⭐⭐⭐ AN EMPRESS FORCED INTO EXISTENCE EARLY, BY FEEDING A ROYAL JELLY BLOCK.
 * <p>
 * [stated] "players keep making empress and trying to get her to found a hiuve and get an eggsack. this normally means
 * they have to make one naturally but these idiots dont have paitence for it."
 * </p>
 * <p>
 * ⭐⭐ IT IS A CONCESSION TO IMPATIENCE, SO IT IS DELIBERATELY FASTER — [stated] "this can happen 2x as fast the carving
 * biomass for royal chamber is free for empress". Making it merely possible but equally slow would not solve the
 * problem it exists for.
 * </p>
 * <p>
 * ⭐⭐⭐ SHE FOUNDS AS A QUEEN AND BECOMES AN EMPRESS AFTERWARDS, AND THAT IS NOT A FICTION — IT IS WHAT THE MOD ALREADY
 * DOES. A natural empress is always a queen who founded her hive first and then emerged through
 * {@code EmpressEmergenceRitual}; she inherits the location she made as a queen. That is why the whole founding
 * pipeline is typed to {@code Queen} ({@code foundNewLineage(Queen, ...)},
 * {@code SpreadZoneCheck.wouldAllow(Queen, ...)}) while {@code Empress extends Xenomorph}: no empress has ever founded
 * a hive. Ordering it this way reuses a proven path instead of widening the classes behind the Aug-17 surface-hive
 * bugs.
 * </p>
 * <p>
 * ⚠ SITUATIONS 1 AND 2 ONLY. Situations 3-6 mutate an EXISTING hive's power structure (succession, lineage ownership,
 * rival empresses) and are staged separately — nothing here touches a lineage that already has an empress.
 * </p>
 */
public final class ForcedEmpressFounding {

    /**
     * ⭐⭐ A FORCED HIVE IS DELIBERATELY SHALLOW. Y 26 to 40, never below 0.
     * <p>
     * [stated] "empress hives forced are shallow is the idea which is why it never goes below 0."
     * </p>
     * <p>
     * ⭐ SHALLOWER THAN EVERY NATURAL BAND, WHICH IS THE POINT. A queen's common band tops out at Y 20 and her rare band
     * reaches −50; a forced hive sits above all of it. Impatience buys a hive that is quicker to reach and easier for
     * players to find and fight — a concession with a cost, not a free win.
     * </p>
     */
    private static final int FORCED_Y_MIN = 26;

    private static final int FORCED_Y_MAX = 40;

    /** [stated] "it never goes below 0". */
    private static final int FORCED_Y_FLOOR = 0;

    /**
     * Rock kept overhead where the ground is low.
     * <p>
     * ⚠⚠ NOT REDUNDANT WITH THE BAND. On an ocean floor or in a deep ravine the terrain itself sits at Y 30-40, and a
     * hive founded at the surface there is "open to the world" — which {@code SpreadZoneCheck} Rule 0 refuses outright,
     * and which is exactly how the surface-hive loop began. Modest on purpose, so it bites only in terrain the band did
     * not anticipate rather than dragging every forced hive deeper than intended.
     * </p>
     */
    private static final int MINIMUM_DEPTH_BELOW_SURFACE = 8;

    private ForcedEmpressFounding() {}

    /**
     * Whether this queen may be forced into founding an empress hive where she stands.
     * <p>
     * [stated] situation 1: "she isnt in a territory claim not in a hives radius and has room to spread."
     * {@code SpreadZoneCheck.wouldAllow} asks exactly that, and is the same gate a natural queen passes — so a forced
     * founding can never plant itself somewhere an ordinary one would be refused.
     * </p>
     */
    public static boolean canForceHere(Queen queen) {
        if (!(queen.level() instanceof ServerLevel)) {
            return false;
        }
        var standingIn = HiveLocationRegistry.INSTANCE.getByChunk(queen.level().dimension(), queen.chunkPosition());
        if (standingIn != null && standingIn.isAlive()) {
            // ⚠⚠ THIS IS THE LIKELIEST ONE, AND IT READS AS A DEAD CLICK. She is inside a live claim, so
            // forceFounding declines - and evolveInPlace has usually declined already for its own reason, so
            // BOTH paths refuse and the player sees nothing at all.
            lastRefusal = "she is inside another hive's claim - she can only found on unclaimed ground";
            return false;
        }
        var zone = SpreadZoneCheck.evaluate(queen, queen.blockPosition());
        if (zone instanceof SpreadZoneResult.Blocked blocked) {
            // ⭐ The founding check already writes a human sentence for every refusal - reuse it rather than
            // inventing a second vocabulary that can drift from the first.
            lastRefusal = blocked.reason();
            return false;
        }
        return true;
    }

    /**
     * ⭐⭐⭐ SENDS HER DIGGING DOWN TO A SHALLOW ANCHOR. She founds when she ARRIVES, not now.
     * <p>
     * ⚠⚠ THE FIRST VERSION OF THIS FOUNDED IMMEDIATELY AND WAS WRONG. It called {@code foundNewLineage} on the spot, so
     * the hive appeared at depth while she stood on the surface where the player fed her - no descent, no dig, and the
     * six digging clips had no user at all. [stated] the whole point was that "she would basically dig straight down
     * and do the carving/building".
     * </p>
     * <p>
     * ⭐ THE DESCENT ALREADY EXISTS AND IS NOT DUPLICATED HERE. {@code LocationMoveActions} runs during the LOCATION
     * phase - windup, {@code setDigging(true)}, travel to the anchor - and the animation dispatcher plays the digging
     * clips straight off {@code setDigging}. Committing an anchor and entering that phase is the entire job.
     * </p>
     * <p>
     * ⚠ FOUNDING ON ARRIVAL ALSO MEANS THE SPREAD CHECKS RUN, so a forced founding cannot land somewhere an ordinary
     * one would be refused. Founding up front skipped them.
     * </p>
     *
     * @return true if she was sent
     */
    public static boolean forceFounding(ServerLevel level, Queen queen) {
        if (!canForceHere(queen)) {
            return false;
        }

        var anchor = pickAnchor(level, queen);

        FORCED_PENDING.add(queen.getUUID());
        queen.getLifecyclePhaseManager().beginForcedDescent(anchor);

        Alien.LOGGER.info(
            "Hive: {} sent on a forced descent to {} - 2x dig and a free royal chamber on arrival",
            queen.getUUID(),
            anchor
        );
        return true;
    }

    /**
     * Queens fed a jelly block who are still digging to their forced anchor.
     * <p>
     * ⚠ The forced flag has to survive the descent, because the LOCATION it marks does not exist until she gets there -
     * and that flag is what the carve system reads for the 2x speed and the free royal chamber.
     * </p>
     * <p>
     * ⚠ In memory only: a restart mid-dig costs her the concession, not the hive. Failing toward ordinary pace is the
     * safe direction.
     * </p>
     */
    private static final java.util.Set<java.util.UUID> FORCED_PENDING =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * ⭐⭐ THE OTHER HALF OF THE DESCENT - called when a queen actually founds.
     * <p>
     * ⚠ Without this she digs down, founds an ordinary hive and stays a queen. Marking the location and starting her
     * emergence is what makes the whole thing a FORCED EMPRESS founding rather than a fast dig.
     * </p>
     *
     * @return true if this founding was a forced one
     */
    public static boolean onFounded(
        ServerLevel level,
        Queen queen,
        com.alien.common.gameplay.hive.location.HiveLocation location
    ) {
        if (!FORCED_PENDING.remove(queen.getUUID())) {
            return false;
        }

        location.setForcedEmpressFounding(true);
        beginForcedEmergence(level, queen, location);

        Alien.LOGGER.info(
            "Hive: forced empress {} reached her anchor and founded {} - emergence begins",
            queen.getUUID(),
            location.id()
        );
        return true;
    }

    /**
     * Starts her emergence into an empress on the hive she has just founded.
     * <p>
     * \u2b50\u2b50 REUSES THE NATURAL RITUAL RATHER THAN A SECOND TRANSFORM. {@code EmpressEmergenceRitual} already
     * turns a queen into an empress correctly, and the parts it gets right are the parts that are easy to get wrong: it
     * FORCES her UUID to the id the lineage is already acting on, and it snapshots faction memberships across the
     * discard. A parallel implementation would drift from it the first time either is touched.
     * </p>
     * <p>
     * \u26a0 The lineage's empressId is set FIRST, because the ritual reads it to decide which id the new entity must
     * carry. That ordering is the whole reason the natural path works.
     * </p>
     */
    public static void beginForcedEmergence(
        ServerLevel level,
        Queen queen,
        com.alien.common.gameplay.hive.location.HiveLocation location
    ) {
        crownForcedEmpress(location, queen.getUUID());
        EmpressEmergenceRitual.start(queen, location.lineageFactionId(), level.getGameTime());
    }

    /**
     * ⭐⭐ SITUATION 3 AND 6 — EVOLVE A QUEEN WHO ALREADY HAS A HIVE.
     * <p>
     * [stated] situation 3: "they would feed her the royal jelly cube and she would then get off her eggsack and evolve
     * into an empress which upon emergence would make her own eggsack the empress one and sit on it taking over that
     * hive as its queen because it is the same queen." <br>
     * [stated] situation 6: in a lineage that already has an empress "she would evolve like normal and make her eggsack
     * etc but she would yeild linage ownership to the oldest empress."
     * </p>
     * <p>
     * ⭐ THE TWO ARE ONE CODE PATH, AND THE ONLY DIFFERENCE IS WHO ENDS UP SOVEREIGN — which {@code addPretender}
     * already decides: a lineage with no empress takes her as ruler, one that has a sovereign files her behind it by
     * crowning time. No branch is needed here at all.
     * </p>
     * <p>
     * ⚠ SHE KEEPS HER OWN HIVE EITHER WAY. She founded it and she is still the same entity, so nothing about the
     * location changes hands - only the lineage's crown is in question.
     * </p>
     *
     * @return true if the evolution started
     */
    /**
     * ⭐⭐⭐ WHY THE LAST ATTEMPT WAS REFUSED. Written here, read by the interaction, shown to the player.
     * <p>
     * ⚠⚠ EVERY REFUSAL PATH RETURNED A BARE false, AND THE INTERACTION TURNED THAT INTO InteractionResult.FAIL - WHICH
     * IS INDISTINGUISHABLE FROM A DEAD CLICK. Players have reported "cant feed the queen a royal jelly block" for days
     * and nobody - them, you, or me - could see which condition was refusing. I guessed at it four times. A feature
     * that cannot say why it declined cannot be diagnosed from a screenshot.
     * </p>
     */
    private static String lastRefusal = "";

    public static String lastRefusal() {
        return lastRefusal;
    }

    public static boolean evolveInPlace(ServerLevel level, Queen queen) {
        var location = HiveLocationRegistry.INSTANCE.getByChunk(queen.level().dimension(), queen.chunkPosition());
        if (location == null || !location.isAlive()) {
            // ⚠ Not an error on its own - she may be in open ground, which is forceFounding's job. Recorded so
            // that if THAT refuses too, the player is told the reason that actually applies.
            lastRefusal = "she is not standing in a living hive";
            return false;
        }
        // ⚠⚠ FOUNDER ID IS THE WRONG TEST AND IT BROKE THE FEATURE. This used to require the queen to be the hive's
        // recorded FOUNDER, which is only true of a queen who dug it herself as a queen. Any queen who arrived by
        // SUCCESSION, MIGRATION, PROMOTION FROM A DAUGHTER or LEGACY RECOVERY has somebody else's id there - so
        // evolveInPlace refused, execution fell through to forceFounding, that refused too because she is standing
        // in a live claim, and the whole interaction returned FAIL. Reported as "the queen won't accept the jelly
        // block", and it would have hit most established hives.
        //
        // ⭐ The real question is whether this hive's throne is HERS TO GIVE UP - that is, whether an empress is
        // already sitting on it. If one is, she is a rival (situation 5) and the schism path owns her. If not, the
        // queen standing in the hive IS its ruler however she came to be here.
        if (location.hasSittingEmpress()) {
            lastRefusal = "an empress already sits on this hive's throne";
            return false;
        }

        var faction = Alien.MOD.factions().get(location.lineageFactionId());
        if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
            return false;
        }

        // ⚠⚠ SHE MUST BE MARKED AS EVOLVING BEFORE ANYTHING ELSE. [stated] "this would probably be a new marker
        // instead of captured or killed as evolving so the hive knows shes changing and not try to replace her."
        // The emergence ritual takes her AI and invulnerability, and a hive that reads that as a lost queen starts
        // raising a successor - so without the marker, feeding her produces TWO queens.
        location.setQueenEvolving(true);

        lineage.addPretender(queen.getUUID(), level.getGameTime());
        lineage.markDirty();

        EmpressEmergenceRitual.start(queen, location.lineageFactionId(), level.getGameTime());

        Alien.LOGGER.info(
            "Hive: queen {} is evolving into an empress in place at {} (sovereign is now {})",
            queen.getUUID(),
            location.id(),
            lineage.empressId()
        );
        return true;
    }

    /**
     * Is an empress already riding THIS hive's eggsack, acting as its queen?
     * <p>
     * ⚠ The hive's own founder is the one that matters. Any other empress standing in the chunk is a visitor, and a
     * visitor does not make the throne occupied.
     * </p>
     */
    private static boolean hasSittingEmpress(com.alien.common.gameplay.hive.location.HiveLocation location) {
        return location.hasSittingEmpress();
    }

    /**
     * ⭐⭐ SITUATION 4, THE ENTRY POINT — an empress finds herself standing in a hive with a queen and no crown.
     * <p>
     * ⚠ SHE MUST ALREADY BE UNCROWNED HERSELF. An empress who already rules a lineage wandering into a neighbour's hive
     * is a RIVAL (situation 5, handled by {@code EmpressAbsorption} through the war system), not a successor — letting
     * her take it here would hand her a free hive for trespassing.
     * </p>
     */
    public static void tryClaimCrownlessHive(
        com.alien.common.gameplay.entity.living.alien.xenomorph.empress.Empress empress
    ) {
        if (!(empress.level() instanceof ServerLevel level)) {
            return;
        }
        var location = HiveLocationRegistry.INSTANCE
            .getByChunk(empress.level().dimension(), empress.chunkPosition());
        if (location == null || !location.isAlive()) {
            return;
        }

        var faction = Alien.MOD.factions().get(location.lineageFactionId());
        if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
            return;
        }
        // ⭐⭐⭐ THE TEST IS PER-HIVE OCCUPANCY, NOT THE LINEAGE CROWN.
        //
        // [stated] "wether its the sovereign or not doesnt matter. This situation is if they spawn an empress when
        // one is already infront of them acting as that hives queen. it has nothing to do with the lineage at all."
        //
        // ⚠⚠ I ORIGINALLY GATED ON lineage.empressId() != null AND IT WAS WRONG. Under a corridor network that is
        // true because of an empress SEVERAL HIVES AWAY who has never been here - so an empress dropped into a hive
        // with an ordinary queen on its eggsack did nothing at all. Worse, it contradicted situation 6, which says a
        // second empress in a lineage is legitimate and simply yields seniority.
        // 🚨🚨 IT MUST BE SOMEBODY ELSE'S THRONE. THIS EXACT LINE SHIPPED WITHOUT THE IDENTITY CHECK AND BROKE A
        // LEGITIMATE EMPRESS.
        //
        // A naturally promoted queen IS the sitting empress of her own hive, so "is an empress sitting here" was true
        // for the one hive she is entitled to. Every tick she was treated as an intruder, sent away by
        // EmpressSchism.relocate - which STRIPS HER OVIPOSITOR on the way out - and then failed to found anywhere,
        // so she stayed put and it happened again a second later.
        //
        // ⚠ A live log caught it 96 times in one session: "the lineage has room, so she leaves to found for it"
        // immediately followed by "could not find anywhere to found - she remains displaced". Reported from the
        // player's side as an empress whose eggsack "appears for a split second then outright disappears" - which is
        // precisely what it looks like when something tears it off her once a second.
        //
        // ⭐ The question was never "is a throne occupied" but "is it occupied BY SOMEBODY ELSE".
        var sitting = location.sittingEmpressId();
        if (sitting != null && !sitting.equals(empress.getUUID())) {
            EmpressSchism.relocate(level, empress, location, lineage);
            return;
        }

        // ⚠ She must not already be somebody else's sovereign elsewhere.
        for (var factionId : Alien.MOD.factions().getFactionIds(empress.getUUID())) {
            var other = Alien.MOD.factions().get(factionId);
            if (
                other != null
                    && other.data() instanceof LineageFactionData otherLineage
                    && empress.getUUID().equals(otherLineage.empressId())
            ) {
                return;
            }
        }

        var founder = location.founderId();
        if (founder == null) {
            return;
        }

        // ⭐ THE CROWN FIRST, THEN THE EVICTION. If the displaced queen left before the lineage had its empress, the
        // hive would spend a moment queenless AND crownless - which is the state QueenSuccessionTask reacts to by
        // raising a replacement, and the hive would end up with a queen it did not need.
        crownForcedEmpress(location, empress.getUUID());
        // ⚠ RECORD THE SEAT, not just the crown. The lineage knows it HAS an empress; only this says WHERE she sits,
        // and every unloaded-hive occupancy question downstream reads it.
        location.setSittingEmpressId(empress.getUUID());

        for (
            var candidate : level.getEntitiesOfClass(
                com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen.class,
                empress.getBoundingBox().inflate(48.0)
            )
        ) {
            if (founder.equals(candidate.getUUID())) {
                succeedInPlace(level, candidate);
                break;
            }
        }

        Alien.LOGGER.info(
            "Hive: empress {} assumed the crown of {} by succession",
            empress.getUUID(),
            location.id()
        );
    }

    /**
     * ⭐⭐ SITUATION 4 — AN EMPRESS IS PUT INTO A HIVE THAT ALREADY HAS A QUEEN.
     * <p>
     * [stated] "She would take over the rule from that queen. The current queen would then get up and leave forming a
     * daughter hive for free without a slot taken. this is a succession of ownership. That empress then resumes tasks
     * for that hive same bank everything."
     * </p>
     * <p>
     * ⭐⭐ "WITHOUT A SLOT TAKEN" IS THE PER-HIVE DAUGHTER LIMIT, NOT THE LINEAGE CAP — he was explicit that these are
     * two different limits. A hive may seed exactly {@code maxDaughterHivesPerLocation} (2) daughters in its whole
     * life; an evicted ruler is not one of them, because she is not a daughter being RAISED, she is a queen being
     * DISPLACED. Spending a slot on her would punish the hive for something done to it.
     * </p>
     * <p>
     * 🚨 THE 8-HIVE LINEAGE CAP IS UNTOUCHED AND MUST STAY THAT WAY — [stated] "nothing should allow a linage to have a
     * 9th hive". What forcing an empress skips is the MATURITY requirement (a lineage may crown at 2 hives instead of
     * waiting for 4), never the ceiling. Her founding runs the ordinary spread checks, which enforce it.
     * </p>
     * <p>
     * ⚠ SHE CLEARS THE CLAIM FIRST, LIKE ANY DAUGHTER — [stated] "like the daughters clear the claim first". No
     * exemption from the spread-zone rule: only the slot is free, not the placement.
     * </p>
     *
     * @return true if the succession happened
     */
    public static boolean succeedInPlace(ServerLevel level, Queen displaced) {
        var location = HiveLocationRegistry.INSTANCE
            .getByChunk(displaced.level().dimension(), displaced.chunkPosition());
        if (location == null || !location.isAlive()) {
            return false;
        }

        var faction = Alien.MOD.factions().get(location.lineageFactionId());
        if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
            return false;
        }
        if (lineage.empressId() != null) {
            return false; // the lineage already answers a crown - that is situation 5, a rival, not a succession
        }

        // ⚠ SHE GIVES UP THE SACK BEFORE SHE GOES. It belongs to the hive she is leaving, and the empress about to
        // take the throne will grow her own - a departing queen carrying it away would strip the hive she is
        // surrendering, which is the opposite of "that empress then resumes tasks for that hive same bank
        // everything".
        displaced.getOvipositorManager().abandonOvipositor();

        // ⭐ THE FREE DAUGHTER. Not decrementing daughterHivesFounded, simply never incrementing it: her departure
        // costs the hive nothing it would otherwise have spent.
        displaced.getLifecyclePhaseManager().rearmAfterHiveLoss();

        Alien.LOGGER.info(
            "Hive: queen {} displaced by an empress at {} - leaving to found without spending a daughter slot",
            displaced.getUUID(),
            location.id()
        );
        return true;
    }

    /** Records her as the lineage's empress. */
    public static void crownForcedEmpress(
        com.alien.common.gameplay.hive.location.HiveLocation location,
        java.util.UUID empressId
    ) {
        var faction = Alien.MOD.factions().get(location.lineageFactionId());
        if (faction != null && faction.data() instanceof LineageFactionData lineage) {
            // ⭐ No election and no maturity gate — [stated] "an empress forced early doesnt need to follow the hive
            // assignment when 4 hives are reached for the linage". The 8-hive cap still binds her afterwards.
            lineage.setEmpressId(empressId);
            // ⚠ This runs from a player interaction, outside the loaded tick that normally marks the lineage — the
            // same gap that lost the inhibitor's claim and every relogged worker. Mark it here.
            lineage.markDirty();
        }
    }

    /**
     * Her own chunk's centre, at a forced-band Y, reshaped per dimension and clamped under the local surface.
     * <p>
     * ⚠⚠ DIMENSION RULES APPLY — [stated] "obviously nether and end hive rules when applicable would apply for the
     * location digging vs not digging etc". {@code remapFromOverworldBand} is what turns an overworld-shaped band into
     * the right depth for a dimension whose vertical shape differs; a raw 26-40 in the Nether means something quite
     * different from the overworld. End hives do not dig at all, and their own branch in {@code enterLocation} handles
     * that — a forced empress there founds in place like any other End queen.
     * </p>
     */
    private static BlockPos pickAnchor(ServerLevel level, Queen queen) {
        var chunk = queen.chunkPosition();
        var rolled = FORCED_Y_MIN + queen.getRandom().nextInt(FORCED_Y_MAX - FORCED_Y_MIN + 1);

        var profile = DimensionHiveProfiles.get(level);
        rolled = DimensionHiveProfiles.remapFromOverworldBand(rolled, profile);

        // \u26a0\u26a0 surfaceY, NOT THE RAW HEIGHTMAP. In a ceiled dimension the heightmap reports the BEDROCK ROOF,
        // which is the same trap that once branded every Nether shelf vent as SURFACE - clamping against it would put
        // a forced Nether hive somewhere absurd. surfaceY knows the difference: sky dimensions use the heightmap,
        // shelf dimensions search outward from nearY for the cavern floor she is actually standing on.
        //
        // \u26a0 NO_SURFACE means there is nothing above her worth clearing (the End's void), so the band stands.
        var surface = DimensionHiveProfiles.surfaceY(
            level,
            profile,
            chunk.getMiddleBlockX(),
            chunk.getMiddleBlockZ(),
            queen.blockPosition().getY()
        );
        var deepest = surface == DimensionHiveProfiles.NO_SURFACE
            ? rolled
            : Math.min(rolled, surface - MINIMUM_DEPTH_BELOW_SURFACE);

        return chunk.getMiddleBlockPosition(Math.max(FORCED_Y_FLOOR, deepest));
    }
}
