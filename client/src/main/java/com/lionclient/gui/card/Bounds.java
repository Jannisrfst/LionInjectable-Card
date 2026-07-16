package com.lionclient.gui.card;

public final class Bounds {
    public final int left;
    public final int top;
    public final int right;
    public final int bottom;

    public Bounds(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public boolean contains(int x, int y) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    public int width() {
        return right - left;
    }

    public int height() {
        return bottom - top;
    }
}
