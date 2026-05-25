package com.lionclient.combat.lag;

public abstract class AbstractTimeout {
    private volatile boolean forcefullyTimedOut;

    protected abstract boolean shouldHaveTimedOut();

    public final boolean isTimedOut() {
        return forcefullyTimedOut || shouldHaveTimedOut();
    }

    public final void forceTimeOut() {
        forcefullyTimedOut = true;
    }
}
