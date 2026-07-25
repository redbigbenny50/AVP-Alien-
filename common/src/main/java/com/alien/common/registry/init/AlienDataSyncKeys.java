package com.alien.common.registry.init;

import com.alien.Alien;
import com.alien.AlienResources;
import com.alien.common.gameplay.entity.living.alien.ovomorph.Ovomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.CocoonSourceForm;
import com.alien.common.gameplay.entity.living.alien.xenomorph.CocoonState;
import com.blib.api.common.data_sync.v1.model.DataSyncKey;
import com.blib.api.common.registry.v1.BLibBuiltInRegistries;
import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import com.just.codec.stream.impl.StreamCodecs;
import com.mojang.serialization.Codec;

import java.util.function.Function;

public class AlienDataSyncKeys {

    private static final BLibRegistry<DataSyncKey<?>> REGISTRY = Alien.MOD.registries().create(BLibBuiltInRegistries.DATA_SYNC_KEYS);

    public static final BLibHolder<DataSyncKey<Integer>> ACID_MULTIPLIER = create(
        "acid_multiplier",
        builder -> builder.networkSynchronized(StreamCodecs.INT)
            .persistent("Multiplier", Codec.INT)
            .build(1)
    );

    public static final BLibHolder<DataSyncKey<Integer>> ACID_TICK_COUNT_FOR_MULTIPLIER = create(
        "acid_tick_count_for_multiplier",
        builder -> builder.persistent("TickCountForMultiplier", Codec.INT)
            .build(0)
    );

    public static final BLibHolder<DataSyncKey<Boolean>> ADOLESCENT_HAS_DORSAL_TUBES = create(
        "adolescent_has_dorsal_tubes",
        builder -> builder.networkSynchronized(StreamCodecs.BOOLEAN)
            .build(true)
    );

    public static final BLibHolder<DataSyncKey<Float>> ALIEN_MOLT_ALPHA = create(
        "alien_molt_alpha",
        builder -> builder.networkSynchronized(StreamCodecs.FLOAT)
            .build(0F)
    );

    public static final BLibHolder<DataSyncKey<Boolean>> ALIEN_IS_POISONED = create(
        "alien_is_poisoned",
        builder -> builder.networkSynchronized(StreamCodecs.BOOLEAN)
            .persistent("isPoisoned", Codec.BOOL)
            .build(false)
    );

    public static final BLibHolder<DataSyncKey<Boolean>> ALIEN_IS_MOVING_QUICKLY = create(
        "alien_is_moving_quickly",
        builder -> builder.networkSynchronized(StreamCodecs.BOOLEAN)
            .build(false)
    );

    public static final BLibHolder<DataSyncKey<Integer>> OVOMORPH_DESIRE_TO_HATCH = create(
        "ovomorph_desire_to_hatch",
        builder -> builder.persistent("desireToHatch", Codec.INT)
            .build(0)
    );

    public static final BLibHolder<DataSyncKey<Integer>> OVOMORPH_HATCH_DURATION_IN_TICKS = create(
        "ovomorph_hatch_duration_in_ticks",
        builder -> builder.persistent("hatchDurationInTicks", Codec.INT)
            .build(-1)
    );

    public static final BLibHolder<DataSyncKey<Byte>> OVOMORPH_HATCH_STATE = create(
        "ovomorph_hatch_state",
        builder -> builder.networkSynchronized(StreamCodecs.BYTE)
            .persistent("hatchState", Codec.BYTE)
            .build((byte) Ovomorph.DEFAULT_HATCH_STATE.getId())
    );

    public static final BLibHolder<DataSyncKey<Boolean>> OVOMORPH_IS_ROOTED = create(
        "ovomorph_is_rooted",
        builder -> builder.networkSynchronized(StreamCodecs.BOOLEAN)
            .persistent("isRooted", Codec.BOOL)
            .build(true)
    );

    public static final BLibHolder<DataSyncKey<Byte>> OVOMORPH_MAXIMUM_SPAWN_COUNT = create(
        "ovomorph_maximum_spawn_count",
        builder -> builder.networkSynchronized(StreamCodecs.BYTE)
            .persistent("maximumSpawnCount", Codec.BYTE)
            .build((byte) 1)
    );

    public static final BLibHolder<DataSyncKey<Integer>> OVOMORPH_REMAINING_SPAWN_DELAY_IN_TICKS = create(
        "ovomorph_remaining_spawn_delay_in_ticks",
        builder -> builder.persistent("remainingSpawnDelayInTicks", Codec.INT)
            .build(-1)
    );

    public static final BLibHolder<DataSyncKey<Integer>> OVOMORPH_SPAWN_COUNT = create(
        "ovomorph_spawn_count",
        builder -> builder.persistent("spawnCount", Codec.INT)
            .build(0)
    );

    public static final BLibHolder<DataSyncKey<Boolean>> FACEHUGGER_IS_LUNGING = create(
        "facehugger_is_lunging",
        builder -> builder.networkSynchronized(StreamCodecs.BOOLEAN)
            .build(false)
    );

    public static final BLibHolder<DataSyncKey<Boolean>> PARASITE_IS_FERTILE = create(
        "parasite_is_fertile",
        builder -> builder.networkSynchronized(StreamCodecs.BOOLEAN)
            .persistent("isFertile", Codec.BOOL)
            .build(true)
    );

    public static final BLibHolder<DataSyncKey<Integer>> PARASITE_TICKS_ATTACHED_TO_HOST = create(
        "parasite_ticks_attached_to_host",
        builder -> builder.persistent("ticksAttachedToHost", Codec.INT)
            .build(0)
    );

    /**
     * The SERVER'S OWN TRUTH about a parasite's attachment: the entity id of the host it is riding, or -1 when
     * detached. Entity ids are per-session, so this is deliberately NOT persistent - it is a live wire for clients,
     * whose passenger lists can go stale (a refused or lost dismount leaves a ghost hugger glued on). The client
     * self-heals from this value in {@code Parasite.tick}.
     */
    public static final BLibHolder<DataSyncKey<Integer>> PARASITE_ATTACHED_HOST_ID = create(
        "parasite_attached_host_id",
        builder -> builder.networkSynchronized(StreamCodecs.INT)
            .build(-1)
    );

    public static final BLibHolder<DataSyncKey<Integer>> QUEEN_BIND_CHAIN_COUNT = create(
        "queen_bind_chain_count",
        builder -> builder.networkSynchronized(StreamCodecs.INT)
            .build(0)
    );

    public static final BLibHolder<DataSyncKey<Boolean>> QUEEN_HAS_INHIBITOR = create(
        "queen_has_inhibitor",
        builder -> builder.networkSynchronized(StreamCodecs.BOOLEAN)
            .persistent("hasInhibitor", Codec.BOOL)
            .build(false)
    );

    public static final BLibHolder<DataSyncKey<Boolean>> QUEEN_IS_INCAPACITATED = create(
        "queen_is_incapacitated",
        builder -> builder.networkSynchronized(StreamCodecs.BOOLEAN)
            .persistent("isIncapacitated", Codec.BOOL)
            .build(false)
    );

    public static final BLibHolder<DataSyncKey<Boolean>> QUEEN_IS_TRACKED = create(
        "queen_is_tracked",
        builder -> builder.networkSynchronized(StreamCodecs.BOOLEAN)
            .persistent("isTracked", Codec.BOOL)
            .build(false)
    );

    public static final BLibHolder<DataSyncKey<Integer>> XENOMORPH_ATTACK_DURATION_IN_TICKS = create(
        "xenomorph_attack_duration_in_ticks",
        builder -> builder.networkSynchronized(StreamCodecs.INT)
            .build(0)
    );

    public static final BLibHolder<DataSyncKey<Integer>> XENOMORPH_ATTACK_ID = create(
        "xenomorph_attack_id",
        builder -> builder.networkSynchronized(StreamCodecs.INT)
            .build(0)
    );

    public static final BLibHolder<DataSyncKey<Boolean>> CHRYSALIS_IS_ROLLING = create(
        "chrysalis_is_rolling",
        builder -> builder.networkSynchronized(StreamCodecs.BOOLEAN)
            .build(false)
    );

    public static final BLibHolder<DataSyncKey<Float>> CHRYSALIS_ROLL_YAW = create(
        "chrysalis_roll_yaw",
        builder -> builder.networkSynchronized(StreamCodecs.FLOAT)
            .build(0F)
    );

    public static final BLibHolder<DataSyncKey<Integer>> CHRYSALIS_ROLL_COOLDOWN_TICKS = create(
        "chrysalis_roll_cooldown_ticks",
        builder -> builder.networkSynchronized(StreamCodecs.INT)
            .persistent("rollCooldownTicks", Codec.INT)
            .build(0)
    );

    public static final BLibHolder<DataSyncKey<Boolean>> CHRYSALIS_ROLL_WAS_SMASHED = create(
        "chrysalis_roll_was_smashed",
        builder -> builder.networkSynchronized(StreamCodecs.BOOLEAN)
            .build(false)
    );

    public static final BLibHolder<DataSyncKey<Boolean>> CHRYSALIS_IS_STUNNED = create(
        "chrysalis_is_stunned",
        builder -> builder.networkSynchronized(StreamCodecs.BOOLEAN)
            .build(false)
    );

    public static final BLibHolder<DataSyncKey<Integer>> CHRYSALIS_STUN_DURATION_TICKS = create(
        "chrysalis_stun_duration_ticks",
        builder -> builder.networkSynchronized(StreamCodecs.INT)
            .build(0)
    );

    public static final BLibHolder<DataSyncKey<AttackType>> ATTACK_TYPE = create(
        "attack_type",
        builder -> builder.networkSynchronized(AttackType.CODEC)
            .build(AttackType.NONE)
    );

    public static final BLibHolder<DataSyncKey<Boolean>> XENOMORPH_IS_LUNGING = create(
        "xenomorph_is_lunging",
        builder -> builder.networkSynchronized(StreamCodecs.BOOLEAN)
            .build(false)
    );

    public static final BLibHolder<DataSyncKey<Boolean>> XENOMORPH_IS_HIBERNATING = create(
        "xenomorph_is_hibernating",
        builder -> builder.networkSynchronized(StreamCodecs.BOOLEAN)
            .build(false)
    );

    public static final BLibHolder<DataSyncKey<Boolean>> XENOMORPH_IS_DIGGING = create(
        "xenomorph_is_digging",
        builder -> builder.networkSynchronized(StreamCodecs.BOOLEAN)
            .build(false)
    );

    /**
     * Founding-core stand-dig (construction economy step 6): true while the queen is carving her own chamber. Synced,
     * NOT persisted - the carve site re-derives it on the first loaded tick after a reload. The client QueenAnimator
     * drives the digStandStart / standDigging / digStandStop triptych off its edges, because animation dispatch only
     * works client-side.
     */
    public static final BLibHolder<DataSyncKey<Boolean>> QUEEN_IS_STAND_DIGGING = create(
        "queen_is_stand_digging",
        builder -> builder.networkSynchronized(StreamCodecs.BOOLEAN)
            .build(false)
    );

    /**
     * Carve-crew dig gait (construction economy step 5): 0 = not on a carve crew, 1 = digger (walk dig at 70%), 2 =
     * placer (walk dig at 50%). Synced, NOT persisted - crews are transient and re-sourced after a reload. The client
     * DroneAnimator folds it into the locomotion selection, because animation dispatch only works client-side.
     */
    public static final BLibHolder<DataSyncKey<Integer>> DRONE_CARVE_DIG_MODE = create(
        "drone_carve_dig_mode",
        builder -> builder.networkSynchronized(StreamCodecs.INT)
            .build(0)
    );

    /** Runner counterpart of {@link #DRONE_CARVE_DIG_MODE} - runners crew the same digs, placements and repairs. */
    public static final BLibHolder<DataSyncKey<Integer>> RUNNER_CARVE_DIG_MODE = create(
        "runner_carve_dig_mode",
        builder -> builder.networkSynchronized(StreamCodecs.INT)
            .build(0)
    );

    public static final BLibHolder<DataSyncKey<Boolean>> XENOMORPH_IS_CRAWLING = create(
        "xenomorph_is_crawling",
        builder -> builder.networkSynchronized(StreamCodecs.BOOLEAN)
            .build(false)
    );

    public static final BLibHolder<DataSyncKey<CocoonState>> XENOMORPH_COCOON_STATE = create(
        "xenomorph_cocoon_state",
        builder -> builder.networkSynchronized(CocoonState.STREAM_CODEC)
            .build(CocoonState.NONE)
    );

    public static final BLibHolder<DataSyncKey<CocoonSourceForm>> XENOMORPH_COCOON_SOURCE_FORM = create(
        "xenomorph_cocoon_source_form",
        builder -> builder.networkSynchronized(CocoonSourceForm.STREAM_CODEC)
            .build(CocoonSourceForm.NONE)
    );

    public static final BLibHolder<DataSyncKey<Integer>> XENOMORPH_COCOON_ANIMATION_ID = create(
        "xenomorph_cocoon_animation_id",
        builder -> builder.networkSynchronized(StreamCodecs.INT)
            .build(0)
    );

    private static <T> BLibHolder<DataSyncKey<T>> create(String path, Function<DataSyncKey.Builder<T>, DataSyncKey<T>> factory) {
        var resourceLocation = AlienResources.location(path);
        return REGISTRY.createHolder(path, () -> factory.apply(new DataSyncKey.Builder<>(resourceLocation)));
    }

    public static void initialize() {
        REGISTRY.registerAll();
    }
}
