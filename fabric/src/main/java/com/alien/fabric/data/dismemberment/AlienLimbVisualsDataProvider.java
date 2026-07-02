package com.alien.fabric.data.dismemberment;

import com.alien.AlienResources;
import com.blib.api.common.dismemberment.v1.LimbBoneTransform;
import com.blib.api.common.dismemberment.v1.datagen.LimbVisualsDataProvider;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class AlienLimbVisualsDataProvider extends LimbVisualsDataProvider {

    private static final Gson GSON = new Gson();

    private static final Path ANIMATION_DIR = Path.of(
        "common",
        "src",
        "main",
        "resources",
        "assets",
        "avp_alien",
        "animations",
        "entity"
    );

    public AlienLimbVisualsDataProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    protected void generate() {
        for (var group : AlienXenomorphLimbGroups.ALL) {
            xenomorph(group);
        }
    }

    private void xenomorph(AlienXenomorphLimbGroups.Group group) {
        var prefix = group.prefix();
        var headYOffset = group.queen() ? 0.375 : 0.125;
        var headPitch = group.queen() ? -60.0 : 0.0;
        var parent = templateId(prefix);
        var file = template(parent);
        file.visual(
            limb(prefix, "head"),
            "gHead",
            visuals -> {
                visuals.renderOffset(0.0, headYOffset, 0.0);
                if (group.queen()) {
                    visuals.excludedBones("gInhibitor");
                }
                if (headPitch != 0.0) {
                    visuals.renderRotation(headPitch, 0.0, 0.0);
                }
            }
        )
            .visual(
                limb(prefix, "left_arm"),
                "gLeftShoulder",
                visuals -> {
                    visuals.renderOffset(0.0, 0.25, 0.0).renderRotation(-135.0, 0.0, 0.0);
                    if (group.queen()) {
                        visuals.excludedBones("gLeftArmShackle");
                    }
                }
            )
            .visual(
                limb(prefix, "right_arm"),
                "gRightShoulder",
                visuals -> {
                    visuals.renderOffset(0.0, 0.25, 0.0).renderRotation(-135.0, 0.0, 0.0);
                    if (group.queen()) {
                        visuals.excludedBones("gRightArmShackle");
                    }
                }
            )
            .visual(
                limb(prefix, "left_leg"),
                "gLeftLeg",
                visuals -> visuals.renderOffset(0.0, 0.5, 0.0).renderRotation(-90.0, 0.0, 0.0)
            )
            .visual(
                limb(prefix, "right_leg"),
                "gRightLeg",
                visuals -> visuals.renderOffset(0.0, 0.5, 0.0).renderRotation(-90.0, 0.0, 0.0)
            )
            .visual(
                limb(prefix, "tail"),
                "gTail1",
                visuals -> visuals
                    .renderOffset(0.0, tailRenderYOffset(group), 0.0)
                    .boneTransforms(tailIdlePose(prefix))
            );

        for (var entityType : group.entityTypes()) {
            entity(entityType).parent(parent);
        }
    }

    private static ResourceLocation templateId(String prefix) {
        return AlienResources.location(prefix + "_template");
    }

    private static ResourceLocation limb(String prefix, String suffix) {
        return AlienResources.location(prefix + "_" + suffix);
    }

    private static double tailRenderYOffset(AlienXenomorphLimbGroups.Group group) {
        if (group.queen()) {
            return 1.1;
        }

        return switch (group.prefix()) {
            case "burster", "runner", "prowler" -> 0.45;
            case "praetorian", "crusher", "carrier", "ravager", "predalien", "harbinger", "empress" -> 0.85;
            default -> 0.65;
        };
    }

    private static Map<String, LimbBoneTransform> tailIdlePose(String prefix) {
        var animationPath = ANIMATION_DIR.resolve(prefix + ".animation.json");

        if (!Files.exists(animationPath)) {
            return Map.of();
        }

        try (var reader = Files.newBufferedReader(animationPath)) {
            var root = GSON.fromJson(reader, JsonObject.class);
            var animations = root.getAsJsonObject("animations");

            if (animations == null) {
                return Map.of();
            }

            var idle = resolveIdleAnimation(animations);

            if (idle == null) {
                return Map.of();
            }

            var bones = idle.getAsJsonObject("bones");

            if (bones == null) {
                return Map.of();
            }

            var transforms = new LinkedHashMap<String, LimbBoneTransform>();

            for (var entry : bones.entrySet()) {
                var boneName = entry.getKey();

                if (!boneName.startsWith("gTail")) {
                    continue;
                }

                var transform = readBoneTransform(entry.getValue().getAsJsonObject());

                if (transform != null) {
                    transforms.put(boneName, transform);
                }
            }

            return transforms;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read xenomorph idle animation for " + prefix, exception);
        }
    }

    private static JsonObject resolveIdleAnimation(JsonObject animations) {
        if (animations.has("idle")) {
            return animations.getAsJsonObject("idle");
        }

        if (animations.has("animation.idle")) {
            return animations.getAsJsonObject("animation.idle");
        }

        for (var entry : animations.entrySet()) {
            if (entry.getKey().contains("idle")) {
                return entry.getValue().getAsJsonObject();
            }
        }

        return null;
    }

    private static LimbBoneTransform readBoneTransform(JsonObject bone) {
        var position = readFirstVector(bone.get("position"));
        var rotation = readFirstVector(bone.get("rotation"));
        var scale = readFirstVector(bone.get("scale"));

        if (position.isEmpty() && rotation.isEmpty() && scale.isEmpty()) {
            return null;
        }

        return new LimbBoneTransform(position, rotation, scale);
    }

    private static Optional<Vec3> readFirstVector(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return Optional.empty();
        }

        if (element.isJsonPrimitive()) {
            var value = element.getAsDouble();
            return Optional.of(new Vec3(value, value, value));
        }

        if (element.isJsonArray()) {
            return Optional.of(readVector(element.getAsJsonArray()));
        }

        var object = element.getAsJsonObject();

        if (object.has("vector")) {
            return Optional.of(readVector(object.getAsJsonArray("vector")));
        }

        for (var entry : object.entrySet()) {
            var key = entry.getKey();

            if (key.equals("easing") || key.equals("easingArgs") || key.equals("lerp_mode")) {
                continue;
            }

            var value = entry.getValue();

            if (value.isJsonArray()) {
                return Optional.of(readVector(value.getAsJsonArray()));
            }

            var keyframe = value.getAsJsonObject();

            if (keyframe.has("vector")) {
                return Optional.of(readVector(keyframe.getAsJsonArray("vector")));
            }

            if (keyframe.has("pre")) {
                return Optional.of(readNestedVector(keyframe.get("pre")));
            }

            if (keyframe.has("post")) {
                return Optional.of(readNestedVector(keyframe.get("post")));
            }
        }

        return Optional.empty();
    }

    private static Vec3 readNestedVector(JsonElement element) {
        if (element.isJsonArray()) {
            return readVector(element.getAsJsonArray());
        }

        return readVector(element.getAsJsonObject().getAsJsonArray("vector"));
    }

    private static Vec3 readVector(JsonArray vector) {
        return new Vec3(readStaticComponent(vector.get(0)), readStaticComponent(vector.get(1)), readStaticComponent(vector.get(2)));
    }

    private static double readStaticComponent(JsonElement element) {
        try {
            return element.getAsDouble();
        } catch (NumberFormatException exception) {
            return 0.0;
        }
    }
}
