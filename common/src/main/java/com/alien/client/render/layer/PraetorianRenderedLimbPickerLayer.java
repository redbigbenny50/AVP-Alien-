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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Captures the exact cube transforms used by the renderer for firearm-targetable Praetorian limbs.
 * <p>
 * The capture deliberately occurs in {@link #renderForBone}, while BLib's pose stack is still at the final animated
 * transform for that bone. Reading {@code AzBone#getWorldSpaceMatrix()} after the model has rendered loses the final
 * pivot translation and can leave a moving tail one pose behind.
 * </p>
 * <p>
 * Capturing is limited to entities a shot from the local player could actually hit this frame (see
 * {@link #couldBeTargeted}), or every entity while F3+B hitboxes are shown. Everything else is skipped and its old
 * sample discarded, so a crowd of aliens no longer costs one oriented box per cube per frame each.
 * </p>
 */
public final class PraetorianRenderedLimbPickerLayer<T> implements AzRenderLayer<UUID, T> {

    /**
     * How far a firearm ray can reach, in blocks. Entities farther than this are never captured. Set this to the
     * longest firearm range in the mod.
     */
    private static final double MAX_FIREARM_RANGE = 128.0;

    /**
     * Fixed padding (blocks) around the entity's bounding box for limbs that swing outside it, mainly the tail and arms
     * mid-attack.
     */
    private static final double LIMB_REACH_MARGIN = 3.0;

    /**
     * Extra padding per block of distance, covering firearm spread and the look direction moving between this frame and
     * the shot. 0.1 is about a 5.7 degree half-angle; raise it if the widest-spread weapon misses limbs at range.
     */
    private static final double SPREAD_PER_BLOCK = 0.1;

    /** Model-name lookups per entity type, so the hitbox catalog's path matching runs once per type, not per frame. */
    private static final Map<EntityType<?>, ModelInfo> MODEL_BY_TYPE = new IdentityHashMap<>();

    /** Marker for "this entity type has no hitbox model". */
    private static final ModelInfo NO_MODEL = new ModelInfo("", Map.of(), Map.of());

    /** Reused across entities (they render one at a time); published as an immutable copy. */
    private final ArrayList<LimbHitboxVolume> activeCubes = new ArrayList<>(32);

    private final Matrix4f scratchMatrix = new Matrix4f();

    private final Vector3f scratchCenter = new Vector3f();

    private final Vector3f scratchAxis = new Vector3f();

    private int activeEntityId = -1;

    private ModelInfo activeModel;

    private Vec3 activeCameraPosition;

    @Override
    public void preRender(AzRendererPipelineContext<UUID, T> context) {
        reset();

        if (!(context.animatable() instanceof Xenomorph xenomorph)) {
            return;
        }

        var model = modelFor(xenomorph.getType());

        if (model == NO_MODEL) {
            return;
        }

        if (!couldBeTargeted(xenomorph, context.partialTick())) {
            // Out of the line of fire: skip the capture and drop any old sample so a stale pose can't be hit.
            PraetorianRenderedLimbPicker.discard(xenomorph.getId());
            return;
        }

        activeEntityId = xenomorph.getId();
        activeModel = model;
        activeCameraPosition = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    }

    @Override
    public void render(AzRendererPipelineContext<UUID, T> context) {
        if (
            activeModel != null && context.animatable() instanceof Entity entity && activeEntityId == entity.getId()
        ) {
            PraetorianRenderedLimbPicker.publish(entity.getId(), activeCubes);
        }

        reset();
    }

    @Override
    public void renderForBone(AzRendererPipelineContext<UUID, T> context, AzBone bone) {
        if (activeModel == null || bone.isHidden()) {
            return;
        }

        var limbId = activeModel.limbFor(bone);

        if (limbId == null) {
            return;
        }

        // At this point the same pose stack used for the drawn cube includes the complete final animation hierarchy.
        // Convert the renderer's camera-relative coordinates back to world coordinates before publishing the volumes
        // for the firearm raycast.
        var boneMatrix = context.poseStack().last().pose();
        var cubes = bone.getCubes();

        for (int i = 0, size = cubes.size(); i < size; i++) {
            var volume = toRenderedVolume(cubes.get(i), boneMatrix, limbId);

            if (volume != null) {
                activeCubes.add(volume);
            }
        }
    }

    private void reset() {
        activeEntityId = -1;
        activeModel = null;
        activeCameraPosition = null;
        activeCubes.clear();
    }

    /**
     * Whether a shot from the local player could reach {@code entity} this frame: its bounding box, padded for limbs
     * and spread, intersects the player's look ray within firearm range. Always true while F3+B hitboxes are shown, so
     * the diagnostic still draws every alien.
     */
    private static boolean couldBeTargeted(Entity entity, float partialTick) {
        var minecraft = Minecraft.getInstance();

        if (minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes()) {
            return true;
        }

        var player = minecraft.player;

        if (player == null) {
            return false;
        }

        var eye = player.getEyePosition(partialTick);
        var look = player.getViewVector(partialTick);
        var box = entity.getBoundingBox();

        var centerX = (box.minX + box.maxX) * 0.5;
        var centerY = (box.minY + box.maxY) * 0.5;
        var centerZ = (box.minZ + box.maxZ) * 0.5;
        var dx = centerX - eye.x;
        var dy = centerY - eye.y;
        var dz = centerZ - eye.z;
        var distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distance > MAX_FIREARM_RANGE + LIMB_REACH_MARGIN) {
            return false;
        }

        var pad = LIMB_REACH_MARGIN + distance * SPREAD_PER_BLOCK;

        return rayHitsBox(
            eye.x,
            eye.y,
            eye.z,
            look.x,
            look.y,
            look.z,
            MAX_FIREARM_RANGE,
            box.minX - pad,
            box.minY - pad,
            box.minZ - pad,
            box.maxX + pad,
            box.maxY + pad,
            box.maxZ + pad
        );
    }

    /** Slab test for a ray of length {@code range} against an axis-aligned box, without allocating. */
    private static boolean rayHitsBox(
        double ox,
        double oy,
        double oz,
        double dx,
        double dy,
        double dz,
        double range,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ
    ) {
        double tMin = 0;
        double tMax = range;

        for (int axis = 0; axis < 3; axis++) {
            double origin = axis == 0 ? ox : axis == 1 ? oy : oz;
            double direction = axis == 0 ? dx : axis == 1 ? dy : dz;
            double min = axis == 0 ? minX : axis == 1 ? minY : minZ;
            double max = axis == 0 ? maxX : axis == 1 ? maxY : maxZ;

            if (Math.abs(direction) < 1.0E-9) {
                if (origin < min || origin > max) {
                    return false;
                }
                continue;
            }

            double t1 = (min - origin) / direction;
            double t2 = (max - origin) / direction;

            if (t1 > t2) {
                double swap = t1;
                t1 = t2;
                t2 = swap;
            }

            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);

            if (tMin > tMax) {
                return false;
            }
        }

        return true;
    }

    /**
     * The cube's oriented box in world space, or {@code null} for a flat cube.
     * <p>
     * Same result as averaging and projecting every vertex: a cube's vertices are the 8 corners of its local box, so
     * the centre is the box centre and each half-extent is the local extent scaled by the matrix along that axis. Uses
     * the cube's load-time bounds and rotation ({@link GeoCube#bounds()}, {@link GeoCube#transform()}) instead of
     * recomputing them from the vertices.
     * </p>
     */
    private LimbHitboxVolume toRenderedVolume(GeoCube cube, Matrix4f boneMatrix, ResourceLocation limbId) {
        var bounds = cube.bounds();

        if (bounds.isEmpty() || bounds.extentX() <= 0F || bounds.extentY() <= 0F || bounds.extentZ() <= 0F) {
            return null;
        }

        var transform = cube.transform();
        var matrix = transform.identity() ? boneMatrix : scratchMatrix.set(boneMatrix).mul(transform.pose());

        var center = matrix.transformPosition(bounds.centerX(), bounds.centerY(), bounds.centerZ(), scratchCenter);
        var camera = activeCameraPosition;
        var worldCenter = new Vec3(center.x + camera.x, center.y + camera.y, center.z + camera.z);

        var xAxis = matrix.transformDirection(1, 0, 0, scratchAxis);
        var halfX = bounds.extentX() * xAxis.length();
        var xAxisVec = normalized(xAxis);

        var yAxis = matrix.transformDirection(0, 1, 0, scratchAxis);
        var halfY = bounds.extentY() * yAxis.length();
        var yAxisVec = normalized(yAxis);

        var zAxis = matrix.transformDirection(0, 0, 1, scratchAxis);
        var halfZ = bounds.extentZ() * zAxis.length();
        var zAxisVec = normalized(zAxis);

        if (halfX <= 0F || halfY <= 0F || halfZ <= 0F) {
            return null;
        }

        return new LimbHitboxVolume(
            limbId,
            worldCenter,
            xAxisVec,
            yAxisVec,
            zAxisVec,
            new Vec3(halfX, halfY, halfZ),
            1.0F,
            0.0F,
            0.0F
        );
    }

    private static Vec3 normalized(Vector3f axis) {
        var length = axis.length();
        return length > 0F ? new Vec3(axis.x / length, axis.y / length, axis.z / length) : Vec3.ZERO;
    }

    private static ModelInfo modelFor(EntityType<?> type) {
        var cached = MODEL_BY_TYPE.get(type);

        if (cached != null) {
            return cached;
        }

        var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        var info = AdultXenomorphHitboxCatalog.modelForEntityPath(typeId.getPath())
            .map(model -> new ModelInfo(model, roots(model), directRoots(model)))
            .orElse(NO_MODEL);

        MODEL_BY_TYPE.put(type, info);
        return info;
    }

    private static Map<String, ResourceLocation> roots(String model) {
        var roots = new HashMap<String, ResourceLocation>();
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

    /**
     * One hitbox model's limb roots, plus the resolved limb for each bone name. Bone hierarchies are the same for every
     * entity using the model, so the parent walk runs once per bone name instead of once per bone per frame.
     */
    private static final class ModelInfo {

        /** Marker for "this bone belongs to no limb". */
        private static final ResourceLocation NO_LIMB = ResourceLocation.withDefaultNamespace("none");

        private final String model;

        private final Map<String, ResourceLocation> roots;

        private final Map<String, ResourceLocation> directRoots;

        private final Map<String, ResourceLocation> limbByBone = new HashMap<>();

        private ModelInfo(String model, Map<String, ResourceLocation> roots, Map<String, ResourceLocation> directRoots) {
            this.model = model;
            this.roots = roots;
            this.directRoots = directRoots;
        }

        ResourceLocation limbFor(AzBone bone) {
            var cached = limbByBone.get(bone.getName());

            if (cached == null) {
                cached = resolve(bone);
                limbByBone.put(bone.getName(), cached);
            }

            return cached == NO_LIMB ? null : cached;
        }

        private ResourceLocation resolve(AzBone bone) {
            var direct = directRoots.get(bone.getName());

            if (direct != null) {
                return direct;
            }

            for (var current = bone; current != null; current = current.getParent()) {
                var limbId = roots.get(current.getName());

                if (limbId != null) {
                    return limbId;
                }
            }

            return NO_LIMB;
        }
    }
}
