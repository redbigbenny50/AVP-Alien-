package com.alien.common.gameplay.hive.structure;

import com.alien.Alien;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The catalog of parsed hive structure pieces, loaded once per server from the hand-authored .nbt templates. This is
 * the assembler's menu: it turns the raw structure templates into queryable {@link HivePiece} records (footprint, typed
 * sockets, functional positions) so the planner can ask "which pieces can attach to this frontier socket?".
 * <p>
 * Loaded lazily on first access with a server (via {@link #get(MinecraftServer)}), because structure templates are only
 * available once the server's {@code StructureTemplateManager} exists. Cleared on server stop (wire in the same place
 * other hive registries clear).
 */
public final class HivePieceRegistry {

    public static final HivePieceRegistry INSTANCE = new HivePieceRegistry();

    private final Map<ResourceLocation, HivePiece> pieces = new HashMap<>();

    private boolean loaded;

    private HivePieceRegistry() {}

    /** Returns the registry, loading it from the server's structure templates on first call. */
    public static HivePieceRegistry get(MinecraftServer server) {
        if (!INSTANCE.loaded) {
            INSTANCE.load(server);
        }
        return INSTANCE;
    }

    /**
     * Parses every catalog piece from the server's structure templates into {@link HivePiece} records. Missing or
     * unreadable templates are logged and skipped (the rest still load), so one bad piece never breaks the catalog.
     */
    public void load(MinecraftServer server) {
        pieces.clear();
        var manager = server.getStructureManager();
        int ok = 0;
        int missing = 0;
        for (var id : HivePieceCatalog.all()) {
            var templateOpt = manager.get(id);
            if (templateOpt.isEmpty()) {
                Alien.LOGGER.warn("Hive piece template not found: {}", id);
                missing++;
                continue;
            }
            try {
                StructureTemplate template = templateOpt.get();
                CompoundTag nbt = template.save(new CompoundTag());
                HivePiece piece = HivePieceParser.parse(id, nbt);
                pieces.put(id, piece);
                ok++;
            } catch (Exception e) {
                Alien.LOGGER.error("Failed to parse hive piece {}: {}", id, e.toString());
            }
        }
        loaded = true;
        Alien.LOGGER.info("Hive piece registry loaded: {} pieces ({} missing)", ok, missing);
    }

    /** The parsed piece for an id, or null if it wasn't loaded. */
    public HivePiece get(ResourceLocation id) {
        return pieces.get(id);
    }

    /** All loaded pieces (read-only). */
    public Map<ResourceLocation, HivePiece> all() {
        return Collections.unmodifiableMap(pieces);
    }

    /** All loaded pieces that expose at least one socket of the given door type (in their authored orientation). */
    public List<HivePiece> piecesWithDoorType(String doorType) {
        return pieces.values()
            .stream()
            // The queen chamber is the founding seed only - it must never be selected as a growth piece, or the
            // planner would stamp a second core off an open royal socket.
            .filter(p -> !p.id().equals(HivePieceCatalog.QUEEN_CHAMBER))
            .filter(p -> p.hasSocketType(doorType))
            .toList();
    }

    public boolean isLoaded() {
        return loaded;
    }

    /** Drops all loaded pieces. Call on server stop; the next {@link #get(MinecraftServer)} reloads. */
    public void clear() {
        pieces.clear();
        loaded = false;
    }
}
