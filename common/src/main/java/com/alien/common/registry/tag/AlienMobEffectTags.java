package com.alien.common.registry.tag;

import com.alien.AlienResources;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;

public class AlienMobEffectTags {

    public static final TagKey<MobEffect> DOES_NOT_AFFECT_ALIENS = create("does_not_affect_aliens");

    /**
     * Radiation-class effects. Refused by every alien EXCEPT the aberrant strain (the species is radiation-immune
     * by decree; aberrants are the weak line and burn instead) - which is why this cannot live in
     * DOES_NOT_AFFECT_ALIENS. Cross-mod ids (avp_human's) sit in the json as OPTIONAL entries.
     */
    public static final TagKey<MobEffect> RADIATION = create("radiation");

    /**
     * Effects a chestburster does NOT inherit from the host it came out of.
     * <p>
     * The birth copies the host's active effects onto the newborn at {@code Integer.MAX_VALUE} duration, deliberately:
     * whatever the host was carrying when it burst becomes part of what came out. That is the intended mechanic and it
     * stays. This tag is the exception list - the host's own LIFECYCLE state, which describes the pregnancy rather than
     * the child, and which turns pathological when it is made permanent.
     * <p>
     * Separate from {@link #DOES_NOT_AFFECT_ALIENS} because these effects MUST be able to affect aliens: metamorphosis
     * drives the entire caste ladder. The distinction is inheritance, not immunity.
     */
    public static final TagKey<MobEffect> NOT_INHERITED_BY_EMBRYO = create("not_inherited_by_embryo");

    private static TagKey<MobEffect> create(String name) {
        return TagKey.create(Registries.MOB_EFFECT, AlienResources.location(name));
    }
}
