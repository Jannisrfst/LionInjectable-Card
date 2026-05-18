package lion.client;

public final class NativeBridge {
    private NativeBridge() {}

    public static final int VK_LBUTTON  = 0x01;
    public static final int VK_RBUTTON  = 0x02;
    public static final int VK_MBUTTON  = 0x04;
    public static final int VK_F8       = 0x77;

    public static final int CLICK_LEFT   = 0;
    public static final int CLICK_RIGHT  = 1;
    public static final int CLICK_MIDDLE = 2;

    public static native boolean isKeyDown(int vk);

    public static native void sendClick(int button);

    public static native boolean isMinecraftFocused();

    public static native String getFocusedWindowTitle();

    public static native int getForegroundPid();

    public static native long findMinecraftHwnd();

    public static native int[] getWindowRect(long hwnd);

    public static native void setClickThrough(long hwnd);

    public static native long findOwnWindowByTitle(String titleSubstring);

    public static native boolean attachAgent(String jarPath);

    public static boolean isAvailable() {
        try { getForegroundPid(); return true; }
        catch (UnsatisfiedLinkError e) { return false; }
    }
}
