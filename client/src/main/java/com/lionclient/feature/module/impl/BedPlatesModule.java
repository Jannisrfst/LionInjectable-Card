package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.NumberSetting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public final class BedPlatesModule extends Module {
    private static final int FALLBACK_RESCAN_INTERVAL_TICKS = 240;
    private static final int FALLBACK_RESCAN_CHUNKS_PER_TICK = 1;
    private static final float LABEL_BASE_SCALE = 0.026F;
    private static final float LABEL_DISTANCE_REFERENCE = 32.0F;
    private static final float LABEL_DISTANCE_MULTIPLIER = 6.0F;
    private static final float LABEL_MAX_SCALE = 0.30F;

    private final NumberSetting range = new NumberSetting("Range", 5, 128, 1, 128);
    private final NumberSetting layers = new NumberSetting("Layers", 1, 4, 1, 2);
    private final BooleanSetting showDistance = new BooleanSetting("Show Distance", true);

    private final Map<String, CachedBed> bedCache = new HashMap<String, CachedBed>();
    private final Map<Long, Set<String>> chunkBeds = new HashMap<Long, Set<String>>();
    private final Set<Long> scannedChunks = new HashSet<Long>();
    private final Deque<Long> rescanQueue = new ArrayDeque<Long>();
    private final Set<Long> queuedChunks = new HashSet<Long>();

    private WorldClient cachedWorld;
    private int ticksSinceFallback;
    private int lastRange = range.getValue();
    private int lastLayers = layers.getValue();

    public BedPlatesModule() {
        super("BedPlates", "Shows the unique defense blocks around nearby beds.", Category.RENDER, Keyboard.KEY_NONE);
        addSetting(range);
        addSetting(layers);
        addSetting(showDistance);
    }

    @Override
    protected void onEnable() {
        resetCache();
    }

    @Override
    protected void onDisable() {
        resetCache();
    }

    @Override
    public void onClientTick() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        WorldClient world = mc.theWorld;
        if (player == null || world == null) {
            resetCache();
            return;
        }

        if (cachedWorld != world) {
            resetCache();
            cachedWorld = world;
        }

        if (lastRange != range.getValue() || lastLayers != layers.getValue()) {
            lastRange = range.getValue();
            lastLayers = layers.getValue();
            resetChunkTracking();
            queueNearbyChunks(player, true);
        }

        boolean bedBroken = removeBrokenBeds(world);
        if (bedBroken) {
            queueNearbyChunks(player, true);
        }

        scanNewNearbyChunks(world, player);

        ticksSinceFallback++;
        if (ticksSinceFallback >= FALLBACK_RESCAN_INTERVAL_TICKS) {
            ticksSinceFallback = 0;
            queueNearbyChunks(player, true);
        }

        processQueuedRescans(world);
    }

    @Override
    public void onRenderWorld(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.theWorld == null || bedCache.isEmpty()) {
            return;
        }

        List<BedRenderInfo> beds = new ArrayList<BedRenderInfo>();
        double maxDistanceSq = range.getValue() * range.getValue();
        for (CachedBed cachedBed : bedCache.values()) {
            double distanceSq = getDistanceSq(player, cachedBed.first, cachedBed.second);
            if (distanceSq <= maxDistanceSq) {
                beds.add(new BedRenderInfo(cachedBed.first, cachedBed.second, cachedBed.defenses, distanceSq));
            }
        }

        if (beds.isEmpty()) {
            return;
        }

        Collections.sort(beds, new Comparator<BedRenderInfo>() {
            @Override
            public int compare(BedRenderInfo left, BedRenderInfo right) {
                return Double.compare(left.distanceSq, right.distanceSq);
            }
        });

        for (BedRenderInfo bed : beds) {
            renderLabel(mc, bed);
        }
    }

    private void scanNewNearbyChunks(WorldClient world, EntityPlayerSP player) {
        int chunkRadius = getChunkRadius();
        int playerChunkX = MathHelper.floor_double(player.posX) >> 4;
        int playerChunkZ = MathHelper.floor_double(player.posZ) >> 4;

        for (int chunkX = playerChunkX - chunkRadius; chunkX <= playerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = playerChunkZ - chunkRadius; chunkZ <= playerChunkZ + chunkRadius; chunkZ++) {
                long chunkKey = makeChunkKey(chunkX, chunkZ);
                if (scannedChunks.contains(chunkKey) || !isChunkLoaded(world, chunkX, chunkZ)) {
                    continue;
                }
                scanChunk(world, chunkX, chunkZ);
            }
        }
    }

    private boolean removeBrokenBeds(WorldClient world) {
        boolean removed = false;
        List<String> staleKeys = new ArrayList<String>();
        for (Map.Entry<String, CachedBed> entry : bedCache.entrySet()) {
            CachedBed bed = entry.getValue();
            if (!isBedBlock(world, bed.first) || !isBedBlock(world, bed.second)) {
                staleKeys.add(entry.getKey());
            }
        }

        for (String staleKey : staleKeys) {
            CachedBed removedBed = bedCache.remove(staleKey);
            if (removedBed == null) {
                continue;
            }
            long ownerChunkKey = getOwnerChunkKey(removedBed.first);
            Set<String> keys = chunkBeds.get(ownerChunkKey);
            if (keys != null) {
                keys.remove(staleKey);
                if (keys.isEmpty()) {
                    chunkBeds.remove(ownerChunkKey);
                }
            }
            removed = true;
        }
        return removed;
    }

    private void queueNearbyChunks(EntityPlayerSP player, boolean resetScannedState) {
        int chunkRadius = getChunkRadius();
        int playerChunkX = MathHelper.floor_double(player.posX) >> 4;
        int playerChunkZ = MathHelper.floor_double(player.posZ) >> 4;

        for (int chunkX = playerChunkX - chunkRadius; chunkX <= playerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = playerChunkZ - chunkRadius; chunkZ <= playerChunkZ + chunkRadius; chunkZ++) {
                long chunkKey = makeChunkKey(chunkX, chunkZ);
                if (resetScannedState) {
                    scannedChunks.remove(chunkKey);
                }
                if (queuedChunks.add(chunkKey)) {
                    rescanQueue.addLast(chunkKey);
                }
            }
        }
    }

    private void processQueuedRescans(WorldClient world) {
        int processed = 0;
        while (!rescanQueue.isEmpty() && processed < FALLBACK_RESCAN_CHUNKS_PER_TICK) {
            long chunkKey = rescanQueue.removeFirst();
            queuedChunks.remove(chunkKey);
            int chunkX = unpackChunkX(chunkKey);
            int chunkZ = unpackChunkZ(chunkKey);
            if (isChunkLoaded(world, chunkX, chunkZ)) {
                scanChunk(world, chunkX, chunkZ);
            } else {
                scannedChunks.remove(chunkKey);
                chunkBeds.remove(chunkKey);
            }
            processed++;
        }
    }

    private void scanChunk(WorldClient world, int chunkX, int chunkZ) {
        if (!isChunkLoaded(world, chunkX, chunkZ)) {
            return;
        }

        Chunk chunk = world.getChunkFromChunkCoords(chunkX, chunkZ);
        long chunkKey = makeChunkKey(chunkX, chunkZ);
        Set<String> foundBeds = new HashSet<String>();
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int y = 0; y < 256; y++) {
                    BlockPos pos = new BlockPos(startX + localX, y, startZ + localZ);
                    Block block = chunk.getBlock(pos);
                    if (!(block instanceof BlockBed)) {
                        continue;
                    }

                    BedPair pair = resolveBedPair(world, pos);
                    if (getOwnerChunkKey(pair.first) != chunkKey) {
                        continue;
                    }

                    String bedKey = makeBedKey(pair.first, pair.second);
                    if (!foundBeds.add(bedKey)) {
                        continue;
                    }

                    bedCache.put(bedKey, new CachedBed(pair.first, pair.second, collectDefenseBlocks(world, pair.first, pair.second)));
                }
            }
        }

        Set<String> previousBeds = chunkBeds.put(chunkKey, foundBeds);
        if (previousBeds != null) {
            for (String previousKey : previousBeds) {
                if (!foundBeds.contains(previousKey)) {
                    bedCache.remove(previousKey);
                }
            }
        }
        if (foundBeds.isEmpty()) {
            chunkBeds.remove(chunkKey);
        }
        scannedChunks.add(chunkKey);
    }

    private BedPair resolveBedPair(WorldClient world, BlockPos pos) {
        BlockPos otherPart = pos;
        BlockPos[] neighbors = new BlockPos[] {
            pos.north(),
            pos.south(),
            pos.east(),
            pos.west()
        };
        for (BlockPos neighbor : neighbors) {
            if (isBedBlock(world, neighbor)) {
                otherPart = neighbor;
                break;
            }
        }

        BlockPos first = comparePos(pos, otherPart) <= 0 ? pos : otherPart;
        BlockPos second = comparePos(pos, otherPart) <= 0 ? otherPart : pos;
        return new BedPair(first, second);
    }

    private List<ItemStack> collectDefenseBlocks(WorldClient world, BlockPos first, BlockPos second) {
        Set<String> seen = new LinkedHashSet<String>();
        List<ItemStack> stacks = new ArrayList<ItemStack>();
        int radius = layers.getValue();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = 0; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    addDefenseBlock(world, seen, stacks, first.add(dx, dy, dz));
                    addDefenseBlock(world, seen, stacks, second.add(dx, dy, dz));
                }
            }
        }

        return stacks;
    }

    private void addDefenseBlock(WorldClient world, Set<String> seen, List<ItemStack> stacks, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block == null || block == Blocks.air || block instanceof BlockBed || block.getMaterial() == Material.air) {
            return;
        }

        Item item = Item.getItemFromBlock(block);
        if (item == null) {
            return;
        }

        int meta = block.damageDropped(state);
        String key = Item.itemRegistry.getNameForObject(item) + ":" + meta;
        if (seen.add(key)) {
            stacks.add(new ItemStack(item, 1, meta));
        }
    }

    private boolean isBedBlock(WorldClient world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof BlockBed;
    }

    private boolean isChunkLoaded(WorldClient world, int chunkX, int chunkZ) {
        return world.getChunkProvider().chunkExists(chunkX, chunkZ);
    }

    private int getChunkRadius() {
        return Math.max(1, (range.getValue() + 15) >> 4);
    }

    private double getDistanceSq(EntityPlayerSP player, BlockPos first, BlockPos second) {
        double centerX = (first.getX() + second.getX()) / 2.0D + 0.5D;
        double centerY = Math.max(first.getY(), second.getY()) + 0.5D;
        double centerZ = (first.getZ() + second.getZ()) / 2.0D + 0.5D;
        double deltaX = player.posX - centerX;
        double deltaY = player.posY - centerY;
        double deltaZ = player.posZ - centerZ;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }

    private void renderLabel(Minecraft mc, BedRenderInfo bed) {
        FontRenderer font = mc.fontRendererObj;
        double viewerX = mc.getRenderManager().viewerPosX;
        double viewerY = mc.getRenderManager().viewerPosY;
        double viewerZ = mc.getRenderManager().viewerPosZ;

        double x = (bed.first.getX() + bed.second.getX()) / 2.0D + 0.5D - viewerX;
        double y = Math.max(bed.first.getY(), bed.second.getY()) + 1.35D - viewerY;
        double z = (bed.first.getZ() + bed.second.getZ()) / 2.0D + 0.5D - viewerZ;

        String distanceText = showDistance.isEnabled()
            ? String.format(Locale.US, "%.1fm", Math.sqrt(bed.distanceSq))
            : null;

        float scale = getLabelScale(bed.distanceSq);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, z);
            GL11.glNormal3f(0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(-scale, -scale, scale);

            if (bed.defenses.isEmpty()) {
                GlStateManager.disableLighting();
                GlStateManager.depthMask(false);
                GlStateManager.disableDepth();
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

                String text = "Uncovered";
                int width = font.getStringWidth(text) / 2;
                drawBackground(width);
                GlStateManager.enableDepth();
                GlStateManager.depthMask(true);
                font.drawString(text, -width, 0, 0xFFFFFFFF);
            } else {
                drawDefenseIcons(mc, font, bed.defenses, distanceText);
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

    private void drawDefenseIcons(Minecraft mc, FontRenderer font, List<ItemStack> defenses, String distanceText) {
        RenderItem renderItem = mc.getRenderItem();
        if (renderItem == null) {
            return;
        }

        float iconSize = 16.0F;
        float spacing = 18.0F;
        int count = defenses.size();
        float totalWidth = count * spacing;
        float startX = -totalWidth / 2.0F;

        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        drawBackgroundRect(startX - 2.0F, -iconSize - 2.0F, startX + totalWidth + 2.0F, 2.0F);

        if (distanceText != null) {
            int width = font.getStringWidth(distanceText) / 2;
            font.drawString(distanceText, -width, 4, 0xFFFFFFFF);
        }

        mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.01F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        for (int i = 0; i < count; i++) {
            ItemStack stack = defenses.get(i);
            TextureAtlasSprite sprite = resolveSprite(renderItem, stack);
            if (sprite == null) {
                continue;
            }

            float left = startX + i * spacing + (spacing - iconSize) / 2.0F;
            float top = -iconSize;
            drawSpriteQuad(sprite, left, top, iconSize);
        }

        GlStateManager.disableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private TextureAtlasSprite resolveSprite(RenderItem renderItem, ItemStack stack) {
        try {
            IBakedModel model = renderItem.getItemModelMesher().getItemModel(stack);
            TextureAtlasSprite sprite = model == null ? null : model.getParticleTexture();
            if (sprite != null) {
                return sprite;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void drawSpriteQuad(TextureAtlasSprite sprite, float left, float top, float size) {
        float minU = sprite.getMinU();
        float maxU = sprite.getMaxU();
        float minV = sprite.getMinV();
        float maxV = sprite.getMaxV();
        float right = left + size;
        float bottom = top + size;

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        renderer.pos(left, bottom, 0.0D).tex(minU, maxV).endVertex();
        renderer.pos(right, bottom, 0.0D).tex(maxU, maxV).endVertex();
        renderer.pos(right, top, 0.0D).tex(maxU, minV).endVertex();
        renderer.pos(left, top, 0.0D).tex(minU, minV).endVertex();
        tessellator.draw();
    }

    private float getLabelScale(double distanceSq) {
        float distance = (float) Math.sqrt(distanceSq);
        float scaled = LABEL_BASE_SCALE * Math.max(1.0F, (distance / LABEL_DISTANCE_REFERENCE) * LABEL_DISTANCE_MULTIPLIER);
        return Math.min(LABEL_MAX_SCALE, scaled);
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

    private void drawBackgroundRect(float minX, float minY, float maxX, float maxY) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        GlStateManager.disableTexture2D();
        renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        renderer.pos(minX, minY, 0.0D).color(0.0F, 0.0F, 0.0F, 0.45F).endVertex();
        renderer.pos(minX, maxY, 0.0D).color(0.0F, 0.0F, 0.0F, 0.45F).endVertex();
        renderer.pos(maxX, maxY, 0.0D).color(0.0F, 0.0F, 0.0F, 0.45F).endVertex();
        renderer.pos(maxX, minY, 0.0D).color(0.0F, 0.0F, 0.0F, 0.45F).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();
    }

    private void resetCache() {
        cachedWorld = null;
        ticksSinceFallback = 0;
        bedCache.clear();
        resetChunkTracking();
    }

    private void resetChunkTracking() {
        chunkBeds.clear();
        scannedChunks.clear();
        rescanQueue.clear();
        queuedChunks.clear();
    }

    private long getOwnerChunkKey(BlockPos pos) {
        return makeChunkKey(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private long makeChunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private int unpackChunkX(long chunkKey) {
        return (int) (chunkKey >> 32);
    }

    private int unpackChunkZ(long chunkKey) {
        return (int) chunkKey;
    }

    private String makeBedKey(BlockPos first, BlockPos second) {
        return first.getX() + ":" + first.getY() + ":" + first.getZ() + "|" + second.getX() + ":" + second.getY() + ":" + second.getZ();
    }

    private int comparePos(BlockPos left, BlockPos right) {
        if (left.getY() != right.getY()) {
            return left.getY() - right.getY();
        }
        if (left.getX() != right.getX()) {
            return left.getX() - right.getX();
        }
        return left.getZ() - right.getZ();
    }

    private static final class BedPair {
        private final BlockPos first;
        private final BlockPos second;

        private BedPair(BlockPos first, BlockPos second) {
            this.first = first;
            this.second = second;
        }
    }

    private static final class CachedBed {
        private final BlockPos first;
        private final BlockPos second;
        private final List<ItemStack> defenses;

        private CachedBed(BlockPos first, BlockPos second, List<ItemStack> defenses) {
            this.first = first;
            this.second = second;
            this.defenses = defenses;
        }
    }

    private static final class BedRenderInfo {
        private final BlockPos first;
        private final BlockPos second;
        private final List<ItemStack> defenses;
        private final double distanceSq;

        private BedRenderInfo(BlockPos first, BlockPos second, List<ItemStack> defenses, double distanceSq) {
            this.first = first;
            this.second = second;
            this.defenses = defenses;
            this.distanceSq = distanceSq;
        }
    }
}
