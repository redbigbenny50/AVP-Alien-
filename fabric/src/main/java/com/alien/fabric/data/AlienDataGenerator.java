package com.alien.fabric.data;

import com.alien.fabric.data.advancement.AdvancementProvider;
import com.alien.fabric.data.damage_type.DamageTypeBootstrapper;
import com.alien.fabric.data.damage_type.DamageTypeProvider;
import com.alien.fabric.data.dismemberment.AlienLimbDefinitionDataProvider;
import com.alien.fabric.data.dismemberment.AlienLimbVisualsDataProvider;
import com.alien.fabric.data.growth_stages.GrowthStageSubProvider;
import com.alien.fabric.data.infections.InfectionSubProvider;
import com.alien.fabric.data.jukebox_song.AlienJukeboxSongsProvider;
import com.alien.fabric.data.lang.en_us.EnglishLanguageProvider;
import com.alien.fabric.data.loot.BlockLootTableProvider;
import com.alien.fabric.data.loot.EntityLootTableProvider;
import com.alien.fabric.data.model.BlockModelProvider;
import com.alien.fabric.data.model.ItemModelProvider;
import com.alien.fabric.data.model.XenomorphHeadBlockStateProvider;
import com.alien.fabric.data.molting_profile.MoltingProfileSubProvider;
import com.alien.fabric.data.raid_wave.RaidWaveProfileDataProvider;
import com.alien.fabric.data.recipe.RecipeProvider;
import com.alien.fabric.data.reinforcement_profile.ReinforcementProfileDataProvider;
import com.alien.fabric.data.tag.AlienBiomeTagProvider;
import com.alien.fabric.data.tag.AlienBlockTagProvider;
import com.alien.fabric.data.tag.AlienDamageTypeTagProvider;
import com.alien.fabric.data.tag.AlienEntityTypeTagProvider;
import com.alien.fabric.data.tag.AlienItemTagProvider;
import com.alien.fabric.data.tag.AlienMobEffectTagProvider;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;

public class AlienDataGenerator implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        var pack = fabricDataGenerator.createPack();
        // Advancement providers
        pack.addProvider(AdvancementProvider::new);

        // Damage Type providers
        pack.addProvider(DamageTypeProvider::new);

        // Language providers
        pack.addProvider(EnglishLanguageProvider::new);

        // Model providers
        pack.addProvider(BlockModelProvider::new);
        pack.addProvider(ItemModelProvider::new);
        pack.addProvider(XenomorphHeadBlockStateProvider::new);

        // Recipe providers
        pack.addProvider(RecipeProvider::new);

        // Tag providers
        pack.addProvider(AlienBlockTagProvider::new);
        pack.addProvider(AlienBiomeTagProvider::new);
        pack.addProvider(AlienDamageTypeTagProvider::new);
        pack.addProvider(AlienEntityTypeTagProvider::new);
        pack.addProvider(AlienItemTagProvider::new);
        pack.addProvider(AlienMobEffectTagProvider::new);

        // Loot providers
        pack.addProvider(BlockLootTableProvider::new);
        pack.addProvider(EntityLootTableProvider::new);

        // Jukebox Song Providers
        pack.addProvider(AlienJukeboxSongsProvider::new);

        // Custom Providers
        // GENE BONUSES - DISABLED FOR DATAGEN, NOT FOR THE GAME.
        //
        // GeneBonusDataSubProvider references com.human.common.gameplay.gene.Genes, and avp_human is declared
        // modCompileOnly - on the COMPILE classpath but not the RUNTIME one. Datagen actually launches a JVM, so
        // the class is missing and the whole run dies with NoClassDefFoundError before any other provider gets to
        // write a single file.
        //
        // Turning this off costs nothing at runtime: datagen never DELETES, so the already-generated
        // data/avp_alien/gene_bonus_data/*.json still ships with the mod and gene bonuses still work in full for
        // anyone who installs avp_human. The only thing lost is the ability to REGENERATE that file.
        //
        // Re-enable this line (and put avp_human on the runtime classpath - modLocalRuntime instead of, or as well
        // as, modCompileOnly in fabric/build.gradle) whenever the gene bonus tables themselves need to change.
        // pack.addProvider(GeneBonusDataSubProvider::new);

        pack.addProvider(MoltingProfileSubProvider::new);
        pack.addProvider(GrowthStageSubProvider::new);
        pack.addProvider(com.alien.fabric.data.hive_unit_purchase.HiveUnitPurchaseDataProvider::new);
        pack.addProvider(RaidWaveProfileDataProvider::new);
        pack.addProvider(ReinforcementProfileDataProvider::new);
        pack.addProvider(InfectionSubProvider::new);
        pack.addProvider(AlienLimbDefinitionDataProvider::new);
        pack.addProvider(AlienLimbVisualsDataProvider::new);
    }

    @Override
    public void buildRegistry(RegistrySetBuilder registryBuilder) {
        registryBuilder.add(Registries.DAMAGE_TYPE, DamageTypeBootstrapper::bootstrap);
    }
}
