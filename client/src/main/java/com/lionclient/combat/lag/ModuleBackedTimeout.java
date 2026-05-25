package com.lionclient.combat.lag;

import com.lionclient.feature.module.Module;

public final class ModuleBackedTimeout extends AbstractTimeout {
    private final Module module;
    private final AbstractTimeout secondary;
    private boolean moduleDisabled;

    public ModuleBackedTimeout(Module module) {
        this(module, null);
    }

    public ModuleBackedTimeout(Module module, AbstractTimeout secondary) {
        this.module = module;
        this.secondary = secondary;
        if (!module.isEnabled()) {
            moduleDisabled = true;
        }
    }

    @Override
    protected boolean shouldHaveTimedOut() {
        if (!module.isEnabled()) {
            moduleDisabled = true;
        }
        if (moduleDisabled) {
            return true;
        }
        return secondary != null && secondary.isTimedOut();
    }
}
