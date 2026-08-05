package com.alien.mixin;

import com.alien.common.gameplay.hive.lifecycle.NukeStrike;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tells the hive system a nuke went off.
 * <h2>Why a mixin and not their callback</h2> AVP: Human's explosion already walks the entities in the BLAST and
 * transmutes aliens to irradiated, guarded by {@code AVPAlien.MOD.isLoaded()}. That is not enough here: a hive dies if
 * the explosion OR THE IRRADIATED BIOME reaches its territory, and the biome radius is 128 against a blast of 5. A hive
 * the fallout swallows but the blast never touches is invisible to an entity loop, so the reaction needs the detonation
 * point and BOTH radii and has to test hive TERRITORIES rather than entities. BLib's ExplosionCallbacks are fixed by
 * the caller at build time, so there is nothing to hook on the explosion itself.
 * <h2>{@code @Pseudo}</h2> The target class only exists when AVP: Human is installed. {@code @Pseudo} is what makes
 * Mixin skip a mixin whose target is absent instead of failing to apply - without it, this would crash every instance
 * that does not have that mod. Nukes are AVP: Human content, so there is nothing to react to in those instances anyway.
 * <h2>Who gets blamed</h2> {@code PrimedNuke} tracks a fuse and a block state and nothing else - no owner, no placer -
 * and it is normally set off by redstone, so there is no player on the call stack to attribute it to. The nearest
 * player at detonation is used instead: whoever armed it is almost always the one standing there watching. If nobody is
 * close enough, nobody is blamed and the hive simply dies unattributed.
 */
@Pseudo
@Mixin(targets = "com.human.util.NuclearExplosionUtil", remap = false)
public abstract class MixinNuclearExplosionUtil_HiveStrike {

    @Inject(method = "createNuclearExplosion", at = @At("HEAD"), remap = false)
    private static void avp_alien$reactToNuke(
        ServerLevel level,
        Vec3 center,
        int biomeRadius,
        int blastRadius,
        CallbackInfoReturnable<Object> callbackInfo
    ) {
        NukeStrike.onDetonation(level, center, biomeRadius, blastRadius);
    }
}
