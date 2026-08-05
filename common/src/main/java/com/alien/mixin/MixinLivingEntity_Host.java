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
    private static final String NBT_SUPPRESSION_DOSE_COUNT = "suppressionDoseCount";

    @Unique
    private int suppressionDoseCount;

    @Unique
    private static final String NBT_JELLY_TOXICITY = "jellyToxicity";

    @Unique
    private int jellyToxicity;

    @Unique
    private static final String NBT_SUPPRESSION_SPENT = "suppressionSpent";

    @Unique
    private boolean suppressionSpent;

    @Unique
    private static final String NBT_EMBRYO_WITHERED = "embryoWithered";

    @Unique
    private boolean embryoWithered;

    @Unique
    private static final String NBT_EMBRYO_IRRADIATED = "embryoIrradiated";

    /** Jelly Sickness IV - the wither-grade lethal tier (amplifier 3 = tier IV). */
    @Unique
    private static final int LETHAL_JELLY_SICKNESS_AMPLIFIER = 3;

    @Unique
    private boolean embryoIrradiated;

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
        var self = LivingEntity.class.cast(this);
        // A carried host cannot HARM anyone: the serverAiStep suspension in MixinMob_IncapacitateHost stops it
        // acting, and this victim-side guard voids whatever slips past that - an arrow it loosed just before the
        // grab, or an AI that deals damage outside the suspended tick ([stated] "i just watched a piglin kill a
        // drone while being carried"). getEntity() is the CAUSING entity (the shooter for a projectile), so ranged
        // and melee both resolve to the carried host.
        if (
            source.getEntity() instanceof LivingEntity attacker
                && attacker != self
                && com.alien.common.gameplay.hive.party.HostCaptureTask.isBeingCarriedHome(attacker)
        ) {
            callbackInfo.setReturnValue(false); // no damage dealt
            return;
        }
        if (!source.is(DamageTypes.IN_WALL)) {
            return;
        }
        if (HostParking.isParked(self)) {
            callbackInfo.setReturnValue(false); // no damage dealt
            return;
        }
        // THE CARRY LEG OF THE SAME RULE ([stated] "carried hosts dont suffocate. large hosts are suffocating in
        // walls if the xeno walks them around"): a xenomorph hauling a captive home drags it through corridors and
        // web gaps sized for the xeno, not the cargo - a villager clears them, an iron golem clips the wall the
        // whole way and arrives dead. While the hive's own carry bookkeeping says this entity IS the captor's
        // recorded cargo, wall damage is waived. Same anti-abuse scoping as the parked case: merely riding an alien
        // is not enough - HostCaptureTask.carriedHost must name this exact entity - and the exemption ends the
        // moment it is dropped or parked (parking then takes over above).
        if (com.alien.common.gameplay.hive.party.HostCaptureTask.isBeingCarriedHome(self)) {
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

        this.suppressionDoseCount = compoundTag.getInt(NBT_SUPPRESSION_DOSE_COUNT);
        this.jellyToxicity = compoundTag.getInt(NBT_JELLY_TOXICITY);
        this.suppressionSpent = compoundTag.getBoolean(NBT_SUPPRESSION_SPENT);
        this.embryoWithered = compoundTag.getBoolean(NBT_EMBRYO_WITHERED);
        this.embryoIrradiated = compoundTag.getBoolean(NBT_EMBRYO_IRRADIATED);
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

        compoundTag.putInt(NBT_SUPPRESSION_DOSE_COUNT, suppressionDoseCount);
        compoundTag.putInt(NBT_JELLY_TOXICITY, jellyToxicity);
        compoundTag.putBoolean(NBT_SUPPRESSION_SPENT, suppressionSpent);
        compoundTag.putBoolean(NBT_EMBRYO_WITHERED, embryoWithered);
        compoundTag.putBoolean(NBT_EMBRYO_IRRADIATED, embryoIrradiated);
    }

    /**
     * "You die and the burster appears." Poisoning the host is never a way to cheat the birth: a marked embryo
     * chest-bursts on its host's death, whatever killed them, instead of going to the grave. Three marks qualify:
     * <ul>
     * <li>the jelly DEATH SENTENCE ({@code embryoWithered}) - the burster emerges withered;</li>
     * <li>JELLY SICKNESS IV, the lethal tier - a host who dies mid-sickness marks the embryo withered here, so dying
     * early to the sickness still produces the demon rather than a clean escape;</li>
     * <li>RADIATION ({@code embryoIrradiated}) - a host who dies early to the rads still gives birth, and the burster
     * carries its boiler destiny out of the corpse.</li>
     * </ul>
     */
    @Inject(at = @At("HEAD"), method = "die")
    public void avp_alien$witheredEmbryoDeathBurst(DamageSource damageSource, CallbackInfo callbackInfo) {
        var self = LivingEntity.class.cast(this);

        if (self.level().isClientSide || getEmbryoType().isNone()) {
            return;
        }

        // Dying while the lethal sickness tier is on the host counts as the death sentence landing.
        var jellySickness = self.getEffect(com.alien.common.registry.init.AlienMobEffects.getJellySicknessHolder());
        if (jellySickness != null && jellySickness.getAmplifier() >= LETHAL_JELLY_SICKNESS_AMPLIFIER) {
            setEmbryoWithered(true);
        }

        if (!isEmbryoWithered() && !isEmbryoIrradiated()) {
            return;
        }

        AlienEmbryoUtil.birthEmbryos(self);
        self.level()
            .playSound(
                null,
                self,
                com.alien.common.registry.init.AlienSoundEvents.ENTITY_CHESTBURSTER_BURST.get(),
                net.minecraft.sounds.SoundSource.HOSTILE,
                0.25F,
                1
            );
        removeEmbryo();
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
    public int getSuppressionDoseCount() {
        return suppressionDoseCount;
    }

    @Override
    public void setSuppressionDoseCount(int doseCount) {
        this.suppressionDoseCount = doseCount;
    }

    @Override
    public int getJellyToxicity() {
        return jellyToxicity;
    }

    @Override
    public void setJellyToxicity(int toxicity) {
        this.jellyToxicity = toxicity;
    }

    @Override
    public boolean isSuppressionSpent() {
        return suppressionSpent;
    }

    @Override
    public void setSuppressionSpent(boolean spent) {
        this.suppressionSpent = spent;
    }

    @Override
    public boolean isEmbryoWithered() {
        return embryoWithered;
    }

    @Override
    public void setEmbryoWithered(boolean withered) {
        this.embryoWithered = withered;
    }

    @Override
    public boolean isEmbryoIrradiated() {
        return embryoIrradiated;
    }

    @Override
    public void setEmbryoIrradiated(boolean irradiated) {
        this.embryoIrradiated = irradiated;
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
