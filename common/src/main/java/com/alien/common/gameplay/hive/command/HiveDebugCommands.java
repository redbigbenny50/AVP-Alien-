package com.alien.common.gameplay.hive.command;

import com.alien.Alien;
import com.alien.common.gameplay.hive.convoy.Convoy;
import com.alien.common.gameplay.hive.convoy.MigrationDispatch;
import com.alien.common.gameplay.hive.convoy.RaidDispatch;
import com.alien.common.gameplay.hive.convoy.ReinforcementDispatcher;
import com.alien.common.gameplay.hive.empress.EmpressEmergenceRitual;
import com.alien.common.gameplay.hive.empress.EmpressEmergenceTask;
import com.alien.common.gameplay.hive.faction.FactionAesthetics;
import com.alien.common.gameplay.hive.faction.FactionNaming;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.faction.LineageInvariantTask;
import com.alien.common.gameplay.hive.faction.VariantFactionData;
import com.alien.common.gameplay.hive.faction.VariantFactionRegistry;
import com.alien.common.gameplay.hive.growth.BiomassIncome;
import com.alien.common.gameplay.hive.growth.CatchUpEngine;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.alien.common.gameplay.hive.id.HiveLocationIds;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.id.VariantIds;
import com.alien.common.gameplay.hive.lifecycle.LocationDeathHandler;
import com.alien.common.gameplay.hive.lifecycle.QueenSettlementDetector;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.level.saveddata.TrackedQueenRegistry;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.RaidWaveProfileRegistry;
import com.alien.common.registry.init.AlienFactionDataTypes;
import com.blib.api.common.faction.v1.FactionMember;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Debug-only commands for inspecting and seeding the new hive system. Live at {@code /avp_alien debug hive ...}. The
 * mutator subcommands ({@code
 * mint_lineage_at_player}, {@code mint_location_in_lineage}) bypass the empress + spread-zone gates that
 * production-path founding will enforce once Phase 5 lands.
 */
public final class HiveDebugCommands {

    private static final String LINEAGE_ID_ARG = "lineage_id";

    private static final String LOCATION_ID_ARG = "location_id";

    private static final String VARIANT_ARG = "variant";

    private static final int FORCE_JOIN_RADIUS_BLOCKS = 64;

    private static final String ENTITY_TYPE_ARG = "entity_type";

    private static final String COUNT_ARG = "count";

    private HiveDebugCommands() {}

    /**
     * ⭐⭐⭐ THE GROUPED COMMAND TREE. Was 52 FLAT subcommands under {@code /avp_alien debug hive <name>}.
     * <p>
     * [stated] "i think theres too many i think we can condense ... we need to have them seperated properly as location
     * supply remove trigger and any other categories" and [stated] "can we further reduce it to just /avp hive".
     * </p>
     * <p>
     * NOTHING WAS DELETED — every handler below is still reachable. The savings are structural:
     * <ul>
     * <li>SEVEN position-based inspects folded into {@code here}, which is also the answer to "an easier way to get the
     * location id and lineage id" — it resolves both from under your feet and prints them click-copyable.</li>
     * <li>SEVEN legacy-queen commands folded into {@code legacy &lt;list|wake|kill&gt; [all|area|nearest]}. They were
     * the same two verbs crossed with the same three scopes, written out longhand.</li>
     * <li>{@code dump_indexes} + {@code rebuild_indexes} → {@code maintenance indexes &lt;dump|rebuild&gt;};
     * {@code validate} + {@code force_invariant_check} → {@code maintenance check &lt;registry|invariants&gt;}.</li>
     * </ul>
     * </p>
     */
    public static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("hive")
            .then(here())
            .then(inspect())
            .then(list())
            .then(trigger())
            .then(supply())
            .then(remove())
            .then(create_())
            .then(legacy())
            .then(maintenance())
            .then(config())
            .then(diag())
            .then(debugToggles());
    }

    /**
     * ⭐⭐ EVERYTHING UNDER YOUR FEET, IN ONE COMMAND. The seven inspects that took no argument and read from the
     * player's position were seven commands only because nobody had grouped them.
     * <p>
     * Bare {@code here} runs the settlement read, which is the one that names both the location and its lineage — so
     * the ID hunt that used to be {@code list_lineages} → copy → {@code inspect_lineage} → copy is now one command.
     * </p>
     */
    private static LiteralArgumentBuilder<CommandSourceStack> here() {
        return Commands.literal("here")
            .requires(CommandSourceStack::isPlayer)
            .executes(HiveDebugCommands::inspectHere)
            .then(Commands.literal("settlement").executes(HiveDebugCommands::inspectSettlement))
            .then(Commands.literal("queen").executes(HiveDebugCommands::inspectQueen))
            .then(Commands.literal("empress").executes(HiveDebugCommands::inspectEmpress))
            .then(Commands.literal("targeting").executes(HiveDebugCommands::inspectTargeting))
            .then(Commands.literal("hunters").executes(HiveDebugCommands::inspectHunters))
            .then(Commands.literal("ovipositor").executes(HiveDebugCommands::inspectOvipositor))
            .then(Commands.literal("slab").executes(HiveDebugCommands::inspectSlab))
            .then(Commands.literal("maturation").executes(HiveDebugCommands::inspectQueenlessMaturation));
    }

    /** Reads that need an ID. Everything positional lives under {@link #here()} instead. */
    private static LiteralArgumentBuilder<CommandSourceStack> inspect() {
        return Commands.literal("inspect")
            .then(
                Commands.literal("location")
                    .executes(HiveDebugCommands::inspectLocation)
                    .then(
                        Commands.argument(LOCATION_ID_ARG, ResourceLocationArgument.id())
                            .suggests(HiveDebugCommands::suggestHiveNames)
                            .executes(HiveDebugCommands::inspectLocation)
                    )
            )
            .then(
                Commands.literal("lineage")
                    .executes(HiveDebugCommands::inspectLineageNearest)
                    .then(
                        Commands.argument(LINEAGE_ID_ARG, ResourceLocationArgument.id())
                            .suggests(HiveDebugCommands::suggestLineageNames)
                            .executes(HiveDebugCommands::inspectLineageById)
                    )
            )
            .then(
                Commands.literal("variant")
                    .then(
                        Commands.argument(VARIANT_ARG, StringArgumentType.string())
                            .executes(HiveDebugCommands::inspectVariant)
                    )
            )
            .then(
                Commands.literal("kills")
                    .then(
                        Commands.argument(LINEAGE_ID_ARG, ResourceLocationArgument.id())
                            .suggests(HiveDebugCommands::suggestLineageNames)
                            .executes(HiveDebugCommands::inspectKillAttribution)
                    )
            );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> list() {
        return Commands.literal("list")
            .then(Commands.literal("lineages").executes(HiveDebugCommands::listLineages))
            .then(Commands.literal("tracked").executes(HiveDebugCommands::listTracked))
            .then(Commands.literal("convoys").executes(HiveDebugCommands::listConvoys))
            .then(Commands.literal("emerging").executes(HiveDebugCommands::listEmerging))
            .then(
                Commands.literal("vents")
                    .executes(HiveDebugCommands::listVents)
                    .then(
                        Commands.argument(LOCATION_ID_ARG, ResourceLocationArgument.id())
                            .suggests(HiveDebugCommands::suggestHiveNames)
                            .executes(HiveDebugCommands::listVents)
                    )
            );
    }

    /** Anything that makes the hive DO something now, rather than reading or granting. */
    private static LiteralArgumentBuilder<CommandSourceStack> trigger() {
        return Commands.literal("trigger")
            .then(Commands.literal("emergence_scan").executes(HiveDebugCommands::forceEmergenceScan))
            .then(Commands.literal("reinforcements").executes(HiveDebugCommands::forceDispatchReinforcements))
            .then(
                Commands.literal("party")
                    .requires(CommandSourceStack::isPlayer)
                    .then(Commands.literal("host_hunt").executes(ctx -> forceParty(ctx, "host_hunt")))
                    .then(Commands.literal("biomass_hunting").executes(ctx -> forceParty(ctx, "biomass_hunting")))
                    .then(Commands.literal("surface_spawn").executes(ctx -> forceParty(ctx, "surface_spawn")))
            )
            .then(
                Commands.literal("raid")
                    .requires(CommandSourceStack::isPlayer)
                    .then(
                        Commands.argument(LINEAGE_ID_ARG, ResourceLocationArgument.id())
                            .suggests(HiveDebugCommands::suggestLineageNames)
                            .executes(HiveDebugCommands::forceRaidOnSelf)
                    )
            )
            .then(
                Commands.literal("migration")
                    .executes(HiveDebugCommands::forceMigration)
                    .then(
                        Commands.argument(LOCATION_ID_ARG, ResourceLocationArgument.id())
                            .suggests(HiveDebugCommands::suggestHiveNames)
                            .executes(HiveDebugCommands::forceMigration)
                    )
            )
            .then(
                Commands.literal("grow")
                    .executes(HiveDebugCommands::forceGrowLocation)
                    .then(
                        Commands.argument(LOCATION_ID_ARG, ResourceLocationArgument.id())
                            .suggests(HiveDebugCommands::suggestHiveNames)
                            .executes(HiveDebugCommands::forceGrowLocation)
                    )
            )
            .then(
                Commands.literal("queenless_advance")
                    .then(
                        Commands.argument(LINEAGE_ID_ARG, ResourceLocationArgument.id())
                            .suggests(HiveDebugCommands::suggestLineageNames)
                            .executes(HiveDebugCommands::forceQueenlessAdvance)
                    )
            )
            .then(
                Commands.literal("join_nearby")
                    .requires(CommandSourceStack::isPlayer)
                    .then(Commands.literal("variant").executes(HiveDebugCommands::forceVariantJoinNearby))
                    .then(Commands.literal("shed_check").executes(HiveDebugCommands::forceShedCheckNearby))
                    .then(
                        Commands.literal("lineage")
                            .then(
                                Commands.argument(LINEAGE_ID_ARG, ResourceLocationArgument.id())
                                    .suggests(HiveDebugCommands::suggestLineageNames)
                                    .executes(HiveDebugCommands::forceLineageJoinNearby)
                            )
                    )
            )
            .then(
                Commands.literal("skip")
                    .requires(CommandSourceStack::isPlayer)
                    .then(Commands.literal("hibernation").executes(HiveDebugCommands::hibernationSkip))
                    .then(Commands.literal("settlement").executes(HiveDebugCommands::skipSettlement))
            );
    }

    /**
     * Grants INTO a hive. [stated] "add a command for jelly to supply" — and BOTH jelly pools are here, because royal
     * and scourge are separate economies with separate caps and topping up the wrong one looks like the command
     * silently failing.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> supply() {
        return Commands.literal("supply")
            .then(
                Commands.literal("biomass")
                    .then(
                        Commands.argument(COUNT_ARG, IntegerArgumentType.integer())
                            .executes(HiveDebugCommands::addBiomass)
                            .then(
                                Commands.argument(LOCATION_ID_ARG, ResourceLocationArgument.id())
                                    .suggests(HiveDebugCommands::suggestHiveNames)
                                    .executes(HiveDebugCommands::addBiomass)
                            )
                    )
            )
            .then(
                Commands.literal("jelly")
                    .then(
                        Commands.literal("royal")
                            .then(
                                Commands.argument(COUNT_ARG, IntegerArgumentType.integer())
                                    .executes(ctx -> addJelly(ctx, true))
                                    .then(
                                        Commands.argument(LOCATION_ID_ARG, ResourceLocationArgument.id())
                                            .suggests(HiveDebugCommands::suggestHiveNames)
                                            .executes(ctx -> addJelly(ctx, true))
                                    )
                            )
                    )
                    .then(
                        Commands.literal("scourge")
                            .then(
                                Commands.argument(COUNT_ARG, IntegerArgumentType.integer())
                                    .executes(ctx -> addJelly(ctx, false))
                                    .then(
                                        Commands.argument(LOCATION_ID_ARG, ResourceLocationArgument.id())
                                            .suggests(HiveDebugCommands::suggestHiveNames)
                                            .executes(ctx -> addJelly(ctx, false))
                                    )
                            )
                    )
            )
            .then(
                Commands.literal("reserve")
                    .then(
                        Commands.argument(ENTITY_TYPE_ARG, ResourceLocationArgument.id())
                            .then(
                                Commands.argument(COUNT_ARG, IntegerArgumentType.integer(1))
                                    .executes(HiveDebugCommands::addReserve)
                                    .then(
                                        Commands.argument(LOCATION_ID_ARG, ResourceLocationArgument.id())
                                            .suggests(HiveDebugCommands::suggestHiveNames)
                                            .executes(HiveDebugCommands::addReserve)
                                    )
                            )
                    )
            );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> remove() {
        return Commands.literal("remove")
            .then(
                Commands.literal("location")
                    .then(
                        Commands.argument(LOCATION_ID_ARG, ResourceLocationArgument.id())
                            .suggests(HiveDebugCommands::suggestHiveNames)
                            .executes(HiveDebugCommands::killLocation)
                    )
            )
            .then(
                Commands.literal("parties")
                    .requires(CommandSourceStack::isPlayer)
                    .executes(HiveDebugCommands::clearParties)
            )
            .then(
                // ⚠⚠ NO "all" LITERAL ON ITS OWN. The word after `remove` decides whether one hive dies or every
                // hive in the world does, and `location` vs `all` is a single keystroke apart in a command the
                // player is already typing under pressure. The confirm token below is what actually protects this.
                Commands.literal("all")
                    .executes(HiveDebugCommands::wipeAllPrompt)
                    .then(
                        Commands.argument(WIPE_CONFIRM_ARG, StringArgumentType.word())
                            .executes(HiveDebugCommands::wipeAllConfirmed)
                    )
            );
    }

    private static final String WIPE_CONFIRM_ARG = "confirm";

    /** One-shot tokens, per player. ⚠ Cleared on use, so a re-run of the same chat click cannot fire twice. */
    private static final Map<UUID, String> PENDING_WIPE = new HashMap<>();

    /**
     * ⭐⭐⭐ THE WARNING, WITH A CLICKABLE CONFIRM. Step one of two - this NEVER destroys anything.
     * <p>
     * [stated] "write in a proper kill command with a warning asking to confirm with a button menu popup."
     * </p>
     * <p>
     * ⚠⚠ A TYPED CONFIRM WOULD NOT BE ENOUGH. The whole point is that the player reads what they are about to lose
     * FIRST - so the prompt counts the hives, lineages and members by name before offering the button, and the token is
     * random so nobody can learn the confirm string and skip the count.
     * </p>
     */
    private static int wipeAllPrompt(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var locations = new ArrayList<>(HiveLocationRegistry.INSTANCE.all());
        var lineages = new java.util.HashSet<net.minecraft.resources.ResourceLocation>();
        for (var location : locations) {
            lineages.add(location.lineageFactionId());
        }

        // ⚠⚠ COUNT THE SAME WAY THE WIPE DESTROYS - by SWEEPING, not by summing hive membership. The first version
        // counted totalReliableXenomorphPopulation per location and so promised a number that excluded every alien
        // belonging to no hive, which is precisely the set that kept surviving the wipe.
        var members = 0;
        for (var level : source.getServer().getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (
                    entity instanceof com.alien.common.gameplay.entity.living.alien.Alien
                        || entity instanceof com.alien.common.gameplay.entity.living.alien.ovipositor.Ovipositor
                        || entity instanceof com.alien.common.gameplay.entity.living.alien.royal_cocoon.RoyalCocoon
                ) {
                    members++;
                }
            }
        }

        // ⚠ Counted for the prompt as well as destroyed, because they are the reason a "clean" world still refuses
        // to let a queen found - a player who is not told about them cannot understand the result.
        // 🚨🚨 THE LEGACY DATA IS A REASON TO RUN EVEN WITH NOTHING ELSE LEFT.
        //
        // ⚠⚠ MY EARLY-OUT MADE THE WIPE A NO-OP ON AN ALREADY-EMPTY WORLD, AND THAT IS EXACTLY WHEN IT WAS NEEDED.
        // A player who had already wiped ran it again to clear the legacy data, got "There are no hives to remove",
        // and the command returned BEFORE setting the purge flag - so recovery re-minted the sleepers on the next
        // load and the "old echoes" refusal came straight back. The one case the guard rejected was the one case
        // that still had work to do.
        var legacyPending = com.alien.common.gameplay.hive.migration.LegacyHiveRecoveryData
            .getOrCreate(source.getServer())
            .isSomeAnd(data -> !data.legacyPurged());

        if (locations.isEmpty() && members == 0 && !legacyPending) {
            source.sendSuccess(() -> Component.literal("There are no hives to remove."), false);
            return 0;
        }

        var token = Long.toHexString(java.util.concurrent.ThreadLocalRandom.current().nextLong() & 0xFFFFFFL);
        var playerId = source.getEntity() == null ? null : source.getEntity().getUUID();
        if (playerId != null) {
            PENDING_WIPE.put(playerId, token);
        }

        var locationCount = locations.size();
        var lineageCount = lineages.size();
        var memberCount = members;

        source.sendSuccess(
            () -> Component.literal("⚠ THIS REMOVES EVERY HIVE IN THE WORLD ⚠")
                .withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD),
            false
        );
        source.sendSuccess(
            () -> Component.literal(
                "  " + locationCount + " hive(s), " + lineageCount + " lineage(s), "
                    + memberCount + " alien(s) will be destroyed."
            ).withStyle(net.minecraft.ChatFormatting.GRAY),
            false
        );
        source.sendSuccess(
            () -> Component.literal("  This cannot be undone. Back up your save first.")
                .withStyle(net.minecraft.ChatFormatting.GRAY),
            false
        );
        source.sendSuccess(
            () -> Component.literal("[ CONFIRM - DESTROY EVERYTHING ]")
                .withStyle(
                    style -> style.withColor(net.minecraft.ChatFormatting.DARK_RED)
                        .withBold(true)
                        .withClickEvent(
                            new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                "/avp hive remove all " + token
                            )
                        )
                        .withHoverEvent(
                            new net.minecraft.network.chat.HoverEvent(
                                net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                Component.literal("No further prompt. Everything above is destroyed immediately.")
                            )
                        )
                ),
            false
        );
        source.sendSuccess(
            () -> Component.literal("[ Cancel ]")
                .withStyle(
                    style -> style.withColor(net.minecraft.ChatFormatting.GREEN)
                        .withClickEvent(
                            new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                "/avp hive remove all cancel"
                            )
                        )
                ),
            false
        );
        return 1;
    }

    /**
     * Step two. Removes every location, its lineage, and every member those hives own.
     * <p>
     * ⚠⚠ MEMBERS ARE DISCARDED TOO, AND THAT IS THE POINT. {@code killAdmin} removes the LOCATION and deliberately
     * leaves members in the parent lineage - correct for one hive dying, useless for a reset, because it leaves a world
     * full of homeless xenomorphs that get adopted or found again. A player asking for a clean slate means a clean
     * slate.
     * </p>
     */
    private static int wipeAllConfirmed(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var given = StringArgumentType.getString(ctx, WIPE_CONFIRM_ARG);
        var playerId = source.getEntity() == null ? null : source.getEntity().getUUID();

        if ("cancel".equalsIgnoreCase(given)) {
            if (playerId != null) {
                PENDING_WIPE.remove(playerId);
            }
            source.sendSuccess(() -> Component.literal("Cancelled. Nothing was removed."), false);
            return 1;
        }

        var expected = playerId == null ? null : PENDING_WIPE.remove(playerId);
        if (expected == null || !expected.equals(given)) {
            // ⚠ Covers a stale chat click from an earlier prompt as well as a guessed token - either way the count
            // they were shown is no longer the world they would be destroying.
            source.sendFailure(
                Component.literal("That confirmation has expired. Run /avp hive remove all again to see the count.")
            );
            return 0;
        }

        // 🚨🚨 THE LEGACY DATA MUST GO TOO, OR THE SLATE IS NOT CLEAN.
        //
        // ⚠⚠ MY FIRST VERSION LEFT IT AND MY OWN "old echoes" RULE THEN BLOCKED THE FRESH START. A player wiped his
        // world, placed a new queen, and she was refused founding by a SLEEPING LEGACY QUEEN reserving ground that
        // he had just told the game to clear - "Legacy hive recovery: detected old hive data and repaired 18/18 hive
        // snapshot(s)" in his log, after remove all reported success. The two features were correct on their own and
        // wrong together.
        //
        // ⭐ Clearing legacyDetected is the switch isAwaitingLegacyWake reads FIRST, so it retires every dormant
        // queen at once - they stop reserving ground, stop being exempt from dormancy, and behave as ordinary
        // xenomorphs from here.
        com.alien.common.gameplay.hive.migration.LegacyHiveRecoveryData.getOrCreate(source.getServer())
            .ifSome(data -> {
                data.setLegacyDetected(false);
                data.setRecoveryApplied(true);
                // ⭐ The one-way flag that makes the wipe survive a restart - see LegacyHiveRecoveryData#legacyPurged.
                data.setLegacyPurged(true);
            });

        // 🚨🚨 AND THE SLEEPING QUEENS THEMSELVES, WHICH THE LOOP BELOW WILL NEVER REACH.
        //
        // ⚠⚠ CLEARING THE RECOVERY DATA IS NOT ENOUGH AND MY FIRST FIX FOR THIS WAS WRONG. `legacyDormant` is a
        // field ON THE QUEEN ENTITY, and the founding rule filters on `queen.isLegacyDormant()` directly - so a
        // queen already standing in the world keeps reserving her ground no matter what the save data says.
        //
        // ⚠⚠ AND SHE IS IN NO LOCATION'S MEMBER LIST. An unrecovered legacy queen belongs to no hive at all, so the
        // per-location sweep below cannot see her: every claim really was dead and she was still there. That is
        // exactly the report - "still getting the old echoes message" on a world with nothing left to own it.
        // ⚠ The queens themselves are handled by the world sweep below - she is an Alien like any other. What is
        // cleared HERE is the save data, so recovery cannot mint a fresh set of them on the next load.

        var removedLocations = 0;
        for (var location : new ArrayList<>(HiveLocationRegistry.INSTANCE.all())) {
            var serverLevel = source.getServer().getLevel(location.dimension());
            if (serverLevel == null) {
                continue; // an unloaded dimension's hives are untouched; say so in the summary rather than pretend
            }
            var faction = Alien.MOD.factions().get(location.lineageFactionId());
            if (faction != null && faction.data() instanceof LineageFactionData lineage) {
                LocationDeathHandler.killAdmin(serverLevel, location, lineage, "remove all debug command");
                removedLocations++;
            }
        }

        // ⭐⭐⭐ THEN SWEEP THE WHOLE WORLD. EVERY ALIEN, WHATEVER IT BELONGS TO.
        //
        // [stated] "it shouldnt matter if shes in a hive or running to the store to buy smokes all xenos killed
        // means all xenos killed ... its meant to wipe a world as if it never had aliens to begin with the only
        // thing left behind is the blocks."
        //
        // ⚠⚠ I BUILT THIS THREE TIMES AS "ENUMERATE HIVES AND THEIR MEMBERS" AND PATCHED A CATEGORY EACH TIME - the
        // loaded list, then the bank, then sleeping legacy queens - and every pass missed whatever belonged to no
        // hive. A stray adopted by nobody, a hauler mid-journey, a facehugger on a host, a queen walking to found:
        // none of them are in a member list. ENUMERATING CATEGORIES IS THE WRONG SHAPE. The instruction is every
        // alien, so the code is every alien.
        //
        // ⚠ Alien is the root of everything living the mod spawns - Xenomorph, Parasite and Ovomorph all extend it.
        // Ovipositor and RoyalCocoon do not (they are plain Mobs), so they are named explicitly rather than assumed.
        var removedMembers = 0;
        for (var level : source.getServer().getAllLevels()) {
            // ⚠ getAllEntities returns an ITERABLE, not a Collection, so it cannot be handed to ArrayList directly -
            // and it must be copied before discarding, because discarding mutates the very list being walked.
            var present = new ArrayList<net.minecraft.world.entity.Entity>();
            level.getAllEntities().forEach(present::add);
            for (var entity : present) {
                if (
                    entity instanceof com.alien.common.gameplay.entity.living.alien.Alien
                        || entity instanceof com.alien.common.gameplay.entity.living.alien.ovipositor.Ovipositor
                        || entity instanceof com.alien.common.gameplay.entity.living.alien.royal_cocoon.RoyalCocoon
                ) {
                    entity.discard();
                    removedMembers++;
                }
            }
        }

        var locationTotal = removedLocations;
        var memberTotal = removedMembers;
        var remaining = HiveLocationRegistry.INSTANCE.all().size();
        source.sendSuccess(
            () -> Component.literal(
                "Removed " + locationTotal + " hive(s), " + memberTotal + " xenomorph(s)"
                    + "."
                    + (remaining == 0 ? "" : " " + remaining + " remain in unloaded dimensions.")
            ),
            true
        );
        return 1;
    }

    /** ⚠ Named {@code create_} because {@link #create()} is the tree root — the command word is still "create". */
    private static LiteralArgumentBuilder<CommandSourceStack> create_() {
        return Commands.literal("create")
            .then(
                Commands.literal("lineage")
                    .requires(CommandSourceStack::isPlayer)
                    .executes(ctx -> mintLineageAtPlayer(ctx, AlienVariant.NORMAL))
                    .then(
                        Commands.argument(VARIANT_ARG, StringArgumentType.string())
                            .executes(ctx -> {
                                var variantName = StringArgumentType.getString(ctx, VARIANT_ARG)
                                    .toUpperCase(Locale.ROOT);
                                AlienVariant variant;

                                try {
                                    variant = AlienVariant.valueOf(variantName);
                                } catch (IllegalArgumentException ignored) {
                                    ctx.getSource()
                                        .sendFailure(
                                            Component.literal("Unknown variant: " + variantName)
                                        );
                                    return 0;
                                }

                                return mintLineageAtPlayer(ctx, variant);
                            })
                    )
            )
            .then(
                Commands.literal("location")
                    .requires(CommandSourceStack::isPlayer)
                    .then(
                        Commands.argument(LINEAGE_ID_ARG, ResourceLocationArgument.id())
                            .suggests(HiveDebugCommands::suggestLineageNames)
                            .executes(HiveDebugCommands::mintLocationInLineage)
                    )
            )
            .then(Commands.literal("inhibit_here").executes(HiveDebugCommands::inhibitHere))
            .then(Commands.literal("web_host").executes(HiveDebugCommands::webHost))
            .then(
                Commands.literal("claim_radius")
                    .then(
                        Commands.argument("radius", IntegerArgumentType.integer(0, 32))
                            .executes(HiveDebugCommands::claimRadius)
                            .then(
                                Commands.argument(LOCATION_ID_ARG, ResourceLocationArgument.id())
                                    .suggests(HiveDebugCommands::suggestHiveNames)
                                    .executes(HiveDebugCommands::claimRadius)
                            )
                    )
            );
    }

    /**
     * ⭐⭐ SEVEN COMMANDS BECOME TWO VERBS CROSSED WITH THREE SCOPES, which is what they always were.
     * {@code wake_nearest_legacy_queen} / {@code wake_legacy_queens_in_area} / {@code wake_all_legacy_queens} and the
     * three {@code kill_} twins were the same grid written out longhand.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> legacy() {
        return Commands.literal("legacy")
            .then(Commands.literal("list").executes(HiveDebugCommands::listLegacyQueens))
            .then(Commands.literal("purge").executes(HiveDebugCommands::purgeLegacy))
            .then(
                Commands.literal("wake")
                    .then(Commands.literal("nearest").executes(HiveDebugCommands::wakeNearestLegacyQueen))
                    .then(Commands.literal("all").executes(HiveDebugCommands::wakeAllLegacyQueens))
                    .then(
                        Commands.literal("area")
                            .then(
                                Commands.argument("radius", IntegerArgumentType.integer(1, MAX_LEGACY_AREA_RADIUS))
                                    .executes(HiveDebugCommands::wakeLegacyQueensInArea)
                            )
                    )
            )
            .then(
                Commands.literal("kill")
                    .then(Commands.literal("nearest").executes(HiveDebugCommands::killNearestLegacyQueen))
                    .then(Commands.literal("all").executes(HiveDebugCommands::killAllLegacyQueens))
                    .then(
                        Commands.literal("area")
                            .then(
                                Commands.argument("radius", IntegerArgumentType.integer(1, MAX_LEGACY_AREA_RADIUS))
                                    .executes(HiveDebugCommands::killLegacyQueensInArea)
                            )
                    )
            );
    }

    /**
     * \u2b50\u2b50 THE CONFIG, IN GAME. [stated] set applies LIVE; anything needing a reload says so.
     * <p>
     * {@code set} writes the file immediately, so a value tuned in-game survives the restart \u2014 which is the whole
     * point, since until now the in-game inspector was the ONLY way to change a value and every change was lost.
     * </p>
     */
    /**
     * \u26a0\u26a0 A TOP-LEVEL GROUP, NOT A CHILD OF config(). I first nested this inside the config group by mistake,
     * which made the real command "/avp hive config diag start" while every instruction said "/avp hive diag start" -
     * and the only feedback was Brigadier's "Incorrect argument for command". A diagnostic nobody can invoke is worse
     * than no diagnostic.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> diag() {
        return Commands.literal("diag")
            .then(Commands.literal("start").executes(HiveDebugCommands::diagStart))
            .then(Commands.literal("stop").executes(HiveDebugCommands::diagStop))
            // ⭐ "all" measures the WHOLE SERVER, not just this mod. The hive diagnostic can only ever say "avp_alien
            // was 1.7% - look elsewhere"; this says WHERE.
            .then(
                Commands.literal("all")
                    .then(Commands.literal("start").executes(HiveDebugCommands::wideDiagStart))
                    .then(Commands.literal("stop").executes(HiveDebugCommands::wideDiagStop))
            )
            // ⭐ Watches the nearest xenomorph and logs which animation branch it takes, every tick.
            .then(Commands.literal("anim").executes(HiveDebugCommands::animDiag));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> config() {
        return Commands.literal("config")
            .then(
                Commands.literal("list")
                    .executes(ctx -> listConfig(ctx, null))
                    .then(
                        Commands.argument("group", StringArgumentType.greedyString())
                            .executes(ctx -> listConfig(ctx, StringArgumentType.getString(ctx, "group")))
                    )
            )
            .then(
                Commands.literal("get")
                    .then(
                        Commands.argument("field", StringArgumentType.word())
                            .executes(HiveDebugCommands::getConfig)
                    )
            )
            .then(
                Commands.literal("set")
                    .then(
                        Commands.argument("field", StringArgumentType.word())
                            .then(
                                Commands.argument("value", StringArgumentType.greedyString())
                                    .executes(HiveDebugCommands::setConfig)
                            )
                    )
            )
            .then(
                Commands.literal("gui")
                    .requires(CommandSourceStack::isPlayer)
                    .executes(HiveDebugCommands::openConfigGui)
            )
            .then(Commands.literal("reload").executes(HiveDebugCommands::reloadConfig))
            .then(Commands.literal("save").executes(HiveDebugCommands::saveConfig));
    }

    /** Every field, or one group's worth. Values that differ from the default are marked so tuning is visible. */
    private static int listConfig(
        com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
        @org.jetbrains.annotations.Nullable String groupFilter
    ) {
        var source = ctx.getSource();
        var config = HiveLocationRegistry.INSTANCE.config();
        var shown = 0;

        for (var group : com.alien.common.gameplay.hive.config.HiveConfigSchema.groups()) {
            if (groupFilter != null && !group.name().equalsIgnoreCase(groupFilter)) {
                continue;
            }
            source.sendSuccess(() -> Component.literal("-- " + group.name()).withStyle(ChatFormatting.GRAY), false);
            for (var field : group.fields()) {
                var value = com.alien.common.gameplay.hive.config.HiveConfigSchema.valueAsString(config, field.name());
                var changed = !value.equals(field.defaultValue());
                source.sendSuccess(
                    () -> Component.literal("  " + field.name() + " = ")
                        .append(
                            Component.literal(value)
                                .withStyle(changed ? ChatFormatting.YELLOW : ChatFormatting.WHITE)
                        )
                        .append(
                            Component.literal(changed ? "  (default " + field.defaultValue() + ")" : "")
                                .withStyle(ChatFormatting.DARK_GRAY)
                        ),
                    false
                );
                shown++;
            }
        }

        if (shown == 0) {
            source.sendFailure(Component.literal("No config group matched '" + groupFilter + "'."));
            return 0;
        }
        return shown;
    }

    private static int getConfig(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var name = StringArgumentType.getString(ctx, "field");
        var field = com.alien.common.gameplay.hive.config.HiveConfigSchema.field(name);
        if (field == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown config field: " + name));
            return 0;
        }
        var value = com.alien.common.gameplay.hive.config.HiveConfigSchema
            .valueAsString(HiveLocationRegistry.INSTANCE.config(), name);
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    name + " = " + value + "  (" + field.type().displayName()
                        + ", default " + field.defaultValue() + ", group " + field.group() + ")"
                ),
                false
            );

        // \u2b50 The plain-language explanation, right under the value. Same text the companion reference file
        // carries, so an admin reading it in chat and a pack author reading it on disk get the identical wording.
        var description = com.alien.common.gameplay.hive.config.HiveConfigDescriptions.of(name);
        if (description != null) {
            ctx.getSource()
                .sendSuccess(
                    () -> Component.literal("  " + description).withStyle(ChatFormatting.GRAY),
                    false
                );
        }
        return 1;
    }

    /**
     * \u26a0 APPLIES LIVE AND SAVES. Most fields are read every time they are used, so the change bites immediately.
     * The exceptions are values captured ONCE when something was created \u2014 a hive's slab band, a convoy's speed,
     * an existing location's claim radius \u2014 which keep the value they were built with. The reply says so rather
     * than pretending every field is instant.
     */
    private static int setConfig(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var name = StringArgumentType.getString(ctx, "field");
        var value = StringArgumentType.getString(ctx, "value");
        var field = com.alien.common.gameplay.hive.config.HiveConfigSchema.field(name);
        if (field == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown config field: " + name));
            return 0;
        }

        try {
            var updated = com.alien.common.gameplay.hive.config.HiveConfigSchema
                .withParsedValue(HiveLocationRegistry.INSTANCE.config(), name, value);
            HiveLocationRegistry.INSTANCE.setConfig(updated);
        } catch (RuntimeException exception) {
            ctx.getSource()
                .sendFailure(
                    Component.literal("'" + value + "' is not a valid " + field.type().displayName() + " for " + name)
                );
            return 0;
        }

        var server = ctx.getSource().getServer();
        com.alien.common.gameplay.hive.config.HiveConfigFile.save(server);

        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(name + " = " + value + " (saved)")
                    .append(
                        Component.literal("  existing hives keep values captured when they were founded")
                            .withStyle(ChatFormatting.DARK_GRAY)
                    ),
                true
            );
        return 1;
    }

    /**
     * \u2b50\u2b50 Sends the live config to the player, whose client opens the screen on it.
     * <p>
     * \u26a0 THE COMMAND CANNOT OPEN THE SCREEN ITSELF - it runs on the SERVER and screens are client-side. Sending the
     * values and letting the client open on arrival also means the screen never exists for a moment showing defaults it
     * is about to overwrite.
     * </p>
     */
    /**
     * ⭐⭐ Starts the diagnostic. Deliberately trivial to explain: start, play, stop, send the file.
     * <p>
     * ⚠ The reply names the file explicitly, because the whole point is that the reporter should not have to know where
     * anything lives or how to read a profiler.
     * </p>
     */
    /**
     * Starts the server-wide capture.
     * <p>
     * ⚠ THE WARNING IN THE MESSAGE IS NOT BOILERPLATE. A running capture times every entity and block entity on the
     * server, which costs TPS in itself - so a long capture distorts the very thing being measured. Thirty seconds to a
     * minute of the laggy behaviour is plenty.
     * </p>
     */
    /**
     * Watches the nearest xenomorph for 10 seconds and logs its animation decision every tick.
     * <p>
     * ⚠ Stand next to the mob that is misbehaving and run it, then let it do the thing - idle, walk, go stiff. The log
     * shows whether the branch is wrong, flip-flopping, or correct-but-not-playing, which look identical on screen and
     * have completely different causes.
     * </p>
     */
    private static int animDiag(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var subject = nearestXenomorph(ctx);

        if (subject == null) {
            ctx.getSource().sendFailure(Component.literal("No xenomorph within 32 blocks."));
            return 0;
        }

        com.alien.common.gameplay.hive.diag.AnimationDiag.watch(subject.getUUID(), 200);
        Alien.LOGGER.info(
            "[animdiag] === watching {} ({}) for 200 ticks ===",
            subject.getType().getDescriptionId(),
            subject.getUUID()
        );

        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "Watching " + subject.getType().getDescriptionId()
                        + " for 10s - see [animdiag] in latest.log."
                ),
                false
            );
        return 1;
    }

    private static int wideDiagStart(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        com.alien.common.gameplay.hive.diag.WideDiagProfiler.start();
        ctx.getSource()
            .sendSuccess(
                () -> net.minecraft.network.chat.Component.literal(
                    "Server-wide diagnostic started - this itself costs some TPS. Let it run through the lag for "
                        + "30-60 seconds, then: /avp hive diag all stop"
                ),
                false
            );
        return 1;
    }

    private static int wideDiagStop(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var report = com.alien.common.gameplay.hive.diag.WideDiagProfiler.stop(ctx.getSource().getServer());

        if (report == null) {
            ctx.getSource().sendFailure(Component.literal("No server-wide diagnostic was running."));
            return 0;
        }

        // ⚠ THE LOG IS THE REAL OUTPUT. Chat truncates and scrolls; the report is wide and meant to be read or pasted
        // from latest.log, which is also what gets sent when someone reports lag.
        for (var line : report.split("\n")) {
            Alien.LOGGER.info("[widediag] {}", line);
        }

        ctx.getSource()
            .sendSuccess(
                () -> Component.literal("Server-wide diagnostic written to the log - see [widediag] in latest.log."),
                false
            );
        return 1;
    }

    private static int diagStart(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        com.alien.common.gameplay.hive.diag.DiagProfiler.start(ctx.getSource().getServer());
        ctx.getSource()
            .sendSuccess(
                () -> net.minecraft.network.chat.Component.literal(
                    "Hive diagnostic started. Play until it lags, then run: /avp hive diag stop"
                ),
                false
            );
        return 1;
    }

    /**
     * ⭐⭐ Stops the diagnostic and offers BOTH routes: a file, and a click-to-copy.
     * <p>
     * ⚠ THE CLIPBOARD IS THE ROUTE MOST REPORTERS WILL USE. Plenty of people have no idea where their game directory
     * is, and "send me the file at config/avp_alien-diag-....txt" ends the conversation. One click and a paste into
     * Discord does not.
     * </p>
     */
    private static int diagStop(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var report = com.alien.common.gameplay.hive.diag.DiagProfiler.stop(ctx.getSource().getServer());
        if (report == null) {
            ctx.getSource()
                .sendFailure(
                    net.minecraft.network.chat.Component.literal("No diagnostic is running.")
                );
            return 0;
        }

        // ⚠ A chat packet is size-limited, and a long report would silently fail to send rather than truncate
        // visibly. Trimming the CLIPBOARD copy keeps the click working; the file on disk always holds everything.
        var clipboard = report.text().length() > CLIPBOARD_LIMIT
            ? report.text().substring(0, CLIPBOARD_LIMIT)
                + "\n\n[trimmed - the full report is in the file named above]"
            : report.text();

        var copy = net.minecraft.network.chat.Component.literal("[Click here to copy the report]")
            .withStyle(
                style -> style
                    .withColor(net.minecraft.ChatFormatting.GREEN)
                    .withClickEvent(
                        new net.minecraft.network.chat.ClickEvent(
                            net.minecraft.network.chat.ClickEvent.Action.COPY_TO_CLIPBOARD,
                            clipboard
                        )
                    )
                    .withHoverEvent(
                        new net.minecraft.network.chat.HoverEvent(
                            net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                            net.minecraft.network.chat.Component.literal("Copies the whole report - paste it to the devs")
                        )
                    )
            );

        ctx.getSource()
            .sendSuccess(
                () -> net.minecraft.network.chat.Component.literal(
                    report.file() == null
                        ? "Hive diagnostic finished (the file could not be written)."
                        : "Hive diagnostic written to: " + report.file()
                ),
                false
            );
        ctx.getSource().sendSuccess(() -> copy, false);
        return 1;
    }

    /** Chat packets are size-limited; the file always has the untrimmed report. */
    private static final int CLIPBOARD_LIMIT = 30000;

    private static int openConfigGui(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var player = ctx.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        com.alien.Alien.MOD
            .networking()
            .sendToClient(
                player,
                new com.alien.common.network.payload.S2CHiveConfigSnapshotPayload(
                    com.alien.common.gameplay.hive.config.HiveConfigSchema
                        .toTag(HiveLocationRegistry.INSTANCE.config())
                )
            );
        return 1;
    }

    private static int reloadConfig(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        com.alien.common.gameplay.hive.config.HiveConfigFile.load(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("Hive config reloaded from disk."), true);
        return 1;
    }

    private static int saveConfig(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        com.alien.common.gameplay.hive.config.HiveConfigFile.save(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("Hive config written to disk."), true);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> maintenance() {
        return Commands.literal("maintenance")
            .then(
                Commands.literal("indexes")
                    .then(Commands.literal("dump").executes(HiveDebugCommands::dumpIndexes))
                    .then(Commands.literal("rebuild").executes(HiveDebugCommands::rebuildIndexes))
            )
            .then(
                Commands.literal("check")
                    .executes(ctx -> {
                        validate(ctx);
                        return forceInvariantCheck(ctx);
                    })
                    .then(Commands.literal("registry").executes(HiveDebugCommands::validate))
                    .then(Commands.literal("invariants").executes(HiveDebugCommands::forceInvariantCheck))
            );
    }

    /** Client-side overlays and log verbosity. Toggles, not actions — kept apart from {@link #trigger()}. */
    private static LiteralArgumentBuilder<CommandSourceStack> debugToggles() {
        return Commands.literal("debug")
            .then(
                Commands.literal("render")
                    .requires(CommandSourceStack::isPlayer)
                    .executes(HiveDebugCommands::toggleRender)
            )
            .then(
                Commands.literal("router")
                    .requires(CommandSourceStack::isPlayer)
                    .executes(HiveDebugCommands::toggleRouter)
            )
            .then(Commands.literal("log_spawns").executes(HiveDebugCommands::toggleDebugSpawns));
    }

    // ------------------------------------------------------------------------------------------------------------
    // [stated] Oct 3: hive commands accept SHORT NAMES. The full ids (lineage/20b149dc-...) are too long to type, so a
    // hive location also answers to its name - no_1_2 for "Hive NO_1_2" (variant code, lineage number, location
    // number) - and a lineage to no_1, or to the name of any of its hives. Full ids still work exactly as before.
    // ⚠ Lowercase: these arguments are Minecraft resource ids, which accept no capitals; matching ignores case.
    // ⚠ A lineage with no living hive has no hive name left to match - it still needs its full id.
    // ------------------------------------------------------------------------------------------------------------

    /** A hive location's short name, lowercase ("no_1_2"), or null if it has none yet. */
    private static @org.jetbrains.annotations.Nullable String shortName(HiveLocation location) {
        var faction = Alien.MOD.factions().get(location.id().value());
        var name = faction == null ? null : faction.name();

        if (name == null || name.isBlank()) {
            return null;
        }

        return (name.startsWith("Hive ") ? name.substring("Hive ".length()) : name).toLowerCase(Locale.ROOT);
    }

    /** "no_1_2" -> "no_1": a hive name without its location number is its lineage's short name. */
    private static String lineageShortName(String hiveShortName) {
        var cut = hiveShortName.lastIndexOf('_');
        return cut > 0 ? hiveShortName.substring(0, cut) : hiveShortName;
    }

    /** The hive location named {@code raw}'s path (a bare name parses as minecraft:no_1_2), or null. */
    private static @org.jetbrains.annotations.Nullable HiveLocation locationByShortName(net.minecraft.resources.ResourceLocation raw) {
        var wanted = raw.getPath().toLowerCase(Locale.ROOT);

        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (wanted.equals(shortName(location))) {
                return location;
            }
        }

        return null;
    }

    /**
     * The lineage argument as a lineage id: a real lineage id is used as given; otherwise a lineage short name (no_1)
     * or a hive name (no_1_2) is turned into that lineage's id. Anything unrecognised is passed through unchanged, so
     * the command reports it exactly as it always did.
     */
    private static net.minecraft.resources.ResourceLocation resolveLineageArg(
        com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx
    ) {
        var raw = ResourceLocationArgument.getId(ctx, LINEAGE_ID_ARG);

        if (Alien.MOD.factions().get(raw) != null) {
            return raw;
        }

        var wanted = raw.getPath().toLowerCase(Locale.ROOT);

        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            var name = shortName(location);

            if (name != null && (wanted.equals(name) || wanted.equals(lineageShortName(name)))) {
                return location.lineageFactionId();
            }
        }

        return raw;
    }

    /** Tab completion for hive arguments: every hive's short name. */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestHiveNames(
        com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
        com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        return net.minecraft.commands.SharedSuggestionProvider.suggest(
            HiveLocationRegistry.INSTANCE.all().stream().map(HiveDebugCommands::shortName).filter(java.util.Objects::nonNull).sorted(),
            builder
        );
    }

    /** Tab completion for lineage arguments: every living lineage's short name. */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestLineageNames(
        com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
        com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        return net.minecraft.commands.SharedSuggestionProvider.suggest(
            HiveLocationRegistry.INSTANCE.all()
                .stream()
                .map(HiveDebugCommands::shortName)
                .filter(java.util.Objects::nonNull)
                .map(HiveDebugCommands::lineageShortName)
                .distinct()
                .sorted(),
            builder
        );
    }

    /**
     * ⭐⭐ THE LOCATION ID IS OPTIONAL EVERYWHERE, AND WHEN GIVEN IT IS ALWAYS THE LAST ARGUMENT.
     * <p>
     * [stated] "for things like reserve the location id is really long and you cant actually see what you are typing in
     * so maybe make the location the last input value you enter."
     * </p>
     * <p>
     * A hive location id is a UUID-length string, so on a multi-argument command it pushed everything after it off the
     * visible width of the chat box and you were typing the amount blind. Moving it last means the short, meaningful
     * arguments are always readable, and the long one is the thing you paste and hit enter on.
     * </p>
     * <p>
     * ⭐ AND USUALLY YOU DO NOT NEED IT AT ALL. Every one of these commands is something you run while standing in the
     * hive you are talking about, so an omitted id resolves to the location under your feet. Typing the id is now the
     * exception - for a hive you are not standing in - rather than the rule.
     * </p>
     */
    private static @org.jetbrains.annotations.Nullable HiveLocation resolveLocationArgOrHere(
        com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx
    ) {
        net.minecraft.resources.ResourceLocation locationId = null;
        try {
            locationId = ResourceLocationArgument.getId(ctx, LOCATION_ID_ARG);
        } catch (IllegalArgumentException ignored) {
            // The argument node was not matched - Brigadier's own signal for "this optional was omitted".
        }

        if (locationId != null) {
            var byId = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(locationId));

            // [stated] Oct 3: a short hive name works too - no_1_2 for "Hive NO_1_2".
            if (byId == null) {
                byId = locationByShortName(locationId);
            }

            if (byId == null) {
                ctx.getSource().sendFailure(Component.literal("No hive location with id " + locationId));
            }
            return byId;
        }

        var player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("No location id given, and the console is not standing anywhere."));
            return null;
        }

        var here = HiveLocationRegistry.INSTANCE.getByChunk(player.level().dimension(), player.chunkPosition());
        if (here == null || !here.isAlive()) {
            ctx.getSource()
                .sendFailure(Component.literal("You are not standing in a hive claim - give a location id instead."));
            return null;
        }
        return here;
    }

    /**
     * ⭐⭐⭐ ONE COMMAND, EVERYTHING ABOUT WHERE YOU ARE STANDING.
     * <p>
     * [stated] "if we can condense them into one command with multiple questions answered thats fine. especially the
     * inspect here because that should give you all the info you would want about that exact area."
     * </p>
     * <p>
     * ⚠ THIS IS THE ONE PLACE THE OVERHAUL ACTUALLY REDUCES THE COMMAND COUNT rather than re-homing it. Grouping
     * fifty-two flat names under ten parents made them findable, but every leaf still had to be invoked separately -
     * eight reads meant eight commands. This runs all eight, in the order you would want them: WHERE you are, then WHO
     * is here, then WHAT they are doing.
     * </p>
     * <p>
     * Each section stays individually addressable ({@code here queen}, {@code here slab}...) for when you want one
     * answer without the wall of text. A section that finds nothing SAYS so - "no ovipositor" is information about the
     * area too.
     * </p>
     */
    private static int inspectHere(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) {
            return 0;
        }

        var pos = player.blockPosition();
        source.sendSuccess(
            () -> Component.literal("=== HIVE REPORT @ ")
                .append(copyableId(pos.getX() + " " + pos.getY() + " " + pos.getZ()))
                .append(Component.literal(" in " + player.level().dimension().location() + " ===")),
            false
        );

        section(ctx, "SETTLEMENT", HiveDebugCommands::inspectSettlement);
        section(ctx, "QUEEN", HiveDebugCommands::inspectQueen);
        section(ctx, "EMPRESS", HiveDebugCommands::inspectEmpress);
        section(ctx, "OVIPOSITOR", HiveDebugCommands::inspectOvipositor);
        section(ctx, "QUEENLESS MATURATION", HiveDebugCommands::inspectQueenlessMaturation);
        section(ctx, "TARGETING", HiveDebugCommands::inspectTargeting);
        section(ctx, "HUNTERS", HiveDebugCommands::inspectHunters);
        section(ctx, "SLAB", HiveDebugCommands::inspectSlab);

        return 1;
    }

    /**
     * Runs one section of the report under its own heading.
     * <p>
     * ⚠ THE CATCH IS DELIBERATE. These handlers were written as standalone commands and several assume a subject exists
     * - {@code Objects.requireNonNull(getPlayer())}, a queen within range, a slab underfoot. Standalone that is fine,
     * because you only ran the one you meant; in a combined report ONE missing subject would abort the other seven. A
     * section that blows up reports itself and the report carries on.
     * </p>
     */
    private static void section(
        com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
        String heading,
        java.util.function.ToIntFunction<com.mojang.brigadier.context.CommandContext<CommandSourceStack>> body
    ) {
        ctx.getSource()
            .sendSuccess(() -> Component.literal("-- " + heading).withStyle(ChatFormatting.GRAY), false);
        try {
            body.applyAsInt(ctx);
        } catch (Exception exception) {
            ctx.getSource()
                .sendSuccess(
                    () -> Component.literal("   (unavailable here)").withStyle(ChatFormatting.DARK_GRAY),
                    false
                );
        }
    }

    /**
     * [stated] "add a command for jelly to supply". Mirrors {@link #addBiomass} exactly, including clamping at zero so
     * a negative amount drains rather than underflows.
     */
    private static int addJelly(
        com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
        boolean royal
    ) {
        var location = resolveLocationArgOrHere(ctx);
        if (location == null) {
            return 0;
        }
        var locationId = location.id();
        var amount = IntegerArgumentType.getInteger(ctx, COUNT_ARG);

        var pool = royal ? "Royal" : "Scourge";
        if (royal) {
            location.setRoyalJelly(Math.max(0, location.royalJelly() + amount));
        } else {
            location.setScourgeJelly(Math.max(0, location.scourgeJelly() + amount));
        }

        var now = royal ? location.royalJelly() : location.scourgeJelly();
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(pool + " jelly for " + locationId + " is now " + now + "."),
                true
            );
        return now;
    }

    /**
     * Debug toggle (Slice B1 test harness): flips the {@code inhibited} flag on the hive location claiming the player's
     * current chunk. An inhibited location runs no autonomy (claims/biomass/spawning/contests) — used to verify the
     * gate before the real queen-driven claim lifecycle (Slice B2) wires it.
     */
    /**
     * Force-dispatch a party from the hive whose claim the player is standing in, reporting exactly which gate blocked
     * it when nothing spawns (the dispatchers are otherwise silent about refusals).
     */
    /**
     * Drop every active party for the hive you are standing in, so a new one can be dispatched immediately. Parties
     * hold a slot for their whole duration (5 min), which makes iterating on party behaviour painful: force_party just
     * answers "there is already one".
     */
    /**
     * Dump what every nearby xenomorph thinks it is doing about host hunting: is it a party member, does the party
     * still exist, does it see a quarry, and is an attack target blocking the capture goal.
     */
    /** Where this worker has been sent to collect an egg, or none. */
    private static String haulTargetOf(com.alien.common.gameplay.entity.living.alien.Alien alien) {
        if (!(alien instanceof com.alien.common.gameplay.entity.living.alien.EggCarrier carrier)) {
            return "n/a";
        }
        var egg = carrier.getEggPickupManager().getTargetOvomorphOrNull();
        return egg == null ? "none" : egg.blockPosition().toShortString();
    }

    private static int inspectHunters(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }
        var box = player.getBoundingBox().inflate(48.0);
        var aliens = player.level()
            .getEntitiesOfClass(
                com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph.class,
                box
            );
        if (aliens.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No xenomorphs within 48 blocks."), false);
            return 1;
        }
        for (var alien : aliens) {
            var membership = alien.partyMembership();
            var onHunt = com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host.HostHuntDuty
                .isOnHostHunt(alien);
            var quarry = com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host.HostSensors
                .findCaptureTarget(alien);
            var target = alien.getTarget();
            var carrying = com.alien.common.gameplay.hive.party.HostCaptureTask.isCarryingHost(alien);
            var line = alien.getType().getDescription().getString()
                + " @" + alien.blockPosition().toShortString()
                + " | party=" + (membership == null ? "NONE" : "yes")
                + " onHunt=" + onHunt
                + " quarry=" + (quarry == null ? "none" : quarry.getType().getDescription().getString())
                + " attackTarget=" + (target == null ? "none" : target.getType().getDescription().getString())
                + " carrying=" + carrying
                // ⭐⭐ THE ERRAND, NOT JUST THE FIGHT. Every field on this line was about combat, so a worker sent to
                // fetch an egg forty blocks away printed identically to one standing idle - which is exactly how a
                // report of runners "attacking nothing" went unanswered until it was guessed at. The POSITION is the
                // point: it says at once whether the thing it is charging at is simply off-screen.
                + " haulingEggAt=" + haulTargetOf(alien);
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int clearParties(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }
        var chunk = new ChunkPos(player.blockPosition());
        var dimension = player.level().dimension();
        HiveLocation target = null;
        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (
                location.isAlive()
                    && location.dimension().equals(dimension)
                    && location.claimedChunks().contains(chunk)
            ) {
                target = location;
                break;
            }
        }
        if (target == null) {
            source.sendFailure(Component.literal("Stand inside a hive claim."));
            return 0;
        }
        var cleared = target.parties().size();
        // Strip membership from any loaded member first, or they linger as orphans whose party no longer exists.
        var memberBox = player.getBoundingBox().inflate(256.0);
        for (
            var alien : player.level()
                .getEntitiesOfClass(
                    com.alien.common.gameplay.entity.living.alien.Alien.class,
                    memberBox
                )
        ) {
            if (alien.partyMembership() != null) {
                alien.clearPartyMembership();
            }
        }
        target.parties().clear();
        source.sendSuccess(
            () -> Component.literal("Cleared " + cleared + " active part(y/ies). You can dispatch again now."),
            true
        );
        return 1;
    }

    private static int forceParty(
        com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
        String partyType
    ) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            source.sendFailure(Component.literal("Server level only."));
            return 0;
        }
        var chunk = new ChunkPos(player.blockPosition());
        var dimension = player.level().dimension();
        HiveLocation target = null;
        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (
                location.isAlive()
                    && location.dimension().equals(dimension)
                    && location.claimedChunks().contains(chunk)
            ) {
                target = location;
                break;
            }
        }
        if (target == null) {
            source.sendFailure(Component.literal("Stand inside a hive claim to dispatch one of its parties."));
            return 0;
        }

        var config = HiveLocationRegistry.INSTANCE.config();
        var server = source.getServer();

        // Report the common gates up-front - a silent no-op is the usual confusion when testing parties.
        // Host hunts need a SURFACE vent; biomass and attack will also take a FRONTIER one.
        var vents = "host_hunt".equals(partyType)
            ? com.alien.common.gameplay.hive.party.PartyVentUtil.findSurfaceVents(level, target)
            : com.alien.common.gameplay.hive.party.PartyVentUtil.findPartyVents(level, target);
        if (!"surface_spawn".equals(partyType) && vents.isEmpty()) {
            source.sendFailure(
                Component.literal(
                    "This hive has no near-surface vent, so it cannot dispatch that party. Surface-spawn parties "
                        + "seed those vents - run force_party surface_spawn first."
                )
            );
            return 0;
        }
        if (
            "host_hunt".equals(partyType)
                && com.alien.common.gameplay.hive.structure.HostChamberSlots.firstFreeSpot(level, target) == null
        ) {
            source.sendFailure(
                Component.literal(
                    "No free host-chamber spot: the hive will not hunt hosts it has nowhere to put."
                )
            );
            return 0;
        }

        // Name the EXACT gate - "already active or reserves empty" was useless when testing.
        for (var party : target.parties()) {
            boolean sameType = switch (partyType) {
                case "host_hunt" -> party instanceof com.alien.common.gameplay.hive.party.HiveParty.HostHunt;
                case "biomass_hunting" ->
                    party instanceof com.alien.common.gameplay.hive.party.HiveParty.BiomassHunting;
                case "surface_spawn" ->
                    party instanceof com.alien.common.gameplay.hive.party.HiveParty.SurfaceSpawn;
                default -> false;
            };
            if (sameType) {
                source.sendFailure(
                    Component.literal(
                        "A " + partyType + " party is ALREADY active (one at a time). Run "
                            + "/avp_alien debug hive clear_parties to drop it, then dispatch again."
                    )
                );
                return 0;
            }
        }

        // Host hunts are DRONES only - no drones in the reserve pool means nothing to send.
        if ("host_hunt".equals(partyType)) {
            var hasDrones = false;
            for (var type : target.localReserves().getAvailableEntityTypes()) {
                if (type.is(com.alien.common.registry.tag.AlienEntityTypeTags.DRONES)) {
                    hasDrones = true;
                    break;
                }
            }
            if (!hasDrones) {
                source.sendFailure(
                    Component.literal(
                        "No DRONES in the reserve pool - host hunts are drones only. Let the hive grow some, or use "
                            + "/avp_alien debug hive add_reserve."
                    )
                );
                return 0;
            }
        }

        var before = target.parties().size();
        switch (partyType) {
            // An arrow case takes ONE statement - each of these needs a block, since a forced dispatch must first
            // clear the 3-day cooldown or it is silently eaten by the timer and looks like a bug.
            case "host_hunt" -> {
                target.setLastHostHuntPartyTick(0L);
                com.alien.common.gameplay.hive.party.HostHuntPartyDispatch.tryRun(server, target, config);
            }
            case "biomass_hunting" -> {
                target.setLastBiomassPartyTick(0L);
                com.alien.common.gameplay.hive.party.BiomassHuntingPartyDispatch.tryRun(server, target, config);
            }
            case "surface_spawn" -> {
                target.setLastSurfacePartyTick(0L);
                com.alien.common.gameplay.hive.party.SurfacePartyDispatch.tryRun(server, target, config);
            }
            default -> {
                source.sendFailure(Component.literal("Unknown party type: " + partyType));
                return 0;
            }
        }

        if (target.parties().size() > before) {
            var dispatched = target; // effectively-final copy for the lambda
            source.sendSuccess(
                () -> Component.literal("Dispatched a " + partyType + " party for " + dispatched.id() + "."),
                true
            );
            return 1;
        }
        source.sendFailure(
            Component.literal(
                "Dispatch refused. Most likely: a party of that type is already active, or the reserves are empty "
                    + "(check /avp_alien debug hive inspect_location)."
            )
        );
        return 0;
    }

    private static int webHost(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            source.sendFailure(Component.literal("Server level only."));
            return 0;
        }
        var chunk = new ChunkPos(player.blockPosition());
        var dimension = player.level().dimension();
        HiveLocation target = null;
        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (
                location.isAlive()
                    && location.dimension().equals(dimension)
                    && location.claimedChunks().contains(chunk)
            ) {
                target = location;
                break;
            }
        }
        if (target == null) {
            source.sendFailure(Component.literal("No hive location claims this chunk."));
            return 0;
        }
        var spot = com.alien.common.gameplay.hive.structure.HostChamberSlots.firstFreeSpot(level, target);
        if (spot == null) {
            source.sendFailure(Component.literal("No free host-chamber web spot (is a host chamber built and loaded near you?)."));
            return 0;
        }
        var villager = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if (villager == null) {
            source.sendFailure(Component.literal("Failed to create test villager."));
            return 0;
        }
        com.alien.common.gameplay.hive.structure.HostParking.embed(level, villager, spot.pos(), spot.facing());
        level.addFreshEntity(villager);
        final var placed = spot;
        source.sendSuccess(() -> Component.literal("Webbed a test villager at " + placed.pos() + " facing " + placed.facing() + "."), true);
        return 1;
    }

    /**
     * Wake the single nearest hibernating legacy queen.
     * <p>
     * [stated] "these old queens hibernate until awoken directly by the player or turned back on with the commands
     * there should be one for nearest legacy queen the other is awakening all legacy queens." This is the first of the
     * two. Only LOADED queens can be found - a sleeper in an unloaded chunk is invisible to any entity query - so this
     * searches the player's own level and reports honestly when it finds nobody.
     */
    /** Upper bound on the area commands. Big enough to cover a legacy hive cluster, small enough not to be a sweep. */
    private static final int MAX_LEGACY_AREA_RADIUS = 512;

    /**
     * The nearest LOADED legacy queen to the player, or null.
     * <p>
     * {@code dormantOnly} separates the two uses: waking cares only about sleepers, but killing should also reach a
     * legacy queen who has already been woken - she is still legacy, and still cullable.
     */
    private static com.alien.common.gameplay.entity.living.alien.xenomorph.queen.@org.jetbrains.annotations.Nullable Queen nearestLegacyQueen(
        net.minecraft.server.level.ServerLevel level,
        net.minecraft.world.entity.player.Player player,
        boolean dormantOnly
    ) {
        var candidates = dormantOnly
            ? com.alien.common.gameplay.hive.migration.LegacyHiveRecovery
                .dormantLegacyQueensIn(level, level.getWorldBorder().getCollisionShape().bounds())
            : com.alien.common.gameplay.hive.migration.LegacyHiveRecovery
                .legacyQueensIn(level, level.getWorldBorder().getCollisionShape().bounds());

        com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen nearest = null;
        var nearestDistanceSqr = Double.MAX_VALUE;

        for (var queen : candidates) {
            var distanceSqr = queen.distanceToSqr(player);
            if (distanceSqr < nearestDistanceSqr) {
                nearestDistanceSqr = distanceSqr;
                nearest = queen;
            }
        }
        return nearest;
    }

    /** The player-centred cube the area commands act on. */
    private static net.minecraft.world.phys.AABB legacyAreaBox(
        net.minecraft.world.entity.player.Player player,
        int radius
    ) {
        return new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(radius);
    }

    /**
     * Wake the single nearest hibernating legacy queen.
     * <p>
     * [stated] "there should be one for nearest legecy queen the other is awakening all legecy queens", later extended
     * with [stated] "i think an area command to wake up queens would be good... nearest, in an area, and then all
     * server wide." Only LOADED queens can be found - a sleeper in an unloaded chunk is invisible to any entity query -
     * so nearest and area both report honestly when they find nobody, and only the server-wide pair arm the persisted
     * flag that catches unloaded sleepers as they load.
     */
    private static int wakeNearestLegacyQueen(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }

        var nearest = nearestLegacyQueen(source.getLevel(), player, true);
        if (nearest == null) {
            source.sendFailure(
                Component.literal("No hibernating legacy queen is loaded in this dimension. Travel to her chunks first.")
            );
            return 0;
        }

        if (!com.alien.common.gameplay.hive.migration.LegacyHiveRecovery.awakenLegacyQueen(nearest)) {
            source.sendFailure(Component.literal("Found a dormant queen but recovery refused to wake her."));
            return 0;
        }

        final var woken = nearest;
        final var blocks = (int) Math.sqrt(woken.distanceToSqr(player));
        source.sendSuccess(
            () -> Component
                .literal("Woke legacy queen " + woken.getUUID() + " (" + blocks + " blocks away) at " + woken.blockPosition() + ".")
                .withStyle(ChatFormatting.GREEN),
            true
        );
        return 1;
    }

    /** Kill the single nearest legacy queen, asleep or already woken. */
    private static int killNearestLegacyQueen(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }

        var nearest = nearestLegacyQueen(source.getLevel(), player, false);
        if (nearest == null) {
            source.sendFailure(Component.literal("No legacy queen is loaded in this dimension."));
            return 0;
        }

        final var target = nearest;
        final var at = target.blockPosition();
        final var blocks = (int) Math.sqrt(target.distanceToSqr(player));
        if (!com.alien.common.gameplay.hive.migration.LegacyHiveRecovery.killLegacyQueen(target)) {
            source.sendFailure(Component.literal("Found a queen but recovery does not consider her legacy."));
            return 0;
        }

        source.sendSuccess(
            () -> Component
                .literal("Killed legacy queen (" + blocks + " blocks away) at " + at + ".")
                .withStyle(ChatFormatting.YELLOW),
            true
        );
        return 1;
    }

    private static int wakeLegacyQueensInArea(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }

        var radius = IntegerArgumentType.getInteger(ctx, "radius");
        var woken = com.alien.common.gameplay.hive.migration.LegacyHiveRecovery
            .awakenLegacyQueensIn(source.getLevel(), legacyAreaBox(player, radius));

        source.sendSuccess(
            () -> Component
                .literal("Woke " + woken + " legacy queen(s) within " + radius + " blocks.")
                .withStyle(woken > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY),
            true
        );
        return woken;
    }

    private static int killLegacyQueensInArea(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }

        var radius = IntegerArgumentType.getInteger(ctx, "radius");
        var killed = com.alien.common.gameplay.hive.migration.LegacyHiveRecovery
            .killLegacyQueensIn(source.getLevel(), legacyAreaBox(player, radius));

        source.sendSuccess(
            () -> Component
                .literal("Killed " + killed + " legacy queen(s) within " + radius + " blocks.")
                .withStyle(killed > 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY),
            true
        );
        return killed;
    }

    /**
     * Wake every legacy queen, and ARM the persisted flag so sleepers in unloaded chunks wake as they load.
     * <p>
     * That persisted arming is why this is not just a loop: most of an old world's queens are not loaded when the
     * command runs, and {@code wakeAllLegacyQueens} in the recovery data catches each one at load time instead.
     */
    private static int wakeAllLegacyQueens(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var woken = com.alien.common.gameplay.hive.migration.LegacyHiveRecovery.awakenLegacyQueens(source.getServer());
        source.sendSuccess(
            () -> Component
                .literal(
                    "Woke " + woken + " loaded legacy queen(s). Any still in unloaded chunks will wake as they load."
                )
                .withStyle(ChatFormatting.GREEN),
            true
        );
        return 1;
    }

    /**
     * Cull every legacy queen, and ARM the persisted kill flag so sleepers in unloaded chunks are discarded as they
     * load. Irreversible - the server-wide flags persist in NBT.
     */
    private static int killAllLegacyQueens(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var killed = com.alien.common.gameplay.hive.migration.LegacyHiveRecovery.killLegacyQueens(source.getServer());
        source.sendSuccess(
            () -> Component
                .literal(
                    "Killed " + killed + " loaded legacy queen(s). Any still in unloaded chunks will be culled as they load."
                )
                .withStyle(ChatFormatting.YELLOW),
            true
        );
        return 1;
    }

    /** Who is still asleep and where - so the nearest-queen command can be aimed instead of guessed at. */
    /**
     * ⭐⭐ RETIRES THIS WORLD'S LEGACY DATA WITHOUT DESTROYING ANYTHING ELSE.
     * <p>
     * ⚠⚠ {@code remove all} ALSO SETS THE PURGE FLAG, BUT IT IS THE WRONG TOOL ONCE A PLAYER HAS STARTED REBUILDING. He
     * wiped his world, then began placing the queens he actually wants - and re-running the wipe to clear the legacy
     * data would kill those queens too. This does the legacy half on its own: the sleepers go, the flag is set, and
     * every hive he has since founded is untouched.
     * </p>
     * <p>
     * ⚠ Recovery re-detects on EVERY load while {@code hive_data.dat} sits on disk, so clearing the sleepers without
     * setting the flag would last exactly until the next restart. Both halves or neither.
     * </p>
     */
    private static int purgeLegacy(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var sleepers = com.alien.common.gameplay.hive.migration.LegacyHiveRecovery
            .loadedDormantLegacyQueens(source.getServer());
        for (var sleeper : sleepers) {
            sleeper.discard();
        }
        var removed = sleepers.size();

        com.alien.common.gameplay.hive.migration.LegacyHiveRecoveryData.getOrCreate(source.getServer())
            .ifSome(data -> {
                data.setLegacyDetected(false);
                data.setRecoveryApplied(true);
                data.setLegacyPurged(true);
            });

        source.sendSuccess(
            () -> Component.literal(
                "Legacy data purged. " + removed + " sleeping legacy queen(s) removed."
                    + " Old hive data on disk will no longer be recovered, and this survives a restart."
            ),
            true
        );
        return 1;
    }

    private static int listLegacyQueens(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var dormant = com.alien.common.gameplay.hive.migration.LegacyHiveRecovery
            .loadedDormantLegacyQueens(source.getServer());

        if (dormant.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No hibernating legacy queens are currently loaded."), false);
            return 0;
        }

        source.sendSuccess(
            () -> Component.literal("Hibernating legacy queens loaded: " + dormant.size()).withStyle(ChatFormatting.GOLD),
            false
        );
        for (var queen : dormant) {
            source.sendSuccess(
                () -> Component.literal(
                    "  " + queen.getUUID() + " in " + queen.level().dimension().location() + " at " + queen.blockPosition()
                ),
                false
            );
        }
        return dormant.size();
    }

    private static int inhibitHere(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }
        var chunk = new ChunkPos(player.blockPosition());
        var dimension = player.level().dimension();
        HiveLocation target = null;
        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (
                location.isAlive()
                    && location.dimension().equals(dimension)
                    && location.claimedChunks().contains(chunk)
            ) {
                target = location;
                break;
            }
        }
        if (target == null) {
            source.sendFailure(Component.literal("No hive location claims this chunk."));
            return 0;
        }
        var now = !target.isInhibited();
        target.setInhibited(now);
        final var resolved = target;
        source.sendSuccess(() -> Component.literal("Location " + resolved.id() + " inhibited = " + now), true);
        return 1;
    }

    private static int listLineages(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var ids = Alien.MOD.factions()
            .getAllIds()
            .stream()
            .filter(LineageIds::isLineageId)
            .toList();

        ctx.getSource()
            .sendSuccess(
                () -> Component.literal("Lineages (" + ids.size() + "):"),
                false
            );

        for (var id : ids) {
            var faction = Alien.MOD.factions().get(id);
            if (!(faction != null && faction.data() instanceof LineageFactionData lineage)) {
                ctx.getSource()
                    .sendSuccess(
                        () -> Component.literal("  ").append(copyableId(id.toString())).append(Component.literal(" [missing data]")),
                        false
                    );
                continue;
            }

            ctx.getSource()
                .sendSuccess(
                    () -> Component.literal("  ")
                        .append(copyableId(id.toString()))
                        .append(
                            Component.literal(
                                " variant=" + lineage.variant()
                                    + " dim=" + lineage.dimension().location()
                                    + " locations=" + lineage.locationsById().size()
                                    + " empress=" + (lineage.empressId() == null ? "none" : lineage.empressId().toString())
                                    + (lineage.pendingEmpressSeatId() == null
                                        ? ""
                                        : " (ELECTED, awaiting molt at " + lineage.pendingEmpressSeatId() + ")")
                                    + (lineage.empressCooldownUntilTick() <= ctx.getSource().getServer().overworld().getGameTime()
                                        ? ""
                                        : " (crowning locked for "
                                            + ((lineage.empressCooldownUntilTick()
                                                - ctx.getSource().getServer().overworld().getGameTime()) / 24000L)
                                            + " more MC days)")
                            )
                        ),
                    false
                );

            // List each location ID (clickable to copy) so players can feed it into inspect_location / kill_location.
            for (var locId : lineage.locationsById().keySet()) {
                ctx.getSource()
                    .sendSuccess(
                        () -> Component.literal("      location: ").append(copyableId(locId.value().toString())),
                        false
                    );
            }
        }

        return ids.size();
    }

    private static int listTracked(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var now = source.getLevel().getGameTime();

        TrackedQueenRegistry.getOrCreate(source.getServer()).ifSome(registry -> {
            var entries = registry.entries();

            source.sendSuccess(() -> Component.literal("Tracked queens (" + entries.size() + "):"), false);

            for (var e : entries.entrySet()) {
                var id = e.getKey();
                var entry = e.getValue();
                var ageSeconds = Math.max(0, (now - entry.lastSeenGameTime()) / 20);

                source.sendSuccess(
                    () -> Component.literal("  ")
                        .append(copyableId(id.toString()))
                        .append(
                            Component.literal(
                                " " + entry.name()
                                    + " dim=" + entry.dimension().location()
                                    + " pos=" + entry.pos().getX() + "," + entry.pos().getY() + "," + entry.pos().getZ()
                                    + " seen " + ageSeconds + "s ago"
                            )
                        ),
                    false
                );
            }
        });

        return 1;
    }

    private static int dumpIndexes(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "HiveLocationRegistry: locations=" + HiveLocationRegistry.INSTANCE.locationCount()
                        + " lineages=" + HiveLocationRegistry.INSTANCE.lineageCount()
                ),
                false
            );

        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            ctx.getSource()
                .sendSuccess(
                    () -> Component.literal(
                        "  " + location.id() + " @ " + location.centerPos()
                            + " in " + location.dimension().location()
                            + " chunks=" + location.claimedChunks().size()
                            + " biomass=" + location.biomass()
                    ),
                    false
                );
        }

        return HiveLocationRegistry.INSTANCE.locationCount();
    }

    private static int inspectLocation(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var location = resolveLocationArgOrHere(ctx);
        if (location == null) {
            return 0;
        }
        var locationId = location.id();

        ctx.getSource()
            .sendSuccess(
                () -> Component.literal("Location " + location.id()),
                false
            );
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal("  lineage=" + location.lineageFactionId()),
                false
            );
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal("  dimension=" + location.dimension().location()),
                false
            );
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal("  centerPos=" + location.centerPos()),
                false
            );
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "  slab=Y " + location.hiveFloorY() + ".." + location.hiveCeilingY()
                        + ", vents=" + location.ventManager().ventCount()
                        + ", reproductive=" + location.reproductiveEstablished()
                        + ", loadedMembers=" + location.loadedMembersByType().values().stream().mapToInt(java.util.Set::size).sum()
                ),
                false
            );
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal("  founderId=" + location.founderId()),
                false
            );
        var config = HiveLocationRegistry.INSTANCE.config();
        var nextCost = BiomassIncome.claimCost(location, config);
        var biomassCap = BiomassIncome.biomassCap(location, config);
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "  age=" + location.ageInTicks() + " ticks, biomass=" + location.biomass()
                        + "/" + biomassCap + " (next claim costs " + nextCost + ")"
                        + ", peakXeno=" + location.peakXenomorphCount()
                        + ", lastGrowthTick=" + location.lastGrowthTick()
                ),
                false
            );
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "  claimedChunks=" + location.claimedChunks().size()
                        + ", decoratedChunks=" + location.decoratedChunks().size()
                        + ", reserves total=" + location.localReserves().getReliableCount()
                ),
                false
            );

        var leaderId = location.leadership().getLeaderIdOrNull();
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal("  leader=" + (leaderId == null ? "none" : leaderId.toString())),
                false
            );

        var loadedHere = location.loadedMembersByType()
            .values()
            .stream()
            .mapToInt(java.util.Set::size)
            .sum();
        var bossBar = location.bossBar();
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "  loadedHere=" + loadedHere
                        + ", bossBar=" + (bossBar == null
                            ? "(uninitialized)"
                            : "angry=" + bossBar.isAngry() + " evacuating=" + bossBar.isEvacuating())
                        + ", evacuatingTicksLeft=" + location.evacuatingRemainingTicks()
                        + ", combatRespiteTicksLeft=" + location.combatRespiteRemainingTicks()
                        + ", combatKillsSinceLastRespite=" + location.combatKillsSinceLastRespite()
                ),
                false
            );

        if (!location.loadedMembersByType().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("  loaded members by type:"), false);
            for (var entry : location.loadedMembersByType().entrySet()) {
                var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entry.getKey());
                ctx.getSource()
                    .sendSuccess(() -> Component.literal("    " + typeId + " = " + entry.getValue().size()), false);
                for (var uuid : entry.getValue()) {
                    ctx.getSource().sendSuccess(() -> Component.literal("      " + uuid), false);
                }
            }
        }

        // Location faction (per-tick death key) + no-contact safety net status.
        var locationFaction = Alien.MOD.factions().get(location.id().value());
        var locationMemberCount = locationFaction != null ? locationFaction.membership().getMembers().size() : 0;
        var serverLevel = ctx.getSource().getServer().getLevel(location.dimension());
        var chunksLoaded = 0;
        if (serverLevel != null) {
            for (var chunk : location.claimedChunks()) {
                if (serverLevel.getChunkSource().hasChunk(chunk.x, chunk.z)) {
                    chunksLoaded++;
                }
            }
        }
        final var finalChunksLoaded = chunksLoaded;
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "  location-faction members=" + locationMemberCount
                        + ", noContactTicksAccrued=" + location.noContactTicksAccrued()
                        + "/" + HiveLocationRegistry.INSTANCE.config().locationMaxNoContactTicks()
                        + ", chunksLoaded=" + finalChunksLoaded + "/" + location.claimedChunks().size()
                ),
                false
            );

        // Economy snapshot: resources + population vs cap + per-caste counts.
        var econConfig = HiveLocationRegistry.INSTANCE.config();
        var totalPop = com.alien.common.gameplay.hive.economy.CastePopulation.totalTrackedPopulation(location);
        var popCap = econConfig.populationPerChunk() * location.claimedChunks().size();
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "  resources: biomass=" + location.biomass()
                        + ", royalJelly=" + location.royalJelly() + "/"
                        + com.alien.common.gameplay.hive.economy.JellyProduction.royalJellyCap(location)
                        + ", scourgeJelly=" + location.scourgeJelly() + "/"
                        + com.alien.common.gameplay.hive.economy.JellyProduction.scourgeJellyCap(location)
                ),
                false
            );
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal("  population=" + totalPop + "/" + popCap),
                false
            );

        var castePop = com.alien.common.gameplay.hive.economy.CastePopulation.popByCaste(location);

        // ⭐⭐ THE SECOND CAP, and the one that usually explains "why has my hive stopped growing". population=X/Y
        // above is populationPerChunk x claimedChunks; THIS is HiveBalanceTask.MEMBER_CAP (250, 400 under an
        // empress), and it is measured against WORKING ADULTS ONLY - queens, every military caste, the founding
        // retinue and the carve crew are all subtracted first. Either ceiling can halt production and they are not
        // interchangeable, so both are printed. Read straight off HiveBalanceTask so the two cannot drift.
        var workers = com.alien.common.gameplay.hive.economy.HiveBalanceTask.workingAdults(location, castePop);
        var workerCap = com.alien.common.gameplay.hive.economy.HiveBalanceTask.memberCapFor(location);
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "  workers=" + workers + "/" + workerCap
                        + (workers >= workerCap ? "  (AT WORKER CAP - no more will be bought)" : "")
                ),
                false
            );

        ctx.getSource().sendSuccess(() -> Component.literal("  per-caste:"), false);
        for (var entry : castePop.entrySet()) {
            if (entry.getValue() > 0) {
                ctx.getSource()
                    .sendSuccess(
                        () -> Component.literal("    " + entry.getKey().location() + " = " + entry.getValue()),
                        false
                    );
            }
        }

        // Also list the lineage's BLib membership so you can compare with what's actually routed
        // into the location. A UUID in lineage membership but not in loadedHere means the entity
        // isn't loaded right now, or its chunk isn't owned by this location.
        var lineageFaction = Alien.MOD.factions().get(location.lineageFactionId());
        if (lineageFaction != null) {
            var lineageMembers = lineageFaction.membership().getMembers();
            ctx.getSource()
                .sendSuccess(
                    () -> Component.literal("  lineage membership total=" + lineageMembers.size()),
                    false
                );
            for (var member : lineageMembers) {
                if (member instanceof FactionMember.Entity entityMember) {
                    ctx.getSource()
                        .sendSuccess(() -> Component.literal("    " + entityMember.uuid()), false);
                }
            }
        }

        var reservesByType = location.localReserves().underlying().getBackingMap();
        if (!reservesByType.isEmpty()) {
            ctx.getSource()
                .sendSuccess(() -> Component.literal("  reserves breakdown:"), false);
            for (var entry : reservesByType.entrySet()) {
                var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entry.getKey());
                ctx.getSource()
                    .sendSuccess(() -> Component.literal("    " + typeId + " = " + entry.getValue()), false);
            }
        }
        var identityReserves = location.localReserves().identity();
        if (identityReserves.getCount() > 0) {
            ctx.getSource()
                .sendSuccess(() -> Component.literal("  identity reserves breakdown:"), false);
            for (var type : identityReserves.getAvailableEntityTypes()) {
                var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                var count = identityReserves.getCount(type);
                ctx.getSource()
                    .sendSuccess(() -> Component.literal("    " + typeId + " = " + count), false);
            }
        }

        return 1;
    }

    private static int addReserve(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var location = resolveLocationArgOrHere(ctx);
        if (location == null) {
            return 0;
        }
        var locationId = location.id();
        var entityTypeId = ResourceLocationArgument.getId(ctx, ENTITY_TYPE_ARG);
        var count = IntegerArgumentType.getInteger(ctx, COUNT_ARG);

        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entityTypeId)) {
            ctx.getSource().sendFailure(Component.literal("No entity type with id " + entityTypeId));
            return 0;
        }

        var entityType = BuiltInRegistries.ENTITY_TYPE.get(entityTypeId);
        if (!location.localReserves().tryAdd(entityType, count)) {
            ctx.getSource()
                .sendFailure(
                    Component.literal(
                        "Reserve add rejected: " + entityTypeId + " does not match " + locationId + "'s variant"
                    )
                );
            return 0;
        }

        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "Added " + count + " of " + entityTypeId + " to " + locationId
                ),
                true
            );
        return count;
    }

    private static int mintLineageAtPlayer(
        com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
        AlienVariant variant
    ) {
        var player = Objects.requireNonNull(ctx.getSource().getPlayer());
        var level = ctx.getSource().getLevel();

        // Ensure variant faction exists.
        var variantFaction = VariantFactionRegistry.getOrCreate(variant);

        // Mint lineage faction.
        var lineageId = LineageIds.create();
        var lineageFaction = Alien.MOD.factions().getOrCreate(lineageId, AlienFactionDataTypes.LINEAGE);
        var lineageData = lineageFaction.data();

        if (lineageData == null) {
            ctx.getSource().sendFailure(Component.literal("Lineage data was null after creation."));
            return 0;
        }

        FactionAesthetics.applyDefaults(lineageFaction, variant, FactionAesthetics.Tier.LINEAGE);
        lineageData.setFactionId(lineageId);

        var variantData = variantFaction.data();
        var lineageNumber = variantData != null ? variantData.allocateLineageNumber() : 0L;
        lineageData.setLineageNumber(lineageNumber);
        lineageFaction.setName(FactionNaming.forLineage(variant, lineageNumber));

        lineageData.setVariant(variant);
        lineageData.setParentVariantFactionId(variantFaction.id());
        lineageData.setDimension(level.dimension());
        lineageData.setFounderId(player.getUUID());

        // Mint location at player's chunk center.
        var locationId = HiveLocationIds.create();
        var centerChunk = new ChunkPos(player.blockPosition());
        var centerPos = centerChunk.getMiddleBlockPosition(player.blockPosition().getY());
        var location = new HiveLocation(locationId, lineageId, level.dimension(), centerPos, player.getUUID());

        lineageData.addLocation(location);
        HiveLocationRegistry.INSTANCE.register(location);

        com.alien.common.gameplay.hive.growth.HiveLocationClaims.claim(
            level,
            location,
            centerChunk,
            level.getGameTime()
        );

        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "Minted lineage " + lineageId + " (" + variant + ") with location " + locationId
                        + " at " + centerPos
                ),
                true
            );

        return 1;
    }

    private static int mintLocationInLineage(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var player = Objects.requireNonNull(ctx.getSource().getPlayer());
        var level = ctx.getSource().getLevel();

        var lineageFactionId = resolveLineageArg(ctx);
        var faction = Alien.MOD.factions().get(lineageFactionId);
        if (faction == null || !(faction.data() instanceof LineageFactionData lineageData)) {
            ctx.getSource().sendFailure(Component.literal("No lineage with id " + lineageFactionId));
            return 0;
        }

        if (!lineageData.dimension().equals(level.dimension())) {
            ctx.getSource()
                .sendFailure(
                    Component.literal(
                        "Lineage is in " + lineageData.dimension().location()
                            + ", but you are in " + level.dimension().location()
                    )
                );
            return 0;
        }

        var locationId = HiveLocationIds.create();
        var centerChunk = new ChunkPos(player.blockPosition());
        var centerPos = centerChunk.getMiddleBlockPosition(player.blockPosition().getY());
        var location = new HiveLocation(locationId, lineageFactionId, level.dimension(), centerPos, player.getUUID());

        lineageData.addLocation(location);
        HiveLocationRegistry.INSTANCE.register(location);

        com.alien.common.gameplay.hive.growth.HiveLocationClaims.claim(
            level,
            location,
            centerChunk,
            level.getGameTime()
        );

        var locationNumber = lineageData.allocateLocationNumber();
        location.setLocationNumber(locationNumber);

        var locationFaction = Alien.MOD.factions().getOrCreate(locationId.value(), AlienFactionDataTypes.LOCATION);
        FactionAesthetics.applyDefaults(locationFaction, lineageData.variant(), FactionAesthetics.Tier.LOCATION);
        var locationData = locationFaction.data();
        if (locationData != null) {
            locationData.setLocationId(locationId);
        }
        locationFaction.setName(
            FactionNaming.forLocation(lineageData.variant(), lineageData.lineageNumber(), locationNumber)
        );

        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "Minted location " + locationId + " in lineage " + lineageFactionId
                        + " at " + centerPos
                ),
                true
            );

        return 1;
    }

    private static int inspectVariant(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var variantName = StringArgumentType.getString(ctx, VARIANT_ARG).toUpperCase(Locale.ROOT);
        AlienVariant variant;

        try {
            variant = AlienVariant.valueOf(variantName);
        } catch (IllegalArgumentException ignored) {
            ctx.getSource().sendFailure(Component.literal("Unknown variant: " + variantName));
            return 0;
        }

        var factionId = VariantIds.of(variant);
        var faction = Alien.MOD.factions().get(factionId);

        if (faction == null) {
            ctx.getSource()
                .sendFailure(Component.literal("Variant faction does not exist yet: " + factionId));
            return 0;
        }

        var members = faction.membership().getMembers();
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "Variant " + variant + " (" + factionId + ") has " + members.size() + " members:"
                ),
                false
            );

        if (members.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("  (none)"), false);
        } else {
            for (var member : members) {
                if (member instanceof FactionMember.Entity entityMember) {
                    ctx.getSource()
                        .sendSuccess(() -> Component.literal("  " + entityMember.uuid()), false);
                }
            }
        }

        if (faction.data() instanceof VariantFactionData variantData) {
            ctx.getSource()
                .sendSuccess(
                    () -> Component.literal(
                        "  data.variant=" + variantData.variant()
                            + " ageInTicks=" + variantData.ageInTicks()
                            + " queenMothers=" + variantData.queenMotherIdsByDimension().size()
                    ),
                    false
                );
        }

        return members.size();
    }

    private static int forceLineageJoinNearby(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var player = Objects.requireNonNull(ctx.getSource().getPlayer());
        var level = ctx.getSource().getLevel();

        var lineageFactionId = resolveLineageArg(ctx);
        var faction = Alien.MOD.factions().get(lineageFactionId);

        if (faction == null || !(faction.data() instanceof LineageFactionData lineageData)) {
            ctx.getSource().sendFailure(Component.literal("No lineage with id " + lineageFactionId));
            return 0;
        }

        if (!lineageData.dimension().equals(level.dimension())) {
            ctx.getSource()
                .sendFailure(
                    Component.literal(
                        "Lineage is in " + lineageData.dimension().location()
                            + ", but you are in " + level.dimension().location()
                    )
                );
            return 0;
        }

        var area = player.getBoundingBox().inflate(FORCE_JOIN_RADIUS_BLOCKS);
        var aliens = level.getEntitiesOfClass(
            com.alien.common.gameplay.entity.living.alien.Alien.class,
            area
        );

        var joined = 0;
        for (var alien : aliens) {
            if (alien.getVariant() == lineageData.variant() && faction.membership().addEntity(alien)) {
                joined++;
            }
        }

        var finalJoined = joined;
        var finalScanned = aliens.size();
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "Force-joined " + finalJoined + " of " + finalScanned
                        + " nearby aliens to lineage " + lineageFactionId + " (variant filter applied)."
                ),
                true
            );
        return joined;
    }

    private static int forceVariantJoinNearby(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var player = Objects.requireNonNull(ctx.getSource().getPlayer());
        var level = ctx.getSource().getLevel();

        var area = player.getBoundingBox().inflate(FORCE_JOIN_RADIUS_BLOCKS);
        var aliens = level.getEntitiesOfClass(
            com.alien.common.gameplay.entity.living.alien.Alien.class,
            area
        );

        var joined = 0;
        for (var alien : aliens) {
            var variant = alien.getVariant();
            if (variant == null) {
                continue;
            }

            var faction = VariantFactionRegistry.getOrCreate(variant);
            if (!faction.membership().hasMember(FactionMember.entity(alien))) {
                if (faction.membership().addEntity(alien)) {
                    joined++;
                }
            }
        }

        var finalJoined = joined;
        var finalScanned = aliens.size();
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "Force-joined " + finalJoined + " of " + finalScanned + " nearby aliens to their variant factions."
                ),
                true
            );
        return joined;
    }

    private static int forceShedCheckNearby(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var player = Objects.requireNonNull(ctx.getSource().getPlayer());
        var level = ctx.getSource().getLevel();

        var area = player.getBoundingBox().inflate(FORCE_JOIN_RADIUS_BLOCKS);
        var aliens = level.getEntitiesOfClass(
            com.alien.common.gameplay.entity.living.alien.Alien.class,
            area
        );

        var shed = 0;
        var checked = 0;
        for (var alien : aliens) {
            checked++;
            alien.getHiveManager().debugForceShedEligible();
            if (alien.getHiveManager().tryShedFromLineages(level.getGameTime())) {
                shed++;
            }
        }

        var finalShed = shed;
        var finalChecked = checked;
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "Force-shed check on " + finalChecked + " nearby aliens; " + finalShed + " were shed."
                ),
                true
            );
        return shed;
    }

    /**
     * Wraps an ID (or any string) in a chat component that copies the raw text to the clipboard when clicked, with a
     * hover tooltip. Mirrors how vanilla {@code /locate} makes its results clickable. Use this anywhere a lineage or
     * location ID is printed so players can grab the long numeric IDs without retyping them.
     */
    private static Component copyableId(String id) {
        return Component.literal(id)
            .withStyle(
                style -> style
                    .withColor(ChatFormatting.AQUA)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, id))
                    .withHoverEvent(
                        new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Click to copy: " + id)
                        )
                    )
            );
    }

    /**
     * Lists all known vent positions in a location's territory (and the total count). Vents are held in memory and
     * re-register on chunk load, so unloaded territory may under-report until visited.
     */
    private static int listVents(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var location = resolveLocationArgOrHere(ctx);
        if (location == null) {
            return 0;
        }
        var locationId = location.id();

        var vents = location.ventManager().allVents();
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal("Location " + location.id() + " has " + vents.size() + " known vent(s):"),
                false
            );
        for (var pos : vents) {
            var kind = location.ventManager().kindOf(pos);
            ctx.getSource()
                .sendSuccess(
                    () -> Component.literal("  " + pos.toShortString() + "  [" + (kind == null ? "?" : kind) + "]"),
                    false
                );
        }
        return vents.size();
    }

    /**
     * Debug: grants biomass to a location so ovipositor/claim/economy behaviour can be tested without waiting for
     * passive income.
     */
    private static int addBiomass(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var location = resolveLocationArgOrHere(ctx);
        if (location == null) {
            return 0;
        }
        var locationId = location.id();
        var amount = IntegerArgumentType.getInteger(ctx, COUNT_ARG);
        location.setBiomass(Math.max(0, location.biomass() + amount));
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal("Biomass for " + locationId + " is now " + location.biomass() + "."),
                true
            );
        return location.biomass();
    }

    /**
     * Finds the nearest queen to the player and prints which ovipositor-creation gate(s) are blocking egg-laying.
     */
    private static int inspectOvipositor(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var player = Objects.requireNonNull(ctx.getSource().getPlayer());
        var level = ctx.getSource().getLevel();

        var queen = level.getEntitiesOfClass(
            com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen.class,
            player.getBoundingBox().inflate(64.0)
        ).stream().min(java.util.Comparator.comparingDouble(q -> q.distanceToSqr(player))).orElse(null);

        if (queen == null) {
            ctx.getSource().sendFailure(Component.literal("No queen within 64 blocks."));
            return 0;
        }

        var report = queen.getOvipositorManager().debugReport();
        ctx.getSource().sendSuccess(() -> Component.literal(report), false);
        return 1;
    }

    /** The nearest xenomorph of ANY caste within 32 blocks, for the animation watch. */
    private static com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph nearestXenomorph(
        com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx
    ) {
        var player = Objects.requireNonNull(ctx.getSource().getPlayer());
        var level = ctx.getSource().getLevel();

        return level.getEntitiesOfClass(
            com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph.class,
            player.getBoundingBox().inflate(32.0)
        ).stream().min(java.util.Comparator.comparingDouble(x -> x.distanceToSqr(player))).orElse(null);
    }

    private static com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen nearestQueen(
        com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx
    ) {
        var player = Objects.requireNonNull(ctx.getSource().getPlayer());
        var level = ctx.getSource().getLevel();
        return level.getEntitiesOfClass(
            com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen.class,
            player.getBoundingBox().inflate(64.0)
        ).stream().min(java.util.Comparator.comparingDouble(q -> q.distanceToSqr(player))).orElse(null);
    }

    /**
     * TARGETING DIAG. Walks the real predicate chain for the nearest xenomorph against everything around it and reports
     * which gate refuses, in BOTH directions. Written for the empress/marine mutual-ignore, which survived every static
     * check - tags, attackable/isAlliedTo overrides, GOAP packages, attack config, follow range - so the missing
     * information was runtime state. Its standingInLocation line is what finally named the cause.
     * <p>
     * Goes to chat AND to the log, so a report can be pasted from latest.log rather than retyped from screenshots.
     */
    private static int inspectTargeting(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var player = Objects.requireNonNull(ctx.getSource().getPlayer());
        var level = ctx.getSource().getLevel();
        var subject = level.getEntitiesOfClass(
            com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph.class,
            player.getBoundingBox().inflate(64.0)
        ).stream().min(java.util.Comparator.comparingDouble(x -> x.distanceToSqr(player))).orElse(null);

        if (subject == null) {
            ctx.getSource().sendFailure(Component.literal("No xenomorph within 64 blocks."));
            return 0;
        }

        var lines = com.alien.common.gameplay.entity.living.alien.xenomorph.ai.combat.XenomorphTargetingDiagnostics.report(subject);

        for (var line : lines) {
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
            Alien.LOGGER.info("[targetdiag] {}", line);
        }

        return 1;
    }

    private static com.alien.common.gameplay.entity.living.alien.xenomorph.empress.Empress nearestEmpress(
        com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx
    ) {
        var player = Objects.requireNonNull(ctx.getSource().getPlayer());
        var level = ctx.getSource().getLevel();
        return level.getEntitiesOfClass(
            com.alien.common.gameplay.entity.living.alien.xenomorph.empress.Empress.class,
            player.getBoundingBox().inflate(64.0)
        ).stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
    }

    /**
     * Status for the nearest empress (within 64 blocks): identity, eggsack state, and — per lineage she leads — the
     * number of hive locations and the number of queens she controls. The lineage-wide counterpart to inspect_queen.
     */
    private static int inspectEmpress(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var empress = nearestEmpress(ctx);
        if (empress == null) {
            ctx.getSource().sendFailure(Component.literal("No empress within 64 blocks."));
            return 0;
        }

        var src = ctx.getSource();
        var uuid = empress.getUUID();
        var typeId = net.minecraft.world.entity.EntityType.getKey(empress.getType());

        src.sendSuccess(
            () -> Component.literal("=== Empress ")
                .append(copyableId(uuid.toString()))
                .append(
                    Component.literal(
                        "  " + typeId + "  @ " + empress.getBlockX() + " " + empress.getBlockY() + " "
                            + empress.getBlockZ() + "  hp=" + (int) empress.getHealth() + "/"
                            + (int) empress.getMaxHealth() + " ==="
                    )
                ),
            false
        );
        src.sendSuccess(
            () -> Component.literal("  eggsack: hasOvipositor=" + empress.getEmpressOvipositorManager().hasOvipositor()),
            false
        );

        var lineageIds = new java.util.ArrayList<net.minecraft.resources.ResourceLocation>();
        for (var factionId : Alien.MOD.factions().getFactionIds(uuid)) {
            if (LineageIds.isLineageId(factionId)) {
                lineageIds.add(factionId);
            }
        }
        if (lineageIds.isEmpty()) {
            src.sendSuccess(() -> Component.literal("  lineage: none"), false);
            return 1;
        }

        for (var lineageId : lineageIds) {
            var faction = Alien.MOD.factions().get(lineageId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
                continue;
            }
            var queenIds = new java.util.HashSet<java.util.UUID>();
            for (var location : lineage.locationsById().values()) {
                for (var member : location.knownMembersByType().entrySet()) {
                    if (member.getKey().is(com.alien.common.registry.tag.AlienEntityTypeTags.QUEENS)) {
                        queenIds.addAll(member.getValue());
                    }
                }
            }
            int locations = lineage.locationsById().size();
            int queens = queenIds.size();
            boolean seated = uuid.equals(lineage.empressId());
            src.sendSuccess(
                () -> Component.literal("  lineage: ")
                    .append(copyableId(lineageId.toString()))
                    .append(
                        Component.literal(
                            "  locations=" + locations + "  queens controlled=" + queens
                                + "  seatedEmpress=" + seated
                        )
                    ),
                false
            );
        }

        return 1;
    }

    /**
     * One-stop status for the nearest queen (within 64 blocks): identity, front-end lifecycle phase + timers, the
     * founding gate + settlement countdown, eggsack, and — once she is bound — her lineage and founded location(s) with
     * biomass. Lineage and location ids render as click-to-copy.
     */
    private static int inspectQueen(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var queen = nearestQueen(ctx);
        if (queen == null) {
            ctx.getSource().sendFailure(Component.literal("No queen within 64 blocks."));
            return 0;
        }

        var src = ctx.getSource();
        var mgr = queen.getLifecyclePhaseManager();
        var uuid = queen.getUUID();
        var currentTick = src.getLevel().getGameTime();
        var settlementTicks = HiveLocationRegistry.INSTANCE.config().settlementTicks();
        var typeId = net.minecraft.world.entity.EntityType.getKey(queen.getType());

        src.sendSuccess(
            () -> Component.literal("=== Queen ")
                .append(copyableId(uuid.toString()))
                .append(
                    Component.literal(
                        "  " + typeId + "  @ " + queen.getBlockX() + " " + queen.getBlockY() + " " + queen.getBlockZ()
                            + "  hp=" + (int) queen.getHealth() + "/" + (int) queen.getMaxHealth() + " ==="
                    )
                ),
            false
        );

        var frontEnd = new StringBuilder(
            "  front-end: phase=" + mgr.getPhase()
                + "  developing=" + mgr.getDevelopingTicksRemaining() + "t"
                + "  hibernation=" + mgr.getHibernationTicksRemaining() + "t"
        );
        if (mgr.getPhase() == com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenLifecyclePhase.HIBERNATION) {
            frontEnd.append("  activity=").append(mgr.getHibernationActivity());
            if (
                mgr.getHibernationActivity() == com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenLifecyclePhaseManager.HibernationActivity.DEFENDING
            ) {
                frontEnd.append(" (calm=").append(mgr.getDisturbanceCalmTicks()).append("t)");
            }
        }
        frontEnd.append("  anchor=")
            .append(mgr.getAnchor())
            .append("  (enabled=")
            .append(com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenLifecyclePhaseManager.isEnabled())
            .append(")");
        src.sendSuccess(() -> Component.literal(frontEnd.toString()), false);

        var state = QueenSettlementDetector.snapshot().get(uuid);
        var founding = new StringBuilder(
            "  founding: readyToFound=" + mgr.isReadyToFound()
                + "  inCombat[target=" + (queen.getTarget() != null)
                + " hurt=" + queen.hurtTime
                + " lastHurt=" + (queen.getLastHurtByMob() != null) + "]"
        );
        if (state != null) {
            var elapsed = state.accumulatedTicks();
            var remaining = Math.max(0L, settlementTicks - elapsed);
            founding.append("  SETTLING elapsed=").append(elapsed).append("t remaining=").append(remaining).append("t");
        } else if (mgr.isReadyToFound()) {
            founding.append("  (not settling yet — she just became ready, or has not begun banking)");
        } else {
            founding.append("  (not founding yet — still in the front-end lifecycle)");
        }
        src.sendSuccess(() -> Component.literal(founding.toString()), false);

        src.sendSuccess(() -> Component.literal("  eggsack: " + queen.getOvipositorManager().debugReport()), false);

        var lineageIds = new java.util.ArrayList<net.minecraft.resources.ResourceLocation>();
        for (var factionId : Alien.MOD.factions().getFactionIds(uuid)) {
            if (LineageIds.isLineageId(factionId)) {
                lineageIds.add(factionId);
            }
        }
        if (lineageIds.isEmpty()) {
            src.sendSuccess(() -> Component.literal("  lineage: none (not bound to a lineage yet)"), false);
        } else {
            for (var lineageId : lineageIds) {
                src.sendSuccess(
                    () -> Component.literal("  lineage: ").append(copyableId(lineageId.toString())),
                    false
                );
            }
        }

        HiveLocation home = null;
        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (uuid.equals(location.founderId())) {
                home = location;
                break;
            }
        }
        if (home == null) {
            src.sendSuccess(() -> Component.literal("  location: none founded yet"), false);
        } else {
            var idStr = home.id().toString();
            var biomass = home.biomass();
            var loadedMembers = loadedAdultCount(home);
            var totalMembers = com.alien.common.gameplay.hive.economy.CastePopulation
                .totalReliableXenomorphPopulation(home);
            src.sendSuccess(
                () -> Component.literal("  location: ")
                    .append(copyableId(idStr))
                    .append(
                        Component.literal(
                            "  biomass=" + biomass
                                + "  members=" + totalMembers
                                + " (loaded=" + loadedMembers + " banked=" + (totalMembers - loadedMembers) + ")"
                        )
                    ),
                false
            );
        }

        return 1;
    }

    /**
     * Adults of this location that are LOADED RIGHT NOW - the ones you could walk up to and count.
     * <p>
     * ⚠⚠ THE OLD {@code members=} READ {@code knownMembersByType} AND WAS WRONG IN BOTH DIRECTIONS AT ONCE. That map is
     * a persisted roster of member UUIDs: it holds NO reserves at all, and it keeps the UUIDs of members that unloaded
     * without being banked (queens, convoy members, name-tagged specimens) forever. A live log had it print
     * {@code members=1} for a hive that four minutes later evacuated FIFTY-TWO members - they were all in the bank,
     * which that map never sees. {@code CastePopulation}'s own header says known members are deliberately excluded from
     * the real counts "because unloaded UUIDs can become stale", and this was the last readout still trusting them.
     * </p>
     * <p>
     * The commands now print the reliable total (loaded + all three reserve banks - the SAME figure as the boss bar)
     * and break the two apart, because "how many are standing here" and "how many does this hive own" are both
     * questions worth answering and conflating them is what made the number look broken.
     * </p>
     */
    private static int loadedAdultCount(HiveLocation location) {
        var count = 0;
        for (var entry : location.loadedMembersByType().entrySet()) {
            if (entry.getKey().is(com.alien.common.registry.tag.AlienEntityTypeTags.XENOMORPHS)) {
                count += entry.getValue().size();
            }
        }
        return count;
    }

    /**
     * Lists every hive location under a lineage with per-location biomass + members and lineage-wide totals. With no
     * argument it uses the nearest queen's lineage; pass a lineage id (copy it from {@code inspect_queen}) to inspect
     * any lineage. This is the lineage-wide counterpart to {@code inspect_queen}, which only shows a queen's own hive.
     */
    private static int inspectLineageNearest(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var queen = nearestQueen(ctx);
        if (queen == null) {
            ctx.getSource().sendFailure(Component.literal("No queen within 64 blocks — pass a lineage id instead."));
            return 0;
        }
        net.minecraft.resources.ResourceLocation lineageId = null;
        for (var factionId : Alien.MOD.factions().getFactionIds(queen.getUUID())) {
            if (LineageIds.isLineageId(factionId)) {
                lineageId = factionId;
                break;
            }
        }
        if (lineageId == null) {
            ctx.getSource().sendFailure(Component.literal("Nearest queen is not bound to a lineage yet."));
            return 0;
        }
        return printLineage(ctx, lineageId);
    }

    private static int inspectLineageById(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        return printLineage(ctx, resolveLineageArg(ctx));
    }

    private static int printLineage(
        com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
        net.minecraft.resources.ResourceLocation lineageId
    ) {
        var src = ctx.getSource();
        var locationIds = HiveLocationRegistry.INSTANCE.byLineage(lineageId);

        src.sendSuccess(
            () -> Component.literal("=== Lineage ")
                .append(copyableId(lineageId.toString()))
                .append(Component.literal(" ===")),
            false
        );

        if (locationIds.isEmpty()) {
            src.sendSuccess(() -> Component.literal("  no locations under this lineage"), false);
            return 0;
        }

        var total = locationIds.size();
        var totalBiomass = 0;
        var totalMembers = 0;
        var loaded = 0;
        for (var locId : locationIds) {
            var location = HiveLocationRegistry.INSTANCE.get(locId);
            if (location == null) {
                src.sendSuccess(
                    () -> Component.literal("  ")
                        .append(copyableId(locId.toString()))
                        .append(Component.literal("  (not loaded)")),
                    false
                );
                continue;
            }
            loaded++;
            var biomass = location.biomass();
            var members = com.alien.common.gameplay.hive.economy.CastePopulation
                .totalReliableXenomorphPopulation(location);
            var loadedMembers = loadedAdultCount(location);
            totalBiomass += biomass;
            totalMembers += members;

            var center = location.centerPos();
            var dimPath = location.dimension().location().getPath();
            var founder = location.founderId();
            var founderStr = founder != null ? founder.toString().substring(0, 8) : "none";
            var idStr = locId.toString();
            src.sendSuccess(
                () -> Component.literal("  ")
                    .append(copyableId(idStr))
                    .append(
                        Component.literal(
                            "  biomass=" + biomass
                                + "  members=" + members
                                + " (loaded=" + loadedMembers + " banked=" + (members - loadedMembers) + ")"
                                + "  @ " + dimPath + " " + center.getX() + " " + center.getY() + " " + center.getZ()
                                + "  founder=" + founderStr
                        )
                    ),
                false
            );
        }

        var fBiomass = totalBiomass;
        var fMembers = totalMembers;
        var fLoaded = loaded;
        src.sendSuccess(
            () -> Component.literal(
                "  totals: locations=" + fLoaded + "/" + total + " loaded  biomass=" + fBiomass
                    + "  members=" + fMembers
            ),
            false
        );
        return fLoaded;
    }

    /** Zeroes the nearest hibernating queen's sleep timer so she wakes and founds on the next tick. */
    /**
     * Debug (Slice B test harness): force the nearest ready-to-found queen to settle and found immediately at her
     * current chunk, bypassing the out-of-combat settlement timer that combat keeps resetting. Mirrors the production
     * commit path ({@code SpreadZoneCheck} -> {@code HiveLocationFoundingService}); if the spread-zone check blocks her
     * (e.g. too close to another claim) it reports that reason instead of founding.
     */
    /**
     * Founds the nearest queen where she stands, from ANY phase.
     * <p>
     * ⭐⭐ ONE CODE PATH WITH THE POISON JELLY BLOCK, ON PURPOSE. This used to carry its own copy of the logic and had
     * drifted: it required isReadyToFound(), so it only worked on a queen ALREADY in FOUNDING_HANDOFF - useless on a
     * new queen, which is the only kind anyone wants to place. The block handles any phase, and a command that does
     * less than the survival item is a command nobody can be told to use.
     * </p>
     * <p>
     * ⚠ THE TERRITORY GUARD COMES WITH IT. ForcedQueenSettlement waives only the surface rule; another hive's ground
     * still refuses, so this cannot be used to overlap hives either.
     * </p>
     */
    private static int skipSettlement(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var queen = nearestQueen(ctx);
        if (queen == null) {
            ctx.getSource().sendFailure(Component.literal("No queen within 64 blocks."));
            return 0;
        }

        if (!(queen.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            ctx.getSource().sendFailure(Component.literal("Server level only."));
            return 0;
        }

        var refusal = com.alien.common.gameplay.hive.lifecycle.ForcedQueenSettlement
            .force(serverLevel, queen, ctx.getSource().getPlayer());

        if (refusal != null) {
            ctx.getSource().sendFailure(Component.literal(refusal));
            return 0;
        }

        ctx.getSource()
            .sendSuccess(
                () -> Component.literal("Queen " + queen.getUUID() + " founded her hive where she was standing."),
                true
            );
        return 1;
    }

    private static int hibernationSkip(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var queen = nearestQueen(ctx);
        if (queen == null) {
            ctx.getSource().sendFailure(Component.literal("No queen within 64 blocks."));
            return 0;
        }

        var mgr = queen.getLifecyclePhaseManager();
        if (mgr.getPhase() != com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenLifecyclePhase.HIBERNATION) {
            ctx.getSource()
                .sendFailure(Component.literal("Nearest queen is in phase " + mgr.getPhase() + ", not HIBERNATION."));
            return 0;
        }

        mgr.skipHibernation();
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal("Skipped hibernation for queen " + queen.getUUID() + " — she founds next tick."),
                true
            );
        return 1;
    }

    /**
     * Reports the slab band of the hive location whose claimed chunk the player is standing in (or the nearest loaded
     * location if the player is not inside one), and whether the player's current Y is inside that band. Phase 1 debug
     * aid for verifying the spawn/resin Y-clamp.
     */
    private static int inspectSlab(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var player = Objects.requireNonNull(ctx.getSource().getPlayer());
        var level = ctx.getSource().getLevel();
        var playerY = player.blockPosition().getY();

        var chunk = new ChunkPos(player.blockPosition());
        var location = HiveLocationRegistry.INSTANCE.getByChunk(level.dimension(), chunk);

        if (location == null) {
            ctx.getSource()
                .sendFailure(
                    Component.literal("You are not standing in any hive's claimed chunk (" + chunk + ").")
                );
            return 0;
        }

        var floor = location.hiveFloorY();
        var ceiling = location.hiveCeilingY();
        var inside = location.withinSlab(playerY);

        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "Hive " + location.id() + "\n"
                        + "  center=" + location.centerPos() + "\n"
                        + "  slab band: Y " + floor + " .. " + ceiling + " (with tolerance)\n"
                        + "  your Y=" + playerY + " -> " + (inside
                            ? "INSIDE slab (spawns allowed here)"
                            : "OUTSIDE slab (spawns clamped away here)")
                ),
                false
            );
        return inside ? 1 : 0;
    }

    /**
     * Toggles {@link com.alien.common.gameplay.hive.spawning.HiveLoadedSpawner#DEBUG_SPAWN_REJECTS}. While on, every
     * spawn attempt rejected for being outside a hive's slab is logged to the server console. Phase 1 debug aid.
     */
    private static int toggleRouter(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        com.alien.common.gameplay.hive.structure.HiveRouter.ENABLED =
            !com.alien.common.gameplay.hive.structure.HiveRouter.ENABLED;
        boolean now = com.alien.common.gameplay.hive.structure.HiveRouter.ENABLED;
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "Blueprint router is now " + (now ? "ON (goal-based)" : "OFF (greedy planner)") + "."
                ),
                false
            );
        return now ? 1 : 0;
    }

    private static int toggleRender(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var player = Objects.requireNonNull(ctx.getSource().getPlayer());
        var now = !com.alien.common.network.handler.HiveRenderToggleHandler.isEnabled(player.getUUID());
        com.alien.common.network.handler.HiveRenderToggleHandler.setEnabled(player, now);
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal("Hive render overlay is now " + (now ? "ON" : "OFF") + "."),
                false
            );
        return now ? 1 : 0;
    }

    private static int toggleDebugSpawns(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var now = !com.alien.common.gameplay.hive.spawning.HiveLoadedSpawner.DEBUG_SPAWN_REJECTS;
        com.alien.common.gameplay.hive.spawning.HiveLoadedSpawner.DEBUG_SPAWN_REJECTS = now;
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal("Hive spawn-reject logging is now " + (now ? "ON" : "OFF") + "."),
                true
            );
        return now ? 1 : 0;
    }

    private static int claimRadius(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var location = resolveLocationArgOrHere(ctx);
        if (location == null) {
            return 0;
        }
        var locationId = location.id();
        var radius = IntegerArgumentType.getInteger(ctx, "radius");

        var centerChunk = new ChunkPos(location.centerPos());
        var serverLevel = ctx.getSource().getLevel();
        var added = 0;

        for (var dx = -radius; dx <= radius; dx++) {
            for (var dz = -radius; dz <= radius; dz++) {
                var chunk = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                if (
                    com.alien.common.gameplay.hive.growth.HiveLocationClaims.claim(
                        serverLevel,
                        location,
                        chunk,
                        serverLevel.getGameTime()
                    )
                ) {
                    added++;
                }
            }
        }

        var finalAdded = added;
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "Claimed " + finalAdded + " new chunks around " + locationId + " (radius=" + radius + ")."
                        + " Total claimed=" + location.claimedChunks().size()
                ),
                true
            );
        return added;
    }

    private static int inspectSettlement(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var snapshot = QueenSettlementDetector.snapshot();
        var currentTick = ctx.getSource().getLevel().getGameTime();
        var settlementTicks = HiveLocationRegistry.INSTANCE.config().settlementTicks();

        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "Settlement detector: " + snapshot.size() + " queens currently anchored "
                        + "(threshold=" + settlementTicks + " ticks):"
                ),
                false
            );

        if (snapshot.isEmpty()) {
            ctx.getSource()
                .sendSuccess(
                    () -> Component.literal("  (none — no queens are standing still without combat)"),
                    false
                );
            return 0;
        }

        for (var entry : snapshot.entrySet()) {
            var elapsed = entry.getValue().accumulatedTicks();
            var remaining = Math.max(0L, settlementTicks - elapsed);
            ctx.getSource()
                .sendSuccess(
                    () -> Component.literal(
                        "  " + entry.getKey()
                            + " @ chunk " + entry.getValue().chunk()
                            + " elapsed=" + elapsed + "t remaining=" + remaining + "t"
                    ),
                    false
                );
        }

        return snapshot.size();
    }

    private static int killLocation(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var location = resolveLocationArgOrHere(ctx);
        if (location == null) {
            return 0;
        }
        var locationId = location.id();

        var faction = Alien.MOD.factions().get(location.lineageFactionId());
        if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
            ctx.getSource().sendFailure(Component.literal("Owning lineage missing for " + locationId));
            return 0;
        }

        var serverLevel = ctx.getSource().getServer().getLevel(location.dimension());
        if (serverLevel == null) {
            ctx.getSource().sendFailure(Component.literal("Dimension not loaded: " + location.dimension().location()));
            return 0;
        }

        LocationDeathHandler.killAdmin(serverLevel, location, lineage, "kill_location debug command");

        ctx.getSource()
            .sendSuccess(() -> Component.literal("Killed location " + locationId), true);
        return 1;
    }

    private static int forceGrowLocation(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var location = resolveLocationArgOrHere(ctx);
        if (location == null) {
            return 0;
        }
        var locationId = location.id();

        var faction = Alien.MOD.factions().get(location.lineageFactionId());
        if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
            ctx.getSource().sendFailure(Component.literal("Owning lineage missing for " + locationId));
            return 0;
        }

        var serverLevel = ctx.getSource().getServer().getLevel(location.dimension());
        if (serverLevel == null) {
            ctx.getSource().sendFailure(Component.literal("Dimension not loaded: " + location.dimension().location()));
            return 0;
        }

        var beforeChunks = location.claimedChunks().size();
        var beforeBiomass = location.biomass();
        CatchUpEngine.catchUpTo(serverLevel, location, lineage, serverLevel.getGameTime());

        var addedChunks = location.claimedChunks().size() - beforeChunks;
        var biomassDelta = location.biomass() - beforeBiomass;

        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "Force-grew " + locationId + ": +" + addedChunks + " chunks, biomass " + beforeBiomass
                        + " → " + location.biomass() + " (delta=" + biomassDelta + ")"
                ),
                true
            );
        return addedChunks;
    }

    private static int listConvoys(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var totalConvoys = 0;
        var lineagesWithConvoys = 0;

        for (var factionId : Alien.MOD.factions().getAllIds()) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
                continue;
            }
            if (lineage.convoys().isEmpty()) {
                continue;
            }
            lineagesWithConvoys++;
            ctx.getSource()
                .sendSuccess(
                    () -> Component.literal(
                        "Lineage " + factionId + " has " + lineage.convoys().size() + " convoy(s):"
                    ),
                    false
                );
            for (var convoy : lineage.convoys()) {
                totalConvoys++;
                if (convoy instanceof Convoy.Reinforcement reinforcement) {
                    ctx.getSource()
                        .sendSuccess(
                            () -> Component.literal(
                                "  REINFORCEMENT " + reinforcement.id()
                                    + " src=" + reinforcement.sourceLocationId()
                                    + " dst=" + reinforcement.destinationLocationId()
                                    + " pos=" + formatVec(reinforcement.currentPos())
                                    + " composition=" + reinforcement.composition().getCount()
                            ),
                            false
                        );
                } else if (convoy instanceof Convoy.Migration migration) {
                    ctx.getSource()
                        .sendSuccess(
                            () -> Component.literal(
                                "  MIGRATION " + migration.id()
                                    + " src=" + migration.sourceLocationId()
                                    + " dst=" + migration.destinationLocationId()
                                    + " pos=" + formatVec(migration.currentPos())
                                    + " composition=" + migration.composition().getCount()
                                    + " biomass=" + migration.biomassPayload()
                                    + (migration.carriesEmpress() ? " (carries empress)" : "")
                            ),
                            false
                        );
                } else if (convoy instanceof Convoy.Raid raid) {
                    ctx.getSource()
                        .sendSuccess(
                            () -> Component.literal(
                                "  RAID " + raid.id()
                                    + " src=" + raid.sourceLocationId()
                                    + " target=" + raid.targetPlayerId()
                                    + " pos=" + formatVec(raid.currentPos())
                                    + " lastSeen=" + raid.lastKnownTargetPos()
                                    + " composition=" + raid.composition().getCount()
                                    + " expiresAt=" + raid.expiresAtTick()
                            ),
                            false
                        );
                }
            }
        }

        var finalTotalConvoys = totalConvoys;
        var finalLineagesWithConvoys = lineagesWithConvoys;
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "Total: " + finalTotalConvoys + " convoy(s) across " + finalLineagesWithConvoys + " lineage(s)"
                ),
                false
            );
        return totalConvoys;
    }

    private static String formatVec(net.minecraft.world.phys.Vec3 v) {
        return String.format("(%.1f, %.1f, %.1f)", v.x, v.y, v.z);
    }

    private static int forceDispatchReinforcements(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ReinforcementDispatcher.scanAndDispatch(ctx.getSource().getServer());
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal("Triggered ReinforcementDispatcher.scanAndDispatch — see /list_convoys for results"),
                true
            );
        return 1;
    }

    private static int forceMigration(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var location = resolveLocationArgOrHere(ctx);
        if (location == null) {
            return 0;
        }
        var locationId = location.id();

        var faction = Alien.MOD.factions().get(location.lineageFactionId());
        if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
            ctx.getSource().sendFailure(Component.literal("Lineage missing for " + locationId));
            return 0;
        }

        var ok = MigrationDispatch.forceMigration(ctx.getSource().getServer(), location, lineage);
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    ok
                        ? "Migration dispatched from " + locationId + " — see /list_convoys for the convoy"
                        : "Migration declined (no sister destination, or lineage data missing)"
                ),
                true
            );
        return ok ? 1 : 0;
    }

    private static int forceRaidOnSelf(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var player = Objects.requireNonNull(ctx.getSource().getPlayer());
        var lineageFactionId = resolveLineageArg(ctx);
        var faction = Alien.MOD.factions().get(lineageFactionId);

        if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
            ctx.getSource().sendFailure(Component.literal("No lineage with id " + lineageFactionId));
            return 0;
        }

        var waveProfile = RaidWaveProfileRegistry.forVariant(lineage.variant());
        var ok = RaidDispatch.forceRaid(ctx.getSource().getServer(), lineage, lineageFactionId, player);
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    ok
                        ? "Raid dispatched against " + player.getGameProfile().getName()
                            + " from largest eligible source — see /list_convoys"
                        : "Raid declined (no eligible source — needs a HARBINGER in a location with " +
                            HiveLocationRegistry.INSTANCE.config().raidMinLocationSizeChunks() + "+ chunks, " +
                            "and reserves that satisfy the " + waveProfile.totalSize() + "-member " +
                            lineage.variant().name() + " raid wave profile)"
                ),
                true
            );
        return ok ? 1 : 0;
    }

    private static int inspectKillAttribution(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var lineageFactionId = resolveLineageArg(ctx);
        var faction = Alien.MOD.factions().get(lineageFactionId);

        if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
            ctx.getSource().sendFailure(Component.literal("No lineage with id " + lineageFactionId));
            return 0;
        }

        var attribution = lineage.killAttributionByPlayer();
        var currentTick = ctx.getSource().getLevel().getGameTime();
        var aggroWindow = HiveLocationRegistry.INSTANCE.config().raidAggroWindowTicks();

        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "Lineage " + lineageFactionId + " kill attribution (" + attribution.size() + " players, "
                        + "aggro window=" + aggroWindow + " ticks):"
                ),
                false
            );

        if (attribution.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("  (none)"), false);
            return 0;
        }

        for (var entry : attribution.entrySet()) {
            var recent = lineage.countRecentKills(entry.getKey(), currentTick, aggroWindow);
            ctx.getSource()
                .sendSuccess(
                    () -> Component.literal(
                        "  " + entry.getKey() + " — " + recent + " kill(s) within window"
                    ),
                    false
                );
        }
        return attribution.size();
    }

    private static int listEmerging(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var snapshot = EmpressEmergenceRitual.snapshot();
        var currentTick = ctx.getSource().getLevel().getGameTime();
        var moltDuration = HiveLocationRegistry.INSTANCE.config().empressMoltDurationTicks();

        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "Empress emergence: " + snapshot.size() + " queens currently molting (duration=" + moltDuration + "t):"
                ),
                false
            );

        if (snapshot.isEmpty()) {
            ctx.getSource()
                .sendSuccess(
                    () -> Component.literal("  (none — run /force_emergence_scan to trigger if conditions are met)"),
                    false
                );
            return 0;
        }

        for (var entry : snapshot.entrySet()) {
            var elapsed = currentTick - entry.getValue().startedAtTick();
            var remaining = Math.max(0L, moltDuration - elapsed);
            ctx.getSource()
                .sendSuccess(
                    () -> Component.literal(
                        "  queen=" + entry.getKey()
                            + " lineage=" + entry.getValue().lineageFactionId()
                            + " elapsed=" + elapsed + "t remaining=" + remaining + "t"
                    ),
                    false
                );
        }

        return snapshot.size();
    }

    private static int forceEmergenceScan(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        EmpressEmergenceTask.scanAndStart(ctx.getSource().getServer());
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "EmpressEmergenceTask.scanAndStart fired — see /list_emerging for results"
                ),
                true
            );
        return 1;
    }

    private static int forceInvariantCheck(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        LineageInvariantTask.scanAll();
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal("LineageInvariantTask.scanAll fired — see server log for any evictions"),
                true
            );
        return 1;
    }

    private static int rebuildIndexes(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        HiveLocationRegistry.INSTANCE.rebuildFromFactions();
        HiveLocationRegistry.INSTANCE.repairTerritoryClaims(ctx.getSource().getServer());
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "HiveLocationRegistry rebuilt: locations="
                        + HiveLocationRegistry.INSTANCE.locationCount()
                        + " lineages="
                        + HiveLocationRegistry.INSTANCE.lineageCount()
                        + " (see server log for details)"
                ),
                true
            );
        return HiveLocationRegistry.INSTANCE.locationCount();
    }

    private static int validate(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        HiveLocationRegistry.INSTANCE.validate();
        HiveLocationRegistry.INSTANCE.repairTerritoryClaims(ctx.getSource().getServer());
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "Validate complete. Locations=" + HiveLocationRegistry.INSTANCE.locationCount()
                        + " lineages=" + HiveLocationRegistry.INSTANCE.lineageCount()
                        + " (see server log for details)"
                ),
                false
            );
        return 1;
    }

    private static int forceQueenlessAdvance(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var lineageId = resolveLineageArg(ctx);
        var advanced = com.alien.common.gameplay.hive.lifecycle.QueenlessMaturationTask.forceAdvance(
            ctx.getSource().getServer(),
            lineageId
        );
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal("Queenless maturation force-advanced " + advanced + " location(s) in lineage " + lineageId),
                true
            );
        return advanced;
    }

    private static int inspectQueenlessMaturation(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var server = ctx.getSource().getServer();
        var stageInterval = HiveLocationRegistry.INSTANCE.config().protoHiveStageInterval();
        var currentTick = server.overworld().getGameTime();
        var reported = 0;

        for (var factionId : Alien.MOD.factions().getAllIds()) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
                continue;
            }
            if (lineage.empressId() != null || lineage.pendingEmpressEmergence()) {
                continue;
            }

            for (var location : lineage.locationsById().values()) {
                if (!location.isAlive()) {
                    continue;
                }
                var leaderId = location.leadership().getLeaderIdOrNull();
                var leader = com.alien.common.gameplay.hive.lifecycle.QueenlessMaturationTask.peekLeader(server, location);
                var ticksSinceAdvance = location.queenlessMaturationLastAdvanceTick() == Long.MIN_VALUE
                    ? -1
                    : currentTick - location.queenlessMaturationLastAdvanceTick();
                var ticksUntilNext = ticksSinceAdvance < 0 ? -1 : Math.max(0, stageInterval - ticksSinceAdvance);

                final var localFactionId = factionId;
                final var localLeaderId = leaderId;
                final var localLeader = leader;
                final var localUntil = ticksUntilNext;
                ctx.getSource()
                    .sendSuccess(
                        () -> Component.literal(
                            "  lineage=" + localFactionId
                                + " location=" + location.id()
                                + " leader=" + localLeaderId
                                + " type=" + (localLeader == null
                                    ? "(unloaded)"
                                    : localLeader.getType().builtInRegistryHolder().key().location())
                                + " ticksUntilNextAdvance=" + (localUntil < 0 ? "(timer reset on next scan)" : localUntil)
                        ),
                        false
                    );
                reported++;
            }
        }

        var finalReported = reported;
        ctx.getSource()
            .sendSuccess(() -> Component.literal("Queenless maturation: " + finalReported + " location(s) eligible"), false);
        return reported;
    }

}
