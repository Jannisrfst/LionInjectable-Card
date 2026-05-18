package net.minecraft.client.gui;

public class GuiScreen {
    public int width;
    public int height;

    public GuiScreen() {}

    public  void drawScreen     (int mouseX, int mouseY, float partialTicks) {}
    public  void initGui        () {}
    public  void onGuiClosed    () {}
    public  boolean doesGuiPauseGame() { return false; }
    protected void mouseClicked  (int mouseX, int mouseY, int button) {}
    protected void mouseReleased (int mouseX, int mouseY, int button) {}
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {}
    protected void keyTyped      (char typedChar, int keyCode)              {}

    public  void func_73863_a    (int mouseX, int mouseY, float partialTicks) {}
    public  void func_73866_w_   () {}
    public  void func_146281_b   () {}
    public  boolean func_73868_f () { return false; }
    protected void func_73864_a  (int mouseX, int mouseY, int button) {}
    protected void func_146286_b (int mouseX, int mouseY, int button) {}
    protected void func_146273_a (int mouseX, int mouseY, int button, long timeSinceLastClick) {}
    protected void func_73869_a  (char typedChar, int keyCode) {}
}
