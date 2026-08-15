package com.alien.common.gameplay.entity.living.alien;

import com.alien.AlienResources;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.faction.HiveMemberLocationResolver;
import com.alien.common.model.lifecycle.growth.MoltPhase;
import com.alien.common.model.lifecycle.growth.MoltingProfile;
import com.alien.common.registry.MoltingProfileRegistry;
import com.alien.common.util.AlienPredicates;
import com.blib.api.common.nbt.v1.model.NBTSerializable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

public class MoltingManager implements NBTSerializable {

    public static final String MOLT_PHASE_INDEX_TAG = "moltPhaseIndex";

    public static final String MOLT_PHASE_TICKS_TAG = "moltPhaseTicks";

    public static final String MOLT_TARGET_SCALE_REACHED_TICKS_TAG = "moltTargetScaleReachedTicks";

    private static final ResourceLocation MOLTING_PROFILE_MODIFIER = AlienResources.location("molting_profile");

    private static final int MOLT_FADE_TICKS = 20;

    private static final int RECENTLY_HURT_WINDOW_IN_TICKS = 10 * 20;

    private final Alien entity;

    private int phaseIndex;

    private int phaseElapsedTicks;

    private int targetScaleReachedTicks;

    public MoltingManager(Alien entity) {
        this.entity = entity;
        this.phaseIndex = 0;
        this.phaseElapsedTicks = 0;
        this.targetScaleReachedTicks = Integer.MAX_VALUE;

        var data = getData();

        if (data != null) {
            if (!data.isFullyMatured(phaseIndex)) {
                this.targetScaleReachedTicks = 0;
            }

            applyScaleModifier(data);
        }
    }

    /**
     * Jumps this alien straight to its profile's {@code endScale}, skipping the whole growth chain.
     * <p>
     * [stated] "lets make it so that any summoned xenomorph skips that growth phase and is full size immidiately. spawn
     * eggs can stay the same as they are." A queen or empress otherwise spawns at 0.85 and needs 15 minutes of
     * undisturbed ticking to reach 1.0 - fine for the world, useless when you are summoning one to test with.
     * <p>
     * Advancing {@code phaseIndex} past the last phase is what "matured" means to every other reader here
     * ({@code isFullyMatured}), so this needs no new state and persists through the existing NBT. Setting the scale
     * attribute directly would not work: {@link #applyScaleModifier} recomputes a transient modifier from the phase on
     * every molt tick and would overwrite it.
     */
    public void matureImmediately() {
        var data = getData();

        if (data == null || data.isFullyMatured(phaseIndex)) {
            return;
        }

        this.phaseIndex = data.phases().size();
        this.phaseElapsedTicks = 0;
        this.targetScaleReachedTicks = 0;

        applyScaleModifier(data);
        entity.moltAlpha.set(0F);
    }

    public void tick() {
        if (entity.level().isClientSide) {
            return;
        }

        var data = getData();

        if (data == null) {
            targetScaleReachedTicks = Integer.MAX_VALUE;
            return;
        }

        if (data.isFullyMatured(phaseIndex)) {
            entity.moltAlpha.set(0F);
            incrementTargetScaleReachedTicks();
            return;
        }

        targetScaleReachedTicks = 0;

        var currentPhase = data.phases().get(phaseIndex);

        if (!isMolting(currentPhase) && shouldStartMoltImmediately()) {
            if (!canStartMolting()) {
                return;
            }

            phaseElapsedTicks = currentPhase.idleTicks();
        }

        if (willStartMolting(currentPhase) && !canStartMolting()) {
            // ⚠⚠ THE VALVE. Waiting for calm is right, waiting FOREVER is the bug that froze every adolescent in the
            // tester's world - see moltBlockedTicks.
            if (moltBlockedTicks < MOLT_FORCE_TICKS) {
                moltBlockedTicks++;
                return;
            }
        }

        moltBlockedTicks = 0;

        phaseElapsedTicks++;

        if (phaseElapsedTicks >= currentPhase.totalTicks()) {
            phaseIndex++;
            phaseElapsedTicks = 0;

            if (data.isFullyMatured(phaseIndex)) {
                targetScaleReachedTicks = 0;
            }

            applyScaleModifier(data);
            entity.moltAlpha.set(0F);
            return;
        }

        var moltAlpha = computeMoltAlpha(currentPhase);
        entity.moltAlpha.set(moltAlpha);

        if (isMolting(currentPhase)) {
            applyScaleModifier(data);
        }
    }

    /**
     * Immediately completes the current molt phase (the young alien "ate" something nourishing). Mirrors what
     * {@link #tick()} does when a phase runs out of ticks. No-op once fully matured.
     *
     * @return true if a phase was actually advanced.
     */
    public boolean advancePhase() {
        var data = getData();
        if (data == null || data.isFullyMatured(phaseIndex)) {
            return false;
        }
        phaseIndex++;
        phaseElapsedTicks = 0;
        if (data.isFullyMatured(phaseIndex)) {
            targetScaleReachedTicks = 0;
        }
        applyScaleModifier(data);
        entity.moltAlpha.set(0F);
        return true;
    }

    public boolean hasReachedTargetScale() {
        var data = getData();

        if (data == null) {
            return true;
        }

        return data.isFullyMatured(phaseIndex);
    }

    public boolean hasReachedTargetScaleFor(int ticks) {
        return hasReachedTargetScale() && targetScaleReachedTicks >= ticks;
    }

    public float getCurrentScale() {
        var data = getData();

        if (data == null) {
            return 1.0f;
        }

        if (data.isFullyMatured(phaseIndex)) {
            return data.endScale();
        }

        var currentPhase = data.phases().get(phaseIndex);

        if (!isMolting(currentPhase)) {
            return data.scaleBeforePhase(phaseIndex);
        }

        var moltElapsed = phaseElapsedTicks - currentPhase.idleTicks();
        var moltProgress = (float) moltElapsed / currentPhase.moltTicks();
        var phaseStartScale = data.scaleBeforePhase(phaseIndex);
        var phaseEndScale = data.scaleForPhase(phaseIndex);

        return phaseStartScale + (phaseEndScale - phaseStartScale) * moltProgress;
    }

    public boolean isMolting() {
        var data = getData();

        if (data == null || data.isFullyMatured(phaseIndex)) {
            return false;
        }

        return isMolting(data.phases().get(phaseIndex));
    }

    public void skipToFullMaturity() {
        var data = getData();
        if (data == null) {
            targetScaleReachedTicks = Integer.MAX_VALUE;
            entity.moltAlpha.set(0F);
            return;
        }

        phaseIndex = data.phases().size();
        phaseElapsedTicks = 0;
        targetScaleReachedTicks = Integer.MAX_VALUE;
        entity.moltAlpha.set(0F);
        applyScaleModifier(data);
    }

    private boolean isMolting(MoltPhase phase) {
        return phaseElapsedTicks >= phase.idleTicks();
    }

    private boolean shouldStartMoltImmediately() {
        return entity instanceof Xenomorph xenomorph && xenomorph.getGrowthManager().hasActiveGrowthRequirement();
    }

    private boolean willStartMolting(MoltPhase phase) {
        return !isMolting(phase) && phaseElapsedTicks + 1 >= phase.idleTicks();
    }

    /**
     * ⭐ THE VALVE, [stated] "ok on b and c". How long a phase may sit blocked before it molts anyway: 30 seconds.
     * <p>
     * {@link #canStartMolting()} asks for calm - not aggroed, not moving, no path, hive not tracking players. For an
     * ADULT that is nearly always true within seconds. For a JUVENILE it was very nearly never true: the Aug 11 work
     * gave adolescents their own hunting and wandering AI, and reparenting them onto Xenomorph switched on the
     * {@code hasActiveBLibPath()} clause that had returned false for them unconditionally beforehand. Between the two
     * they never stood still, never advanced a phase, never fully matured - and {@code GrowthManager} refuses to grow
     * anything that has not fully matured. Hence "a bunch of adols running around that seem to just not grow at all".
     * </p>
     * <p>
     * ⚠ THE SETTLE GOAL IS THE FIX; THIS IS THE GUARANTEE. A juvenile now parks itself when a phase comes due, so in
     * practice this counter rarely runs out. It exists for the cases the goal cannot reach - a hive bossbar left angry
     * by a player farming it, a juvenile permanently cornered and re-aggroing, or any clause someone adds to
     * {@code canStartMolting} later. Without it this can silently wedge again, which is exactly how we got here.
     * </p>
     */
    private static final int MOLT_FORCE_TICKS = 600;

    /** Consecutive ticks this phase has been held at the molt boundary waiting for calm. */
    private int moltBlockedTicks;

    /**
     * True while a phase is due but calm has not arrived - i.e. the alien is standing at the molt boundary. The
     * settle-and-molt goal watches this to decide when to stop the juvenile and let it change.
     */
    public boolean isWaitingToMolt() {
        var data = getData();

        if (data == null || data.isFullyMatured(phaseIndex)) {
            return false;
        }

        return willStartMolting(data.phases().get(phaseIndex)) && !canStartMolting();
    }

    private boolean canStartMolting() {
        return !isVulnerableAndOnFire()
            && !isAggroed()
            && !wasRecentlyHurt()
            && !isHiveLocationTrackingPlayers()
            && !isMoving()
            && !hasActiveBLibPath()
            && !hasNearbyAttackTarget();
    }

    private boolean isVulnerableAndOnFire() {
        return entity.isOnFire() && !entity.fireImmune();
    }

    private boolean isAggroed() {
        return entity.getTarget() != null;
    }

    private boolean wasRecentlyHurt() {
        var lastHurtTime = entity.getLastHurtTimeInTicks();
        return lastHurtTime > 0 && entity.tickCount - lastHurtTime < RECENTLY_HURT_WINDOW_IN_TICKS;
    }

    private boolean isHiveLocationTrackingPlayers() {
        var location = HiveMemberLocationResolver.reserveReturnLocation(entity);
        if (location == null || !location.isAlive()) {
            return false;
        }

        var bossBar = location.bossBar();
        return bossBar != null && bossBar.isAngry();
    }

    private boolean isMoving() {
        return entity.getMovementAnalyzer().isMoving();
    }

    private boolean hasActiveBLibPath() {
        return entity instanceof Xenomorph xenomorph && xenomorph.hasActiveBLibPath();
    }

    private boolean hasNearbyAttackTarget() {
        if (!(entity instanceof Xenomorph xenomorph)) {
            return false;
        }

        return xenomorph.getEntitySenseCache()
            .getByClass(LivingEntity.class)
            .stream()
            .anyMatch(potentialTarget -> AlienPredicates.canAcquireTarget(xenomorph, potentialTarget));
    }

    private float computeMoltAlpha(MoltPhase phase) {
        if (!isMolting(phase)) {
            var ticksUntilMolt = phase.idleTicks() - phaseElapsedTicks;

            if (ticksUntilMolt <= MOLT_FADE_TICKS) {
                return 1.0F - (float) ticksUntilMolt / MOLT_FADE_TICKS;
            }

            return 0F;
        }

        var moltElapsed = phaseElapsedTicks - phase.idleTicks();
        var moltRemaining = phase.moltTicks() - moltElapsed;

        if (moltRemaining <= MOLT_FADE_TICKS) {
            return (float) moltRemaining / MOLT_FADE_TICKS;
        }

        return 1.0F;
    }

    private void applyScaleModifier(MoltingProfile data) {
        var scaleInstance = entity.getAttribute(Attributes.SCALE);

        if (scaleInstance == null) {
            return;
        }

        var currentScale = getCurrentScale();
        var modifierValue = currentScale - 1.0;

        scaleInstance.removeModifier(MOLTING_PROFILE_MODIFIER);

        if (Math.abs(modifierValue) > 0.001) {
            scaleInstance.addTransientModifier(
                new AttributeModifier(MOLTING_PROFILE_MODIFIER, modifierValue, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            );
        }
    }

    private void incrementTargetScaleReachedTicks() {
        if (targetScaleReachedTicks < Integer.MAX_VALUE) {
            targetScaleReachedTicks++;
        }
    }

    private @Nullable MoltingProfile getData() {
        return MoltingProfileRegistry.get(entity.getType());
    }

    @Override
    public void load(CompoundTag compoundTag) {
        if (compoundTag.contains(MOLT_PHASE_INDEX_TAG)) {
            this.phaseIndex = compoundTag.getInt(MOLT_PHASE_INDEX_TAG);
        }

        if (compoundTag.contains(MOLT_PHASE_TICKS_TAG)) {
            this.phaseElapsedTicks = compoundTag.getInt(MOLT_PHASE_TICKS_TAG);
        }

        if (compoundTag.contains(MOLT_TARGET_SCALE_REACHED_TICKS_TAG)) {
            this.targetScaleReachedTicks = compoundTag.getInt(MOLT_TARGET_SCALE_REACHED_TICKS_TAG);
        }

        var data = getData();

        if (data != null) {
            if (!compoundTag.contains(MOLT_TARGET_SCALE_REACHED_TICKS_TAG)) {
                this.targetScaleReachedTicks = data.isFullyMatured(phaseIndex) ? Integer.MAX_VALUE : 0;
            }

            applyScaleModifier(data);
        }
    }

    @Override
    public void save(CompoundTag compoundTag) {
        compoundTag.putInt(MOLT_PHASE_INDEX_TAG, phaseIndex);
        compoundTag.putInt(MOLT_PHASE_TICKS_TAG, phaseElapsedTicks);
        compoundTag.putInt(MOLT_TARGET_SCALE_REACHED_TICKS_TAG, targetScaleReachedTicks);
    }
}
