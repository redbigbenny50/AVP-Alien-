package com.alien.common.gameplay.hive.dimension;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * The End-style hive ruleset. Where {@link DimensionHiveProfiles} answers GEOMETRY questions (where is the surface, how
 * deep is deep), this class answers BEHAVIOR questions: may this hive build, expand, produce, decay? The End - floating
 * islands over void, nothing to dig, nothing to host - gets a hive that is a PLAYER-BUILT FORTRESS rather than a
 * self-growing empire: everything it has, the player supplied, and it stores that army in a reserve bank and defends
 * its island with it. Nothing simulates, nothing generates, nothing expands on its own.
 * <p>
 * SCOPE - the End itself, plus any modded dimension opted in via the {@code avp_alien:end_style_hive_dimensions}
 * dimension-type tag ("this vent system is only for the end and dimensions similar from other mods"). Overworld and
 * nether hives are completely unchanged; every rule here defaults to today's behavior outside end-style dimensions.
 * <p>
 * THE RULESET, one line each (full rationale in the design notes):
 * <ul>
 * <li>No building, digging, chambers, or carving. The one sanctioned exception is the VENT - workers place one per
 * chunk, once ever, and a player-placed vent of the hive's strain counts (that half already works: the vent block
 * entity binds itself to the claiming location and enforces the strain match).</li>
 * <li>No natural queen spawns, no expansion, no convoys of any type, no decay or siege attrition - claims are granted
 * whole at placement and are permanent while the hive lives.</li>
 * <li>No simulated growth and no economy. ANYTHING THAT ACCUMULATES UNUSED IS OFF: jelly production (royal and
 * scourge), biomass generation, firewall stability. Population comes only from spawn eggs, summons, and host-born
 * xenomorphs.</li>
 * <li>Banking is VENT-ONLY into ONE UNIVERSAL CAPLESS store per hive; only same-strain ADULTS bank; name-tagged
 * xenomorphs never do. No vent standing means no banking and a frozen (not lost) bank.</li>
 * <li>Queen death leaves a REGENT praetorian and a hive that persists on its bank; queenless members never despawn;
 * summoned royals take over; two queens coexist. A hive reduced to pure bank (no vents, no surface members) is culled
 * after {@link #CULL_GRACE_MILLIS} of real time unless reconnected.</li>
 * </ul>
 */
public final class EndStyleHiveRules {

    private EndStyleHiveRules() {}

    /**
     * Modded dimensions opt into End-style hives by adding their DIMENSION TYPE to this tag. The End itself is always
     * end-style, tag or no tag, so packs cannot accidentally opt it out.
     */
    public static final TagKey<DimensionType> END_STYLE_DIMENSIONS = TagKey.create(
        Registries.DIMENSION_TYPE,
        ResourceLocation.fromNamespaceAndPath("avp_alien", "end_style_hive_dimensions")
    );

    /** Active worker ceiling per End hive - the hive's entire visible labor force. */
    public static final int ACTIVE_WORKER_CAP = 10;

    /** [stated] "expand the slab higher and lower from the queen" - half-height of the vertical band. */
    public static final int VERTICAL_SLAB_HALF_HEIGHT = 32;

    /** Physical standing-egg ceiling around the queen. */
    public static final int STANDING_EGG_CAP = 20;

    /**
     * The cleanup rule: a hive reduced to PURE BANK - zero vents, zero surface members - survives this long in real
     * wall-clock time. Reconnect it (place a vent, or add active members) and the clock clears; let it sit and the hive
     * is culled and the territory drops. Seven real days: a raided player has a genuine week to come back and revive
     * the fortress, but abandonment is abandonment.
     */
    public static final long CULL_GRACE_MILLIS = 7L * 24L * 60L * 60L * 1000L;

    /**
     * Location-shaped convenience for the scan tasks, which iterate locations across dimensions: resolves the
     * location's level off the server and answers {@link #isEndStyle}. A location in an unloaded/unknown dimension
     * answers {@code false} - the scan then treats it exactly as it does today, which is the safe direction.
     */
    public static boolean isEndStyle(
        net.minecraft.server.MinecraftServer server,
        com.alien.common.gameplay.hive.location.HiveLocation location
    ) {
        var level = server.getLevel(location.dimension());
        return level != null && isEndStyle(level);
    }

    /** The single switch. Every rule below is phrased so that a {@code false} here means today's behavior. */
    public static boolean isEndStyle(ServerLevel level) {
        return level.dimension() == Level.END
            || level.dimensionTypeRegistration().is(END_STYLE_DIMENSIONS);
    }

    // ---- What an end-style hive may NOT do. Each gate reads as "true means blocked", so call sites stay honest ----

    /** Construction, carving, chambers, structure founding, catch-up building - all of it. */
    public static boolean forbidsConstruction(ServerLevel level) {
        return isEndStyle(level);
    }

    /** Surface parties, territory growth, daughter-hive founding, spread attempts - the player IS the expansion. */
    public static boolean forbidsExpansion(ServerLevel level) {
        return isEndStyle(level);
    }

    /** All four convoy types: raid, reinforcement, migration, rescue. Convoys path overland; the End is void. */
    public static boolean forbidsConvoys(ServerLevel level) {
        return isEndStyle(level);
    }

    /** Wild queens never take root here - hives are brought, not born. */
    public static boolean forbidsNaturalQueenSpawns(ServerLevel level) {
        return isEndStyle(level);
    }

    /**
     * The whole accumulator family: jelly production (royal and scourge), biomass generation, firewall stability,
     * reserve purchasing, queenless maturation. "Anything that accumulates we dont use would be turned off."
     */
    public static boolean forbidsEconomy(ServerLevel level) {
        return isEndStyle(level);
    }

    /** Population-pressure decay and siege attrition - a fixed footprint needs neither; claims are permanent. */
    public static boolean forbidsDecay(ServerLevel level) {
        return isEndStyle(level);
    }

    /**
     * The empress apex economy: rescues, the network budget, the self-reveal, and above all the dig-in levy - the levy
     * moves banked members between hives, and End banks are the PLAYER'S hand-fed property. Hard-gated here even though
     * its trigger chain is already dead without rescues, so no future change reopens it by accident.
     */
    public static boolean forbidsApexEconomy(ServerLevel level) {
        return isEndStyle(level);
    }

    /**
     * The nuked biome exists here too - it "would basically still be a nuclear fallout location" and irradiated
     * conversion applies in full - but its WEATHER DRESSING does not: no precipitation, no ash snow, no fallout mob
     * spawning.
     */
    public static boolean forbidsFalloutWeather(ServerLevel level) {
        return isEndStyle(level);
    }
}
