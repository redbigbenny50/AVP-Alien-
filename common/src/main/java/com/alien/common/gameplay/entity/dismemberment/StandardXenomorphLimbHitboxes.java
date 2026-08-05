package com.alien.common.gameplay.entity.dismemberment;

import com.alien.AlienResources;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
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

    private static AnimationTime animationFor(Xenomorph xenomorph, AnimatedGeoHitboxProfile profile, double partialTick) {
        if (!xenomorph.attackType.get().isNone()) {
            var attackName = xenomorph.attackType.get().id();
            var animation = attackName.contains("tail")
                ? profile.firstAnimation(
                    "fullattacktail",
                    "fullquadattacktail",
                    "attacktail",
                    "attack.tail",
                    "attacktail.tail",
                    "tailattackquad.tail"
                )
                : attackName.contains("bite")
                    ? profile.firstAnimation(
                        "fullattackbite",
                        "fullquadattackbite",
                        "attackbite",
                        "attack.crawlbite",
                        "attackbite.head",
                        "biteattack.head"
                    )
                    : profile.firstAnimation(
                        "fullattackclaw",
                        "fullattackarm",
                        "attackclaw",
                        "attack.claw",
                        "attackclaw.rightarm",
                        "clawattackquad.rightarm"
                    );
            var elapsedTicks = Math.max(0.0D, xenomorph.level().getGameTime() - xenomorph.attackStartedAtGameTime.get() + partialTick);
            var duration = Math.max(1, xenomorph.attackDurationInTicks.get());
            return new AnimationTime(animation, elapsedTicks * profile.animationLengthSeconds(animation) / duration);
        }

        var animation = xenomorph.isUnderWater()
            ? profile.firstAnimation("swim")
            : xenomorph.getCrawlingManager().isCrawling()
                ? profile.firstAnimation("crawl", "crawlidle", "crawl.idle")
                : xenomorph.isMovingHorizontally.get() && xenomorph.onGround()
                    ? xenomorph.isMovingQuickly.get() ? profile.firstAnimation("run") : profile.firstAnimation("walk")
                    : profile.firstAnimation("idle");
        return new AnimationTime(animation, (xenomorph.tickCount + partialTick) / 20.0D);
    }

    private static AnimatedGeoHitboxProfile profile(String prefix) {
        var roots = new HashMap<String, AnimatedGeoHitboxProfile.LimbRule>();
        roots.put("gHead", rule(prefix + "_head", 2.0F, 0.0F, 0.08D));
        if (AdultXenomorphHitboxCatalog.isHeadOnlyModel(prefix)) {
            return AnimatedGeoHitboxProfile.load(prefix, roots);
        }
        roots.put("gLeftShoulder", rule(prefix + "_left_arm", 1.0F, 25.0F, 0.12D));
        roots.put("gRightShoulder", rule(prefix + "_right_arm", 1.0F, 25.0F, 0.12D));
        roots.put("gLeftLeg", rule(prefix + "_left_leg", 1.0F, 30.0F, 0.12D));
        roots.put("gRightLeg", rule(prefix + "_right_leg", 1.0F, 30.0F, 0.12D));
        roots.put("gTail1", rule(prefix + "_tail", 1.0F, 35.0F, 0.18D));

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
