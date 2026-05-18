package net.minecraft.util;

public class AxisAlignedBB {
    public double minX, minY, minZ, maxX, maxY, maxZ;

    public AxisAlignedBB(double minX, double minY, double minZ,
                         double maxX, double maxY, double maxZ) {
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
    }

    public AxisAlignedBB expand(double x, double y, double z) {
        return new AxisAlignedBB(minX - x, minY - y, minZ - z,
                                 maxX + x, maxY + y, maxZ + z);
    }
}
