package com.alien.common.gameplay.entity.dismemberment;

import com.alien.AlienResources;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.spitter.Spitter;
import com.blib.api.common.dismemberment.v1.hitbox.LimbHitboxRegistry;
import com.blib.api.common.dismemberment.v1.hitbox.LimbHitboxVolume;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Shared server-authoritative adapter for the adult Xenomorph rig.
 * <p>
 * The authored geometry remains the source of truth. Profiles are loaded once at startup and a living entity's volumes
 * are then cached for its current server tick, so shotgun pellets and simultaneous players do not rebuild an animated
 * model repeatedly.
 * </p>
 */
public final class StandardXenomorphLimbHitboxes {

    private static final Map<String, AnimatedGeoHitboxProfile> PROFILES = new HashMap<>();

    private static final Map<Xenomorph, CachedVolumes> SERVER_VOLUMES = Collections.synchronizedMap(new WeakHashMap<>());

    static void register(ResourceLocation entityTypeId, String modelName) {
        var profile = PROFILES.computeIfAbsent(modelName, StandardXenomorphLimbHitboxes::profile);
        LimbHitboxRegistry.register(
            entityTypeId,
            entity -> entity instanceof Xenomorph xenomorph ? serverVolumes(xenomorph, profile) : List.of()
        );
    }

    private static List<LimbHitboxVolume> serverVolumes(Xenomorph xenomorph, AnimatedGeoHitboxProfile profile) {
        var gameTime = xenomorph.level().getGameTime();
        var cached = SERVER_VOLUMES.get(xenomorph);
        if (cached != null && cached.gameTime() == gameTime && cached.profile() == profile) {
            return cached.volumes();
        }
        var volumes = List.copyOf(volumes(xenomorph, profile, 0.5D));
        SERVER_VOLUMES.put(xenomorph, new CachedVolumes(gameTime, profile, volumes));
        return volumes;
    }

    private static List<LimbHitboxVolume> volumes(Xenomorph xenomorph, AnimatedGeoHitboxProfile profile, double partialTick) {
        var animation = animationFor(xenomorph, profile, partialTick);
        return profile.volumes(xenomorph, animation.name(), animation.timeSeconds());
    }

    /**
     * ⚠⚠ THIS IS WHERE THE SERVER DECIDES WHAT POSE THE LIMBS ARE IN, and it MUST agree with the clip the client
     * dispatched. Every candidate list here is tried in order and the first name the model actually contains wins;
     * {@code firstAnimation} returns {@code ""} when NOTHING matches, and an empty name silently falls back to the BIND
     * POSE - no crash, no log, just limb hitboxes frozen in a T-pose for the whole swing.
     * <p>
     * ⚠ THAT IS EXACTLY WHAT HAD HAPPENED. The renaming passes moved warrior, runner, prowler and spitter onto the
     * dotted convention ({@code attack.claw.left} and friends) but this shared adapter still only knew the run-together
     * names, so none of the four had working attack hitboxes. The dotted names below are the fix. The legacy names are
     * KEPT because the castes that have not been converted yet still use them.
     * </p>
     */
    private static AnimationTime animationFor(Xenomorph xenomorph, AnimatedGeoHitboxProfile profile, double partialTick) {
        var quad = isQuadPosture(xenomorph);

        if (!xenomorph.attackType.get().isNone()) {
            var attackName = xenomorph.attackType.get().id();
            var animation = attackName.contains("tail")
                ? profile.firstAnimation(
                    quad ? "quad.attack.tail" : "attack.tail",
                    "attack.tail",
                    "fullattacktail",
                    "fullquadattacktail",
                    "attacktail",
                    "attacktail.tail",
                    "tailattackquad.tail"
                )
                : attackName.contains("crawl")
                    // ⚠ BEFORE the bite and claw branches: a crawl bite id contains "bite" and a crawl claw id
                    // contains "claw", so either would be swallowed by those branches and replay the STANDING clip
                    // while the body is on the ground.
                    ? profile.firstAnimation(crawlAttackCandidates(xenomorph, attackName))
                    : attackName.contains("backhand")
                        // ⚠ MIRRORED like the claw, so the side comes from the same seeded helper. Listed BEFORE the
                        // bite/claw branches because "backhand" contains none of their words and would otherwise
                        // fall through to the claw candidates and play the wrong arm clip entirely.
                        ? profile.firstAnimation(backhandCandidates(xenomorph))
                        : attackName.contains("bite")
                            ? profile.firstAnimation(
                                quad ? "quad.attack.bite" : "attack.bite",
                                "attack.bite",
                                "fullattackbite",
                                "fullquadattackbite",
                                "attackbite",
                                "attack.crawlbite",
                                "attackbite.head",
                                "biteattack.head"
                            )
                            : profile.firstAnimation(clawCandidates(xenomorph, quad));
            var elapsedTicks = Math.max(0.0D, xenomorph.level().getGameTime() - xenomorph.attackStartedAtGameTime.get() + partialTick);
            var duration = Math.max(1, xenomorph.attackDurationInTicks.get());
            return new AnimationTime(animation, elapsedTicks * profile.animationLengthSeconds(animation) / duration);
        }

        var animation = xenomorph.isUnderWater()
            ? profile.firstAnimation("swim")
            // ⭐⭐ THE CRAWL POSE MUST MATCH WHAT THE ANIMATOR IS ACTUALLY PLAYING.
            //
            // ⚠⚠ THIS ALWAYS PICKED THE MOVING GAIT. The animator plays `crawl` only while a crawling xenomorph
            // is MOVING and `crawl.idle` when it is stationary - but the hitboxes replayed `crawl` either way. A
            // motionless crawling alien therefore had its limb volumes posed from a walk cycle while the model
            // showed the idle: the crest was plainly visible, the shot passed through empty space beside the
            // volume, and the head-only caliber immunity never triggered. [stated] "the small bullet immunity to
            // the crests works when its standing but when crawling it loses this", and "the crest is visible so
            // its not being tucked inside the torso" - it was not tucked, it was in the wrong pose.
            //
            // ⚠ THE STANDING BRANCH BELOW ALREADY DID THIS CORRECTLY (walk vs run vs idle by movement), which is
            // exactly why the bug only showed while crawling.
            : xenomorph.getCrawlingManager().isCrawling()
                ? xenomorph.isMovingHorizontally.get() && xenomorph.onGround()
                    ? profile.firstAnimation("crawl", "crawlidle", "crawl.idle")
                    : profile.firstAnimation("crawl.idle", "crawlidle", "crawl")
                : xenomorph.isMovingHorizontally.get() && xenomorph.onGround()
                    ? xenomorph.isMovingQuickly.get() ? profile.firstAnimation("run") : profile.firstAnimation("walk")
                    : profile.firstAnimation(quad ? "quad.idle" : "idle", "idle");
        return new AnimationTime(animation, (xenomorph.tickCount + partialTick) / 20.0D);
    }

    /**
     * ⚠ THE CLAW IS MIRRORED, so the side has to be resolved the same way the dispatcher resolved it - through
     * {@link MirroredAttackSide}, whose alternation is seeded from the synced attack start time precisely so that it
     * cannot flip mid-swing and land the hitboxes on the opposite arm from the one the player can see moving.
     */
    /** Crawl attacks: the bite is unmirrored, the claw mirrors like every other arm swing. */
    private static String[] crawlAttackCandidates(Xenomorph xenomorph, String attackName) {
        if (attackName.contains("bite")) {
            return new String[] { "crawl.attack.bite", "attack.crawlbite", "crawlbite" };
        }

        var side = MirroredAttackSide.useLeftArm(xenomorph) ? "left" : "right";

        return new String[] {
            "crawl.attack." + side,
            "crawl.attack.right"
        };
    }

    /** The backhand shove - mirrored, and only the converted castes have it, so there are no legacy fallbacks. */
    private static String[] backhandCandidates(Xenomorph xenomorph) {
        var side = MirroredAttackSide.useLeftArm(xenomorph) ? "left" : "right";

        return new String[] {
            "attack.backhand." + side,
            "attack.backhand.right"
        };
    }

    private static String[] clawCandidates(Xenomorph xenomorph, boolean quad) {
        var side = MirroredAttackSide.useLeftArm(xenomorph) ? "left" : "right";

        return new String[] {
            (quad ? "quad.attack.claw." : "attack.claw.") + side,
            "attack.claw." + side,
            "fullattackclaw",
            "fullattackarm",
            "attackclaw",
            "attack.claw",
            "attackclaw.rightarm",
            "clawattackquad.rightarm"
        };
    }

    /**
     * Only the spitter has two standing postures. Everything else is permanently upright as far as this adapter is
     * concerned, and asking for a quad clip it does not have would just fall through to the biped candidate anyway.
     */
    private static boolean isQuadPosture(Xenomorph xenomorph) {
        return xenomorph instanceof Spitter spitter && spitter.isQuadPosture.get();
    }

    /**
     * ⭐⭐ HOW HARD A CASTE'S LIMBS ARE TO SHOOT OFF, as a multiplier on the limb DAMAGE THRESHOLD - the pool a limb
     * absorbs before it detaches.
     * <p>
     * [stated] "the crusher is supposed to be heavily armored so its limbs should have 4x the resistance to being shot
     * off", then "harbinger, empress, and queen have resistance to their limbs like the crusher. queens have 4x
     * resistance and harbinger/empress is 5x resistance."
     * </p>
     * <p>
     * ⚠ THIS IS THE *THRESHOLD*, NOT THE CALIBER RULE. The two are separate on purpose and do different jobs: this
     * decides how much punishment a limb takes before coming off, {@code BulletResistance} decides how much of a given
     * round lands at all. A flat multiplier here cannot tell a pistol from a sniper, which is exactly why the caliber
     * half had to live somewhere else.
     * </p>
     * <p>
     * ⚠ KEYED ON THE PREFIX, so it is the ART PROFILE that is plated, not the entity class - which means every strain
     * of a caste inherits it automatically and no registration is needed.
     * </p>
     */
    private static float limbThresholdMultiplierFor(String prefix) {
        return switch (prefix) {
            case "crusher", "queen" -> 4.0F;
            case "harbinger", "empress" -> 5.0F;
            default -> 1.0F;
        };
    }

    private static AnimatedGeoHitboxProfile profile(String prefix) {
        var roots = new HashMap<String, AnimatedGeoHitboxProfile.LimbRule>();
        var limbScale = limbThresholdMultiplierFor(prefix);

        // ⚠ EVERY CASTE KEEPS THE ORDINARY 2.0 HEADSHOT MULTIPLIER, plated ones included. All the head and arm
        // armour lives in the CALIBER rule in BulletResistance, because a flat multiplier here would tax every
        // caliber alike - a pistol and a sniper would be reduced identically, which is not what any of these
        // castes were specified to do.
        roots.put("gHead", rule(prefix + "_head", 2.0F, 0.0F, 0.08D));
        if (AdultXenomorphHitboxCatalog.isHeadOnlyModel(prefix)) {
            return AnimatedGeoHitboxProfile.load(prefix, roots);
        }
        roots.put("gLeftShoulder", rule(prefix + "_left_arm", 1.0F, 25.0F * limbScale, 0.12D));
        roots.put("gRightShoulder", rule(prefix + "_right_arm", 1.0F, 25.0F * limbScale, 0.12D));
        roots.put("gLeftLeg", rule(prefix + "_left_leg", 1.0F, 30.0F * limbScale, 0.12D));
        roots.put("gRightLeg", rule(prefix + "_right_leg", 1.0F, 30.0F * limbScale, 0.12D));
        roots.put("gTail1", rule(prefix + "_tail", 1.0F, 35.0F * limbScale, 0.18D));

        // These are non-detachable extensions of the normal body hitbox. Their roots are deliberately direct-only:
        // recursing into the head and arms would make the same authored cubes belong to both torso and limb regions.
        if (prefix.equals("queen") || prefix.equals("empress") || prefix.equals("harbinger")) {
            roots.put("gUpperBody", directRule(prefix + "_torso", 1.0F, 0.08D));
        }
        if (prefix.equals("harbinger")) {
            roots.put("gLeftWhip", rule(prefix + "_left_back_whip", 1.0F, 0.0F, 0.12D));
            roots.put("gRightWhip", rule(prefix + "_right_back_whip", 1.0F, 0.0F, 0.12D));
        }
        return AnimatedGeoHitboxProfile.load(prefix, roots);
    }

    private static AnimatedGeoHitboxProfile.LimbRule rule(String id, float healthDamageMultiplier, float threshold, double padding) {
        return new AnimatedGeoHitboxProfile.LimbRule(
            AlienResources.location(id),
            healthDamageMultiplier,
            threshold == 0.0F ? 0.0F : 1.0F,
            threshold,
            padding
        );
    }

    private static AnimatedGeoHitboxProfile.LimbRule directRule(String id, float healthDamageMultiplier, double padding) {
        return new AnimatedGeoHitboxProfile.LimbRule(AlienResources.location(id), healthDamageMultiplier, 0.0F, 0.0F, padding, false);
    }

    private record AnimationTime(
        String name,
        double timeSeconds
    ) {}

    private record CachedVolumes(
        long gameTime,
        AnimatedGeoHitboxProfile profile,
        List<LimbHitboxVolume> volumes
    ) {}

    private StandardXenomorphLimbHitboxes() {}
}
