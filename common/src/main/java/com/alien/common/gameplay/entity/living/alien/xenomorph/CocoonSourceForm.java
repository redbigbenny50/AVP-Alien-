package com.alien.common.gameplay.entity.living.alien.xenomorph;

import com.blib.api.common.codec.v1.stream.impl.EnumStreamCodec;
import com.just.codec.stream.StreamCodec;
import com.mojang.serialization.Codec;

/**
 * Which creature a metamorphosing xenomorph came from, recorded on the <em>destination</em> entity at transition time
 * so the client can pick a source-specific emerge animation. Currently only meaningful for the queen, who emerges from
 * either a praetorian ({@code molt.prae}) or a crusher ({@code molt.crusher}); {@link #NONE} for everything else, which
 * keeps the shared {@code molt.emerge} behaviour. Synced like {@link CocoonState} (the emerge animation is chosen
 * client-side).
 */
public enum CocoonSourceForm {

    NONE,
    PRAETORIAN,
    CRUSHER;

    public static final Codec<CocoonSourceForm> PERSISTENT_CODEC =
        Codec.STRING.xmap(CocoonSourceForm::valueOf, CocoonSourceForm::name);

    public static final StreamCodec<CocoonSourceForm> STREAM_CODEC = EnumStreamCodec.of(CocoonSourceForm.class, NONE);
}
