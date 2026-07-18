package com.alien.common.gameplay.entity.living.alien.xenomorph.queen.ai.location_move;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenLifecyclePhaseManager;
import com.blib.api.common.goap.v1.GOAPSensors;
import com.blib.api.common.goap.v1.action.ActionMasks;
import com.blib.api.common.goap.v1.action.BLibAction;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.action.Action;
import com.just.ai.goap.condition.expression.Expressions;
import com.just.ai.goap.state.Blackboard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Action for the location-phase dig-to-anchor behaviour (Stage 2b).
 * <p>
 * Two stages, tracked in the action blackboard:
 * <ol>
 * <li><b>Wind-up</b> ({@link #WINDUP_TICKS}): she holds in place on the surface, telegraphing with particles but
 * <em>not</em> yet clipping — a window for the player to interrupt her (attacking her drops the action, which resets
 * the wind-up when it re-selects). She is a normal, solid, attackable entity here.</li>
 * <li><b>Descent</b>: she goes noclip and sinks toward the committed anchor — slowly ({@link #DESCENT_SPEED}) while
 * cutting through blocks so a player can race her down, but quickly ({@link #AIR_DESCENT_SPEED}) through open air/caves
 * where there is nothing to dig.</li>
 * </ol>
 * Movement is velocity-based ({@code setDeltaMovement}); with noPhysics on, her own {@code move()} applies it straight
 * through blocks. Navigation is stopped each tick so the move-control can't fight or spin the velocity, and the action
 * owns her yaw. MOVE+LOOK, gated by {@code HAS_ATTACK_TARGET == false} so combat wins, negative cost so it beats idle.
 */
public final class LocationMoveActions {

    /** Hold-in-place telegraph before descending (~15s) so the player can react/interrupt. */
    private static final int WINDUP_TICKS = 15 * 20;

    /** Blocks travelled per tick during descent. Deliberately slow (~2 blocks/sec) so a player can dig down to her. */
    private static final double DESCENT_SPEED = 0.1;

    /**
     * Faster descent (~12 blocks/sec) used while the next block down is open air. There is nothing to dig in a gap or
     * cave, so the slow {@link #DESCENT_SPEED} (which exists only so a player can race her down <em>through stone</em>)
     * would just make her hang in the air; this drops her through the opening and reverts to dig-speed the moment she
     * re-enters blocks. A "fall" through open space without ever leaving noclip.
     */
    private static final double AIR_DESCENT_SPEED = 0.6;

    /** Close enough that the phase manager's arrival handling takes over; avoids jittering on the last fraction. */
    private static final double ARRIVAL_EPSILON = 0.3;

    /**
     * Negative so the planner prefers digging over idle wandering; shallower than combat costs so combat still wins.
     */
    private static final float COST = -1.0F;

    private static final int WINDUP_PARTICLE_COUNT = 10;

    private static final int DESCENT_PARTICLE_COUNT = 18;

    /** Ticks between dig crunches. The particle emitters run every tick; a break sound at 20/s would be unbearable. */
    private static final int DIG_SOUND_INTERVAL_TICKS = 5;

    private static final StateKey<Integer> KEY_WINDUP_REMAINING = StateKey.sensed("location_dig_windup_remaining");

    public static final Action<Xenomorph> DIG_TO_ANCHOR = BLibAction.<Xenomorph>builder("DigToLocationAnchorAction")
        .addMasks(ActionMasks.MOVE, ActionMasks.LOOK)
        .addPrecondition(GOAPSensors.HAS_ATTACK_TARGET.key(), Expressions.Boolean.isFalse())
        .addPrecondition(LocationMoveSensors.IS_LOCATING.key(), Expressions.Boolean.isTrue())
        .addPrecondition(LocationMoveSensors.IS_AT_ANCHOR.key(), Expressions.Boolean.isFalse())
        .addEffect(LocationMoveSensors.IS_AT_ANCHOR.key().asDerived(), true)
        .withCost(COST)
        .withPerformCallback(LocationMoveActions::performDig)
        .withFinishCallback(context -> {
            // Digging is scoped to this action running: clear the clip/no-gravity state the instant it stops (arrival,
            // combat, leaving LOCATION) so she is never noclip while some other behaviour is moving her.
            if (context.getActor() instanceof Queen queen) {
                queen.setDigging(false);
            }
            context.getBlackboard(Blackboard.Scope.ACTION).clear();
        })
        .build();

    private static Action.Signal performDig(Action.Context<? extends Xenomorph> context) {
        var actor = context.getActor();
        var anchor = LocationMoveSensors.locationAnchorOrNull(actor);

        // No longer locating (left LOCATION, became established, behaviour disabled) — let GOAP replan.
        if (anchor == null) {
            return Action.Signal.ABORT;
        }

        // anchor != null guarantees a LOCATION-phase queen; defensive cast.
        if (!(actor instanceof Queen queen)) {
            return Action.Signal.ABORT;
        }

        // Stop vanilla pathing so the move-control can't fight (or spin) us.
        actor.getNavigation().stop();

        var blackboard = context.getBlackboard(Blackboard.Scope.ACTION);
        var windupRemaining = blackboard.getOrDefault(KEY_WINDUP_REMAINING, WINDUP_TICKS);

        // ---- Wind-up: hold on the surface, telegraph, stay solid/interruptible (not yet clipping). ----
        if (windupRemaining > 0) {
            blackboard.set(KEY_WINDUP_REMAINING, windupRemaining - 1);
            queen.setDigging(false);
            actor.setDeltaMovement(Vec3.ZERO);
            spawnDigParticles(actor, WINDUP_PARTICLE_COUNT);
            return Action.Signal.CONTINUE;
        }

        // ---- Descent: go noclip and sink slowly toward the anchor. ----
        queen.setDigging(true);

        var target = new Vec3(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
        var current = actor.position();
        var delta = target.subtract(current);
        var distance = delta.length();

        // Effectively arrived: hold still. IS_AT_ANCHOR flips true next tick and the phase manager handles the
        // pocket-clear + hand-off, which drops this action from contention.
        if (distance <= ARRIVAL_EPSILON) {
            actor.setDeltaMovement(Vec3.ZERO);
            return Action.Signal.CONTINUE;
        }

        // Gate the dig to diggable terrain: if the block one step ahead is undiggable (xenomorph-immune, unbreakable,
        // or a block entity — not resin-replaceable or alien-breakable, and not air), she can't pass. Settle here.
        var direction = delta.scale(1.0 / distance);
        var lead = BlockPos.containing(
            current.x + direction.x,
            current.y + direction.y,
            current.z + direction.z
        );
        if (!QueenLifecyclePhaseManager.isDiggable(actor.level(), lead)) {
            actor.setDeltaMovement(Vec3.ZERO);
            queen.getLifecyclePhaseManager().onDigBlocked();
            return Action.Signal.CONTINUE;
        }

        // Velocity-based clip movement: with noPhysics on, move() applies this delta directly, through blocks. Through
        // open air there is nothing to dig, so she drops at AIR_DESCENT_SPEED and only crawls at DESCENT_SPEED while
        // actually cutting through blocks — restoring a "fall" through caves/gaps without leaving noclip.
        var descentSpeed = actor.level().getBlockState(lead).isAir() ? AIR_DESCENT_SPEED : DESCENT_SPEED;
        var velocity = delta.scale(Math.min(descentSpeed, distance) / distance);

        // VOID GUARD: her anchor Y is her intended floor (the weighted band never rolls below it). The bedrock
        // blacklist stops her cutting THROUGH a bedrock block, but the overworld bedrock layer is a random mix of
        // bedrock and AIR - and air passes isDiggable - so a descent column that lines up with a gap would let her
        // clip straight past the floor and free-fall into the void below Y=-64. Never let this tick's clip carry
        // her BELOW the anchor Y: if the step would overshoot it, cap the downward move to land exactly on it and
        // settle. She can only ever dig DOWN to her anchor, never past it, gap or no gap.
        var floorY = anchor.getY();
        if (current.y + velocity.y < floorY) {
            actor.setDeltaMovement(0.0, floorY - current.y, 0.0);
            actor.setPos(actor.getX(), Math.max(actor.getY(), floorY), actor.getZ());
            spawnDigParticles(actor, DESCENT_PARTICLE_COUNT);
            return Action.Signal.CONTINUE;
        }

        actor.setDeltaMovement(velocity);
        faceHorizontal(actor, delta);
        spawnDigParticles(actor, DESCENT_PARTICLE_COUNT);

        return Action.Signal.CONTINUE;
    }

    /**
     * Owns yaw so nothing else spins her. Only turns when there is meaningful horizontal travel (a vertical shaft keeps
     * her current facing).
     */
    private static void faceHorizontal(Xenomorph actor, Vec3 delta) {
        var horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 1.0E-3) {
            return;
        }
        var yaw = (float) (Mth.atan2(delta.z, delta.x) * (180.0 / Math.PI)) - 90.0F;
        actor.setYRot(yaw);
        actor.yBodyRot = yaw;
        actor.yHeadRot = yaw;
    }

    private static void spawnDigParticles(Xenomorph actor, int count) {
        if (!(actor.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // Sample the block she is inside (descent) or standing on (wind-up on the surface).
        var state = serverLevel.getBlockState(actor.blockPosition());
        if (state.isAir()) {
            state = serverLevel.getBlockState(actor.blockPosition().below());
        }
        if (state.isAir()) {
            return;
        }

        // Crunch of the block she is chewing through - the sampled block's OWN break sound, so digging stone,
        // dirt or gravel each sound right. Throttled: this method runs every tick.
        if (actor.tickCount % DIG_SOUND_INTERVAL_TICKS == 0) {
            var breakSound = state.getSoundType().getBreakSound();
            var pitch = 0.7F + actor.getRandom().nextFloat() * 0.3F;
            serverLevel.playSound(
                null,
                actor.getX(),
                actor.getY(),
                actor.getZ(),
                breakSound,
                SoundSource.BLOCKS,
                0.6F,
                pitch
            );
        }

        var particle = new BlockParticleOption(ParticleTypes.BLOCK, state);
        var width = actor.getBbWidth();
        var height = actor.getBbHeight();

        // Body cloud.
        serverLevel.sendParticles(
            particle,
            actor.getX(),
            actor.getY() + height * 0.5,
            actor.getZ(),
            count,
            width * 0.6,
            height * 0.45,
            width * 0.6,
            0.02
        );

        // Heavier burst at her feet, where the displacement reads as digging.
        serverLevel.sendParticles(
            particle,
            actor.getX(),
            actor.getY() + 0.1,
            actor.getZ(),
            Math.max(4, count / 2),
            width * 0.7,
            0.1,
            width * 0.7,
            0.06
        );
    }

    private LocationMoveActions() {
        throw new UnsupportedOperationException();
    }
}
