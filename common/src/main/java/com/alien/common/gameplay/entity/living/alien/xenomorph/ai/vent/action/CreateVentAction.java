package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.vent.action;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.entity.living.alien.xenomorph.VentBuilder;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.vent.VentKind;
import com.alien.common.gameplay.hive.vent.VentPlacement;
import com.blib.api.common.goap.v1.action.impl.NeoMoveToPosAction;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.action.Action;
import com.just.ai.goap.state.Blackboard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Place a FRONTIER vent: the hive's outpost onto the caves and openings beyond its structure.
 * <p>
 * <b>Xenomorphs no longer dig.</b> The old action rayed into a wall, carved a three-block tunnel of resin web with
 * resin walls, and buried the vent block inside the rock at depth three. Nothing could path to such a vent, which is
 * why every consumer had to go hunting for a standable cell near it, and why carriers stalled forever outside hills.
 * <p>
 * Now the vent is simply placed in an <b>open cell resting against a solid face</b> - floor, wall or ceiling - webbed
 * on every side that touches air, with resin creeping over the rock around it. The wall stays solid. See
 * {@link VentPlacement}.
 * <p>
 * The hive's own internal ducts are STRUCTURE vents and come with the templates; nothing is dug for them. SURFACE vents
 * are dropped by surface parties. This action builds the third kind and only the third kind, out in the rock.
 */
public class CreateVentAction {

    /** How far a builder will look for somewhere to put a vent. */
    private static final int SEARCH_RADIUS = 5;

    /**
     * The openness test. A frontier vent is meant to sit at a cave mouth or an opening - somewhere a party can actually
     * pour out of - not wedged into a crevice or flat against a boulder in a dirt tunnel. So the cell must have real
     * space around it: at least this many of its 26 surrounding cells open.
     */
    private static final int MIN_OPEN_NEIGHBOURS = 9;

    private static final StateKey<BlockPos> KEY_VENT_SPOT = StateKey.sensed("vent_spot");

    private static final StateKey<Boolean> KEY_HAS_PLACED = StateKey.sensed("vent_has_placed");

    public static boolean hasVentTarget(Xenomorph xenomorph) {
        return findVentSpot(xenomorph) != null;
    }

    public static Action.Signal perform(Action.Context<? extends Xenomorph> context) {
        var xenomorph = context.getActor();
        var blackboard = context.getBlackboard(Blackboard.Scope.ACTION);

        if (blackboard.getOrDefault(KEY_HAS_PLACED, false)) {
            return Action.Signal.CONTINUE;
        }

        var ventSpot = blackboard.getOrDefault(KEY_VENT_SPOT, (BlockPos) null);
        if (ventSpot == null) {
            ventSpot = findVentSpot(xenomorph);
            if (ventSpot == null) {
                recordVentTargetSearchFailure(xenomorph);
                return Action.Signal.ABORT;
            }
            blackboard.set(KEY_VENT_SPOT, ventSpot);
        }

        // Walk to the mouth itself. Unlike the old buried vent, this cell is open ground - it can actually be stood in.
        var result = NeoMoveToPosAction.perform(context, Vec3.atBottomCenterOf(ventSpot), 0.5);

        return switch (result) {
            case FINISHED -> {
                placeVent(xenomorph, ventSpot);

                if (xenomorph instanceof VentBuilder ventBuilder) {
                    ventBuilder.getVentData().setLastVentCreationTick(xenomorph.tickCount);
                    ventBuilder.getVentData().clearVentTargetSearchFailure();
                }

                blackboard.set(KEY_HAS_PLACED, true);
                yield Action.Signal.CONTINUE;
            }
            case MOVING -> Action.Signal.CONTINUE;
            default -> {
                recordVentTargetSearchFailure(xenomorph);
                yield Action.Signal.ABORT;
            }
        };
    }

    public static void onFinish(Action.Context<? extends Xenomorph> context) {
        NeoMoveToPosAction.onFinish(context);
    }

    private static void placeVent(Xenomorph xenomorph, BlockPos ventSpot) {
        VentPlacement.place(
            xenomorph.level(),
            ventSpot,
            AlienVariantTypes.getFor(xenomorph),
            VentKind.FRONTIER
        );
    }

    /**
     * An open cell that rests against a solid face, sits in real open space, is reachable, and is not right on top of a
     * vent the hive already has.
     */
    private static @Nullable BlockPos findVentSpot(Xenomorph xenomorph) {
        var level = xenomorph.level();
        var origin = xenomorph.blockPosition();

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (
            var candidate : BlockPos.betweenClosed(
                origin.offset(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS),
                origin.offset(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)
            )
        ) {
            var pos = candidate.immutable();

            if (!VentPlacement.isOpen(level, pos)) {
                continue; // the vent sits ON a surface, not inside it
            }
            if (!VentPlacement.restsOnSolidFace(level, pos)) {
                continue; // nothing to mount it against
            }
            if (!isOpening(level, pos)) {
                continue; // a crevice, not a cave mouth
            }
            if (hasVentNearby(xenomorph, pos)) {
                continue; // the hive already has a mouth here
            }

            var distance = pos.distSqr(origin);
            if (distance >= bestDistance) {
                continue;
            }

            var path = xenomorph.getNavigation().createPath(pos, 1);
            if (path == null || !path.canReach()) {
                continue;
            }

            best = pos;
            bestDistance = distance;
        }

        return best;
    }

    /** Real open space around the cell - a cave mouth or an opening, rather than a crack in the rock. */
    private static boolean isOpening(Level level, BlockPos pos) {
        var open = 0;
        for (var dx = -1; dx <= 1; dx++) {
            for (var dy = -1; dy <= 1; dy++) {
                for (var dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    if (VentPlacement.isOpen(level, pos.offset(dx, dy, dz))) {
                        open++;
                    }
                }
            }
        }
        return open >= MIN_OPEN_NEIGHBOURS;
    }

    private static boolean hasVentNearby(Xenomorph xenomorph, BlockPos pos) {
        var owningLocation = HiveLocationRegistry.INSTANCE.getByChunk(
            xenomorph.level().dimension(),
            new ChunkPos(pos)
        );
        return owningLocation != null && !owningLocation.ventManager().getVentsWithinSection(pos).isEmpty();
    }

    private static void recordVentTargetSearchFailure(Xenomorph xenomorph) {
        if (xenomorph instanceof VentBuilder ventBuilder) {
            ventBuilder.getVentData().recordVentTargetSearchFailure(xenomorph.tickCount);
        }
    }

    private CreateVentAction() {
        throw new UnsupportedOperationException();
    }
}
