package com.alien.common.gameplay.hive.party;

import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.vent.VentKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Which vents a party may use as a door.
 * <p>
 * This used to ask the heightmap: a vent counted as "near-surface" if its Y was within a band of the terrain height of
 * ITS OWN column. That is not the same question as "can a party actually get in and out here" - a vent buried under a
 * hill is near the surface of the hill, and a carrier standing on open ground at y=3 would set off to walk to it at
 * y=-4, fail to path, and stand there holding a live host forever.
 * <p>
 * A vent's role is now recorded when it is placed ({@link VentKind}), so this just asks.
 * <ul>
 * <li><b>Host hunts</b> use SURFACE vents only, both to launch from and to carry a captive home to. The front door.</li>
 * <li><b>Biomass hunts and attack parties</b> use SURFACE or FRONTIER - anywhere that opens onto the world.</li>
 * <li><b>Nothing</b> launches from a STRUCTURE vent: those are the hive's internal ducts and lead nowhere outside.</li>
 * </ul>
 */
public final class PartyVentUtil {

    private PartyVentUtil() {}

    /** The hive's front doors: vents dropped in the open by surface parties. Host hunts use only these. */
    public static List<BlockPos> findSurfaceVents(ServerLevel level, HiveLocation location) {
        return new ArrayList<>(location.ventManager().ventsOfKind(VentKind.SURFACE));
    }

    /** Every vent that opens onto the outside world - the surface doors plus the cave-mouth outposts. */
    public static List<BlockPos> findPartyVents(ServerLevel level, HiveLocation location) {
        return new ArrayList<>(location.ventManager().ventsOfKind(VentKind.SURFACE, VentKind.FRONTIER));
    }

    /** Vents a defender may safely emerge from. Never FRONTIER - it would strand them out in a cave. */
    public static List<BlockPos> findDefenderVents(ServerLevel level, HiveLocation location) {
        return new ArrayList<>(location.ventManager().ventsOfKind(VentKind.STRUCTURE, VentKind.SURFACE));
    }
}