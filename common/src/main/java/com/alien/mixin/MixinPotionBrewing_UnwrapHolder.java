package com.alien.mixin;

import com.blib.api.common.registry.v1.BLibHolder;
import net.minecraft.core.Holder;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Keeps BLibHolder wrappers out of vanilla's brewing table, so brewed potions can be networked.
 * <p>
 * <b>The bug this fixes.</b> A recipe holding a wrapper cannot be encoded: {@code Registry.asHolderIdMap()} indexes the
 * registry's own {@code Holder.Reference} objects BY IDENTITY, so a wrapper resolves to id -1 and the packet dies with
 * "Unregistered holder in ResourceKey[minecraft:root / minecraft:potion]". Immersive Engineering surfaces it by
 * building a machine recipe for every brewable potion; those ship in {@code update_recipes}, so in a pack with both
 * mods the encode fails and the player is DISCONNECTED ON JOIN.
 * <p>
 * <b>Why here and not at registration.</b> {@code AlienPotions.registerBrewingRecipes} runs during mod init, before the
 * potions are actually in {@code BuiltInRegistries.POTION} - unwrapping there returns NULL and crashes world load
 * instead. {@code addMix} runs from {@code PotionBrewing.bootstrap} during server construction, by which point every
 * holder is bound, so this is the first moment the real reference can be obtained. It is also the LAST moment before
 * the wrapper is stored in a {@code Mix} for good.
 * <p>
 * Falls through untouched for anything that is not a BLibHolder, and for a BLibHolder that somehow still will not bind
 * - a wrapper in the table is a networking bug, but a null in the table is an immediate crash.
 */
@Mixin(PotionBrewing.Builder.class)
public abstract class MixinPotionBrewing_UnwrapHolder {

    @ModifyVariable(method = "addMix", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Holder<Potion> avp_alien$unwrapInput(Holder<Potion> input) {
        return avp_alien$unwrap(input);
    }

    @ModifyVariable(method = "addMix", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private Holder<Potion> avp_alien$unwrapResult(Holder<Potion> result) {
        return avp_alien$unwrap(result);
    }

    private static Holder<Potion> avp_alien$unwrap(Holder<Potion> holder) {
        if (!(holder instanceof BLibHolder<Potion> blibHolder)) {
            return holder;
        }

        var backing = blibHolder.getBackingHolder();
        return backing == null ? holder : backing;
    }
}
