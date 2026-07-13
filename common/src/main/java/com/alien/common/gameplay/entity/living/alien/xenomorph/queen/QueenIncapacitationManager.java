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
 * On top of that sits the DOWN CAP: go down more than {@link #MAX_DOWNS} times inside {@link #DOWN_WINDOW_TICKS} and
 * the next defeat is a real death. The cap takes precedence over the heal and timeout exits - you cannot rescue your
 * way out of being worn down.
 * <p>
 * Every number here is a per-strain knob in the design. They are constants for now; when strain config lands, these
 * become its defaults.
 */
public final class QueenIncapacitationManager {

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

    private static final String NBT_FIRST_DOWN_TICK = "QueenIncapFirstDownTick";

    private final Queen queen;

    private float bar;

    private int downCount;

    private long firstDownGameTime = Long.MIN_VALUE;

    /** How near a player must be to watch her go. */
    private static final double BAR_VISIBLE_RADIUS = 48.0;

    /** Shown only while she is down. Server-side, so it syncs itself - no custom payload needed. */
    private @org.jetbrains.annotations.Nullable ServerBossEvent bossEvent;

    public QueenIncapacitationManager(Queen queen) {
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
        if (!(queen.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        var now = serverLevel.getGameTime();
        if (firstDownGameTime == Long.MIN_VALUE || now - firstDownGameTime > DOWN_WINDOW_TICKS) {
            // Window expired (or first ever down) - the count starts over from here.
            firstDownGameTime = now;
            downCount = 0;
        }

        // The down cap takes PRECEDENCE over every other exit: worn down too many times, she simply dies.
        if (downCount >= MAX_DOWNS) {
            return false;
        }

        downCount++;
        goDown();
        return true;
    }

    private void goDown() {
        bar = BAR_START;
        showBar();
        queen.setIncapacitated(true);
        queen.setHealth(1.0F);
        queen.setNoAi(true);
        queen.getNavigation().stop();
        queen.setTarget(null);
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
        return false;
    }

    /** A xenomorph got to her in time. She wakes at half health. */
    public void healRescue() {
        if (!queen.isIncapacitated()) {
            return;
        }
        wake(queen.getMaxHealth() * XENO_HEAL_FRACTION);
    }

    private void wake(float health) {
        bar = 0.0F;
        hideBar();
        queen.setIncapacitated(false);
        queen.setHealth(Math.max(1.0F, health));
        queen.setNoAi(false);
    }

    public void tick() {
        if (!queen.isIncapacitated() || !(queen.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // The bar refills on its own. Reaching full IS the self-recovery exit: she gets up at full strength.
        // FLOAT division on purpose: integer division here is 100 / 12000 == 0, which clamped to 1 point a
        // tick and filled the whole bar in five seconds instead of ten minutes.
        bar += (float) BAR_MAX / SELF_RECOVERY_TICKS;
        if (bar >= BAR_MAX) {
            wake(queen.getMaxHealth());
            return;
        }

        updateBar(serverLevel);

        if (queen.tickCount % HEAL_SCAN_INTERVAL_TICKS != 0) {
            return;
        }

        // Rescue: any adult xenomorph of the hive standing over her brings her round. Facehuggers cannot - by design
        // they can neither harm nor help her, which is what stops an unattended breeder freeing herself.
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
            healRescue();
            return;
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
        return Component.translatable("boss.avp_alien.queen_incapacitated")
            .append(Component.literal(" (" + downsLeft + ")").withStyle(ChatFormatting.DARK_RED));
    }

    /** She left the world while down (killed, unloaded, removed): never leak the bar. */
    public void onRemoved() {
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
        tag.putLong(NBT_FIRST_DOWN_TICK, firstDownGameTime);
    }

    public void load(CompoundTag tag) {
        bar = tag.getFloat(NBT_BAR);
        downCount = tag.getInt(NBT_DOWN_COUNT);
        if (tag.contains(NBT_FIRST_DOWN_TICK)) {
            firstDownGameTime = tag.getLong(NBT_FIRST_DOWN_TICK);
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
