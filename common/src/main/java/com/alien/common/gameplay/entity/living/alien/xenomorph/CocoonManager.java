package com.alien.common.gameplay.entity.living.alien.xenomorph;

import com.alien.common.gameplay.entity.living.alien.GrowthManager;
import com.alien.common.gameplay.entity.living.alien.royal_cocoon.RoyalCocoon;
import com.alien.common.gameplay.entity.living.alien.xenomorph.crusher.Crusher;
import com.alien.common.gameplay.entity.living.alien.xenomorph.praetorian.Praetorian;
import com.alien.common.gameplay.hive.faction.FactionMembershipTransfer;
import com.alien.common.gameplay.hive.faction.LocationMembership;
import com.alien.common.model.lifecycle.growth.CocooningConfig;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.tag.AlienEntityTypeTags;
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

    public static final String COCOON_ROYAL_CAGE_TAG = "cocoonRoyalCageId";

    /**
     * ⚠ MUST BE >= THE LONGEST AUTHORED EMERGE CLIP, or the tail of the animation is cut off when the state ends.
     * <p>
     * Raised from 20 (1s) to 30 (1.5s) because the spitter's {@code molt.emerge} is exactly 1.5s and was losing its
     * last ten ticks. Castes whose emerge is shorter simply hold on their last frame for the remainder, which is what
     * they already did between the clip ending and the state ending.
     * </p>
     */
    private static final int EMERGE_TIME_IN_TICKS = 30;

    private static final int TRANSITION_RETRY_TIME_IN_TICKS = 10 * 20;

    private final Xenomorph xenomorph;

    private @Nullable EntityType<?> targetType;

    /** The other form this molt could produce, if the stage named one. Swapped with {@link #targetType} on redirect. */
    private @Nullable EntityType<?> alternateType;

    private int sourceTimeInTicks;

    private int destinationTimeInTicks;

    private int elapsedTicks;

    /**
     * The royal cage this molt is happening inside, if any. Only praetorian/crusher -> queen spawns one.
     * <p>
     * Held so the cage can be found by IDENTITY instead of by looking around, both to re-point its occupant at the
     * entity swap and to consume it on emergence. See {@link RoyalCocoon#setOccupantId}.
     * </p>
     */
    private @Nullable java.util.UUID royalCageId;

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
                handOverRoyalCage(newXenomorph);
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
        // She has emerged (or the transition aborted): the cage is consumed and vanishes. Found by IDENTITY, so it
        // can only ever remove THIS molt's cage; a no-op for non-royal metamorphoses, which never had one.
        discardRoyalCage();
        this.targetType = null;
        this.alternateType = null;
        xenomorph.cocoonSourceForm.set(CocoonSourceForm.NONE);
        this.elapsedTicks = 0;
        this.retryTicks = 0;
        setState(CocoonState.NONE);
    }

    /**
     * ⭐⭐ WHO GETS A VISIBLE COCOON TO SIT IN. [stated] "royal adolescent to its molted forms, pred adolescent to
     * predalien and queen to empress should all have the royal cocoon that they sit in", plus [stated] "also praetorian
     * to harbinger as well".
     * <p>
     * ⚠⚠ THIS USED TO KEY ON THE TARGET TYPE ALONE, and it was a hardcoded list of three queen entities. That shape
     * could not express his rule: a ROYAL ADOLESCENT gets a cocoon whatever it becomes, so the deciding fact is the
     * SOURCE, not the destination. It now asks both.
     * </p>
     * <p>
     * ⚠ THE COCOON'S STRAIN COMES FROM THE MOLTING XENOMORPH, NOT FROM THE TARGET TYPE. Keying it off the target meant
     * every new destination needed three more branches here, which is exactly how the empress and the predalien got
     * left out in the first place.
     * </p>
     */
    private @Nullable EntityType<RoyalCocoon> royalCocoonTypeFor(@Nullable EntityType<?> target) {
        if (!usesRoyalCocoon(target)) {
            return null;
        }

        return switch (xenomorph.getVariant()) {
            case NORMAL -> AlienEntityTypes.ROYAL_COCOON.get();
            case NETHER -> AlienEntityTypes.NETHER_ROYAL_COCOON.get();
            case ABERRANT -> AlienEntityTypes.ABERRANT_ROYAL_COCOON.get();
            // ⚠ NO IRRADIATED ROYAL COCOON EXISTS. Falling through to null keeps the molt working WITHOUT a
            // visible cocoon rather than crashing on a missing entity type - the same silent-gap handling
            // Boiler.getType uses for its own irradiated hole. Register one and this line becomes a branch.
            case IRRADIATED -> null;
        };
    }

    private boolean usesRoyalCocoon(@Nullable EntityType<?> target) {
        // A royal adolescent forms inside a cocoon whatever it is on its way to becoming - the source decides.
        if (xenomorph.getType().is(AlienEntityTypeTags.ADOLESCENTS) && xenomorph.isRoyal()) {
            return true;
        }

        if (xenomorph.getType().is(AlienEntityTypeTags.PREDALIEN_ADOLESCENTS)) {
            return true;
        }

        if (target == null) {
            return false;
        }

        // ⚠ TAGS, NOT A LIST OF ENTITY TYPES. The old hardcoded trio silently excluded every strain and every
        // destination added later; a tag picks those up for free.
        return target.is(AlienEntityTypeTags.QUEENS)
            || target.is(AlienEntityTypeTags.EMPRESSES)
            || target.is(AlienEntityTypeTags.PREDALIENS)
            // ⚠ HARBINGERS ARE **NOT** IN ROYAL_XENOMORPHS (that tag is praetorians + predaliens + queens), so
            // this needs its own line - the praetorian→harbinger molt would otherwise be the one royal
            // transformation still happening in the open.
            || target.is(AlienEntityTypeTags.HARBINGERS);
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
        cocoon.setOccupantId(xenomorph.getUUID());
        serverLevel.addFreshEntity(cocoon);
        this.royalCageId = cocoon.getUUID();
    }

    /**
     * Hands the cage over to the entity that just replaced this one, keeping both halves of the link straight.
     * <p>
     * ⚠ THIS IS WHY THE LINK CAN BE EXPLICIT AT ALL. The old comment claimed a stored link could not survive the source
     * -> destination swap, so it searched by proximity instead. But the swap happens in exactly one place and both
     * entities are in scope there, so the link only has to be re-pointed once.
     * </p>
     */
    private void handOverRoyalCage(Xenomorph newXenomorph) {
        if (royalCageId == null || !(xenomorph.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (serverLevel.getEntity(royalCageId) instanceof RoyalCocoon cage) {
            cage.setOccupantId(newXenomorph.getUUID());
        }

        newXenomorph.getCocoonManager().royalCageId = royalCageId;
        this.royalCageId = null;
    }

    /**
     * ⚠ REPLACES A 4-BLOCK PROXIMITY SWEEP. That sweep discarded EVERY royal cage within four blocks of the emerging
     * royal - so a queen finishing her molt beside another forming royal deleted that one's cage too, cancelling a
     * metamorphosis nobody touched. The cage is this molt's own or it is not removed.
     */
    private void discardRoyalCage() {
        if (royalCageId == null || !(xenomorph.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (serverLevel.getEntity(royalCageId) instanceof RoyalCocoon cage) {
            // Clear the link first: the cage kills its occupant when it DIES, and this is a consumption, not a kill.
            cage.setOccupantId(null);
            cage.discard();
        }

        this.royalCageId = null;
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

        if (compoundTag.hasUUID(COCOON_ROYAL_CAGE_TAG)) {
            this.royalCageId = compoundTag.getUUID(COCOON_ROYAL_CAGE_TAG);
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

        if (royalCageId != null) {
            compoundTag.putUUID(COCOON_ROYAL_CAGE_TAG, royalCageId);
        }
    }
}
