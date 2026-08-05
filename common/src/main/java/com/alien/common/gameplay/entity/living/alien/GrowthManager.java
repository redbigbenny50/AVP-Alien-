package com.alien.common.gameplay.entity.living.alien;

import com.alien.common.gameplay.entity.living.alien.xenomorph.boiler.Boiler;
import com.alien.common.gameplay.hive.faction.FactionMembershipTransfer;
import com.alien.common.gameplay.hive.faction.LocationMembership;
import com.alien.common.model.lifecycle.growth.GrowthRequirement;
import com.alien.common.model.lifecycle.growth.GrowthStage;
import com.alien.common.registry.GrowthStageRegistry;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.alien.compatibility.avp_human.AVPHuman;
import com.alien.compatibility.avp_human.GeneManagerProxy;
import com.blib.api.common.entity.v1.EntityTransitionUtil;
import com.blib.api.common.nbt.v1.model.NBTSerializable;
import com.human.common.gameplay.gene.GeneOperationType;
import com.human.common.gameplay.gene.Genes;
import com.human.common.model.GeneCarrier;
import com.human.common.util.GeneIntegrityUtil;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GrowthManager implements NBTSerializable {

    private static final String GROWTH_TIME_IN_TICKS_TAG_KEY = "growthTimeInTicks";

    private static final int POST_MOLT_GROWTH_BUFFER_TICKS = 20;

    public static final Set<String> TRANSITION_NBT_KEY_BLACKLIST = Util.make(() -> {
        var set = new HashSet<>(EntityTransitionUtil.DEFAULT_NBT_KEY_BLACKLIST);
        set.add(GROWTH_TIME_IN_TICKS_TAG_KEY);
        set.add(MoltingManager.MOLT_PHASE_INDEX_TAG);
        set.add(MoltingManager.MOLT_PHASE_TICKS_TAG);
        set.add(MoltingManager.MOLT_TARGET_SCALE_REACHED_TICKS_TAG);
        set.add(com.alien.common.gameplay.entity.living.alien.xenomorph.CocoonManager.COCOON_STATE_TAG);
        set.add(com.alien.common.gameplay.entity.living.alien.xenomorph.CocoonManager.COCOON_TARGET_TYPE_TAG);
        set.add(com.alien.common.gameplay.entity.living.alien.xenomorph.CocoonManager.COCOON_ALTERNATE_TYPE_TAG);
        set.add(com.alien.common.gameplay.entity.living.alien.xenomorph.CocoonManager.COCOON_SOURCE_TIME_TAG);
        set.add(com.alien.common.gameplay.entity.living.alien.xenomorph.CocoonManager.COCOON_DESTINATION_TIME_TAG);
        set.add(com.alien.common.gameplay.entity.living.alien.xenomorph.CocoonManager.COCOON_ELAPSED_TICKS_TAG);
        return set;
    });

    private final Alien entity;

    private boolean growOverTime;

    private int growthTimeInTicks;

    private int growthRetryTimeInTicks;

    private boolean readyToGrow;

    private @Nullable GrowthStage activeRequirementGrowthStage;

    public GrowthManager(Alien entity) {
        this.entity = entity;
        this.growOverTime = true;
        this.readyToGrow = false;
    }

    public void tick() {
        if (entity.level().isClientSide || canNeverGrow()) {
            return;
        }

        var matchingStage = findActiveOrMatchingGrowthStage();

        if (matchingStage == null) {
            return;
        }

        if (matchingStage.hasRequirements()) {
            tickEffectBasedGrowth(matchingStage);
        } else if (growOverTime) {
            tickTimeBasedGrowth(matchingStage);
        }

        if (!readyToGrow) {
            return;
        }

        if (!entity.getMoltingManager().hasReachedTargetScaleFor(POST_MOLT_GROWTH_BUFFER_TICKS)) {
            return;
        }

        this.growthRetryTimeInTicks = Math.max(growthRetryTimeInTicks - 1, 0);

        if (growthRetryTimeInTicks > 0) {
            return;
        }

        switch (grow(matchingStage)) {
            case GrowthResult.AlreadyFullyGrown ignored -> {/* NO-OP */}
            case GrowthResult.CanNotGrow ignored -> {/* NO-OP */}
            case GrowthResult.CocoonStarted ignored -> {/* NO-OP */}
            case GrowthResult.Success ignored -> {/* NO-OP */}
            case GrowthResult.FailedTransitionResult failedTransitionResult -> {
                if (failedTransitionResult.result instanceof EntityTransitionUtil.EntityTransitionResult.Obstructed) {
                    this.growthRetryTimeInTicks = 20 * 10;
                }
            }
        }
    }

    private @Nullable GrowthStage findActiveOrMatchingGrowthStage() {
        if (activeRequirementGrowthStage != null) {
            return activeRequirementGrowthStage;
        }

        return findMatchingGrowthStage();
    }

    private @Nullable GrowthStage findMatchingGrowthStage() {
        var hostType = entity.getHostType().unwrapOr(null);
        var candidates = GrowthStageRegistry.getCandidates(hostType, entity.getType());

        for (var candidate : candidates) {
            if (!candidate.hasRequirements()) {
                return candidate;
            }

            if (allRequirementsMet(candidate.requirements())) {
                return candidate;
            }
        }

        return null;
    }

    private boolean allRequirementsMet(List<GrowthRequirement> requirements) {
        for (var requirement : requirements) {
            if (!requirement.test(entity)) {
                return false;
            }
        }

        return true;
    }

    private void tickEffectBasedGrowth(GrowthStage stage) {
        var requirementsMet = allRequirementsMet(stage.requirements());

        if (requirementsMet) {
            activeRequirementGrowthStage = stage;
        }

        if (activeRequirementGrowthStage == null) {
            this.readyToGrow = false;
            return;
        }

        if (!requirementsMet && !entity.getMoltingManager().isMolting() && !entity.getMoltingManager().hasReachedTargetScale()) {
            activeRequirementGrowthStage = null;
            this.readyToGrow = false;
            return;
        }

        this.readyToGrow = true;
    }

    private void tickTimeBasedGrowth(GrowthStage stage) {
        this.growthTimeInTicks++;

        if (growthTimeInTicks >= stage.growthTimeInTicks()) {
            this.readyToGrow = true;
        }
    }

    public GrowthResult grow() {
        var stage = findMatchingGrowthStage();

        if (stage == null) {
            return GrowthResult.AlreadyFullyGrown.INSTANCE;
        }

        return grow(stage);
    }

    /**
     * Force-grows the entity into the {@code stage}'s {@code to} form, bypassing the stage's growth requirements (e.g.,
     * the metamorphosis mob effect). Used by hive-driven maturation paths
     * ({@link com.alien.common.gameplay.hive.lifecycle.QueenlessMaturationTask}) where the requirement is the hive's
     * social state rather than a player-applied effect.
     * <p>
     * Still respects {@link #canNeverGrow()} (poisoned/irradiated entities don't transition) and the cocoon pipeline
     * for xenomorphs — visually identical to a regular grow, just without the requirement gate.
     */
    public GrowthResult forceGrow(GrowthStage growthStage) {
        return grow(growthStage);
    }

    public GrowthResult grow(GrowthStage growthStage) {
        this.growthTimeInTicks = 0;
        this.readyToGrow = false;
        this.activeRequirementGrowthStage = null;

        if (canNeverGrow()) {
            return GrowthResult.CanNotGrow.INSTANCE;
        }

        var nextFormType = growthStage.to();
        var canBecomeBoiler = canBecomeBoiler(nextFormType);

        if (canBecomeBoiler) {
            // The IRRADIATED strain has no boiler form yet (Boiler.getType returns null for it). Substituting a null
            // here would transition the alien into nothing - fall through to its natural adult form instead. Remove
            // this guard the day an irradiated boiler is registered.
            var boilerType = Boiler.getType(entity.getVariant());
            if (boilerType != null) {
                nextFormType = boilerType;
            }
        }

        removeRequirementEffects(growthStage);

        if (entity instanceof com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph xenomorph) {
            xenomorph.getCocoonManager().prepare(nextFormType, growthStage.alternate().orElse(null), growthStage.cocooning());
            return GrowthResult.CocoonStarted.INSTANCE;
        }

        // Snapshot hive faction membership before the transition discards the old entity (UUID is in the default
        // blacklist, so the new entity has a fresh id and wouldn't otherwise inherit membership).
        var factionSnapshot = FactionMembershipTransfer.snapshot(entity);

        var transitionResult = EntityTransitionUtil.transitionInto(entity, nextFormType, TRANSITION_NBT_KEY_BLACKLIST);

        Entity nextForm = null;

        if (transitionResult instanceof EntityTransitionUtil.EntityTransitionResult.Success<?> success) {
            nextForm = success.newEntity();
        }

        if (nextForm == null) {
            return new GrowthResult.FailedTransitionResult(transitionResult);
        }

        // Carry over hive membership, and auto-join the parent location if the new form is a xenomorph in territory
        // (covers the chestburster -> adolescent case where the old form wasn't a faction member).
        FactionMembershipTransfer.apply(factionSnapshot, nextForm);
        if (entity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            LocationMembership.autoJoinAtPosition(nextForm, serverLevel);
        }

        return new GrowthResult.Success(nextForm);
    }

    private boolean canNeverGrow() {
        return entity.isPoisoned() || entity.isIrradiated();
    }

    private void removeRequirementEffects(GrowthStage stage) {
        for (var requirement : stage.requirements()) {
            if (requirement instanceof GrowthRequirement.MobEffectRequirement effectRequirement) {
                entity.removeEffect(effectRequirement.effect());
            }
        }
    }

    private boolean canBecomeBoiler(EntityType<?> nextFormType) {
        if (!isProperTransition(nextFormType)) {
            return false;
        }

        // Born of an irradiated host: guaranteed, not a roll - the rads did their work in the womb.
        return entity.isBoilerDestined()
            || shouldBecomeBoilerFromGeneDecay()
            || shouldBecomeBoilerFromAcidVolatility();
    }

    private boolean isProperTransition(EntityType<?> nextFormType) {
        var isCurrentlyAdolescent = entity.getType().is(AlienEntityTypeTags.ADOLESCENTS);
        var willGrowIntoAdult = nextFormType.is(AlienEntityTypeTags.XENOMORPHS);

        return isCurrentlyAdolescent && willGrowIntoAdult;
    }

    private boolean shouldBecomeBoilerFromAcidVolatility() {
        return switch (entity.getGeneManager()) {
            case GeneManagerProxy.EMPTY ignored -> false;
            case GeneManagerProxy.Wrapper wrapper -> {
                var geneContainer = wrapper.geneManager().getGeneContainer();
                var additiveAcidVolatility = geneContainer.getActiveGeneMap()
                    .getValue(Genes.ACID_VOLATILITY, GeneOperationType.ADDITIVE);
                var multiplicativeAcidVolatility = geneContainer.getActiveGeneMap()
                    .getValue(Genes.ACID_VOLATILITY, GeneOperationType.MULTIPLICATIVE);

                var totalAcidVolatility = additiveAcidVolatility + multiplicativeAcidVolatility;

                yield entity.getRandom().nextDouble() < totalAcidVolatility;
            }
        };
    }

    private boolean shouldBecomeBoilerFromGeneDecay() {
        if (!AVPHuman.MOD.isLoaded()) {
            return false;
        }

        var geneCarrier = (GeneCarrier) entity;
        var geneDecayLevel = GeneIntegrityUtil.getGeneDecayLevel(geneCarrier);

        return switch (geneDecayLevel) {
            case FATAL -> true;
            case STABLE, UNSTABLE -> false;
            case VOLATILE -> {
                var totalGeneIntegrity = Math.abs(GeneIntegrityUtil.getTotalGeneticIntegrity(geneCarrier));
                var chance = totalGeneIntegrity - Math.floor(totalGeneIntegrity);
                yield entity.getRandom().nextDouble() < chance;
            }
        };
    }

    @Override
    public void load(CompoundTag compoundTag) {
        if (compoundTag.contains(GROWTH_TIME_IN_TICKS_TAG_KEY)) {
            this.growthTimeInTicks = compoundTag.getInt(GROWTH_TIME_IN_TICKS_TAG_KEY);
        }
    }

    @Override
    public void save(CompoundTag compoundTag) {
        compoundTag.putInt(GROWTH_TIME_IN_TICKS_TAG_KEY, growthTimeInTicks);
    }

    public GrowthManager setGrowOverTime(boolean growOverTime) {
        this.growOverTime = growOverTime;
        return this;
    }

    public boolean hasActiveGrowthRequirement() {
        var stage = activeRequirementGrowthStage != null ? activeRequirementGrowthStage : findMatchingGrowthStage();
        return stage != null && stage.hasRequirements() && allRequirementsMet(stage.requirements());
    }

    public sealed interface GrowthResult {

        enum AlreadyFullyGrown implements GrowthResult {
            INSTANCE
        }

        enum CanNotGrow implements GrowthResult {
            INSTANCE
        }

        enum CocoonStarted implements GrowthResult {
            INSTANCE
        }

        record FailedTransitionResult(EntityTransitionUtil.EntityTransitionResult result) implements GrowthResult {}

        record Success(Entity newEntity) implements GrowthResult {}
    }
}
