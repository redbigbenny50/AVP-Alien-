package com.alien.common.gameplay.hive.convoy;

import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.blib.api.common.entity.v1.EntityReserves;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * A traveling group of xenomorphs moving between locations or hunting a player. Sealed so codecs can dispatch on
 * subtype.
 * <p>
 * Phase 8 ships {@link Reinforcement}. Phase 8b adds {@link Migration} (whole-location evacuation) and {@link Raid}
 * (player-hunting counter-attack).
 * <p>
 * Common semantics across all subtypes:
 * <ul>
 * <li>{@link #composition} — the EntityReserves bag of "who's in the convoy." Decrements on combat losses and refunds
 * to a live hive location on disband when possible.</li>
 * <li>{@link #currentPos} — the abstract Vec3 position. Updated every tick by {@link ConvoyTravel}.</li>
 * <li>{@link #dispatchedTick} — when the convoy was first minted; used by raid expiry (Phase 8b) and just informational
 * for reinforcement.</li>
 * </ul>
 */
public sealed interface Convoy {

    ConvoyId id();

    ResourceLocation lineageFactionId();

    ResourceKey<Level> dimension();

    Vec3 currentPos();

    void setCurrentPos(Vec3 newPos);

    EntityReserves composition();

    Map<UUID, EntityType<?>> materializedMembers();

    void trackMaterializedMember(UUID memberId, EntityType<?> entityType);

    EntityType<?> untrackMaterializedMember(UUID memberId);

    int materializedCountMatching(Predicate<EntityType<?>> predicate);

    long dispatchedTick();

    /**
     * Sister-to-sister rebalancing. The default convoy type. Source has surplus reserves; destination is low. The
     * empress dispatches; the convoy travels through abstract space; on arrival, composition pours into the
     * destination's local reserves.
     * <p>
     * See {@code HIVE_REDESIGN_06_CONVOYS.md} § 4.
     */
    final class Reinforcement implements Convoy {

        private final ConvoyId id;

        private final ResourceLocation lineageFactionId;

        private final ResourceKey<Level> dimension;

        private final HiveLocationId sourceLocationId;

        private final HiveLocationId destinationLocationId;

        private final BlockPos destinationPos;

        private final EntityReserves composition;

        private final ConvoyMaterializedMembers materializedMembers;

        private final long dispatchedTick;

        private Vec3 currentPos;

        public Reinforcement(
            ConvoyId id,
            ResourceLocation lineageFactionId,
            ResourceKey<Level> dimension,
            HiveLocationId sourceLocationId,
            HiveLocationId destinationLocationId,
            Vec3 currentPos,
            BlockPos destinationPos,
            EntityReserves composition,
            long dispatchedTick
        ) {
            this(
                id,
                lineageFactionId,
                dimension,
                sourceLocationId,
                destinationLocationId,
                currentPos,
                destinationPos,
                composition,
                Map.of(),
                dispatchedTick
            );
        }

        public Reinforcement(
            ConvoyId id,
            ResourceLocation lineageFactionId,
            ResourceKey<Level> dimension,
            HiveLocationId sourceLocationId,
            HiveLocationId destinationLocationId,
            Vec3 currentPos,
            BlockPos destinationPos,
            EntityReserves composition,
            Map<UUID, EntityType<?>> materializedMembers,
            long dispatchedTick
        ) {
            this.id = id;
            this.lineageFactionId = lineageFactionId;
            this.dimension = dimension;
            this.sourceLocationId = sourceLocationId;
            this.destinationLocationId = destinationLocationId;
            this.currentPos = currentPos;
            this.destinationPos = destinationPos;
            this.composition = composition;
            this.materializedMembers = new ConvoyMaterializedMembers(materializedMembers);
            this.dispatchedTick = dispatchedTick;
        }

        @Override
        public ConvoyId id() {
            return id;
        }

        @Override
        public ResourceLocation lineageFactionId() {
            return lineageFactionId;
        }

        @Override
        public ResourceKey<Level> dimension() {
            return dimension;
        }

        @Override
        public Vec3 currentPos() {
            return currentPos;
        }

        @Override
        public void setCurrentPos(Vec3 newPos) {
            this.currentPos = newPos;
        }

        @Override
        public EntityReserves composition() {
            return composition;
        }

        @Override
        public Map<UUID, EntityType<?>> materializedMembers() {
            return materializedMembers.members();
        }

        @Override
        public void trackMaterializedMember(UUID memberId, EntityType<?> entityType) {
            materializedMembers.track(memberId, entityType);
        }

        @Override
        public EntityType<?> untrackMaterializedMember(UUID memberId) {
            return materializedMembers.untrack(memberId);
        }

        @Override
        public int materializedCountMatching(Predicate<EntityType<?>> predicate) {
            return materializedMembers.countMatching(predicate);
        }

        @Override
        public long dispatchedTick() {
            return dispatchedTick;
        }

        public HiveLocationId sourceLocationId() {
            return sourceLocationId;
        }

        public HiveLocationId destinationLocationId() {
            return destinationLocationId;
        }

        public BlockPos destinationPos() {
            return destinationPos;
        }
    }

    /**
     * A whole-location evacuation. The source location is doomed; its remaining members and biomass payload are folded
     * into a sister location. Per {@code HIVE_REDESIGN_06_CONVOYS.md} § 5.
     */
    final class Migration implements Convoy {

        private final ConvoyId id;

        private final ResourceLocation lineageFactionId;

        private final ResourceKey<Level> dimension;

        private final HiveLocationId sourceLocationId;

        private final HiveLocationId destinationLocationId;

        private final BlockPos destinationPos;

        private final EntityReserves composition;

        private final ConvoyMaterializedMembers materializedMembers;

        private final int biomassPayload;

        private final boolean carriesEmpress;

        private final long dispatchedTick;

        private Vec3 currentPos;

        public Migration(
            ConvoyId id,
            ResourceLocation lineageFactionId,
            ResourceKey<Level> dimension,
            HiveLocationId sourceLocationId,
            HiveLocationId destinationLocationId,
            Vec3 currentPos,
            BlockPos destinationPos,
            EntityReserves composition,
            int biomassPayload,
            boolean carriesEmpress,
            long dispatchedTick
        ) {
            this(
                id,
                lineageFactionId,
                dimension,
                sourceLocationId,
                destinationLocationId,
                currentPos,
                destinationPos,
                composition,
                Map.of(),
                biomassPayload,
                carriesEmpress,
                dispatchedTick
            );
        }

        public Migration(
            ConvoyId id,
            ResourceLocation lineageFactionId,
            ResourceKey<Level> dimension,
            HiveLocationId sourceLocationId,
            HiveLocationId destinationLocationId,
            Vec3 currentPos,
            BlockPos destinationPos,
            EntityReserves composition,
            Map<UUID, EntityType<?>> materializedMembers,
            int biomassPayload,
            boolean carriesEmpress,
            long dispatchedTick
        ) {
            this.id = id;
            this.lineageFactionId = lineageFactionId;
            this.dimension = dimension;
            this.sourceLocationId = sourceLocationId;
            this.destinationLocationId = destinationLocationId;
            this.currentPos = currentPos;
            this.destinationPos = destinationPos;
            this.composition = composition;
            this.materializedMembers = new ConvoyMaterializedMembers(materializedMembers);
            this.biomassPayload = biomassPayload;
            this.carriesEmpress = carriesEmpress;
            this.dispatchedTick = dispatchedTick;
        }

        @Override
        public ConvoyId id() {
            return id;
        }

        @Override
        public ResourceLocation lineageFactionId() {
            return lineageFactionId;
        }

        @Override
        public ResourceKey<Level> dimension() {
            return dimension;
        }

        @Override
        public Vec3 currentPos() {
            return currentPos;
        }

        @Override
        public void setCurrentPos(Vec3 newPos) {
            this.currentPos = newPos;
        }

        @Override
        public EntityReserves composition() {
            return composition;
        }

        @Override
        public Map<UUID, EntityType<?>> materializedMembers() {
            return materializedMembers.members();
        }

        @Override
        public void trackMaterializedMember(UUID memberId, EntityType<?> entityType) {
            materializedMembers.track(memberId, entityType);
        }

        @Override
        public EntityType<?> untrackMaterializedMember(UUID memberId) {
            return materializedMembers.untrack(memberId);
        }

        @Override
        public int materializedCountMatching(Predicate<EntityType<?>> predicate) {
            return materializedMembers.countMatching(predicate);
        }

        @Override
        public long dispatchedTick() {
            return dispatchedTick;
        }

        public HiveLocationId sourceLocationId() {
            return sourceLocationId;
        }

        public HiveLocationId destinationLocationId() {
            return destinationLocationId;
        }

        public BlockPos destinationPos() {
            return destinationPos;
        }

        public int biomassPayload() {
            return biomassPayload;
        }

        public boolean carriesEmpress() {
            return carriesEmpress;
        }
    }

    /**
     * A counter-attack against a player who recently attacked the lineage. The convoy's destination tracks the target
     * player's position over time (snapshotted into {@link #lastKnownTargetPos} each tick by
     * {@link com.alien.common.gameplay.hive.tick.LineageConvoyTickTask}).
     * <p>
     * Per {@code HIVE_REDESIGN_06_CONVOYS.md} § 6.
     */
    final class Raid implements Convoy {

        public static final int WAVE_COUNT = 5;

        public enum ReturnHomeReason {

            NONE("none"),
            TARGET_UNAVAILABLE("target_unavailable"),
            TARGET_DEFEATED("target_defeated");

            private final String serializedName;

            ReturnHomeReason(String serializedName) {
                this.serializedName = serializedName;
            }

            public String serializedName() {
                return serializedName;
            }

            public static ReturnHomeReason fromSerializedName(String serializedName) {
                for (var reason : values()) {
                    if (reason.serializedName.equals(serializedName)) {
                        return reason;
                    }
                }
                return NONE;
            }
        }

        private final ConvoyId id;

        private final ResourceLocation lineageFactionId;

        private final ResourceKey<Level> dimension;

        private final HiveLocationId sourceLocationId;

        private final UUID targetPlayerId;

        private final EntityReserves composition;

        private final ConvoyMaterializedMembers materializedMembers;

        private final long dispatchedTick;

        private final long expiresAtTick;

        private boolean warningIssued;

        private int nextWaveIndex;

        private int activeWaveIndex;

        private int activeWaveInitialCount;

        private long waveBreakStartedTick;

        private int frenziedJoinCount;

        private int targetDeathCount;

        private long deathWindowStartedTick;

        private long lastTargetDeathTick;

        private long targetDownSinceTick;

        private long lossConfirmedTick;

        private boolean targetWasAlive;

        private boolean returningHome;

        private ReturnHomeReason returnHomeReason;

        private @Nullable HiveLocationId returnLocationId;

        private @Nullable BlockPos returnPos;

        private Vec3 currentPos;

        private BlockPos lastKnownTargetPos;

        /**
         * How many waves this raid fires. Defaults to {@link #WAVE_COUNT} (5) for ordinary raids; a revenge raid sets
         * it to 3 via {@link #setWaveCount}. Kept as a mutable field (rather than threaded through the constructor
         * chain) to minimize churn across the many {@code Raid} constructor overloads.
         */
        private int waveCount = WAVE_COUNT;

        /**
         * True if this is a revenge raid (queen killed) rather than an ordinary kill-threshold raid. Drives the
         * revenge wave profile lookup and 3-wave count. Set via {@link #markRevenge} at dispatch.
         */
        private boolean revenge = false;

        /**
         * True if this is a rescue raid (queen captured/lost) — tracks the player holding her; success = she's freed,
         * not the target's death. Uses the rescue wave profile. Set via {@link #markRescue} at dispatch.
         */
        private boolean rescue = false;

        public Raid(
            ConvoyId id,
            ResourceLocation lineageFactionId,
            ResourceKey<Level> dimension,
            HiveLocationId sourceLocationId,
            UUID targetPlayerId,
            Vec3 currentPos,
            BlockPos lastKnownTargetPos,
            EntityReserves composition,
            long dispatchedTick,
            long expiresAtTick
        ) {
            this(
                id,
                lineageFactionId,
                dimension,
                sourceLocationId,
                targetPlayerId,
                currentPos,
                lastKnownTargetPos,
                composition,
                Map.of(),
                dispatchedTick,
                expiresAtTick
            );
        }

        public Raid(
            ConvoyId id,
            ResourceLocation lineageFactionId,
            ResourceKey<Level> dimension,
            HiveLocationId sourceLocationId,
            UUID targetPlayerId,
            Vec3 currentPos,
            BlockPos lastKnownTargetPos,
            EntityReserves composition,
            Map<UUID, EntityType<?>> materializedMembers,
            long dispatchedTick,
            long expiresAtTick
        ) {
            this(
                id,
                lineageFactionId,
                dimension,
                sourceLocationId,
                targetPlayerId,
                currentPos,
                lastKnownTargetPos,
                composition,
                materializedMembers,
                false,
                0,
                -1,
                0,
                -1L,
                false,
                null,
                null,
                dispatchedTick,
                expiresAtTick
            );
        }

        public Raid(
            ConvoyId id,
            ResourceLocation lineageFactionId,
            ResourceKey<Level> dimension,
            HiveLocationId sourceLocationId,
            UUID targetPlayerId,
            Vec3 currentPos,
            BlockPos lastKnownTargetPos,
            EntityReserves composition,
            Map<UUID, EntityType<?>> materializedMembers,
            boolean warningIssued,
            long dispatchedTick,
            long expiresAtTick
        ) {
            this(
                id,
                lineageFactionId,
                dimension,
                sourceLocationId,
                targetPlayerId,
                currentPos,
                lastKnownTargetPos,
                composition,
                materializedMembers,
                warningIssued,
                0,
                -1,
                0,
                -1L,
                false,
                null,
                null,
                dispatchedTick,
                expiresAtTick
            );
        }

        public Raid(
            ConvoyId id,
            ResourceLocation lineageFactionId,
            ResourceKey<Level> dimension,
            HiveLocationId sourceLocationId,
            UUID targetPlayerId,
            Vec3 currentPos,
            BlockPos lastKnownTargetPos,
            EntityReserves composition,
            Map<UUID, EntityType<?>> materializedMembers,
            boolean warningIssued,
            int nextWaveIndex,
            int activeWaveIndex,
            int activeWaveInitialCount,
            long waveBreakStartedTick,
            boolean returningHome,
            @Nullable HiveLocationId returnLocationId,
            @Nullable BlockPos returnPos,
            long dispatchedTick,
            long expiresAtTick
        ) {
            this(
                id,
                lineageFactionId,
                dimension,
                sourceLocationId,
                targetPlayerId,
                currentPos,
                lastKnownTargetPos,
                composition,
                materializedMembers,
                warningIssued,
                nextWaveIndex,
                activeWaveIndex,
                activeWaveInitialCount,
                waveBreakStartedTick,
                returningHome,
                returningHome ? ReturnHomeReason.TARGET_DEFEATED : ReturnHomeReason.NONE,
                returnLocationId,
                returnPos,
                dispatchedTick,
                expiresAtTick
            );
        }

        public Raid(
            ConvoyId id,
            ResourceLocation lineageFactionId,
            ResourceKey<Level> dimension,
            HiveLocationId sourceLocationId,
            UUID targetPlayerId,
            Vec3 currentPos,
            BlockPos lastKnownTargetPos,
            EntityReserves composition,
            Map<UUID, EntityType<?>> materializedMembers,
            boolean warningIssued,
            int nextWaveIndex,
            int activeWaveIndex,
            int activeWaveInitialCount,
            long waveBreakStartedTick,
            boolean returningHome,
            ReturnHomeReason returnHomeReason,
            @Nullable HiveLocationId returnLocationId,
            @Nullable BlockPos returnPos,
            long dispatchedTick,
            long expiresAtTick
        ) {
            this(
                id,
                lineageFactionId,
                dimension,
                sourceLocationId,
                targetPlayerId,
                currentPos,
                lastKnownTargetPos,
                composition,
                materializedMembers,
                warningIssued,
                nextWaveIndex,
                activeWaveIndex,
                activeWaveInitialCount,
                waveBreakStartedTick,
                0,
                0,
                -1L,
                -1L,
                -1L,
                -1L,
                false,
                returningHome,
                returnHomeReason,
                returnLocationId,
                returnPos,
                dispatchedTick,
                expiresAtTick
            );
        }

        public Raid(
            ConvoyId id,
            ResourceLocation lineageFactionId,
            ResourceKey<Level> dimension,
            HiveLocationId sourceLocationId,
            UUID targetPlayerId,
            Vec3 currentPos,
            BlockPos lastKnownTargetPos,
            EntityReserves composition,
            Map<UUID, EntityType<?>> materializedMembers,
            boolean warningIssued,
            int nextWaveIndex,
            int activeWaveIndex,
            int activeWaveInitialCount,
            long waveBreakStartedTick,
            int frenziedJoinCount,
            boolean returningHome,
            ReturnHomeReason returnHomeReason,
            @Nullable HiveLocationId returnLocationId,
            @Nullable BlockPos returnPos,
            long dispatchedTick,
            long expiresAtTick
        ) {
            this(
                id,
                lineageFactionId,
                dimension,
                sourceLocationId,
                targetPlayerId,
                currentPos,
                lastKnownTargetPos,
                composition,
                materializedMembers,
                warningIssued,
                nextWaveIndex,
                activeWaveIndex,
                activeWaveInitialCount,
                waveBreakStartedTick,
                frenziedJoinCount,
                0,
                -1L,
                -1L,
                -1L,
                -1L,
                false,
                returningHome,
                returnHomeReason,
                returnLocationId,
                returnPos,
                dispatchedTick,
                expiresAtTick
            );
        }

        public Raid(
            ConvoyId id,
            ResourceLocation lineageFactionId,
            ResourceKey<Level> dimension,
            HiveLocationId sourceLocationId,
            UUID targetPlayerId,
            Vec3 currentPos,
            BlockPos lastKnownTargetPos,
            EntityReserves composition,
            Map<UUID, EntityType<?>> materializedMembers,
            boolean warningIssued,
            int nextWaveIndex,
            int activeWaveIndex,
            int activeWaveInitialCount,
            long waveBreakStartedTick,
            int frenziedJoinCount,
            int targetDeathCount,
            long deathWindowStartedTick,
            long lastTargetDeathTick,
            long targetDownSinceTick,
            long lossConfirmedTick,
            boolean targetWasAlive,
            boolean returningHome,
            ReturnHomeReason returnHomeReason,
            @Nullable HiveLocationId returnLocationId,
            @Nullable BlockPos returnPos,
            long dispatchedTick,
            long expiresAtTick
        ) {
            this.id = id;
            this.lineageFactionId = lineageFactionId;
            this.dimension = dimension;
            this.sourceLocationId = sourceLocationId;
            this.targetPlayerId = targetPlayerId;
            this.currentPos = currentPos;
            this.lastKnownTargetPos = lastKnownTargetPos;
            this.composition = composition;
            this.materializedMembers = new ConvoyMaterializedMembers(materializedMembers);
            this.warningIssued = warningIssued;
            this.nextWaveIndex = Math.max(0, nextWaveIndex);
            this.activeWaveIndex = activeWaveIndex;
            this.activeWaveInitialCount = Math.max(0, activeWaveInitialCount);
            this.waveBreakStartedTick = waveBreakStartedTick;
            this.frenziedJoinCount = Math.max(0, frenziedJoinCount);
            this.targetDeathCount = Math.max(0, targetDeathCount);
            this.deathWindowStartedTick = deathWindowStartedTick;
            this.lastTargetDeathTick = lastTargetDeathTick;
            this.targetDownSinceTick = targetDownSinceTick;
            this.lossConfirmedTick = lossConfirmedTick;
            this.targetWasAlive = targetWasAlive;
            this.returningHome = returningHome;
            this.returnHomeReason = returningHome ? normalizeReturnHomeReason(returnHomeReason) : ReturnHomeReason.NONE;
            this.returnLocationId = returnLocationId;
            this.returnPos = returnPos;
            this.dispatchedTick = dispatchedTick;
            this.expiresAtTick = expiresAtTick;
        }

        private static ReturnHomeReason normalizeReturnHomeReason(ReturnHomeReason reason) {
            return reason == ReturnHomeReason.NONE ? ReturnHomeReason.TARGET_DEFEATED : reason;
        }

        @Override
        public ConvoyId id() {
            return id;
        }

        @Override
        public ResourceLocation lineageFactionId() {
            return lineageFactionId;
        }

        @Override
        public ResourceKey<Level> dimension() {
            return dimension;
        }

        @Override
        public Vec3 currentPos() {
            return currentPos;
        }

        @Override
        public void setCurrentPos(Vec3 newPos) {
            this.currentPos = newPos;
        }

        @Override
        public EntityReserves composition() {
            return composition;
        }

        @Override
        public Map<UUID, EntityType<?>> materializedMembers() {
            return materializedMembers.members();
        }

        @Override
        public void trackMaterializedMember(UUID memberId, EntityType<?> entityType) {
            materializedMembers.track(memberId, entityType);
        }

        @Override
        public EntityType<?> untrackMaterializedMember(UUID memberId) {
            return materializedMembers.untrack(memberId);
        }

        @Override
        public int materializedCountMatching(Predicate<EntityType<?>> predicate) {
            return materializedMembers.countMatching(predicate);
        }

        @Override
        public long dispatchedTick() {
            return dispatchedTick;
        }

        public HiveLocationId sourceLocationId() {
            return sourceLocationId;
        }

        public UUID targetPlayerId() {
            return targetPlayerId;
        }

        public BlockPos lastKnownTargetPos() {
            return lastKnownTargetPos;
        }

        public void setLastKnownTargetPos(BlockPos newPos) {
            this.lastKnownTargetPos = newPos;
        }

        public long expiresAtTick() {
            return expiresAtTick;
        }

        public boolean warningIssued() {
            return warningIssued;
        }

        public void setWarningIssued(boolean warningIssued) {
            this.warningIssued = warningIssued;
        }

        public int nextWaveIndex() {
            return nextWaveIndex;
        }

        public int waveCount() {
            return waveCount;
        }

        public void setWaveCount(int waveCount) {
            this.waveCount = Math.max(1, waveCount);
        }

        public boolean isRevenge() {
            return revenge;
        }

        public void markRevenge() {
            this.revenge = true;
        }

        public boolean isRescue() {
            return rescue;
        }

        public void markRescue() {
            this.rescue = true;
        }

        public void advanceWave() {
            nextWaveIndex++;
        }

        public int activeWaveIndex() {
            return activeWaveIndex;
        }

        public int activeWaveInitialCount() {
            return activeWaveInitialCount;
        }

        public long waveBreakStartedTick() {
            return waveBreakStartedTick;
        }

        public int frenziedJoinCount() {
            return frenziedJoinCount;
        }

        public void incrementFrenziedJoinCount() {
            frenziedJoinCount++;
        }

        public int targetDeathCount() {
            return targetDeathCount;
        }

        public long deathWindowStartedTick() {
            return deathWindowStartedTick;
        }

        public long lastTargetDeathTick() {
            return lastTargetDeathTick;
        }

        public long targetDownSinceTick() {
            return targetDownSinceTick;
        }

        public long lossConfirmedTick() {
            return lossConfirmedTick;
        }

        public boolean targetWasAlive() {
            return targetWasAlive;
        }

        public boolean lossConfirmed() {
            return lossConfirmedTick >= 0L;
        }

        public void noteTargetAlive() {
            targetWasAlive = true;
            targetDownSinceTick = -1L;
        }

        public void noteTargetDown(long currentTick) {
            if (targetDownSinceTick < 0L) {
                targetDownSinceTick = currentTick;
            }
        }

        public void recordTargetDeath(long currentTick, long deathWindowTicks) {
            if (deathWindowStartedTick < 0L || currentTick - deathWindowStartedTick > deathWindowTicks) {
                deathWindowStartedTick = currentTick;
                targetDeathCount = 0;
            }

            targetDeathCount++;
            lastTargetDeathTick = currentTick;
            targetDownSinceTick = currentTick;
            targetWasAlive = false;
        }

        public void setTargetWasAlive(boolean targetWasAlive) {
            this.targetWasAlive = targetWasAlive;
        }

        public void confirmLoss(long currentTick) {
            if (lossConfirmedTick < 0L) {
                lossConfirmedTick = currentTick;
            }
        }

        public void beginWave(int waveIndex, int spawnedCount) {
            this.activeWaveIndex = Math.clamp(waveIndex, 0, waveCount - 1);
            this.activeWaveInitialCount = Math.max(1, spawnedCount);
            this.nextWaveIndex = Math.max(nextWaveIndex, this.activeWaveIndex + 1);
            this.waveBreakStartedTick = -1L;
        }

        public boolean shouldStartWaveBreak() {
            return activeWaveIndex >= 0
                && waveBreakStartedTick < 0L
                && materializedMembers().isEmpty()
                && composition().getCount() > 0;
        }

        public void startWaveBreak(long currentTick) {
            this.waveBreakStartedTick = currentTick;
        }

        public boolean isWaveBreakActive(long currentTick, long bufferTicks) {
            return bufferTicks > 0L
                && waveBreakStartedTick >= 0L
                && currentTick < waveBreakStartedTick + bufferTicks;
        }

        public float waveBreakProgress(long currentTick, long bufferTicks) {
            if (waveBreakStartedTick < 0L || bufferTicks <= 0L) {
                return 1.0F;
            }
            var elapsed = currentTick - waveBreakStartedTick;
            return Math.clamp(elapsed / (float) bufferTicks, 0.0F, 1.0F);
        }

        public boolean canSpawnWave(long currentTick, long bufferTicks) {
            if (!materializedMembers().isEmpty()) {
                return false;
            }
            if (bufferTicks <= 0L) {
                return true;
            }
            return waveBreakStartedTick >= 0L && currentTick >= waveBreakStartedTick + bufferTicks;
        }

        public int displayWaveIndex() {
            if (activeWaveIndex >= 0 && (!materializedMembers().isEmpty() || composition().getCount() <= 0)) {
                return Math.clamp(activeWaveIndex, 0, waveCount - 1);
            }
            return Math.clamp(nextWaveIndex, 0, waveCount - 1);
        }

        public boolean returningHome() {
            return returningHome;
        }

        public ReturnHomeReason returnHomeReason() {
            return returnHomeReason;
        }

        public @Nullable HiveLocationId returnLocationId() {
            return returnLocationId;
        }

        public @Nullable BlockPos returnPos() {
            return returnPos;
        }

        public void beginReturnHome(HiveLocationId locationId, BlockPos locationPos) {
            beginReturnHome(locationId, locationPos, ReturnHomeReason.TARGET_DEFEATED);
        }

        public void beginReturnHome(
            @Nullable HiveLocationId locationId,
            @Nullable BlockPos locationPos,
            ReturnHomeReason reason
        ) {
            this.returningHome = true;
            this.returnHomeReason = normalizeReturnHomeReason(reason);
            this.returnLocationId = locationId;
            this.returnPos = locationPos;
        }

        public void resumeHunt() {
            this.returningHome = false;
            this.returnHomeReason = ReturnHomeReason.NONE;
            this.returnLocationId = null;
            this.returnPos = null;
            this.lossConfirmedTick = -1L;
            this.targetDownSinceTick = -1L;
        }

        public void rewindActiveWave() {
            if (activeWaveIndex >= 0) {
                this.nextWaveIndex = Math.clamp(activeWaveIndex, 0, waveCount - 1);
            }
            this.activeWaveIndex = -1;
            this.activeWaveInitialCount = 0;
            this.waveBreakStartedTick = -1L;
        }
    }
}
