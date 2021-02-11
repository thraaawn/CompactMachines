package org.dave.compactmachines3.gui.machine;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import org.dave.compactmachines3.gui.framework.WidgetGuiContainer;
import org.dave.compactmachines3.utility.DimensionBlockPos;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class GuiMachineData {
    public static int machineSize;
    public static int id;

    public static BlockPos roomPos;
    public static DimensionBlockPos machinePos;
    public static UUID owner;
    public static String ownerName;
    public static String customName;
    public static ArrayList<String> playerWhiteList;
    public static boolean locked;

    public static boolean requiresNewDisplayList = false;
    public static boolean canRender = false;

    public static boolean isOwner(EntityPlayer player) {
        return player.getUniqueID().equals(owner); // returns false on null owner
    }

    public static boolean isUsedCube() {
        return id != -1;
    }

    public static boolean isAllowedToEnter(EntityPlayer player) {
        return !locked
                || owner == null
                || player.getUniqueID().equals(owner)
                || playerWhiteList.contains(player.getName());
    }

    public static void updateGuiMachineData(int machineSize, int id, BlockPos roomPos, DimensionBlockPos machinePos, UUID owner,
                                            String ownerName, String customName, Set<String> playerWhiteList, boolean locked) {
        canRender = false;
        GuiMachineData.machineSize = machineSize;
        GuiMachineData.id = id;
        GuiMachineData.roomPos = roomPos;
        GuiMachineData.machinePos = machinePos;
        GuiMachineData.owner = owner;
        GuiMachineData.ownerName = ownerName;
        GuiMachineData.customName = customName;
        GuiMachineData.playerWhiteList = new ArrayList<>(playerWhiteList);
        GuiMachineData.locked = locked;

        requiresNewDisplayList = true;
        canRender = true;

        if(Minecraft.getMinecraft().currentScreen instanceof WidgetGuiContainer) {
            WidgetGuiContainer widgetGuiContainer = (WidgetGuiContainer)Minecraft.getMinecraft().currentScreen;
            if(widgetGuiContainer == null) {
                return;
            }

            widgetGuiContainer.fireDataUpdateEvent();
        }
    }
}
