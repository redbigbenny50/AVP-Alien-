package com.alien.common.gameplay.level.saveddata;

import com.blib.api.common.spatial.v1.region.RegionPos;
import com.blib.api.common.time.v1.Cooldown;
import com.just.core.functional.option.Option;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks permanently blacklisted chunk spawn positions for queens using 128x128 chunk regions and BitSets.
 */
public class QueenSpawnChunkData extends SavedData {

    private static final String DATA_NAME = "queen_spawn_chunk_data_v2";

    private static final String NBT_REGIONS = "blacklistedRegions";

    private static final String NBT_SPAWN_COOLDOWN_IN_TICKS = "spawnCooldownInTicks";

    private static final String NBT_WILD_QUEEN_CHUNKS = "wildQueenChunks";

    private final Map<RegionPos, BitSet> regionChunkBits;

    private final Cooldown spawnCooldown;

    /** Chunks where a naturally spawned (wild) queen took root — used to keep wild queens far apart. */
    private final java.util.Set<Long> wildQueenChunks = new java.util.HashSet<>();

    private QueenSpawnChunkData() {
        this(Map.of());
    }

    private QueenSpawnChunkData(Map<RegionPos, BitSet> regionChunkBits) {
        this.regionChunkBits = new HashMap<>();
        this.regionChunkBits.putAll(regionChunkBits);
        this.spawnCooldown = Cooldown.withCooldownTime(NBT_SPAWN_COOLDOWN_IN_TICKS, Duration.ofMinutes(5));
    }

    public void tick() {
        spawnCooldown.tick();
    }

    public void addWildQueenChunk(ChunkPos pos) {
        wildQueenChunks.add(pos.toLong());
        setDirty();
    }

    /** True when no wild queen has taken root within {@code minChebyshevChunks} chunks of {@code pos}. */
    public boolean isFarFromWildQueenChunks(ChunkPos pos, int minChebyshevChunks) {
        for (var packed : wildQueenChunks) {
            var other = new ChunkPos(packed);
            if (Math.max(Math.abs(other.x - pos.x), Math.abs(other.z - pos.z)) < minChebyshevChunks) {
                return false;
            }
        }
        return true;
    }

    public void addChunkToBlacklist(BlockPos pos) {
        addChunkToBlacklist(new ChunkPos(pos));
    }

    public void addChunkToBlacklist(ChunkPos pos) {
        var region = RegionPos.fromChunkPos(pos);
        var bitSet = regionChunkBits.computeIfAbsent(region, k -> new BitSet(RegionPos.REGION_SIZE * RegionPos.REGION_SIZE));
        var bitIndex = bitIndexInRegion(pos.x, pos.z);

        bitSet.set(bitIndex);

        setDirty();
    }

    public boolean isChunkBlacklisted(BlockPos pos) {
        return isChunkBlacklisted(new ChunkPos(pos));
    }

    public boolean isChunkBlacklisted(ChunkPos pos) {
        var region = RegionPos.fromChunkPos(pos);
        var bitSet = regionChunkBits.get(region);

        if (bitSet == null) {
            return false;
        }

        var bitIndex = bitIndexInRegion(pos.x, pos.z);

        return bitSet.get(bitIndex);
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag compoundTag, @NotNull HolderLookup.Provider provider) {
        var regionsTag = new CompoundTag();

        for (var entry : regionChunkBits.entrySet()) {
            var region = entry.getKey();
            var bits = entry.getValue();
            var bytes = bits.toByteArray();
            var longs = toLongArray(bytes);

            regionsTag.put(Long.toString(region.toLong()), new LongArrayTag(longs));
        }

        compoundTag.put(NBT_REGIONS, regionsTag);
        spawnCooldown.save(compoundTag);
        compoundTag.putLongArray(NBT_WILD_QUEEN_CHUNKS, wildQueenChunks.stream().mapToLong(Long::longValue).toArray());

        return compoundTag;
    }

    public static QueenSpawnChunkData load(CompoundTag compoundTag, HolderLookup.Provider provider) {
        var regionMap = new HashMap<RegionPos, BitSet>();

        var regionsTag = compoundTag.getCompound(NBT_REGIONS);

        for (var key : regionsTag.getAllKeys()) {
            var packedKey = Long.parseLong(key);
            var region = RegionPos.fromLong(packedKey);

            var longs = regionsTag.getLongArray(key);
            var bytes = toByteArray(longs);
            var bitSet = BitSet.valueOf(bytes);

            regionMap.put(region, bitSet);
        }

        var queenSpawnChunkData = new QueenSpawnChunkData(regionMap);

        queenSpawnChunkData.spawnCooldown.load(compoundTag);

        for (var packed : compoundTag.getLongArray(NBT_WILD_QUEEN_CHUNKS)) {
            queenSpawnChunkData.wildQueenChunks.add(packed);
        }

        return queenSpawnChunkData;
    }

    public static Option<QueenSpawnChunkData> getOrCreate(Level level) {
        return level.isClientSide
            ? Option.none()
            : Option.some(
                ((ServerLevel) level).getDataStorage()
                    .computeIfAbsent(factory(level), DATA_NAME)
            );
    }

    private static Factory<QueenSpawnChunkData> factory(Level level) {
        return new Factory<>(QueenSpawnChunkData::new, QueenSpawnChunkData::load, null);
    }

    private static int bitIndexInRegion(int chunkX, int chunkZ) {
        var localX = chunkX & (RegionPos.REGION_SIZE - 1);
        var localZ = chunkZ & (RegionPos.REGION_SIZE - 1);
        return localZ * RegionPos.REGION_SIZE + localX;
    }

    private static long[] toLongArray(byte[] bytes) {
        var buffer = ByteBuffer.wrap(bytes);
        var len = (int) Math.ceil(bytes.length / 8.0);
        var result = new long[len];

        for (var i = 0; i < len && buffer.remaining() >= 8; i++) {
            result[i] = buffer.getLong();
        }

        if (buffer.remaining() > 0) {
            long last = 0;

            for (var i = 0; buffer.remaining() > 0; i++) {
                last |= ((long) buffer.get() & 0xFFL) << (8 * i);
            }

            result[len - 1] = last;
        }

        return result;
    }

    private static byte[] toByteArray(long[] longs) {
        var buffer = ByteBuffer.allocate(longs.length * Long.BYTES);

        for (var l : longs) {
            buffer.putLong(l);
        }

        return buffer.array();
    }

    public Cooldown getSpawnCooldown() {
        return spawnCooldown;
    }
}
