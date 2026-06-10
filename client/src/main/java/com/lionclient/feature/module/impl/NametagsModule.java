package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public final class NametagsModule extends Module {
    private static final float LABEL_BASE_SCALE = 0.026F;
    private static final float LABEL_DISTANCE_REFERENCE = 32.0F;
    private static final float LABEL_DISTANCE_MULTIPLIER = 6.0F;
    private static final float LABEL_MAX_SCALE = 0.30F;

    private static NametagsModule instance;

    private final NumberSetting scale = new NumberSetting("Scale", 50, 300, 5, 100);
    private final BooleanSetting border = new BooleanSetting("Border", true);
    private final BooleanSetting playersOnly = new BooleanSetting("Players Only", false);
    private final BooleanSetting showArmor = new BooleanSetting("Show Armor", false);
    private final BooleanSetting hideVanilla = new BooleanSetting("Hide Vanilla", true);

    private boolean registered;

    public NametagsModule() {
        super("Nametags", "Massively scales up nametags and outlines them with the ClickGUI accent color.", Category.RENDER, Keyboard.KEY_NONE);
        instance = this;
        addSetting(scale);
        addSetting(border);
        addSetting(playersOnly);
        addSetting(showArmor);
        addSetting(hideVanilla);
    }

    public static NametagsModule getInstance() {
        return instance;
    }

    public boolean shouldHideVanillaNametags() {
        return isEnabled() && hideVanilla.isEnabled();
    }

    @Override
    protected void onEnable() {
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(this);
            registered = true;
        }
    }

    @Override
    protected void onDisable() {
        if (registered) {
            MinecraftForge.EVENT_BUS.unregister(this);
            registered = false;
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        RenderManager renderManager = mc.getRenderManager();
        if (renderManager == null) {
            return;
        }

        float partialTicks = event.partialTicks;
        for (Object obj : mc.theWorld.loadedEntityList) {
            if (!(obj instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase entity = (EntityLivingBase) obj;
            if (playersOnly.isEnabled() && !(entity instanceof EntityPlayer)) {
                continue;
            }
            if (!shouldShowNametag(entity, mc)) {
                continue;
            }

            String text = resolveLabel(entity);
            if (text == null || text.isEmpty()) {
                continue;
            }

            double x = interpolate(entity.lastTickPosX, entity.posX, partialTicks) - renderManager.viewerPosX;
            double y = interpolate(entity.lastTickPosY, entity.posY, partialTicks) - renderManager.viewerPosY;
            double z = interpolate(entity.lastTickPosZ, entity.posZ, partialTicks) - renderManager.viewerPosZ;

            renderCustomNametag(mc, renderManager, entity, text, x, y, z);
        }
    }

    private static double interpolate(double prev, double current, float partialTicks) {
        return prev + (current - prev) * partialTicks;
    }

    private boolean shouldShowNametag(EntityLivingBase entity, Minecraft mc) {
        if (entity == mc.getRenderViewEntity()) {
            return false;
        }
        Entity riding = mc.thePlayer == null ? null : mc.thePlayer.ridingEntity;
        if (entity == riding) {
            return false;
        }

        if (entity instanceof EntityPlayer) {
            if (entity == mc.thePlayer) {
                return false;
            }
            if (entity.isInvisibleToPlayer(mc.thePlayer)) {
                return false;
            }
            Team team = entity.getTeam();
            Team playerTeam = mc.thePlayer == null ? null : mc.thePlayer.getTeam();
            if (team != null) {
                switch (team.getNameTagVisibility()) {
                    case NEVER:
                        return false;
                    case HIDE_FOR_OTHER_TEAMS:
                        return playerTeam != null && team.isSameTeam(playerTeam);
                    case HIDE_FOR_OWN_TEAM:
                        return playerTeam == null || !team.isSameTeam(playerTeam);
                    case ALWAYS:
                    default:
                        return true;
                }
            }
            return true;
        }
        return entity.getAlwaysRenderNameTag() || entity.hasCustomName();
    }

    private String resolveLabel(EntityLivingBase entity) {
        String text = null;
        if (entity.getDisplayName() != null) {
            text = entity.getDisplayName().getFormattedText();
        }
        if (text == null || text.isEmpty()) {
            text = entity.getName();
        }
        if (text == null || text.isEmpty()) {
            return text;
        }
        return EnumChatFormatting.getTextWithoutFormattingCodes(text);
    }

    private void renderCustomNametag(Minecraft mc, RenderManager renderManager, EntityLivingBase entity,
                                     String text, double x, double y, double z) {
        FontRenderer font = mc.fontRendererObj;
        if (font == null) {
            return;
        }

        double distanceSq = x * x + y * y + z * z;
        float labelScale = getLabelScale(distanceSq) * (scale.getValue() / 100.0F);
        double labelY = y + entity.height + 0.5D - (entity.isSneaking() ? 0.25D : 0.0D);

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, labelY, z);
            GL11.glNormal3f(0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(-labelScale, -labelScale, labelScale);
            GlStateManager.disableLighting();
            GlStateManager.depthMask(false);
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            int halfWidth = font.getStringWidth(text) / 2;
            drawBackground(halfWidth);
            if (border.isEnabled()) {
                drawBorder(halfWidth);
            }
            font.drawString(text, -halfWidth, 0, 0xFFFFFFFF);
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            font.drawString(text, -halfWidth, 0, 0xFFFFFFFF);
            if (showArmor.isEnabled() && entity instanceof EntityPlayer) {
                drawArmorRow(mc, (EntityPlayer) entity);
            }
        } finally {
            GlStateManager.disableBlend();
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.disableLighting();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    private float getLabelScale(double distanceSq) {
        float distance = (float) Math.sqrt(distanceSq);
        float scaled = LABEL_BASE_SCALE * Math.max(1.0F, (distance / LABEL_DISTANCE_REFERENCE) * LABEL_DISTANCE_MULTIPLIER);
        return Math.min(LABEL_MAX_SCALE, scaled);
    }

    private void drawArmorRow(Minecraft mc, EntityPlayer player) {
        ItemStack[] armor = player.inventory.armorInventory;
        if (armor == null) {
            return;
        }
        int count = 0;
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null) {
                count++;
            }
        }
        if (count == 0) {
            return;
        }

        RenderItem renderItem = mc.getRenderItem();
        if (renderItem == null) {
            return;
        }

        float itemSize = 30.0F;
        float spacing = 32.0F;
        float totalWidth = count * spacing;
        float startX = -totalWidth / 2.0F;
        float centerY = -itemSize / 2.0F - 4.0F;

        GlStateManager.pushMatrix();
        try {
            mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
            mc.getTextureManager().getTexture(TextureMap.locationBlocksTexture).setBlurMipmap(false, false);
            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            int drawn = 0;
            for (int i = armor.length - 1; i >= 0; i--) {
                ItemStack stack = armor[i];
                if (stack == null) {
                    continue;
                }
                float centerX = startX + drawn * spacing + spacing / 2.0F;
                drawn++;

                IBakedModel model = renderItem.getItemModelMesher().getItemModel(stack);
                if (model == null) {
                    continue;
                }

                GlStateManager.pushMatrix();
                try {
                    GlStateManager.translate(centerX, centerY, 0.0F);
                    GlStateManager.scale(itemSize, -itemSize, itemSize);
                    renderItem.renderItem(stack, model);
                } finally {
                    GlStateManager.popMatrix();
                }
            }

            GlStateManager.disableAlpha();
            GlStateManager.disableRescaleNormal();
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private void drawBackground(int halfWidth) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        GlStateManager.disableTexture2D();
        renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        renderer.pos(-halfWidth - 2, -2, 0.0D).color(0.0F, 0.0F, 0.0F, 0.45F).endVertex();
        renderer.pos(-halfWidth - 2, 10, 0.0D).color(0.0F, 0.0F, 0.0F, 0.45F).endVertex();
        renderer.pos(halfWidth + 2, 10, 0.0D).color(0.0F, 0.0F, 0.0F, 0.45F).endVertex();
        renderer.pos(halfWidth + 2, -2, 0.0D).color(0.0F, 0.0F, 0.0F, 0.45F).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();
    }

    private void drawBorder(int halfWidth) {
        int color = ClickGuiModule.getModernAccentColor();
        float r = ((color >>> 16) & 255) / 255.0F;
        float g = ((color >>> 8) & 255) / 255.0F;
        float b = (color & 255) / 255.0F;

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        GlStateManager.disableTexture2D();
        GL11.glLineWidth(1.5F);
        renderer.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        renderer.pos(-halfWidth - 2, -2, 0.0D).color(r, g, b, 1.0F).endVertex();
        renderer.pos(halfWidth + 2, -2, 0.0D).color(r, g, b, 1.0F).endVertex();
        renderer.pos(halfWidth + 2, 10, 0.0D).color(r, g, b, 1.0F).endVertex();
        renderer.pos(-halfWidth - 2, 10, 0.0D).color(r, g, b, 1.0F).endVertex();
        tessellator.draw();
        GL11.glLineWidth(1.0F);
        GlStateManager.enableTexture2D();
    }
}
