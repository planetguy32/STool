package me.planetguy.stool;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.command.ICommand;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;
import net.minecraftforge.event.world.BlockEvent;

import java.util.*;

@Mod(modid = Constants.modID, version = Constants.version, acceptableRemoteVersions = "*")
public class Stool {

    public static HashMap<String, String> nicks=new HashMap<>();

    private static Configuration cfg;

    public String defaultPrefix;
    public String defaultSuffix;

    public static boolean IS_DB_READ_ONLY =false;

    public void setupNicks(){
        try {
            String[] text = cfg.get(
                    "ranks",
                    "ranks",
                    new String[0],
                    "Form: player=rank"
            ).getStringList();
            for (String s : text) {
                String[] parts = s.split("=");
                nicks.put(parts[0], parts[1]);
            }
            defaultPrefix=cfg.getString("defaultPrefix",
                    "ranks","#","Default username prefix");

            defaultSuffix=cfg.getString("defaultSuffix",
                    "ranks","#","Default username prefix");

            IS_DB_READ_ONLY =cfg.getBoolean("readOnly", "general", false, "Should the DB be read-only?");

            cfg.save();

        }catch(Exception e){
            e.printStackTrace();
        }
    }


    public static void updateNickname(String player, String newNick) {
        List<String> strings=new ArrayList<>();
        nicks.put(player, newNick);
        Property prop = cfg.get(

                "ranks",
                "ranks",
                new String[0],
                "Form: player=rank"
        );
        for(String name:nicks.keySet()){
            strings.add(name+"="+nicks.get(name));
        }
        prop.set(strings.toArray(new String[0]));
        cfg.save();
    }


    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent pie) {
        cfg=new Configuration(pie.getSuggestedConfigurationFile());
        setupNicks();
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
        String username;
        if(nicks.containsKey(event.username)){
            username=nicks.get(event.username);
        } else {
            username=defaultPrefix+event.username+defaultSuffix;
        }

        String text = event.message;
        if ("pause".equals(text)) {
            if (SqlLogger.pause(event.player))
                setChatMessage(event, "\u00A74" + event.message, username);
        }
        if ("resume".equals(text)) {
            if (SqlLogger.resume(event.player))
                setChatMessage(event, "\u00A7A" + event.message, username);
        }
        if ("go".equals(text)) {
            tryToStartMatch(event, false);
            setChatMessage(event, "\u00A7A"+event.message, username);
        }
        if ("ready".equals(text)) {
            broadcastMessage(SqlLogger.getCurrentGameGuess());
        }
        if ("gg".equals(text)){
            setChatMessage(event, "\u00A75" + event.message, username);
            broadcastMessage("\u00A76Please end the match - use /win (if you won) or /lose (otherwise)");
        }
        setChatMessage(event, text, username);
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

    public static IChatComponent generateUsernameComponent(EntityPlayer p, String nickname) {
        ChatComponentText chatcomponenttext = new ChatComponentText(ScorePlayerTeam.formatPlayerName(p.getTeam(), nickname));
        chatcomponenttext.getChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg " + p.getCommandSenderName() + " "));
        return chatcomponenttext;
    }

    public static void setChatMessage(ServerChatEvent event, String newText, String nick) {
        event.component = new ChatComponentTranslation("chat.type.text",
                generateUsernameComponent(event.player, nick),
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
        /*
        if (logEvent(event)) {
            ItemStack hand=event.getPlayer().getItemInUse();
            SqlLogger.addEvent(
                    event.getPlayer(),
                    "break",
                    event.block.getLocalizedName(),
                    hand==null ? "null" : hand.getUnlocalizedName());
        }
        */
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