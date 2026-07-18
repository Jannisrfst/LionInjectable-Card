package com.lionclient.gui.card;

import com.lionclient.feature.module.impl.ClickGuiModule;

public final class CardTheme {
    public static final int BG = 0xFF0D0D0F;
    public static final int BG_BLUR_TINT = 0x66000000;
    public static final int CARD = 0xFF161618;
    public static final int CARD_HOVER = 0xFF1D1D20;
    public static final int CARD_BORDER = 0xFF262629;
    public static final int TRACK_OFF = 0xFF2A2A2E;
    public static final int THUMB_OFF = 0xFFCFCFD4;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_DIM = 0xFF8A8A90;
    public static final int BADGE_BG = 0xFF2A2A2E;
    public static final int DANGER = 0xFFE5484D;

    private CardTheme() {
    }

    public static int accent() {
        return 0xFF000000 | (ClickGuiModule.getCardAccentColor() & 0x00FFFFFF);
    }

    public static int accentDark() {
        return GuiGfx.mix(accent(), 0xFF000000, 0.35F);
    }

    public static float scale() {
        return ClickGuiModule.getCardScale();
    }
}
