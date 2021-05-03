package com.robotgryphon.compactmachines.util;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.text.TranslationTextComponent;

public abstract class TranslationUtil {

    public static String messageId(ResourceLocation message) {
        return Util.makeDescriptionId("message", message);
    }

    public static TranslationTextComponent message(ResourceLocation message) {
        return new TranslationTextComponent(messageId(message));
    }

    public static TranslationTextComponent message(ResourceLocation message, Object... params) {
        return new TranslationTextComponent(messageId(message), params);
    }

    public static String tooltipId(ResourceLocation tooltip) {
        return Util.makeDescriptionId("tooltip", tooltip);
    }

    public static TranslationTextComponent tooltip(ResourceLocation tooltip) {
        return new TranslationTextComponent(tooltipId(tooltip));
    }

    public static TranslationTextComponent tooltip(ResourceLocation tooltip, Object... params) {
        return new TranslationTextComponent(tooltipId(tooltip), params);
    }
}
