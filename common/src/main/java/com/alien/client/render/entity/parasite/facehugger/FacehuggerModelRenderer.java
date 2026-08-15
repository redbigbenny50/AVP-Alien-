package com.alien.client.render.entity.parasite.facehugger;

import com.alien.client.render.entity.carrier.CarrierSpineBoneCache;
import com.alien.client.render.entity.head.EntityHeadData;
import com.alien.client.render.entity.head.EntityHeadDataCache;
import com.alien.client.render.entity.head.HeadAttachmentClientCache;
import com.alien.client.render.entity.parasite.attachment.ParasiteHeadAttachmentOffsetDataCache;
import com.alien.common.gameplay.entity.living.alien.parasite.facehugger.Facehugger;
import com.alien.common.gameplay.entity.living.alien.xenomorph.carrier.Carrier;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.client.render.v1.AzLayerRenderer;
import com.blib.api.client.render.v1.entity.model.AzEntityModelRenderer;
import com.blib.api.client.render.v1.entity.pipeline.AzEntityRendererPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

public class FacehuggerModelRenderer extends AzEntityModelRenderer<Facehugger> {

    public FacehuggerModelRenderer(
        AzEntityRendererPipeline<Facehugger> entityRendererPipeline,
        AzLayerRenderer<UUID, Facehugger> layerRenderer
    ) {
        super(entityRendererPipeline, layerRenderer);
    }

    @Override
    protected void applyRotations(
        Facehugger facehugger,
        PoseStack poseStack,
        float ageInTicks,
        float rotationYaw,
        float partialTick,
        float nativeScale
    ) {
        if (facehugger.getVehicle() instanceof Carrier carrier) {
            applySpineRidingRotations(facehugger, poseStack, partialTick, carrier);
            return;
        }

        if (!facehugger.getAttachmentManager().isAttachedToHost()) {
            super.applyRotations(facehugger, poseStack, ageInTicks, rotationYaw, partialTick, 1);
            return;
        }

        if (facehugger.isDeadOrDying()) {
            super.applyRotations(facehugger, poseStack, ageInTicks, rotationYaw, partialTick, 1);
            return;
        }

        var host = (LivingEntity) facehugger.getVehicle();

        if (host == null) {
            return;
        }

        var data = HeadAttachmentClientCache.get(host.getType());

        if (data != null) {
            applyHuggingRotations(facehugger, poseStack, partialTick, host, data);
            return;
        }

        // Compatibility tier: sibling mods (avp_human, avp_predator) register their own mobs into the legacy
        // code-driven cache during client setup. Honored second so a head_data JSON can always override.
        var legacyData = EntityHeadDataCache.get(host.getType());

        if (legacyData != null) {
            applyLegacyHuggingRotations(facehugger, poseStack, partialTick, host, legacyData);
            return;
        }

        // No head profile for this host type (typically a third-party mob nobody has written a
        // data/<ns>/head_data JSON for yet): approximate the face from the host's eye height and hitbox so the
        // hugger still latches somewhere sensible instead of floating at the vanilla passenger position.
        applyFallbackHuggingRotations(facehugger, poseStack, partialTick, host);
    }

    private void applySpineRidingRotations(
        Facehugger facehugger,
        PoseStack poseStack,
        float partialTick,
        Carrier carrier
    ) {
        var spineData = CarrierSpineBoneCache.get(carrier.getId());

        if (spineData == null) {
            return;
        }

        var facehuggers = carrier.getPassengers()
            .stream()
            .filter(p -> p.getType().is(AlienEntityTypeTags.FACEHUGGERS))
            .toList();

        var index = facehuggers.indexOf(facehugger);

        if (index < 0 || index >= spineData.modelPositions().length) {
            return;
        }

        var offset = spineData.modelPositions()[index];

        if (offset == null) {
            return;
        }

        var carrierX = Mth.lerp(partialTick, carrier.xOld, carrier.getX());
        var carrierY = Mth.lerp(partialTick, carrier.yOld, carrier.getY());
        var carrierZ = Mth.lerp(partialTick, carrier.zOld, carrier.getZ());

        var entityX = Mth.lerp(partialTick, facehugger.xOld, facehugger.getX());
        var entityY = Mth.lerp(partialTick, facehugger.yOld, facehugger.getY());
        var entityZ = Mth.lerp(partialTick, facehugger.zOld, facehugger.getZ());

        poseStack.translate(
            carrierX + offset.x - entityX,
            carrierY + offset.y - entityY,
            carrierZ + offset.z - entityZ
        );

        var carrierYaw = Mth.rotLerp(partialTick, carrier.yBodyRotO, carrier.yBodyRot);
        poseStack.mulPose(Axis.YN.rotationDegrees(carrierYaw));

        var rotation = spineData.rotations()[index];

        if (rotation != null) {
            poseStack.mulPose(Axis.ZP.rotation(rotation.z));
            poseStack.mulPose(Axis.YP.rotation(rotation.y));
            poseStack.mulPose(Axis.XP.rotation(rotation.x));
        }
    }

    private void applyHuggingRotations(
        Facehugger facehugger,
        PoseStack poseStack,
        float partialTick,
        LivingEntity host,
        HeadAttachmentClientCache.BakedHeadAttachment data
    ) {
        var bodyYaw = Mth.rotLerp(partialTick, host.yBodyRotO, host.yBodyRot);
        var headYaw = Mth.rotLerp(partialTick, host.yHeadRotO, host.yHeadRot) - bodyYaw;
        var headPitch = Mth.rotLerp(partialTick, host.getXRot(), host.xRotO);

        // Per-individual size adaptation: when the profile declares the hitbox height it was tuned against,
        // measure this specific host and scale the whole placement to match (mods like MCA give the same entity
        // type a different size per individual, and keep the hitbox honest via refreshDimensions). Without a
        // declared reference, fall back to the vanilla SCALE attribute, which is 1.0 for ordinary mobs.
        var scale = data.referenceHeight() != null
            ? host.getBbHeight() / data.referenceHeight()
            : (double) host.getScale();
        scale = Mth.clamp(scale, 0.25, 4.0);

        var xPivot = data.pivot().x * scale;
        var yPivot = data.pivot().y * scale;
        var zPivot = data.pivot().z * scale;
        var ySize = data.size().y * scale;
        var zSize = data.size().z * scale;

        poseStack.mulPose(Axis.YN.rotationDegrees(bodyYaw));

        poseStack.translate(xPivot, yPivot - host.getBbHeight(), -zPivot);
        poseStack.mulPose(Axis.YN.rotationDegrees(headYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(headPitch));
        poseStack.translate(-xPivot, -yPivot + host.getBbHeight(), zPivot);

        var yOffset = data.verticalOffset() != null ? data.verticalOffset() * scale : -ySize;
        var zOffset = data.faceOffset() != null ? data.faceOffset() * scale : zSize;

        // Profiles are authored against the standard facehugger; larger or smaller hugger variants push out or pull
        // in by their height difference so one JSON stays correct for every parasite size. Deliberately unscaled —
        // this term is about the parasite, not the host.
        zOffset += facehugger.getBbHeight() - AlienEntityTypes.FACEHUGGER_HEIGHT;

        poseStack.translate(0, yOffset, zOffset);
    }

    /**
     * Pre-datapack placement path, kept verbatim for entries the sibling mods still register into
     * {@link EntityHeadDataCache} in code. Identical math to the original renderer, including the optional per-mob
     * offset suppliers from {@link ParasiteHeadAttachmentOffsetDataCache}.
     */
    private void applyLegacyHuggingRotations(
        Facehugger facehugger,
        PoseStack poseStack,
        float partialTick,
        LivingEntity host,
        EntityHeadData data
    ) {
        var bodyYaw = Mth.rotLerp(partialTick, host.yBodyRotO, host.yBodyRot);
        var headYaw = Mth.rotLerp(partialTick, host.yHeadRotO, host.yHeadRot) - bodyYaw;
        var headPitch = Mth.rotLerp(partialTick, host.getXRot(), host.xRotO);

        var xPivot = data.pivot().x;
        var yPivot = data.pivot().y;
        var zPivot = data.pivot().z;
        var ySize = data.size().y;
        var zSize = data.size().z;

        poseStack.mulPose(Axis.YN.rotationDegrees(bodyYaw));

        poseStack.translate(xPivot, yPivot - host.getBbHeight(), -zPivot);
        poseStack.mulPose(Axis.YN.rotationDegrees(headYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(headPitch));
        poseStack.translate(-xPivot, -yPivot + host.getBbHeight(), zPivot);

        var offsetSuppliers = ParasiteHeadAttachmentOffsetDataCache.get(host.getType());

        if (offsetSuppliers != null) {
            var yOffset = offsetSuppliers.verticalOffsetSupplier().apply(data, facehugger);
            var zOffset = offsetSuppliers.faceOffsetSupplier().apply(data, facehugger);
            poseStack.translate(0, yOffset, zOffset);
        } else {
            poseStack.translate(0, -ySize, zSize);
        }
    }

    private void applyFallbackHuggingRotations(
        Facehugger facehugger,
        PoseStack poseStack,
        float partialTick,
        LivingEntity host
    ) {
        var bodyYaw = Mth.rotLerp(partialTick, host.yBodyRotO, host.yBodyRot);
        var headYaw = Mth.rotLerp(partialTick, host.yHeadRotO, host.yHeadRot) - bodyYaw;
        var headPitch = Mth.rotLerp(partialTick, host.getXRot(), host.xRotO);

        var hostX = Mth.lerp(partialTick, host.xOld, host.getX());
        var hostY = Mth.lerp(partialTick, host.yOld, host.getY());
        var hostZ = Mth.lerp(partialTick, host.zOld, host.getZ());

        var entityX = Mth.lerp(partialTick, facehugger.xOld, facehugger.getX());
        var entityY = Mth.lerp(partialTick, facehugger.yOld, facehugger.getY());
        var entityZ = Mth.lerp(partialTick, facehugger.zOld, facehugger.getZ());

        // Re-anchor the hugger at the host's eye point, rotate with the host's body and head there, then push it
        // forward onto the front of the hitbox — a plausible face latch for any mob shape.
        poseStack.translate(
            hostX - entityX,
            hostY + host.getEyeHeight() - entityY,
            hostZ - entityZ
        );

        poseStack.mulPose(Axis.YN.rotationDegrees(bodyYaw));
        poseStack.mulPose(Axis.YN.rotationDegrees(headYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(headPitch));

        poseStack.translate(0, -facehugger.getBbHeight() / 2.0, host.getBbWidth() / 2.0);
    }
}
