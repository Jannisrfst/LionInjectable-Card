package com.lionclient.gui.card;

import com.lionclient.LionClient;
import com.lionclient.config.ConfigManager;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.module.ModuleManager;
import com.lionclient.feature.module.impl.ClickGuiModule;
import com.lionclient.feature.setting.DecimalSetting;
import com.lionclient.feature.setting.IntRangeSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.feature.setting.Setting;
import com.lionclient.gui.render.HudBlurRenderer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public final class CardClickGuiScreen extends GuiScreen {
    private static final int PREFERRED_CARD_WIDTH = 236;
    private static final int MAX_COLUMNS = 2;
    private static final int TOP_MARGIN = 40;
    private static final int BOTTOM_MARGIN = 24;
    private static final int NAV_CONTENT_GAP = 10;
    private static final int CARD_GAP = 8;
    private static final int SIDE_MARGIN = 32;

    private final ModuleManager moduleManager;
    private final Map<Module, Float> expandProgress = new HashMap<Module, Float>();
    private final Set<Module> expandedModules = new HashSet<Module>();
    private final RenderState state = new RenderState();

    private CategoryTab.Tab selectedTab = CategoryTab.Tab.COMBAT;
    private float scroll;
    private float scrollTarget;
    private long lastFrameTime;

    public CardClickGuiScreen(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        lastFrameTime = 0L;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (state.valueEditor != null) {
            state.valueEditor.updateCursorCounter();
        }

        float delta = getDeltaSeconds();
        for (Module module : moduleManager.getModules()) {
            float target = expandedModules.contains(module) ? 1.0F : 0.0F;
            Float current = expandProgress.get(module);
            float value = GuiGfx.animate(current == null ? 0.0F : current.floatValue(), target, delta * 10.0F);
            expandProgress.put(module, Float.valueOf(value));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        try {
            drawScreenImpl(mouseX, mouseY, partialTicks);
        } catch (Throwable ignored) {
        }
    }

    private void drawScreenImpl(int mouseX, int mouseY, float partialTicks) {
        float scale = CardTheme.scale();
        ScaledResolution resolution = new ScaledResolution(this.mc);
        int scaledWidth = Math.round(resolution.getScaledWidth_double() / scale);
        int scaledHeight = Math.round(resolution.getScaledHeight_double() / scale);
        int scaledMouseX = Math.round(mouseX / scale);
        int scaledMouseY = Math.round(mouseY / scale);

        boolean blurred = false;
        if (ClickGuiModule.isBackgroundBlurEnabled()) {
            List<float[]> regions = new ArrayList<float[]>(1);
            regions.add(new float[]{0.0F, 0.0F, this.width, this.height, 0.0F});
            blurred = HudBlurRenderer.render(regions, ClickGuiModule.getBackgroundBlurStrength());
        }
        if (blurred) {
            drawRect(0, 0, this.width, this.height, CardTheme.BG_BLUR_TINT); // dezente Abdunklung über dem Blur
        } else {
            drawRect(0, 0, this.width, this.height, CardTheme.BG);           // Fallback: opak schwarz wie bisher
        }

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0F);

        state.accent = CardTheme.accent();

        Layout layout = createLayout(scaledWidth, scaledHeight);
        CategoryTab.draw(layout.navX, layout.navY, layout.gridWidth, selectedTab, scaledMouseX, scaledMouseY, 1.0F, this.fontRendererObj);

        GuiGfx.beginScissor(layout.contentBounds, this.mc, scale);
        if (selectedTab == CategoryTab.Tab.PROFILES) {
            drawProfilesTab(layout, scaledMouseX, scaledMouseY);
        } else if (selectedTab.isModuleTab()) {
            drawModuleTab(layout, scaledMouseX, scaledMouseY);
        }
        GuiGfx.endScissor();

        GlStateManager.popMatrix();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawModuleTab(Layout layout, int mouseX, int mouseY) {
        List<Module> modules = collectModules(selectedTab);
        float maxScroll = Math.max(0.0F, totalContentHeight(modules, layout) - layout.contentBounds.height());
        scrollTarget = GuiGfx.clamp(scrollTarget, 0.0F, maxScroll);
        scroll = GuiGfx.animate(scroll, scrollTarget, 0.24F);

        int baseTop = layout.contentBounds.top - Math.round(scroll);
        List<PlacedCard> placed = layoutCards(modules, layout, baseTop);
        for (PlacedCard card : placed) {
            if (card.y + card.height >= layout.contentBounds.top && card.y <= layout.contentBounds.bottom) {
                ModuleCardRenderer.draw(card.module, card.x, card.y, card.width, progressOf(card.module), mouseX, mouseY, 1.0F, this.fontRendererObj, state);
            }
        }
    }

    private void drawProfilesTab(Layout layout, int mouseX, int mouseY) {
        ConfigManager configManager = moduleManager.getConfigManager();
        float maxScroll = Math.max(0.0F, ProfilesPanel.contentHeight(configManager, layout.gridWidth, this.fontRendererObj) - layout.contentBounds.height());
        scrollTarget = GuiGfx.clamp(scrollTarget, 0.0F, maxScroll);
        scroll = GuiGfx.animate(scroll, scrollTarget, 0.24F);

        int y = layout.contentBounds.top - Math.round(scroll);
        ProfilesPanel.draw(configManager, layout.contentBounds.left, y, layout.gridWidth, mouseX, mouseY, 1.0F, this.fontRendererObj);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        float scale = CardTheme.scale();
        int scaledWidth = Math.round(this.width / scale);
        int scaledHeight = Math.round(this.height / scale);
        int scaledMouseX = Math.round(mouseX / scale);
        int scaledMouseY = Math.round(mouseY / scale);
        Layout layout = createLayout(scaledWidth, scaledHeight);

        if (handleValueEditorMouseClick(layout, scaledMouseX, scaledMouseY, mouseButton)) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        CategoryTab.Tab clickedTab = CategoryTab.clicked(layout.navX, layout.navY, layout.gridWidth, scaledMouseX, scaledMouseY, this.fontRendererObj);
        if (clickedTab != null) {
            if (clickedTab == CategoryTab.Tab.UNLOAD) {
                performUnload();
            } else if (clickedTab != selectedTab) {
                selectedTab = clickedTab;
                scroll = 0.0F;
                scrollTarget = 0.0F;
            }
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        if (!layout.contentBounds.contains(scaledMouseX, scaledMouseY)) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        if (selectedTab == CategoryTab.Tab.PROFILES) {
            ProfilesPanel.click(moduleManager.getConfigManager(), layout.contentBounds.left, layout.contentBounds.top - Math.round(scroll), layout.gridWidth, scaledMouseX, scaledMouseY, mouseButton, this.fontRendererObj);
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        if (selectedTab.isModuleTab()) {
            handleModuleClick(layout, scaledMouseX, scaledMouseY, mouseButton);
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void handleModuleClick(Layout layout, int mouseX, int mouseY, int mouseButton) {
        List<Module> modules = collectModules(selectedTab);
        int baseTop = layout.contentBounds.top - Math.round(scroll);
        List<PlacedCard> placed = layoutCards(modules, layout, baseTop);
        for (PlacedCard card : placed) {
            if (mouseX >= card.x && mouseX <= card.x + card.width && mouseY >= card.y && mouseY <= card.y + card.height) {
                ModuleCardRenderer.ClickResult result = ModuleCardRenderer.click(card.module, card.x, card.y, card.width, progressOf(card.module), mouseX, mouseY, mouseButton, this.fontRendererObj, state);
                if (result == ModuleCardRenderer.ClickResult.EXPAND_TOGGLED) {
                    if (expandedModules.contains(card.module)) {
                        expandedModules.remove(card.module);
                    } else {
                        expandedModules.add(card.module);
                    }
                }
                return;
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (state.draggingSetting == null || !selectedTab.isModuleTab()) {
            return;
        }

        float scale = CardTheme.scale();
        int scaledWidth = Math.round(this.width / scale);
        int scaledHeight = Math.round(this.height / scale);
        int scaledMouseX = Math.round(mouseX / scale);
        int scaledMouseY = Math.round(mouseY / scale);
        Layout layout = createLayout(scaledWidth, scaledHeight);

        List<Module> modules = collectModules(selectedTab);
        int baseTop = layout.contentBounds.top - Math.round(scroll);
        List<PlacedCard> placed = layoutCards(modules, layout, baseTop);
        for (PlacedCard card : placed) {
            for (Setting setting : card.module.getSettings()) {
                if (setting == state.draggingSetting) {
                    SettingRenderer.drag(setting, card.x + 12, card.y, card.width - 24, scaledMouseX, scaledMouseY, state);
                    return;
                }
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state0) {
        super.mouseReleased(mouseX, mouseY, state0);
        if (state.draggingSetting != null) {
            SettingRenderer.release(state.draggingSetting, state);
            state.draggingSetting = null;
            state.draggingRangeHigh = false;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (state.bindingModule != null) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE) {
                if (state.bindingModule.canBeUnbound()) {
                    state.bindingModule.setKeyCode(Keyboard.KEY_NONE);
                }
            } else {
                state.bindingModule.setKeyCode(keyCode);
            }

            state.bindingModule = null;
            return;
        }

        if (state.editingSetting != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                clearValueEditor();
                return;
            }

            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                commitValueEditor();
                return;
            }
        }

        if (handleCloseKeybind(keyCode)) {
            return;
        }

        if (state.editingSetting != null && state.valueEditor != null) {
            state.valueEditor.textboxKeyTyped(typedChar, keyCode);
            state.valueEditor.setText(sanitizeValueText(state.valueEditor.getText(), state.editingSetting instanceof DecimalSetting));
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    private boolean handleCloseKeybind(int keyCode) {
        if (keyCode == Keyboard.KEY_NONE) {
            return false;
        }

        ClickGuiModule clickGuiModule = ClickGuiModule.getInstance();
        if (clickGuiModule == null || keyCode != clickGuiModule.getKeyCode()) {
            return false;
        }

        LionClient client = LionClient.getInstance();
        if (client == null) {
            return false;
        }

        client.toggleClickGui();
        return true;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }

        float scale = CardTheme.scale();
        int scaledWidth = Math.round(this.width / scale);
        int scaledHeight = Math.round(this.height / scale);
        int mouseX = Math.round((Mouse.getEventX() * this.width / this.mc.displayWidth) / scale);
        int mouseY = Math.round(((this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1) / scale));
        Layout layout = createLayout(scaledWidth, scaledHeight);

        if (!layout.contentBounds.contains(mouseX, mouseY)) {
            return;
        }

        float amount = wheel > 0 ? -34.0F : 34.0F;
        scrollTarget += amount;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void onGuiClosed() {
        commitValueEditor();
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
        state.bindingModule = null;
        state.editingSetting = null;
        state.valueEditor = null;
        state.draggingSetting = null;
        state.draggingRangeHigh = false;
        state.openDropdown = null;
    }

    private void performUnload() {
        for (Module module : moduleManager.getModules()) {
            if (module.isEnabled()) {
                module.setEnabled(false);
            }
        }
        this.mc.displayGuiScreen(null);
    }

    private List<Module> collectModules(CategoryTab.Tab tab) {
        List<Module> modules = new ArrayList<Module>();
        for (Category category : tab.categories()) {
            modules.addAll(moduleManager.getVisibleModules(category));
        }
        return modules;
    }

    private float progressOf(Module module) {
        Float value = expandProgress.get(module);
        return value == null ? 0.0F : value.floatValue();
    }

    /**
     * Places the module cards into a row-major grid (1 or 2 columns depending on available width).
     * Render, click and drag all consume this single list so their geometry can never desync.
     * Each row's height is the tallest card in the row (cards expand independently).
     */
    private List<PlacedCard> layoutCards(List<Module> modules, Layout layout, int baseTop) {
        List<PlacedCard> placed = new ArrayList<PlacedCard>(modules.size());
        int cols = layout.columns;
        int cardWidth = layout.cardWidth;
        int rowTop = baseTop;
        for (int i = 0; i < modules.size(); i += cols) {
            int rowHeight = 0;
            for (int c = 0; c < cols && (i + c) < modules.size(); c++) {
                Module module = modules.get(i + c);
                int h = ModuleCardRenderer.heightOf(module, progressOf(module), cardWidth, this.fontRendererObj);
                if (h > rowHeight) {
                    rowHeight = h;
                }
            }
            for (int c = 0; c < cols && (i + c) < modules.size(); c++) {
                Module module = modules.get(i + c);
                int h = ModuleCardRenderer.heightOf(module, progressOf(module), cardWidth, this.fontRendererObj);
                int cardX = layout.contentBounds.left + (c * (cardWidth + CARD_GAP));
                placed.add(new PlacedCard(module, cardX, rowTop, cardWidth, h));
            }
            rowTop += rowHeight + CARD_GAP;
        }
        return placed;
    }

    private float totalContentHeight(List<Module> modules, Layout layout) {
        int cols = layout.columns;
        int cardWidth = layout.cardWidth;
        float height = 0.0F;
        for (int i = 0; i < modules.size(); i += cols) {
            int rowHeight = 0;
            for (int c = 0; c < cols && (i + c) < modules.size(); c++) {
                Module module = modules.get(i + c);
                int h = ModuleCardRenderer.heightOf(module, progressOf(module), cardWidth, this.fontRendererObj);
                if (h > rowHeight) {
                    rowHeight = h;
                }
            }
            height += rowHeight + CARD_GAP;
        }
        return height;
    }

    private Layout createLayout(int scaledWidth, int scaledHeight) {
        int available = scaledWidth - SIDE_MARGIN;
        int columns = 1;
        int cardWidth = Math.max(120, Math.min(PREFERRED_CARD_WIDTH, available));
        // Use a second column only when there is comfortably room for two full-width cards + gap.
        if (MAX_COLUMNS >= 2 && available >= (PREFERRED_CARD_WIDTH * 2) + CARD_GAP) {
            columns = 2;
            cardWidth = PREFERRED_CARD_WIDTH;
        }
        int gridWidth = (cardWidth * columns) + (CARD_GAP * (columns - 1));
        int gridX = (scaledWidth - gridWidth) / 2;
        int navY = TOP_MARGIN;
        int contentTop = navY + CategoryTab.height() + NAV_CONTENT_GAP;
        int contentBottom = scaledHeight - BOTTOM_MARGIN;
        Bounds contentBounds = new Bounds(gridX, contentTop, gridX + gridWidth, Math.max(contentTop, contentBottom));
        return new Layout(gridX, navY, gridWidth, columns, cardWidth, contentBounds);
    }

    private boolean handleValueEditorMouseClick(Layout layout, int mouseX, int mouseY, int mouseButton) {
        if (state.editingSetting == null || state.valueEditor == null) {
            return false;
        }

        if (mouseButton == 0) {
            state.valueEditor.mouseClicked(mouseX, mouseY, mouseButton);
            return true;
        }

        commitValueEditor();
        return false;
    }

    private void clearValueEditor() {
        state.editingSetting = null;
        state.valueEditor = null;
    }

    private void commitValueEditor() {
        if (state.editingSetting == null || state.valueEditor == null) {
            return;
        }

        String text = state.valueEditor.getText().trim();
        if (!text.isEmpty() && !"-".equals(text) && !".".equals(text) && !"-.".equals(text)) {
            try {
                if (state.editingSetting instanceof NumberSetting) {
                    ((NumberSetting) state.editingSetting).setManualValue(Integer.parseInt(text), true);
                } else if (state.editingSetting instanceof DecimalSetting) {
                    ((DecimalSetting) state.editingSetting).setManualValue(Double.parseDouble(text), true);
                } else if (state.editingSetting instanceof IntRangeSetting) {
                    ((IntRangeSetting) state.editingSetting).saveValue();
                }
            } catch (NumberFormatException ignored) {
            }
        }

        clearValueEditor();
    }

    private String sanitizeValueText(String input, boolean allowDecimal) {
        StringBuilder builder = new StringBuilder();
        boolean hasDecimal = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isDigit(c)) {
                builder.append(c);
                continue;
            }

            if (c == '-' && builder.length() == 0) {
                builder.append(c);
                continue;
            }

            if (allowDecimal && c == '.' && !hasDecimal) {
                if (builder.length() == 0 || (builder.length() == 1 && builder.charAt(0) == '-')) {
                    builder.append('0');
                }
                builder.append('.');
                hasDecimal = true;
            }
        }
        return builder.toString();
    }

    private float getDeltaSeconds() {
        long now = System.currentTimeMillis();
        if (lastFrameTime == 0L) {
            lastFrameTime = now;
            return 0.016F;
        }

        float delta = (now - lastFrameTime) / 1000.0F;
        lastFrameTime = now;
        return GuiGfx.clamp(delta, 0.0F, 0.05F);
    }

    private static final class PlacedCard {
        private final Module module;
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private PlacedCard(Module module, int x, int y, int width, int height) {
            this.module = module;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private static final class Layout {
        private final int navX;
        private final int navY;
        private final int gridWidth;
        private final int columns;
        private final int cardWidth;
        private final Bounds contentBounds;

        private Layout(int navX, int navY, int gridWidth, int columns, int cardWidth, Bounds contentBounds) {
            this.navX = navX;
            this.navY = navY;
            this.gridWidth = gridWidth;
            this.columns = columns;
            this.cardWidth = cardWidth;
            this.contentBounds = contentBounds;
        }
    }
}
