package com.alien.common.gameplay.hive.containment;

import com.blib.api.common.storage.v1.DataStore;
import net.minecraft.nbt.CompoundTag;

/**
 * ⭐⭐⭐ WHEN A PLAYER WAS LAST IN THIS CHUNK. THE HALF OF CONTAINMENT DETECTION THAT MOVEMENT ALONE CANNOT SEE.
 * <p>
 * [stated] "could it also detect player position when it gets the stuck trigger that would seperate genueinly stuck vs
 * a pet or zoo is if a player has been in the surrounding chunks recently."
 * </p>
 * <p>
 * ⭐⭐ MOVEMENT ALONE IS AMBIGUOUS AND THIS RESOLVES IT. A xenomorph that has not left a small area for several minutes
 * is either somebody's exhibit in a 5x5x3 cell or a drone wedged in a ravine — the two look identical from the entity's
 * side. What separates them is whether anyone ever comes to look at it. A zoo is a place a player returns to; a ravine
 * is not.
 * </p>
 * <p>
 * ⚠⚠ THIS HANGS OFF THE CHUNK, NOT THE ENTITY, AND THAT IS THE WHOLE POINT. Entity-side tracking resets when the chunk
 * unloads — which happens precisely when nobody is around, so a rarely-visited zoo would forget it was a zoo at exactly
 * the moment the answer mattered. A chunk remembers that players frequent it even while empty.
 * </p>
 * <p>
 * ⚠ Game time, not wall clock: it must be comparable to every other timer in the hive systems, and it must not advance
 * while the world is closed.
 * </p>
 */
public class PlayerVisitDataStore implements DataStore {

    private static final String NBT_LAST_SEEN = "LastSeenGameTime";

    /**
     * ⚠ VOLATILE. Written from the server tick and read from the chunk-unload path, and C2ME moves chunk work onto
     * worker threads — BLib's own chunk store manager is fully concurrent for exactly that reason. A single long needs
     * no lock, but it does need to be visible across threads.
     */
    private volatile long lastSeenGameTime = Long.MIN_VALUE;

    /** Stamps a visit. Cheap enough to call from a per-tick player sweep. */
    public void stamp(long gameTime) {
        lastSeenGameTime = gameTime;
    }

    /**
     * Whether a player has been here within {@code withinTicks}.
     * <p>
     * ⚠ A chunk never visited returns false rather than "infinitely long ago", so the default answer is "nobody has
     * been here" — the safe direction, because it lets ordinary stuck-member cleanup keep working everywhere the
     * feature has no evidence.
     * </p>
     */
    public boolean visitedWithin(long gameTime, long withinTicks) {
        if (lastSeenGameTime == Long.MIN_VALUE) {
            return false;
        }
        if (gameTime - lastSeenGameTime > withinTicks) {
            // ⭐⭐ EXPIRE IT HERE. A stamp older than the window can never answer anything again, and clearing it makes
            // save() write an empty tag - which BLib's chunk store removes outright
            // ({@code if (storeTag.isEmpty()) chunkTag.remove(...)}), so the entry disappears from the region file
            // rather than sitting there forever.
            //
            // ⚠ Pruning at READ time is the only place with a game time to compare against - neither load() nor
            // save() knows one. It means a store in a chunk nothing ever asks about again is not pruned, which is
            // exactly why the hive-proximity filter in ContainmentDetector matters: together they bound the set.
            lastSeenGameTime = Long.MIN_VALUE;
            return false;
        }
        return true;
    }

    public long lastSeenGameTime() {
        return lastSeenGameTime;
    }

    @Override
    public void load(CompoundTag compoundTag) {
        lastSeenGameTime = compoundTag.contains(NBT_LAST_SEEN)
            ? compoundTag.getLong(NBT_LAST_SEEN)
            : Long.MIN_VALUE;
    }

    @Override
    public void save(CompoundTag compoundTag) {
        if (lastSeenGameTime != Long.MIN_VALUE) {
            compoundTag.putLong(NBT_LAST_SEEN, lastSeenGameTime);
        }
    }
}
