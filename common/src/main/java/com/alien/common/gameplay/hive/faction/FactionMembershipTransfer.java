package com.alien.common.gameplay.hive.faction;

import com.alien.Alien;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.alien.common.gameplay.hive.id.HiveLocationIds;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.model.alien.variant.AlienVariant;
import com.blib.api.common.faction.v1.Faction;
import com.blib.api.common.faction.v1.FactionMember;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Carries hive faction membership (lineage + location tiers) across an
 * {@link com.blib.api.common.entity.v1.EntityTransitionUtil#transitionInto entity transition}, which mints a fresh UUID
 * and would otherwise silently drop the molting alien from every faction it belonged to.
 * <p>
 * Two-phase: snapshot the old entity's faction ids <em>before</em> calling {@code transitionInto}, then apply to the
 * new entity after a {@code Success} result. The snapshot is needed because {@code transitionInto} discards the old
 * entity internally before returning, which BLib treats as a member removal — so by the time the caller sees the
 * {@code Success}, the old UUID is no longer in any faction.
 *
 * <pre>{@code
 * var snapshot = FactionMembershipTransfer.snapshot(oldEntity);
 * var result = EntityTransitionUtil.transitionInto(oldEntity, newType);
 * if (result instanceof EntityTransitionResult.Success<?> success) {
 *     FactionMembershipTransfer.apply(snapshot, success.newEntity());
 * }
 * }</pre>
 */
public final class FactionMembershipTransfer {

    private FactionMembershipTransfer() {}

    /**
     * Returns the set of lineage + location faction ids the entity is currently a member of. Order is insertion-stable.
     */
    public static Set<ResourceLocation> snapshot(Entity entity) {
        var snapshot = new LinkedHashSet<ResourceLocation>();
        for (var factionId : Alien.MOD.factions().getFactionIds(entity.getUUID())) {
            if (LineageIds.isLineageId(factionId) || HiveLocationIds.isHiveLocationId(factionId)) {
                snapshot.add(factionId);
            }
        }
        return snapshot;
    }

    /**
     * Adds {@code newEntity} to every faction in the snapshot it isn't already a member of, skipping any whose variant
     * doesn't match the new entity's. Variant mismatch is expected on the {@code transitionIntoVariant} path (drone →
     * irradiated drone): the old lineage's variant no longer applies to the new entity, so its membership must be
     * dropped on the floor.
     */
    public static void apply(Set<ResourceLocation> snapshot, Entity newEntity) {
        var factions = Alien.MOD.factions();
        var member = FactionMember.entity(newEntity);

        // ⚠⚠⚠ LINEAGE FIRST, LOCATION SECOND. THE ORDER IS THE WHOLE FIX.
        // <p>
        // {@code LocationFactionData.onMemberAdded} evicts on the spot anyone who is not ALREADY in the location's
        // parent lineage faction: it computes {@code notInLineage = !lineageFaction.membership().hasMember(member)}
        // and calls {@code removeMember} immediately. So joining the LOCATION before the LINEAGE is self-defeating -
        // the entity is added and thrown straight back out, and the lineage join that follows arrives too late to
        // save it.
        // </p>
        // <p>
        // ⚠ AND THE SNAPSHOT IS A {@link Set}, SO THE OLD SINGLE LOOP RAN IN HASH ORDER — effectively random per
        // UUID. That is why this was intermittent rather than total, and why evolving a whole brood at once lit it
        // up: every molt runs this, so with enough transitions a good fraction land in the losing order and lose
        // their hive. The log signature is a run of
        // "Hive: evicting <uuid> ... variantMatch=true, inParentLineage=false" - variant fine, lineage missing,
        // which is precisely a location-before-lineage add and nothing else.
        // </p>
        addAll(snapshot, newEntity, factions, member, true);
        addAll(snapshot, newEntity, factions, member, false);
    }

    private static void addAll(
        Set<ResourceLocation> snapshot,
        Entity newEntity,
        com.blib.api.common.faction.v1.FactionManager factions,
        FactionMember member,
        boolean lineagePass
    ) {
        for (var factionId : snapshot) {
            if (LineageIds.isLineageId(factionId) != lineagePass) {
                continue;
            }

            var faction = factions.get(factionId);
            if (faction == null || faction.membership().hasMember(member)) {
                continue;
            }

            var factionVariant = variantOfFaction(faction);
            if (factionVariant != null && !FactionVariantPolicy.variantMatches(newEntity, factionVariant)) {
                continue;
            }

            faction.membership().addEntity(newEntity);
        }
    }

    private static @Nullable AlienVariant variantOfFaction(Faction<?> faction) {
        if (faction.data() instanceof LineageFactionData lineage) {
            return lineage.variant();
        }
        if (HiveLocationIds.isHiveLocationId(faction.id())) {
            var location = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(faction.id()));
            if (location == null) {
                return null;
            }
            var parent = Alien.MOD.factions().get(location.lineageFactionId());
            if (parent != null && parent.data() instanceof LineageFactionData parentLineage) {
                return parentLineage.variant();
            }
        }
        return null;
    }
}
