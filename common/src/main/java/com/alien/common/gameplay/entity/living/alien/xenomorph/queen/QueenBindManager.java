package com.alien.common.gameplay.entity.living.alien.xenomorph.queen;

import com.alien.Alien;
import com.alien.common.gameplay.block.entity.capture.anchor.AnchorBlockEntity;
import com.alien.common.gameplay.hive.lifecycle.QueenCaptivity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Layer 2 - queen-side capture bind state. Tracks up to 8 capture chains (anchor block positions, in attach order) and
 * drives the restraint they put on her.
 * <p>
 * This is the source of truth for a queen's restraint; the per-anchor Layer 1 clamp is suppressed for queens (see
 * {@code AnchorBlockEntity.serverTick}). Attach/detach are fired from {@code AnchorBlockEntity.bind/release}, the
 * single choke points every chain attach/release flows through.
 * </p>
 * <p>
 * ⭐⭐⭐ Oct 3 - THE CHUNK-CENTRE TETHER IS GONE. [stated] testers asked that a chained queen not be locked into the
 * centre of a chunk. That tether only existed because the inhibitor gave her a one-chunk claim that had to stay put;
 * captive queens no longer hold ANY claim (see {@link QueenCaptivity}), so nothing needs her in a particular chunk.
 * </p>
 * <p>
 * THE NEW RULES, all [stated]:
 * </p>
 * <ul>
 * <li><b>Each chain is 24 blocks long and that is the end of it</b> - "she cant get past 24 thats the end of the chain
 * link". Measured from the anchor's handle to the nearest point of her hitbox, so a tall queen hanging under her rig is
 * not penalised for her own height.</li>
 * <li><b>Hitting the end is a tug of war - while she is on 1 to 3 chains</b> - "each time it hits 24 theres the chance
 * to break with a 2s cooldown": {@value #CHAIN_SNAP_CHANCE} per roll, at most one roll per chain every
 * {@value #CHAIN_SNAP_COOLDOWN_TICKS} ticks. A snapped chain frees that anchor (the block survives) and nothing drops.
 * [stated] "only at 1-3 once the 4th chain is in place she will be trapped" - from four chains the end of a chain is a
 * hard stop and never rolls.</li>
 * <li><b>Every chain she breaks herself is announced</b> - "The queen breaks her shackles" (see
 * {@link QueenCaptivity#announceShacklesBroken}).</li>
 * <li><b>On load</b> - [stated] a queen inside every chain's 24 stays exactly where she is; one outside any of them is
 * moved to the centre of her anchors; any chain she still cannot reach from there breaks. See
 * {@link #reconcileAfterLoad}.</li>
 * <li><b>More chains, more resistance</b> - "the more chains you add the more resistance there is to her pulling
 * against them": any motion AWAY from the centre is damped harder per chain.</li>
 * <li><b>Two or more chains pull her to a dynamic centre between their anchors</b>. One chain is a plain leash.</li>
 * <li><b>A captive seated on her chained sack is the exception</b> - she is moved (by fishing rod), not pulling: she
 * stays wherever she is put, feels no resistance, and the 24-block end is a hard stop with no snap roll.</li>
 * <li>Still four chains to bind ({@link #FULLY_BOUND_CHAINS}); still four anchors above her sharing a Y to hang
 * ({@link #suspensionAnchorY()}).</li>
 * </ul>
 * <p>
 * ⚠ EVERY CORRECTION GOES THROUGH {@code move()}, NEVER {@code setPos}. The old fully-bound pin teleported her onto the
 * chunk centre every tick - a centre inside a wall put her inside the wall. Moving with collision means a wall between
 * her and the centre simply stops her, and a chain held past its length by a wall just keeps rolling to snap.
 * </p>
 */
public class QueenBindManager {

    /** Maximum chains on a queen: 4 to fully bind, plus 4 for extra securement. */
    public static final int MAX_CHAINS = 8;

    /** Chains needed to count as fully bound / contained (the captive-breeder threshold). */
    public static final int FULLY_BOUND_CHAINS = 4;

    /** [stated] "each anchor can be pulled out to 24". The end of the chain - she cannot get further. */
    public static final double CHAIN_LENGTH = 24.0;

    /** [stated] "if she manages to pull out to 24 theres a 10% chance the chain can break". */
    public static final double CHAIN_SNAP_CHANCE = 0.10;

    /** [stated] "with a 2s cooldown" - per chain, so two straining chains roll independently. */
    public static final int CHAIN_SNAP_COOLDOWN_TICKS = 40;

    /** Slack for the load check, so a queen saved pressed against the end of a chain is not "outside" it. */
    private static final double LOAD_TOLERANCE = 0.1;

    /** How far past the end she must be pressed before it counts as straining (absorbs float noise). */
    private static final double STRAIN_EPSILON = 0.01;

    /**
     * Resistance per chain to motion AWAY from the centre: the outward part of her velocity is multiplied by
     * {@code 1 / (1 + RESISTANCE_PER_CHAIN * chains)} every tick - 1 chain keeps ~74%, 2 ~59%, 4 ~42%, 8 ~26%.
     */
    private static final double RESISTANCE_PER_CHAIN = 0.35;

    /** Within this horizontal distance of the centre she is left alone, so she does not jitter on the spot. */
    private static final double CENTRE_SETTLE_RADIUS = 1.0;

    /** Centring pull for a queen walking under her own AI, per block off-centre per chain (velocity, blocks/tick). */
    private static final double PULL_PER_BLOCK_PER_CHAIN = 0.004;

    /** Cap on the centring pull added per tick - roughly a slow walk once friction settles it. */
    private static final double MAX_PULL = 0.06;

    /**
     * A body with no AI of its own is DRAGGED toward the centre at this pace (blocks/tick), like the hand-hold drag.
     */
    private static final double LIMP_DRAG_SPEED = 0.12;

    private static final String TAG_ANCHORS = "BindAnchors";

    /**
     * Written on every save. Its ABSENCE on load is what marks a rig built under the old chunk-centre tether, and only
     * such a rig gets {@link #reconcileAfterLoad}. [stated] "thats only to count for times she starts with a longer
     * reach of then 24" - a rig built under the 24-block rules is never touched by it, however she was moved.
     */
    private static final String TAG_RULES_V2 = "BindRulesV2";

    /** Read and discarded - the old chunk-centre tether's lock. Kept as a name so old saves are recognised. */
    private static final String LEGACY_TAG_BIND_CHUNK = "BindChunk";

    private final Queen queen;

    /** Anchor block positions in attach order. Index doubles as the shackle slot for the geo/render slices. */
    private final List<BlockPos> anchors = new ArrayList<>();

    /** Per-chain snap cooldown: earliest game tick each anchor may roll again. Transient - a reload just re-arms. */
    private final Map<BlockPos, Long> nextSnapRollTick = new HashMap<>();

    /** This tick's chain handle per anchor (see {@link #refreshChainPoints}). */
    private final Map<BlockPos, Vec3> chainPoints = new HashMap<>();

    /** Set by {@link #load}; the first server tick runs {@link #reconcileAfterLoad}. */
    private boolean reconcileOnNextTick;

    public QueenBindManager(Queen queen) {
        this.queen = queen;
    }

    // ---- queries ----

    public int chainCount() {
        return anchors.size();
    }

    public boolean hasAnyChain() {
        return !anchors.isEmpty();
    }

    /**
     * ⭐⭐⭐ THE Y SHE HANGS FROM, or empty if she cannot hang.
     * <p>
     * [stated] "if all 4 are the same y she can hang if they arent she cant if theres less than 4 she cant", and
     * [stated] any four of them sharing a Y is enough. COUNT, NOT ARRANGEMENT; the row must be ABOVE her; the HIGHEST
     * qualifying row wins so the result does not depend on attach order. Unchanged by the Oct 3 tether rewrite.
     * </p>
     */
    public OptionalInt suspensionAnchorY() {
        var size = anchors.size();
        if (size < FULLY_BOUND_CHAINS) {
            return OptionalInt.empty();
        }

        // At most 8 anchors, so a plain count-per-anchor is cheaper than building a map every tick (this runs twice a
        // tick for a chained queen: once for the suspension, once for the restraint centre).
        var queenY = queen.blockPosition().getY();
        var best = Integer.MIN_VALUE;
        for (int i = 0; i < size; i++) {
            var y = anchors.get(i).getY();
            if (y <= queenY || y <= best) {
                continue;
            }
            var count = 0;
            for (int j = 0; j < size; j++) {
                if (anchors.get(j).getY() == y) {
                    count++;
                }
            }
            if (count >= FULLY_BOUND_CHAINS) {
                best = y;
            }
        }
        return best == Integer.MIN_VALUE ? OptionalInt.empty() : OptionalInt.of(best);
    }

    public boolean isFullyBound() {
        return anchors.size() >= FULLY_BOUND_CHAINS;
    }

    /** Live view of the bound anchors, attach-ordered. */
    public List<BlockPos> anchors() {
        return anchors;
    }

    // ---- attach / detach ----

    /**
     * Chance that securing THIS chain jolts a downed queen awake. Escalating - the final securing chain is by far the
     * riskiest, so the closer you are to owning her, the more likely you are to lose her.
     */
    private static double wakeChanceForChain(int chainNumber) {
        return switch (chainNumber) {
            case 1 -> 0.05;
            case 2 -> 0.10;
            case 3 -> 0.20;
            case 4 -> 0.40;
            default -> 0.0; // chains 5-8 are belt-and-braces on an already-secured queen
        };
    }

    /**
     * ⭐ [stated] "if you try to place a chain on an anchor thats detected as farther than 24 from the center it will
     * say anchor too far. if youre adding additional after shes caught same rule cant be further than 24 from the
     * established center."
     * <p>
     * The FIRST chain has no established centre yet, so it is measured to her. Every later chain is measured to the
     * centre of the chains already on her. ⚠ BOTH ALSO HAVE TO REACH HER: a chain that starts out longer than its own
     * length would be straining - and rolling to snap - from the moment it went on.
     * </p>
     */
    public boolean canAttachFrom(BlockPos anchorPos) {
        if (anchors.contains(anchorPos)) {
            return true; // re-binding the same anchor is a no-op, not a distance question
        }
        var anchorPoint = chainPoint(queen.level(), anchorPos);
        if (distanceToBody(anchorPoint) > CHAIN_LENGTH) {
            return false;
        }
        if (anchors.isEmpty()) {
            return true;
        }
        return anchorPoint.distanceTo(centre3d(anchors)) <= CHAIN_LENGTH;
    }

    /** Whether one more chain can go on at all. */
    public boolean hasRoomForChain() {
        return anchors.size() < MAX_CHAINS;
    }

    /**
     * Register a chain from {@code anchorPos}. The FIRST chain severs her from any hive (see {@link QueenCaptivity}).
     */
    public void attach(BlockPos anchorPos) {
        if (anchors.size() >= MAX_CHAINS || anchors.contains(anchorPos)) {
            return;
        }
        var firstChain = anchors.isEmpty();
        anchors.add(anchorPos.immutable());

        // A player putting chains back on her wipes any kin-rescue credit for an earlier, half-finished rescue.
        queen.clearFreedByKin();

        // ⭐ [stated] "chaining should severe her like the inhibitor does". The first chain is the moment of capture.
        if (firstChain && queen.level() instanceof ServerLevel serverLevel) {
            QueenCaptivity.onCaptured(serverLevel, queen);
        }

        // Chaining a DOWNED queen is the capture race: every chain you fit is another roll that she comes round
        // in your hands. Waking here does not undo the chains already on her - she simply wakes up wearing them.
        if (queen.isIncapacitated()) {
            var chance = wakeChanceForChain(anchors.size());
            if (chance > 0.0 && queen.getRandom().nextDouble() < chance) {
                queen.getIncapacitationManager().healRescue();
            }
        }
    }

    /** Drop the chain from {@code anchorPos}. */
    public void detach(BlockPos anchorPos) {
        anchors.remove(anchorPos);
        nextSnapRollTick.remove(anchorPos);
        chainPoints.remove(anchorPos);
    }

    /**
     * Push each bound anchor's slot index to its block entity so the client knows which shackle bone its chain attaches
     * to. Re-run every tick (the setter is change-gated, so this is a no-op once stable).
     */
    private void resyncShackleSlots() {
        for (int i = 0; i < anchors.size(); i++) {
            if (queen.level().getBlockEntity(anchors.get(i)) instanceof AnchorBlockEntity anchor) {
                anchor.setShackleSlot(i);
            }
        }
    }

    // ---- break-on-attack ----

    /**
     * One break roll per attack the queen makes. While she can still fight (1-3 chains) each swing has a chance to snap
     * the most-recently-attached chain; at 4+ chains she is locked (0%). Server-only. Unchanged by the Oct 3 rewrite -
     * it sits alongside the new strain roll rather than replacing it.
     */
    public void onQueenAttack() {
        if (queen.level().isClientSide() || anchors.isEmpty()) {
            return;
        }
        double chance = switch (anchors.size()) {
            case 1 -> 0.05;
            case 2 -> 0.03;
            case 3 -> 0.01;
            default -> 0.0; // 4+ chains: unbreakable
        };
        if (chance <= 0.0 || queen.getRandom().nextDouble() >= chance) {
            return;
        }
        breakOwnChain(anchors.get(anchors.size() - 1));
    }

    /** She broke this chain herself: snap it and tell everyone nearby. */
    private void breakOwnChain(BlockPos anchorPos) {
        snapChain(anchorPos);
        QueenCaptivity.announceShacklesBroken(queen);
    }

    /** Snap one chain: the anchor block survives (just freed) and this bind drops. Nothing is dropped. */
    private void snapChain(BlockPos anchorPos) {
        if (queen.level().getBlockEntity(anchorPos) instanceof AnchorBlockEntity anchor) {
            anchor.release(); // clears the anchor's bind and detaches it here through the bind wiring
        } else {
            detach(anchorPos); // fallback: the anchor block is already gone
        }
    }

    // ---- tick ----

    public void tick() {
        if (queen.level().isClientSide()) {
            return;
        }
        // Keep the synced chain count in lockstep so the client (shackle reveal + chain render) sees the right value.
        if (queen.bindChainCount.get() != anchors.size()) {
            queen.bindChainCount.set(anchors.size());
        }
        resyncShackleSlots();
        if (anchors.isEmpty()) {
            nextSnapRollTick.clear();
            reconcileOnNextTick = false;
            return;
        }
        refreshChainPoints();
        if (reconcileOnNextTick) {
            reconcileOnNextTick = false;
            reconcileAfterLoad();
            if (anchors.isEmpty()) {
                return;
            }
            refreshChainPoints();
        }
        applyRestraint();
    }

    /**
     * Looks every anchor's chain handle up ONCE per tick. The restraint needs each handle two or three times (the
     * centre, the chain-length check, the load check); asking the level for the block entity each time was up to ~24
     * lookups a tick for a fully rigged queen.
     */
    private void refreshChainPoints() {
        chainPoints.clear();
        var level = queen.level();
        for (var anchorPos : anchors) {
            chainPoints.put(anchorPos, chainPoint(level, anchorPos));
        }
    }

    private Vec3 cachedPoint(BlockPos anchorPos) {
        var point = chainPoints.get(anchorPos);
        return point != null ? point : chainPoint(queen.level(), anchorPos);
    }

    /**
     * \u2b50 [stated] "if she is in the bounds of the 24 rull she will stay inplace as if she was moved there. If she
     * is outside that rule on one of the anchors she will be snapped to the center of the 4 anchors. if she is still
     * not able to be between any of the 4 anchors she will break the chain of the one too far".
     * <p>
     * \u26a0 ONE-TIME OLD-WORLD UPGRADE ONLY - [stated] "thats only the first time loading an old world ... thats only
     * to count for times she starts with a longer reach of then 24". It runs on the first tick after a queen saved by
     * an older version loads (block entities are not reliably there yet when her NBT is read), then never again: her
     * next save carries {@link #TAG_RULES_V2}. Queens moved around under the new rules are never snapped or broken by
     * it.
     * </p>
     */
    private void reconcileAfterLoad() {
        if (!anyChainOverLength()) {
            return; // within every chain: she stays exactly where she is
        }

        // Move her to the centre of her anchors (horizontally - height stays hers, or the hanging code's). A spot she
        // would be stuck in is refused; a few blocks up or down are tried before giving up and leaving her in place.
        var centre = centre3d(anchors);
        var moved = false;
        for (var dy : new int[] { 0, 1, -1, 2, -2, 3, -3 }) {
            var target = new Vec3(centre.x, queen.getY() + dy, centre.z);
            var box = queen.getDimensions(queen.getPose()).makeBoundingBox(target);
            if (queen.level().noCollision(queen, box)) {
                queen.teleportTo(target.x, target.y, target.z);
                moved = true;
                break;
            }
        }
        Alien.LOGGER.info(
            "Captivity: queen {} loaded outside her chains' reach - {}",
            queen.getUUID(),
            moved ? "moved to the centre of her anchors" : "no free space at the centre, left in place"
        );

        // Whatever is still out of reach from there breaks.
        var brokeAny = false;
        for (var anchorPos : new ArrayList<>(anchors)) {
            if (distanceToBody(cachedPoint(anchorPos)) > CHAIN_LENGTH + LOAD_TOLERANCE) {
                snapChain(anchorPos);
                brokeAny = true;
            }
        }
        if (brokeAny) {
            QueenCaptivity.announceShacklesBroken(queen);
        }
    }

    private boolean anyChainOverLength() {
        for (var anchorPos : anchors) {
            if (distanceToBody(cachedPoint(anchorPos)) > CHAIN_LENGTH + LOAD_TOLERANCE) {
                return true;
            }
        }
        return false;
    }

    private void applyRestraint() {
        var level = queen.level();
        var chains = anchors.size();

        // 1. RESISTANCE + CENTRING. Damp motion away from the centre (harder per chain), and with 2+ chains pull her
        // in.
        var moved = false;

        // \u2b50 Oct 3 - A CAPTIVE SEATED ON HER CHAINED SACK IS MOVED, SHE DOES NOT PULL. [stated] "if the queen is
        // moved
        // after being chained she stays in that position. she cant be pulled past 24 no break chance she just cant go
        // past it and the other chains adjust their length accordingly." She never walks - the only thing that moves
        // her
        // is a player's fishing rod - so for her:
        // - no centring pull: wherever she is put, she stays;
        // - no resistance: that is HER struggling against the chains, and she is not struggling;
        // - each chain is still a hard 24, but reaching it never rolls a snap.
        // "The other chains adjust their length" needs no code: a chain is drawn from its anchor to her wherever she
        // is.
        var seatedCaptive = queen.isInhibited() && queen.isRidingOvipositor();

        var centre = restraintCentre();
        var dx = centre.x - queen.getX();
        var dz = centre.z - queen.getZ();
        var horizontal = Math.sqrt(dx * dx + dz * dz);

        if (horizontal > 1.0e-4) {
            var towardX = dx / horizontal;
            var towardZ = dz / horizontal;

            var velocity = queen.getDeltaMovement();
            var inward = velocity.x * towardX + velocity.z * towardZ;
            if (inward < 0.0 && !seatedCaptive) {
                // Moving outward: keep only a fraction of that outward component.
                var keep = 1.0 / (1.0 + RESISTANCE_PER_CHAIN * chains);
                var removed = -inward * (1.0 - keep);
                queen.setDeltaMovement(velocity.x + towardX * removed, velocity.y, velocity.z + towardZ * removed);
                moved = true;
            }

            // [stated] "anchors will now try to pull the queen to a dynamic center between them" - from two chains up.
            // One chain is a leash: its "centre" is the anchor itself, and dragging her onto it would be wrong.
            // Not for a seated captive - see the top of this method.
            if (chains >= 2 && !seatedCaptive && horizontal > CENTRE_SETTLE_RADIUS) {
                if (isLimp()) {
                    var step = Math.min(LIMP_DRAG_SPEED, horizontal - CENTRE_SETTLE_RADIUS);
                    queen.move(MoverType.SELF, new Vec3(towardX * step, 0.0, towardZ * step));
                    moved = true;
                } else {
                    var strength = Math.min(
                        (horizontal - CENTRE_SETTLE_RADIUS) * PULL_PER_BLOCK_PER_CHAIN * chains,
                        MAX_PULL
                    );
                    var v = queen.getDeltaMovement();
                    queen.setDeltaMovement(v.x + towardX * strength, v.y, v.z + towardZ * strength);
                    queen.hasImpulse = true;
                    moved = true;
                }
            }
        }

        // 2. THE END OF EACH CHAIN. Hard stop at 24, and every time she is pressed against it, a snap roll.
        var now = level.getGameTime();
        // [stated] "only at 1-3 once the 4th chain is in place she will be trapped".
        var canSnap = !seatedCaptive && chains < FULLY_BOUND_CHAINS;
        BlockPos toSnap = null;
        for (var anchorPos : new ArrayList<>(anchors)) {
            var overshoot = enforceChainLength(anchorPos);
            if (overshoot <= STRAIN_EPSILON) {
                continue;
            }
            moved = true;
            if (!canSnap) {
                continue; // a hard stop only: seated on her sack, or four chains or more
            }
            var readyAt = nextSnapRollTick.getOrDefault(anchorPos, 0L);
            if (now < readyAt) {
                continue;
            }
            nextSnapRollTick.put(anchorPos, now + CHAIN_SNAP_COOLDOWN_TICKS);
            if (toSnap == null && queen.getRandom().nextDouble() < CHAIN_SNAP_CHANCE) {
                toSnap = anchorPos;
            }
        }
        if (toSnap != null) {
            breakOwnChain(toSnap);
        }
        if (moved) {
            queen.hurtMarked = true; // only when something actually changed - not a velocity packet every tick
        }
    }

    /**
     * Pulls her back to the end of one chain if she is past it, and returns how far past it she WAS (0 if she was
     * inside). The return is what decides whether that chain is straining.
     * <p>
     * An anchor ABOVE her pulls her back along the chain, up included, and stops her fall - she hangs at the end of it.
     * An anchor level with or below her corrects horizontally only, leaving height to gravity.
     * </p>
     */
    private double enforceChainLength(BlockPos anchorPos) {
        var anchorPoint = cachedPoint(anchorPos);
        var nearest = nearestBodyPoint(anchorPoint);
        var distance = anchorPoint.distanceTo(nearest);
        if (distance <= CHAIN_LENGTH) {
            return 0.0;
        }

        var dy = anchorPoint.y - nearest.y;

        // ⭐ ANCHOR ABOVE HER: SHE HANGS AT THE END OF THE CHAIN. [stated] if the ground is taken out from under her
        // "she would stop at the maximum length so she would hang at the end of the chains." Pulled straight back along
        // the chain - up as well as across - and her fall stopped, so she swings under the anchor instead of dropping
        // past it. (A hanging rig never gets here: she hangs only 4 blocks under her anchors.)
        if (dy > 0.0) {
            var toAnchor = anchorPoint.subtract(nearest);
            var length = toAnchor.length();
            if (length > 1.0e-4) {
                var direction = toAnchor.scale(1.0 / length);
                queen.move(MoverType.SELF, direction.scale(length - CHAIN_LENGTH));

                // The chain is taut: nothing of her motion may carry on away from the anchor, and she is not falling.
                var v = queen.getDeltaMovement();
                var away = -(v.x * direction.x + v.y * direction.y + v.z * direction.z);
                if (away > 0.0) {
                    v = v.add(direction.scale(away));
                }
                queen.setDeltaMovement(v.x, Math.max(0.0, v.y), v.z);
                queen.resetFallDistance();
            }
            return distance - CHAIN_LENGTH;
        }

        // Anchor level with or below her: horizontal only. Height belongs to gravity, which already pulls her toward a
        // low anchor; if the vertical gap alone is past 24 the chain just strains (and, on 1-3 chains, rolls).
        var allowedHorizontal = Math.sqrt(Math.max(0.0, CHAIN_LENGTH * CHAIN_LENGTH - dy * dy));
        var hx = anchorPoint.x - nearest.x;
        var hz = anchorPoint.z - nearest.z;
        var currentHorizontal = Math.sqrt(hx * hx + hz * hz);

        if (currentHorizontal > allowedHorizontal && currentHorizontal > 1.0e-4) {
            var towardX = hx / currentHorizontal;
            var towardZ = hz / currentHorizontal;
            var correction = currentHorizontal - allowedHorizontal;
            queen.move(MoverType.SELF, new Vec3(towardX * correction, 0.0, towardZ * correction));

            // Cancel what is left of her outward motion along that chain - the chain is taut.
            var v = queen.getDeltaMovement();
            var outward = -(v.x * towardX + v.z * towardZ);
            if (outward > 0.0) {
                queen.setDeltaMovement(v.x + towardX * outward, v.y, v.z + towardZ * outward);
            }
        }
        return distance - CHAIN_LENGTH;
    }

    /**
     * Where the chains pull her: the horizontal centre of all her anchors - or, while she hangs, of the row she hangs
     * from, so a second tier of anchors at another height does not drag her sideways off her gallows.
     */
    private Vec3 restraintCentre() {
        var hangY = suspensionAnchorY();
        List<BlockPos> pulling = anchors;
        if (hangY.isPresent()) {
            var row = new ArrayList<BlockPos>();
            for (var anchor : anchors) {
                if (anchor.getY() == hangY.getAsInt()) {
                    row.add(anchor);
                }
            }
            pulling = row;
        }
        return centre3d(pulling);
    }

    /** Mean of the chain handles of {@code positions}. */
    private Vec3 centre3d(List<BlockPos> positions) {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (var pos : positions) {
            var point = cachedPoint(pos);
            x += point.x;
            y += point.y;
            z += point.z;
        }
        var n = Math.max(1, positions.size());
        return new Vec3(x / n, y / n, z / n);
    }

    /** The chain's bind point on its anchor (the {@code gHandle}), or the block centre if the anchor is gone. */
    private static Vec3 chainPoint(Level level, BlockPos anchorPos) {
        if (level.getBlockEntity(anchorPos) instanceof AnchorBlockEntity anchor) {
            return anchor.chainAnchorPoint();
        }
        return Vec3.atCenterOf(anchorPos);
    }

    /** Closest point of her hitbox to {@code point}. */
    private Vec3 nearestBodyPoint(Vec3 point) {
        AABB box = queen.getBoundingBox();
        return new Vec3(
            Math.max(box.minX, Math.min(point.x, box.maxX)),
            Math.max(box.minY, Math.min(point.y, box.maxY)),
            Math.max(box.minZ, Math.min(point.z, box.maxZ))
        );
    }

    private double distanceToBody(Vec3 point) {
        return point.distanceTo(nearestBodyPoint(point));
    }

    /**
     * A body that cannot walk is DRAGGED rather than nudged. Velocity nudges assume the mob's own travel carries it; a
     * downed queen (no AI) has her velocity damped away before travel runs, and a captive breeder seated on her chained
     * sack has her horizontal travel frozen by {@code Queen.travel}. Same reasoning as the hand-hold drag.
     */
    private boolean isLimp() {
        return !queen.isEffectiveAi() || queen.isIncapacitated() || (queen.isInhibited() && queen.isRidingOvipositor());
    }

    // ---- persistence ----

    public void load(CompoundTag tag) {
        anchors.clear();
        nextSnapRollTick.clear();
        // One-time upgrade only: a save written by this version carries the marker and is never re-checked.
        reconcileOnNextTick = !tag.getBoolean(TAG_RULES_V2);
        if (tag.contains(TAG_ANCHORS)) {
            for (long packed : tag.getLongArray(TAG_ANCHORS)) {
                anchors.add(BlockPos.of(packed));
            }
        }
        // LEGACY_TAG_BIND_CHUNK is deliberately ignored: there is no chunk tether any more, and save() no longer
        // writes it, so it falls out of the save the first time she is written.
    }

    /**
     * Reconcile persisted chains against the world once, after load. An anchor broken while the queen was UNLOADED
     * never ran its release() -> detach() (that resolves the queen via getEntity, which returns null for an unloaded
     * entity), so she can reload still bound to an anchor block that no longer exists. Drop any anchor whose block is
     * no longer a live AnchorBlockEntity bound to HER.
     */
    public void onLoaded() {
        if (queen.level().isClientSide() || anchors.isEmpty()) {
            return;
        }
        var survivors = new ArrayList<BlockPos>(anchors.size());
        for (var anchorPos : anchors) {
            if (
                queen.level().getBlockEntity(anchorPos) instanceof AnchorBlockEntity anchor
                    && queen.getUUID().equals(anchor.getBoundMobId())
            ) {
                survivors.add(anchorPos);
            }
        }
        if (survivors.size() != anchors.size()) {
            anchors.clear();
            anchors.addAll(survivors);
        }
    }

    public void save(CompoundTag tag) {
        tag.putBoolean(TAG_RULES_V2, true);
        if (anchors.isEmpty()) {
            return;
        }
        long[] packed = new long[anchors.size()];
        for (int i = 0; i < anchors.size(); i++) {
            packed[i] = anchors.get(i).asLong();
        }
        tag.putLongArray(TAG_ANCHORS, packed);
    }
}
