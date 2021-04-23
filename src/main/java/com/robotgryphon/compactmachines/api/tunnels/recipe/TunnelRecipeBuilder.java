package com.robotgryphon.compactmachines.api.tunnels.recipe;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.robotgryphon.compactmachines.api.tunnels.Constants;
import com.robotgryphon.compactmachines.api.tunnels.TunnelDefinition;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.ICriterionInstance;
import net.minecraft.advancements.IRequirementsStrategy;
import net.minecraft.advancements.criterion.RecipeUnlockedTrigger;
import net.minecraft.data.IFinishedRecipe;
import net.minecraft.data.ShapelessRecipeBuilder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tags.ITag;
import net.minecraft.util.IItemProvider;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

public class TunnelRecipeBuilder {

    private final List<Ingredient> ingredients = Lists.newArrayList();
    private final Advancement.Builder advancement = Advancement.Builder.advancement();
    private String group;
    private int count;
    private ResourceLocation tunnelType;

    private TunnelRecipeBuilder(TunnelDefinition definition, int count) {
        this.tunnelType = definition.getRegistryName();
        this.count = count;
    }

    public static TunnelRecipeBuilder tunnel(TunnelDefinition definition) {
        return new TunnelRecipeBuilder(definition, 1);
    }

    public static TunnelRecipeBuilder tunnel(TunnelDefinition definition, int count) {
        return new TunnelRecipeBuilder(definition, count);
    }

    public TunnelRecipeBuilder requires(ITag<Item> itemTag) {
        return this.requires(Ingredient.of(itemTag));
    }

    public TunnelRecipeBuilder requires(IItemProvider item) {
        return this.requires(Ingredient.of(item));
    }

    public TunnelRecipeBuilder requires(Ingredient ingredient) {
        this.ingredients.add(ingredient);
        return this;
    }

    public TunnelRecipeBuilder unlockedBy(String criterionName, ICriterionInstance crit) {
        this.advancement.addCriterion(criterionName, crit);
        return this;
    }

    public TunnelRecipeBuilder group(String groupName) {
        this.group = groupName;
        return this;
    }

    public void save(Consumer<IFinishedRecipe> consumer) {
        this.ensureValid(tunnelType);
        this.advancement
                .parent(new ResourceLocation("recipes/root"))
                .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(TunnelRecipeHelper.getRecipeId(tunnelType)))
                .rewards(AdvancementRewards.Builder.recipe(tunnelType))
                .requirements(IRequirementsStrategy.OR);

        consumer.accept(new TunnelRecipeBuilder.Result(
                this.tunnelType,
                this.count,
                this.group == null ? "" : this.group, this.ingredients,
                this.advancement,
                new ResourceLocation(tunnelType.getNamespace(), "recipes/" + tunnelType.getPath())
        ));
    }

    private void ensureValid(ResourceLocation recipeId) {
        if (this.advancement.getCriteria().isEmpty()) {
            throw new IllegalStateException("No way of obtaining recipe " + recipeId);
        }
    }

    public static class Result implements IFinishedRecipe {
        private final ResourceLocation tunnelType;
        private final int count;
        private final String group;
        private final List<Ingredient> ingredients;
        private final Advancement.Builder advancement;
        private final ResourceLocation advancementId;

        public Result(ResourceLocation tunnelType, int count, String group, List<Ingredient> ingredients, Advancement.Builder adv, ResourceLocation advId) {
            this.tunnelType = tunnelType;
            this.count = count;
            this.group = group;
            this.ingredients = ingredients;
            this.advancement = adv;
            this.advancementId = advId;
        }

        @Override
        public void serializeRecipeData(JsonObject output) {
            if (!this.group.isEmpty()) {
                output.addProperty("group", this.group);
            }

            JsonArray jsonarray = new JsonArray();

            for(Ingredient ingredient : this.ingredients) {
                jsonarray.add(ingredient.toJson());
            }

            output.add("ingredients", jsonarray);

            JsonObject result = new JsonObject();
            result.addProperty("item", Constants.TUNNEL_ID.toString());
            if (this.count > 1) {
                result.addProperty("count", this.count);
            }

            JsonObject definition = new JsonObject();
            definition.addProperty("id", this.tunnelType.toString());
            JsonObject nbt = new JsonObject();
            nbt.add("definition", definition);

            result.add("nbt", nbt);

            output.add("result", result);
        }

        @Override
        public ResourceLocation getId() {
            return TunnelRecipeHelper.getRecipeId(tunnelType);
        }

        @Override
        public IRecipeSerializer<?> getType() {
            return IRecipeSerializer.SHAPELESS_RECIPE;
        }

        @Nullable
        @Override
        public JsonObject serializeAdvancement() {
            return this.advancement.serializeToJson();
        }

        @Nullable
        @Override
        public ResourceLocation getAdvancementId() {
            return this.advancementId;
        }
    }
}
