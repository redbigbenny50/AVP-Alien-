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

    private static final String KIND_CLASSIFIER_VERSION_TAG = "avp_vent_kind_classifier_version";

    /**
     * Bump when the kind-classification rule changes in a way that already-persisted answers must be corrected. Version
     * 2 = the shelf-aware surface test: before it, every nether vent that went through classification was measured
     * against the bedrock ROOF heightmap and persisted as FRONTIER - which is why tester worlds had working, placed,
     * webbed vents that the party system refused to count as surface. The heal below re-runs classification ONCE per
     * vent in CEILED dimensions only; sky dimensions were classified correctly all along and their persisted (including
     * deliberately placed) kinds are never touched.
     */
    /**
     * How often a vent re-resolves which hive owns it.
     * <p>
     * ⚠ Not persisted and deliberately not synced - it is a throttle, not state. An unbound vent ignores it entirely.
     * </p>
     */
    private static final int BINDING_REFRESH_INTERVAL_TICKS = 20;

    /**
     * How often a vent with NO resolvable owner tries again.
     * <p>
     * ⚠ Longer than the bound interval on purpose: this is the path that runs findNearestInDim, and a vent left behind
     * by a dead hive will keep failing it forever. Five seconds is still fast enough that a vent inside a hive that is
     * being founded binds promptly.
     * </p>
     */
    private static final int UNBOUND_RETRY_INTERVAL_TICKS = 100;

    /** Consecutive failed lookups before a vent accepts that its hive is gone. */
    private static final int ORPHAN_STRIKES_BEFORE_DORMANT = 5;

    /**
     * How often a dormant, hiveless vent bothers to look again.
     * <p>
     * ⚠ Three minutes. Long enough that a graveyard of dead-hive vents costs effectively nothing, short enough that a
     * new hive founding on top of the ruins picks them up while its first workers are still digging.
     * </p>
     */
    private static final int DORMANT_RETRY_INTERVAL_TICKS = 20 * 180;

    private int orphanStrikes;

    private int ticksSinceBindingRefresh;

    private static final int KIND_CLASSIFIER_VERSION = 2;

    private int kindClassifierVersion = 0;

    /**
     * Deliberate placements carry an authoritative kind - stamp them current so the heal pass never re-derives them.
     */
    public void markKindCurrent() {
        kindClassifierVersion = KIND_CLASSIFIER_VERSION;
    }

    private static final String BOUND_TAG = "BoundLocationId";

    /** Extra chunks past a hive's territory radius that a legacy orphan vent may be adopted from. */
    private static final int LEGACY_ORPHAN_DRIFT_CHUNKS = 6;

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

        // 5s per vent. History: 3s originally, raised to 10s because the cry-for-help event fires on EVERY hit a
        // hive member takes and every vent within earshot answers independently, so vent-dense rooms (hubs run 40+
        // vents) filled with defenders the moment a player opened fire. 10s overcorrected — playtesters reported
        // too few aliens coming to the aid — so this sits at the midpoint. Vent density stays the difficulty dial;
        // this only caps how fast each individual vent can pump.
        this.alienSpawnCooldown = Cooldown.withCooldownTime("spawnAlienCooldown", Duration.ofSeconds(5));
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

    /** Bind this vent to its owning hive at placement, so ownership survives reloads and unclaimed ground. */
    public void setBoundLocation(@Nullable HiveLocationId locationId) {
        this.boundLocationId = locationId;
        setChanged();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag compoundTag, @NotNull HolderLookup.Provider provider) {
        super.saveAdditional(compoundTag, provider);
        if (kind != null) {
            compoundTag.putString(KIND_TAG, kind.name());
        }
        if (kindClassifierVersion > 0) {
            compoundTag.putInt(KIND_CLASSIFIER_VERSION_TAG, kindClassifierVersion);
        }
        // Persist the owning hive. Without this the binding was lost on every reload, forcing serverTick to
        // re-derive ownership from getByChunk - which only knows CLAIMED chunks, so a surface vent dropped on
        // open frontier ground could never be re-owned and never re-registered (host hunts saw no surface vent).
        if (boundLocationId != null) {
            compoundTag.putString(BOUND_TAG, boundLocationId.value().toString());
        }
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag compoundTag, @NotNull HolderLookup.Provider provider) {
        super.loadAdditional(compoundTag, provider);
        kind = compoundTag.contains(KIND_TAG) ? VentKind.byName(compoundTag.getString(KIND_TAG)) : null;
        kindClassifierVersion = compoundTag.contains(KIND_CLASSIFIER_VERSION_TAG)
            ? compoundTag.getInt(KIND_CLASSIFIER_VERSION_TAG)
            : 0;
        boundLocationId = compoundTag.contains(BOUND_TAG)
            ? HiveLocationId.of(net.minecraft.resources.ResourceLocation.parse(compoundTag.getString(BOUND_TAG)))
            : null;
    }

    public @Nullable HiveLocation getBoundLocation() {
        return boundLocationId == null ? null : HiveLocationRegistry.INSTANCE.get(boundLocationId);
    }

    public static void serverTick(Level level, BlockPos ventPos, BlockState blockState, ResinVentBlockEntity vent) {
        // The cooldown is the only thing here that genuinely needs twenty ticks a second.
        vent.alienSpawnCooldown.tick();

        // 🚨🚨 EVERYTHING BELOW IS A BINDING REFRESH, AND IT RAN EVERY TICK ON EVERY VENT. A crash report from a live
        // server showed 849 resin vents in one dimension - so this body ran roughly 17,000 times a SECOND, and each
        // pass does a chunk-map lookup, a faction lookup and a vent re-registration.
        //
        // ⚠⚠ AND AN UNBOUND VENT IS FAR WORSE: it calls findNearestInDim, which is a FOUR-DEEP nested loop over 64
        // rings - up to about a million iterations - once per vent per tick. A handful of orphaned vents alone could
        // dominate a tick.
        //
        // ⭐ A VENT'S OWNING HIVE ALMOST NEVER CHANGES. Refreshing once a second is indistinguishable in play and cuts
        // the work by twenty. An UNBOUND vent still resolves immediately, so a freshly placed or freshly loaded vent
        // binds on its first tick rather than waiting.
        // 🚨🚨 AN ORPHANED VENT IS THE EXPENSIVE CASE, NOT THE CHEAP ONE - THROTTLE IT HARDEST.
        //
        // ⚠⚠ WHEN A HIVE DIES ITS VENTS KEEP TICKING. They are ordinary block entities; nothing unregisters them. So
        // every one of them fails to resolve an owner and falls through to findNearestInDim - a FOUR-DEEP nested loop
        // over 64 rings, up to about a million iterations - and it would do that EVERY TICK, forever, for every vent
        // the dead hive left behind. A server with 849 vents that loses a large hive is doing hundreds of millions of
        // iterations a second for nothing.
        //
        // ⭐ SO THE UNBOUND CASE BACKS OFF FURTHEST. A bound vent re-checks once a second because its answer is cheap
        // and might change; an unbound one waits five, because its answer is expensive and almost certainly will not.
        // 🚨🚨 A VENT LEFT BY A DEAD HIVE GIVES UP EVENTUALLY. NOTHING REMOVES HIVE BLOCKS WHEN A LOCATION DIES - the
        // resin and the vents simply stay in the world - so without this every vent of every hive that has EVER died
        // keeps hunting for an owner for the rest of the world's life. That load only accumulates.
        //
        // ⭐ After ORPHAN_STRIKES_BEFORE_DORMANT consecutive failures it goes dormant and re-checks only every few
        // minutes, so a chunk that is later claimed by a NEW hive still adopts its vents - just not at the cost of
        // searching hundreds of times a minute in the meantime.
        var interval = vent.boundLocationId != null
            ? BINDING_REFRESH_INTERVAL_TICKS
            : vent.orphanStrikes >= ORPHAN_STRIKES_BEFORE_DORMANT
                ? DORMANT_RETRY_INTERVAL_TICKS
                : UNBOUND_RETRY_INTERVAL_TICKS;

        if (++vent.ticksSinceBindingRefresh < interval) {
            return;
        }

        vent.ticksSinceBindingRefresh = 0;

        var ventVariantTypeOption = AlienVariantTypes.getFor(blockState);
        if (ventVariantTypeOption.isNone()) {
            return;
        }
        var ventVariant = ventVariantTypeOption.unwrap().variant();

        // Re-resolve every tick: the chunk's owning location may have changed (claim transfer or death).
        var owningLocation = HiveLocationRegistry.INSTANCE.getByChunk(level.dimension(), new ChunkPos(ventPos));

        // getByChunk only knows CLAIMED chunks. A SURFACE vent is deliberately dropped on open frontier ground
        // the hive does not claim, so its chunk resolves to null even though the vent legitimately belongs to a
        // live hive. Fall back to the RECORDED owner (persisted since placement) rather than disowning it - that
        // false-disown is exactly why host hunts reported no surface vent despite one sitting in the open.
        if (owningLocation == null && vent.boundLocationId != null) {
            owningLocation = HiveLocationRegistry.INSTANCE.get(vent.boundLocationId);
        }

        // Recovery for vents placed BEFORE owner-persistence existed (or whose owner id was lost): a vent with a
        // stamped KIND is a real placed vent, so adopt the nearest same-variant hive within territory range and
        // write the owner back down. This heals existing worlds' orphaned surface vents without a reset; a fresh
        // vent is always already bound at placement, so this only ever runs for legacy orphans.
        if (owningLocation == null && vent.kind != null) {
            var nearest = HiveLocationRegistry.INSTANCE.findNearestInDim(level.dimension(), ventPos);
            if (nearest != null) {
                var config = HiveLocationRegistry.INSTANCE.config();
                // Territory radius plus a drift margin: surface parties roam a few chunks past the claimed
                // footprint before dropping a vent, so allow that much slack when adopting an orphan.
                var rangeChunks = config.maxTerritoryRadiusChunks() + LEGACY_ORPHAN_DRIFT_CHUNKS;
                var ventChunk = new ChunkPos(ventPos);
                var centerChunk = new ChunkPos(nearest.centerPos());
                var within = Math.max(
                    Math.abs(ventChunk.x - centerChunk.x),
                    Math.abs(ventChunk.z - centerChunk.z)
                ) <= rangeChunks;
                if (within && nearest.lineageVariantOrNull() == ventVariant) {
                    owningLocation = nearest;
                    vent.setBoundLocation(nearest.id());
                }
            }
        }

        if (owningLocation == null) {
            vent.orphanStrikes = Math.min(vent.orphanStrikes + 1, ORPHAN_STRIKES_BEFORE_DORMANT);

            // ⭐⭐ ITS HIVE IS GONE - GO DORMANT AND STOP TICKING ENTIRELY. Setting DORMANT makes getTicker return null,
            // so Minecraft drops this block entity from the ticking list altogether: the ruin stays in the world as
            // scenery and costs nothing at all.
            //
            // ⚠ FLAG 2 (clients only, no neighbour updates) - this is a state flip on a block nobody is interacting
            // with, and neighbour updates on hive blocks are exactly what filled a live server's scheduled-tick queue.
            if (
                vent.orphanStrikes >= ORPHAN_STRIKES_BEFORE_DORMANT
                    && level != null
                    && !blockState.getValue(com.alien.common.gameplay.block.resin.vent.ResinVentBlock.DORMANT)
            ) {
                level.setBlock(
                    ventPos,
                    blockState.setValue(com.alien.common.gameplay.block.resin.vent.ResinVentBlock.DORMANT, true),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS
                );
            }
            // Genuinely ownerless: neither a claimed chunk nor a live recorded owner. Drop the binding.
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
        vent.orphanStrikes = 0;

        if (vent.kind == null) {
            // Template-stamped, or from a world older than vent kinds. Work it out once and write it down, so nothing
            // downstream ever has to guess from geometry again.
            var config = HiveLocationRegistry.INSTANCE.config();
            vent.setKind(
                HiveVents.classifyUntagged(level, owningLocation, ventPos, config.surfacePartySurfaceBandBlocks())
            );
            vent.kindClassifierVersion = KIND_CLASSIFIER_VERSION;
        } else if (
            vent.kindClassifierVersion < KIND_CLASSIFIER_VERSION
                && level != null
                && level.dimensionType().hasCeiling()
        ) {
            // THE ONE-TIME HEAL for kinds poisoned by the roof-heightmap bug (see KIND_CLASSIFIER_VERSION): in a
            // ceiled dimension, re-derive the kind under the fixed shelf-aware rule and write it down again. A shelf
            // vent flips FRONTIER -> SURFACE right where it stands; structure and pocket vents re-derive to what
            // they already were. Runs once per vent, then the version stamp retires it forever.
            var config = HiveLocationRegistry.INSTANCE.config();
            vent.setKind(
                HiveVents.classifyUntagged(level, owningLocation, ventPos, config.surfacePartySurfaceBandBlocks())
            );
            vent.kindClassifierVersion = KIND_CLASSIFIER_VERSION;
            vent.setChanged();
        }
        if (vent.kindClassifierVersion < KIND_CLASSIFIER_VERSION) {
            // Sky dimension: the old rule was already correct there - just retire the heal check.
            vent.kindClassifierVersion = KIND_CLASSIFIER_VERSION;
            vent.setChanged();
        }

        owningLocation.ventManager().addVent(ventPos, vent.kind);
    }

    @Override
    public @NotNull CryForHelpListener getListener() {
        return cryForHelpListener;
    }
}
