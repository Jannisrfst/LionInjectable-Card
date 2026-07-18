package com.lionclient.gui.card;

import com.lionclient.config.ConfigManager;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;

/**
 * Profiles tab rendered as a compact 2-column card grid (mirrors the module-card grid).
 * Each profile card: name + subtitle, with a folder and a trash icon-button top-right.
 * Clicking the card body loads that profile; the last grid cell is a "+ New Profile" card.
 * Layout is computed once in {@link #layout} so draw and click can never desync.
 */
public final class ProfilesPanel {
    private static final int COLUMNS = 2;
    private static final int CARD_HEIGHT = 52;
    private static final int CARD_GAP = 8;
    private static final int PAD = 10;
    private static final int ICON_BUTTON = 16;
    private static final int ICON_GLYPH = 10;
    private static final float RADIUS = 10.0F;

    private ProfilesPanel() {
    }

    public static int contentHeight(ConfigManager cm, int width, FontRenderer fr) {
        int count = cm.listConfigs().size() + 1; // + "New Profile" card
        int rows = (count + COLUMNS - 1) / COLUMNS;
        return rows * (CARD_HEIGHT + CARD_GAP);
    }

    public static void draw(ConfigManager cm, int x, int y, int width, int mouseX, int mouseY, float alpha, FontRenderer fr) {
        Cell[] cells = layout(cm, x, y, width);
        String current = cm.getCurrentConfigName();

        for (Cell cell : cells) {
            if (cell.isNew) {
                drawNewCard(cell.card, mouseX, mouseY, alpha, fr);
                continue;
            }

            boolean active = cell.name.equals(current);
            boolean hovered = cell.card.contains(mouseX, mouseY);

            int fill = hovered ? CardTheme.CARD_HOVER : CardTheme.CARD;
            GuiGfx.roundedRect(cell.card.left, cell.card.top, cell.card.right, cell.card.bottom, RADIUS, GuiGfx.scaleAlpha(fill, alpha));
            int borderColor = active ? CardTheme.accent() : CardTheme.CARD_BORDER;
            GuiGfx.roundedOutline(cell.card.left, cell.card.top, cell.card.right, cell.card.bottom, RADIUS, active ? 1.5F : 1.0F, GuiGfx.scaleAlpha(borderColor, alpha));

            int nameMaxWidth = cell.folderButton.left - 6 - (cell.card.left + PAD);
            String name = fit(fr, cell.name, nameMaxWidth);
            fr.drawString(name, cell.card.left + PAD, cell.card.top + PAD, GuiGfx.scaleAlpha(CardTheme.TEXT, alpha));

            String subtitle = active ? "Active configuration" : "Saved profile";
            int subColor = active ? CardTheme.accent() : CardTheme.TEXT_DIM;
            fr.drawString(subtitle, cell.card.left + PAD, cell.card.top + PAD + fr.FONT_HEIGHT + 4, GuiGfx.scaleAlpha(subColor, alpha));

            drawIconButton(cell.folderButton, false, mouseX, mouseY, alpha);
            drawIconButton(cell.trashButton, true, mouseX, mouseY, alpha);
        }
    }

    public static boolean click(ConfigManager cm, int x, int y, int width, int mouseX, int mouseY, int button, FontRenderer fr) {
        if (button != 0) {
            return false;
        }

        Cell[] cells = layout(cm, x, y, width);
        for (Cell cell : cells) {
            if (cell.isNew) {
                if (cell.card.contains(mouseX, mouseY)) {
                    cm.createNextConfig();
                    return true;
                }
                continue;
            }

            if (cell.trashButton.contains(mouseX, mouseY)) {
                cm.load(cell.name);
                cm.deleteCurrentConfig();
                return true;
            }
            if (cell.folderButton.contains(mouseX, mouseY)) {
                cm.openFolder();
                return true;
            }
            if (cell.card.contains(mouseX, mouseY)) {
                cm.load(cell.name);
                return true;
            }
        }

        return false;
    }

    private static void drawNewCard(Bounds card, int mouseX, int mouseY, float alpha, FontRenderer fr) {
        boolean hovered = card.contains(mouseX, mouseY);
        int fill = hovered ? CardTheme.CARD_HOVER : CardTheme.CARD;
        GuiGfx.roundedRect(card.left, card.top, card.right, card.bottom, RADIUS, GuiGfx.scaleAlpha(fill, alpha));
        GuiGfx.roundedOutline(card.left, card.top, card.right, card.bottom, RADIUS, 1.0F, GuiGfx.scaleAlpha(CardTheme.accent(), alpha));

        String label = "+ New Profile";
        int textX = card.left + ((card.width() - fr.getStringWidth(label)) / 2);
        int textY = card.top + ((card.height() - fr.FONT_HEIGHT) / 2);
        fr.drawString(label, textX, textY, GuiGfx.scaleAlpha(hovered ? CardTheme.TEXT : CardTheme.accent(), alpha));
    }

    private static void drawIconButton(Bounds b, boolean danger, int mouseX, int mouseY, float alpha) {
        boolean hovered = b.contains(mouseX, mouseY);
        if (hovered) {
            GuiGfx.roundedRect(b.left, b.top, b.right, b.bottom, 3.0F, GuiGfx.scaleAlpha(CardTheme.BADGE_BG, alpha));
        }

        int color;
        if (danger) {
            color = CardTheme.DANGER;
        } else {
            color = hovered ? CardTheme.TEXT : CardTheme.TEXT_DIM;
        }
        int argb = GuiGfx.scaleAlpha(color, alpha);

        int cx = (b.left + b.right) / 2;
        int cy = (b.top + b.bottom) / 2;
        if (danger) {
            drawTrashIcon(cx, cy, ICON_GLYPH, argb);
        } else {
            drawFolderIcon(cx, cy, ICON_GLYPH, argb);
        }
    }

    private static void drawFolderIcon(int cx, int cy, int size, int argb) {
        float half = size / 2.0F;
        float l = cx - half;
        float r = cx + half;
        float t = cy - half;
        float b = cy + half;
        float bodyTop = t + (size * 0.28F);
        GuiGfx.roundedRect(l, t + (size * 0.12F), l + (size * 0.5F), bodyTop + 1.0F, 1.0F, argb); // Lasche
        GuiGfx.roundedRect(l, bodyTop, r, b, 1.5F, argb);                                          // Korpus
    }

    private static void drawTrashIcon(int cx, int cy, int size, int argb) {
        float half = size / 2.0F;
        float l = cx - half;
        float r = cx + half;
        float t = cy - half;
        float b = cy + half;
        float lidTop = t + (size * 0.14F);
        GuiGfx.roundedRect(cx - (size * 0.16F), t, cx + (size * 0.16F), lidTop, 0.5F, argb);        // Griff
        GuiGfx.roundedRect(l, lidTop, r, lidTop + (size * 0.16F), 0.5F, argb);                       // Deckel
        GuiGfx.roundedRect(l + (size * 0.12F), lidTop + (size * 0.22F), r - (size * 0.12F), b, 1.0F, argb); // Behälter
    }

    private static String fit(FontRenderer fr, String text, int maxWidth) {
        if (maxWidth <= 0 || fr.getStringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int budget = maxWidth - fr.getStringWidth(ellipsis);
        if (budget <= 0) {
            return ellipsis;
        }
        return fr.trimStringToWidth(text, budget) + ellipsis;
    }

    private static Cell[] layout(ConfigManager cm, int x, int y, int width) {
        List<String> configs = cm.listConfigs();
        int count = configs.size() + 1; // + "New Profile" card
        int cardWidth = (width - (CARD_GAP * (COLUMNS - 1))) / COLUMNS;

        Cell[] cells = new Cell[count];
        for (int i = 0; i < count; i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int cardX = x + (col * (cardWidth + CARD_GAP));
            int cardY = y + (row * (CARD_HEIGHT + CARD_GAP));
            Bounds card = new Bounds(cardX, cardY, cardX + cardWidth, cardY + CARD_HEIGHT);

            if (i >= configs.size()) {
                cells[i] = new Cell(card, null, true, null, null);
                continue;
            }

            int iconY = card.top + 6;
            Bounds trash = new Bounds(card.right - PAD - ICON_BUTTON, iconY, card.right - PAD, iconY + ICON_BUTTON);
            Bounds folder = new Bounds(trash.left - 4 - ICON_BUTTON, iconY, trash.left - 4, iconY + ICON_BUTTON);
            cells[i] = new Cell(card, configs.get(i), false, folder, trash);
        }
        return cells;
    }

    private static final class Cell {
        final Bounds card;
        final String name;
        final boolean isNew;
        final Bounds folderButton;
        final Bounds trashButton;

        Cell(Bounds card, String name, boolean isNew, Bounds folderButton, Bounds trashButton) {
            this.card = card;
            this.name = name;
            this.isNew = isNew;
            this.folderButton = folderButton;
            this.trashButton = trashButton;
        }
    }
}
