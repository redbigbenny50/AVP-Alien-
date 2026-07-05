package com.alien.common.gameplay.hive.config;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class HiveConfigSchema {

    private static final List<GroupSpec> GROUP_SPECS = List.of(
            group("Lineage Seeds", "protoHiveStageInterval"),
            group(
                    "Location Lifecycle",
                    "settlementTicks",
                    "locationMaxNoContactTicks",
                    "locationBootstrapGraceTicks",
                    "bossBarDisplayRadiusBlocks",
                    "angryGraceTicks",
                    "contestTickWindow",
                    "minimumHiveLocationDistanceChunks",
                    "initialHiveLocationClaimRadiusChunks"
            ),
            group("Shedding", "shedGraceTicks", "minLineageAgeForShedding"),
            group(
                    "Convoys",
                    "convoySpeedBlocksPerSecond",
                    "arrivalRadiusBlocks",
                    "manifestDistanceBlocks",
                    "convoyInterceptRadiusBlocks",
                    "reinforcementSpeedMultiplier",
                    "migrationSpeedMultiplier",
                    "raidSpeedMultiplier"
            ),
            group("Reinforcements", "reinforcementSourceCooldownTicks", "reinforcementMinSize", "reinforcementMaxSize"),
            group(
                    "Migration",
                    "resettleGraceTicks",
                    "migrationBiomassDecayTicks",
                    "migrationTerritoryFloorChunks",
                    "migrationRallyTicks",
                    "migrationBiomassPayloadCap"
            ),
            group(
                    "Raids",
                    "raidThresholdKills",
                    "raidAggroWindowTicks",
                    "raidMinLocationSizeChunks",
                    "perSourceRaidCooldownTicks",
                    "raidExpiryTicks",
                    "baseRaidSize",
                    "raidSizePerClaimedChunk",
                    "raidEngageRadiusBlocks"
            ),
            group("Combat Pressure", "combatRespiteKillThreshold", "combatRespiteMinTicks", "combatRespiteMaxTicks"),
            group(
                    "Leadership",
                    "empressMoltDurationTicks",
                    "localLeaderPickCadenceTicks",
                    "firewallCooldownTicks",
                    "firewallStabilityScanIntervalTicks",
                    "firewallJellyFloor",
                    "firewallCrowningJellyCost"
            ),
            group(
                    "Abstract Spread",
                    "maxLineageSpreadChunks",
                    "lineageSpreadCooldownTicks",
                    "maxLocationsPerLineage",
                    "maxLocationsUnderEmpress",
                    "minimumPopulationForHiveSpread",
                    "abstractSpreadMinFounderGroupSize",
                    "abstractSpreadMaxFounderGroupSize",
                    "foragerJoinTicks"
            ),
            group(
                    "Claiming And Resin",
                    "claimActivityWindowTicks",
                    "perLocationClaimCooldownTicks",
                    "lineageScanIntervalTicks",
                    "maxChunksPerLocation",
                    "maxTerritoryRadiusChunks",
                    "maxChunksPerLineage",
                    "maxLineagesPerDimensionPerVariant",
                    "maxClaimsPerScan",
                    "resinFullDensityTicks",
                    "maxPassiveClaimsPerUnloadedScan"
            ),
            group(
                    "Biomass",
                    "baseChunkCost",
                    "ovipositorCreationBiomassCost",
                    "resinSpreadBiomassCost",
                    "growthFactor",
                    "baseUnloadedBiomassPerChunkPerSec",
                    "unloadedEmpressBonusPerSec",
                    "loadedBiomassPerLoadedXenomorphPerSec",
                    "loadedBiomassPerNonAlienKill",
                    "loadedBiomassPerResinBlockPlaced",
                    "loadedBiomassPerOvomorphPerSec",
                    "loadedBiomassEmpressPresentBonusPerSec",
                    "loadedBiomassIdleBonusPerSec",
                    "biomassAccumulationCapMultiplier"
            ),
            group(
                    "Persistence",
                    "biomassDirtyThreshold",
                    "lastGrowthTickDirtyThreshold",
                    "slowPathLocationUpdatesPerTick",
                    "passiveClaimCatchUpWindowCap"
            ),
            group(
                    "Population And Spawning",
                    "populationPerChunk",
                    "minimumPopulationRatioForClaiming",
                    "hiveSpawnerMinimumLoadedXenomorphs",
                    "reserveSpawnsCanIgnoreResin",
                    "hiveSpawnerIntervalTicks",
                    "hiveSpawnerMaxSpawnAttemptsPerLocation",
                    "hiveSpawnerMaxSpawnsPerLocation",
                    "maxOvomorphsPerHiveLocation",
                    "royalJellyTicksPerProduction",
                    "scourgeJellyTicksPerQueenProduction",
                    "scourgeJellyTicksPerHarbingerProduction"
            ),
            group("Queen Lifecycle", "queenFrontEndPhasesEnabled")
    );

    private static final List<Field> FIELDS;

    private static final List<Group> GROUPS;

    private static final Map<String, Field> FIELDS_BY_NAME;

    private static final RecordComponent[] COMPONENTS = HiveConfig.class.getRecordComponents();

    private static final Map<String, Integer> COMPONENT_INDEX_BY_NAME = new HashMap<>();

    private static final Constructor<HiveConfig> CANONICAL_CONSTRUCTOR;

    static {
        try {
            var parameterTypes = new Class<?>[COMPONENTS.length];
            for (var i = 0; i < COMPONENTS.length; i++) {
                COMPONENT_INDEX_BY_NAME.put(COMPONENTS[i].getName(), i);
                parameterTypes[i] = COMPONENTS[i].getType();
            }
            CANONICAL_CONSTRUCTOR = HiveConfig.class.getDeclaredConstructor(parameterTypes);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }

        var defaults = HiveConfig.defaults();
        var fields = new ArrayList<Field>();
        var groups = new ArrayList<Group>();
        var seen = new java.util.HashSet<String>();

        for (var spec : GROUP_SPECS) {
            var groupFields = new ArrayList<Field>();
            for (var fieldName : spec.fieldNames()) {
                var field = createField(fieldName, spec.name(), defaults);
                if (field == null) {
                    continue;
                }
                groupFields.add(field);
                fields.add(field);
                seen.add(fieldName);
            }
            if (!groupFields.isEmpty()) {
                groups.add(new Group(spec.name(), List.copyOf(groupFields)));
            }
        }

        var fallbackFields = new ArrayList<Field>();
        for (var component : COMPONENTS) {
            if (seen.contains(component.getName())) {
                continue;
            }
            var field = createField(component.getName(), "Other", defaults);
            if (field != null) {
                fallbackFields.add(field);
                fields.add(field);
            }
        }
        if (!fallbackFields.isEmpty()) {
            groups.add(new Group("Other", List.copyOf(fallbackFields)));
        }

        var byName = new LinkedHashMap<String, Field>();
        for (var field : fields) {
            byName.put(field.name(), field);
        }
        FIELDS = List.copyOf(fields);
        GROUPS = List.copyOf(groups);
        FIELDS_BY_NAME = Map.copyOf(byName);
    }

    private HiveConfigSchema() {}

    public static List<Field> fields() {
        return FIELDS;
    }

    public static List<Group> groups() {
        return GROUPS;
    }

    public static @Nullable Field field(String name) {
        return FIELDS_BY_NAME.get(name);
    }

    public static String valueAsString(HiveConfig config, String fieldName) {
        return formatValue(read(config, fieldName));
    }

    public static CompoundTag toTag(HiveConfig config) {
        var tag = new CompoundTag();
        for (var field : FIELDS) {
            tag.putString(field.name(), valueAsString(config, field.name()));
        }
        return tag;
    }

    public static HiveConfig withParsedValue(HiveConfig config, String fieldName, String value) {
        var field = field(fieldName);
        if (field == null) {
            throw new IllegalArgumentException("Unknown hive config field: " + fieldName);
        }

        var args = new Object[COMPONENTS.length];
        for (var i = 0; i < COMPONENTS.length; i++) {
            var component = COMPONENTS[i];
            args[i] = component.getName().equals(fieldName) ? parse(field, value) : read(config, component.getName());
        }

        try {
            return CANONICAL_CONSTRUCTOR.newInstance(args);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to rebuild HiveConfig", exception);
        }
    }

    private static @Nullable Field createField(String name, String group, HiveConfig defaults) {
        var index = COMPONENT_INDEX_BY_NAME.get(name);
        if (index == null) {
            return null;
        }
        var component = COMPONENTS[index];
        return new Field(name, labelFor(name), group, valueType(component.getType()), valueAsString(defaults, name));
    }

    private static ValueType valueType(Class<?> type) {
        if (type == int.class) {
            return ValueType.INT;
        }
        if (type == long.class) {
            return ValueType.LONG;
        }
        if (type == double.class) {
            return ValueType.DOUBLE;
        }
        if (type == boolean.class) {
            return ValueType.BOOLEAN;
        }
        throw new IllegalArgumentException("Unsupported HiveConfig field type: " + type.getName());
    }

    private static Object parse(Field field, String value) {
        var text = value == null ? "" : value.trim();
        try {
            return switch (field.type()) {
                case INT -> Integer.parseInt(text);
                case LONG -> Long.parseLong(text);
                case DOUBLE -> Double.parseDouble(text);
                case BOOLEAN -> parseBoolean(text);
            };
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid " + field.type().displayName() + " for " + field.name() + ": " + value);
        }
    }

    private static boolean parseBoolean(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "on" -> true;
            case "false", "0", "no", "off" -> false;
            default -> throw new IllegalArgumentException("Invalid boolean: " + value);
        };
    }

    private static Object read(HiveConfig config, String fieldName) {
        Objects.requireNonNull(config, "config");
        var index = COMPONENT_INDEX_BY_NAME.get(fieldName);
        if (index == null) {
            throw new IllegalArgumentException("Unknown hive config field: " + fieldName);
        }
        try {
            return COMPONENTS[index].getAccessor().invoke(config);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to read HiveConfig field: " + fieldName, exception);
        }
    }

    private static String formatValue(Object value) {
        if (value instanceof Double doubleValue) {
            return Double.toString(doubleValue);
        }
        return String.valueOf(value);
    }

    private static String labelFor(String name) {
        var out = new StringBuilder(name.length() + 8);
        for (var i = 0; i < name.length(); i++) {
            var ch = name.charAt(i);
            if (i == 0) {
                out.append(Character.toUpperCase(ch));
            } else if (Character.isUpperCase(ch)) {
                out.append(' ').append(ch);
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private static GroupSpec group(String name, String... fieldNames) {
        return new GroupSpec(name, List.of(fieldNames));
    }

    public enum ValueType {

        INT("integer"),
        LONG("long"),
        DOUBLE("decimal"),
        BOOLEAN("boolean");

        private final String displayName;

        ValueType(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public record Field(
            String name,
            String label,
            String group,
            ValueType type,
            String defaultValue
    ) {}

    public record Group(
            String name,
            List<Field> fields
    ) {}

    private record GroupSpec(
            String name,
            List<String> fieldNames
    ) {}
}