package com.alien.common.gameplay.entity.living.alien.xenomorph;

import com.alien.common.gameplay.entity.living.alien.GrowthManager;
import com.alien.common.gameplay.entity.living.alien.royal_cocoon.RoyalCocoon;
import com.alien.common.gameplay.entity.living.alien.xenomorph.crusher.Crusher;
import com.alien.common.gameplay.entity.living.alien.xenomorph.praetorian.Praetorian;
import com.alien.common.gameplay.hive.faction.FactionMembershipTransfer;
import com.alien.common.gameplay.hive.faction.LocationMembership;
import com.alien.common.model.lifecycle.growth.CocooningConfig;
import com.alien.common.registry.init.AlienEntityTypes;
import com.blib.api.common.entity.v1.EntityTransitionUtil;
import com.blib.api.common.nbt.v1.model.NBTSerializable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class CocoonManager implements NBTSerializable {

    public static final String COCOON_STATE_TAG = "cocoonState";

    public static final String COCOON_TARGET_TYPE_TAG = "cocoonTargetType";

    public static final String COCOON_ALTERNATE_TYPE_TAG = "cocoonAlternateType";

    public static final String COCOON_SOURCE_FORM_TAG = "cocoonSourceForm";

    public static final String COCOON_SOURCE_TIME_TAG = "cocoonSourceTimeInTicks";

    public static final String COCOON_DESTINATION_TIME_TAG = "cocoonDestinationTimeInTicks";

    public static final String COCOON_ELAPSED_TICKS_TAG = "cocoonElapsedTicks";

    private static final int EMERGE_TIME_IN_TICKS = 20;

    private static final int TRANSITION_RETRY_TIME_IN_TICKS = 10 * 20;

    private final Xenomorph xenomorph;

    private @Nullable EntityType<?> targetType;

    /** The other form this molt could produce, if the stage named one. Swapped with {@link #targetType} on redirect. */
    private @Nullable EntityType<?> alternateType;

    private int sourceTimeInTicks;

    private int destinationTimeInTicks;

    private int elapsedTicks;

    private int retryTicks;

    public CocoonManager(Xenomorph xenomorph) {
        this.xenomorph = xenomorph;
        this.sourceTimeInTicks = CocooningConfig.DEFAULT_SOURCE_TIME_IN_TICKS;
        this.destinationTimeInTicks = CocooningConfig.DEFAULT_DESTINATION_TIME_IN_TICKS;
    }

    public void prepare(EntityType<?> targetType, CocooningConfig config) {
        prepare(targetType, null, config);
    }

    public void prepare(EntityType<?> targetType, @Nullable EntityType<?> alternateType, CocooningConfig config) {
        if (getState().shouldRunCocoonAction()) {
            return;
        }

        this.targetType = targetType;
        this.alternateType = alternateType;
        this.sourceTimeInTicks = Math.max(config.sourceTimeInTicks(), 1);
        this.destinationTimeInTicks = Math.max(config.destinationTimeInTicks(), 1);
        this.elapsedTicks = 0;
        this.retryTicks = 0;
        setState(CocoonState.PENDING);
    }

    public @Nullable EntityType<?> getTargetType() {
        return targetType;
    }

    /**
     * True while this molt can still be pointed somewhere else.
     * <p>
     * The window closes at the END of SOURCE_COCOONING, not at emergence, because that is where
     * {@code transitionToDestination} actually replaces the entity: from DESTINATION_COCOONING onward the xenomorph IS
     * already its new caste and {@code targetType} has been cleared. "Redirect the molt" therefore has to mean the
     * molt-ENTER phase - there is nothing left to redirect afterwards.
     */
    public boolean canRedirectTarget() {
        return alternateType != null
            && targetType != null
            && (getState() == CocoonState.PENDING || getState() == CocoonState.SOURCE_COCOONING);
    }

    /**
     * Points this molt at its alternate outcome and banks the old one as the new alternate, so a further dose inside
     * the window simply toggles back. Extra outcomes beyond two are a later problem.
     */
    public boolean redirectTarget() {
        if (!canRedirectTarget()) {
            return false;
        }

        var previousTarget = targetType;
        this.targetType = alternateType;
        this.alternateType = previousTarget;
        return true;
    }

    public boolean shouldRunCocoonAction() {
        return getState().shouldRunCocoonAction();
    }

    public boolean isLocked() {
        return getState().isLocked();
    }

    public CocoonState getState() {
        return xenomorph.cocoonState.get();
    }

    public int getAnimationId() {
        return xenomorph.cocoonAnimationId.get();
    }

    public boolean performCocoonTick() {
        stopMovementAndTargeting();

        return switch (getState()) {
            case NONE -> false;
            case PENDING -> {
                beginSourceCocooning();
                yield true;
            }
            case SOURCE_COCOONING -> tickSourceCocooning();
            case DESTINATION_COCOONING -> tickDestinationCocooning();
            case EMERGING -> tickEmerging();
        };
    }

    public void maintainLockedState() {
        if (isLocked()) {
            stopMovementAndTargeting();
        }
    }

    public void beginDestinationCocooning(int destinationTimeInTicks) {
        this.targetType = null;
        this.alternateType = null;
        this.sourceTimeInTicks = 1;
        this.destinationTimeInTicks = Math.max(destinationTimeInTicks, 1);
        this.elapsedTicks = 0;
        this.retryTicks = 0;
        setState(CocoonState.DESTINATION_COCOONING);
        incrementAnimationId();
        stopMovementAndTargeting();
    }

    private void beginSourceCocooning() {
        this.elapsedTicks = 0;
        setState(CocoonState.SOURCE_COCOONING);
        incrementAnimationId();

        // Praetorian/crusher -> queen transitions form inside a visible resin "royal cocoon". Other
        // metamorphoses (e.g. chestburster -> adolescent) get no cocoon: royalCocoonTypeFor returns null.
        EntityType<RoyalCocoon> cocoonType = royalCocoonTypeFor(this.targetType);
        if (cocoonType != null) {
            spawnRoyalCocoon(cocoonType);
        }
    }

    private boolean tickSourceCocooning() {
        elapsedTicks++;

        if (elapsedTicks < sourceTimeInTicks) {
            return true;
        }

        if (retryTicks > 0) {
            retryTicks--;
            return true;
        }

        return transitionToDestination();
    }

    private boolean transitionToDestination() {
        if (targetType == null) {
            clear();
            return false;
        }

        // Snapshot hive faction membership before transitionInto discards the old xenomorph.
        var factionSnapshot = FactionMembershipTransfer.snapshot(xenomorph);

        var transitionResult = EntityTransitionUtil.transitionInto(
            xenomorph,
            targetType,
            GrowthManager.TRANSITION_NBT_KEY_BLACKLIST,
            true
        );

        if (transitionResult instanceof EntityTransitionUtil.EntityTransitionResult.Success<?> success) {
            var newEntity = success.newEntity();

            // Carry over hive faction membership across the new UUID, plus auto-join the parent location if the
            // new form is a xenomorph in territory (handles chestburster -> adolescent).
            FactionMembershipTransfer.apply(factionSnapshot, newEntity);
            if (xenomorph.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                LocationMembership.autoJoinAtPosition(newEntity, serverLevel);
            }

            if (newEntity instanceof Xenomorph newXenomorph) {
                RoyalCandidateProgress.carryRoyalLineCandidate(xenomorph, newXenomorph);
                newXenomorph.getCocoonManager().beginDestinationCocooning(destinationTimeInTicks);
                // Record what she emerged from so the client can pick a source-specific emerge animation
                // (queen: molt.prae vs molt.crusher). 'xenomorph' here is the source that is being replaced.
                newXenomorph.cocoonSourceForm.set(sourceFormOf(xenomorph));
            }

            return false;
        }

        if (transitionResult instanceof EntityTransitionUtil.EntityTransitionResult.Obstructed) {
            retryTicks = TRANSITION_RETRY_TIME_IN_TICKS;
            return true;
        }

        clear();
        return false;
    }

    private boolean tickDestinationCocooning() {
        elapsedTicks++;

        if (elapsedTicks < destinationTimeInTicks) {
            return true;
        }

        elapsedTicks = 0;
        setState(CocoonState.EMERGING);
        incrementAnimationId();
        return true;
    }

    private boolean tickEmerging() {
        elapsedTicks++;

        if (elapsedTicks < EMERGE_TIME_IN_TICKS) {
            return true;
        }

        clear();
        return false;
    }

    private static CocoonSourceForm sourceFormOf(Xenomorph source) {
        if (source instanceof Praetorian) {
            return CocoonSourceForm.PRAETORIAN;
        }
        if (source instanceof Crusher) {
            return CocoonSourceForm.CRUSHER;
        }
        return CocoonSourceForm.NONE;
    }

    private void stopMovementAndTargeting() {
        xenomorph.setDeltaMovement(Vec3.ZERO);
        xenomorph.getNavigation().stop();
        xenomorph.setTarget(null);
    }

    private void clear() {
        // She has emerged (or the transition aborted): the cage is consumed and vanishes. Proximity-based so it
        // works across the source -> destination entity swap; a no-op for non-royal metamorphoses (none nearby).
        discardNearbyRoyalCocoons();
        this.targetType = null;
        this.alternateType = null;
        xenomorph.cocoonSourceForm.set(CocoonSourceForm.NONE);
        this.elapsedTicks = 0;
        this.retryTicks = 0;
        setState(CocoonState.NONE);
    }

    private static @Nullable EntityType<RoyalCocoon> royalCocoonTypeFor(@Nullable EntityType<?> target) {
        if (target == AlienEntityTypes.QUEEN.get()) {
            return AlienEntityTypes.ROYAL_COCOON.get();
        }
        if (target == AlienEntityTypes.ABERRANT_QUEEN.get()) {
            return AlienEntityTypes.ABERRANT_ROYAL_COCOON.get();
        }
        if (target == AlienEntityTypes.NETHER_QUEEN.get()) {
            return AlienEntityTypes.NETHER_ROYAL_COCOON.get();
        }
        // Irradiated queens don't molt, and empresses use their own layer: no royal cocoon.
        return null;
    }

    private void spawnRoyalCocoon(EntityType<RoyalCocoon> type) {
        if (!(xenomorph.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        RoyalCocoon cocoon = type.create(serverLevel);
        if (cocoon == null) {
            return;
        }
        cocoon.moveTo(xenomorph.getX(), xenomorph.getY(), xenomorph.getZ(), xenomorph.getYRot(), 0.0F);
        cocoon.setPersistenceRequired();
        serverLevel.addFreshEntity(cocoon);
    }

    private void discardNearbyRoyalCocoons() {
        if (!(xenomorph.level() instanceof ServerLevel)) {
            return;
        }
        for (RoyalCocoon cocoon : xenomorph.level().getEntitiesOfClass(RoyalCocoon.class, xenomorph.getBoundingBox().inflate(4.0))) {
            cocoon.discard();
        }
    }

    private void setState(CocoonState state) {
        xenomorph.cocoonState.set(state);
    }

    private void incrementAnimationId() {
        xenomorph.cocoonAnimationId.set(xenomorph.cocoonAnimationId.get() + 1);
    }

    @Override
    public void load(CompoundTag compoundTag) {
        if (compoundTag.contains(COCOON_STATE_TAG)) {
            setState(CocoonState.valueOf(compoundTag.getString(COCOON_STATE_TAG)));
        }

        if (compoundTag.contains(COCOON_TARGET_TYPE_TAG)) {
            this.targetType = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(compoundTag.getString(COCOON_TARGET_TYPE_TAG)));
        }

        if (compoundTag.contains(COCOON_ALTERNATE_TYPE_TAG)) {
            this.alternateType = BuiltInRegistries.ENTITY_TYPE.get(
                ResourceLocation.parse(compoundTag.getString(COCOON_ALTERNATE_TYPE_TAG))
            );
        }

        if (compoundTag.contains(COCOON_SOURCE_FORM_TAG)) {
            xenomorph.cocoonSourceForm.set(CocoonSourceForm.valueOf(compoundTag.getString(COCOON_SOURCE_FORM_TAG)));
        }

        if (compoundTag.contains(COCOON_SOURCE_TIME_TAG)) {
            this.sourceTimeInTicks = compoundTag.getInt(COCOON_SOURCE_TIME_TAG);
        }

        if (compoundTag.contains(COCOON_DESTINATION_TIME_TAG)) {
            this.destinationTimeInTicks = compoundTag.getInt(COCOON_DESTINATION_TIME_TAG);
        }

        if (compoundTag.contains(COCOON_ELAPSED_TICKS_TAG)) {
            this.elapsedTicks = compoundTag.getInt(COCOON_ELAPSED_TICKS_TAG);
        }
    }

    @Override
    public void save(CompoundTag compoundTag) {
        var state = getState();

        if (state == CocoonState.NONE) {
            return;
        }

        compoundTag.putString(COCOON_STATE_TAG, state.name());

        if (targetType != null) {
            compoundTag.putString(COCOON_TARGET_TYPE_TAG, BuiltInRegistries.ENTITY_TYPE.getKey(targetType).toString());
        }

        if (alternateType != null) {
            compoundTag.putString(COCOON_ALTERNATE_TYPE_TAG, BuiltInRegistries.ENTITY_TYPE.getKey(alternateType).toString());
        }

        compoundTag.putString(COCOON_SOURCE_FORM_TAG, xenomorph.cocoonSourceForm.get().name());

        compoundTag.putInt(COCOON_SOURCE_TIME_TAG, sourceTimeInTicks);
        compoundTag.putInt(COCOON_DESTINATION_TIME_TAG, destinationTimeInTicks);
        compoundTag.putInt(COCOON_ELAPSED_TICKS_TAG, elapsedTicks);
    }
}
