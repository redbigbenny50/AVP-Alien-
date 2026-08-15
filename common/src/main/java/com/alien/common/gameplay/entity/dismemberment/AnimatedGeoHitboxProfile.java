package com.alien.common.gameplay.entity.dismemberment;

import com.blib.api.common.dismemberment.v1.DismembermentManager;
import com.blib.api.common.dismemberment.v1.hitbox.LimbHitboxVolume;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Server-side evaluator for Bedrock geo models and their animation files.
 * <p>
 * The renderer remains responsible for drawing the model, but firearm targeting must not read renderer state: a
 * dedicated server has no renderer, and a client pose can be late or spoofed. This profile evaluates the authored bone
 * hierarchy and cubes from the same asset files, producing additive oriented hurtboxes in world space.
 * </p>
 */
final class AnimatedGeoHitboxProfile {

    private final Map<String, Bone> bones;

    private final Map<String, Animation> animations;

    private final Map<String, LimbRule> limbRoots;

    private AnimatedGeoHitboxProfile(Map<String, Bone> bones, Map<String, Animation> animations, Map<String, LimbRule> limbRoots) {
        this.bones = bones;
        this.animations = animations;
        this.limbRoots = limbRoots;
    }

    static AnimatedGeoHitboxProfile load(String modelName, Map<String, LimbRule> limbRoots) {
        Objects.requireNonNull(modelName, "modelName");
        try (
            var geometryInput = AnimatedGeoHitboxProfile.class.getResourceAsStream(
                "/assets/avp_alien/geo/entity/" + modelName + ".geo.json"
            );
            var animationInput = AnimatedGeoHitboxProfile.class.getResourceAsStream(
                "/assets/avp_alien/animations/entity/" + modelName + ".animation.json"
            )
        ) {
            if (geometryInput == null || animationInput == null) {
                throw new IllegalStateException("Missing hitbox assets for " + modelName);
            }
            var geometry = JsonParser.parseReader(new InputStreamReader(geometryInput, StandardCharsets.UTF_8)).getAsJsonObject();
            var animations = JsonParser.parseReader(new InputStreamReader(animationInput, StandardCharsets.UTF_8)).getAsJsonObject();
            return new AnimatedGeoHitboxProfile(readBones(geometry), readAnimations(animations), Map.copyOf(limbRoots));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load animated hitbox profile " + modelName, exception);
        }
    }

    List<LimbHitboxVolume> volumes(LivingEntity entity, String animationName, double animationTimeSeconds) {
        var animation = animations.get(animationName);
        var matrices = new HashMap<String, Matrix4f>();
        for (var bone : bones.values()) {
            matrixFor(bone, animation, animationTimeSeconds, matrices);
        }

        var result = new ArrayList<LimbHitboxVolume>();
        for (var entry : limbRoots.entrySet()) {
            var root = bones.get(entry.getKey());
            if (root != null) {
                addVolumes(root, entry.getValue(), entity, matrices, result);
            }
        }
        return result;
    }

    double animationLengthSeconds(String animationName) {
        var animation = animations.get(animationName);
        return animation == null ? 0.0D : animation.lengthSeconds();
    }

    /** Returns the first authored animation available in this model, falling back to its neutral pose. */
    String firstAnimation(String... candidates) {
        for (var candidate : candidates) {
            if (animations.containsKey(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private Matrix4f matrixFor(Bone bone, Animation animation, double animationTimeSeconds, Map<String, Matrix4f> matrices) {
        var cached = matrices.get(bone.name());
        if (cached != null) {
            return cached;
        }
        var parent = bone.parent() == null
            ? new Matrix4f()
            : matrixFor(bones.get(bone.parent()), animation, animationTimeSeconds, matrices);
        var transform = new Matrix4f(parent);
        var pose = animation == null ? BonePose.DEFAULT : animation.poseFor(bone.name(), animationTimeSeconds);
        var pivot = modelVector(bone.pivot());
        var position = modelVector(pose.position());
        var rotation = bone.rotation().add(pose.rotation());

        transform
            .translate(position)
            .translate(pivot)
            .rotateZ((float) Math.toRadians(rotation.z()))
            .rotateY((float) Math.toRadians(-rotation.y()))
            .rotateX((float) Math.toRadians(-rotation.x()))
            .scale((float) pose.scale().x, (float) pose.scale().y, (float) pose.scale().z)
            .translate(-pivot.x, -pivot.y, -pivot.z);
        matrices.put(bone.name(), transform);
        return transform;
    }

    private void addVolumes(
        Bone bone,
        LimbRule rule,
        LivingEntity entity,
        Map<String, Matrix4f> matrices,
        List<LimbHitboxVolume> output
    ) {
        // A detached limb must disappear from the authoritative firearm target set immediately. This also skips the
        // rest of the bone subtree, which is especially important for the Praetorian's multi-segment tail.
        if (DismembermentManager.isDetached(entity, rule.limbId())) {
            return;
        }
        var boneTransform = matrices.get(bone.name());
        for (var cube : bone.cubes()) {
            // Bedrock geometry permits flat visual planes (one dimension is zero). They have no physical volume and
            // therefore cannot be a firearm hurtbox.
            if (cube.size().x <= 0.0D || cube.size().y <= 0.0D || cube.size().z <= 0.0D) {
                continue;
            }
            var transform = new Matrix4f(boneTransform);
            var pivot = modelVector(cube.pivot());
            transform
                .translate(pivot)
                .rotateZ((float) Math.toRadians(cube.rotation().z()))
                .rotateY((float) Math.toRadians(-cube.rotation().y()))
                .rotateX((float) Math.toRadians(-cube.rotation().x()))
                .translate(-pivot.x, -pivot.y, -pivot.z);
            var volume = toWorldVolume(entity, transform, cube, rule);
            if (volume != null) {
                output.add(volume);
            }
        }
        if (rule.includeDescendants()) {
            for (var childName : bone.children()) {
                addVolumes(bones.get(childName), rule, entity, matrices, output);
            }
        }
    }

    private LimbHitboxVolume toWorldVolume(LivingEntity entity, Matrix4f transform, Cube cube, LimbRule rule) {
        var inflate = cube.inflate() / 16.0F;
        var origin = cubeOrigin(cube.origin(), cube.size());
        var size = new Vector3f(
            (float) cube.size().x / 16.0F + inflate * 2.0F,
            (float) cube.size().y / 16.0F + inflate * 2.0F,
            (float) cube.size().z / 16.0F + inflate * 2.0F
        );
        if (size.x <= 0.0F || size.y <= 0.0F || size.z <= 0.0F) {
            return null;
        }
        origin.sub(inflate, inflate, inflate);
        var localCenter = transform.transformPosition(new Vector3f(origin).add(new Vector3f(size).mul(0.5F)));
        var localX = transform.transformDirection(new Vector3f(1, 0, 0)).normalize();
        var localY = transform.transformDirection(new Vector3f(0, 1, 0)).normalize();
        var localZ = transform.transformDirection(new Vector3f(0, 0, 1)).normalize();
        // Match AzEntityModelRenderer.applyRotations: it turns the model by 180 - body yaw. In particular, the
        // authored local Z axis maps to (sin(yaw), 0, -cos(yaw)); reversing it mirrors a tail to the mob's front.
        var yaw = Math.toRadians(entity.yBodyRot);
        var worldX = new Vec3(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
        var worldY = new Vec3(0.0D, 1.0D, 0.0D);
        var worldZ = new Vec3(Math.sin(yaw), 0.0D, -Math.cos(yaw));
        var scale = entity.getScale();

        return new LimbHitboxVolume(
            rule.limbId(),
            entity.position()
                .add(worldX.scale(localCenter.x * scale))
                .add(worldY.scale(localCenter.y * scale))
                .add(worldZ.scale(localCenter.z * scale)),
            mapAxis(localX, worldX, worldY, worldZ),
            mapAxis(localY, worldX, worldY, worldZ),
            mapAxis(localZ, worldX, worldY, worldZ),
            new Vec3(size.x * scale * 0.5D, size.y * scale * 0.5D, size.z * scale * 0.5D).add(
                rule.padding() * scale,
                rule.padding() * scale,
                rule.padding() * scale
            ),
            rule.healthDamageMultiplier(),
            rule.limbDamageMultiplier(),
            rule.limbDamageThreshold()
        );
    }

    private static Vec3 mapAxis(Vector3f axis, Vec3 worldX, Vec3 worldY, Vec3 worldZ) {
        return worldX.scale(axis.x).add(worldY.scale(axis.y)).add(worldZ.scale(axis.z)).normalize();
    }

    private static Vector3f modelVector(Vec3 vector) {
        return new Vector3f((float) -vector.x / 16.0F, (float) vector.y / 16.0F, (float) vector.z / 16.0F);
    }

    private static Vector3f cubeOrigin(Vec3 origin, Vec3 size) {
        return new Vector3f(
            (float) -(origin.x + size.x) / 16.0F,
            (float) origin.y / 16.0F,
            (float) origin.z / 16.0F
        );
    }

    private static Map<String, Bone> readBones(JsonObject root) {
        var result = new HashMap<String, Bone>();
        var entries = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
        for (var entry : entries) {
            var object = entry.getAsJsonObject();
            var name = object.get("name").getAsString();
            result.put(
                name,
                new Bone(
                    name,
                    stringOrNull(object, "parent"),
                    vector(object.get("pivot")),
                    vectorOrZero(object.get("rotation")),
                    readCubes(object),
                    new ArrayList<>()
                )
            );
        }
        for (var bone : result.values()) {
            if (bone.parent() != null && result.containsKey(bone.parent())) {
                result.get(bone.parent()).children().add(bone.name());
            }
        }
        return Map.copyOf(result);
    }

    private static List<Cube> readCubes(JsonObject bone) {
        if (!bone.has("cubes")) {
            return List.of();
        }
        var result = new ArrayList<Cube>();
        for (var entry : bone.getAsJsonArray("cubes")) {
            var object = entry.getAsJsonObject();
            result.add(
                new Cube(
                    vector(object.get("origin")),
                    vector(object.get("size")),
                    object.has("pivot") ? vector(object.get("pivot")) : Vec3.ZERO,
                    vectorOrZero(object.get("rotation")),
                    object.has("inflate") ? object.get("inflate").getAsFloat() : 0.0F
                )
            );
        }
        return List.copyOf(result);
    }

    private static Map<String, Animation> readAnimations(JsonObject root) {
        var result = new HashMap<String, Animation>();
        for (var entry : root.getAsJsonObject("animations").entrySet()) {
            var animation = entry.getValue().getAsJsonObject();
            var length = animation.has("animation_length") ? animation.get("animation_length").getAsDouble() : 0.0D;
            var loop = animation.has("loop") && animation.get("loop").getAsBoolean();
            var poses = new HashMap<String, AnimatedBone>();
            var bones = animation.has("bones") ? animation.getAsJsonObject("bones") : new JsonObject();
            for (var boneEntry : bones.entrySet()) {
                var bone = boneEntry.getValue().getAsJsonObject();
                poses.put(
                    new String(boneEntry.getKey()),
                    new AnimatedBone(
                        Channel.read(bone.get("rotation")),
                        Channel.read(bone.get("position")),
                        Channel.read(bone.get("scale"))
                    )
                );
            }
            result.put(entry.getKey(), new Animation(length, loop, Map.copyOf(poses)));
        }
        return Map.copyOf(result);
    }

    private static String stringOrNull(JsonObject object, String name) {
        return object.has(name) ? object.get(name).getAsString() : null;
    }

    private static Vec3 vectorOrZero(JsonElement value) {
        return value == null ? Vec3.ZERO : vector(value);
    }

    private static Vec3 vector(JsonElement value) {
        var array = value.getAsJsonArray();
        return new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
    }

    record LimbRule(
        ResourceLocation limbId,
        float healthDamageMultiplier,
        float limbDamageMultiplier,
        float limbDamageThreshold,
        double padding,
        boolean includeDescendants
    ) {

        LimbRule(
            ResourceLocation limbId,
            float healthDamageMultiplier,
            float limbDamageMultiplier,
            float limbDamageThreshold,
            double padding
        ) {
            this(limbId, healthDamageMultiplier, limbDamageMultiplier, limbDamageThreshold, padding, true);
        }
    }

    private record Bone(
        String name,
        String parent,
        Vec3 pivot,
        Vec3 rotation,
        List<Cube> cubes,
        List<String> children
    ) {}

    private record Cube(
        Vec3 origin,
        Vec3 size,
        Vec3 pivot,
        Vec3 rotation,
        float inflate
    ) {}

    private record BonePose(
        Vec3 rotation,
        Vec3 position,
        Vec3 scale
    ) {

        private static final BonePose DEFAULT = new BonePose(Vec3.ZERO, Vec3.ZERO, new Vec3(1.0D, 1.0D, 1.0D));
    }

    private record Animation(
        double lengthSeconds,
        boolean loop,
        Map<String, AnimatedBone> bones
    ) {

        private BonePose poseFor(String boneName, double timeSeconds) {
            var bone = bones.get(boneName);
            if (bone == null) {
                return BonePose.DEFAULT;
            }
            var time = loop && lengthSeconds > 0.0D
                ? timeSeconds % lengthSeconds
                : Mth.clamp(timeSeconds, 0.0D, lengthSeconds);
            return new BonePose(
                bone.rotation().at(time, Vec3.ZERO),
                bone.position().at(time, Vec3.ZERO),
                bone.scale().at(time, new Vec3(1, 1, 1))
            );
        }
    }

    private record AnimatedBone(
        Channel rotation,
        Channel position,
        Channel scale
    ) {}

    private record Channel(List<Keyframe> frames) {

        private static final Channel EMPTY = new Channel(List.of());

        private static Channel read(JsonElement value) {
            if (value == null) {
                return EMPTY;
            }
            var frames = new ArrayList<Keyframe>();
            if (value.isJsonArray() || value.isJsonPrimitive()) {
                frames.add(new Keyframe(0.0D, readValue(value)));
            } else {
                var object = value.getAsJsonObject();
                if (object.has("vector")) {
                    frames.add(new Keyframe(0.0D, readValue(object.get("vector"))));
                } else {
                    for (var entry : object.entrySet()) {
                        try {
                            var key = Double.parseDouble(entry.getKey());
                            var frame = entry.getValue();
                            if (frame.isJsonObject()) {
                                var frameObject = frame.getAsJsonObject();
                                frame = frameObject.has("post") ? frameObject.get("post") : frameObject.get("pre");
                                if (frame != null && frame.isJsonObject()) {
                                    frame = frame.getAsJsonObject().get("vector");
                                }
                            }
                            if (frame != null) {
                                frames.add(new Keyframe(key, readValue(frame)));
                            }
                        } catch (NumberFormatException ignored) {
                            // Metadata such as easing belongs to the keyframe, not this channel's timeline.
                        }
                    }
                }
            }
            frames.sort(Comparator.comparingDouble(Keyframe::time));
            return frames.isEmpty() ? EMPTY : new Channel(List.copyOf(frames));
        }

        private static AnimatedVector readValue(JsonElement value) {
            if (value.isJsonPrimitive()) {
                var expression = value.getAsString();
                return new AnimatedVector(expression, expression, expression);
            }
            var array = value.getAsJsonArray();
            return new AnimatedVector(array.get(0).getAsString(), array.get(1).getAsString(), array.get(2).getAsString());
        }

        private Vec3 at(double time, Vec3 fallback) {
            if (frames.isEmpty()) {
                return fallback;
            }
            var before = frames.getFirst();
            var after = frames.getLast();
            for (var frame : frames) {
                if (frame.time() >= time) {
                    after = frame;
                    break;
                }
                before = frame;
            }
            if (before == after || after.time() <= before.time()) {
                return before.value().evaluate(time);
            }
            var fraction = (time - before.time()) / (after.time() - before.time());
            return before.value().evaluate(time).lerp(after.value().evaluate(time), fraction);
        }

    }

    private record Keyframe(
        double time,
        AnimatedVector value
    ) {}

    private record AnimatedVector(
        String x,
        String y,
        String z
    ) {

        private Vec3 evaluate(double animationTime) {
            return new Vec3(
                MathExpression.evaluate(x, animationTime),
                MathExpression.evaluate(y, animationTime),
                MathExpression.evaluate(z, animationTime)
            );
        }

    }

    /** Minimal MoLang numeric subset used by the Praetorian assets: arithmetic, parentheses, sin/cos and anim_time. */
    private static final class MathExpression {

        private final String input;

        private final double animationTime;

        private int cursor;

        private MathExpression(String input, double animationTime) {
            this.input = input.replace("Math.", "")
                .replace("math.", "")
                .replace("query.anim_time", Double.toString(animationTime))
                .replace(" ", "");
            this.animationTime = animationTime;
        }

        static double evaluate(String expression, double animationTime) {
            try {
                var parser = new MathExpression(expression, animationTime);
                var value = parser.expression();
                return parser.cursor == parser.input.length() ? value : 0.0D;
            } catch (RuntimeException ignored) {
                return 0.0D;
            }
        }

        private double expression() {
            var value = term();
            while (cursor < input.length() && (input.charAt(cursor) == '+' || input.charAt(cursor) == '-')) {
                value = input.charAt(cursor++) == '+' ? value + term() : value - term();
            }
            return value;
        }

        private double term() {
            var value = factor();
            while (cursor < input.length() && (input.charAt(cursor) == '*' || input.charAt(cursor) == '/')) {
                value = input.charAt(cursor++) == '*' ? value * factor() : value / factor();
            }
            return value;
        }

        private double factor() {
            if (cursor < input.length() && input.charAt(cursor) == '-') {
                cursor++;
                return -factor();
            }
            if (cursor < input.length() && input.charAt(cursor) == '(') {
                cursor++;
                var value = expression();
                require(')');
                return value;
            }
            var start = cursor;
            while (cursor < input.length() && (Character.isLetter(input.charAt(cursor)) || input.charAt(cursor) == '_')) {
                cursor++;
            }
            if (start != cursor) {
                var function = input.substring(start, cursor);
                require('(');
                var value = expression();
                require(')');
                return switch (function) {
                    // MoLang's trig functions take degrees. Using Java's radian functions here made expressions such
                    // as cos(query.anim_time * 120) oscillate almost twenty times per second instead of the
                    // authored one-third-of-a-cycle-per-second tail sway.
                    case "sin" -> Math.sin(Math.toRadians(value));
                    case "cos" -> Math.cos(Math.toRadians(value));
                    default -> 0.0D;
                };
            }
            while (cursor < input.length() && (Character.isDigit(input.charAt(cursor)) || input.charAt(cursor) == '.')) {
                cursor++;
            }
            return Double.parseDouble(input.substring(start, cursor));
        }

        private void require(char expected) {
            if (cursor >= input.length() || input.charAt(cursor++) != expected) {
                throw new IllegalArgumentException("Expected " + expected);
            }
        }
    }
}
