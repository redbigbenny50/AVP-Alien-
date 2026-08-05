# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

AVP (Alien) is a Minecraft mod for version 1.21.1 that adds alien-related content from the Alien, Predator, and AVP franchises. It is part of a modular system alongside AVP-Human and AVP-Predator modules. The mod supports both Fabric and NeoForge mod loaders.

## Build Commands

```bash
# Build for both loaders
./gradlew build

# Run Fabric client
./gradlew :fabric:runClient

# Run NeoForge client
./gradlew :neoforge:runClient

# Run data generation (generates assets, recipes, loot tables, etc.)
./gradlew runAllDatagen          # Both loaders
./gradlew :neoforge:runData      # NeoForge only
./gradlew :fabric:runDatagen     # Fabric only

# Format code with Spotless before committing
./gradlew spotlessApply
```

## Architecture

### Multi-Loader Structure

This is a multi-loader Minecraft mod using a shared `common` module pattern:

- **common/** - Shared code compiled against vanilla Minecraft using NeoForm. Contains all game logic, entities, blocks, items, and registries. Has no access to loader-specific APIs.
- **fabric/** - Fabric loader implementation. Includes data generation providers and loader-specific integrations.
- **neoforge/** - NeoForge loader implementation. Minimal loader-specific code, mostly just the mod entry point.
- **buildSrc/** - Gradle plugins (`multiloader-common.gradle`, `multiloader-loader.gradle`) that wire up the multi-loader build.

### Key Entry Points

- `common/src/main/java/com/alien/Alien.java` - Main mod class with initialization logic
- `fabric/src/main/java/com/alien/fabric/AlienFabric.java` - Fabric entry point
- `neoforge/src/main/java/com/alien/neoforge/AlienNeoForge.java` - NeoForge entry point

### Core Systems

**Entity Hierarchy** (`com.alien.common.gameplay.entity`):
- `Alien` - Base class for all alien entities
- `Xenomorph` - Base for adult xenomorphs (Drone, Warrior, Runner, Praetorian, Queen, etc.)
- `Parasite` - Base for parasitic aliens (Facehugger)
- Entity lifecycle: Ovomorph → Facehugger → Chestburster → Adolescent → Adult forms

**Hive System** (`com.alien.common.gameplay.hive`):
- `Hive` - Manages xenomorph hive state, membership, and AI tasks
- `HiveMembershipManager/Cache` - Tracks aliens belonging to hives
- `HiveTask` implementations for hive-level AI (balancing populations, merging hives, leader selection)
- Level data stored via `HiveLevelData`

**Registry Pattern** (`com.alien.common.registry.init`):
- Blocks, items, entities, sounds organized into init classes (e.g., `AlienBlocks`, `AlienEntityTypes`)
- Block variants by type: regular, chitin, resin, with nether/aberrant/irradiated variants
- Uses BLib's registry abstraction for cross-loader compatibility

**Data-Driven Systems**:
- Growth stages: `GrowthStageRegistry` with JSON data from `common/src/main/generated/data/avp_alien/growth_stages/`
- Infections: `InfectionRegistry` for entity infection configuration
- Reload listeners rebuild registry mappings when tags update

### Dependencies

- **BLib** - Core library providing cross-loader abstractions, events, registries
- **just-goap** - Goal-Oriented Action Planning for entity AI
- **just-codec/just-core** - Utility libraries

Optional integrations: AVP-Human, AVP-Predator, Gigeresque

### Data Generation

Data generators are in `fabric/src/main/java/com/alien/fabric/data/`. Generated assets go to `common/src/main/generated/` (automatically copied from fabric after datagen). Run datagen after modifying:
- Block/item models and blockstates
- Recipes
- Loot tables
- Tags
- Language files
- Growth stage/infection data

## Development Notes

- Requires Java 21
- IntelliJ IDEA recommended; Eclipse/VSCode not supported
- Run `./gradlew build` at least once before PRs to apply Spotless formatting
- Mod ID: `avp_alien`
- Package: `com.alien`
