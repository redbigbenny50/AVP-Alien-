package com.alien.mixin;

import com.alien.common.model.alien.FreeMob;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.goap.v1.GOAPUser;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MixinMob_IncapacitateHost extends LivingEntity implements FreeMob {

    protected MixinMob_IncapacitateHost(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected boolean isImmobile() {
        if (this.getPassengers().stream().anyMatch(entity -> entity.getType().is(AlienEntityTypeTags.PARASITES))) {
            return true;
        }

        return super.isImmobile();
    }

    @Override
    public boolean isUsingItem() {
        if (this.getPassengers().stream().anyMatch(entity -> entity.getType().is(AlienEntityTypeTags.PARASITES))) {
            return false;
        }

        return super.isUsingItem();
    }

    /**
     * A carried host does not fight back - or do anything else - while it is the captor's recorded cargo.
     * <p>
     * capture() calls removeFreedom(), but that only disables a GOAP agent; a VANILLA mob has none, and its Brain/goal
     * AI was never touched - so a carried piglin kept its full combat brain and could kill the drone hauling it.
     * Cancelling serverAiStep suspends sensing, targeting, attacks and item use for exactly the ticks the carry
     * bookkeeping covers - TRANSIENT by construction, unlike setNoAi(true), which persists in NBT and would leave a
     * permanently brainless mob if any drop path (captor killed mid-haul) missed the restore. On drop its AI simply
     * resumes; delivery then parks it under noAi as before. The outgoing-damage guard in MixinLivingEntity_Host
     * backstops anything this cannot reach.
     */
    @Inject(at = @At("HEAD"), method = "serverAiStep", cancellable = true)
    private void avp_alien$carriedHostsDoNotAct(CallbackInfo callbackInfo) {
        if (com.alien.common.gameplay.hive.party.HostCaptureTask.isBeingCarriedHome(this)) {
            callbackInfo.cancel();
        }
    }

    @Override
    public void removeFreedom() {
        var self = Mob.class.cast(this);
        self.xxa = 0;
        self.zza = 0;
        self.yya = 0;
        self.yBodyRot = 0;
        self.setSpeed(0.0f);
        self.setAggressive(false);
        self.setSilent(true);

        var agent = ((GOAPUser<?>) self).blib$getGOAPAgentOrNull();

        if (agent != null) {
            agent.setEnabled(false);
        }
    }

    @Override
    public void restoreFreedom() {
        var self = Mob.class.cast(this);
        self.setAggressive(true);
        self.setSilent(false);

        var agent = ((GOAPUser<?>) self).blib$getGOAPAgentOrNull();

        if (agent != null) {
            agent.setEnabled(true);
        }
    }
}
