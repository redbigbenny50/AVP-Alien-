package com.alien.common.gameplay.entity.dismemberment;

import com.alien.AlienResources;
import com.alien.common.gameplay.entity.living.alien.xenomorph.drone.Drone;
import com.alien.common.gameplay.entity.living.alien.xenomorph.drone.DroneAnimationRefs;
import com.blib.api.common.dismemberment.v1.hitbox.LimbHitboxRegistry;
import com.blib.api.common.dismemberment.v1.hitbox.LimbHitboxVolume;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Drone's additive firearm limb hurtboxes, evaluated from its authored geometry and animations. */
public final class DroneLimbHitboxes {

    public static final ResourceLocation HEAD = AlienResources.location("drone_head");

    public static final ResourceLocation LEFT_ARM = AlienResources.location("drone_left_arm");

    public static final ResourceLocation RIGHT_ARM = AlienResources.location("drone_right_arm");

    public static final ResourceLocation LEFT_LEG = AlienResources.location("drone_left_leg");

    public static final ResourceLocation RIGHT_LEG = AlienResources.location("drone_right_leg");

    public static final ResourceLocation TAIL = AlienResources.location("drone_tail");

    private static final Map<Drone, CachedVolumes> SERVER_VOLUMES = Collections.synchronizedMap(new WeakHashMap<>());

    private static final AnimatedGeoHitboxProfile PROFILE = AnimatedGeoHitboxProfile.load(
        "drone",
        Map.of(
            "gHead",
            head(),
            "gLeftShoulder",
            limb("drone_left_arm", 25.0F),
            "gRightShoulder",
            limb("drone_right_arm", 25.0F),
            "gLeftLeg",
            limb("drone_left_leg", 30.0F),
            "gRightLeg",
            limb("drone_right_leg", 30.0F),
            "gTail1",
            limb("drone_tail", 35.0F)
        )
    );

    static void register(ResourceLocation... entityTypeIds) {
        for (var entityTypeId : entityTypeIds) {
            LimbHitboxRegistry.register(entityTypeId, entity -> entity instanceof Drone drone ? serverVolumes(drone) : List.of());
        }
    }

    public static List<LimbHitboxVolume> volumes(Drone drone, double partialTick) {
        var animation = animationFor(drone, partialTick);
        return PROFILE.volumes(drone, animation.name(), animation.timeSeconds());
    }

    private static List<LimbHitboxVolume> serverVolumes(Drone drone) {
        var gameTime = drone.level().getGameTime();
        var cached = SERVER_VOLUMES.get(drone);
        if (cached != null && cached.gameTime() == gameTime) {
            return cached.volumes();
        }
        var volumes = List.copyOf(volumes(drone, 0.5D));
        SERVER_VOLUMES.put(drone, new CachedVolumes(gameTime, volumes));
        return volumes;
    }

    private static AnimationTime animationFor(Drone drone, double partialTick) {
        var attackType = drone.attackType.get();
        if (!attackType.isNone()) {
            // ⚠ THE CLAW MUST RESOLVE TO THE SAME SIDE THE DISPATCHER CHOSE. This runs SERVER-side to place the limb
            // hitboxes, so picking the other clip of the mirrored pair would put the arm boxes on the opposite side of
            // the body from the arm the player can see swinging. MirroredAttackSide keys off the synced attack start
            // time precisely so both ends agree for the whole swing.
            var animation = attackType == Drone.BITE
                ? DroneAnimationRefs.ATTACK_BITE_ANIMATION_NAME
                : attackType == Drone.CLAW
                    ? (MirroredAttackSide.useLeftArm(drone)
                        ? DroneAnimationRefs.ATTACK_CLAW_LEFT_ANIMATION_NAME
                        : DroneAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME)
                    : DroneAnimationRefs.ATTACK_TAIL_ANIMATION_NAME;
            var elapsed = Math.max(0.0D, drone.level().getGameTime() - drone.attackStartedAtGameTime.get() + partialTick);
            var duration = Math.max(1, drone.attackDurationInTicks.get());
            return new AnimationTime(animation, elapsed * PROFILE.animationLengthSeconds(animation) / duration);
        }
        var animation = drone.isUnderWater()
            ? DroneAnimationRefs.SWIM_ANIMATION_NAME
            // ⭐ Same crawl-pose match as StandardXenomorphLimbHitboxes - the moving gait and the crawl idle are
            // different poses, and replaying the wrong one puts the limb volumes where the model is not.
            : drone.getCrawlingManager().isCrawling()
                ? drone.isMovingHorizontally.get() && drone.onGround()
                    ? DroneAnimationRefs.CRAWL_ANIMATION_NAME
                    : DroneAnimationRefs.CRAWL_IDLE_ANIMATION_NAME
                : drone.isMovingHorizontally.get() && drone.onGround()
                    ? drone.isMovingQuickly.get() ? DroneAnimationRefs.RUN_ANIMATION_NAME : DroneAnimationRefs.WALK_ANIMATION_NAME
                    : DroneAnimationRefs.IDLE_ANIMATION_NAME;
        return new AnimationTime(animation, (drone.tickCount + partialTick) / 20.0D);
    }

    private static AnimatedGeoHitboxProfile.LimbRule head() {
        return new AnimatedGeoHitboxProfile.LimbRule(HEAD, 2.0F, 0.0F, 0.0F, 0.08D);
    }

    private static AnimatedGeoHitboxProfile.LimbRule limb(String id, float threshold) {
        return new AnimatedGeoHitboxProfile.LimbRule(
            AlienResources.location(id),
            1.0F,
            1.0F,
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

    private DroneLimbHitboxes() {}
}
