package com.robotgryphon.compactmachines.api.tunnels.recipe;

import com.robotgryphon.compactmachines.api.tunnels.TunnelDefinition;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;

public abstract class TunnelRecipeHelper {
    public static ResourceLocation getRecipeId(@Nonnull ResourceLocation tunnelType) {
        return new ResourceLocation(tunnelType.getNamespace(), "tunnels/" + tunnelType.getPath());
    }
}
