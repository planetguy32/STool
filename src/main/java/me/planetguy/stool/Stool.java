package me.planetguy.stool;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.command.ICommand;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;
import net.minecraftforge.event.world.BlockEvent;

import java.util.*;

@Mod(modid = Constants.modID, version = Constants.version, acceptableRemoteVersions = "*")
public class Stool {

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent pie) {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
    }

    @Mod.EventHandler
    public static void init(FMLServerStartingEvent event) {
        for(ICommand cmd:Commands.createCommands()){
            event.registerServerCommand(cmd);
        }
        SqlLogger.setupSql();
    }

    @SubscribeEvent
    public void handle(ServerChatEvent event) {
        String text = event.message;
        if ("pause".equals(text)) {
            if (SqlLogger.pause(event.player))
                setChatMessage(event, "\u00A74" + event.message);
        }
        if ("resume".equals(text)) {
            if (SqlLogger.resume(event.player))
                setChatMessage(event, "\u00A7A" + event.message);
        }
        if ("go".equals(text)) {
            tryToStartMatch(event, false);
            setChatMessage(event, "\u00A7A"+event.message);
        }
        if ("ready".equals(text)) {
            broadcastMessage(SqlLogger.getCurrentGameGuess());
        }
        if ("gg".equals(text)){
            setChatMessage(event, "\u00A75" + event.message);
            broadcastMessage("\u00A76Please end the match - use /win (if you won) or /lose (otherwise)");
        }
        SqlLogger.addEvent(event.player, "chat", event.message);
    }

    private int time = 0;

    private static List<EntityPlayerMP> getPlayers(){
        return MinecraftServer.getServer()
                        .getConfigurationManager()
                        .playerEntityList;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        if (SqlLogger.isInGame()) {
            time++;
            //Every 100 ticks, make events
            if (time % 100 == 0) {
                for (EntityPlayerMP p : getPlayers()) {
                    SqlLogger.addEvent(p, "pos");
                }
            }
        }
    }

    @SubscribeEvent
    public void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        SqlLogger.addEvent(event.player, "login");
    }

    @SubscribeEvent
    public void onLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        SqlLogger.addEvent(event.player, "logout");
    }

    private void tryToStartMatch(ServerChatEvent event, boolean isFromYN) {
        @SuppressWarnings("unchecked") List<EntityPlayerMP> players =
                MinecraftServer.getServer()
                        .getConfigurationManager()
                        .playerEntityList;

        List<String> usernames = new ArrayList<>();
        for (EntityPlayerMP player : players) {
            usernames.add(player.getCommandSenderName());
        }

        broadcastMessage(SqlLogger.startMatch(usernames));
    }

    public static void setChatMessage(ServerChatEvent event, String newText) {
        event.component = new ChatComponentTranslation("chat.type.text",
                event.player.func_145748_c_(),
                newText);
    }

    public static void broadcastMessage(String message){
        for(EntityPlayerMP player:getPlayers()){
            for(String s:message.split("\n")){
                player.addChatMessage(new ChatComponentText(s));
            }
        }
    }

    @SubscribeEvent
    public void handle(LivingDeathEvent event) {
        EntityLivingBase entity = event.entityLiving;
        if (entity instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) entity;
            DamageSource source = event.source;
            Entity dealer = source.getEntity();
            String dealerName = "";
            if (dealer instanceof EntityPlayerMP)
                dealerName = ((EntityPlayerMP) dealer).getDisplayName();
            SqlLogger.addEvent(player, "death", source.damageType, dealerName);
            for(Object playerObj:MinecraftServer.getServer().getConfigurationManager().playerEntityList){
                EntityPlayerMP otherPlayer= (EntityPlayerMP) playerObj;
                if(otherPlayer.getDisplayName().equals(dealerName)) {
                    SqlLogger.addEvent(otherPlayer, "kill", source.damageType, player.getDisplayName());
                    break;
                }
            }
        }
    }

    @SubscribeEvent
    public void handle(LivingHurtEvent event) {
        EntityLivingBase entity = event.entityLiving;
        if (entity instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) entity;
            DamageSource source = event.source;
            Entity dealer = source.getEntity();
            String dealerName = "";
            if (dealer instanceof EntityPlayerMP)
                dealerName = ((EntityPlayerMP) dealer).getDisplayName();
            SqlLogger.addEvent(player, "takedmg", source.damageType, dealerName);
            for(Object playerObj:MinecraftServer.getServer().getConfigurationManager().playerEntityList){
                EntityPlayerMP otherPlayer= (EntityPlayerMP) playerObj;
                if(otherPlayer.getDisplayName().equals(dealerName)) {
                    SqlLogger.addEvent(otherPlayer, "dealdmg", source.damageType, player.getDisplayName());
                    break;
                }
            }
        }
    }

    private boolean logEvent(Event event) {
        return true;
    }

    @SubscribeEvent
    public void handle(PlayerUseItemEvent event) {
        System.out.println(event);
        if (logEvent(event)) {
            SqlLogger.addEvent(
                    event.entityPlayer,
                    "item",
                    event.item.getDisplayName(), event.duration + "");
        }
    }

    @SubscribeEvent
    public void handle(BlockEvent.BreakEvent event) {
        if (logEvent(event)) {
            ItemStack hand=event.getPlayer().getItemInUse();
            SqlLogger.addEvent(
                    event.getPlayer(),
                    "break",
                    event.block.getLocalizedName(),
                    hand==null ? "null" : hand.getUnlocalizedName());
        }
    }

    @SubscribeEvent
    public void handle(BlockEvent.PlaceEvent event) {
        if (logEvent(event)) {
            SqlLogger.addEvent(
                    event.player,
                    "place",
                    event.block.getLocalizedName());
        }
    }

}