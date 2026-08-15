package com.alien.common.registry.init;

import com.alien.Alien;
import com.alien.AlienResources;
import com.alien.common.gameplay.entity.living.alien.ovomorph.Ovomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.CocoonSourceForm;
import com.alien.common.gameplay.entity.living.alien.xenomorph.CocoonState;
import com.alien.common.model.alien.variant.AlienVariant;
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

    /**
     * Withered mark: wither-immune already (effect tag), black smoke aura, attacks inflict wither. NBT-persisted and
     * deliberately NOT in {@code GrowthManager.TRANSITION_NBT_KEY_BLACKLIST}, so it rides every growth transition - a
     * withered burster becomes a withered adult becomes, potentially, a withered queen.
     */
    /**
     * Born of an irradiated host: this alien grows into a BOILER instead of the drone/runner it would otherwise become.
     * Persistent and deliberately NOT transition-blacklisted, so the mark rides chestburster -> adolescent -> adult and
     * is still readable at the one transition that matters. Not networked - no client visual.
     */
    public static final BLibHolder<DataSyncKey<Boolean>> ALIEN_IS_BOILER_DESTINED = create(
        "alien_is_boiler_destined",
        builder -> builder.persistent("avpBoilerDestined", Codec.BOOL)
            .build(false)
    );

    public static final BLibHolder<DataSyncKey<Boolean>> ALIEN_IS_WITHERED = create(
        "alien_is_withered",
        builder -> builder.networkSynchronized(StreamCodecs.BOOLEAN)
            .persistent("avpWithered", Codec.BOOL)
            .build(false)
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

    public static final BLibHolder<DataSyncKey<Integer>> XENOMORPH_ATTACK_STARTED_AT_GAME_TIME = create(
        "xenomorph_attack_started_at_game_time",
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

    /** ⭐ The razor claw dodge buff: ticks remaining. Networked (the animator gates the flurry on it) + persistent. */
    public static final BLibHolder<DataSyncKey<Integer>> RAZOR_CLAW_DODGE_BUFF_TICKS = create(
        "razor_claw_dodge_buff_ticks",
        builder -> builder.networkSynchronized(StreamCodecs.INT)
            .persistent("dodgeBuffTicks", Codec.INT)
            .build(0)
    );

    public static final BLibHolder<DataSyncKey<Integer>> RAZOR_CLAW_DODGE_COOLDOWN_TICKS = create(
        "razor_claw_dodge_cooldown_ticks",
        builder -> builder.networkSynchronized(StreamCodecs.INT)
            .persistent("dodgeCooldownTicks", Codec.INT)
            .build(0)
    );

    /** ⭐ Bumped on every dodge so the animator can edge-detect and play the clip exactly once. */
    public static final BLibHolder<DataSyncKey<Integer>> RAZOR_CLAW_DODGE_ID = create(
        "razor_claw_dodge_id",
        builder -> builder.networkSynchronized(StreamCodecs.INT)
            .build(0)
    );

    /** ⭐ The empress's scream. Her own keys, not the queen's - she is not a Queen subclass and shares no state. */
    public static final BLibHolder<DataSyncKey<Integer>> EMPRESS_SCREAM_COOLDOWN_TICKS = create(
        "empress_scream_cooldown_ticks",
        builder -> builder.networkSynchronized(StreamCodecs.INT)
            .persistent("screamCooldownTicks", Codec.INT)
            .build(0)
    );

    public static final BLibHolder<DataSyncKey<Boolean>> EMPRESS_SCREAMED_AT_FIRST_THRESHOLD = create(
        "empress_screamed_at_first_threshold",
        builder -> builder.networkSynchronized(StreamCodecs.BOOLEAN)
            .persistent("screamedAtFirstThreshold", Codec.BOOL)
            .build(false)
    );

    public static final BLibHolder<DataSyncKey<Boolean>> EMPRESS_SCREAMED_AT_SECOND_THRESHOLD = create(
        "empress_screamed_at_second_threshold",
        builder -> builder.networkSynchronized(StreamCodecs.BOOLEAN)
            .persistent("screamedAtSecondThreshold", Codec.BOOL)
            .build(false)
    );

    public static final BLibHolder<DataSyncKey<Integer>> EMPRESS_SCREAM_ID = create(
        "empress_scream_id",
        builder -> builder.networkSynchronized(StreamCodecs.INT).build(0)
    );

    /** ⭐ The queen's scream: cooldown and the two threshold latches. Persistent so a reload cannot re-trigger it. */
    public static final BLibHolder<DataSyncKey<Integer>> QUEEN_SCREAM_COOLDOWN_TICKS = create(
        "queen_scream_cooldown_ticks",
        builder -> builder.networkSynchronized(StreamCodecs.INT)
            .persistent("screamCooldownTicks", Codec.INT)
            .build(0)
    );

    public static final BLibHolder<DataSyncKey<Boolean>> QUEEN_SCREAMED_AT_FIRST_THRESHOLD = create(
        "queen_screamed_at_first_threshold",
        builder -> builder.networkSynchronized(StreamCodecs.BOOLEAN)
            .persistent("screamedAtFirstThreshold", Codec.BOOL)
            .build(false)
    );

    public static final BLibHolder<DataSyncKey<Boolean>> QUEEN_SCREAMED_AT_SECOND_THRESHOLD = create(
        "queen_screamed_at_second_threshold",
        builder -> builder.networkSynchronized(StreamCodecs.BOOLEAN)
            .persistent("screamedAtSecondThreshold", Codec.BOOL)
            .build(false)
    );

    /** ⭐ Bumped on every scream so the animator can edge-detect and play the clip exactly once. */
    public static final BLibHolder<DataSyncKey<Integer>> QUEEN_SCREAM_ID = create(
        "queen_scream_id",
        builder -> builder.networkSynchronized(StreamCodecs.INT).build(0)
    );

    /** ⭐ The defensive curl. Networked so the animator can play the stance, persistent so it survives a reload. */
    public static final BLibHolder<DataSyncKey<Boolean>> CHRYSALIS_IS_DEFENDING = create(
        "chrysalis_is_defending",
        builder -> builder.networkSynchronized(StreamCodecs.BOOLEAN)
            .persistent("isDefending", Codec.BOOL)
            .build(false)
    );

    public static final BLibHolder<DataSyncKey<Integer>> CHRYSALIS_DEFENSE_TICKS_REMAINING = create(
        "chrysalis_defense_ticks_remaining",
        builder -> builder.networkSynchronized(StreamCodecs.INT)
            .persistent("defenseTicksRemaining", Codec.INT)
            .build(0)
    );

    public static final BLibHolder<DataSyncKey<Integer>> CHRYSALIS_DEFENSE_COOLDOWN_TICKS = create(
        "chrysalis_defense_cooldown_ticks",
        builder -> builder.networkSynchronized(StreamCodecs.INT)
            .persistent("defenseCooldownTicks", Codec.INT)
            .build(0)
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

    /**
     * ⭐ THE SPITTER'S POSTURE. True = down on all fours, false = upright.
     * <p>
     * The spitter is the only caste with two standing postures, and the choice has to be visible to BOTH sides: the
     * client picks the idle and attack clip from it, and the server replays the same attack clip every tick to work out
     * where the limb hitboxes are. A client-only flag would put the arm hitboxes in a different posture from the arm
     * the player can see swinging.
     * </p>
     * <p>
     * NOT persisted - posture re-derives itself within a tick or two of loading, and a spitter frozen mid-charge in NBT
     * should come back standing rather than crouched in a pose nothing is driving.
     * </p>
     */
    public static final BLibHolder<DataSyncKey<Boolean>> SPITTER_IS_QUAD_POSTURE = create(
        "spitter_is_quad_posture",
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

    /**
     * ⭐ The STRAIN OF THE ROYAL an eggsack grew out of, as an {@link AlienVariant} id.
     * <p>
     * An ovipositor has no variant of its own - there is one entity type for every strain - so the renderer read the
     * strain off the queen it rides. That breaks the moment she is knocked off it: the sack deliberately LINGERS as
     * scenery with no vehicle, and with nothing to read it fell back to the plain sheet. An aberrant hive's eggsack
     * turned black the instant its queen stood up.
     * </p>
     * <p>
     * ⚠ NETWORK-SYNCHRONIZED AND PERSISTENT both. Synchronized because the texture is resolved client-side; persistent
     * so an abandoned sack still remembers its strain after a reload, which is exactly when nothing else can tell it.
     * </p>
     */
    public static final BLibHolder<DataSyncKey<Integer>> OVIPOSITOR_ROYAL_VARIANT_ID = create(
        "ovipositor_royal_variant_id",
        builder -> builder.networkSynchronized(StreamCodecs.INT)
            .persistent("RoyalVariantId", Codec.INT)
            .build(AlienVariant.NORMAL.getId())
    );

    private static <T> BLibHolder<DataSyncKey<T>> create(String path, Function<DataSyncKey.Builder<T>, DataSyncKey<T>> factory) {
        var resourceLocation = AlienResources.location(path);
        return REGISTRY.createHolder(path, () -> factory.apply(new DataSyncKey.Builder<>(resourceLocation)));
    }

    public static void initialize() {
        REGISTRY.registerAll();
    }
}
