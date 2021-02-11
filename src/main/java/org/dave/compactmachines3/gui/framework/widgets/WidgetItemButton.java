package org.dave.compactmachines3.gui.framework.widgets;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class WidgetItemButton extends WidgetButton {
    private final ItemStack itemStack;
    private final boolean keepBackground;
    private final int xOffset;
    private final int yOffset;

    public WidgetItemButton(Item item, String unlocalizedLabel, boolean keepBackground) {
        this(new ItemStack(item), unlocalizedLabel, keepBackground);
    }

    public WidgetItemButton(Item item, String unlocalizedLabel, boolean keepBackground, int xOffset, int yOffset) {
        this(new ItemStack(item), unlocalizedLabel, keepBackground, xOffset, yOffset);
    }

    public WidgetItemButton(ItemStack itemStack, String unlocalizedLabel, boolean keepBackground) {
        this(itemStack, unlocalizedLabel, keepBackground, 0, 0);
    }

    public WidgetItemButton(ItemStack itemStack, String unlocalizedLabel, boolean keepBackground, int xOffset, int yOffset) {
        super("");
        this.setId("ItemButton[" + unlocalizedLabel + "]");
        this.setTooltipLines(I18n.format(unlocalizedLabel));

        this.itemStack = itemStack;
        this.keepBackground = keepBackground;
        this.xOffset = xOffset;
        this.yOffset = yOffset;

        this.setWidth(20);
        this.setHeight(20);
    }

    @Override
    public void draw(GuiScreen screen) {
        if (keepBackground) {
            super.draw(screen);
        }

        screen.mc.getRenderItem().renderItemAndEffectIntoGUI(itemStack, (width - 16) / 2 + xOffset, (height - 16) / 2 + yOffset);
    }
}
