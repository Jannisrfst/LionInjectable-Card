package com.lionclient.gui.card;

import com.lionclient.feature.module.Category;
import net.minecraft.client.gui.FontRenderer;

public final class CategoryTab {
    private static final int OUTER_PAD = 4;
    private static final int BAR_HEIGHT = 28;
    private static final float CORNER_RADIUS = 12.0F;
    private static final int PILL_PAD_X = 10;  // horizontales Padding der schmalen Tab-Pille um das Label
    private static final int PILL_INSET_Y = 3; // vertikaler Abstand der Pille zum Nav-Rand

    private CategoryTab() {
    }

    public enum Tab {
        COMBAT("Combat"),
        MOVE("Move"),
        VISUAL("Visual"),
        UTILITY("Utility"),
        PROFILES("Profiles"),
        UNLOAD("Unload");

        private final String label;

        Tab(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public boolean isModuleTab() {
            return this == COMBAT || this == MOVE || this == VISUAL || this == UTILITY;
        }

        public Category[] categories() {
            switch (this) {
                case COMBAT:
                    return new Category[] { Category.COMBAT };
                case MOVE:
                    return new Category[] { Category.MOVEMENT };
                case VISUAL:
                    return new Category[] { Category.RENDER };
                case UTILITY:
                    return new Category[] { Category.CLIENT, Category.PLAYER, Category.MISC };
                default:
                    return new Category[0];
            }
        }
    }

    public static int height() {
        return BAR_HEIGHT + (OUTER_PAD * 2);
    }

    public static void draw(int x, int y, int width, Tab selected, int mouseX, int mouseY, float alpha, FontRenderer fr) {
        int right = x + width;
        int bottom = y + height();

        // Leicht transparenter Nav-Hintergrund, damit die Leiste auf dem Blur schwebt statt als
        // solider schwarzer Kasten zu sitzen. Keine helle Outline mehr (Referenz hat keinen Rand).
        GuiGfx.roundedRect(x, y, right, bottom, CORNER_RADIUS, GuiGfx.scaleAlpha(CardTheme.NAV_BG, alpha));

        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            Bounds bounds = tabBounds(x, y, width, i, tabs.length);
            boolean isSelected = tab == selected;
            boolean hovered = bounds.contains(mouseX, mouseY);

            String label = tab.label();
            int textWidth = fr.getStringWidth(label);
            int textX = bounds.left + ((bounds.width() - textWidth) / 2);
            int textY = bounds.top + ((bounds.height() - fr.FONT_HEIGHT) / 2);

            // Schmale Pille, die nur das Label umschließt (statt den ganzen Slot zu füllen), wie in der Referenz.
            int pillLeft = textX - PILL_PAD_X;
            int pillRight = textX + textWidth + PILL_PAD_X;
            int pillTop = bounds.top + PILL_INSET_Y;
            int pillBottom = bounds.bottom - PILL_INSET_Y;
            if (isSelected) {
                GuiGfx.pill(pillLeft, pillTop, pillRight, pillBottom, GuiGfx.scaleAlpha(CardTheme.CARD_HOVER, alpha));
            } else if (hovered) {
                GuiGfx.pill(pillLeft, pillTop, pillRight, pillBottom, GuiGfx.scaleAlpha(CardTheme.CARD, alpha * 0.6F));
            }

            int textColor;
            if (tab == Tab.UNLOAD) {
                textColor = CardTheme.DANGER;
            } else if (isSelected) {
                textColor = CardTheme.TEXT;
            } else if (hovered) {
                textColor = GuiGfx.mix(CardTheme.TEXT_DIM, CardTheme.TEXT, 0.5F);
            } else {
                textColor = CardTheme.TEXT_DIM;
            }
            fr.drawString(label, textX, textY, GuiGfx.scaleAlpha(textColor, alpha));
        }
    }

    public static Tab clicked(int x, int y, int width, int mouseX, int mouseY, FontRenderer fr) {
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Bounds bounds = tabBounds(x, y, width, i, tabs.length);
            if (bounds.contains(mouseX, mouseY)) {
                return tabs[i];
            }
        }
        return null;
    }

    private static Bounds tabBounds(int x, int y, int width, int index, int count) {
        int innerLeft = x + OUTER_PAD;
        int innerRight = (x + width) - OUTER_PAD;
        int innerWidth = innerRight - innerLeft;
        int slot = innerWidth / count;

        int left = innerLeft + (slot * index);
        int right = (index == count - 1) ? innerRight : (left + slot);
        int top = y + OUTER_PAD;
        int bottom = top + BAR_HEIGHT;
        return new Bounds(left, top, right, bottom);
    }
}
