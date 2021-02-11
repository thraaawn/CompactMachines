package org.dave.compactmachines3.gui.psd;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.dave.compactmachines3.reference.Resources;

import java.io.IOException;

public class GuiPSDScreen extends GuiScreen {
    protected static final ResourceLocation RESOURCE_BACKGROUND = Resources.Gui.PSD_SCREEN;

    protected static final int GUI_WIDTH = 256;
    protected static final int GUI_HEIGHT = 201;

    public int offsetY;
    public int offsetX;

    private final Pages pages;

    public GuiPSDScreen() {
        this.pages = new Pages();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        GlStateManager.color(1f, 1f, 1f, 1f);
        mc.renderEngine.bindTexture(RESOURCE_BACKGROUND);
        drawTexturedModalRect(width / 2 - GUI_WIDTH / 2, height - GUI_HEIGHT, 0, 0, GUI_WIDTH, GUI_HEIGHT);

        this.offsetY = (height - GUI_HEIGHT) + 15;
        this.offsetX = (width / 2 - GUI_WIDTH / 2) + 16;



        Page activePage = pages.getActivePage();
        if(activePage != null) {
            activePage.draw(this, this.fontRenderer, itemRender, mouseX, mouseY);
        } else {
            // Draw 404 not found
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        Page activePage = pages.getActivePage();
        if(activePage != null) {
            activePage.mouseClicked(this, mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
