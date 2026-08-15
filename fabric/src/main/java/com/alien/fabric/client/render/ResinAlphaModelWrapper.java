package com.alien.fabric.client.render;

import com.alien.AlienResources;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.function.Supplier;

public class ResinAlphaModelWrapper extends ForwardingBakedModel {

    private static final float RESIN_ALPHA = 0.75F;

    private static final int RESIN_COLOR = ((int) (RESIN_ALPHA * 255.0F) << 24) | 0x00FFFFFF;

    private static final Set<ResourceLocation> RESIN_MODELS = Set.of(
        AlienResources.location("block/aberrant_resin_vein"),
        AlienResources.location("block/aberrant_resin_vein_2"),
        AlienResources.location("block/aberrant_resin_vein_3"),
        AlienResources.location("block/aberrant_resin_vein_4"),
        AlienResources.location("block/aberrant_resin_web"),
        AlienResources.location("block/irradiated_resin_vein"),
        AlienResources.location("block/irradiated_resin_vein_2"),
        AlienResources.location("block/irradiated_resin_vein_3"),
        AlienResources.location("block/irradiated_resin_vein_4"),
        AlienResources.location("block/irradiated_resin_web"),
        AlienResources.location("block/nether_resin_vein"),
        AlienResources.location("block/nether_resin_vein_2"),
        AlienResources.location("block/nether_resin_vein_3"),
        AlienResources.location("block/nether_resin_vein_4"),
        AlienResources.location("block/nether_resin_web"),
        AlienResources.location("block/resin_vein"),
        AlienResources.location("block/resin_vein_1"),
        AlienResources.location("block/resin_vein_2"),
        AlienResources.location("block/resin_vein_3"),
        AlienResources.location("block/resin_vein_4"),
        AlienResources.location("block/resin_web")
    );

    private ResinAlphaModelWrapper(BakedModel wrapped) {
        this.wrapped = wrapped;
    }

    public static void register() {
        ModelLoadingPlugin.register(
            pluginContext -> pluginContext.modifyModelAfterBake()
                .register(ModelModifier.WRAP_PHASE, (model, context) -> {
                    // Many baked models have a null resourceId (generated/special models); RESIN_MODELS is an immutable
                    // Set, whose contains(null) throws NPE. Without this guard the modifier threw once per such model,
                    // spamming "Failed to modify baked model after bake" thousands of times every resource reload.
                    var resourceId = context.resourceId();
                    if (model == null || resourceId == null || !RESIN_MODELS.contains(resourceId)) {
                        return model;
                    }

                    return new ResinAlphaModelWrapper(model);
                })
        );
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(
        BlockAndTintGetter blockView,
        BlockState state,
        BlockPos pos,
        Supplier<RandomSource> randomSupplier,
        RenderContext context
    ) {
        emitWithResinAlpha(context, () -> wrapped.emitBlockQuads(blockView, state, pos, randomSupplier, context));
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<RandomSource> randomSupplier, RenderContext context) {
        emitWithResinAlpha(context, () -> wrapped.emitItemQuads(stack, randomSupplier, context));
    }

    private static void emitWithResinAlpha(RenderContext context, Runnable emitter) {
        context.pushTransform(quad -> {
            var material = translucentMaterial();

            if (material != null) {
                quad.material(material);
            }

            quad.color(RESIN_COLOR, RESIN_COLOR, RESIN_COLOR, RESIN_COLOR);
            return true;
        });

        try {
            emitter.run();
        } finally {
            context.popTransform();
        }
    }

    private static RenderMaterial translucentMaterial() {
        var renderer = RendererAccess.INSTANCE.getRenderer();

        if (renderer == null) {
            return null;
        }

        return renderer.materialFinder().blendMode(BlendMode.TRANSLUCENT).find();
    }
}
