package com.lionclient.feature.module.impl;

import com.lionclient.LionClient;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Team;
import org.lwjgl.input.Keyboard;

public final class BedwarsModule extends Module {
    private final BooleanSetting armorColor = new BooleanSetting("Armor Color", true);
    private final BooleanSetting tabColor = new BooleanSetting("Tab Color", true);

    public BedwarsModule() {
        super("Bedwars", "Skips teammates (same armor/tab color) for AimAssist and KillAura.", Category.MISC, Keyboard.KEY_NONE);
        addSetting(armorColor);
        addSetting(tabColor);
    }

    public static boolean isTeammate(EntityPlayer target) {
        LionClient client = LionClient.getInstance();
        if (client == null) {
            return false;
        }

        BedwarsModule module = client.getModuleManager().getModule(BedwarsModule.class);
        return module != null && module.isEnabled() && module.checkTeammate(target);
    }

    private boolean checkTeammate(EntityPlayer target) {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer self = minecraft.thePlayer;
        if (self == null || target == null || target == self) {
            return false;
        }

        if (armorColor.isEnabled() && sameArmorColor(self, target)) {
            return true;
        }

        return tabColor.isEnabled() && sameTabColor(self, target);
    }

    private boolean sameArmorColor(EntityPlayer self, EntityPlayer target) {
        int selfColor = getArmorColor(self);
        int targetColor = getArmorColor(target);
        return selfColor != -1 && selfColor == targetColor;
    }

    private int getArmorColor(EntityPlayer player) {
        ItemStack[] armor = player.inventory.armorInventory;
        if (armor == null) {
            return -1;
        }

        for (int i = 0; i < armor.length; i++) {
            ItemStack stack = armor[i];
            if (stack == null || !(stack.getItem() instanceof ItemArmor)) {
                continue;
            }
            ItemArmor itemArmor = (ItemArmor) stack.getItem();
            if (itemArmor.getArmorMaterial() != ItemArmor.ArmorMaterial.LEATHER || !itemArmor.hasColor(stack)) {
                continue;
            }
            return itemArmor.getColor(stack);
        }
        return -1;
    }

    private boolean sameTabColor(EntityPlayer self, EntityPlayer target) {
        String selfPrefix = getTeamColorPrefix(self);
        String targetPrefix = getTeamColorPrefix(target);
        return selfPrefix != null && selfPrefix.equals(targetPrefix);
    }

    private String getTeamColorPrefix(EntityPlayer player) {
        Team team = player.getTeam();
        if (!(team instanceof ScorePlayerTeam)) {
            return null;
        }
        String prefix = ((ScorePlayerTeam) team).getColorPrefix();
        return prefix == null || prefix.isEmpty() ? null : prefix;
    }
}
