package com.alien.common.gameplay.item;

import com.alien.common.data.AlienAdvancements;
import com.alien.common.registry.init.AlienMobEffects;
import com.blib.api.common.advancement.v1.BLibAdvancement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The shared rules for swallowing raw hive jelly, whichever kind it is.
 * <h2>The sickness ratchet</h2> In the fiction, royal jelly is a tonic that turns on you if you live on it. Here EVERY
 * dose makes you sick - the first bite is already Jelly Sickness I - and doses taken inside a shared sixty-second
 * window ratchet the rung up, to a ceiling of III. Wait the window out and the ladder drops back to I. Both jellies
 * feed the same window on purpose: alternating them is still living on jelly, and letting a player launder the cooldown
 * by switching flavours would defeat the whole rule.
 * <p>
 * The rung is tracked HERE rather than read back off the active effect, and that is not incidental. Jelly Sickness I
 * runs for ten seconds while the window runs for sixty, so a second dose at t=30s would find no effect on the player at
 * all and would quietly restart the ladder at I forever. The dose record outlives the symptom.
 * <p>
 * III is the ceiling deliberately. {@code JellySicknessStatusEffect} treats I-III as poison-like (they leave the victim
 * at half a heart and never finish them) and reserves IV for the wither-grade tier that CAN kill. Greed should cost a
 * player their health bar, not their life.
 * <p>
 * The window lives in a plain in-memory map rather than saved data: sixty seconds has no business surviving a server
 * restart, and a stale entry is pruned the next time that player eats.
 */
public final class JellyConsumption {

    /** Eat a second jelly inside this window and the sickness ratchets one rung. */
    public static final int SHARED_WINDOW_TICKS = 20 * 60;

    /** Buff duration for both jellies. */
    public static final int BUFF_DURATION_TICKS = 20 * 30;

    /** Amplifier 1 reads as "II" in the HUD. */
    public static final int BUFF_AMPLIFIER = 1;

    /**
     * Hunger icons the jelly COSTS. Raw hive jelly is not a meal - it is a stimulant, and it burns through you.
     * <p>
     * Flip this to a positive restore by swapping the {@code subtract} in {@link #applyHungerCost} for
     * {@code player.getFoodData().eat(...)}; the items are otherwise unchanged.
     */
    public static final int HUNGER_ICONS_COST = 3;

    /** Two food points per icon on the HUD. */
    private static final int FOOD_POINTS_PER_ICON = 2;

    /** Jelly Sickness durations for rungs I, II and III - matched to the Growth Suppression potion's own ladder. */
    private static final int[] SICKNESS_DURATION_TICKS = { 200, 240, 300 };

    /** Rung III. {@code JellySicknessStatusEffect} amplifier 3 is the lethal tier and is never reached from eating. */
    private static final int MAX_SICKNESS_AMPLIFIER = 2;

    /** Player UUID to their last dose. Server side only, never persisted. */
    private static final Map<UUID, Dose> LAST_DOSE = new HashMap<>();

    /**
     * When a player's last jelly went down, and which rung of Jelly Sickness it landed on.
     *
     * @param tick game time of that dose
     * @param rung amplifier used, 0-based: 0 is Jelly Sickness I
     */
    private record Dose(
        long tick,
        int rung
    ) {}

    private JellyConsumption() {}

    /**
     * Runs the shared half of eating a jelly: the hunger cost, then the sickness ratchet if this bite landed inside the
     * window. The caller applies its own buffs and grants its own advancement.
     */
    public static void consume(LivingEntity eater) {
        if (!(eater instanceof Player player) || player.level().isClientSide) {
            return;
        }

        applyHungerCost(player);

        var now = player.level().getGameTime();
        var previous = LAST_DOSE.get(player.getUUID());
        var withinWindow = previous != null && now - previous.tick() <= SHARED_WINDOW_TICKS;

        // Every dose is a dose: a clean bite still lands on rung I. Only a bite inside the window climbs.
        var rung = withinWindow ? Math.min(previous.rung() + 1, MAX_SICKNESS_AMPLIFIER) : 0;

        LAST_DOSE.put(player.getUUID(), new Dose(now, rung));
        pruneStaleEntries(now);

        player.addEffect(
            new MobEffectInstance(AlienMobEffects.getJellySicknessHolder(), SICKNESS_DURATION_TICKS[rung], rung)
        );
    }

    private static void applyHungerCost(Player player) {
        var foodData = player.getFoodData();
        var cost = HUNGER_ICONS_COST * FOOD_POINTS_PER_ICON;

        foodData.setFoodLevel(Math.max(0, foodData.getFoodLevel() - cost));
        foodData.setSaturation(Math.max(0.0F, foodData.getSaturationLevel() - cost));
    }

    /**
     * Drops entries whose window has long closed. Called on every bite, so the map never outgrows the number of players
     * who have eaten jelly recently.
     */
    private static void pruneStaleEntries(long now) {
        LAST_DOSE.entrySet().removeIf(entry -> now - entry.getValue().tick() > SHARED_WINDOW_TICKS * 2L);
    }

    /** Convenience for the item classes: the eater as a server player, or null. */
    /**
     * Every jelly that can be eaten. The set advancement checks against this, so a fifth jelly is added here and
     * nowhere else.
     */
    private static final List<BLibAdvancement> EVERY_JELLY_ADVANCEMENT = List.of(
        AlienAdvancements.EAT_RAW_ROYAL_JELLY,
        AlienAdvancements.EAT_RAW_SCOURGE_JELLY,
        AlienAdvancements.EAT_RAW_IRRADIATED_JELLY,
        AlienAdvancements.EAT_POISON_JELLY
    );

    /**
     * Grants a jelly's own advancement, then the set advancement if that was the last one missing.
     * <p>
     * CHECKED rather than counted: {@code isGranted} asks the player's real advancement progress, so it survives
     * restarts, still works if one was handed out by command, and needs no separate tally to keep in sync.
     */
    public static void grantJellyAdvancement(ServerPlayer serverPlayer, BLibAdvancement advancement) {
        advancement.grant(serverPlayer);

        for (var jellyAdvancement : EVERY_JELLY_ADVANCEMENT) {
            if (!jellyAdvancement.isGranted(serverPlayer)) {
                return;
            }
        }

        AlienAdvancements.EAT_EVERY_JELLY.grant(serverPlayer);
    }

    public static ServerPlayer serverPlayerOrNull(LivingEntity eater) {
        return eater instanceof ServerPlayer serverPlayer ? serverPlayer : null;
    }
}
