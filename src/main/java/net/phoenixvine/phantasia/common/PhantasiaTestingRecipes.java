package net.phoenixvine.phantasia.common;

import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.phantasia.Phantasia;

import static com.gregtechceu.gtceu.common.data.GTMaterials.Air;

public class PhantasiaTestingRecipes {

    public static void init(RecipeOutput provider) {
        GTRecipeTypes.GAS_COLLECTOR_RECIPES.recipeBuilder("air_from_scene")
                .circuitMeta(7)
                .outputFluids(Air.getFluid(10000))
                .dimension(ResourceLocation.fromNamespaceAndPath(Phantasia.MOD_ID, "scene"))
                .duration(200)
                .EUt(16)
                .save(provider);
    }
}
