package com.lionclient.feature.module.impl;

import com.lionclient.LionClient;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.ActionSetting;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.gui.card.GuiGfx;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.input.Keyboard;

public final class HudModule extends Module {
    private static final int DEFAULT_X = 4;
    private static final int DEFAULT_Y = 4;
    private static HudModule instance;

    private static final String LOGO_TEXT = "LionClient";
    private static final float LOGO_SCALE = 2.0F;

    /** Dark translucent fill used behind each line in the Clean style. */
    private static final int BOX_COLOR = 0xC81E1E22;

    private final EnumSetting<Mode> mode = new EnumSetting<Mode>("Mode", Mode.values(), Mode.CLEAN);
    private final BooleanSetting logo = new BooleanSetting("Logo", false);
    private final BooleanSetting lowercase = new BooleanSetting("Lowercase", true);

    // Clean-style layout
    private final NumberSetting paddingX = new NumberSetting("Padding X", 0, 20, 1, 5);
    private final NumberSetting paddingY = new NumberSetting("Padding Y", 0, 20, 1, 2);
    private final NumberSetting rounding = new NumberSetting("Rounding", 0, 12, 1, 4);

    // Clean-style colors
    private final NumberSetting textRed = new NumberSetting("Text red", 0, 255, 5, 232);
    private final NumberSetting textGreen = new NumberSetting("Text green", 0, 255, 5, 120);
    private final NumberSetting textBlue = new NumberSetting("Text blue", 0, 255, 5, 43);
    private final NumberSetting detailRed = new NumberSetting("Details red", 0, 255, 5, 170);
    private final NumberSetting detailGreen = new NumberSetting("Details green", 0, 255, 5, 170);
    private final NumberSetting detailBlue = new NumberSetting("Details blue", 0, 255, 5, 170);

    // Classic-style solid color
    private final NumberSetting red = new NumberSetting("Red", 0, 255, 5, 255);
    private final NumberSetting green = new NumberSetting("Green", 0, 255, 5, 255);
    private final NumberSetting blue = new NumberSetting("Blue", 0, 255, 5, 255);

    private final NumberSetting x = new NumberSetting("X", 0, 4000, 2, DEFAULT_X);
    private final NumberSetting y = new NumberSetting("Y", 0, 4000, 2, DEFAULT_Y);
    private final ActionSetting editor = new ActionSetting("Move HUD", new Runnable() {
        @Override
        public void run() {
            LionClient client = LionClient.getInstance();
            if (client != null) {
                client.openHudEditor();
            }
        }
    }, new ActionSetting.ValueProvider() {
        @Override
        public String get() {
            return "OPEN";
        }
    });

    public HudModule() {
        super("HUD", "Displays enabled modules on screen.", Category.RENDER, Keyboard.KEY_NONE);
        instance = this;

        BooleanSupplier isClean = new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return mode.getValue() == Mode.CLEAN;
            }
        };
        BooleanSupplier isClassic = new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return mode.getValue() == Mode.CLASSIC;
            }
        };

        paddingX.setVisibility(isClean);
        paddingY.setVisibility(isClean);
        rounding.setVisibility(isClean);
        textRed.setVisibility(isClean);
        textGreen.setVisibility(isClean);
        textBlue.setVisibility(isClean);
        detailRed.setVisibility(isClean);
        detailGreen.setVisibility(isClean);
        detailBlue.setVisibility(isClean);
        red.setVisibility(isClassic);
        green.setVisibility(isClassic);
        blue.setVisibility(isClassic);

        addSetting(mode);
        addSetting(logo);
        addSetting(lowercase);
        addSetting(paddingX);
        addSetting(paddingY);
        addSetting(rounding);
        addSetting(textRed);
        addSetting(textGreen);
        addSetting(textBlue);
        addSetting(detailRed);
        addSetting(detailGreen);
        addSetting(detailBlue);
        addSetting(red);
        addSetting(green);
        addSetting(blue);
        addSetting(x);
        addSetting(y);
        addSetting(editor);
    }

    @Override
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.gameSettings.showDebugInfo) {
            return;
        }

        renderModuleList(event.resolution, getEnabledEntries());
    }

    public void renderEditorPreview(ScaledResolution resolution) {
        List<Entry> previewLines = getEnabledEntries();
        if (previewLines.isEmpty()) {
            previewLines = getSampleEntries();
        }
        renderModuleList(resolution, previewLines);
    }

    public static HudModule getInstance() {
        return instance;
    }

    public int getAnchorX() {
        return x.getValue();
    }

    public int getAnchorY() {
        return y.getValue();
    }

    public void setPosition(int x, int y) {
        this.x.setValue(x);
        this.y.setValue(y);
    }

    public boolean isRightAligned(ScaledResolution resolution) {
        return x.getValue() >= resolution.getScaledWidth() / 2;
    }

    public int getPreviewWidth(Minecraft minecraft) {
        List<Entry> entries = getPreviewEntries();
        int width = 0;
        for (Entry entry : entries) {
            width = Math.max(width, entryWidth(minecraft.fontRendererObj, entry));
        }
        if (mode.getValue() == Mode.CLEAN) {
            width += paddingX.getValue() * 2;
        }
        if (logo.isEnabled()) {
            width = Math.max(width, (int) (minecraft.fontRendererObj.getStringWidth(LOGO_TEXT) * LOGO_SCALE));
        }
        return width;
    }

    public int getPreviewHeight(Minecraft minecraft) {
        int height = getPreviewEntries().size() * lineHeight(minecraft.fontRendererObj);
        if (logo.isEnabled()) {
            height += (int) (minecraft.fontRendererObj.FONT_HEIGHT * LOGO_SCALE) + 4;
        }
        return height;
    }

    private int lineHeight(FontRenderer fontRenderer) {
        if (mode.getValue() == Mode.CLEAN) {
            return fontRenderer.FONT_HEIGHT + paddingY.getValue() * 2;
        }
        return fontRenderer.FONT_HEIGHT + 2;
    }

    private void renderModuleList(ScaledResolution resolution, List<Entry> entries) {
        Minecraft minecraft = Minecraft.getMinecraft();
        FontRenderer fontRenderer = minecraft.fontRendererObj;
        int anchorX = Math.max(0, Math.min(x.getValue(), resolution.getScaledWidth()));
        int anchorY = Math.max(0, Math.min(y.getValue(), Math.max(0, resolution.getScaledHeight() - fontRenderer.FONT_HEIGHT)));
        boolean rightAligned = anchorX >= resolution.getScaledWidth() / 2;
        int lineY = anchorY;

        Mode current = mode.getValue();
        if (logo.isEnabled()) {
            lineY += drawLogo(fontRenderer, anchorX, lineY, rightAligned, current == Mode.CLASSIC ? getClassicColor() : getModernColor(0));
        }

        if (current == Mode.CLEAN) {
            renderClean(fontRenderer, entries, anchorX, lineY, rightAligned);
            return;
        }

        int color = current == Mode.CLASSIC ? getClassicColor() : 0;
        for (int i = 0; i < entries.size(); i++) {
            String line = entries.get(i).joined();
            int drawX = rightAligned ? anchorX - fontRenderer.getStringWidth(line) : anchorX;
            int lineColor = current == Mode.MODERN ? getModernColor(i) : color;
            fontRenderer.drawStringWithShadow(line, drawX, lineY, lineColor);
            lineY += fontRenderer.FONT_HEIGHT + 2;
        }
    }

    private void renderClean(FontRenderer fontRenderer, List<Entry> entries, int anchorX, int startY, boolean rightAligned) {
        int padX = paddingX.getValue();
        int padY = paddingY.getValue();
        int radius = rounding.getValue();
        int boxHeight = fontRenderer.FONT_HEIGHT + padY * 2;
        int textColor = getTextColor();
        int detailColor = getDetailColor();
        int spaceWidth = fontRenderer.getStringWidth(" ");

        int lineY = startY;
        for (Entry entry : entries) {
            int nameWidth = fontRenderer.getStringWidth(entry.name);
            int textWidth = entryWidth(fontRenderer, entry);

            int textLeft = rightAligned ? (anchorX - textWidth) : anchorX;
            float boxLeft = textLeft - padX;
            float boxRight = textLeft + textWidth + padX;

            GuiGfx.roundedRect(boxLeft, lineY, boxRight, lineY + boxHeight, radius, BOX_COLOR);

            int textY = lineY + padY;
            fontRenderer.drawString(entry.name, textLeft, textY, textColor);
            if (!entry.detail.isEmpty()) {
                fontRenderer.drawString(entry.detail, textLeft + nameWidth + spaceWidth, textY, detailColor);
            }

            lineY += boxHeight;
        }
    }

    private int entryWidth(FontRenderer fontRenderer, Entry entry) {
        int width = fontRenderer.getStringWidth(entry.name);
        if (!entry.detail.isEmpty()) {
            width += fontRenderer.getStringWidth(" ") + fontRenderer.getStringWidth(entry.detail);
        }
        return width;
    }

    private int drawLogo(FontRenderer fontRenderer, int anchorX, int lineY, boolean rightAligned, int color) {
        float scaledWidth = fontRenderer.getStringWidth(LOGO_TEXT) * LOGO_SCALE;
        float drawX = rightAligned ? anchorX - scaledWidth : anchorX;

        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(drawX, (float) lineY, 0.0F);
        net.minecraft.client.renderer.GlStateManager.scale(LOGO_SCALE, LOGO_SCALE, 1.0F);
        fontRenderer.drawStringWithShadow(LOGO_TEXT, 0.0F, 0.0F, color);
        net.minecraft.client.renderer.GlStateManager.popMatrix();

        return (int) (fontRenderer.FONT_HEIGHT * LOGO_SCALE) + 4;
    }

    private List<Entry> getEnabledEntries() {
        List<Entry> entries = new ArrayList<Entry>();
        LionClient client = LionClient.getInstance();
        if (client == null) {
            return entries;
        }

        for (Module module : client.getModuleManager().getModules()) {
            if (!module.isEnabled() || module == this || !module.isVisible()) {
                continue;
            }
            String hudInfo = module.getHudInfo();
            String detail = hudInfo == null ? "" : hudInfo;
            entries.add(new Entry(transform(module.getName()), transform(detail)));
        }

        sortByWidth(entries);
        return entries;
    }

    private List<Entry> getPreviewEntries() {
        List<Entry> entries = getEnabledEntries();
        if (entries.isEmpty()) {
            entries = getSampleEntries();
        }
        return entries;
    }

    private List<Entry> getSampleEntries() {
        List<Entry> entries = new ArrayList<Entry>();
        entries.add(new Entry(transform("KillAura"), transform("regular")));
        entries.add(new Entry(transform("AutoClicker"), transform("butterfly")));
        entries.add(new Entry(transform("Sprint"), ""));
        sortByWidth(entries);
        return entries;
    }

    private String transform(String text) {
        if (text == null) {
            return "";
        }
        return lowercase.isEnabled() ? text.toLowerCase(java.util.Locale.ROOT) : text;
    }

    private void sortByWidth(final List<Entry> entries) {
        final FontRenderer fontRenderer = Minecraft.getMinecraft().fontRendererObj;
        Collections.sort(entries, new Comparator<Entry>() {
            @Override
            public int compare(Entry left, Entry right) {
                return entryWidth(fontRenderer, right) - entryWidth(fontRenderer, left);
            }
        });
    }

    private int getClassicColor() {
        return 0xFF000000 | ((red.getValue() & 255) << 16) | ((green.getValue() & 255) << 8) | (blue.getValue() & 255);
    }

    private int getTextColor() {
        return 0xFF000000 | ((textRed.getValue() & 255) << 16) | ((textGreen.getValue() & 255) << 8) | (textBlue.getValue() & 255);
    }

    private int getDetailColor() {
        return 0xFF000000 | ((detailRed.getValue() & 255) << 16) | ((detailGreen.getValue() & 255) << 8) | (detailBlue.getValue() & 255);
    }

    private int getModernColor(int index) {
        double time = System.currentTimeMillis() / 320.0D;
        float wave = (float) ((Math.sin(time + (index * 0.45D)) + 1.0D) * 0.5D);
        return 0xFF000000 | ClickGuiModule.blendColor(ClickGuiModule.getLightAccentColor(), ClickGuiModule.getDarkAccentColor(), wave);
    }

    /** A single HUD line: module name plus an optional detail suffix, coloured independently. */
    private static final class Entry {
        private final String name;
        private final String detail;

        private Entry(String name, String detail) {
            this.name = name == null ? "" : name;
            this.detail = detail == null ? "" : detail;
        }

        private String joined() {
            return detail.isEmpty() ? name : name + " " + detail;
        }
    }

    private enum Mode {
        CLEAN("Clean"),
        MODERN("Modern"),
        CLASSIC("Classic");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
