package com.alien.common.gameplay.entity.living.alien.xenomorph.ascension;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.faction.HiveMemberLocationResolver;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.model.lifecycle.growth.CocooningConfig;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3f;

/**
 * ⭐⭐⭐ AN ISOLATED ROYAL CROWNS ITSELF.
 * <p>
 * [stated] "i want to make standalone praetorians have a way of becoming queens ... basically if they are isolated and
 * not part of a hive so situations where they are summoned or egg spawned in outside of a hives territory so they arent
 * adopted ... i said praetorian but this also applies to crushers ... they arent adopted or become part of an existing
 * hive within 3 minecraft days they begin to build up jelly in an internal bank tied to their nbt. This generates jelly
 * twice as long as a queen ... The purpose of this bank is to reach 50 jelly inwhich case they will become a queen."
 * </p>
 * <p>
 * ⭐⭐ THIS IS A SELF-FUNDED MIRROR OF {@code QueenPromotionService}, DELIBERATELY, DOWN TO THE DETAILS — same caste
 * preference (praetorian, else crusher), same 50-jelly price ({@code queenPromotionJellyCost}), and the same ROYAL
 * COCOON to finish it. A hive raises a daughter by spending its vats; a lone royal with no hive spends itself. The only
 * difference is where the jelly comes from, which is why it reads as the same event to anyone watching.
 * </p>
 * <p>
 * ⚠ NOTHING IS NEEDED AFTER THE COCOON. A queen with no lineage drops straight into the ordinary wild-queen lifecycle —
 * locate, dig, found — which is the same path a naturally spawned wild queen already takes.
 * </p>
 */
public final class IsolatedRoyalAscension {

    private static final String NBT_ISOLATED_TICKS = "IsolatedRoyalTicks";

    private static final String NBT_BANKED_JELLY = "IsolatedRoyalJelly";

    /** [stated] "within 3 minecraft days". One MC day is 24000 ticks. */
    private static final long ISOLATION_REQUIRED_TICKS = 3L * 24_000L;

    /**
     * [stated] "This generates jelly twice as long as a queen."
     * <p>
     * A queen's hive produces one royal jelly per {@code royalJellyTicksPerProduction}, which is 1200 ticks — one game
     * minute. Twice as long is 2400. ⚠ Read off the config at runtime rather than hardcoded, so retuning the queen
     * retunes this with her and the "half a queen" relationship can never quietly drift.
     * </p>
     */
    private static final int SLOWER_THAN_A_QUEEN = 2;

    /** Ticks between checks. The whole thing is measured in MC days, so a half-second cadence is ample. */
    private static final int SCAN_INTERVAL_TICKS = 10;

    /** How long the crowning cocoon takes, matched to the hive's own daughter molt. */
    private static final int COCOON_TICKS = 600;

    private static final Vector3f JELLY_PARTICLE_COLOUR = new Vector3f(0.36F, 0.85F, 0.30F);

    private static final float JELLY_PARTICLE_SCALE = 1.1F;

    private final Xenomorph royal;

    private long isolatedTicks;

    private int bankedJelly;

    public IsolatedRoyalAscension(Xenomorph royal) {
        this.royal = royal;
    }

    public void tick() {
        if (!(royal.level() instanceof ServerLevel serverLevel) || !royal.isAlive()) {
            return;
        }
        if (royal.tickCount % SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        if (!isEligibleCaste()) {
            return;
        }

        // ⭐⭐ POISON JELLY FREEZES THE WHOLE THING - [stated] "if poison jelly is used on it then this process
        // never begins because poison jelly stops and caste change from occuring."
        //
        // ⚠⚠ isPoisoned IS PERMANENT, NOT A TIMED DOSE. Nothing expires it - [stated] "if its poisoned its
        // permanant unless you use metamorphosis to remove the gate" - and MetamorphosisStatusEffect is the only
        // thing anywhere that calls setPoisoned(false). So in practice this is a PERMANENT SEAL that a player can
        // deliberately break, not a delay to wait out.
        //
        // It still PAUSES rather than cancels, and that is what makes the cure meaningful: the clock stops, no jelly
        // accrues, no particles, and it cannot crown - but the BANK SURVIVES, so a royal cured years later resumes
        // from the jelly it had rather than starting over. Same rule as being adopted and later orphaned again.
        //
        // Same flag and same phrasing GrowthManager.canNeverGrow already uses, so the suppression potion means ONE
        // thing everywhere: no caste change, by any route.
        if (royal.isPoisoned()) {
            return;
        }

        if (!isIsolated()) {
            // Taken in. The clock stops, but the BANK IS KEPT: a royal that is orphaned again resumes where it left
            // off rather than starting over, and the jelly it made was still made.
            isolatedTicks = 0L;
            return;
        }

        isolatedTicks += SCAN_INTERVAL_TICKS;
        if (isolatedTicks < ISOLATION_REQUIRED_TICKS) {
            return;
        }

        var config = HiveLocationRegistry.INSTANCE.config();
        var ticksPerJelly = Math.max(1L, config.royalJellyTicksPerProduction() * SLOWER_THAN_A_QUEEN);
        var cost = config.queenPromotionJellyCost();

        // Elapsed-since-threshold, so the bank is a pure function of how long it has been alone. No accumulator to
        // drift, and a chunk that unloads mid-wait resumes exactly where it stopped.
        var earned = (int) ((isolatedTicks - ISOLATION_REQUIRED_TICKS) / ticksPerJelly);
        bankedJelly = Math.min(cost, earned);

        emitJellyParticles(serverLevel);

        if (bankedJelly >= cost) {
            tryCrown(serverLevel);
        }
    }

    /**
     * [stated] "i said praetorian but this also applies to crushers" — the same two castes, in the same order of
     * preference, that {@code QueenPromotionService.pickPromotable} draws on for a hive's own daughter.
     */
    private boolean isEligibleCaste() {
        return royal.getType().is(AlienEntityTypeTags.PRAETORIANS) || royal.getType().is(AlienEntityTypeTags.CRUSHERS);
    }

    /**
     * ⭐⭐ ISOLATED MEANS BOTH: NO HIVE OWNS IT, AND IT IS NOT STANDING ON ANYONE'S GROUND.
     * <p>
     * Membership alone is not enough — a royal could belong to a hive whose location has since died. Position alone is
     * not enough either — a hive's own praetorian wandering outside the claim for an afternoon must not start crowning
     * itself. Requiring both is what makes this fire for exactly the case he described: summoned, spawn-egged or grown
     * wild, with nobody to take it in.
     * </p>
     * <p>
     * ⚠ A royal in a cocoon is already becoming something and is left alone.
     * </p>
     */
    private boolean isIsolated() {
        if (royal.getCocoonManager().shouldRunCocoonAction()) {
            return false;
        }
        if (HiveMemberLocationResolver.reserveReturnLocation(royal) != null) {
            return false;
        }

        var standingOn = HiveLocationRegistry.INSTANCE.getByChunk(royal.level().dimension(), royal.chunkPosition());
        return standingOn == null || !standingOn.isAlive();
    }

    /**
     * [stated] "green particle effects coming off them like the potion swirls for example but dont use the swirls use
     * like smoke or redstone." Coloured dust — redstone's particle with the colour swapped for green.
     */
    private void emitJellyParticles(ServerLevel serverLevel) {
        serverLevel.sendParticles(
            new DustParticleOptions(JELLY_PARTICLE_COLOUR, JELLY_PARTICLE_SCALE),
            royal.getX(),
            royal.getY() + royal.getBbHeight() * 0.6,
            royal.getZ(),
            3,
            royal.getBbWidth() * 0.45,
            royal.getBbHeight() * 0.35,
            royal.getBbWidth() * 0.45,
            0.0
        );
    }

    private void tryCrown(ServerLevel serverLevel) {
        var queenType = Queen.getType(royal.getVariant());
        if (queenType == null) {
            return; // this strain has no queen form (irradiated) - it stays what it is
        }

        royal.getCocoonManager().prepare(queenType, null, new CocooningConfig(COCOON_TICKS, COCOON_TICKS));
        bankedJelly = 0;
        isolatedTicks = 0L;

        Alien.LOGGER.info(
            "Hive: isolated {} at {} banked enough jelly alone and is crowning itself",
            royal.getType().getDescriptionId(),
            royal.blockPosition()
        );

        com.alien.common.gameplay.hive.growth.DaughterQueenAnnouncements.announceRaised(serverLevel, royal);
    }

    public void save(CompoundTag tag) {
        tag.putLong(NBT_ISOLATED_TICKS, isolatedTicks);
        tag.putInt(NBT_BANKED_JELLY, bankedJelly);
    }

    public void load(CompoundTag tag) {
        isolatedTicks = tag.getLong(NBT_ISOLATED_TICKS);
        bankedJelly = tag.getInt(NBT_BANKED_JELLY);
    }

    /** Exposed for {@code /hive} inspection and for anything that wants to show progress. */
    public int bankedJelly() {
        return bankedJelly;
    }

    public long isolatedTicks() {
        return isolatedTicks;
    }
}
