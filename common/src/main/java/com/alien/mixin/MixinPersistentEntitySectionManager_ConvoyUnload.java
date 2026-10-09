package com.alien.mixin;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.hive.convoy.ConvoyMemberTracker;
import com.alien.common.gameplay.hive.location.HiveIdentityReserveUnloadHandler;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;

/**
 * Recalls materialized convoy members before Minecraft serializes an unloading chunk.
 */
@Mixin(PersistentEntitySectionManager.class)
public abstract class MixinPersistentEntitySectionManager_ConvoyUnload<T extends EntityAccess> {

    @Shadow
    @Final
    private EntitySectionStorage<T> sectionStorage;

    @Inject(method = "processChunkUnload", at = @At("HEAD"))
    private void avp_alien$returnConvoyMembersBeforeChunkUnload(
        long chunkPos,
        CallbackInfoReturnable<Boolean> cir
    ) {
        // \u2b50\u2b50\u2b50 NEVER VIRTUALIZE DURING SHUTDOWN. THIS IS THE FIX FOR "reloging IS killing workers".
        //
        // \u26a0\u26a0 THE SHUTDOWN ORDER DESTROYS BOTH COPIES OF A VIRTUALIZED MEMBER:
        // 1. stopServer -> saveAllChunks : chunks written WITH their entities
        // 2. BLib writes factions at the TAIL of saveEverything - the bank as it stood, WITHOUT the deposit
        // 3. level.close() -> chunks unload -> THIS METHOD banks the member and DISCARDS the entity
        // 4. closing re-saves the chunks - now WITHOUT the discarded entity
        // The deposit exists only in memory that is never written again, and the entity is gone from the chunk that
        // was rewritten. Marking the faction dirty in step 3 cannot help: BLib already wrote in step 2.
        //
        // Confirmed in the field: members=4 before quitting, members=1 after, banked=0 on both sides, biomass
        // preserved across the same relog (so the location itself saves fine), and [stated] "the are genuienly gone i
        // have the em vision on if they were still there id see em".
        //
        // An entity left alone simply serializes with its chunk and comes back. Queens are already exempted from
        // virtualization for this exact reason - "she vanishes on relog and the orphaned location then decays" - so
        // this is that same fix, applied to the one moment when it can happen to anything.
        if (com.alien.Alien.isShuttingDown()) {
            return;
        }

        var convoyMembers = new ArrayList<Alien>();
        var hiveMembers = new ArrayList<Alien>();
        this.sectionStorage.getExistingSectionsInChunk(chunkPos)
            .flatMap(EntitySection::getEntities)
            .forEach(entity -> {
                if (entity instanceof Alien alien && alien.convoyMembership() != null && !alien.isRemoved()) {
                    convoyMembers.add(alien);
                } else if (entity instanceof Alien alien && !alien.isRemoved()) {
                    hiveMembers.add(alien);
                }
            });

        // ONLY discard when the convoy actually banked her - exactly what the hive branch below already does.
        //
        // returnToReserves refuses in three cases: the convoy no longer exists, the alien is not in its
        // materializedMembers map, or there is no membership at all. It clears the stale membership and returns
        // false. Discarding anyway destroyed the entity with NOTHING saved - and a founding queen who arrived with a
        // retinue keeps a stale membership after that convoy finishes, so a chunk unload mid-carve deleted her
        // outright. She vanished halfway through digging her claustral cell and did not come back on relog.
        //
        // If the convoy will not take her, she is not a convoy member any more; let her serialize like any other
        // entity.
        for (var alien : convoyMembers) {
            if (ConvoyMemberTracker.returnUnloaded(alien)) {
                alien.discard();
            }
        }
        for (var alien : hiveMembers) {
            if (HiveIdentityReserveUnloadHandler.returnUnloaded(alien)) {
                alien.discard();
            }
        }
    }
}
