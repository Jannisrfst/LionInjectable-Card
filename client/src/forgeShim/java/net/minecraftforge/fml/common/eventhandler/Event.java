package net.minecraftforge.fml.common.eventhandler;

public class Event {
    private boolean canceled;

    public boolean isCancelable()    { return true; }
    public boolean isCanceled()      { return canceled; }
    public void    setCanceled(boolean c) { this.canceled = c; }
}
