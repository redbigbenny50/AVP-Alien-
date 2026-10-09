package com.alien.common.registry.init;

import com.alien.mixin.MixinGameRulesAccessor;
import com.alien.mixin.MixinGameRulesBooleanValueAccessor;
import net.minecraft.world.level.GameRules;

public final class AlienGameRules {

    /**
     * {@return the boolean rule {@code id}} - the one a sibling mod already registered if there is one, otherwise a new
     * one registered here.
     * <p>
     * ⚠ Locked on {@code GameRules.class}, the same monitor avp_predator locks, so the look-up and the registration are
     * one step even if the two mods are set up on different threads.
     * </p>
     * <p>
     * ⚠ Declared ABOVE the rules that use it only for reading order; a static method is callable from any static field
     * initialiser regardless of where it sits.
     * </p>
     */
    private static GameRules.Key<GameRules.BooleanValue> sharedBoolean(String id, GameRules.Category category, boolean defaultValue) {
        synchronized (GameRules.class) {
            @SuppressWarnings("unchecked")
            GameRules.Key<GameRules.BooleanValue>[] found = new GameRules.Key[1];

            GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {

                @Override
                public void visitBoolean(GameRules.Key<GameRules.BooleanValue> key, GameRules.Type<GameRules.BooleanValue> type) {
                    if (key.getId().equals(id)) {
                        found[0] = key;
                    }
                }
            });

            if (found[0] != null) {
                return found[0];
            }

            return MixinGameRulesAccessor.avp_alien$register(
                id,
                category,
                MixinGameRulesBooleanValueAccessor.avp_alien$create(defaultValue, (server, value) -> {})
            );
        }
    }

    public static final GameRules.Key<GameRules.BooleanValue> AVP_ALIEN_TOTEMS_PREVENT_CHESTBURSTER_DEATH =
        MixinGameRulesAccessor.avp_alien$register(
            "avpAlienTotemsPreventChestbursterDeath",
            GameRules.Category.PLAYER,
            MixinGameRulesBooleanValueAccessor.avp_alien$create(false, (server, value) -> {})
        );

    /**
     * Whether a hive may substitute a PREDALIEN into its reserve production. OFF by default.
     * <p>
     * A hive growing its own predaliens from simulated reserves was contentious - it makes them ordinary hive stock
     * rather than the product of a predator being taken by a facehugger, which is where they come from. So it is now
     * opt-in per world, and needs AVP: Predator installed on top: the gamerule alone does nothing without the mod that
     * owns the species.
     */
    public static final GameRules.Key<GameRules.BooleanValue> AVP_ALIEN_HIVES_BREED_PREDALIENS =
        MixinGameRulesAccessor.avp_alien$register(
            "avpAlienHivesBreedPredaliens",
            GameRules.Category.SPAWNING,
            MixinGameRulesBooleanValueAccessor.avp_alien$create(false, (server, value) -> {})
        );

    /**
     * Whether gunfire from OTHER gun mods (TACZ, Point Blank) is balanced against this suite's own guns. ON by default.
     * <p>
     * [stated] "put these settings (nerfs) behind a game rule thats on by default call it gunbalancing so if players
     * want to be op with these mods they can but not by default." ON: each supported mod's parity multiplier, the
     * shared 160 dps budget and the hit floor all apply - see GunDamageParity. OFF: those mods deal their own
     * unmodified damage to xenomorphs. avp_human's guns are never touched either way.
     * </p>
     * <p>
     * ⚠ Read per hit from the xenomorph's own level, so {@code /gamerule gunBalancing false} takes effect on the next
     * shot, with no restart.
     * </p>
     * <p>
     * ⚠⚠ SHARED WITH avp_predator, which balances yautja behind this same rule ([stated] Oct 4: one rule for both mods,
     * not one each). Minecraft crashes at startup on a second registration of the same rule id, so whichever mod loads
     * first registers it and the other finds it and uses the same key - see {@link #sharedBoolean}. avp_predator does
     * exactly the same, so load order does not matter.
     * </p>
     */
    public static final GameRules.Key<GameRules.BooleanValue> GUN_BALANCING = sharedBoolean("gunBalancing", GameRules.Category.MOBS, true);

    /**
     * When ON, hives block natural (vanilla and other-mod) mob spawning inside their own ground. ON by default.
     * <p>
     * [stated] Oct 1: "it looks like your current game rule stops spawning in hives which is fine but it needs its own
     * game rule seperate from the queen one. also both of these game rules are off by default turning them on blocks
     * the spawning." ON blocks spawns inside a hive's slab band, its built chunks and a ruined hive's band, in every
     * hive mode. OFF lets animals and monsters spawn through a hive as if it were not there.
     * </p>
     * <p>
     * ⭐ ON BY DEFAULT, the one exception to "off by default": [stated] once it was clear this covers only vanilla and
     * other mods' mobs, never xenomorphs, "this should be on by default". ON is exactly how hives behaved before the
     * rule existed. Aliens are never affected either way - avp_alien's own spawns skip the check. Read on every spawn
     * attempt, so it takes effect at once.
     * </p>
     */
    public static final GameRules.Key<GameRules.BooleanValue> HIVE_BLOCKS_MOB_SPAWNS =
        MixinGameRulesAccessor.avp_alien$register(
            "hiveBlocksMobSpawns",
            GameRules.Category.SPAWNING,
            MixinGameRulesBooleanValueAccessor.avp_alien$create(true, (server, value) -> {})
        );

    /**
     * When ON, wild queens stop spawning naturally in the world. OFF by default.
     * <p>
     * [stated] Oct 1: "when i originally said natural spawns i meant the queens spawning in the world." Covers both
     * routes {@code QueenNaturalSpawnTask} has: the exploration roll on fresh chunks AND the first-queen guarantee.
     * Placed queens, spawn eggs, hive-raised daughters and commands are untouched.
     * </p>
     * <p>
     * ⭐ No chunk rolls are spent while it is on - the task returns before sampling - so turning it off again later
     * leaves unexplored ground exactly as eligible as it was.
     * </p>
     */
    public static final GameRules.Key<GameRules.BooleanValue> BLOCK_NATURAL_QUEEN_SPAWNS =
        MixinGameRulesAccessor.avp_alien$register(
            "blockNaturalQueenSpawns",
            GameRules.Category.SPAWNING,
            MixinGameRulesBooleanValueAccessor.avp_alien$create(false, (server, value) -> {})
        );

    private AlienGameRules() {}

    public static void initialize() {}
}
