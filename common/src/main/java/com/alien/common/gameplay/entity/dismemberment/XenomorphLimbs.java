package com.alien.common.gameplay.entity.dismemberment;

import com.alien.AlienResources;
import com.blib.api.common.dismemberment.v1.LimbCollisionAdapter;
import com.blib.api.common.dismemberment.v1.LimbCollisionAdapterRegistry;
import com.blib.api.common.dismemberment.v1.LimbCollisionBox;
import com.blib.api.common.dismemberment.v1.SpawnFunctionRegistry;
import com.blib.api.common.registry.v1.BLibHolder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Registers xenomorph limb collision from the actual cubes in each Geo model. */
public final class XenomorphLimbs {

    private static final Map<String, GeoModel> MODELS = new ConcurrentHashMap<>();

    private XenomorphLimbs() {}

    public static void registerSpawnOffsets(String idPrefix) {
        SpawnFunctionRegistry.register(AlienResources.location(idPrefix + "_head"), entity -> new Vec3(0.0, entity.getEyeHeight(), 0.0));
        SpawnFunctionRegistry.register(
            AlienResources.location(idPrefix + "_left_arm"),
            entity -> new Vec3(0.0, entity.getBbHeight() * 0.75, 0.0)
        );
        SpawnFunctionRegistry.register(
            AlienResources.location(idPrefix + "_right_arm"),
            entity -> new Vec3(0.0, entity.getBbHeight() * 0.75, 0.0)
        );
        SpawnFunctionRegistry.register(
            AlienResources.location(idPrefix + "_left_leg"),
            entity -> new Vec3(0.0, entity.getBbHeight() * 0.3, 0.0)
        );
        SpawnFunctionRegistry.register(
            AlienResources.location(idPrefix + "_right_leg"),
            entity -> new Vec3(0.0, entity.getBbHeight() * 0.3, 0.0)
        );
    }

    /**
     * Uses the model's authored cubes and bone hierarchy. The six roots deliberately match the model bone names used by
     * the adult xenomorph Geo files; all descendants belong to the same detachable limb.
     */
    @SafeVarargs
    public static void registerGunModelCollision(
        String idPrefix,
        String geoModel,
        BLibHolder<? extends EntityType<?>>... entityTypes
    ) {
        var model = MODELS.computeIfAbsent(geoModel, XenomorphLimbs::loadModel);
        for (var entityType : entityTypes) {
            LimbCollisionAdapterRegistry.register(entityType.getResourceLocation(), entity -> model.parts(entity, idPrefix));
        }
    }

    private static GeoModel loadModel(String modelName) {
        var resource = "/assets/avp_alien/geo/entity/" + modelName + ".geo.json";
        try (var input = XenomorphLimbs.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing xenomorph Geo model " + resource);
            }
            var root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            var bones = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
            var result = new HashMap<String, Bone>();
            for (var entry : bones) {
                var json = entry.getAsJsonObject();
                var bone = new Bone(
                    json.get("name").getAsString(),
                    stringOrNull(json, "parent"),
                    vector(json.getAsJsonArray("pivot"), true),
                    degreesOrZero(json, "rotation"),
                    cubes(json)
                );
                result.put(bone.name, bone);
            }
            for (var bone : result.values()) {
                if (bone.parent != null && result.containsKey(bone.parent)) {
                    result.get(bone.parent).children.add(bone);
                }
            }
            return new GeoModel(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read xenomorph Geo model " + modelName, exception);
        }
    }

    private static List<Cube> cubes(JsonObject bone) {
        var result = new ArrayList<Cube>();
        if (!bone.has("cubes"))
            return result;
        for (var entry : bone.getAsJsonArray("cubes")) {
            var json = entry.getAsJsonObject();
            var origin = vector(json.getAsJsonArray("origin"), false);
            var size = vector(json.getAsJsonArray("size"), false);
            var inflate = json.has("inflate") ? json.get("inflate").getAsFloat() : 0.0F;
            result.add(
                new Cube(
                    origin,
                    size,
                    json.has("pivot") ? vector(json.getAsJsonArray("pivot"), true) : new Vector3f(),
                    degreesOrZero(json, "rotation"),
                    inflate
                )
            );
        }
        return result;
    }

    private static String stringOrNull(JsonObject object, String property) {
        return object.has(property) ? object.get(property).getAsString() : null;
    }

    private static Vector3f degreesOrZero(JsonObject object, String property) {
        if (!object.has(property))
            return new Vector3f();
        var array = object.getAsJsonArray(property);
        return new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
    }

    private static Vector3f vector(JsonArray array, boolean convertModelAxes) {
        var x = array.get(0).getAsFloat();
        var y = array.get(1).getAsFloat();
        var z = array.get(2).getAsFloat();
        return convertModelAxes ? new Vector3f(-x / 16.0F, y / 16.0F, z / 16.0F) : new Vector3f(x, y, z);
    }

    private record Cube(
        Vector3f origin,
        Vector3f size,
        Vector3f pivot,
        Vector3f rotation,
        float inflate
    ) {}

    private static final class Bone {

        private final String name;

        private final String parent;

        private final Vector3f pivot;

        private final Vector3f rotation;

        private final List<Cube> cubes;

        private final List<Bone> children = new ArrayList<>();

        private Bone(String name, String parent, Vector3f pivot, Vector3f rotation, List<Cube> cubes) {
            this.name = name;
            this.parent = parent;
            this.pivot = pivot;
            this.rotation = rotation;
            this.cubes = cubes;
        }
    }

    private static final class GeoModel {

        private static final Map<String, String> LIMB_ROOTS = Map.of(
            "head",
            "gHead",
            "left_arm",
            "gLeftShoulder",
            "right_arm",
            "gRightShoulder",
            "left_leg",
            "gLeftLeg",
            "right_leg",
            "gRightLeg",
            "tail",
            "gTail1"
        );

        private final Map<String, Bone> bones;

        private GeoModel(Map<String, Bone> bones) {
            this.bones = bones;
        }

        private List<LimbCollisionAdapter.Part> parts(LivingEntity entity, String prefix) {
            var result = new ArrayList<LimbCollisionAdapter.Part>();
            for (var limb : LIMB_ROOTS.entrySet()) {
                var root = bones.get(limb.getValue());
                if (root != null)
                    collect(root, new Matrix4f(), entity, AlienResources.location(prefix + "_" + limb.getKey()), result);
            }
            return result;
        }

        private void collect(
            Bone bone,
            Matrix4f parent,
            LivingEntity entity,
            net.minecraft.resources.ResourceLocation limbId,
            List<LimbCollisionAdapter.Part> result
        ) {
            var transform = new Matrix4f(parent)
                .translate(bone.pivot)
                .rotateZ((float) Math.toRadians(bone.rotation.z))
                .rotateY((float) Math.toRadians(-bone.rotation.y))
                .rotateX((float) Math.toRadians(-bone.rotation.x))
                .translate(-bone.pivot.x, -bone.pivot.y, -bone.pivot.z);
            for (var cube : bone.cubes)
                result.add(new LimbCollisionAdapter.Part(limbId, toWorldBox(entity, transform, cube)));
            for (var child : bone.children)
                collect(child, transform, entity, limbId, result);
        }

        private LimbCollisionBox toWorldBox(LivingEntity entity, Matrix4f boneTransform, Cube cube) {
            var transform = new Matrix4f(boneTransform)
                .translate(cube.pivot)
                .rotateZ((float) Math.toRadians(cube.rotation.z))
                .rotateY((float) Math.toRadians(-cube.rotation.y))
                .rotateX((float) Math.toRadians(-cube.rotation.x))
                .translate(-cube.pivot.x, -cube.pivot.y, -cube.pivot.z);
            var inflate = cube.inflate / 16.0F;
            var min = new Vector3f(
                -(cube.origin.x + cube.size.x) / 16.0F - inflate,
                cube.origin.y / 16.0F - inflate,
                cube.origin.z / 16.0F - inflate
            );
            var size = new Vector3f(
                cube.size.x / 16.0F + inflate * 2.0F,
                cube.size.y / 16.0F + inflate * 2.0F,
                cube.size.z / 16.0F + inflate * 2.0F
            );
            var center = transform.transformPosition(new Vector3f(min).add(new Vector3f(size).mul(0.5F)));
            var xAxis = transform.transformDirection(new Vector3f(1, 0, 0)).normalize();
            var yAxis = transform.transformDirection(new Vector3f(0, 1, 0)).normalize();
            var zAxis = transform.transformDirection(new Vector3f(0, 0, 1)).normalize();
            var yaw = Math.toRadians(entity.getYRot());
            var worldX = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
            var worldY = new Vec3(0.0, 1.0, 0.0);
            var worldZ = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
            var scale = entity.getScale();
            return new LimbCollisionBox(
                entity.position()
                    .add(worldX.scale(center.x * scale))
                    .add(worldY.scale(center.y * scale))
                    .add(worldZ.scale(center.z * scale)),
                mapAxis(xAxis, worldX, worldY, worldZ),
                mapAxis(yAxis, worldX, worldY, worldZ),
                mapAxis(zAxis, worldX, worldY, worldZ),
                new Vec3(size.x * scale * 0.5, size.y * scale * 0.5, size.z * scale * 0.5)
            );
        }

        private Vec3 mapAxis(Vector3f axis, Vec3 worldX, Vec3 worldY, Vec3 worldZ) {
            return worldX.scale(axis.x).add(worldY.scale(axis.y)).add(worldZ.scale(axis.z)).normalize();
        }
    }
}
