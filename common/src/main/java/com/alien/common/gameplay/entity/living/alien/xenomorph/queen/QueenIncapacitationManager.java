package com.alien.common.gameplay.entity.living.alien.xenomorph.queen;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * The queen's downed state: she does not die at 0 HP, she falls over.
 * <p>
 * One state, four exits, governed by a SECOND health pool - the incapacitation bar. It starts at {@link #BAR_START}
 * (one point) and refills to {@link #BAR_MAX} over {@link #SELF_RECOVERY_TICKS}, so she is at her most killable the
 * instant she goes down and hardens the longer she is left alone. The four exits:
 * <ul>
 * <li><b>Kill</b> - drain the bar to 0. Damage taken while down eats the BAR, not her health.</li>
 * <li><b>Heal (rescue)</b> - a nearby xenomorph restores {@link #XENO_HEAL_FRACTION} of her health and she wakes.</li>
 * <li><b>Self-recovery</b> - left alone, the bar fills and she wakes fully healed.</li>
 * <li><b>Capture</b> - the player tags/chains her. She still wakes on heal/timeout; the inhibitor only changes WHO she
 * is when she wakes, not whether she does.</li>
 * </ul>
 * On top of that sits the DOWN CAP: go down more than {@link #MAX_DOWNS} times, each within {@link #DOWN_WINDOW_TICKS}
 * of the LAST one, and the next defeat is a real death. The cap takes precedence over the heal and timeout exits - you
 * cannot rescue your way out of being worn down. Measured from the last down rather than the first ON PURPOSE: measured
 * from the first, a rescue-and-re-down cycle slower than three downs per window kept resetting the count, and she could
 * be ground forever without ever wearing out.
 * <p>
 * Every number here is a per-strain knob in the design. They are constants for now; when strain config lands, these
 * become its defaults.
 */
public final class QueenIncapacitationManager<T extends com.alien.common.gameplay.entity.living.alien.Alien & com.alien.common.gameplay.entity.living.alien.xenomorph.IncapacitatableRoyal> {

    /** Full incapacitation bar. Abstract points, not hearts. */
    public static final int BAR_MAX = 100;

    /** Where the bar starts the moment she drops - she is one point from death. */
    public static final int BAR_START = 1;

    /**
     * Left completely alone, this is how long the bar takes to fill (and she wakes fully healed).
     * <p>
     * Ten minutes: the downed window is not just a finisher timer, it is the window in which you MOVE her. Chaining,
     * hauling and securing a queen has to be realistically possible inside it.
     */
    public static final int SELF_RECOVERY_TICKS = 10 * 60 * 20;

    /**
     * How long a rescuer must stand over her, unbroken, before she comes round.
     * <p>
     * The rescue used to fire the instant any kin came within {@link #HEAL_RADIUS} on a 20-tick scan, which made a
     * downed queen surrounded by her own hive effectively unkillable: she was back up before an attacker could land the
     * finisher, over and over. A channel gives the fight counterplay in both directions — the attacker can break it by
     * killing or driving off the rescuer, and the hive has to actually commit a body to standing over her.
     * <p>
     * Three seconds, matching the 60-tick claw the chained-queen rescue already uses, so the two read as the same act.
     */
    /**
     * ⭐⭐ TEN SECONDS TO DRAG HER BACK UP, AND A PLAYER CAN STOP IT.
     * <p>
     * [stated] "have the kin rescue take 10 seconds and a player can interupt it."
     * </p>
     * <p>
     * ⚠ It was THREE seconds, which is why the reports read as instant: a player hitting a downed queen in her own hive
     * could not out-damage a channel that finished before they landed a second blow.
     * </p>
     */
    public static final int RESCUE_CHANNEL_TICKS = 200;

    /** A rescuing xenomorph restores this fraction of her max health, and she wakes immediately. */
    public static final float XENO_HEAL_FRACTION = 0.5F;

    /** How close a xenomorph must be to rescue her. */
    public static final double HEAL_RADIUS = 4.0;

    /** How often we look for a rescuer (a scan every tick would be wasteful). */
    private static final int HEAL_SCAN_INTERVAL_TICKS = 20;

    /** Downs allowed inside the window. The NEXT one past this kills her outright. */
    public static final int MAX_DOWNS = 3;

    /** The window the down-count is measured over. Downs older than this are forgotten. */
    public static final int DOWN_WINDOW_TICKS = 10 * 60 * 20;

    private static final String NBT_BAR = "QueenIncapBar";

    private static final String NBT_DOWN_COUNT = "QueenIncapDownCount";

    /** Key kept at its original spelling so existing saves still load; it now stores the LAST down. */
    private static final String NBT_FIRST_DOWN_TICK = "QueenIncapFirstDownTick";

    private static final String NBT_LAST_BAR_TICK = "QueenIncapLastBarTick";

    private final T queen;

    private float bar;

    private int downCount;

    private long lastDownGameTime = Long.MIN_VALUE;

    /**
     * Game-time she last stood back up, and the point the forgiveness window is measured from.
     * <p>
     * ⚠ Deliberately NOT persisted: a reload is a clean slate for the down count anyway, and a stale value across a
     * restart would be worse than starting the window fresh.
     * </p>
     */
    private long lastWakeGameTime = Long.MIN_VALUE;

    /** The rescuer currently standing over her, and how long it has held. Transient: a reload restarts the channel. */
    private java.util.UUID rescuerId = null;

    private int rescueChannelTicks = 0;

    /** Game-time the recovery bar was last advanced. Lets the bar catch up in one step across an unload gap. */
    private long lastBarGameTime = Long.MIN_VALUE;

    /** How near a player must be to watch her go. */
    private static final double BAR_VISIBLE_RADIUS = 48.0;

    /** Shown only while she is down. Server-side, so it syncs itself - no custom payload needed. */
    private @org.jetbrains.annotations.Nullable ServerBossEvent bossEvent;

    public QueenIncapacitationManager(T queen) {
        this.queen = queen;
    }

    /** Whether this strain can be put down at all. Per-strain knob; every strain can, for now. */
    public boolean canBeIncapacitated() {
        return true;
    }

    /**
     * Lethal damage arrived. Returns true if she went DOWN instead of dying (so the caller must cancel the death).
     * Returns false if this is a real death: the strain cannot be incapacitated, or she has burned through her downs.
     */
    public boolean onLethalDamage() {
        if (!canBeIncapacitated() || queen.isIncapacitated()) {
            return false;
        }

        // ⭐⭐⭐ NO HEAD IS INSTANT DEATH. NEVER A FINISHER OPPORTUNITY.
        //
        // [stated] "she shouldnt be alive is the first issue with no head she should not go incapacitated it should
        // be instant death."
        //
        // ⚠⚠ THE ORDER IS WHAT PRODUCED THE SCREENSHOT. A ravager tears her head off, the damage is lethal, this
        // method puts her in the downed state at 1 HP - and only THEN does Alien's per-tick head check fire and try
        // to kill her with a SOURCELESS damage source, which a downed queen shrugs off because nothing was there to
        // work her bar. She ended up sprawled and headless and alive, indefinitely.
        //
        // ⚠ Refusing to go down here is the correct fix rather than fixing the death that follows: she should never
        // have entered a state she cannot recover from. A queen without a head has nothing to be rescued into.
        if (queen.isHeadDetached()) {
            return false;
        }
        if (!(queen.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        var now = serverLevel.getGameTime();

        // 🚨🚨 THE WINDOW IS MEASURED FROM WHEN SHE WOKE, NOT FROM WHEN SHE FELL - AND THAT MADE THE EMPRESS IMMORTAL.
        //
        // ⚠⚠ THE ARITHMETIC: self-recovery is maxHealth / regen, so a QUEEN takes 9.7 minutes (350 hp, 0.6/s) and an
        // EMPRESS takes 10.4 (500 hp, 0.8/s). Against a fixed 10-minute DOWN_WINDOW_TICKS stamped at the moment she
        // FELL, the queen's count survived her recovery and the cap fired on her third down - but the empress always
        // woke 24 SECONDS AFTER HER OWN WINDOW EXPIRED, so downCount reset to 0 every single time and MAX_DOWNS could
        // NEVER be reached. She could be knocked down forever and never finished.
        //
        // ⚠⚠ THAT IS WHY EVERY PREVIOUS FIX "WORKED": all of them were verified on a queen, which sits 18 seconds on
        // the safe side of the same line. Reported as "she didnt die whatsoever ... i shot her during the duration of
        // her dig and she still did not die, the incapacitated bar remained on my screen the whole time" - she was
        // cycling down, recovering, standing, and being knocked down again with the count reset each round.
        //
        // ⭐ Measuring from the WAKE is what the window was always meant to express: she has to spend that long UP and
        // unmolested to earn a clean slate. Time spent unconscious no longer counts toward forgiveness, so the cap is
        // reachable for every royal regardless of how long her own recovery takes.
        var sinceClearSlate = lastWakeGameTime == Long.MIN_VALUE ? lastDownGameTime : lastWakeGameTime;

        if (sinceClearSlate == Long.MIN_VALUE || now - sinceClearSlate > DOWN_WINDOW_TICKS) {
            downCount = 0;
        }

        // ⚠ A LOUD WARNING RATHER THAN A SILENT IMMORTAL. If anyone ever retunes health or regen so that a royal's own
        // recovery outlasts the forgiveness window again, the wake-based measurement above still saves us - but the
        // combination means she is effectively never worn down, and that should be visible rather than discovered in
        // a bug report months later.
        if (downCount == 0 && selfRecoveryTicks() > DOWN_WINDOW_TICKS) {
            com.alien.Alien.LOGGER.warn(
                "Royal {} recovers in {}t but the down window is {}t - she can only ever be finished outright, never"
                    + " worn down. Check her maxHealth/healthRegenPerSecond against DOWN_WINDOW_TICKS.",
                queen.getUUID(),
                (long) selfRecoveryTicks(),
                DOWN_WINDOW_TICKS
            );
        }

        // The down cap takes PRECEDENCE over every other exit: worn down too many times, she simply dies.
        if (downCount >= MAX_DOWNS) {
            return false;
        }

        downCount++;
        lastDownGameTime = now;
        goDown();
        return true;
    }

    private void goDown() {
        // ⭐⭐ SHE DROPS THE SACK WHEN SHE GOES DOWN.
        //
        // [stated] "the sack should disappear or at least unattach from it while incapacited." A queen sprawled in
        // the incapacitated pose while still wearing an ovipositor reads as a bug even when everything else is
        // working - and mechanically it is one: she is pacified, she cannot lay, and the sack is a large entity
        // riding a body that is no longer holding it up.
        //
        // ⚠ ABANDON, NOT DESTROY. abandonOvipositor detaches it and lets the ordinary despawn timer run, so a queen
        // who is RESCUED off the floor has not silently lost her hive's egg production - the timer is the same one
        // that already governs a sack whose queen walked away.
        queen.abandonOvipositorForIncapacitation();

        bar = BAR_START;
        clearRescueChannel();
        lastBarGameTime = queen.level().getGameTime();
        showBar();
        queen.setIncapacitated(true);
        queen.setHealth(1.0F);
        queen.setNoAi(true);
        queen.getNavigation().stop();
        queen.setTarget(null);

        // !!! A ROYAL WHO GOES DOWN MID-DESCENT MUST STOP DIGGING. setNoAi and stopping the navigation do not touch
        // the dig state, which owns noPhysics and no-gravity as well as the animation - so an empress shot while
        // tunnelling stayed "digging" at 0 HP, kept playing the dig animation, and could drift through blocks while
        // incapacitated.
        //
        // ⚠ REPORTED: "she stood up and started doing the dig animation again even tho she was at 0 hp ... now shes
        // just stuck like this". Introduced with the lone-empress founding, which is what put a royal underground
        // and mid-dig in the first place - before that a downed royal was never digging.
        if (queen instanceof com.alien.common.gameplay.entity.living.alien.xenomorph.empress.Empress empress) {
            empress.clearDescent();
            empress.setDigging(false);
        }
    }

    /**
     * Damage taken while she is down eats the INCAPACITATION BAR, not her health - this is the player's finisher.
     * Returns true if that killed her.
     */
    public boolean onDamageWhileDown(float amount) {
        if (!queen.isIncapacitated()) {
            return false;
        }
        bar -= Math.max(1.0F, amount);
        if (bar <= 0.0F) {
            bar = 0.0F;
            return true; // finished off
        }
        // ⭐⭐ HITTING HER BREAKS THE RESCUE. Without this a kin channel completes THROUGH a player standing over
        // her, which is the same "she healed through me" complaint arriving by a slower route - and it makes the
        // ten-second channel meaningless, because nothing could ever spend those ten seconds usefully.
        clearRescueChannel();

        // ⚠ The blow takes health as well as bar, so the two never disagree - a player watching her health bar sees
        // the same fight the incapacitation bar is showing.
        applyBarHealth();
        return false;
    }

    /** A xenomorph got to her in time. She wakes at half health. */
    public void healRescue() {
        if (!queen.isIncapacitated()) {
            return;
        }
        wake(queen.getMaxHealth() * XENO_HEAL_FRACTION);
    }

    /**
     * ⚠ SELF-RECOVERY IS NOT A JUMP ANY MORE: her health has been climbing with the bar the whole time, so by the time
     * this is reached she is already at the value being passed. A RESCUE still snaps - [stated] "if aliens rescue her
     * then like you said she snaps awake with half health" - and that is the point of being rescued.
     */
    private void wake(float health) {
        // ⚠ THE CLOCK FOR "HAS SHE BEEN LEFT ALONE" STARTS HERE, not when she fell - see the window check in tick().
        if (queen.level() instanceof ServerLevel wakeLevel) {
            lastWakeGameTime = wakeLevel.getGameTime();
        }

        bar = 0.0F;
        clearRescueChannel();
        lastBarGameTime = Long.MIN_VALUE;
        hideBar();
        queen.setIncapacitated(false);
        queen.setHealth(Math.max(1.0F, health));
        queen.setNoAi(false);
    }

    /**
     * How long a full self-recovery takes, in ticks, derived from HER OWN pool and HER OWN regeneration.
     * <p>
     * ⚠ A buffed royal takes longer, a weakened one less, and an empress takes twice a queen's time for twice the
     * health - all without a table of per-caste numbers to keep in step.
     * </p>
     * <p>
     * ⚠ Falls back to the old fixed window if regen is zero or negative, so a caste that does not regenerate cannot
     * divide by zero and sit down forever.
     * </p>
     */
    private float selfRecoveryTicks() {
        var regenPerSecond = queen.healthRegenPerSecondForRecovery();
        if (regenPerSecond <= 0.0F) {
            return SELF_RECOVERY_TICKS;
        }
        return Math.max(20.0F, queen.getMaxHealth() / regenPerSecond * 20.0F);
    }

    /**
     * Puts her health where the bar says it should be.
     * <p>
     * ⚠ Never below 1: the bar reaching zero is what KILLS her, and that is decided in
     * {@link #onDamageWhileDown(float)}, not here. If this were allowed to set 0 she would die of arithmetic on the way
     * down instead of by the finisher.
     * </p>
     */
    private void applyBarHealth() {
        var target = Math.max(1.0F, queen.getMaxHealth() * (bar / BAR_MAX));
        if (Math.abs(queen.getHealth() - target) > 0.01F) {
            queen.setHealth(Math.min(target, queen.getMaxHealth()));
        }
    }

    public void tick() {
        if (!queen.isIncapacitated() || !(queen.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // The bar refills on its own toward full (full = the self-recovery exit). Advance by ELAPSED game-time,
        // not a fixed per-tick step: when she was down in an UNLOADED chunk this one line applies the whole gap in
        // a single step (same catch-up model the hive CatchUpEngine uses), so a queen left downed offline for the
        // full window wakes on reload instead of freezing at whatever value she had. FLOAT division on purpose.
        var now = serverLevel.getGameTime();
        var elapsed = lastBarGameTime == Long.MIN_VALUE ? 1L : Math.max(0L, now - lastBarGameTime);
        lastBarGameTime = now;
        // ⭐⭐⭐ THE BAR IS HER HEALING, AND IT IS PACED BY HER OWN REGEN - NOT BY A FIXED CLOCK.
        //
        // [stated] "the bar is supposed to fill gradually and go down if a player or anything attacks her so its
        // like a tug of war ... the bar needs to reflect her regen time to full to wake up on her own and it needs
        // to match her health because buffs can give her more or less health so a set time wont really work."
        //
        // ⚠⚠ A FIXED DURATION IS WRONG AND I SHIPPED ONE. Ten seconds for everyone meant an empress with twice a
        // queen's health recovered twice as fast per point, and any buff that raised her maximum made her recover
        // faster still - the opposite of what more health should mean. Deriving the fill from maxHealth / regen ties
        // it to the creature: a bigger pool takes proportionally longer, and a buff that grants more health
        // lengthens the climb by exactly as much as it added.
        bar += (float) BAR_MAX / selfRecoveryTicks() * elapsed;

        // ⭐⭐ HER HEALTH RIDES THE BAR. This is what makes it a tug of war rather than two separate systems: every
        // point the bar gains is health she visibly regains, and every blow that knocks the bar down takes that
        // health straight back off. At a full bar she is already at full health, so waking is not a jump.
        applyBarHealth();

        if (bar >= BAR_MAX) {
            wake(queen.getMaxHealth());
            return;
        }

        updateBar(serverLevel);

        if (queen.tickCount % HEAL_SCAN_INTERVAL_TICKS != 0) {
            return;
        }

        // Rescue: an adult xenomorph OF HER OWN STRAIN standing over her brings her round. Facehuggers cannot - by
        // design they can neither harm nor help her, which is what stops an unattended breeder freeing herself. A
        // rival strain's xenomorph never wakes her: strains are always hostile to one another, and a rival standing
        // over a downed queen is there to finish her, not tend her.
        var box = queen.getBoundingBox().inflate(HEAL_RADIUS);
        for (var candidate : serverLevel.getEntitiesOfClass(LivingEntity.class, box)) {
            if (candidate == queen || !candidate.isAlive()) {
                continue;
            }
            if (!candidate.getType().is(AlienEntityTypeTags.XENOMORPHS)) {
                continue;
            }
            if (candidate.getType().is(AlienEntityTypeTags.PARASITES)) {
                continue;
            }
            if (
                !(candidate instanceof com.alien.common.gameplay.entity.living.alien.Alien rescuer)
                    || !java.util.Objects.equals(rescuer.getVariant(), queen.getVariant())
            ) {
                continue;
            }
            tickRescueChannel(candidate);
            return;
        }

        // Nobody is standing over her: whatever progress a rescuer had made is lost.
        clearRescueChannel();
    }

    /**
     * Advances the rescue channel for the kin currently over her, and wakes her once it completes.
     * <p>
     * The channel belongs to ONE rescuer. If it dies, wanders out of {@link #HEAL_RADIUS} or is replaced by a different
     * xenomorph, the count restarts — a relay of drones passing through cannot chip away at it, and killing the one
     * that is actually tending her genuinely undoes the work.
     */
    private void tickRescueChannel(LivingEntity rescuer) {
        if (!java.util.Objects.equals(rescuerId, rescuer.getUUID())) {
            rescuerId = rescuer.getUUID();
            rescueChannelTicks = 0;
        }

        rescueChannelTicks += HEAL_SCAN_INTERVAL_TICKS;
        publishRescueProgress(Math.min(1.0F, (float) rescueChannelTicks / RESCUE_CHANNEL_TICKS));

        if (rescueChannelTicks >= RESCUE_CHANNEL_TICKS) {
            clearRescueChannel();
            healRescue();
        }
    }

    private void clearRescueChannel() {
        // ⚠ Clear the RESCUER's visible progress too, or an interrupted attempt leaves a frozen bar over a
        // xenomorph that has stopped trying.
        publishRescueProgress(0.0F);
        rescuerId = null;
        rescueChannelTicks = 0;
    }

    /** Shows the attempt over the rescuer's head, so a player can see it and decide to stop it. */
    private void publishRescueProgress(float progress) {
        if (rescuerId == null || !(queen.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (serverLevel.getEntity(rescuerId) instanceof com.alien.common.gameplay.entity.living.alien.Alien rescuer) {
            rescuer.setRescueChannelProgress(progress);
        }
    }

    /**
     * The incapacitation bar itself. It reads as a RESISTANCE meter, not a health bar: it starts nearly empty (she is
     * one point from death the instant she drops) and fills as she recovers. A player watching it fill knows exactly
     * how long they have left to finish her, chain her, or run.
     */
    private void showBar() {
        if (bossEvent != null) {
            return;
        }
        bossEvent = new ServerBossEvent(
            title(),
            AlienVariantTypes.getFor(queen.getVariant()).bossBarColor(),
            BossEvent.BossBarOverlay.PROGRESS
        );
        bossEvent.setProgress(barFraction());
    }

    private void hideBar() {
        if (bossEvent == null) {
            return;
        }
        bossEvent.removeAllPlayers();
        bossEvent.setVisible(false);
        bossEvent = null;
    }

    private void updateBar(ServerLevel level) {
        if (bossEvent == null) {
            showBar();
        }
        if (bossEvent == null) {
            return;
        }
        bossEvent.setProgress(barFraction());
        bossEvent.setName(title());

        // Anyone close enough to act on it sees it; anyone who walks away loses it.
        var radiusSqr = BAR_VISIBLE_RADIUS * BAR_VISIBLE_RADIUS;
        for (var player : level.players()) {
            boolean near = player.distanceToSqr(queen) <= radiusSqr;
            if (near && !bossEvent.getPlayers().contains(player)) {
                bossEvent.addPlayer(player);
            } else if (!near && bossEvent.getPlayers().contains(player)) {
                bossEvent.removePlayer(player);
            }
        }
    }

    private Component title() {
        var downsLeft = Math.max(0, MAX_DOWNS - downCount);
        // ⭐⭐ NAME WHOEVER IS ACTUALLY DOWN. This manager is generic over IncapacitatableRoyal - an EMPRESS uses it
        // too - and the title was hardcoded to the queen's string, so a downed Nether Empress announced itself as
        // "Queen — Incapacitated". [stated] "the name on the bar is queen really why isnt it empress."
        //
        // ⚠ Taken from the entity's OWN type description, so every strain reads correctly and any royal added later
        // names itself without touching this again.
        return Component.translatable("boss.avp_alien.royal_incapacitated", queen.getType().getDescription())
            .append(Component.literal(" (" + downsLeft + ")").withStyle(ChatFormatting.DARK_RED));
    }

    /** She left the world while down (killed, unloaded, removed): never leak the bar. */
    public void onRemoved() {
        clearRescueChannel();
        hideBar();
    }

    /** Current bar value, for the UI and for debug. */
    public float bar() {
        return bar;
    }

    /** Bar as a 0..1 fraction - what a UI actually wants. */
    public float barFraction() {
        return Math.clamp(bar / BAR_MAX, 0.0F, 1.0F);
    }

    public int downCount() {
        return downCount;
    }

    public void save(CompoundTag tag) {
        tag.putFloat(NBT_BAR, bar);
        tag.putInt(NBT_DOWN_COUNT, downCount);
        tag.putLong(NBT_FIRST_DOWN_TICK, lastDownGameTime);
        tag.putLong(NBT_LAST_BAR_TICK, lastBarGameTime);
    }

    public void load(CompoundTag tag) {
        bar = tag.getFloat(NBT_BAR);
        downCount = tag.getInt(NBT_DOWN_COUNT);
        if (tag.contains(NBT_FIRST_DOWN_TICK)) {
            lastDownGameTime = tag.getLong(NBT_FIRST_DOWN_TICK);
        }
        if (tag.contains(NBT_LAST_BAR_TICK)) {
            lastBarGameTime = tag.getLong(NBT_LAST_BAR_TICK);
        }
    }

    /** Sanity: a downed queen must never be left with AI enabled after a reload. */
    public void onLoaded() {
        if (queen.isIncapacitated()) {
            queen.setNoAi(true);
        }
    }

    /** The damage source does not matter for the down/kill decision - kept for future per-source rules. */
    @SuppressWarnings("unused")
    public static boolean isFinisher(DamageSource source) {
        return true;
    }
}
