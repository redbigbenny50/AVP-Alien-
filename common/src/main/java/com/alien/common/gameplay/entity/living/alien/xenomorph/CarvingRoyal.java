package com.alien.common.gameplay.entity.living.alien.xenomorph;

import net.minecraft.world.entity.Mob;

/**
 * ⭐⭐⭐ A ROYAL THAT DIGS OUT HER OWN FOUNDING CHAMBER.
 * <p>
 * [stated] "remember the empress now carves too if shes forced in early. that was the entire point of this."
 * </p>
 * <p>
 * ⚠⚠ THIS IS WHY HER HORIZONTAL DIGGING CLIPS WERE DEAD. {@code CarveSiteWork} cast the founder straight to
 * {@code Queen} in four places, so an EMPRESS founder failed every one of those gates and simply fell through — no cast
 * match, no log line, no carving, and {@code digging.standing.start} / {@code digging.standing} /
 * {@code digging.standing.stop} could never fire no matter how well the descent worked.
 * </p>
 * <p>
 * ⚠ CAPTURE STATE IS ASKED, NOT ASSUMED. The old gate read {@code !isInhibited() && !getBindManager().isFullyBound()},
 * both of which are queen-only — the empress can be neither chained nor inhibited. So the question becomes
 * {@link #canCarve()} and each royal answers it her own way, rather than the carve system knowing about restraints.
 * </p>
 */
public interface CarvingRoyal {

    boolean isStandDigging();

    void setStandDigging(boolean value);

    /**
     * Whether she is free to carve right now.
     * <p>
     * ⚠ The queen consults her inhibitor and her chains. The empress has neither and always answers true.
     * </p>
     */
    boolean canCarve();

    /** Spawns the small crew that digs alongside her, so a founding does not stall on one royal. */
    void spawnFoundingCrewForCarve();

    /** ⚠ Lets CarveSiteWork move and navigate her without casting back to a concrete royal type. */
    Mob asMob();
}
