package com.alien.common.gameplay.hive.config;

import com.alien.Alien;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ⭐⭐⭐ THE HIVE CONFIG FILE. Until this existed THERE WAS NO CONFIG FILE AT ALL.
 * <p>
 * ⚠⚠ WHAT WAS ACTUALLY BROKEN: {@code HiveLocationRegistry} initialised to {@link HiveConfig#defaults()} and
 * {@code setConfig} had exactly ONE caller in the whole repo — the network handler behind the in-game inspector GUI.
 * Nothing read a file, nothing wrote one, and the registry never serialised the config into its SavedData. So all ~150
 * fields were hardcoded, an op with the GUI open was the only way to change one, and every change was LOST ON RESTART.
 * A pack author could not pre-configure anything at all.
 * </p>
 * <p>
 * ⭐ NO NEW MACHINERY WAS NEEDED. {@link HiveConfigSchema} already reflected over every record component, grouped them,
 * and could format and parse each one as a string. It only ever lacked somewhere to put the result.
 * </p>
 * <p>
 * PER INSTANCE, NOT PER WORLD: {@code config/avp_alien-hive.json} beside every other mod's config, which is where a
 * pack author expects to find it.
 * </p>
 */
public final class HiveConfigFile {

    private static final String FILE_NAME = "avp_alien-hive.json";

    private static final String CONFIG_DIR = "config";

    /** Human-readable companion to the JSON. Never read back - see writeReference. */
    private static final String REFERENCE_FILE_NAME = "avp_alien-hive-README.txt";

    private HiveConfigFile() {}

    /**
     * Reads the file into the live config, writing a fully-populated default file first if none exists.
     * <p>
     * ⚠ Any failure leaves the DEFAULTS in place and logs — a malformed config must never stop a server booting.
     * </p>
     */
    public static void load(MinecraftServer server) {
        var path = path(server);

        if (!Files.exists(path)) {
            HiveLocationRegistry.INSTANCE.setConfig(clamp(HiveConfig.defaults()));
            save(server);
            Alien.LOGGER.info("Hive config: wrote a fresh {} with every value at its default", FILE_NAME);
            return;
        }

        try {
            var root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            var flat = flatten(root);
            var config = HiveConfig.defaults();
            var applied = 0;
            var unknown = 0;

            for (var entry : flat.entrySet()) {
                if (HiveConfigSchema.field(entry.getKey()) == null) {
                    // ⚠ NAMED, NOT SWALLOWED. A typo in a hand-edited file otherwise does nothing at all and looks
                    // like the setting being ignored, which is a miserable thing to debug from the outside.
                    Alien.LOGGER.warn("Hive config: unknown field '{}' ignored", entry.getKey());
                    unknown++;
                    continue;
                }
                try {
                    config = HiveConfigSchema.withParsedValue(config, entry.getKey(), entry.getValue());
                    applied++;
                } catch (RuntimeException exception) {
                    Alien.LOGGER.warn(
                        "Hive config: '{}' = '{}' could not be parsed; keeping the default",
                        entry.getKey(),
                        entry.getValue()
                    );
                }
            }

            HiveLocationRegistry.INSTANCE.setConfig(clamp(config));
            Alien.LOGGER.info(
                "Hive config: loaded {} ({} values applied, {} unknown). Fields absent from the file keep their defaults.",
                FILE_NAME,
                applied,
                unknown
            );
        } catch (Exception exception) {
            Alien.LOGGER.error("Hive config: {} could not be read; running on defaults", FILE_NAME, exception);
            HiveLocationRegistry.INSTANCE.setConfig(clamp(HiveConfig.defaults()));
        }
    }

    /**
     * Writes the live config back out, grouped and ordered by {@link HiveConfigSchema}.
     * <p>
     * ⭐ WRITTEN GROUPED, READ FLAT — see {@link #flatten}. The groups exist to make a 150-field file legible to a
     * human; they carry no meaning on the way back in.
     * </p>
     */
    public static void save(MinecraftServer server) {
        var root = new JsonObject();
        var config = HiveLocationRegistry.INSTANCE.config();

        for (var group : HiveConfigSchema.groups()) {
            var groupObject = new JsonObject();
            for (var field : group.fields()) {
                groupObject.add(field.name(), primitive(config, field));
            }
            root.add(group.name(), groupObject);
        }

        var path = path(server);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root));
            writeReference(path.getParent(), config);
        } catch (IOException exception) {
            Alien.LOGGER.error("Hive config: could not write {}", FILE_NAME, exception);
        }
    }

    /**
     * ⭐⭐ A PLAIN-TEXT REFERENCE BESIDE THE CONFIG, REWRITTEN EVERY SAVE.
     * <p>
     * [stated] "can we put a short message that says what each choice does so normal people know what to change it to."
     * JSON has no comments, so the explanations cannot live in the file being edited without becoming fake settings
     * that a typo turns into "unknown field" warnings. A companion file carries them instead: it is never read back, so
     * nothing in it can break a boot, and it is regenerated on every save so it can never describe a version of the
     * config that no longer exists.
     * </p>
     * <p>
     * Each entry shows the CURRENT value alongside the default, so someone reading it can see what has been changed
     * without diffing anything.
     * </p>
     */
    private static void writeReference(Path directory, HiveConfig config) {
        var text = new StringBuilder();
        text.append("AVP: ALIEN - HIVE CONFIG REFERENCE\n");
        text.append("==================================\n\n");
        text.append("Edit ").append(FILE_NAME).append(" beside this file, or use\n");
        text.append("  /avp hive config set <field> <value>\n");
        text.append("in game, which applies immediately and saves.\n\n");
        text.append("This file is regenerated whenever the config is saved. Editing it does nothing.\n");

        for (var group : HiveConfigSchema.groups()) {
            text.append("\n\n").append(group.name().toUpperCase(java.util.Locale.ROOT)).append("\n");
            text.append("-".repeat(Math.max(3, group.name().length()))).append("\n\n");

            for (var field : group.fields()) {
                var value = HiveConfigSchema.valueAsString(config, field.name());
                text.append("  ").append(field.name()).append(" = ").append(value);
                if (!value.equals(field.defaultValue())) {
                    text.append("   (default ").append(field.defaultValue()).append(")");
                }
                text.append("\n");

                var description = HiveConfigDescriptions.of(field.name());
                if (description != null) {
                    for (var line : wrap(description, 86)) {
                        text.append("      ").append(line).append("\n");
                    }
                }
                text.append("\n");
            }
        }

        try {
            Files.writeString(directory.resolve(REFERENCE_FILE_NAME), text.toString());
        } catch (IOException exception) {
            // ⚠ A missing reference is a documentation problem, not a gameplay one - never let it stop a save.
            Alien.LOGGER.warn("Hive config: could not write {}", REFERENCE_FILE_NAME, exception);
        }
    }

    /** Wraps a description so the reference reads like prose rather than one long line. */
    private static java.util.List<String> wrap(String text, int width) {
        var lines = new java.util.ArrayList<String>();
        var line = new StringBuilder();
        for (var word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    /**
     * Collapses the grouped file into name → value, ignoring the group structure entirely.
     * <p>
     * ⭐ THIS IS WHY A HAND-EDITED FILE SURVIVES AN UPDATE. Keys are matched on NAME ALONE, so moving a value between
     * groups, renaming a group, or a future version regrouping the fields all keep working. Anything absent simply
     * keeps its default, so ADDING fields in a later version never invalidates an existing file.
     * </p>
     */
    private static Map<String, String> flatten(JsonObject root) {
        var flat = new LinkedHashMap<String, String>();
        for (var entry : root.entrySet()) {
            if (entry.getValue().isJsonObject()) {
                for (var inner : entry.getValue().getAsJsonObject().entrySet()) {
                    flat.put(inner.getKey(), inner.getValue().getAsString());
                }
            } else {
                flat.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return flat;
    }

    /** Numbers and booleans stay typed in JSON rather than becoming quoted strings, so the file reads naturally. */
    private static JsonPrimitive primitive(HiveConfig config, HiveConfigSchema.Field field) {
        var raw = HiveConfigSchema.valueAsString(config, field.name());
        return switch (field.type()) {
            case BOOLEAN -> new JsonPrimitive(Boolean.parseBoolean(raw));
            case INT -> new JsonPrimitive(Integer.parseInt(raw));
            case LONG -> new JsonPrimitive(Long.parseLong(raw));
            case DOUBLE -> new JsonPrimitive(Double.parseDouble(raw));
        };
    }

    /**
     * ⭐⭐ THE ONE RULE THAT CANNOT BE LEFT TO THE EDITOR: a queen who founds where she is placed CANNOT HAVE DAUGHTERS.
     * <p>
     * A daughter cannot found inside her mother's claim (the spread-zone check refuses outright) and in build-free mode
     * she cannot dig her way somewhere else either — so she would walk until something killed her. [stated] "if they
     * select queen founds where placed/spawns it should turn off daughters."
     * </p>
     * <p>
     * ⚠ CLAMPED SILENTLY WITH A LOG, NOT REJECTED. A mapmaker who sets both should get the sane result and an
     * explanation, not a boot failure over a combination that is easy to reach by accident.
     * </p>
     */
    private static HiveConfig clamp(HiveConfig config) {
        config = clampFloor(
            config,
            "populationPerChunk",
            config.populationPerChunk(),
            1,
            "every population ratio in the mod divides by it, so 0 makes growth, decay and claiming meaningless"
        );
        config = clampFloor(
            config,
            "minimumHiveLocationDistanceChunks",
            config.minimumHiveLocationDistanceChunks(),
            1,
            "0 lets two hives found on the same chunk, which puts their claims in permanent contest"
        );
        config = clampFloor(
            config,
            "buildFreeSlabHalfHeight",
            config.buildFreeSlabHalfHeight(),
            1,
            "a zero-height slab is a hive with no interior - nothing spawns, nothing spreads"
        );
        config = clampCeiling(
            config,
            "buildFreeDaughterSlots",
            config.buildFreeDaughterSlots(),
            4,
            "the lifetime daughter cap"
        );
        config = clampCeiling(
            config,
            "buildFreeQueenEggClusterSize",
            config.buildFreeQueenEggClusterSize(),
            30,
            "eggs ringing the queen"
        );
        config = clampCeiling(
            config,
            "buildFreeEggClusterSize",
            config.buildFreeEggClusterSize(),
            6,
            "eggs per outlying cluster"
        );
        config = clampCeiling(
            config,
            "buildFreeEggClusterAmount",
            config.buildFreeEggClusterAmount(),
            8,
            "outlying egg clusters"
        );
        config = clampCeiling(
            config,
            "buildFreeJellyClusterAmount",
            config.buildFreeJellyClusterAmount(),
            6,
            "jelly vat clusters"
        );
        config = clampFloor(
            config,
            "buildFreeVentVerticalGap",
            config.buildFreeVentVerticalGap(),
            1,
            "a zero-height vent storey divides the band by zero"
        );
        config = clampCeiling(
            config,
            "buildFreeScourgeClusterAmount",
            config.buildFreeScourgeClusterAmount(),
            1,
            "scourge vat clusters"
        );

        if (
            config.buildFreeModeEnabled()
                && config.buildFreeQueenFoundsWherePlaced()
                && config.buildFreeDaughterSlots() > 0
        ) {
            Alien.LOGGER.info(
                "Hive config: buildFreeQueenFoundsWherePlaced is on, so buildFreeDaughterSlots ({}) is forced to 0 -"
                    + " a daughter could neither found inside her mother's claim nor dig elsewhere.",
                config.buildFreeDaughterSlots()
            );
            return HiveConfigSchema.withParsedValue(config, "buildFreeDaughterSlots", "0");
        }
        return config;
    }

    /**
     * \u2b50 CLAMP, DO NOT REJECT, AND ALWAYS SAY WHY.
     * <p>
     * A config file turns every constant in the mod into something a pack author can set to anything. Most values are
     * harmless to get wrong - a hive grows faster or slower - but a few break a mechanic outright, and the failure is
     * silent: the hive simply stops doing something and nobody knows which line caused it.
     * </p>
     * <p>
     * \u26a0 Refusing to boot over a bad number would be worse. The value is corrected, the reason is logged, and the
     * server starts.
     * </p>
     */
    private static HiveConfig clampFloor(HiveConfig config, String field, int actual, int floor, String why) {
        if (actual >= floor) {
            return config;
        }
        Alien.LOGGER.warn("Hive config: {} was {}, raised to {} - {}.", field, actual, floor, why);
        return HiveConfigSchema.withParsedValue(config, field, String.valueOf(floor));
    }

    /** As {@link #clampFloor}, for values with a stated maximum. */
    private static HiveConfig clampCeiling(HiveConfig config, String field, int actual, int ceiling, String why) {
        if (actual <= ceiling) {
            return config;
        }
        Alien.LOGGER.warn("Hive config: {} was {}, lowered to {} - the maximum for {}.", field, actual, ceiling, why);
        return HiveConfigSchema.withParsedValue(config, field, String.valueOf(ceiling));
    }

    private static Path path(MinecraftServer server) {
        return server.getServerDirectory().resolve(CONFIG_DIR).resolve(FILE_NAME);
    }
}
