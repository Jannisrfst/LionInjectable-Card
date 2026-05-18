package net.minecraftforge.event.entity.living;

import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.common.eventhandler.Event;

public class LivingEvent extends Event {
    public final EntityLivingBase entityLiving;
    public LivingEvent(EntityLivingBase entityLiving) { this.entityLiving = entityLiving; }

    public static class LivingJumpEvent extends LivingEvent {
        public LivingJumpEvent(EntityLivingBase entityLiving) { super(entityLiving); }
    }
}
