package com.alien.client.render.dismemberment;

import com.alien.common.gameplay.entity.dismemberment.AdultXenomorphHitboxCatalog;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.blib.api.common.dismemberment.v1.Dismemberable;
import com.blib.api.common.dismemberment.v1.LimbDefinitionRegistry;
import com.blib.api.common.dismemberment.v1.hitbox.LimbHitboxRegistry;
import com.blib.api.common.dismemberment.v1.hitbox.LimbHitboxVolume;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;

/** F3+B overlay for additive firearm limb volumes. */
public final class LimbHitboxRenderer {

    private static final int[][] EDGES = {
        { 0, 1 },
        { 0, 2 },
        { 0, 4 },
        { 1, 3 },
        { 1, 5 },
        { 2, 3 },
        { 2, 6 },
        { 3, 7 },
        { 4, 5 },
        { 4, 6 },
        { 5, 7 },
        { 6, 7 }
    };

    private LimbHitboxRenderer() {}

    public static void render(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 camera) {
        var minecraft = Minecraft.getInstance();
        LimbDiagnostics.tickToggle(minecraft);
        var level = minecraft.level;
        if (level == null || !minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes()) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        // These are diagnostics, not ESP: terrain and other blocks must occlude them.
        RenderSystem.enableDepthTest();
        var consumer = buffers.getBuffer(RenderType.lines());
        var drewAny = false;
        var diagnostics = new ArrayList<Diagnostic>();
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living.isInvisible()) {
                continue;
            }
            var volumes = hasRenderedPose(living)
                ? PraetorianRenderedLimbPicker.renderedVolumes(living.getId())
                : LimbHitboxRegistry.getHitboxes(living);
            for (var volume : volumes) {
                renderVolume(poseStack, consumer, volume, colorFor(volume));
                drewAny = true;
            }
            if (LimbDiagnostics.isTextVisible() && shouldShowDiagnostic(minecraft, living, camera)) {
                diagnostics.add(new Diagnostic(living, volumes));
            }
        }
        if (drewAny) {
            buffers.endBatch(RenderType.lines());
        }
        // Text selects a different render type. It must run only after the line batch has been closed,
        // otherwise the shared BufferSource closes RenderType.lines mid-way through the next hitbox.
        for (var diagnostic : diagnostics) {
            renderDiagnostic(poseStack, buffers, minecraft, diagnostic.living(), diagnostic.renderedVolumes());
        }
        if (!diagnostics.isEmpty()) {
            buffers.endBatch();
        }
        RenderSystem.enableDepthTest();
        poseStack.popPose();
    }

    private static void renderVolume(PoseStack poseStack, VertexConsumer consumer, LimbHitboxVolume volume, float[] color) {
        var corners = corners(volume);
        for (var edge : EDGES) {
            line(poseStack, consumer, corners[edge[0]], corners[edge[1]], color);
        }
    }

    private static Vec3[] corners(LimbHitboxVolume volume) {
        var x = volume.xAxis().scale(volume.halfExtents().x);
        var y = volume.yAxis().scale(volume.halfExtents().y);
        var z = volume.zAxis().scale(volume.halfExtents().z);
        var center = volume.center();
        return new Vec3[] {
            center.subtract(x).subtract(y).subtract(z),
            center.add(x).subtract(y).subtract(z),
            center.subtract(x).add(y).subtract(z),
            center.add(x).add(y).subtract(z),
            center.subtract(x).subtract(y).add(z),
            center.add(x).subtract(y).add(z),
            center.subtract(x).add(y).add(z),
            center.add(x).add(y).add(z)
        };
    }

    private static void line(PoseStack poseStack, VertexConsumer consumer, Vec3 from, Vec3 to, float[] color) {
        var normal = to.subtract(from).normalize();
        consumer.addVertex(poseStack.last(), (float) from.x, (float) from.y, (float) from.z)
            .setColor(color[0], color[1], color[2], color[3])
            .setNormal(poseStack.last(), (float) normal.x, (float) normal.y, (float) normal.z);
        consumer.addVertex(poseStack.last(), (float) to.x, (float) to.y, (float) to.z)
            .setColor(color[0], color[1], color[2], color[3])
            .setNormal(poseStack.last(), (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static float[] colorFor(LimbHitboxVolume volume) {
        var id = volume.limbId().getPath();
        if (id.contains("head")) {
            return new float[] { 1.0F, 0.15F, 0.15F, 1.0F };
        }
        if (id.contains("tail")) {
            return new float[] { 0.15F, 0.9F, 1.0F, 1.0F };
        }
        if (id.contains("arm")) {
            return new float[] { 0.25F, 0.45F, 1.0F, 1.0F };
        }
        return new float[] { 0.2F, 1.0F, 0.3F, 1.0F };
    }

    private static boolean shouldShowDiagnostic(Minecraft minecraft, LivingEntity living, Vec3 camera) {
        var toEntity = living.position().add(0.0D, living.getBbHeight() * 0.5D, 0.0D).subtract(camera);
        return toEntity.lengthSqr() <= 32.0D * 32.0D
            && minecraft.player != null
            && minecraft.player.getViewVector(1.0F).dot(toEntity.normalize()) >= 0.65D;
    }

    private static boolean hasRenderedPose(LivingEntity living) {
        return living instanceof Xenomorph
            && AdultXenomorphHitboxCatalog.modelForEntityPath(BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).getPath()).isPresent();
    }

    /** Renders only while F3+B is active; it has no gameplay or per-tick cost. */
    private static void renderDiagnostic(
        PoseStack poseStack,
        MultiBufferSource.BufferSource buffers,
        Minecraft minecraft,
        LivingEntity living,
        java.util.List<LimbHitboxVolume> renderedVolumes
    ) {
        if (!(living instanceof Dismemberable dismemberable) || LimbDefinitionRegistry.getDefinitions(living).isEmpty()) {
            return;
        }

        var manager = dismemberable.getDismembermentManager();
        var thresholds = new HashMap<net.minecraft.resources.ResourceLocation, Float>();
        for (var volume : LimbHitboxRegistry.getHitboxes(living)) {
            thresholds.putIfAbsent(volume.limbId(), volume.limbDamageThreshold());
        }

        var lines = new ArrayList<String>();
        var crawl = living instanceof Xenomorph xenomorph && xenomorph.getCrawlingManager().isCrawling();
        lines.add(
            "%s  body %.1f/%.1f%s".formatted(
                living.getName().getString(),
                living.getHealth(),
                living.getMaxHealth(),
                crawl ? "  CRAWLING" : ""
            )
        );
        lines.add(
            "limb pose: %s  cubes: %d".formatted(
                hasRenderedPose(living)
                    ? "rendered"
                    : "server",
                renderedVolumes.size()
            )
        );
        for (var definition : LimbDefinitionRegistry.getDefinitions(living)) {
            var id = definition.id();
            var name = id.getPath().replace("praetorian_", "");
            if (manager.isDetached(id)) {
                lines.add(name + ": DETACHED");
                continue;
            }

            var threshold = thresholds.getOrDefault(id, 0.0F);
            if (threshold <= 0.0F) {
                lines.add(name + ": ATTACHED (head bonus)");
            } else {
                lines.add("%s: %.1f / %.1f".formatted(name, manager.getLimbDamage(id), threshold));
            }
        }

        poseStack.pushPose();
        // render(...) has already translated the shared stack by -camera for the wireframes.
        // Applying that subtraction a second time places labels far away from their entity.
        poseStack.translate(living.getX(), living.getY() + living.getBbHeight() + 0.45D, living.getZ());
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        // Match EntityRenderer#renderNameTag's billboard orientation exactly. The previous
        // negative X scale mirrored the glyphs and made this debug text disappear on some drivers.
        poseStack.scale(0.025F, -0.025F, 0.025F);

        var font = minecraft.font;
        var y = 0.0F;
        for (var line : lines) {
            var color = line.contains("DETACHED") ? 0xFFFF5555 : line.contains("CRAWLING") ? 0xFFFFFF55 : 0xFFFFFFFF;
            font.drawInBatch(
                Component.literal(line),
                -font.width(line) / 2.0F,
                y,
                color,
                true,
                poseStack.last().pose(),
                buffers,
                Font.DisplayMode.NORMAL,
                0x55000000,
                0xF000F0
            );
            y += 10.0F;
        }
        poseStack.popPose();
    }

    private record Diagnostic(
        LivingEntity living,
        java.util.List<LimbHitboxVolume> renderedVolumes
    ) {}
}
