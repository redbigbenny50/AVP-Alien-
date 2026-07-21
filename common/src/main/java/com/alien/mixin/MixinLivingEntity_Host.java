package com.alien.mixin;

import com.alien.common.gameplay.entity.living.alien.parasite.Parasite;
import com.alien.common.gameplay.hive.structure.HostParking;
import com.alien.common.model.alien.Host;
import com.alien.common.registry.InfectionRegistry;
import com.alien.common.util.AlienEmbryoUtil;
import com.alien.compatibility.avp_human.GeneContainerProxy;
import com.just.core.functional.option.Option;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_Host extends Entity implements Host {

    @Unique
    private static final String NBT_PARASITE_GENES = "parasiteGenes";

    @Unique
    private static final String NBT_EMBRYO_GROWTH_TIME_IN_TICKS = "embryoGrowthTimeInTicks";

    @Unique
    private static final String NBT_EMBRYO_TYPE = "embryoType";

    @Unique
    private int embryoGrowthTimeInTicks;

    @Unique
    private static final String NBT_HOST_EMBED_GAME_TIME = "hostEmbedGameTime";

    @Unique
    private long hostEmbedGameTime = Long.MIN_VALUE;

    @Unique
    private Option<EntityType<?>> embryoTypeOption = Option.none();

    @Unique
    private GeneContainerProxy parasiteGeneContainer;

    public MixinLivingEntity_Host(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * A webbed host does not suffocate.
     * <p>
     * Hosts are stored embedded in resin webbing, and anything taller or wider than one block ends up with part of
     * itself inside a solid - which vanilla reads as being buried and applies IN_WALL damage for. The hive was quietly
     * killing its own larder: small mobs kept fine, anything bigger died in the chamber. Cargo in storage is not buried
     * alive, so suffocation is refused for as long as it is parked.
     * <p>
     * Scoped to {@link HostParking#isParked} (noAi AND carrying the hive's embed stamp), so it cannot be abused to make
     * an ordinary mob suffocation-proof - a host that is cut loose starts suffocating again like anything else.
     */
    @Inject(at = @At("HEAD"), method = "hurt", cancellable = true)
    public void avp_alien$parkedHostsDoNotSuffocate(
        DamageSource source,
        float amount,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (!source.is(DamageTypes.IN_WALL)) {
            return;
        }
        if (HostParking.isParked(LivingEntity.class.cast(this))) {
            callbackInfo.setReturnValue(false); // no damage dealt
        }
    }

    @Inject(at = @At("HEAD"), method = "tick")
    public void tick(CallbackInfo callbackInfo) {
        var self = LivingEntity.class.cast(this);
        AlienEmbryoUtil.runAlienEmbryoRoutines(self);
    }

    @Inject(at = @At("HEAD"), method = "readAdditionalSaveData")
    public void readAdditionalSaveData(CompoundTag compoundTag, CallbackInfo callbackInfo) {
        this.embryoGrowthTimeInTicks = compoundTag.getInt(NBT_EMBRYO_GROWTH_TIME_IN_TICKS);

        if (compoundTag.contains(NBT_EMBRYO_TYPE)) {
            var resourceLocationString = compoundTag.getString(NBT_EMBRYO_TYPE);
            var resourceLocation = ResourceLocation.parse(resourceLocationString);
            var entityTypeHolderOptional = BuiltInRegistries.ENTITY_TYPE.getHolder(resourceLocation);

            entityTypeHolderOptional.ifPresent(
                $ -> this.embryoTypeOption = Option.some(BuiltInRegistries.ENTITY_TYPE.get(resourceLocation))
            );
        }

        if (compoundTag.contains(NBT_PARASITE_GENES)) {
            var tag = compoundTag.getCompound(NBT_PARASITE_GENES);
            getOrCreateParasiteGeneContainer().load(tag);
        }
        if (compoundTag.contains(NBT_HOST_EMBED_GAME_TIME)) {
            this.hostEmbedGameTime = compoundTag.getLong(NBT_HOST_EMBED_GAME_TIME);
        }
    }

    @Inject(at = @At("HEAD"), method = "addAdditionalSaveData")
    public void addAdditionalSaveData(CompoundTag compoundTag, CallbackInfo callbackInfo) {
        compoundTag.putInt(NBT_EMBRYO_GROWTH_TIME_IN_TICKS, embryoGrowthTimeInTicks);

        embryoTypeOption.ifSome(embryoType -> {
            var resourceLocation = BuiltInRegistries.ENTITY_TYPE.getKey(embryoType);
            compoundTag.putString(NBT_EMBRYO_TYPE, resourceLocation.toString());
        });

        var tag = new CompoundTag();
        getOrCreateParasiteGeneContainer().save(tag);
        compoundTag.put(NBT_PARASITE_GENES, tag);
        if (this.hostEmbedGameTime != Long.MIN_VALUE) {
            compoundTag.putLong(NBT_HOST_EMBED_GAME_TIME, this.hostEmbedGameTime);
        }
    }

    @Override
    public void implantEmbryo(Parasite parasite) {
        var infectionOption = InfectionRegistry.get(getType(), parasite.getType());

        infectionOption.ifSome(infection -> {
            setEmbryoType(infection.embryoType());

            // Assign the active genes from the parasite to the embryo's gene container.
            parasite.getGeneManager()
                .transfer(getOrCreateParasiteGeneContainer(), false);

            var self = LivingEntity.class.cast(this);

            if (self instanceof Mob mob) {
                // Set persistence required since we don't want this mob to despawn while it is carrying an embryo.
                mob.setPersistenceRequired();
            }
        });
    }

    @Override
    public GeneContainerProxy getOrCreateParasiteGeneContainer() {
        if (parasiteGeneContainer == null) {
            this.parasiteGeneContainer = GeneContainerProxy.create();
        }

        return parasiteGeneContainer;
    }

    @Override
    public Option<EntityType<?>> getEmbryoType() {
        return embryoTypeOption;
    }

    @Override
    public void setEmbryoType(@Nullable EntityType<?> embryoType) {
        this.embryoTypeOption = Option.ofNullable(embryoType);
    }

    @Override
    public int getEmbryoGrowthTimeInTicks() {
        return embryoGrowthTimeInTicks;
    }

    @Override
    public void setEmbryoGrowthTimeInTicks(int embryoGrowthTimeInTicks) {
        this.embryoGrowthTimeInTicks = embryoGrowthTimeInTicks;
    }

    @Override
    public long getEmbedGameTime() {
        return this.hostEmbedGameTime;
    }

    @Override
    public void setEmbedGameTime(long gameTime) {
        this.hostEmbedGameTime = gameTime;
    }
}
