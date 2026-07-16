package com.lionclient.gui.card;

import com.lionclient.feature.setting.ActionSetting;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.DecimalSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.IntRangeSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.feature.setting.Setting;
import net.minecraft.client.gui.FontRenderer;

public final class SettingRenderer {
    private static final int ROW_HEIGHT_SIMPLE = 22;
    private static final int ROW_HEIGHT_SLIDER = 40;
    private static final int LABEL_TOP_PAD = 2;
    private static final int TOGGLE_WIDTH = 34;
    private static final int TOGGLE_HEIGHT = 18;
    private static final int PILL_HEIGHT = 18;
    private static final int PILL_MIN_WIDTH = 70;
    private static final int PILL_PADDING = 10;
    private static final int SLIDER_TOP_OFFSET = 24;
    private static final int SLIDER_TRACK_HEIGHT = 4;
    private static final int SLIDER_HANDLE_RADIUS = 5;
    private static final int DROPDOWN_OPTION_HEIGHT = 18;
    private static final int DROPDOWN_MAX_VISIBLE = 6;
    private static final float SLIDER_HIT_PAD = 5.0F;

    private SettingRenderer() {
    }

    public static int heightOf(Setting s, int contentWidth, FontRenderer fr) {
        if (s instanceof NumberSetting || s instanceof DecimalSetting || s instanceof IntRangeSetting) {
            return ROW_HEIGHT_SLIDER;
        }
        return ROW_HEIGHT_SIMPLE;
    }

    public static void draw(Setting s, int x, int y, int width, int mouseX, int mouseY, float alpha, FontRenderer fr, RenderState state) {
        if (s instanceof BooleanSetting) {
            drawBoolean((BooleanSetting) s, x, y, width, alpha, fr, state);
        } else if (s instanceof EnumSetting) {
            drawEnum((EnumSetting<?>) s, x, y, width, mouseX, mouseY, alpha, fr, state);
        } else if (s instanceof IntRangeSetting) {
            drawIntRange((IntRangeSetting) s, x, y, width, mouseX, mouseY, alpha, fr, state);
        } else if (s instanceof NumberSetting || s instanceof DecimalSetting) {
            drawNumeric(s, x, y, width, mouseX, mouseY, alpha, fr, state);
        } else if (s instanceof ActionSetting) {
            drawAction((ActionSetting) s, x, y, width, mouseX, mouseY, alpha, fr, state);
        }
    }

    public static boolean click(Setting s, int x, int y, int width, int mouseX, int mouseY, int button, FontRenderer fr, RenderState state) {
        if (s instanceof BooleanSetting) {
            return clickBoolean((BooleanSetting) s, x, y, width, mouseX, mouseY, button, fr, state);
        }
        if (s instanceof EnumSetting) {
            return clickEnum((EnumSetting<?>) s, x, y, width, mouseX, mouseY, button, fr, state);
        }
        if (s instanceof IntRangeSetting) {
            return clickIntRange((IntRangeSetting) s, x, y, width, mouseX, mouseY, button, fr, state);
        }
        if (s instanceof NumberSetting || s instanceof DecimalSetting) {
            return clickNumeric(s, x, y, width, mouseX, mouseY, button, fr, state);
        }
        if (s instanceof ActionSetting) {
            return clickAction((ActionSetting) s, x, y, width, mouseX, mouseY, button, fr, state);
        }
        return false;
    }

    public static void drag(Setting s, int x, int y, int width, int mouseX, int mouseY, RenderState state) {
        if (state.draggingSetting != s) {
            return;
        }

        if (s instanceof IntRangeSetting) {
            Bounds track = sliderTrackBounds(x, y, width);
            applyRangeValue((IntRangeSetting) s, track, mouseX, state.draggingRangeHigh, false);
        } else if (s instanceof NumberSetting) {
            Bounds track = sliderTrackBounds(x, y, width);
            applyNumberValue((NumberSetting) s, track, mouseX, false);
        } else if (s instanceof DecimalSetting) {
            Bounds track = sliderTrackBounds(x, y, width);
            applyDecimalValue((DecimalSetting) s, track, mouseX, false);
        }
    }

    public static void release(Setting s, RenderState state) {
        if (state.draggingSetting != s) {
            return;
        }

        if (s instanceof IntRangeSetting) {
            ((IntRangeSetting) s).saveValue();
        } else if (s instanceof NumberSetting) {
            NumberSetting number = (NumberSetting) s;
            number.setValue(number.getValue(), true);
        } else if (s instanceof DecimalSetting) {
            DecimalSetting decimal = (DecimalSetting) s;
            decimal.setValue(decimal.getValue(), true);
        }

        state.draggingSetting = null;
        state.draggingRangeHigh = false;
    }

    // ---- BooleanSetting ----

    private static void drawBoolean(BooleanSetting s, int x, int y, int width, float alpha, FontRenderer fr, RenderState state) {
        int rowCenterY = y + (ROW_HEIGHT_SIMPLE / 2);
        fr.drawString(s.getName(), x, rowCenterY - (fr.FONT_HEIGHT / 2), GuiGfx.scaleAlpha(CardTheme.TEXT, alpha));

        Bounds track = toggleBounds(x, y, width);
        float progress = s.isEnabled() ? 1.0F : 0.0F;
        GuiGfx.toggleSwitch(track, progress, state.accent, alpha);
    }

    private static boolean clickBoolean(BooleanSetting s, int x, int y, int width, int mouseX, int mouseY, int button, FontRenderer fr, RenderState state) {
        if (button != 0) {
            return false;
        }

        Bounds row = new Bounds(x, y, x + width, y + ROW_HEIGHT_SIMPLE);
        if (!row.contains(mouseX, mouseY)) {
            return false;
        }

        s.toggle();
        return true;
    }

    private static Bounds toggleBounds(int x, int y, int width) {
        int top = y + ((ROW_HEIGHT_SIMPLE - TOGGLE_HEIGHT) / 2);
        int right = x + width;
        return new Bounds(right - TOGGLE_WIDTH, top, right, top + TOGGLE_HEIGHT);
    }

    // ---- EnumSetting ----

    private static void drawEnum(EnumSetting<?> s, int x, int y, int width, int mouseX, int mouseY, float alpha, FontRenderer fr, RenderState state) {
        int rowCenterY = y + (ROW_HEIGHT_SIMPLE / 2);
        fr.drawString(s.getName(), x, rowCenterY - (fr.FONT_HEIGHT / 2), GuiGfx.scaleAlpha(CardTheme.TEXT, alpha));

        Bounds pillBounds = enumPillBounds(s, x, y, width, fr);
        boolean open = state.openDropdown == s;
        boolean hovered = pillBounds.contains(mouseX, mouseY);
        int fill = open ? state.accent : hovered ? CardTheme.CARD_HOVER : CardTheme.BADGE_BG;
        GuiGfx.badge(pillBounds, s.getValueText(), fill, CardTheme.TEXT, alpha, fr);

        if (open) {
            drawDropdown(s, pillBounds, mouseX, mouseY, fr, state);
        }
    }

    private static boolean clickEnum(EnumSetting<?> s, int x, int y, int width, int mouseX, int mouseY, int button, FontRenderer fr, RenderState state) {
        if (button != 0) {
            return false;
        }

        if (state.openDropdown == s) {
            Bounds pillBounds = enumPillBounds(s, x, y, width, fr);
            Bounds dropdownBounds = dropdownBounds(pillBounds, s.getValues().length);
            Object[] values = s.getValues();
            int visible = Math.min(values.length, DROPDOWN_MAX_VISIBLE);
            for (int i = 0; i < visible; i++) {
                Bounds optionBounds = dropdownOptionBounds(dropdownBounds, i);
                if (optionBounds.contains(mouseX, mouseY)) {
                    s.setIndex(i);
                    state.openDropdown = null;
                    return true;
                }
            }

            if (dropdownBounds.contains(mouseX, mouseY)) {
                return true;
            }

            state.openDropdown = null;
            if (pillBounds.contains(mouseX, mouseY)) {
                return true;
            }
            return false;
        }

        Bounds pillBounds = enumPillBounds(s, x, y, width, fr);
        if (pillBounds.contains(mouseX, mouseY)) {
            state.openDropdown = s;
            return true;
        }

        return false;
    }

    private static void drawDropdown(EnumSetting<?> s, Bounds pillBounds, int mouseX, int mouseY, FontRenderer fr, RenderState state) {
        Object[] values = s.getValues();
        Bounds dropdownBounds = dropdownBounds(pillBounds, values.length);
        GuiGfx.roundedRect(dropdownBounds.left, dropdownBounds.top, dropdownBounds.right, dropdownBounds.bottom, 6.0F, CardTheme.CARD_HOVER);
        GuiGfx.roundedOutline(dropdownBounds.left, dropdownBounds.top, dropdownBounds.right, dropdownBounds.bottom, 6.0F, 1.0F, CardTheme.CARD_BORDER);

        int visible = Math.min(values.length, DROPDOWN_MAX_VISIBLE);
        for (int i = 0; i < visible; i++) {
            Bounds optionBounds = dropdownOptionBounds(dropdownBounds, i);
            boolean hovered = optionBounds.contains(mouseX, mouseY);
            boolean selected = values[i] == s.getValue();
            if (selected) {
                GuiGfx.roundedRect(optionBounds.left + 2, optionBounds.top + 1, optionBounds.right - 2, optionBounds.bottom - 1, 4.0F, GuiGfx.withAlpha(state.accent, 90));
            } else if (hovered) {
                GuiGfx.roundedRect(optionBounds.left + 2, optionBounds.top + 1, optionBounds.right - 2, optionBounds.bottom - 1, 4.0F, CardTheme.CARD_BORDER);
            }

            int textColor = selected ? state.accent : CardTheme.TEXT;
            String text = values[i].toString();
            fr.drawString(text, optionBounds.left + 8, optionBounds.top + ((optionBounds.height() - fr.FONT_HEIGHT) / 2), textColor);
        }
    }

    private static Bounds enumPillBounds(EnumSetting<?> s, int x, int y, int width, FontRenderer fr) {
        int textWidth = fr.getStringWidth(s.getValueText());
        int pillWidth = Math.max(PILL_MIN_WIDTH, textWidth + (PILL_PADDING * 2));
        int top = y + ((ROW_HEIGHT_SIMPLE - PILL_HEIGHT) / 2);
        int right = x + width;
        return new Bounds(right - pillWidth, top, right, top + PILL_HEIGHT);
    }

    private static Bounds dropdownBounds(Bounds pillBounds, int valueCount) {
        int visible = Math.min(valueCount, DROPDOWN_MAX_VISIBLE);
        int height = visible * DROPDOWN_OPTION_HEIGHT;
        return new Bounds(pillBounds.left, pillBounds.bottom + 2, pillBounds.right, pillBounds.bottom + 2 + height);
    }

    private static Bounds dropdownOptionBounds(Bounds dropdownBounds, int index) {
        int top = dropdownBounds.top + (index * DROPDOWN_OPTION_HEIGHT);
        return new Bounds(dropdownBounds.left, top, dropdownBounds.right, top + DROPDOWN_OPTION_HEIGHT);
    }

    // ---- NumberSetting / DecimalSetting ----

    private static void drawNumeric(Setting s, int x, int y, int width, int mouseX, int mouseY, float alpha, FontRenderer fr, RenderState state) {
        fr.drawString(s.getName(), x, y + LABEL_TOP_PAD, GuiGfx.scaleAlpha(CardTheme.TEXT, alpha));

        String valueText = s.getValueText();
        int valueWidth = fr.getStringWidth(valueText);
        fr.drawString(valueText, x + width - valueWidth, y + LABEL_TOP_PAD, GuiGfx.scaleAlpha(state.accent, alpha));

        Bounds track = sliderTrackBounds(x, y, width);
        float progress = numericProgress(s);
        drawSliderTrack(track, progress, state.accent, alpha);

        int handleX = track.left + Math.round(track.width() * progress);
        boolean hovered = state.draggingSetting == s || isNearHandle(mouseX, mouseY, handleX, track.top + (track.height() / 2));
        drawSliderHandle(handleX, track.top + (track.height() / 2), hovered, state.accent, alpha);
    }

    private static boolean clickNumeric(Setting s, int x, int y, int width, int mouseX, int mouseY, int button, FontRenderer fr, RenderState state) {
        if (button != 0) {
            return false;
        }

        Bounds track = sliderTrackBounds(x, y, width);
        Bounds hitArea = new Bounds(track.left, track.top - 8, track.right, track.bottom + 8);
        if (!hitArea.contains(mouseX, mouseY)) {
            return false;
        }

        state.draggingSetting = s;
        state.draggingRangeHigh = false;
        if (s instanceof NumberSetting) {
            applyNumberValue((NumberSetting) s, track, mouseX, false);
        } else if (s instanceof DecimalSetting) {
            applyDecimalValue((DecimalSetting) s, track, mouseX, false);
        }
        return true;
    }

    private static float numericProgress(Setting s) {
        if (s instanceof NumberSetting) {
            NumberSetting n = (NumberSetting) s;
            if (n.getMax() == n.getMin()) {
                return 0.0F;
            }
            return GuiGfx.clamp((n.getValue() - n.getMin()) / (float) (n.getMax() - n.getMin()), 0.0F, 1.0F);
        }
        if (s instanceof DecimalSetting) {
            DecimalSetting d = (DecimalSetting) s;
            if (d.getMax() == d.getMin()) {
                return 0.0F;
            }
            return GuiGfx.clamp((float) ((d.getValue() - d.getMin()) / (d.getMax() - d.getMin())), 0.0F, 1.0F);
        }
        return 0.0F;
    }

    private static void applyNumberValue(NumberSetting s, Bounds track, int mouseX, boolean save) {
        float progress = trackProgress(track, mouseX);
        int range = s.getMax() - s.getMin();
        int step = Math.max(1, s.getStep());
        int steps = Math.round((range * progress) / step);
        s.setValue(s.getMin() + (steps * step), save);
    }

    private static void applyDecimalValue(DecimalSetting s, Bounds track, int mouseX, boolean save) {
        float progress = trackProgress(track, mouseX);
        double range = s.getMax() - s.getMin();
        double stepped = Math.round((range * progress) / s.getStep()) * s.getStep();
        s.setValue(s.getMin() + stepped, save);
    }

    // ---- IntRangeSetting ----

    private static void drawIntRange(IntRangeSetting s, int x, int y, int width, int mouseX, int mouseY, float alpha, FontRenderer fr, RenderState state) {
        fr.drawString(s.getName(), x, y + LABEL_TOP_PAD, GuiGfx.scaleAlpha(CardTheme.TEXT, alpha));

        String valueText = s.getValueText();
        int valueWidth = fr.getStringWidth(valueText);
        fr.drawString(valueText, x + width - valueWidth, y + LABEL_TOP_PAD, GuiGfx.scaleAlpha(state.accent, alpha));

        Bounds track = sliderTrackBounds(x, y, width);
        int range = s.getMax() - s.getMin();
        float lowProgress = range == 0 ? 0.0F : GuiGfx.clamp((s.getLow() - s.getMin()) / (float) range, 0.0F, 1.0F);
        float highProgress = range == 0 ? 0.0F : GuiGfx.clamp((s.getHigh() - s.getMin()) / (float) range, 0.0F, 1.0F);

        int trackColor = GuiGfx.scaleAlpha(CardTheme.TRACK_OFF, alpha);
        GuiGfx.pill(track.left, track.top, track.right, track.bottom, trackColor);

        int lowX = track.left + Math.round(track.width() * lowProgress);
        int highX = track.left + Math.round(track.width() * highProgress);
        if (highX > lowX) {
            int fillColor = GuiGfx.scaleAlpha(state.accent, alpha);
            GuiGfx.pill(lowX, track.top, highX, track.bottom, fillColor);
        }

        int centerY = track.top + (track.height() / 2);
        boolean draggingThis = state.draggingSetting == s;
        boolean lowHover = !draggingThis && isNearHandle(mouseX, mouseY, lowX, centerY);
        boolean highHover = !draggingThis && isNearHandle(mouseX, mouseY, highX, centerY);
        boolean lowActive = draggingThis && !state.draggingRangeHigh;
        boolean highActive = draggingThis && state.draggingRangeHigh;
        drawSliderHandle(lowX, centerY, lowHover || lowActive, state.accent, alpha);
        drawSliderHandle(highX, centerY, highHover || highActive, state.accent, alpha);
    }

    private static boolean clickIntRange(IntRangeSetting s, int x, int y, int width, int mouseX, int mouseY, int button, FontRenderer fr, RenderState state) {
        if (button != 0) {
            return false;
        }

        Bounds track = sliderTrackBounds(x, y, width);
        Bounds hitArea = new Bounds(track.left, track.top - 8, track.right, track.bottom + 8);
        if (!hitArea.contains(mouseX, mouseY)) {
            return false;
        }

        boolean highHandle = chooseRangeHandle(s, track, mouseX);
        state.draggingSetting = s;
        state.draggingRangeHigh = highHandle;
        applyRangeValue(s, track, mouseX, highHandle, false);
        return true;
    }

    private static boolean chooseRangeHandle(IntRangeSetting s, Bounds track, int mouseX) {
        float progress = trackProgress(track, mouseX);
        float clickValue = s.getMin() + (progress * (s.getMax() - s.getMin()));
        return clickValue >= ((s.getLow() + s.getHigh()) / 2.0F);
    }

    private static void applyRangeValue(IntRangeSetting s, Bounds track, int mouseX, boolean highHandle, boolean save) {
        float progress = trackProgress(track, mouseX);
        int value = s.getMin() + Math.round(progress * (s.getMax() - s.getMin()));
        if (highHandle) {
            s.setHigh(value, save);
        } else {
            s.setLow(value, save);
        }
    }

    // ---- ActionSetting ----

    private static void drawAction(ActionSetting s, int x, int y, int width, int mouseX, int mouseY, float alpha, FontRenderer fr, RenderState state) {
        int rowCenterY = y + (ROW_HEIGHT_SIMPLE / 2);
        fr.drawString(s.getName(), x, rowCenterY - (fr.FONT_HEIGHT / 2), GuiGfx.scaleAlpha(CardTheme.TEXT, alpha));

        Bounds pillBounds = actionPillBounds(s, x, y, width, fr);
        boolean hovered = pillBounds.contains(mouseX, mouseY);
        int fill = hovered ? state.accent : CardTheme.BADGE_BG;
        GuiGfx.badge(pillBounds, s.getValueText(), fill, CardTheme.TEXT, alpha, fr);
    }

    private static boolean clickAction(ActionSetting s, int x, int y, int width, int mouseX, int mouseY, int button, FontRenderer fr, RenderState state) {
        if (button != 0) {
            return false;
        }

        Bounds pillBounds = actionPillBounds(s, x, y, width, fr);
        if (!pillBounds.contains(mouseX, mouseY)) {
            return false;
        }

        s.run();
        return true;
    }

    private static Bounds actionPillBounds(ActionSetting s, int x, int y, int width, FontRenderer fr) {
        int textWidth = fr.getStringWidth(s.getValueText());
        int pillWidth = Math.max(PILL_MIN_WIDTH, textWidth + (PILL_PADDING * 2));
        int top = y + ((ROW_HEIGHT_SIMPLE - PILL_HEIGHT) / 2);
        int right = x + width;
        return new Bounds(right - pillWidth, top, right, top + PILL_HEIGHT);
    }

    // ---- shared slider helpers ----

    private static Bounds sliderTrackBounds(int x, int y, int width) {
        int top = y + SLIDER_TOP_OFFSET;
        return new Bounds(x, top, x + width, top + SLIDER_TRACK_HEIGHT);
    }

    private static void drawSliderTrack(Bounds track, float progress, int accent, float alpha) {
        int trackColor = GuiGfx.scaleAlpha(CardTheme.TRACK_OFF, alpha);
        GuiGfx.pill(track.left, track.top, track.right, track.bottom, trackColor);

        int fillRight = track.left + Math.round(track.width() * progress);
        if (fillRight > track.left) {
            int fillColor = GuiGfx.scaleAlpha(accent, alpha);
            GuiGfx.pill(track.left, track.top, fillRight, track.bottom, fillColor);
        }
    }

    private static void drawSliderHandle(int centerX, int centerY, boolean hovered, int accent, float alpha) {
        float radius = hovered ? SLIDER_HANDLE_RADIUS + 1.0F : SLIDER_HANDLE_RADIUS;
        int fill = GuiGfx.scaleAlpha(0xFFFFFFFF, alpha);
        GuiGfx.roundedRect(centerX - radius, centerY - radius, centerX + radius, centerY + radius, radius, fill);
        int outline = GuiGfx.scaleAlpha(accent, alpha);
        GuiGfx.roundedOutline(centerX - radius, centerY - radius, centerX + radius, centerY + radius, radius, 1.5F, outline);
    }

    private static boolean isNearHandle(int mouseX, int mouseY, int handleX, int handleY) {
        float dx = mouseX - handleX;
        float dy = mouseY - handleY;
        float radius = SLIDER_HANDLE_RADIUS + SLIDER_HIT_PAD;
        return (dx * dx) + (dy * dy) <= radius * radius;
    }

    private static float trackProgress(Bounds track, int mouseX) {
        return GuiGfx.clamp((mouseX - track.left) / (float) track.width(), 0.0F, 1.0F);
    }
}
