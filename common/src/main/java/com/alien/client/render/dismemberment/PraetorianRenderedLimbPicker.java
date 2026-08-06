package com.alien.client.render.dismemberment;

import com.alien.AlienResources;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.blib.api.common.dismemberment.v1.hitbox.LimbHitPrediction;
import com.blib.api.common.dismemberment.v1.hitbox.LimbHitPredictionProvider;
import com.blib.api.common.dismemberment.v1.hitbox.LimbHitboxVolume;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tests firearm rays against the cubes BLib actually posed and rendered for a Praetorian.
 * <p>
 * The cache is populated by a render layer, so this class neither evaluates animations nor owns an animation clock. It
 * is queried only while a firearm fires and only sends the selected entity and limb id onward.
 * </p>
 */
public final class PraetorianRenderedLimbPicker implements LimbHitPredictionProvider {

    public static final ResourceLocation HEAD_LIMB = AlienResources.location("praetorian_head");

    public static final ResourceLocation LEFT_ARM_LIMB = AlienResources.location("praetorian_left_arm");

    public static final ResourceLocation RIGHT_ARM_LIMB = AlienResources.location("praetorian_right_arm");

    public static final ResourceLocation LEFT_LEG_LIMB = AlienResources.location("praetorian_left_leg");

    public static final ResourceLocation RIGHT_LEG_LIMB = AlienResources.location("praetorian_right_leg");

    public static final ResourceLocation TAIL_LIMB = AlienResources.location("praetorian_tail");

    public static final ResourceLocation DRONE_HEAD_LIMB = AlienResources.location("drone_head");

    public static final ResourceLocation DRONE_LEFT_ARM_LIMB = AlienResources.location("drone_left_arm");

    public static final ResourceLocation DRONE_RIGHT_ARM_LIMB = AlienResources.location("drone_right_arm");

    public static final ResourceLocation DRONE_LEFT_LEG_LIMB = AlienResources.location("drone_left_leg");

    public static final ResourceLocation DRONE_RIGHT_LEG_LIMB = AlienResources.location("drone_right_leg");

    public static final ResourceLocation DRONE_TAIL_LIMB = AlienResources.location("drone_tail");

    public static final ResourceLocation WARRIOR_HEAD_LIMB = AlienResources.location("warrior_head"), WARRIOR_LEFT_ARM_LIMB = AlienResources
        .location("warrior_left_arm"), WARRIOR_RIGHT_ARM_LIMB = AlienResources.location("warrior_right_arm"), WARRIOR_LEFT_LEG_LIMB =
            AlienResources.location("warrior_left_leg"), WARRIOR_RIGHT_LEG_LIMB = AlienResources.location("warrior_right_leg"),
        WARRIOR_TAIL_LIMB = AlienResources.location("warrior_tail");

    public static final ResourceLocation RUNNER_HEAD_LIMB = AlienResources.location("runner_head"), RUNNER_LEFT_ARM_LIMB = AlienResources
        .location("runner_left_arm"), RUNNER_RIGHT_ARM_LIMB = AlienResources.location("runner_right_arm"), RUNNER_LEFT_LEG_LIMB =
            AlienResources.location("runner_left_leg"), RUNNER_RIGHT_LEG_LIMB = AlienResources.location("runner_right_leg"),
        RUNNER_TAIL_LIMB = AlienResources.location("runner_tail");

    private static final long MAX_SAMPLE_AGE_NANOS = 250_000_000L;

    private static final Map<Integer, Sample> SAMPLES = new ConcurrentHashMap<>();

    public static void publish(int entityId, List<LimbHitboxVolume> tailCubes) {
        if (!tailCubes.isEmpty()) {
            SAMPLES.put(entityId, new Sample(List.copyOf(tailCubes), System.nanoTime()));
        }
    }

    /**
     * Returns the exact cube pose captured from this client's most recent Praetorian render. This is also what F3+B
     * must draw; using the server approximation there would make the diagnostic itself appear to lag behind a tail
     * animation.
     */
    public static List<LimbHitboxVolume> renderedVolumes(int entityId) {
        var sample = SAMPLES.get(entityId);
        if (sample == null) {
            return List.of();
        }
        if (System.nanoTime() - sample.createdAtNanos() > MAX_SAMPLE_AGE_NANOS) {
            SAMPLES.remove(entityId, sample);
            return List.of();
        }
        return sample.cubes();
    }

    @Override
    public Optional<LimbHitPrediction> findNearest(Level level, Vec3 rayStart, Vec3 rayEnd) {
        var now = System.nanoTime();
        var best = SAMPLES.entrySet()
            .stream()
            .flatMap(entry -> {
                var entity = level.getEntity(entry.getKey());
                var sample = entry.getValue();
                if (
                    !(entity instanceof Xenomorph)
                        || entity.isRemoved() || now - sample
                            .createdAtNanos() > MAX_SAMPLE_AGE_NANOS
                ) {
                    SAMPLES.remove(entry.getKey(), sample);
                    return java.util.stream.Stream.empty();
                }
                return sample.cubes()
                    .stream()
                    .map(
                        cube -> cube.clip(rayStart, rayEnd)
                            .map(location -> new Candidate(entity.getId(), cube.limbId(), location))
                            .orElse(null)
                    )
                    .filter(java.util.Objects::nonNull);
            })
            .min(Comparator.comparingDouble(candidate -> rayStart.distanceToSqr(candidate.location())));

        return best.map(
            candidate -> new LimbHitPrediction(candidate.entityId(), candidate.limbId())
        );
    }

    private record Sample(
        List<LimbHitboxVolume> cubes,
        long createdAtNanos
    ) {}

    private record Candidate(
        int entityId,
        ResourceLocation limbId,
        Vec3 location
    ) {}
}
