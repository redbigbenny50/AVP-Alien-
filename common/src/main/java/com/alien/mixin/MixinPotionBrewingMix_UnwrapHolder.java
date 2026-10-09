package com.alien.mixin;

import com.blib.api.common.registry.v1.BLibHolder;
import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Unwraps BLibHolder wrappers at the one point EVERY brewing registration path must pass through.
 * <p>
 * <b>The bug.</b> {@code Registry.asHolderIdMap()} indexes the registry's own {@code Holder.Reference} objects BY
 * IDENTITY, so a wrapper resolves to id -1 and anything that serialises it dies with "Unregistered holder in
 * ResourceKey[minecraft:root / minecraft:potion]". It has now surfaced twice, by two different routes: Immersive
 * Engineering encoding {@code update_recipes}, and - reported 26 Aug - a server CRASHING ON SAVE because a brewed
 * metamorphosis potion sat in a player's inventory and the {@code PotionContents} component could not be written.
 * </p>
 * <p>
 * 🚨 WHY THIS SITS ON Mix AND NOT ON A REGISTRATION METHOD. The previous fix, {@code MixinPotionBrewing_UnwrapHolder},
 * targets {@code PotionBrewing.Builder.addMix} - which is the NEOFORGE path. BLib's Fabric container registers through
 * {@code FabricBrewingRecipeRegistryBuilder} calling {@code builder.registerPotionRecipe(...)} instead, so on Fabric
 * the old mixin never fired at all and the crash survived the fix. Chasing that with a second loader-specific mixin
 * would just leave the next path uncovered.
 * </p>
 * <p>
 * ⭐ {@code Mix} is a record and it is the LAST common step: a brewing entry cannot exist without one, whichever method
 * built it. Unwrapping in its constructor covers both loaders, any future loader, and any other BLib-based mod
 * registering potions in the same instance.
 * </p>
 * <p>
 * ⚠ TIMING IS WHY THIS WORKS AND REGISTRATION-TIME UNWRAPPING DOES NOT. {@code AlienPotions.registerBrewingRecipes}
 * runs during mod init, before the potions are in {@code BuiltInRegistries.POTION}, so {@code getBackingHolder()}
 * returns NULL there and crashes world load - see the comment on that method. A {@code Mix} is only ever constructed
 * during server construction, by which point every holder is bound.
 * </p>
 * <p>
 * ⚠ A wrapper that still will not bind is passed through UNTOUCHED. A wrapper in the table is a networking bug; a null
 * in the table is an immediate crash.
 * </p>
 */
// WARNING: targets = ... rather than @Mixin(PotionBrewing.Mix.class). Mix is PACKAGE-PRIVATE, so the class literal
// will not compile from outside net.minecraft.world.item.alchemy. A string target is the supported way to reach it.
@Mixin(targets = "net.minecraft.world.item.alchemy.PotionBrewing$Mix")
public abstract class MixinPotionBrewingMix_UnwrapHolder {

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Holder<?> avp_alien$unwrapFrom(Holder<?> from) {
        return avp_alien$unwrap(from);
    }

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private static Holder<?> avp_alien$unwrapTo(Holder<?> to) {
        return avp_alien$unwrap(to);
    }

    private static Holder<?> avp_alien$unwrap(Holder<?> holder) {
        if (!(holder instanceof BLibHolder<?> blibHolder)) {
            return holder;
        }

        var backing = blibHolder.getBackingHolder();

        return backing == null ? holder : backing;
    }
}
