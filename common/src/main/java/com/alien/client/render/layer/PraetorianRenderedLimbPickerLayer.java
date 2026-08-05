package com.alien.client.render.layer;

import com.alien.client.render.dismemberment.PraetorianRenderedLimbPicker;
import com.alien.common.gameplay.entity.dismemberment.AdultXenomorphHitboxCatalog;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.blib.api.client.model.v1.AzBone;
import com.blib.api.client.render.v1.AzRendererPipelineContext;
import com.blib.api.client.render.v1.layer.AzRenderLayer;
import com.blib.api.common.dismemberment.v1.hitbox.LimbHitboxVolume;
import com.blib.internal.client.model.GeoCube;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/**
 * Captures the exact cube transforms used by the renderer for firearm-targetable Praetorian limbs.
 * <p>
 * The capture deliberately occurs in {@link #renderForBone}, while BLib's pose stack is still at the final animated
 * transform for that bone. Reading {@code AzBone#getWorldSpaceMatrix()} after the model has rendered loses the final
 * pivot translation and can leave a moving tail one pose behind.
 */
public final class PraetorianRenderedLimbPickerLayer<T> implements AzRenderLayer<UUID, T> {

    private int activeEntityId = -1;

    private ArrayList<LimbHitboxVolume> activeCubes;

    private Map<String, ResourceLocation> activeRoots;

    private Map<String, ResourceLocation> activeDirectRoots;

    @Override
    public void preRender(AzRendererPipelineContext<UUID, T> context) {
        if (context.animatable() instanceof Xenomorph xenomorph) {
            var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(xenomorph.getType());
            var model = AdultXenomorphHitboxCatalog.modelForEntityPath(typeId.getPath());
            if (model.isEmpty()) {
                activeEntityId = -1;
                activeCubes = null;
                activeRoots = null;
                return;
            }
            activeEntityId = xenomorph.getId();
            activeCubes = new ArrayList<>();
            activeRoots = roots(model.get());
            activeDirectRoots = directRoots(model.get());
        } else {
            activeEntityId = -1;
            activeCubes = null;
            activeRoots = null;
            activeDirectRoots = null;
        }
    }

    @Override
    public void render(AzRendererPipelineContext<UUID, T> context) {
        if (
            !(context.animatable() instanceof net.minecraft.world.entity.LivingEntity living) || activeCubes == null
                || activeEntityId != living.getId()
        ) {
            return;
        }
        PraetorianRenderedLimbPicker.publish(living.getId(), activeCubes);
        activeEntityId = -1;
        activeCubes = null;
        activeRoots = null;
        activeDirectRoots = null;
    }

    @Override
    public void renderForBone(AzRendererPipelineContext<UUID, T> context, AzBone bone) {
        if (activeCubes == null || activeRoots == null || activeEntityId < 0 || bone.isHidden()) {
            return;
        }
        var limbId = activeDirectRoots.get(bone.getName());
        if (limbId == null) {
            limbId = limbFor(bone, activeRoots);
        }
        if (limbId == null) {
            return;
        }

        // At this point the same pose stack used for the drawn cube includes the complete final
        // animation hierarchy. Convert the renderer's camera-relative coordinates back to world
        // coordinates before publishing the volumes for the firearm raycast.
        var cameraPosition = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        var boneMatrix = new Matrix4f(context.poseStack().last().pose());
        for (var cube : bone.getCubes()) {
            toRenderedVolume(cube, boneMatrix, cameraPosition, limbId).ifPresent(activeCubes::add);
        }
    }

    private static ResourceLocation limbFor(AzBone bone, Map<String, ResourceLocation> roots) {
        for (var current = bone; current != null; current = current.getParent()) {
            var limbId = roots.get(current.getName());
            if (limbId != null) {
                return limbId;
            }
        }
        return null;
    }

    private static Map<String, ResourceLocation> roots(String model) {
        var roots = new java.util.HashMap<String, ResourceLocation>();
        roots.put("gHead", AdultXenomorphHitboxCatalog.limbId(model, "head"));
        if (AdultXenomorphHitboxCatalog.isHeadOnlyModel(model)) {
            return Map.copyOf(roots);
        }
        roots.put("gLeftShoulder", AdultXenomorphHitboxCatalog.limbId(model, "left_arm"));
        roots.put("gRightShoulder", AdultXenomorphHitboxCatalog.limbId(model, "right_arm"));
        roots.put("gLeftLeg", AdultXenomorphHitboxCatalog.limbId(model, "left_leg"));
        roots.put("gRightLeg", AdultXenomorphHitboxCatalog.limbId(model, "right_leg"));
        roots.put("gTail1", AdultXenomorphHitboxCatalog.limbId(model, "tail"));
        if (model.equals("harbinger")) {
            roots.put("gLeftWhip", AdultXenomorphHitboxCatalog.limbId(model, "left_back_whip"));
            roots.put("gRightWhip", AdultXenomorphHitboxCatalog.limbId(model, "right_back_whip"));
        }
        return Map.copyOf(roots);
    }

    private static Map<String, ResourceLocation> directRoots(String model) {
        if (!model.equals("queen") && !model.equals("empress") && !model.equals("harbinger")) {
            return Map.of();
        }
        return Map.of("gUpperBody", AdultXenomorphHitboxCatalog.limbId(model, "torso"));
    }

    private static java.util.Optional<LimbHitboxVolume> toRenderedVolume(
        GeoCube cube,
        Matrix4f boneMatrix,
        Vec3 cameraPosition,
        ResourceLocation limbId
    ) {
        var matrix = new Matrix4f(boneMatrix);
        var pivot = cube.pivot();
        matrix
            .translate((float) pivot.x() / 16.0F, (float) pivot.y() / 16.0F, (float) pivot.z() / 16.0F)
            .rotateZ((float) cube.rotation().z())
            .rotateY((float) cube.rotation().y())
            .rotateX((float) cube.rotation().x())
            .translate((float) -pivot.x() / 16.0F, (float) -pivot.y() / 16.0F, (float) -pivot.z() / 16.0F);

        var vertices = new ArrayList<Vector3f>();
        for (var quad : cube.quads()) {
            if (quad != null) {
                for (var vertex : quad.vertices()) {
                    vertices.add(matrix.transformPosition(new Vector3f(vertex.position())));
                }
            }
        }
        if (vertices.isEmpty()) {
            return java.util.Optional.empty();
        }

        var center = new Vector3f();
        for (var vertex : vertices) {
            center.add(vertex);
        }
        center.div(vertices.size());
        var xAxis = matrix.transformDirection(new Vector3f(1, 0, 0)).normalize();
        var yAxis = matrix.transformDirection(new Vector3f(0, 1, 0)).normalize();
        var zAxis = matrix.transformDirection(new Vector3f(0, 0, 1)).normalize();
        var halfExtents = new Vector3f();
        for (var vertex : vertices) {
            var offset = new Vector3f(vertex).sub(center);
            halfExtents.x = Math.max(halfExtents.x, Math.abs(offset.dot(xAxis)));
            halfExtents.y = Math.max(halfExtents.y, Math.abs(offset.dot(yAxis)));
            halfExtents.z = Math.max(halfExtents.z, Math.abs(offset.dot(zAxis)));
        }
        if (halfExtents.x <= 0.0F || halfExtents.y <= 0.0F || halfExtents.z <= 0.0F) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(
            new LimbHitboxVolume(
                limbId,
                new Vec3(center.x + cameraPosition.x, center.y + cameraPosition.y, center.z + cameraPosition.z),
                new Vec3(xAxis.x, xAxis.y, xAxis.z),
                new Vec3(yAxis.x, yAxis.y, yAxis.z),
                new Vec3(zAxis.x, zAxis.y, zAxis.z),
                new Vec3(halfExtents.x, halfExtents.y, halfExtents.z),
                1.0F,
                0.0F,
                0.0F
            )
        );
    }
}
