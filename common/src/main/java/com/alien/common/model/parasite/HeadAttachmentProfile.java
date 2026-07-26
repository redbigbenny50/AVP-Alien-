package com.alien.common.model.parasite;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * A datapack-driven description of where a facehugger sits on a host's face. Loaded from
 * {@code data/<namespace>/head_data/<name>.json} by {@code HeadAttachmentReloadListener}, synced to clients whole via
 * {@code S2CHeadAttachmentDataPayload}, and consumed (after a pixels-to-blocks bake) by
 * {@code FacehuggerModelRenderer}.
 * <p>
 * Every number is in <b>model pixels</b> (1/16 of a block) — the same units a modeler reads off the host's model in
 * Blockbench — so a JSON author never has to convert anything:
 * <ul>
 * <li>{@code entity} — the host entity type this profile applies to (e.g. {@code "minecraft:cow"}).</li>
 * <li>{@code size} — the width / height / depth of the host's head cube.</li>
 * <li>{@code pivot} — the point the head rotates around, relative to the host's origin.</li>
 * <li>{@code vertical_offset} (optional) — final up/down nudge of the hugger on the face. Defaults to
 * {@code -size.y}.</li>
 * <li>{@code face_offset} (optional) — final toward/away-from-the-face nudge. Defaults to {@code +size.z}.</li>
 * </ul>
 * The renderer additionally compensates for the attached parasite's own hitbox height, so these numbers are tuned once
 * against the standard facehugger and stay correct for any differently-sized hugger variant.
 * <p>
 * One field breaks the pixels rule on purpose: {@code reference_height} (optional) is the host's hitbox height in
 * <b>blocks</b> that these numbers were tuned against (e.g. {@code 1.8} for a player-model mob). When present, the
 * renderer measures each individual host's actual hitbox height and scales the whole placement by
 * {@code actual / reference} — this is how mods that give the same entity type per-individual sizes (MCA's villagers,
 * for example) get correct placement from a single profile. Leave it out for mobs whose hitbox changes with pose
 * (players sneaking or swimming) — pose shrink would wrongly shrink the placement.
 */
public record HeadAttachmentProfile(
    ResourceLocation entity,
    Vec3 size,
    Vec3 pivot,
    Optional<Double> verticalOffset,
    Optional<Double> faceOffset,
    Optional<Double> referenceHeight
) {

    private static final Codec<Vec3> VEC3_CODEC = Codec.DOUBLE.listOf()
        .comapFlatMap(
            list -> list.size() == 3
                ? DataResult.success(new Vec3(list.get(0), list.get(1), list.get(2)))
                : DataResult.error(() -> "Expected exactly 3 elements, got " + list.size()),
            vec -> List.of(vec.x, vec.y, vec.z)
        );

    public static final Codec<HeadAttachmentProfile> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("entity").forGetter(HeadAttachmentProfile::entity),
            VEC3_CODEC.fieldOf("size").forGetter(HeadAttachmentProfile::size),
            VEC3_CODEC.fieldOf("pivot").forGetter(HeadAttachmentProfile::pivot),
            Codec.DOUBLE.optionalFieldOf("vertical_offset").forGetter(HeadAttachmentProfile::verticalOffset),
            Codec.DOUBLE.optionalFieldOf("face_offset").forGetter(HeadAttachmentProfile::faceOffset),
            Codec.DOUBLE.optionalFieldOf("reference_height").forGetter(HeadAttachmentProfile::referenceHeight)
        ).apply(instance, HeadAttachmentProfile::new)
    );
}
