package com.robotgryphon.compactmachines.datagen;

import com.robotgryphon.compactmachines.config.EnableVanillaRecipesConfigCondition;
import com.robotgryphon.compactmachines.core.Registration;
import net.minecraft.data.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.tags.ITag;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.IItemProvider;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.crafting.ConditionalRecipe;

import java.util.Objects;
import java.util.function.Consumer;

public class RecipeGenerator extends RecipeProvider {
    public RecipeGenerator(DataGenerator generatorIn) {
        super(generatorIn);
    }

    @Override
    protected void buildShapelessRecipes(Consumer<IFinishedRecipe> consumer) {
        ShapedRecipeBuilder.shaped(Registration.ITEM_BREAKABLE_WALL.get(), 16)
                .pattern(" R ")
                .pattern(" I ")
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .define('I', Tags.Items.STORAGE_BLOCKS_IRON)
                .unlockedBy("picked_up_iron", has(Tags.Items.STORAGE_BLOCKS_IRON))
                .save(consumer);

        ShapedRecipeBuilder.shaped(Registration.PERSONAL_SHRINKING_DEVICE.get())
                .pattern(" P ")
                .pattern("EBE")
                .pattern(" I ")
                .define('P', Tags.Items.GLASS_PANES)
                .define('E', Items.ENDER_EYE)
                .define('B', Items.BOOK)
                .define('I', Tags.Items.INGOTS_IRON)
                .unlockedBy("picked_up_ender_eye", has(Items.ENDER_EYE))
                .save(consumer);

        addMachineRecipes(consumer);
    }

    private void addMachineRecipes(Consumer<IFinishedRecipe> consumer) {
        registerMachineRecipe(consumer, Registration.MACHINE_BLOCK_ITEM_TINY.get(), ItemTags.PLANKS);
        registerMachineRecipe(consumer, Registration.MACHINE_BLOCK_ITEM_SMALL.get(), Tags.Items.STORAGE_BLOCKS_IRON);
        registerMachineRecipe(consumer, Registration.MACHINE_BLOCK_ITEM_NORMAL.get(), Tags.Items.STORAGE_BLOCKS_GOLD);
        registerMachineRecipe(consumer, Registration.MACHINE_BLOCK_ITEM_GIANT.get(), Tags.Items.STORAGE_BLOCKS_DIAMOND);
        registerMachineRecipe(consumer, Registration.MACHINE_BLOCK_ITEM_LARGE.get(), Tags.Items.OBSIDIAN);
        registerMachineRecipe(consumer, Registration.MACHINE_BLOCK_ITEM_MAXIMUM.get(), Tags.Items.STORAGE_BLOCKS_EMERALD);
    }

    protected void registerMachineRecipe(Consumer<IFinishedRecipe> consumer, IItemProvider out, ITag<Item> center) {
        Item wall = Registration.ITEM_BREAKABLE_WALL.get();
        ShapedRecipeBuilder recipe = ShapedRecipeBuilder.shaped(out)
                .pattern("WWW");

        if (center != null)
            recipe.pattern("WCW");
        else
            recipe.pattern("W W");

        recipe.pattern("WWW").define('W', wall);
        if (center != null)
            recipe.define('C', center);

        recipe
                .unlockedBy("has_recipe", has(wall));

        ConditionalRecipe.builder()
                .addCondition(new EnableVanillaRecipesConfigCondition())
                .addRecipe(recipe::save)
                .build(consumer, Objects.requireNonNull(out.asItem().getRegistryName()));
    }
}
