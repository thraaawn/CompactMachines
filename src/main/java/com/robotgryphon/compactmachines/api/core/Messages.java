package com.robotgryphon.compactmachines.api.core;

import net.minecraft.util.ResourceLocation;

public abstract class Messages {
    public static final ResourceLocation CANNOT_ENTER_MACHINE = new ResourceLocation(Constants.MOD_ID, "cannot_enter");
    public static final ResourceLocation NO_MACHINE_DATA = new ResourceLocation(Constants.MOD_ID, "no_machine_data");
    public static final ResourceLocation MACHINE_SPAWNPOINT_SET = new ResourceLocation(Constants.MOD_ID, "spawnpoint_set");
    public static final ResourceLocation TELEPORT_OUT_OF_BOUNDS = new ResourceLocation(Constants.MOD_ID, "teleport_oob");
}
