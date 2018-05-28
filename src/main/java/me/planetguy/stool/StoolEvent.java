package me.planetguy.stool;

import net.minecraft.entity.player.EntityPlayer;

public class StoolEvent {
    private final String eventType;
    private final String[] extras;
    private final String displayName;
    public final double posX;
    public final double posY;
    public final double posZ;
    public final long realTime;
    public final long matchTime;



    public StoolEvent(long realTime, long matchTime, EntityPlayer user, String eventType, String... extras) {
        this.eventType = eventType;
        this.extras = extras;
        this.displayName=user.getDisplayName();
        this.posX =user.posX;
        this.posY=user.posY;
        this.posZ=user.posZ;
        this.realTime=realTime;
        this.matchTime=matchTime;
    }

    public String getEventType() {
        return eventType;
    }

    public String[] getExtras() {
        return extras;
    }

    public String getDisplayName() {
        return displayName;
    }
}
