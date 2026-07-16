package com.lionclient.gui.card;

import com.lionclient.config.ConfigManager;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;

public final class ProfilesPanel {
    private static final int HEADER_HEIGHT = 28;
    private static final int HEADER_GAP = 10;
    private static final int ROW_HEIGHT = 30;
    private static final int ROW_GAP = 6;
    private static final int PAD = 12;
    private static final float RADIUS = 10.0F;

    private ProfilesPanel() {
    }

    public static int contentHeight(ConfigManager cm, int width, FontRenderer fr) {
        List<String> configs = cm.listConfigs();
        int rows = configs.size();
        int height = HEADER_HEIGHT + HEADER_GAP;
        if (rows > 0) {
            height += (rows * ROW_HEIGHT) + ((rows - 1) * ROW_GAP);
        }
        return height;
    }

    public static void draw(ConfigManager cm, int x, int y, int width, int mouseX, int mouseY, float alpha, FontRenderer fr) {
        Layout layout = layout(cm, x, y, width);

        drawButton(layout.newButton, "+ New Profile", false, mouseX, mouseY, alpha, fr);
        drawButton(layout.openFolderButton, "Open Folder", false, mouseX, mouseY, alpha, fr);

        String current = cm.getCurrentConfigName();
        for (int i = 0; i < layout.rows.length; i++) {
            Row row = layout.rows[i];
            boolean active = row.name.equals(current);
            boolean hovered = row.card.contains(mouseX, mouseY);

            int fill = hovered ? CardTheme.CARD_HOVER : CardTheme.CARD;
            GuiGfx.roundedRect(row.card.left, row.card.top, row.card.right, row.card.bottom, RADIUS, GuiGfx.scaleAlpha(fill, alpha));
            int borderColor = active ? CardTheme.accent() : CardTheme.CARD_BORDER;
            GuiGfx.roundedOutline(row.card.left, row.card.top, row.card.right, row.card.bottom, RADIUS, active ? 1.5F : 1.0F, GuiGfx.scaleAlpha(borderColor, alpha));

            int textColor = active ? CardTheme.TEXT : CardTheme.TEXT_DIM;
            int textY = row.card.top + ((row.card.height() - fr.FONT_HEIGHT) / 2);
            fr.drawString(row.name, row.card.left + PAD, textY, GuiGfx.scaleAlpha(textColor, alpha));

            if (active) {
                String activeLabel = "ACTIVE";
                fr.drawString(activeLabel, row.card.left + PAD + fr.getStringWidth(row.name) + 8, textY, GuiGfx.scaleAlpha(CardTheme.accent(), alpha));
            }

            drawButton(row.deleteButton, "Delete", true, mouseX, mouseY, alpha, fr);
            drawButton(row.loadButton, "Load", false, mouseX, mouseY, alpha, fr);
        }
    }

    public static boolean click(ConfigManager cm, int x, int y, int width, int mouseX, int mouseY, int button, FontRenderer fr) {
        if (button != 0) {
            return false;
        }

        Layout layout = layout(cm, x, y, width);

        if (layout.newButton.contains(mouseX, mouseY)) {
            cm.createNextConfig();
            return true;
        }
        if (layout.openFolderButton.contains(mouseX, mouseY)) {
            cm.openFolder();
            return true;
        }

        for (Row row : layout.rows) {
            if (row.loadButton.contains(mouseX, mouseY)) {
                cm.load(row.name);
                return true;
            }
            if (row.deleteButton.contains(mouseX, mouseY)) {
                cm.load(row.name);
                cm.deleteCurrentConfig();
                return true;
            }
        }

        return false;
    }

    private static void drawButton(Bounds b, String label, boolean danger, int mouseX, int mouseY, float alpha, FontRenderer fr) {
        boolean hovered = b.contains(mouseX, mouseY);
        int base = danger ? CardTheme.DANGER : CardTheme.BADGE_BG;
        int fill = hovered ? GuiGfx.mix(base, 0xFFFFFFFF, danger ? 0.15F : 0.08F) : base;
        int textColor = danger ? CardTheme.TEXT : CardTheme.TEXT;
        GuiGfx.badge(b, label, fill, textColor, alpha, fr);
    }

    private static Layout layout(ConfigManager cm, int x, int y, int width) {
        int right = x + width;

        int newButtonWidth = 100;
        int openFolderWidth = 110;
        int headerTop = y;
        int headerBottom = headerTop + HEADER_HEIGHT;
        Bounds newButton = new Bounds(x, headerTop, x + newButtonWidth, headerBottom);
        Bounds openFolderButton = new Bounds(right - openFolderWidth, headerTop, right, headerBottom);

        List<String> configs = cm.listConfigs();
        Row[] rows = new Row[configs.size()];
        int rowTop = headerBottom + HEADER_GAP;
        for (int i = 0; i < configs.size(); i++) {
            String name = configs.get(i);
            Bounds card = new Bounds(x, rowTop, right, rowTop + ROW_HEIGHT);

            int loadWidth = 60;
            int deleteWidth = 60;
            int actionTop = card.top + 4;
            int actionBottom = card.bottom - 4;
            Bounds loadButton = new Bounds(right - PAD - loadWidth, actionTop, right - PAD, actionBottom);
            Bounds deleteButton = new Bounds(loadButton.left - 6 - deleteWidth, actionTop, loadButton.left - 6, actionBottom);

            rows[i] = new Row(name, card, loadButton, deleteButton);
            rowTop += ROW_HEIGHT + ROW_GAP;
        }

        return new Layout(newButton, openFolderButton, rows);
    }

    private static final class Layout {
        final Bounds newButton;
        final Bounds openFolderButton;
        final Row[] rows;

        Layout(Bounds newButton, Bounds openFolderButton, Row[] rows) {
            this.newButton = newButton;
            this.openFolderButton = openFolderButton;
            this.rows = rows;
        }
    }

    private static final class Row {
        final String name;
        final Bounds card;
        final Bounds loadButton;
        final Bounds deleteButton;

        Row(String name, Bounds card, Bounds loadButton, Bounds deleteButton) {
            this.name = name;
            this.card = card;
            this.loadButton = loadButton;
            this.deleteButton = deleteButton;
        }
    }
}
