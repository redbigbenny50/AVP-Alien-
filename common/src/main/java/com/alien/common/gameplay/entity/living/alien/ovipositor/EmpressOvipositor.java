package com.alien.common.gameplay.entity.living.alien.ovipositor;

import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * The empress's eggsack: bigger than a queen's, and hers alone.
 * <h2>Why a separate entity rather than a variant flag</h2> It is a different model with its own textures, and it can
 * never be the CHAINED presentation - [stated] "she cant be chained or inhibited so theres no chained version of her
 * eggsack." A queen's ovipositor carries that whole second identity: a chained geo, a restraints render layer, and a
 * renderer that switches between them by asking her whether she is contained. None of that applies here, and a shared
 * entity would mean carrying the branch anyway and trusting it never fires. Splitting them means the empress's version
 * simply has no captive form to get into.
 * <h2>What it inherits</h2> Everything structural, from {@link Ovipositor}: it cannot be knocked back, it ignores
 * damage types that do not hurt aliens, and - importantly - it self-discards the moment it has no living royal beneath
 * it. An eggsack is not a thing that exists in the world; it is a thing that exists ON someone.
 */
public class EmpressOvipositor extends Ovipositor {

    /**
     * Her eggsack's own attribute supplier.
     * <p>
     * EVERY LivingEntity type needs one registered: {@code LivingEntity}'s constructor calls {@code getMaxHealth()}
     * before the entity exists, so a type without a supplier does not misbehave - it throws an NPE the first time
     * anything tries to create one. This type was registered without one, which never surfaced while no empress could
     * reach the point of growing an eggsack.
     * <p>
     * Tougher than the queen's 100: it is a far larger organ on a far larger royal, and cutting it off her should be a
     * commitment rather than a distraction.
     */
    public static AttributeSupplier.Builder createEmpressOvipositorAttributes() {
        return createMobAttributes()
            .add(Attributes.MAX_HEALTH, 200)
            .add(Attributes.MOVEMENT_SPEED, 0);
    }

    public EmpressOvipositor(EntityType<? extends EmpressOvipositor> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * Empresses only. The parent allows the whole QUEENS tag, which INCLUDES empresses (the tag nests
     * {@code #avp_alien:empresses}) - so without this narrowing an empress eggsack would happily ride an ordinary queen
     * and she would be wearing the wrong model.
     */
    @Override
    protected final boolean canRide(@NotNull Entity vehicle) {
        return super.canRide(vehicle) && vehicle.getType().is(AlienEntityTypeTags.EMPRESSES);
    }
}
