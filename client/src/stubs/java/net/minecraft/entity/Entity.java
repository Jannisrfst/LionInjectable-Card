package net.minecraft.entity;

import net.minecraft.util.AxisAlignedBB;

public abstract class Entity {
    public double posX, posY, posZ;
    public double lastTickPosX, lastTickPosY, lastTickPosZ;
    public float  rotationYaw, rotationPitch;
    public boolean isDead;

    public AxisAlignedBB getEntityBoundingBox() { return null; }
    public boolean isInvisible() { return false; }
    public int     getEntityId() { return 0; }
    public String  getName()     { return ""; }
}
