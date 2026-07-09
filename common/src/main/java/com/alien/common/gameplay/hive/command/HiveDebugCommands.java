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

import java.util.Locale;
import java.util.Objects;

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

    public static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("hive")
            .then(Commands.literal("list_lineages").executes(HiveDebugCommands::listLineages))
            .then(Commands.literal("list_tracked").executes(HiveDebugCommands::listTracked))
            .then(Commands.literal("dump_indexes").executes(HiveDebugCommands::dumpIndexes))
            .then(Commands.literal("inhibit_here").executes(HiveDebugCommands::inhibitHere))
            .then(
                Commands.literal("inspect_location")
                    .then(
                        Commands.argument(LOCATION_ID_ARG, ResourceLocationArgument.id())
                            .executes(HiveDebugCommands::inspectLocation)
                    )
            )
            .then(
                Commands.literal("list_vents")
                    .then(
                        Commands.argument(LOCATION_ID_ARG, ResourceLocationArgument.id())
                            .executes(HiveDebugCommands::listVents)
                    )
            )
            .then(
                Commands.literal("kill_location")
                    .then(
                        Commands.argument(LOCATION_ID_ARG, ResourceLocationArgument.id())
                            .executes(HiveDebugCommands::killLocation)
                    )
            )
            .then(
                Commands.literal("mint_lineage_at_player")
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
                Commands.literal("mint_location_in_lineage")
                    .requires(CommandSourceStack::isPlayer)
                    .then(
                        Commands.argument(LINEAGE_ID_ARG, ResourceLocationArgument.id())
                            .executes(HiveDebugCommands::mintLocationInLineage)
                    )
            )
            .then(
                Commands.literal("inspect_variant")
                    .then(
                        Commands.argument(VARIANT_ARG, StringArgumentType.string())
                            .executes(HiveDebugCommands::inspectVariant)
                    )
            )
            .then(
                Commands.literal("force_variant_join_nearby")
                    .requires(CommandSourceStack::isPlayer)
                    .executes(HiveDebugCommands::forceVariantJoinNearby)
            )
            .then(
                Commands.literal("force_lineage_join_nearby")
                    .requires(CommandSourceStack::isPlayer)
                    .then(
                        Commands.argument(LINEAGE_ID_ARG, ResourceLocationArgument.id())
                            .executes(HiveDebugCommands::forceLineageJoinNearby)
                    )
            )
            .then(
                Commands.literal("force_shed_check_nearby")
                    .requires(CommandSourceStack::isPlayer)
                    .executes(HiveDebugCommands::forceShedCheckNearby)
            )
            .then(Commands.literal("rebuild_indexes").executes(HiveDebugCommands::rebuildIndexes))
            .then(Commands.literal("force_invariant_check").executes(HiveDebugCommands::forceInvariantCheck))
            .then(Commands.literal("list_emerging").executes(HiveDebugCommands::listEmerging))
            .then(
                Commands.literal("inspect_slab")
                    .requires(CommandSourceStack::isPlayer)
                    .executes(HiveDebugCommands::inspectSlab)
            )
            .then(
                Commands.literal("inspect_ovipositor")
                    .requires(CommandSourceStack::isPlayer)
                    .executes(HiveDebugCommands::inspectOvipositor)
            )
            .then(
                Commands.literal("inspect_queen")
                    .requires(CommandSourceStack::isPlayer)
                    .executes(HiveDebugCommands::inspectQueen)
            )
            .then(
                Commands.literal("inspect_empress")
                    .requires(CommandSourceStack::isPlayer)
                    .executes(HiveDebugCommands::inspectEmpress)
            )
            .then(
                Commands.literal("inspect_lineage")
                    .requires(CommandSourceStack::isPlayer)
                    .executes(HiveDebugCommands::inspectLineageNearest)
                    .then(
                        Commands.argument(LINEAGE_ID_ARG, ResourceLocationArgument.id())
                            .executes(HiveDebugCommands::inspectLineageById)
                    )
            )
            .then(
                Commands.literal("hibernation_skip")
                    .requires(CommandSourceStack::isPlayer)
                    .executes(HiveDebugCommands::hibernationSkip)
            )
            .then(
                Commands.literal("skip_settlement")
                    .requires(CommandSourceStack::isPlayer)
                    .executes(HiveDebugCommands::skipSettlement)
            )
            .then(
                Commands.literal("log_spawns")
                    .executes(HiveDebugCommands::toggleDebugSpawns)
            )
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
            .then(Commands.literal("force_emergence_scan").executes(HiveDebugCommands::forceEmergenceScan))
            .then(Commands.literal("inspect_settlement").executes(HiveDebugCommands::inspectSettlement))
            .then(
                Commands.literal("force_grow_location")
                    .then(
                        Commands.argument(LOCATION_ID_ARG, ResourceLocationArgument.id())
                            .executes(HiveDebugCommands::forceGrowLocation)
                    )
            )
            .then(Commands.literal("list_convoys").executes(HiveDebugCommands::listConvoys))
            .then(Commands.literal("force_dispatch_reinforcements").executes(HiveDebugCommands::forceDispatchReinforcements))
            .then(
                Commands.literal("force_migration")
                    .then(
                        Commands.argument(LOCATION_ID_ARG, ResourceLocationArgument.id())
                            .executes(HiveDebugCommands::forceMigration)
                    )
            )
            .then(
                Commands.literal("force_raid")
                    .requires(CommandSourceStack::isPlayer)
                    .then(
                        Commands.argument(LINEAGE_ID_ARG, ResourceLocationArgument.id())
                            .executes(HiveDebugCommands::forceRaidOnSelf)
                    )
            )
            .then(
                Commands.literal("inspect_kill_attribution")
                    .then(
                        Commands.argument(LINEAGE_ID_ARG, ResourceLocationArgument.id())
                            .executes(HiveDebugCommands::inspectKillAttribution)
                    )
            )
            .then(
                Commands.literal("claim_radius")
                    .then(
                        Commands.argument(LOCATION_ID_ARG, ResourceLocationArgument.id())
                            .then(
                                Commands.argument("radius", IntegerArgumentType.integer(0, 32))
                                    .executes(HiveDebugCommands::claimRadius)
                            )
                    )
            )
            .then(
                Commands.literal("add_reserve")
                    .then(
                        Commands.argument(LOCATION_ID_ARG, ResourceLocationArgument.id())
                            .then(
                                Commands.argument(ENTITY_TYPE_ARG, ResourceLocationArgument.id())
                                    .then(
                                        Commands.argument(COUNT_ARG, IntegerArgumentType.integer(1))
                                            .executes(HiveDebugCommands::addReserve)
                                    )
                            )
                    )
            )
            .then(
                Commands.literal("add_biomass")
                    .then(
                        Commands.argument(LOCATION_ID_ARG, ResourceLocationArgument.id())
                            .then(
                                Commands.argument(COUNT_ARG, IntegerArgumentType.integer(1))
                                    .executes(HiveDebugCommands::addBiomass)
                            )
                    )
            )
            .then(
                Commands.literal("force_queenless_advance")
                    .then(
                        Commands.argument(LINEAGE_ID_ARG, ResourceLocationArgument.id())
                            .executes(HiveDebugCommands::forceQueenlessAdvance)
                    )
            )
            .then(Commands.literal("inspect_queenless_maturation").executes(HiveDebugCommands::inspectQueenlessMaturation))
            .then(Commands.literal("validate").executes(HiveDebugCommands::validate));
    }

    /**
     * Debug toggle (Slice B1 test harness): flips the {@code inhibited} flag on the hive location claiming the player's
     * current chunk. An inhibited location runs no autonomy (claims/biomass/spawning/contests) — used to verify the
     * gate before the real queen-driven claim lifecycle (Slice B2) wires it.
     */
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
        var locationId = ResourceLocationArgument.getId(ctx, LOCATION_ID_ARG);
        var location = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(locationId));
        if (location == null) {
            ctx.getSource().sendFailure(Component.literal("No hive location with id " + locationId));
            return 0;
        }

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
        var locationId = ResourceLocationArgument.getId(ctx, LOCATION_ID_ARG);
        var entityTypeId = ResourceLocationArgument.getId(ctx, ENTITY_TYPE_ARG);
        var count = IntegerArgumentType.getInteger(ctx, COUNT_ARG);

        var location = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(locationId));
        if (location == null) {
            ctx.getSource().sendFailure(Component.literal("No hive location with id " + locationId));
            return 0;
        }

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

        var lineageFactionId = ResourceLocationArgument.getId(ctx, LINEAGE_ID_ARG);
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

        var lineageFactionId = ResourceLocationArgument.getId(ctx, LINEAGE_ID_ARG);
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
        var locationId = ResourceLocationArgument.getId(ctx, LOCATION_ID_ARG);
        var location = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(locationId));
        if (location == null) {
            ctx.getSource().sendFailure(Component.literal("No hive location with id " + locationId));
            return 0;
        }

        var vents = location.ventManager().allVents();
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal("Location " + location.id() + " has " + vents.size() + " known vent(s):"),
                false
            );
        for (var pos : vents) {
            ctx.getSource().sendSuccess(() -> Component.literal("  " + pos.toShortString()), false);
        }
        return vents.size();
    }

    /**
     * Debug: grants biomass to a location so ovipositor/claim/economy behaviour can be tested without waiting for
     * passive income.
     */
    private static int addBiomass(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var locationId = ResourceLocationArgument.getId(ctx, LOCATION_ID_ARG);
        var amount = IntegerArgumentType.getInteger(ctx, COUNT_ARG);
        var location = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(locationId));
        if (location == null) {
            ctx.getSource().sendFailure(Component.literal("No hive location with id " + locationId));
            return 0;
        }
        location.setBiomass(location.biomass() + amount);
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
            var members = home.knownMembersByType().values().stream().mapToInt(java.util.Set::size).sum();
            src.sendSuccess(
                () -> Component.literal("  location: ")
                    .append(copyableId(idStr))
                    .append(Component.literal("  biomass=" + biomass + "  members=" + members)),
                false
            );
        }

        return 1;
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
        return printLineage(ctx, ResourceLocationArgument.getId(ctx, LINEAGE_ID_ARG));
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
            var members = location.knownMembersByType().values().stream().mapToInt(java.util.Set::size).sum();
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
                            "  biomass=" + biomass + "  members=" + members
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
    private static int skipSettlement(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var queen = nearestQueen(ctx);
        if (queen == null) {
            ctx.getSource().sendFailure(Component.literal("No queen within 64 blocks."));
            return 0;
        }

        var phaseManager = queen.getLifecyclePhaseManager();
        if (!phaseManager.isReadyToFound()) {
            ctx.getSource()
                .sendFailure(
                    Component.literal(
                        "Nearest queen is in phase "
                            + phaseManager.getPhase()
                            + " \u2014 not ready to found (must finish developing -> location -> hibernation first)."
                    )
                );
            return 0;
        }

        var pos = new ChunkPos(queen.blockPosition()).getMiddleBlockPosition(queen.blockPosition().getY());
        var result = com.alien.common.gameplay.hive.lifecycle.SpreadZoneCheck.evaluate(queen, pos);
        if (result instanceof com.alien.common.gameplay.hive.lifecycle.SpreadZoneResult.Blocked blocked) {
            ctx.getSource()
                .sendFailure(Component.literal("Spread-zone check blocked founding: " + blocked.reason()));
            return 0;
        }

        QueenSettlementDetector.forget(queen.getUUID());
        var locationId =
            com.alien.common.gameplay.hive.lifecycle.HiveLocationFoundingService.foundFromResult(queen, pos, result);
        if (locationId == null) {
            ctx.getSource().sendFailure(Component.literal("Founding service returned no location (see server log)."));
            return 0;
        }

        final var foundedId = locationId;
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "Skipped settlement \u2014 queen " + queen.getUUID() + " founded location " + foundedId + "."
                ),
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
        var locationId = ResourceLocationArgument.getId(ctx, LOCATION_ID_ARG);
        var radius = IntegerArgumentType.getInteger(ctx, "radius");
        var location = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(locationId));

        if (location == null) {
            ctx.getSource().sendFailure(Component.literal("No hive location with id " + locationId));
            return 0;
        }

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
        var locationId = ResourceLocationArgument.getId(ctx, LOCATION_ID_ARG);
        var location = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(locationId));

        if (location == null) {
            ctx.getSource().sendFailure(Component.literal("No hive location with id " + locationId));
            return 0;
        }

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
        var locationId = ResourceLocationArgument.getId(ctx, LOCATION_ID_ARG);
        var location = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(locationId));

        if (location == null) {
            ctx.getSource().sendFailure(Component.literal("No hive location with id " + locationId));
            return 0;
        }

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
        var locationId = ResourceLocationArgument.getId(ctx, LOCATION_ID_ARG);
        var location = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(locationId));

        if (location == null) {
            ctx.getSource().sendFailure(Component.literal("No hive location with id " + locationId));
            return 0;
        }

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
        var lineageFactionId = ResourceLocationArgument.getId(ctx, LINEAGE_ID_ARG);
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
                        : "Raid declined (no eligible source — needs empress + a location with " +
                            HiveLocationRegistry.INSTANCE.config().raidMinLocationSizeChunks() + "+ chunks, " +
                            "and reserves that satisfy the " + waveProfile.totalSize() + "-member " +
                            lineage.variant().name() + " raid wave profile)"
                ),
                true
            );
        return ok ? 1 : 0;
    }

    private static int inspectKillAttribution(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var lineageFactionId = ResourceLocationArgument.getId(ctx, LINEAGE_ID_ARG);
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
        var lineageId = ResourceLocationArgument.getId(ctx, LINEAGE_ID_ARG);
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
