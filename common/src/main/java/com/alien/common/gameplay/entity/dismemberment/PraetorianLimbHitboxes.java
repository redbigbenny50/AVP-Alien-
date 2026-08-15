package com.alien.common.gameplay.entity.dismemberment;

import com.alien.AlienResources;
import com.alien.common.gameplay.entity.living.alien.xenomorph.praetorian.Praetorian;
import com.alien.common.gameplay.entity.living.alien.xenomorph.praetorian.PraetorianAnimationRefs;
import com.blib.api.common.dismemberment.v1.hitbox.LimbHitboxRegistry;
import com.blib.api.common.dismemberment.v1.hitbox.LimbHitboxVolume;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Praetorian's server-authoritative animated limb hurtbox registration. */
public final class PraetorianLimbHitboxes {

    public static final ResourceLocation HEAD = AlienResources.location("praetorian_head");

    public static final ResourceLocation LEFT_ARM = AlienResources.location("praetorian_left_arm");

    public static final ResourceLocation RIGHT_ARM = AlienResources.location("praetorian_right_arm");

    public static final ResourceLocation LEFT_LEG = AlienResources.location("praetorian_left_leg");

    public static final ResourceLocation RIGHT_LEG = AlienResources.location("praetorian_right_leg");

    public static final ResourceLocation TAIL = AlienResources.location("praetorian_tail");

    private static final float HEADSHOT_DAMAGE_MULTIPLIER = 2.0F;

    private static final float LIMB_DAMAGE_MULTIPLIER = 1.0F;

    /* These are intentionally per-limb pools; all tail cubes share the one tail id and therefore one pool. */
    private static final float ARM_DAMAGE_THRESHOLD = 45.0F;

    private static final float LEG_DAMAGE_THRESHOLD = 55.0F;

    private static final float TAIL_DAMAGE_THRESHOLD = 65.0F;

    /**
     * A client renders an entity between two server ticks; a firearm raycast is processed on the server tick. Evaluate
     * the authored pose at that tick's midpoint so a fast moving tail is where the player sees it, without trusting a
     * client supplied timestamp or position.
     */
    private static final double SERVER_RENDER_POSE_LEAD_TICKS = 0.5D;

    private static final Map<Praetorian, CachedVolumes> SERVER_VOLUMES = Collections.synchronizedMap(new WeakHashMap<>());

    private static final AnimatedGeoHitboxProfile PROFILE = AnimatedGeoHitboxProfile.load(
        "praetorian",
        Map.of(
            "gHead",
            head(),
            "gLeftShoulder",
            limb("praetorian_left_arm", ARM_DAMAGE_THRESHOLD),
            "gRightShoulder",
            limb("praetorian_right_arm", ARM_DAMAGE_THRESHOLD),
            "gLeftLeg",
            limb("praetorian_left_leg", LEG_DAMAGE_THRESHOLD),
            "gRightLeg",
            limb("praetorian_right_leg", LEG_DAMAGE_THRESHOLD),
            "gTail1",
            limb("praetorian_tail", TAIL_DAMAGE_THRESHOLD)
        )
    );

    static void register(ResourceLocation... entityTypeIds) {
        for (var entityTypeId : entityTypeIds) {
            LimbHitboxRegistry.register(entityTypeId, entity -> {
                if (!(entity instanceof Praetorian praetorian)) {
                    return java.util.List.of();
                }
                return serverVolumes(praetorian);
            });
        }
    }

    /**
     * Client debug rendering uses the current fractional frame; firearm hit detection calls this with zero on the
     * authoritative server tick. Both paths otherwise evaluate the same model asset and animation clock.
     */
    public static List<LimbHitboxVolume> volumes(Praetorian praetorian, double partialTick) {
        var animation = animationFor(praetorian, partialTick);
        return PROFILE.volumes(praetorian, animation.name(), animation.timeSeconds());
    }

    private static List<LimbHitboxVolume> serverVolumes(Praetorian praetorian) {
        var gameTime = praetorian.level().getGameTime();
        var cached = SERVER_VOLUMES.get(praetorian);
        if (cached != null && cached.gameTime() == gameTime) {
            return cached.volumes();
        }

        var volumes = List.copyOf(volumes(praetorian, SERVER_RENDER_POSE_LEAD_TICKS));
        SERVER_VOLUMES.put(praetorian, new CachedVolumes(gameTime, volumes));
        return volumes;
    }

    private static AnimationTime animationFor(Praetorian praetorian, double partialTick) {
        var attackType = praetorian.attackType.get();
        if (!attackType.isNone()) {
            var animation = attackType == Praetorian.BITE
                ? PraetorianAnimationRefs.ATTACK_BITE_ANIMATION_NAME
                : attackType == Praetorian.CLAW
                    // ⚠ THE SIDE MUST MATCH WHAT THE DISPATCHER PICKED. This runs EVERY TICK of a swing while the
                    // dispatcher is asked ONCE, so it resolves through the same seeded helper - otherwise the arm
                    // hitboxes sit on the opposite arm from the one the player can see moving.
                    ? (MirroredAttackSide.useLeftArm(praetorian)
                        ? PraetorianAnimationRefs.ATTACK_CLAW_LEFT_ANIMATION_NAME
                        : PraetorianAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME)
                    : attackType == Praetorian.CRAWL_CLAW
                        ? (MirroredAttackSide.useLeftArm(praetorian)
                            ? PraetorianAnimationRefs.CRAWL_ATTACK_LEFT_ANIMATION_NAME
                            : PraetorianAnimationRefs.CRAWL_ATTACK_RIGHT_ANIMATION_NAME)
                        : attackType == Praetorian.CRAWL_BITE
                            ? PraetorianAnimationRefs.CRAWL_ATTACK_BITE_ANIMATION_NAME
                            : attackType == Praetorian.BACKHAND
                                // The backhand mirrors too, so its hitboxes resolve through the same seed.
                                ? (MirroredAttackSide.useLeftArm(praetorian)
                                    ? PraetorianAnimationRefs.ATTACK_BACKHAND_LEFT_ANIMATION_NAME
                                    : PraetorianAnimationRefs.ATTACK_BACKHAND_RIGHT_ANIMATION_NAME)
                                : PraetorianAnimationRefs.ATTACK_TAIL_ANIMATION_NAME;
            var elapsedTicks = Math.max(
                0.0D,
                praetorian.level().getGameTime() - praetorian.attackStartedAtGameTime.get() + partialTick
            );
            var duration = Math.max(1, praetorian.attackDurationInTicks.get());
            return new AnimationTime(animation, elapsedTicks * PROFILE.animationLengthSeconds(animation) / duration);
        }

        var animation = praetorian.isUnderWater()
            ? PraetorianAnimationRefs.SWIM_ANIMATION_NAME
            // ⭐ Same crawl-pose match as StandardXenomorphLimbHitboxes - the moving gait and the crawl idle are
            // different poses, and replaying the wrong one puts the limb volumes where the model is not.
            : praetorian.getCrawlingManager().isCrawling()
                ? praetorian.isMovingHorizontally.get() && praetorian.onGround()
                    ? PraetorianAnimationRefs.CRAWL_ANIMATION_NAME
                    : PraetorianAnimationRefs.CRAWL_IDLE_ANIMATION_NAME
                : praetorian.isMovingHorizontally.get() && praetorian.onGround()
                    ? praetorian.isMovingQuickly.get()
                        ? PraetorianAnimationRefs.RUN_ANIMATION_NAME
                        : PraetorianAnimationRefs.WALK_ANIMATION_NAME
                    : PraetorianAnimationRefs.IDLE_ANIMATION_NAME;
        return new AnimationTime(animation, (praetorian.tickCount + partialTick) / 20.0D);
    }

    private static AnimatedGeoHitboxProfile.LimbRule head() {
        return new AnimatedGeoHitboxProfile.LimbRule(
            AlienResources.location("praetorian_head"),
            HEADSHOT_DAMAGE_MULTIPLIER,
            0.0F,
            0.0F,
            0.08D
        );
    }

    private static AnimatedGeoHitboxProfile.LimbRule limb(String id, float threshold) {
        return new AnimatedGeoHitboxProfile.LimbRule(
            AlienResources.location(id),
            1.0F,
            LIMB_DAMAGE_MULTIPLIER,
            threshold,
            id.contains("tail") ? 0.18D : 0.12D
        );
    }

    private record AnimationTime(
        String name,
        double timeSeconds
    ) {}

    private record CachedVolumes(
        long gameTime,
        List<LimbHitboxVolume> volumes
    ) {}

    private PraetorianLimbHitboxes() {}
}
