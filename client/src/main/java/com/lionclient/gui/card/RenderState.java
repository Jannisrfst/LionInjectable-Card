package com.lionclient.gui.card;

import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.Setting;
import net.minecraft.client.gui.GuiTextField;

public final class RenderState {
    public int accent;
    public Module bindingModule;
    public Setting draggingSetting;
    public boolean draggingRangeHigh;
    public EnumSetting<?> openDropdown;
    public Setting editingSetting;
    public GuiTextField valueEditor;
}
