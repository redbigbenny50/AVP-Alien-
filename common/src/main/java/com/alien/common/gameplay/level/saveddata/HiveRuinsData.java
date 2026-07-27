package com.alien.common.gameplay.level.saveddata;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-dimension record of chunks a hive once BUILT in, and the Y band it built at, kept after the hive itself is gone.
 * <p>
 * <b>Why this exists.</b> {@code LocationRemovalHelper.remove} releases every claimed chunk and unregisters the
 * location, but it demolishes nothing: the resin, the chambers and the trophy walls stay standing. A hive that dies of
 * {@code natural_decay} - queen killed, population zero, no-contact timeout - or that migrates away therefore leaves a
 * fully built, sealed, pitch-dark ruin whose chunks no longer resolve to any {@code HiveLocation}. The natural-spawn
 * deny mixin used to bail at its {@code location == null} guard and hand that ruin straight back to the vanilla
 * spawner, which is what a dark 12-block-tall room is for. This index is the memory the registry deliberately throws
 * away, and it is what makes the deny hold over the WHOLE room - the bare stone the stamp never wrote, the bone-block
 * trophy plinths, everything - rather than only over the blocks that happen to be resin.
 * <p>
 * <b>Self-healing.</b> Worlds that already contain derelict hives have no recorded ruins, because those hives died
 * before this index existed. {@link #recordObservedBand(ChunkPos, int)} lets the spawn check backfill an entry the
 * first time it catches a spawn standing on hive material in an unrecorded chunk: one denial teaches the index the
 * chunk and its band, and from then on the whole band in that chunk is covered. No migration step, no world reset.
 * <p>
 * Entries are permanent by design. A ruin does not "heal" back into spawnable ground - the room is still there.
 */
public class HiveRuinsData extends SavedData {

    private static final String DATA_NAME = "hive_ruins_v1";

    private static final String NBT_CHUNKS = "ruinChunks";

    private static final String NBT_BANDS = "ruinBands";

    /**
     * Mirrors {@code HiveLocation.SLAB_HEIGHT}. Used only when reconstructing a band from an observed spawn Y, where
     * the floor is known and the ceiling is not.
     */
    private static final int RUIN_BAND_HEIGHT = 16;

    /** Mirrors {@code HiveLocation.SLAB_TOLERANCE} - the same small padding {@code withinSlab} applies. */
    private static final int RUIN_BAND_PADDING = 2;

    /** Packed chunk -> band. Bands are half-open [lo, hi), unioned when a chunk is recorded more than once. */
    private final Map<Long, Band> bandsByChunk = new HashMap<>();

    private HiveRuinsData() {}

    /** Half-open Y band [lo, hi) that a ruin occupies within one chunk. */
    private record Band(
        int lo,
        int hi
    ) {

        Band union(Band other) {
            return new Band(Math.min(lo, other.lo), Math.max(hi, other.hi));
        }

        boolean contains(int y) {
            return y >= lo && y < hi;
        }
    }

    /**
     * Records a chunk a dying location had BUILT into, using that location's own slab band. Call once per built chunk
     * as the location is removed - before the registry forgets it.
     *
     * @param floorY   the location's {@code hiveFloorY()}
     * @param ceilingY the location's {@code hiveCeilingY()}
     */
    public void recordBuiltChunk(ChunkPos chunk, int floorY, int ceilingY) {
        put(chunk, new Band(floorY - RUIN_BAND_PADDING, ceilingY + RUIN_BAND_PADDING));
    }

    /**
     * Backfills a chunk from a single observed floor Y - the self-healing path for hives that died before this index
     * existed. The observed Y is treated as the ruin's floor, since a natural spawn candidate stands on one.
     */
    public void recordObservedBand(ChunkPos chunk, int observedFloorY) {
        put(
            chunk,
            new Band(observedFloorY - RUIN_BAND_PADDING, observedFloorY + RUIN_BAND_HEIGHT + RUIN_BAND_PADDING)
        );
    }

    private void put(ChunkPos chunk, Band band) {
        var packed = chunk.toLong();
        var existing = bandsByChunk.get(packed);
        var merged = existing == null ? band : existing.union(band);
        if (merged.equals(existing)) {
            return;
        }
        bandsByChunk.put(packed, merged);
        setDirty();
    }

    /** True if this chunk holds a hive ruin whose band covers {@code y}. */
    public boolean isRuinedSlab(ChunkPos chunk, int y) {
        var band = bandsByChunk.get(chunk.toLong());
        return band != null && band.contains(y);
    }

    /** True if this chunk holds a hive ruin at any height - diagnostics only. */
    public boolean hasRuin(ChunkPos chunk) {
        return bandsByChunk.containsKey(chunk.toLong());
    }

    public int ruinChunkCount() {
        return bandsByChunk.size();
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag compoundTag, @NotNull HolderLookup.Provider provider) {
        var chunks = new long[bandsByChunk.size()];
        var bands = new int[bandsByChunk.size() * 2];

        var i = 0;
        for (var entry : bandsByChunk.entrySet()) {
            chunks[i] = entry.getKey();
            bands[i * 2] = entry.getValue().lo();
            bands[i * 2 + 1] = entry.getValue().hi();
            i++;
        }

        compoundTag.putLongArray(NBT_CHUNKS, chunks);
        compoundTag.putIntArray(NBT_BANDS, bands);

        return compoundTag;
    }

    public static HiveRuinsData load(CompoundTag compoundTag, HolderLookup.Provider provider) {
        var data = new HiveRuinsData();

        var chunks = compoundTag.getLongArray(NBT_CHUNKS);
        var bands = compoundTag.getIntArray(NBT_BANDS);

        for (var i = 0; i < chunks.length && i * 2 + 1 < bands.length; i++) {
            data.bandsByChunk.put(chunks[i], new Band(bands[i * 2], bands[i * 2 + 1]));
        }

        return data;
    }

    public static HiveRuinsData getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    private static Factory<HiveRuinsData> factory() {
        return new Factory<>(HiveRuinsData::new, HiveRuinsData::load, null);
    }
}
