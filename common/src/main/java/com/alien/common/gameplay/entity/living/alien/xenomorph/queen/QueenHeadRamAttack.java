package com.alien.common.gameplay.entity.living.alien.xenomorph.queen;

import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ScaledDamage;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.tag.AlienBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * THE QUEEN'S HEAD RAM - an area shove that also punches a hole in a wall.
 * <p>
 * [stated] "its a knock back if it hits mobs or a player an aoe knockback to anything 5x5x3 infront of her so up to 3
 * blocks out. also if she rams a wall she breaks blocks in that pattern if its in the xeno break list. it does medium
 * damage if hit has a cool down of 120s."
 * </p>
 * <h2>⚠⚠ IT IS AN AREA ATTACK, NOT A TARGETED ONE</h2> The AttackType's own {@code target} is only what she aimed at;
 * everything in the box is hit whether she was aiming at it or not. So this deliberately IGNORES the passed target and
 * re-scans, which is also what makes it work when she connects with nothing but stone - the wall break has to happen on
 * a swing that hit no entity at all.
 * <h2>⚠ THE TWO EFFECTS ARE INDEPENDENT</h2> A ram can shove a crowd, shatter a wall, both, or neither. Gating the
 * break on having hit something (or the reverse) would make her unable to open a wall while anything stood near her.
 */
public final class QueenHeadRamAttack {

    /** 5 wide, 5 tall, 3 deep in front of her - [stated] "5x5x3 infront of her so up to 3 blocks out". */
    private static final int RAM_HALF_WIDTH = 2;

    private static final int RAM_HEIGHT = 5;

    private static final int RAM_DEPTH = 3;

    /** Medium: below her downward swipe, above nothing. The shove is the point, not the number. */
    private static final float RAM_DAMAGE_FRACTION = 0.65F;

    private static final double RAM_KNOCKBACK_STRENGTH = 1.15;

    /** ⚠ Enough lift to break ground friction and no more - this is a SHOVE, not the crusher's throw. */
    private static final double RAM_VERTICAL_BOOST = 0.34;

    private QueenHeadRamAttack() {}

    public static AttackType create(String id, int cooldownInTicks, int durationInTicks) {
        return AttackType.builder(id)
            .requiresHead()
            .defaultDurationInTicks(durationInTicks)
            .cooldownInTicks(cooldownInTicks)
            .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
            .damageApplicator((xenomorph, target) -> apply(xenomorph, target))
            .build();
    }

    private static void apply(Xenomorph xenomorph, @Nullable LivingEntity ignoredTarget) {
        xenomorph.swing(InteractionHand.MAIN_HAND);

        shoveEveryoneInFront(xenomorph);
        breachWall(xenomorph);
    }

    private static void shoveEveryoneInFront(Xenomorph xenomorph) {
        var facing = Direction.fromYRot(xenomorph.getYRot());
        var origin = xenomorph.blockPosition();

        // The far corner of the box, measured out along her facing and across it.
        var near = origin.relative(facing);
        var far = origin.relative(facing, RAM_DEPTH);
        var box = new AABB(near).minmax(new AABB(far))
            .inflate(RAM_HALF_WIDTH, 0.0, RAM_HALF_WIDTH)
            .setMinY(origin.getY())
            .setMaxY(origin.getY() + RAM_HEIGHT);

        for (var victim : xenomorph.level().getEntitiesOfClass(LivingEntity.class, box)) {
            // ⚠ HER OWN KIND ARE NOT SHOVED. A 5x5x3 shove in a corridor would scatter her own escort every time she
            // opened a wall, and the hive already refuses to damage its own everywhere else.
            if (victim == xenomorph || xenomorph.isAlliedTo(victim) || victim instanceof Xenomorph) {
                continue;
            }

            // ⚠ NO HIT, NO SHOVE - blocked, immune or in i-frames should not still be thrown across the room.
            if (!ScaledDamage.hurtScaled(xenomorph, victim, RAM_DAMAGE_FRACTION)) {
                continue;
            }

            var dx = victim.getX() - xenomorph.getX();
            var dz = victim.getZ() - xenomorph.getZ();
            var length = Math.sqrt(dx * dx + dz * dz);

            if (length < 1.0E-4) {
                // Exactly overlapping - shove along her facing rather than dividing by zero.
                dx = facing.getStepX();
                dz = facing.getStepZ();
                length = 1.0;
            }

            // ⚠ NEGATED: LivingEntity.knockback SUBTRACTS the direction it is given, so the away-vector is negated to
            // throw the victim outward rather than drag it in. Same convention as every other knockback in the mod.
            victim.knockback(RAM_KNOCKBACK_STRENGTH, -(dx / length), -(dz / length));

            var motion = victim.getDeltaMovement();
            victim.setDeltaMovement(motion.x, Math.max(motion.y, 0.0) + RAM_VERTICAL_BOOST, motion.z);
            victim.hurtMarked = true; // server-side shove; without this the client never sees the movement
        }
    }

    /**
     * Punches the same 5x5x3 out of whatever she rammed.
     * <p>
     * ⚠ REUSES THE HARBINGER'S BREAK BLACKLIST ({@code AlienBlockTags.HARBINGER_UNBREAKABLE}) rather than inventing a
     * second list. Two "what can a big xenomorph smash" tags would drift apart the first time either was edited, and
     * anything a harbinger cannot kick through a queen has no business ramming either.
     * </p>
     */
    private static void breachWall(Xenomorph xenomorph) {
        if (!(xenomorph.level() instanceof ServerLevel level)) {
            return;
        }

        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }

        var facing = Direction.fromYRot(xenomorph.getYRot());
        var origin = xenomorph.blockPosition();

        for (var depth = 1; depth <= RAM_DEPTH; depth++) {
            for (var lateral = -RAM_HALF_WIDTH; lateral <= RAM_HALF_WIDTH; lateral++) {
                for (var height = 0; height < RAM_HEIGHT; height++) {
                    var pos = origin.relative(facing, depth)
                        .relative(facing.getClockWise(), lateral)
                        .above(height);

                    if (!canShatter(level, pos)) {
                        continue;
                    }

                    // No drops: a 5x5x3 breach would carpet the floor in items, and the point is the hole.
                    level.destroyBlock(pos, false, xenomorph);
                }
            }
        }
    }

    private static boolean canShatter(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);

        if (state.isAir() || state.hasBlockEntity()) {
            return false;
        }

        // Bedrock and friends report a negative destroy speed - never breakable, by anything.
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }

        return !state.is(AlienBlockTags.HARBINGER_UNBREAKABLE);
    }
}
