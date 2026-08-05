package com.alien.common.gameplay.entity.living.alien.xenomorph.queen;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.lifecycle.QueenInhibitionService;
import com.alien.common.registry.init.item.AlienItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The rescue response the testers went looking for and found missing: when a queen is CHAINED, her own strain answers.
 * Same-strain adult xenomorphs within earshot converge on her capture anchors and break the chains, anchor by anchor,
 * until she stands free. Field report July 25: a chained, inhibited queen was killed by summoned drones of her own
 * kind, who never lifted a claw to free her - the kill is fixed by the kin-mercy rule in {@code AlienPredicates}; this
 * manager builds the rescue itself.
 * <p>
 * Owned and ticked by the bound queen herself (she is the center of the event and the one entity guaranteed loaded
 * while it matters). Design:
 * </p>
 * <ul>
 * <li><b>Trigger:</b> any chain on her ({@code isBound}). A lone wild queen with no kin in range is rescued by nobody -
 * capturing a queen is safe exactly as far as her family is away.</li>
 * <li><b>Recruits:</b> up to {@value #MAX_RESCUERS} same-strain adult xenomorphs within {@value #RECRUIT_RADIUS}
 * blocks. Queens and empresses do not leave their thrones for it; rival strains do not care.</li>
 * <li><b>The work:</b> rescuers are steered to the OLDEST anchor; standing beside it they claw it for
 * {@value #ANCHOR_BREAK_TICKS} ticks, then the anchor block is destroyed (dropping its item - the captor can recover
 * the hardware). {@code AnchorBlockEntity}'s removal path releases the chain, the bind count falls, and the crew moves
 * to the next anchor until she is free.</li>
 * </ul>
 */
public final class QueenRescueManager {

    private static final int RUN_INTERVAL_TICKS = 10;

    private static final int RECRUIT_RADIUS = 48;

    private static final int MAX_RESCUERS = 3;

    /** Claw time per anchor (all rescuers pool onto one anchor; presence, not head-count, sets the pace). */
    private static final int ANCHOR_BREAK_TICKS = 60;

    private static final double BREAK_REACH = 2.6;

    private final Queen queen;

    private final List<UUID> rescuers = new ArrayList<>();

    /**
     * RESCUE DEBT: rescuers remembered after the last chain drops. Lineage-less rescuers become her RETINUE - they join
     * her location on the spot if she has one; otherwise they escort her while she relocates (the locating dig happens
     * through solid rock, so once she tunnels they wait at the mouth) and APPEAR at her new hive the moment she founds,
     * joining it. PERSISTED with the queen ({@link #save}/{@link #load}) so a save/reload mid-relocation keeps the
     * debt: a member missing from the level is treated as UNLOADED, not dead - it is skipped, never purged, and joins
     * whenever it resolves again. The debt expires {@value #RETINUE_DEADLINE_TICKS} loaded-ticks after it formed
     * (persisted countdown), so a retinue whose members are truly gone disbands instead of lingering forever.
     */
    private final List<UUID> retinue = new ArrayList<>();

    /** Loaded-ticks of debt remaining; counts down only while the queen ticks, so unloaded time costs nothing. */
    private int retinueRemainingTicks;

    private static final int RETINUE_DEADLINE_TICKS = 36000;

    private static final String RETINUE_TAG = "RescueRetinue";

    private static final String RETINUE_TICKS_TAG = "RescueRetinueRemainingTicks";

    private boolean wasChained;

    private int breakProgressTicks;

    private BlockPos breakingAnchor;

    public QueenRescueManager(Queen queen) {
        this.queen = queen;
    }

    public void tick() {
        if (queen.level().isClientSide || queen.tickCount % RUN_INTERVAL_TICKS != 0) {
            return;
        }
        if (!(queen.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        var bindManager = queen.getBindManager();
        var chained = bindManager.hasAnyChain() && !bindManager.anchors().isEmpty();
        var helpless = chained || queen.isIncapacitated();
        if (!helpless) {
            if (wasChained) {
                // She just stood free (chains broken, or she recovered from the down) with a crew standing by:
                // settle who belongs to whom.
                wasChained = false;
                resolveFreedom(serverLevel);
            }
            tickRetinue(serverLevel);
            return;
        }
        wasChained = true;

        if (!chained) {
            // DOWNED, not chained: there is no anchor to claw, so kin stand GUARD instead - they converge on her
            // and hold close while she recovers; their ordinary combat AI deals with whatever downed her. The same
            // reconciliation runs when she stands.
            recruit(serverLevel);
            for (var iterator = rescuers.iterator(); iterator.hasNext();) {
                var guard = resolve(serverLevel, iterator, iterator.next());
                if (guard == null) {
                    continue;
                }
                if (guard.distanceToSqr(queen) > 36) {
                    guard.getNavigation().moveTo(queen, 1.15);
                } else {
                    guard.getNavigation().stop();
                }
            }
            return;
        }

        var targetAnchor = bindManager.anchors().get(0);
        if (!targetAnchor.equals(breakingAnchor)) {
            breakingAnchor = targetAnchor;
            breakProgressTicks = 0;
        }

        recruit(serverLevel);

        var anyAdjacent = false;
        for (var iterator = rescuers.iterator(); iterator.hasNext();) {
            var rescuer = resolve(serverLevel, iterator, iterator.next());
            if (rescuer == null) {
                continue;
            }

            if (rescuer.blockPosition().distSqr(targetAnchor) > BREAK_REACH * BREAK_REACH) {
                rescuer.getNavigation()
                    .moveTo(
                        targetAnchor.getX() + 0.5,
                        targetAnchor.getY(),
                        targetAnchor.getZ() + 0.5,
                        1.15
                    );
                continue;
            }

            rescuer.getNavigation().stop();
            rescuer.swing(InteractionHand.MAIN_HAND);
            anyAdjacent = true;
        }

        if (!anyAdjacent) {
            return;
        }

        breakProgressTicks += RUN_INTERVAL_TICKS;
        if (breakProgressTicks < ANCHOR_BREAK_TICKS) {
            return;
        }

        // Chain comes off: drop the anchor hardware for the captor to recover, and let the block-entity removal
        // path fire release() - the single choke point every chain detach flows through.
        serverLevel.destroyBlock(targetAnchor, true);
        breakingAnchor = null;
        breakProgressTicks = 0;
        com.alien.Alien.LOGGER.info(
            "Queen rescue: kin broke a capture anchor at {} freeing queen {} ({} chains remain)",
            targetAnchor,
            queen.getId(),
            queen.getBindManager().chainCount()
        );
    }

    /**
     * The social contract of a rescue, settled the moment she stands free:
     * <ul>
     * <li><b>Rescuers hold a lineage:</b> the queen joins THEIR lineage as a daughter queen (adoption recipe, cap
     * enforced). If the lineage is full, the rescuers simply go home (their own hive AI takes them) and she strikes out
     * to locate and found a fresh lineage of her own.</li>
     * <li><b>Rescuers are strays:</b> they join HER - into her location immediately if she has one, otherwise as her
     * remembered retinue: they escort her, and when she founds they appear at the new hive and join it.</li>
     * </ul>
     */
    private void resolveFreedom(ServerLevel serverLevel) {
        // Freedom means ALL of it. Breaking her chains or waking her while she still wore the inhibitor left her
        // standing, autonomous in name only - the device was only ever removed by a player prying it off with a
        // blade. Her kin tear it off the same way they tore the anchors out, and it drops where she stood so it stays
        // recoverable, exactly as the pry-off does.
        if (queen.isInhibited()) {
            queen.setInhibited(false);
            QueenInhibitionService.onReleased(serverLevel, queen);
            queen.spawnAtLocation(AlienItems.INHIBITOR.get());
        }

        var survivors = new ArrayList<Xenomorph>();
        for (var id : rescuers) {
            if (serverLevel.getEntity(id) instanceof Xenomorph xenomorph && xenomorph.isAlive()) {
                survivors.add(xenomorph);
            }
        }
        rescuers.clear();
        breakingAnchor = null;
        breakProgressTicks = 0;
        if (survivors.isEmpty()) {
            return;
        }

        // A queen who already holds a hive keeps it - she absorbs stray helpers and thanks lineage-bound ones by
        // letting them go home; adoption is only on the table for a queen with nothing.
        var standingLocation = locationOf(queen);
        if (standingLocation != null) {
            var joined = 0;
            for (var rescuer : survivors) {
                if (locationOf(rescuer) == null) {
                    com.alien.common.gameplay.hive.faction.LocationMembership.join(standingLocation, rescuer);
                    joined++;
                }
            }
            if (joined > 0) {
                com.alien.Alien.LOGGER.info(
                    "Queen rescue: {} stray helper(s) joined queen {}'s standing hive",
                    joined,
                    queen.getId()
                );
            }
            return;
        }

        // Path B: any rescuer with a lineage offers her adoption (first found wins - a same-strain crew from
        // multiple different lineages is exotic enough to not deserve a tiebreak ceremony).
        for (var rescuer : survivors) {
            var rescuerLocation = locationOf(rescuer);
            if (rescuerLocation == null) {
                continue;
            }
            if (queen.getLifecyclePhaseManager().tryAdoptIntoLineage(rescuerLocation)) {
                com.alien.Alien.LOGGER.info(
                    "Queen rescue: freed queen {} joins her rescuers' lineage as a daughter queen",
                    queen.getId()
                );
            } else {
                // Lineage cap full: the crew goes home on its own; she founds her own line.
                queen.getLifecyclePhaseManager().beginFreedRelocation();
                com.alien.Alien.LOGGER.info(
                    "Queen rescue: rescuers' lineage is at capacity — freed queen {} strikes out on her own",
                    queen.getId()
                );
            }
            return;
        }

        // Path A: an all-stray crew and a queen with nothing - they become her retinue for the founding ahead.
        for (var rescuer : survivors) {
            retinue.add(rescuer.getUUID());
        }
        retinueRemainingTicks = RETINUE_DEADLINE_TICKS;
        queen.getLifecyclePhaseManager().beginFreedRelocation();
        com.alien.Alien.LOGGER.info(
            "Queen rescue: freed queen {} relocating with a retinue of {} — they will join her founding",
            queen.getId(),
            retinue.size()
        );
    }

    /**
     * Escort while she travels; the APPEARANCE at her founding: teleport-in and join the moment she holds a location.
     */
    private void tickRetinue(ServerLevel serverLevel) {
        if (retinue.isEmpty()) {
            return;
        }
        retinueRemainingTicks -= RUN_INTERVAL_TICKS;
        if (retinueRemainingTicks <= 0) {
            com.alien.Alien.LOGGER.info(
                "Queen rescue: retinue of freed queen {} disbanded — the debt expired before her founding",
                queen.getId()
            );
            retinue.clear();
            return;
        }
        var queenLocation = locationOf(queen);
        for (var iterator = retinue.iterator(); iterator.hasNext();) {
            var id = iterator.next();
            // A missing member is UNLOADED, not dead - the debt survives chunk churn and relogs; only a body
            // confirmed dead is struck from the list.
            var entity = serverLevel.getEntity(id);
            if (entity == null) {
                continue;
            }
            if (!(entity instanceof Xenomorph member) || !member.isAlive()) {
                iterator.remove();
                continue;
            }
            if (queenLocation != null) {
                var home = queenLocation.centerPos();
                member.teleportTo(home.getX() + 0.5, home.getY() + 1.0, home.getZ() + 0.5);
                com.alien.common.gameplay.hive.faction.LocationMembership.join(queenLocation, member);
                iterator.remove();
                continue;
            }
            if (member.distanceToSqr(queen) > 64) {
                member.getNavigation().moveTo(queen, 1.1);
            }
        }
        if (queenLocation != null && retinue.isEmpty()) {
            com.alien.Alien.LOGGER.info("Queen rescue: retinue arrived at freed queen {}'s new hive", queen.getId());
        }
    }

    /** The alien's current hive location, resolved the same way the territory war system does. */
    private static com.alien.common.gameplay.hive.location.HiveLocation locationOf(LivingEntity entity) {
        for (var factionId : com.alien.Alien.MOD.factions().getFactionIds(entity.getUUID())) {
            if (com.alien.common.gameplay.hive.id.HiveLocationIds.isHiveLocationId(factionId)) {
                var location = com.alien.common.gameplay.hive.location.HiveLocationRegistry.INSTANCE
                    .get(com.alien.common.gameplay.hive.id.HiveLocationId.of(factionId));
                if (location != null) {
                    return location;
                }
            }
        }
        return null;
    }

    public void save(net.minecraft.nbt.CompoundTag tag) {
        if (retinue.isEmpty()) {
            return;
        }
        var list = new net.minecraft.nbt.ListTag();
        for (var id : retinue) {
            list.add(net.minecraft.nbt.NbtUtils.createUUID(id));
        }
        tag.put(RETINUE_TAG, list);
        tag.putInt(RETINUE_TICKS_TAG, retinueRemainingTicks);
    }

    public void load(net.minecraft.nbt.CompoundTag tag) {
        retinue.clear();
        if (!tag.contains(RETINUE_TAG)) {
            return;
        }
        for (var entry : tag.getList(RETINUE_TAG, net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            retinue.add(net.minecraft.nbt.NbtUtils.loadUUID(entry));
        }
        retinueRemainingTicks = tag.contains(RETINUE_TICKS_TAG)
            ? tag.getInt(RETINUE_TICKS_TAG)
            : RETINUE_DEADLINE_TICKS;
    }

    private void recruit(ServerLevel serverLevel) {
        rescuers.removeIf(id -> {
            var entity = serverLevel.getEntity(id);
            return !(entity instanceof Xenomorph xenomorph) || !xenomorph.isAlive();
        });
        if (rescuers.size() >= MAX_RESCUERS) {
            return;
        }

        var box = new AABB(queen.blockPosition()).inflate(RECRUIT_RADIUS);
        for (var candidate : serverLevel.getEntitiesOfClass(Xenomorph.class, box, this::isEligibleRescuer)) {
            if (rescuers.size() >= MAX_RESCUERS) {
                break;
            }
            if (!rescuers.contains(candidate.getUUID())) {
                rescuers.add(candidate.getUUID());
            }
        }
    }

    private boolean isEligibleRescuer(Xenomorph candidate) {
        return candidate.isAlive()
            && candidate != queen
            // Queens and empresses hold their thrones; the workers do the freeing.
            && !(candidate instanceof Queen)
            // Her own strain only - rival strains would sooner finish her (and are allowed to).
            && java.util.Objects.equals(candidate.getVariant(), queen.getVariant());
    }

    private Xenomorph resolve(ServerLevel serverLevel, java.util.Iterator<UUID> iterator, UUID id) {
        var entity = serverLevel.getEntity(id);
        if (entity instanceof Xenomorph xenomorph && xenomorph.isAlive()) {
            return xenomorph;
        }
        iterator.remove();
        return null;
    }

}
