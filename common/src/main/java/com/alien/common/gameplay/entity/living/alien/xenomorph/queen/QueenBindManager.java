package com.alien.common.gameplay.entity.living.alien.xenomorph.queen;

import com.alien.common.gameplay.block.entity.capture.anchor.AnchorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Layer 2 — queen-side capture bind state. Tracks up to 8 capture chains (anchor block positions, in attach order) and
 * the bind chunk locked when the first chain attaches. Drives the progressive movement restriction toward that chunk's
 * center; at 4+ chains she is pinned dead-center.
 * <p>
 * This is the source of truth for a queen's restraint; the per-anchor Layer 1 clamp is suppressed for queens (see
 * {@code AnchorBlockEntity.serverTick}). Attach/detach are fired from {@code AnchorBlockEntity.bind/release}, the
 * single choke points every chain attach/release flows through.
 * <p>
 * Slice 1 scope: state, attach/detach, persistence, and the restriction clamp (server-side). Geo reveal, the
 * anchor-to-shackle render, break-on-attack, AI suppression at 4 chains, and founding suppression are later slices; the
 * queries here ({@link #chainCount()}, {@link #isFullyBound()}, {@link #anchors()}) are the hooks they will read.
 */
public class QueenBindManager {

    /** Maximum chains on a queen: 4 to fully bind, plus 4 for extra securement. */
    public static final int MAX_CHAINS = 8;

    /** Chains needed to count as fully bound / contained (the captive-breeder threshold). */
    public static final int FULLY_BOUND_CHAINS = 4;

    private static final String TAG_ANCHORS = "BindAnchors";

    private static final String TAG_BIND_CHUNK = "BindChunk";

    private final Queen queen;

    /** Anchor block positions in attach order. Index doubles as the shackle slot for later geo/render slices. */
    private final List<BlockPos> anchors = new ArrayList<>();

    /** Chunk locked when the first chain attaches; all restriction is measured from its center. Null when unbound. */
    private ChunkPos bindChunk;

    public QueenBindManager(Queen queen) {
        this.queen = queen;
    }

    // ---- queries (hooks for later slices) ----

    public int chainCount() {
        return anchors.size();
    }

    public boolean hasAnyChain() {
        return !anchors.isEmpty();
    }

    /**
     * Fully restrained: 4+ chains, pinned at chunk center. (AI suppression that stops her fighting is a later slice.)
     */
    public boolean isFullyBound() {
        return anchors.size() >= FULLY_BOUND_CHAINS;
    }

    /** Live view of the bound anchors, attach-ordered. */
    public List<BlockPos> anchors() {
        return anchors;
    }

    // ---- attach / detach ----

    /** Register a chain from {@code anchorPos}. The first chain locks the bind chunk to the queen's current chunk. */
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

    public void attach(BlockPos anchorPos) {
        if (anchors.size() >= MAX_CHAINS || anchors.contains(anchorPos)) {
            return;
        }
        if (anchors.isEmpty()) {
            bindChunk = new ChunkPos(queen.blockPosition());
        }
        anchors.add(anchorPos.immutable());

        // Chaining a DOWNED queen is the capture race: every chain you fit is another roll that she comes round
        // in your hands. Waking here does not undo the chains already on her - she simply wakes up wearing them.
        if (queen.isIncapacitated()) {
            var chance = wakeChanceForChain(anchors.size());
            if (chance > 0.0 && queen.getRandom().nextDouble() < chance) {
                queen.getIncapacitationManager().healRescue();
            }
        }
    }

    /** Drop the chain from {@code anchorPos}. Clearing the last chain releases the bind chunk. */
    public void detach(BlockPos anchorPos) {
        anchors.remove(anchorPos);
        if (anchors.isEmpty()) {
            bindChunk = null;
        }
    }

    /**
     * Push each bound anchor's slot index to its block entity so the client knows which shackle bone its chain attaches
     * to. Re-run every tick (the setter is change-gated, so this is a no-op once stable); this keeps slots correct
     * across mid-list releases and world reload without persisting per-anchor render state.
     */
    private void resyncShackleSlots() {
        for (int i = 0; i < anchors.size(); i++) {
            if (queen.level().getBlockEntity(anchors.get(i)) instanceof AnchorBlockEntity anchor) {
                anchor.setShackleSlot(i);
            }
        }
    }

    // ---- break-on-attack (slice 2) ----

    /**
     * One break roll per attack the queen makes. While she can still fight (1-3 chains) each swing has a chance to snap
     * the most-recently-attached chain, letting her unravel one step toward freedom; at 4+ chains she is locked (0%)
     * and no longer attacks anyway. Server-only.
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
        breakNewestChain();
    }

    /** Snap the newest chain: the anchor block survives (just freed) and this bind drops, so she unravels one step. */
    private void breakNewestChain() {
        BlockPos newest = anchors.get(anchors.size() - 1);
        if (queen.level().getBlockEntity(newest) instanceof AnchorBlockEntity anchor) {
            anchor.release(); // clears the anchor's bind and detaches it here through the bind wiring
        } else {
            detach(newest); // fallback: the anchor block is already gone
        }
    }

    // ---- tick: restriction clamp (server-side) ----

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
            bindChunk = null;
            return;
        }
        if (bindChunk == null) {
            return;
        }
        applyRestrictionClamp();
    }

    /** Horizontal tether radius around the bind chunk's center, by current chain count. */
    private double tetherRadius() {
        return switch (anchors.size()) {
            case 1 -> 16.0;
            case 2 -> 10.0;
            case 3 -> 5.0;
            default -> 0.0; // 4+ chains: locked at center
        };
    }

    private void applyRestrictionClamp() {
        double radius = tetherRadius();
        double centerX = bindChunk.getMiddleBlockX() + 0.5;
        double centerZ = bindChunk.getMiddleBlockZ() + 0.5;
        double dx = queen.getX() - centerX;
        double dz = queen.getZ() - centerZ;
        double distSq = dx * dx + dz * dz;

        if (radius <= 0.0) {
            // Fully bound: pin to chunk center, preserving Y so she stays grounded.
            if (distSq > 1.0e-6) {
                queen.setPos(centerX, queen.getY(), centerZ);
                Vec3 v = queen.getDeltaMovement();
                queen.setDeltaMovement(0.0, v.y, 0.0);
                queen.hurtMarked = true;
            }
            return;
        }

        if (distSq > radius * radius) {
            double dist = Math.sqrt(distSq);
            double nx = dx / dist;
            double nz = dz / dist;
            queen.setPos(centerX + nx * radius, queen.getY(), centerZ + nz * radius);

            Vec3 v = queen.getDeltaMovement();
            double outward = v.x * nx + v.z * nz;
            if (outward > 0.0) {
                queen.setDeltaMovement(v.x - nx * outward, v.y, v.z - nz * outward);
            }
            queen.hurtMarked = true;
        }
    }

    // ---- persistence ----

    public void load(CompoundTag tag) {
        anchors.clear();
        bindChunk = null;
        if (tag.contains(TAG_ANCHORS)) {
            for (long packed : tag.getLongArray(TAG_ANCHORS)) {
                anchors.add(BlockPos.of(packed));
            }
        }
        if (tag.contains(TAG_BIND_CHUNK)) {
            bindChunk = new ChunkPos(tag.getLong(TAG_BIND_CHUNK));
        }
    }

    /**
     * Reconcile persisted chains against the world once, after load. An anchor broken while the queen was UNLOADED
     * never ran its release() -> detach() (that resolves the queen via getEntity, which returns null for an unloaded
     * entity), so she can reload still bound to an anchor block that no longer exists - and stay permanently clamped by
     * a phantom chain. Drop any anchor whose block is no longer a live AnchorBlockEntity bound to HER. Mirror of the
     * anchor-side self-heal in AnchorBlockEntity.serverTick.
     */
    public void onLoaded() {
        if (queen.level().isClientSide() || anchors.isEmpty()) {
            return;
        }
        var survivors = new java.util.ArrayList<BlockPos>(anchors.size());
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
            if (anchors.isEmpty()) {
                bindChunk = null;
            }
        }
    }

    public void save(CompoundTag tag) {
        if (anchors.isEmpty()) {
            return;
        }
        long[] packed = new long[anchors.size()];
        for (int i = 0; i < anchors.size(); i++) {
            packed[i] = anchors.get(i).asLong();
        }
        tag.putLongArray(TAG_ANCHORS, packed);
        if (bindChunk != null) {
            tag.putLong(TAG_BIND_CHUNK, bindChunk.toLong());
        }
    }
}
