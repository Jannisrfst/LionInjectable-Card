package com.lionclient.gui.card;

import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.Setting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.input.Keyboard;

/**
 * Renders a single module "card": header (name + keybind badge + toggle switch),
 * a description row with a +/- expand button, and (when expanded) a settings column.
 *
 * ClickResult semantics: TOGGLED means this method already flipped module.toggle();
 * the caller does not need to (and must not) call it again. KEYBIND_START means
 * state.bindingModule was set to m; the caller's key handler / rebinding screen logic
 * reacts to that the same way ModernClickGuiScreen does. EXPAND_TOGGLED means the caller
 * owns per-module expand progress and must flip its own tracked state for m.
 */
public final class ModuleCardRenderer {
    private static final int PADDING = 12;
    private static final int HEADER_HEIGHT = 20;
    private static final int DESC_ROW_HEIGHT = 16;
    private static final int SECTION_GAP = 8;
    private static final int TOGGLE_WIDTH = 34;
    private static final int TOGGLE_HEIGHT = 18;
    private static final int BADGE_HEIGHT = 14;
    private static final int BADGE_PADDING = 8;
    private static final int BADGE_MIN_WIDTH = 40;
    private static final int EXPAND_BUTTON_SIZE = 12;
    private static final float CARD_RADIUS = 10.0F;
    private static final float NAME_SCALE = 1.05F;

    private ModuleCardRenderer() {
    }

    public enum ClickResult {
        TOGGLED,
        EXPAND_TOGGLED,
        SETTING_CONSUMED,
        KEYBIND_START,
        NONE
    }

    public static int heightOf(Module m, float expandProgress, int cardWidth, FontRenderer fr) {
        int collapsed = (PADDING * 2) + HEADER_HEIGHT + SECTION_GAP + DESC_ROW_HEIGHT;

        List<Setting> settings = visibleSettings(m);
        if (settings.isEmpty()) {
            return collapsed;
        }

        int contentWidth = cardWidth - (PADDING * 2);
        int settingsHeight = 0;
        for (int i = 0; i < settings.size(); i++) {
            settingsHeight += SettingRenderer.heightOf(settings.get(i), contentWidth, fr);
            if (i > 0) {
                settingsHeight += SECTION_GAP;
            }
        }
        settingsHeight += SECTION_GAP;

        float progress = GuiGfx.clamp(expandProgress, 0.0F, 1.0F);
        return Math.round(collapsed + (settingsHeight * progress));
    }

    public static void draw(Module m, int x, int y, int cardWidth, float expandProgress,
                             int mouseX, int mouseY, float alpha, FontRenderer fr, RenderState state) {
        float progress = GuiGfx.clamp(expandProgress, 0.0F, 1.0F);
        int height = heightOf(m, progress, cardWidth, fr);
        Bounds card = new Bounds(x, y, x + cardWidth, y + height);

        boolean hovered = card.contains(mouseX, mouseY);
        int cardColor = GuiGfx.scaleAlpha(hovered ? CardTheme.CARD_HOVER : CardTheme.CARD, alpha);
        GuiGfx.roundedRect(card.left, card.top, card.right, card.bottom, CARD_RADIUS, cardColor);
        GuiGfx.roundedOutline(card.left, card.top, card.right, card.bottom, CARD_RADIUS, 1.0F, GuiGfx.scaleAlpha(CardTheme.CARD_BORDER, alpha));

        int contentLeft = x + PADDING;
        int contentRight = x + cardWidth - PADDING;
        int contentWidth = contentRight - contentLeft;
        int headerTop = y + PADDING;

        drawHeader(m, contentLeft, contentRight, headerTop, mouseX, mouseY, alpha, fr, state);

        int descTop = headerTop + HEADER_HEIGHT + SECTION_GAP;
        drawExpandButton(m, contentLeft, descTop, progress, mouseX, mouseY, alpha, fr);
        int descTextX = contentLeft + EXPAND_BUTTON_SIZE + 6;
        String description = fitToWidth(fr, m.getDescription(), contentRight - descTextX);
        fr.drawString(description, descTextX, descTop + ((DESC_ROW_HEIGHT - fr.FONT_HEIGHT) / 2),
            GuiGfx.scaleAlpha(CardTheme.TEXT_DIM, alpha));

        if (progress <= 0.0F) {
            return;
        }

        List<Setting> settings = visibleSettings(m);
        if (settings.isEmpty()) {
            return;
        }

        int settingsTop = descTop + DESC_ROW_HEIGHT + SECTION_GAP;
        int settingsBottom = y + height - PADDING;
        Bounds clip = new Bounds(x, settingsTop, x + cardWidth, Math.max(settingsTop, settingsBottom));
        float settingsAlpha = alpha * GuiGfx.easeOut(progress);

        GuiGfx.beginScissor(clip, net.minecraft.client.Minecraft.getMinecraft(), CardTheme.scale());
        int rowY = settingsTop;
        for (Setting setting : settings) {
            int rowHeight = SettingRenderer.heightOf(setting, contentWidth, fr);
            SettingRenderer.draw(setting, contentLeft, rowY, contentWidth, mouseX, mouseY, settingsAlpha, fr, state);
            rowY += rowHeight + SECTION_GAP;
        }
        GuiGfx.endScissor();
    }

    public static ClickResult click(Module m, int x, int y, int cardWidth, float expandProgress,
                                     int mouseX, int mouseY, int button, FontRenderer fr, RenderState state) {
        float progress = GuiGfx.clamp(expandProgress, 0.0F, 1.0F);
        int height = heightOf(m, progress, cardWidth, fr);

        int contentLeft = x + PADDING;
        int contentRight = x + cardWidth - PADDING;
        int contentWidth = contentRight - contentLeft;
        int headerTop = y + PADDING;

        if (button == 0) {
            Bounds toggleBounds = headerToggleBounds(contentRight, headerTop);
            if (toggleBounds.contains(mouseX, mouseY)) {
                m.toggle();
                return ClickResult.TOGGLED;
            }

            Bounds badgeBounds = keybindBadgeBounds(m, contentRight, headerTop, fr);
            if (badgeBounds.contains(mouseX, mouseY)) {
                state.bindingModule = m;
                return ClickResult.KEYBIND_START;
            }

            int descTop = headerTop + HEADER_HEIGHT + SECTION_GAP;
            Bounds expandBounds = expandButtonBounds(contentLeft, descTop);
            if (expandBounds.contains(mouseX, mouseY)) {
                return ClickResult.EXPAND_TOGGLED;
            }
        }

        if (progress > 0.0F) {
            List<Setting> settings = visibleSettings(m);
            int descTop = headerTop + HEADER_HEIGHT + SECTION_GAP;
            int settingsTop = descTop + DESC_ROW_HEIGHT + SECTION_GAP;
            int settingsBottom = y + height - PADDING;
            if (mouseY >= settingsTop && mouseY <= settingsBottom) {
                int rowY = settingsTop;
                for (Setting setting : settings) {
                    int rowHeight = SettingRenderer.heightOf(setting, contentWidth, fr);
                    if (SettingRenderer.click(setting, contentLeft, rowY, contentWidth, mouseX, mouseY, button, fr, state)) {
                        return ClickResult.SETTING_CONSUMED;
                    }
                    rowY += rowHeight + SECTION_GAP;
                }
            }
        }

        return ClickResult.NONE;
    }

    private static void drawHeader(Module m, int contentLeft, int contentRight, int headerTop,
                                    int mouseX, int mouseY, float alpha, FontRenderer fr, RenderState state) {
        int textY = headerTop + ((HEADER_HEIGHT - Math.round(fr.FONT_HEIGHT * NAME_SCALE)) / 2);
        GuiGfx.textScaled(fr, m.getName(), contentLeft, textY, GuiGfx.scaleAlpha(CardTheme.TEXT, alpha), NAME_SCALE);

        Bounds toggleBounds = headerToggleBounds(contentRight, headerTop);
        float toggleProgress = m.isEnabled() ? 1.0F : 0.0F;
        GuiGfx.toggleSwitch(toggleBounds, toggleProgress, state.accent, alpha);

        Bounds badgeBounds = keybindBadgeBounds(m, contentRight, headerTop, fr);
        boolean bound = m.getKeyCode() != Keyboard.KEY_NONE;
        boolean binding = state.bindingModule == m;
        String badgeText = binding ? "..." : keybindText(m);
        int badgeFill = bound || binding
            ? GuiGfx.withAlpha(state.accent, 60)
            : CardTheme.BADGE_BG;
        int badgeTextColor = bound || binding ? CardTheme.TEXT : CardTheme.TEXT_DIM;
        GuiGfx.badge(badgeBounds, badgeText, badgeFill, badgeTextColor, alpha, fr);
    }

    private static void drawExpandButton(Module m, int contentLeft, int descTop, float progress,
                                          int mouseX, int mouseY, float alpha, FontRenderer fr) {
        Bounds buttonBounds = expandButtonBounds(contentLeft, descTop);
        boolean hovered = buttonBounds.contains(mouseX, mouseY);
        int fill = GuiGfx.scaleAlpha(hovered ? CardTheme.CARD_HOVER : CardTheme.BADGE_BG, alpha);
        GuiGfx.roundedRect(buttonBounds.left, buttonBounds.top, buttonBounds.right, buttonBounds.bottom, 3.0F, fill);
        GuiGfx.roundedOutline(buttonBounds.left, buttonBounds.top, buttonBounds.right, buttonBounds.bottom, 3.0F, 1.0F, GuiGfx.scaleAlpha(CardTheme.CARD_BORDER, alpha));

        // Draw the +/- as primitives so it is pixel-perfectly centred; the vanilla font glyphs
        // for "+" and "-" sit at different heights and never centre cleanly in a small box.
        int glyphColor = GuiGfx.scaleAlpha(CardTheme.TEXT, alpha);
        float cx = buttonBounds.left + (buttonBounds.width() / 2.0F);
        float cy = buttonBounds.top + (buttonBounds.height() / 2.0F);
        float arm = 3.0F;
        float half = 0.75F;
        GuiGfx.roundedRect(cx - arm, cy - half, cx + arm, cy + half, 0.0F, glyphColor);
        if (progress <= 0.5F) {
            GuiGfx.roundedRect(cx - half, cy - arm, cx + half, cy + arm, 0.0F, glyphColor);
        }
    }

    /** Truncates {@code text} with an ellipsis so it never exceeds {@code maxWidth} pixels. */
    private static String fitToWidth(FontRenderer fr, String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return text == null ? "" : text;
        }
        if (fr.getStringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int room = maxWidth - fr.getStringWidth(ellipsis);
        if (room <= 0) {
            return fr.trimStringToWidth(text, maxWidth);
        }
        return fr.trimStringToWidth(text, room) + ellipsis;
    }

    private static Bounds headerToggleBounds(int contentRight, int headerTop) {
        int top = headerTop + ((HEADER_HEIGHT - TOGGLE_HEIGHT) / 2);
        return new Bounds(contentRight - TOGGLE_WIDTH, top, contentRight, top + TOGGLE_HEIGHT);
    }

    private static Bounds keybindBadgeBounds(Module m, int contentRight, int headerTop, FontRenderer fr) {
        String text = keybindText(m);
        int textWidth = fr.getStringWidth(text);
        int badgeWidth = Math.max(BADGE_MIN_WIDTH, textWidth + (BADGE_PADDING * 2));
        int right = contentRight - TOGGLE_WIDTH - 8;
        int top = headerTop + ((HEADER_HEIGHT - BADGE_HEIGHT) / 2);
        return new Bounds(right - badgeWidth, top, right, top + BADGE_HEIGHT);
    }

    private static Bounds expandButtonBounds(int contentLeft, int descTop) {
        int top = descTop + ((DESC_ROW_HEIGHT - EXPAND_BUTTON_SIZE) / 2);
        return new Bounds(contentLeft, top, contentLeft + EXPAND_BUTTON_SIZE, top + EXPAND_BUTTON_SIZE);
    }

    private static String keybindText(Module m) {
        if (m.getKeyCode() == Keyboard.KEY_NONE) {
            return "NONE";
        }

        String name = Keyboard.getKeyName(m.getKeyCode());
        return name == null ? "UNKNOWN" : name.toUpperCase();
    }

    private static List<Setting> visibleSettings(Module m) {
        List<Setting> visible = new ArrayList<Setting>();
        for (Setting setting : m.getSettings()) {
            if (setting.isVisible()) {
                visible.add(setting);
            }
        }
        return visible;
    }
}
