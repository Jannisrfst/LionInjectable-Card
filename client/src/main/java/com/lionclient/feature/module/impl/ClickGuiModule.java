package com.lionclient.feature.module.impl;

import com.lionclient.LionClient;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import org.lwjgl.input.Keyboard;

public final class ClickGuiModule extends Module {
    private static final int DEFAULT_CLASSIC_ACCENT_COLOR = 0x305CA8;
    private static final int DEFAULT_MODERN_ACCENT_COLOR = 0x4A9EFF;
    private static final int DEFAULT_CARD_ACCENT_COLOR = 0xE95420;
    private static ClickGuiModule instance;

    private final EnumSetting<GuiStyle> style = new EnumSetting<GuiStyle>("Style", GuiStyle.values(), GuiStyle.CARD);
    private final NumberSetting red = new NumberSetting("Red", 0, 255, 5, 48);
    private final NumberSetting green = new NumberSetting("Green", 0, 255, 5, 92);
    private final NumberSetting blue = new NumberSetting("Blue", 0, 255, 5, 168);
    private final BooleanSetting snowflakes = new BooleanSetting("Snowflakes", true);
    private final NumberSetting modernRed = new NumberSetting("Modern Red", 0, 255, 1, 74);
    private final NumberSetting modernGreen = new NumberSetting("Modern Green", 0, 255, 1, 158);
    private final NumberSetting modernBlue = new NumberSetting("Modern Blue", 0, 255, 1, 255);
    private final NumberSetting cardRed = new NumberSetting("Card Red", 0, 255, 1, 233);
    private final NumberSetting cardGreen = new NumberSetting("Card Green", 0, 255, 1, 84);
    private final NumberSetting cardBlue = new NumberSetting("Card Blue", 0, 255, 1, 32);
    private final EnumSetting<CardScale> cardScale = new EnumSetting<CardScale>("Card Scale", CardScale.values(), CardScale.NORMAL);

    public ClickGuiModule() {
        super("ClickGUI", "Configure the ClickGUI", Category.CLIENT, Keyboard.KEY_RSHIFT);
        instance = this;
        addSetting(style);
        addSetting(snowflakes);
        addSetting(modernRed);
        addSetting(modernGreen);
        addSetting(modernBlue);
        addSetting(red);
        addSetting(green);
        addSetting(blue);
        addSetting(cardRed);
        addSetting(cardGreen);
        addSetting(cardBlue);
        addSetting(cardScale);

        java.util.function.BooleanSupplier classicVisibility = new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return style.getValue() == GuiStyle.CLASSIC;
            }
        };
        java.util.function.BooleanSupplier modernVisibility = new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return style.getValue() == GuiStyle.MODERN;
            }
        };
        java.util.function.BooleanSupplier cardVisibility = new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return style.getValue() == GuiStyle.CARD;
            }
        };

        snowflakes.setVisibility(modernVisibility);
        modernRed.setVisibility(modernVisibility);
        modernGreen.setVisibility(modernVisibility);
        modernBlue.setVisibility(modernVisibility);
        red.setVisibility(classicVisibility);
        green.setVisibility(classicVisibility);
        blue.setVisibility(classicVisibility);
        cardRed.setVisibility(cardVisibility);
        cardGreen.setVisibility(cardVisibility);
        cardBlue.setVisibility(cardVisibility);
        cardScale.setVisibility(cardVisibility);
    }

    @Override
    public void toggle() {
        LionClient client = LionClient.getInstance();
        if (client != null) {
            client.toggleClickGui();
        }
    }

    @Override
    public boolean canBeUnbound() {
        return false;
    }

    public static int getAccentColor() {
        if (instance == null) {
            return DEFAULT_CLASSIC_ACCENT_COLOR;
        }
        return toColor(instance.red, instance.green, instance.blue);
    }

    public static GuiStyle getGuiStyle() {
        return instance == null ? GuiStyle.CARD : instance.style.getValue();
    }

    public static int getCardAccentColor() {
        if (instance == null) {
            return DEFAULT_CARD_ACCENT_COLOR;
        }
        return toColor(instance.cardRed, instance.cardGreen, instance.cardBlue);
    }

    public static float getCardScale() {
        if (instance == null) {
            return 1.0F;
        }
        return instance.cardScale.getValue().scale;
    }

    public static ClickGuiModule getInstance() {
        return instance;
    }

    public static int getModernAccentColor() {
        if (instance == null) {
            return DEFAULT_MODERN_ACCENT_COLOR;
        }
        return toColor(instance.modernRed, instance.modernGreen, instance.modernBlue);
    }

    public static boolean areSnowflakesEnabled() {
        return instance == null || instance.snowflakes.isEnabled();
    }

    public static int getLightAccentColor() {
        return blendColor(getModernAccentColor(), 0xFFFFFF, 0.52F);
    }

    public static int getDarkAccentColor() {
        return blendColor(getModernAccentColor(), 0x08111B, 0.48F);
    }

    public static int blendColor(int start, int end, float progress) {
        float amount = Math.max(0.0F, Math.min(1.0F, progress));
        int startR = (start >>> 16) & 255;
        int startG = (start >>> 8) & 255;
        int startB = start & 255;
        int endR = (end >>> 16) & 255;
        int endG = (end >>> 8) & 255;
        int endB = end & 255;
        int red = Math.round(startR + ((endR - startR) * amount));
        int green = Math.round(startG + ((endG - startG) * amount));
        int blue = Math.round(startB + ((endB - startB) * amount));
        return (red << 16) | (green << 8) | blue;
    }

    private static int toColor(NumberSetting red, NumberSetting green, NumberSetting blue) {
        return ((red.getValue() & 255) << 16)
            | ((green.getValue() & 255) << 8)
            | (blue.getValue() & 255);
    }

    public enum GuiStyle {
        MODERN("Modern"),
        CLASSIC("Classic"),
        CARD("Card");

        private final String label;

        GuiStyle(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum CardScale {
        SMALL("Small (75%)", 0.75F),
        NORMAL("Normal (100%)", 1.0F),
        LARGE("Large (150%)", 1.5F),
        EXTRA_LARGE("Extra large (200%)", 2.0F);

        private final String label;
        private final float scale;

        CardScale(String label, float scale) {
            this.label = label;
            this.scale = scale;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
