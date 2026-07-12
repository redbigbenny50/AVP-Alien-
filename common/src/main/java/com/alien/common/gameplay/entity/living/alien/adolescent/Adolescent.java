package com.alien.common.gameplay.entity.living.alien.adolescent;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.GrowthManager;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienDataSyncKeys;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.alien.common.util.AlienPredicates;
import com.blib.api.common.data_sync.v1.DataAccessor;
import com.blib.api.common.entity.v1.BLibEntityPredicates;
import com.blib.api.common.entity.v1.PlayerStatConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Adolescent extends Alien {

    public static AttributeSupplier.Builder createAdolescentAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 0f)
            .add(Attributes.ARMOR_TOUGHNESS, 0f)
            .add(Attributes.ATTACK_DAMAGE, PlayerStatConstants.BASE_HEALTH * 0.1F)
            .add(Attributes.FOLLOW_RANGE, 16F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0F)
            .add(Attributes.MAX_HEALTH, PlayerStatConstants.BASE_HEALTH * 0.5F)
            .add(Attributes.MOVEMENT_SPEED, PlayerStatConstants.BASE_WALK_SPEED * 1.025F)
            .add(Attributes.SCALE, 0.7F);
    }

    private static final String NBT_HAS_DORSAL_TUBES = "hasDorsalTubes";

    public final DataAccessor<Boolean> hasDorsalTubes;

    private final AdolescentAnimationDispatcher animationDispatcher;

    private final GrowthManager growthManager;

    public Adolescent(EntityType<? extends Adolescent> entityType, Level level) {
        super(entityType, level);

        this.hasDorsalTubes = new DataAccessor<>(this, AlienDataSyncKeys.ADOLESCENT_HAS_DORSAL_TUBES.get());

        this.animationDispatcher = new AdolescentAnimationDispatcher(this);
        this.growthManager = new GrowthManager(this)
            .setGrowOverTime(true);
    }

    @Override
    public @Nullable EntityType<? extends Alien> getTypeForVariant(AlienVariant alienVariant) {
        return getType(alienVariant, isRoyal());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(
            3,
            new AvoidEntityGoal<>(
                this,
                LivingEntity.class,
                8,
                1,
                1.2,
                entity -> entity instanceof Alien alien
                    ? AlienPredicates.areAliensEnemies(this, alien)
                    : !BLibEntityPredicates.isInvulnerable(entity)
            )
        );
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.5));
    }

    @Override
    public void tick() {
        super.tick();
        growthManager.tick();
        // Eat a spent egg / spent facehugger lying next to us to skip a molt phase (and clear the litter).
        com.alien.common.gameplay.entity.living.alien.MoltFeeding.tickFeeding(this);

        if (!level().isClientSide) {
            getHostType().ifSome(hostType -> hasDorsalTubes.set(!hostType.is(AlienEntityTypeTags.RUNNER_HOSTS)));
        }
    }

    @Override
    protected float getHealthRegenPerSecond() {
        return 0.5F;
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        growthManager.load(compoundTag);

        if (compoundTag.contains(NBT_HAS_DORSAL_TUBES)) {
            hasDorsalTubes.set(compoundTag.getBoolean(NBT_HAS_DORSAL_TUBES));
        }
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        growthManager.save(compoundTag);

        compoundTag.putBoolean(NBT_HAS_DORSAL_TUBES, hasDorsalTubes.get());
    }

    public AdolescentAnimationDispatcher getAnimationDispatcher() {
        return animationDispatcher;
    }

    public static @Nullable EntityType<? extends Alien> getType(AlienVariant alienVariant, boolean isRoyal) {
        if (isRoyal) {
            return switch (alienVariant) {
                case NORMAL -> AlienEntityTypes.ROYAL_ADOLESCENT.get();
                case NETHER -> AlienEntityTypes.ROYAL_NETHER_ADOLESCENT.get();
                case ABERRANT -> AlienEntityTypes.ROYAL_ABERRANT_ADOLESCENT.get();
                case IRRADIATED -> null;
            };
        }

        return switch (alienVariant) {
            case NORMAL -> AlienEntityTypes.ADOLESCENT.get();
            case NETHER -> AlienEntityTypes.NETHER_ADOLESCENT.get();
            case ABERRANT -> AlienEntityTypes.ABERRANT_ADOLESCENT.get();
            case IRRADIATED -> null;
        };
    }
}
