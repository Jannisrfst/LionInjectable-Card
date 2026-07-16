# LionClient — Card ClickGUI Redesign Plan

**Goal:** Add a new, modern **card / accordion** ClickGUI matching the reference mockups:
a rounded pill **top nav** (`Combat · Move · Visual · Utility · Profiles · Unload`), a single
vertical **scrolling column of module cards**, each with a title + keybind badge + iOS toggle and a
`+`/`−` button that expands the card inline to reveal a description + settings (section labels,
pill value selectors, sliders, toggles). Orange accent, true rounded corners.

This is delivered as a **new GuiStyle (`CARD`)**, made the default, leaving the existing
Classic and Modern screens untouched as fallbacks.

---

## Architecture (all new files under `client/src/main/java/com/lionclient/gui/card/`)

| # | File | Owner (subagent) | Depends on |
|---|------|------------------|-----------|
| 1 | `GuiGfx.java` + `CardTheme.java` | **Agent A – Foundation** | none |
| 2 | `CardClickGuiScreen.java` | **Agent B – Screen/Nav** | A (contract), C, D, E |
| 3 | `ModuleCardRenderer.java` | **Agent C – Module cards** | A, D |
| 4 | `SettingRenderer.java` | **Agent D – Setting widgets** | A |
| 5 | `ProfilesPanel.java` + `CategoryTab.java` + wiring edits | **Agent E – Profiles/Unload/wiring** | A |

Subagents code **against the contracts defined below**, not against each other's implementations.
The lead integrates and reconciles after all return. Everything is immediate-mode rendering
(`drawScreen` each frame) — the established pattern in `ModernClickGuiScreen`.

---

## Shared contracts (FROZEN — every agent codes to these signatures)

### `Bounds` (reuse the pattern from ModernClickGuiScreen; put a shared public one in GuiGfx)
```java
public static final class Bounds {
    public final int left, top, right, bottom;
    public Bounds(int left, int top, int right, int bottom);
    public boolean contains(int x, int y);
    public int width();  // right-left
    public int height(); // bottom-top
}
```

### `GuiGfx` (Agent A) — static rendering toolkit, real rounded corners via GL11 triangle fans
```java
public final class GuiGfx {
    static void roundedRect(float l,float t,float r,float b,float radius,int argb);
    static void roundedOutline(float l,float t,float r,float b,float radius,float thickness,int argb);
    static void roundedRectGradient(float l,float t,float r,float b,float radius,int argbTop,int argbBottom);
    static void pill(float l,float t,float r,float b,int argb);                 // radius = height/2
    static void shadow(float l,float t,float r,float b,float radius,int argb);  // soft drop shadow
    // iOS switch: track + animated thumb. progress 0..1 (off..on). Returns nothing.
    static void toggleSwitch(Bounds track,float progress,int accent,float alpha);
    // rounded "chip"/badge with centered text (keybind badges, pill selectors)
    static void badge(Bounds b,String text,int fill,int textColor,float alpha,FontRenderer fr);
    static void beginScissor(Bounds b, Minecraft mc);
    static void endScissor();
    static void textScaled(FontRenderer fr,String s,float x,float y,int color,float scale);
    static int  withAlpha(int rgb,int a);
    static int  scaleAlpha(int argb,float s);
    static int  mix(int a,int b,float t);
    static float clamp(float v,float min,float max);
    static float easeOut(float v);
    static float animate(float cur,float target,float speed);
}
```
Rounded corners: draw center rect + 4 edge rects + 4 quarter-circle triangle fans
(`GL11.glBegin(GL_TRIANGLE_FAN)`), with `GlStateManager.enableBlend()` /
`blendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)`, color via `GlStateManager.color(r,g,b,a)`, texture
disabled, then re-enable texture + disable blend. Provide `~8` segments per corner.

### `CardTheme` (Agent A) — palette + accent + scale, all read live from ClickGuiModule
```java
public final class CardTheme {
    static int accent();          // ClickGuiModule.getCardAccentColor()  (orange default 0xE8722A)
    static int accentDark();      // darker accent for gradients
    static float scale();         // from Scale enum: 0.75 / 1.0 / 1.5 / 2.0
    // color constants:  BG(0xFF0D0D0F) CARD(0xFF161618) CARD_HOVER CARD_BORDER(0xFF262629)
    //                   TRACK_OFF(0xFF2A2A2E) THUMB_OFF TEXT(0xFFFFFFFF) TEXT_DIM(0xFF8A8A90)
    //                   BADGE_BG(0xFF2A2A2E) DANGER(0xFFE5484D)
}
```

### `SettingRenderer` (Agent D) — renders ONE setting row inside an expanded card
```java
public final class SettingRenderer {
    // returns the pixel height this setting occupies (for layout)
    static int heightOf(Setting s, int contentWidth, FontRenderer fr);
    // draw the row; x/y = top-left of the content column, width = column width
    static void draw(Setting s, int x, int y, int width, int mouseX, int mouseY,
                     float alpha, FontRenderer fr, RenderState state);
    // hit-test + mutate on click. returns true if consumed. dropdowns handled via RenderState.
    static boolean click(Setting s, int x, int y, int width, int mouseX, int mouseY,
                         int button, FontRenderer fr, RenderState state);
    // drag (sliders) + release + scroll wheel pass-through as needed
    static void drag(Setting s, int x,int y,int width,int mouseX,int mouseY, RenderState state);
    static void release(Setting s, RenderState state);
    // number text-field editing (reuse GuiTextField approach from ModernClickGuiScreen)
}
```
`RenderState` is a small mutable context object (owned by the screen, passed down) holding:
open enum-dropdown setting, dragging setting, value editor (`GuiTextField`), binding module,
and the accent int. Defined by Agent B, consumed by C/D. **Agent B publishes `RenderState`
skeleton first line of the plan handoff.**

Setting styles to implement (Agent D):
- **BooleanSetting** → label left, iOS `toggleSwitch` right.
- **EnumSetting** → label left, pill selector right showing current value; click opens a rounded
  dropdown list (or left-click cycles forward / right-click cycles back — implement **dropdown**
  to match `Jitter` / `Extra large (200%)` mockups). Section label above when relevant.
- **NumberSetting / DecimalSetting** → label + value chip (editable) + slider track w/ accent fill.
- **IntRangeSetting** → label + dual-handle slider + `low–high` value chip.
- **ActionSetting** → label + right-aligned pill button showing the action verb.
- Section labels (`In-game`, `Main`) → small dim uppercase text, drawn by ModuleCardRenderer
  between groups (grouping = optional; see below).

### `ModuleCardRenderer` (Agent C) — one collapsible module card
```java
public final class ModuleCardRenderer {
    // total height incl. expansion animation (0..1 expandProgress)
    static int heightOf(Module m, float expandProgress, int cardWidth, FontRenderer fr);
    static void draw(Module m, int x, int y, int cardWidth, float expandProgress,
                     int mouseX, int mouseY, float alpha, FontRenderer fr, RenderState state);
    // returns an enum: TOGGLED / EXPAND_TOGGLED / SETTING_CONSUMED / KEYBIND_START / NONE
    static ClickResult click(Module m, int x, int y, int cardWidth, float expandProgress,
                             int mouseX, int mouseY, int button, FontRenderer fr, RenderState state);
}
```
Card header: `name` (bold) + keybind **badge** (`getKeybindText`, orange-tinted if bound, dim if
`NONE`) + iOS toggle far right. Below (only when expanding): `+`/`−` square button + description,
then the settings column via `SettingRenderer`. Rounded card bg + subtle border; hover lightens.
Clicking the header toggle → toggle module. Clicking the `+`/`−` → expand/collapse. Clicking the
badge → start keybinding (uses RenderState.bindingModule). Expansion is animated per-card
(`Map<Module,Float>` in the screen).

### `CardClickGuiScreen extends GuiScreen` (Agent B) — the host
- Fields: `moduleManager`, selected `CategoryTab`, per-module expand animation map, scroll +
  scrollTarget, `openProgress`, `RenderState`, window drag (optional; the mockup nav is full-width
  so a centered fixed panel is fine — **use a centered fixed panel ~460px wide**).
- `drawScreen`: dim background → window bg (rounded) → **top pill nav** → scrolling card column
  (scissor-clipped) → Profiles panel when Profiles tab active. Open animation: fade + slide-up.
- **Top nav** (`CardTheme`): one big rounded pill container holding the 6 tabs evenly spaced;
  selected tab = filled inner pill; `Unload` label in `DANGER` red. Tab hit-testing here.
- Input: `mouseClicked` routes to nav → card list (delegates to `ModuleCardRenderer.click`) →
  Profiles panel. `handleMouseInput` → scroll. `keyTyped` → keybinding + value editor + close key
  (RSHIFT via `ClickGuiModule`). Mirror the robust patterns in `ModernClickGuiScreen`
  (try/catch around draw, `doesGuiPauseGame()=false`, `Keyboard.enableRepeatEvents`).
- **Category mapping** (`CategoryTab` enum from Agent E):
  `COMBAT→Combat`, `MOVEMENT→Move`, `RENDER→Visual`, `CLIENT+PLAYER+MISC→Utility`.
  Build each tab's module list by unioning the mapped enum categories via
  `moduleManager.getVisibleModules(...)`.
- **Unload** tab: on select, disable every module (`for m: m.setEnabled(false)`) then
  `LionClient.getInstance().toggleClickGui()` to close. Confirm-free soft unload.

### Agent E — Profiles, tabs, and wiring
- `CategoryTab.java` enum: `COMBAT("Combat"), MOVE("Move"), VISUAL("Visual"), UTILITY("Utility"),
  PROFILES("Profiles"), UNLOAD("Unload")` with `displayName` + `isModuleTab()` + mapped
  `Category[]`.
- `ProfilesPanel.java`: render list of `configManager.listConfigs()`, highlight
  `getCurrentConfigName()` as `ACTIVE`; rows are cards with `LOAD` pill; top actions
  `New` (`createNextConfig`), `Delete` (`deleteCurrentConfig`), `Open folder` (`openFolder`).
  Same card visual language via `GuiGfx`/`CardTheme`.
- **`ClickGuiModule.java` edits**: add
  - `EnumSetting<GuiStyle>` gains `CARD("Card")` value, default `CARD`.
  - `EnumSetting<Scale> scale` = Small/Normal/Large/Extra large → expose `getCardScale()` float.
  - `NumberSetting cardRed/Green/Blue` default orange `(232,114,42)` → `getCardAccentColor()`.
    (Reuse the existing modern-accent pattern; present under a `Card` visibility group.)
  - `BooleanSetting allowInput` ("Allow input while open", default false) → `isCardInputAllowed()`.
  - Visibility suppliers so Card-group settings show only when style==CARD.
- **`LionClient.java` edits**: construct `CardClickGuiScreen`; in `toggleClickGui()` /
  `refreshClickGuiStyle()` pick `CARD` screen when `getGuiStyle()==CARD`.
- **`ClickGuiModule.GuiStyle`**: add `CARD("Card")` (default returned by `getGuiStyle()` fallback).

---

## Integration (lead, after subagents return)
1. Drop all files into `com/lionclient/gui/card/`.
2. Reconcile `RenderState` shape + method signatures across B/C/D.
3. Apply the `ClickGuiModule` / `LionClient` edits (Agent E) — verify `GuiStyle.CARD` default +
   screen selection.
4. Static sanity pass (JDK-8 syntax: no lambdas in new files except where the codebase already
   uses them; anonymous classes elsewhere — **match existing style: JDK-8 source, prefer anon
   classes**). No `var`, no switch-expressions.
5. Cannot compile on macOS — hand the built to the user (Windows `build.bat`). Provide a
   focused smoke-test checklist.

## Constraints for ALL agents
- **Java 8 source level.** No `var`, no records, no switch expressions, no text blocks. Lambdas are
  used sparingly in the codebase (visibility suppliers) — prefer anonymous inner classes in GUI code.
- Immediate-mode only. No new libraries. Only `net.minecraft.*` / `org.lwjgl.*` / `GlStateManager`
  / `GL11` already available via stubs.
- Wrap `drawScreen` body in try/catch like `ModernClickGuiScreen`.
- Keep everything in the new `card` package; **do not modify `ModernClickGuiScreen` or
  `ClickGuiScreen`.** Only Agent E touches `ClickGuiModule` + `LionClient`.
- Match the mockups: rounded corners everywhere, orange accent, dim card bg, generous padding,
  iOS toggles, keybind badges, pill selectors.

## Smoke-test checklist (user, on Windows)
- RShift opens the Card GUI; nav tabs switch; `Unload` disables all + closes.
- Cards expand/collapse smoothly; toggles flip; keybind badge rebinds on click, Delete clears.
- Enum pill dropdown opens/selects; sliders drag; number field edits; range dual-handle works.
- Profiles: create/load/delete/open-folder; ACTIVE highlight correct; persists across relaunch.
- Scale enum resizes GUI; accent color changes recolor toggles/pills; classic/modern still work.
