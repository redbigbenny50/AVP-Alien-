package com.alien.common.gameplay.block.entity.resin.vent;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.vent.HiveVents;
import com.alien.common.gameplay.hive.vent.VentKind;
import com.alien.common.gameplay.level.gameevent.listener.CryForHelpListener;
import com.alien.common.registry.init.AlienBlockEntityTypes;
import com.blib.api.common.time.v1.Cooldown;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEventListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/**
 * Vent block entity. In hive this binds to a {@link HiveLocation} (not a faction) — the location's chunk-claim set is
 * the source of truth for "which hive owns this vent." Each tick we re-resolve which location currently owns this
 * vent's chunk. If that location's variant matches the vent's variant, the vent registers itself with the location's
 * {@link com.alien.common.gameplay.hive.vent.HiveVentManager} so AI queries can find it.
 */
public class ResinVentBlockEntity extends BlockEntity implements GameEventListener.Provider<CryForHelpListener> {

    private static final String KIND_TAG = "avp_vent_kind";

    private final Cooldown alienSpawnCooldown;

    private final CryForHelpListener cryForHelpListener;

    private @Nullable HiveLocationId boundLocationId;

    /**
     * What this vent is FOR. Null until classified: template-stamped structure vents never carry one from placement,
     * and neither does any vent from a world that predates vent kinds. Both are resolved on first tick and persisted.
     */
    private @Nullable VentKind kind;

    public ResinVentBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(AlienBlockEntityTypes.RESIN_VENT.get(), blockPos, blockState);

        var positionSource = new BlockPositionSource(blockPos);

        this.alienSpawnCooldown = Cooldown.withCooldownTime("spawnAlienCooldown", Duration.ofSeconds(3));
        this.cryForHelpListener = new CryForHelpListener(positionSource);
    }

    public Cooldown getAlienSpawnCooldown() {
        return alienSpawnCooldown;
    }

    public @Nullable HiveLocationId getBoundLocationId() {
        return boundLocationId;
    }

    public @Nullable VentKind getKind() {
        return kind;
    }

    /** Stamp a vent with its role at placement time (surface parties and frontier builders both do this). */
    public void setKind(VentKind kind) {
        this.kind = kind;
        setChanged();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag compoundTag, @NotNull HolderLookup.Provider provider) {
        super.saveAdditional(compoundTag, provider);
        if (kind != null) {
            compoundTag.putString(KIND_TAG, kind.name());
        }
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag compoundTag, @NotNull HolderLookup.Provider provider) {
        super.loadAdditional(compoundTag, provider);
        kind = compoundTag.contains(KIND_TAG) ? VentKind.byName(compoundTag.getString(KIND_TAG)) : null;
    }

    public @Nullable HiveLocation getBoundLocation() {
        return boundLocationId == null ? null : HiveLocationRegistry.INSTANCE.get(boundLocationId);
    }

    public static void serverTick(Level level, BlockPos ventPos, BlockState blockState, ResinVentBlockEntity vent) {
        vent.alienSpawnCooldown.tick();

        var ventVariantTypeOption = AlienVariantTypes.getFor(blockState);
        if (ventVariantTypeOption.isNone()) {
            return;
        }
        var ventVariant = ventVariantTypeOption.unwrap().variant();

        // Re-resolve every tick: the chunk's owning location may have changed (claim transfer or death).
        var owningLocation = HiveLocationRegistry.INSTANCE.getByChunk(level.dimension(), new ChunkPos(ventPos));

        if (owningLocation == null) {
            // No location owns this chunk anymore. Drop the binding.
            if (vent.boundLocationId != null) {
                vent.boundLocationId = null;
            }
            return;
        }

        // The owning location's lineage variant must match the vent's variant — otherwise this vent isn't part of
        // that hive's network (e.g., a normal-variant vent inside a normal lineage's territory after the chunk was
        // contested away from an aberrant lineage).
        var lineageFaction = com.alien.Alien.MOD.factions().get(owningLocation.lineageFactionId());
        if (
                lineageFaction == null
                        || !(lineageFaction.data() instanceof com.alien.common.gameplay.hive.faction.LineageFactionData lineage)
                        || lineage.variant() != ventVariant
        ) {
            // Variant mismatch — disown.
            if (vent.boundLocationId != null) {
                vent.boundLocationId = null;
            }
            return;
        }

        // Bound to this location. Register the vent in its vent manager.
        vent.boundLocationId = owningLocation.id();

        if (vent.kind == null) {
            // Template-stamped, or from a world older than vent kinds. Work it out once and write it down, so nothing
            // downstream ever has to guess from geometry again.
            var config = HiveLocationRegistry.INSTANCE.config();
            vent.setKind(
                    HiveVents.classifyUntagged(level, owningLocation, ventPos, config.surfacePartySurfaceBandBlocks())
            );
        }

        owningLocation.ventManager().addVent(ventPos, vent.kind);
    }

    @Override
    public @NotNull CryForHelpListener getListener() {
        return cryForHelpListener;
    }
}