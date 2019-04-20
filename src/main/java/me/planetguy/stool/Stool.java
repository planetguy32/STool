package me.planetguy.stool;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.command.ICommand;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.item.ItemStack;
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
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;
import net.minecraftforge.event.world.BlockEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Mod(modid = Constants.modID, version = Constants.version, acceptableRemoteVersions = "*")
public class Stool {

    private static HashMap<String, String> nicks=new HashMap<>();

    private static Configuration cfg;

    private String defaultPrefix;
    private String defaultSuffix;

    public static boolean IS_IN_EDIT_SERVER_MODE =false;

    private DbPublisher dbPublisher;

    private void setupNicks(){
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
                    "ranks","#","Default username suffix");

            IS_IN_EDIT_SERVER_MODE = cfg.getBoolean("readOnly", "general", false, "Should the DB be read-only?");

            if(IS_IN_EDIT_SERVER_MODE) {
                dbPublisher = new DbPublisher();
            }

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
        if(IS_IN_EDIT_SERVER_MODE)
            return;
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
    }

    @Mod.EventHandler
    public static void startServer(FMLServerStartingEvent event) {
        for(ICommand cmd:
                IS_IN_EDIT_SERVER_MODE
                        ? Commands.createQueryCommands()
                        :Commands.createGameCommands()
                ){
            event.registerServerCommand(cmd);
        }
        SqlLogger.ACTIVE_LOGGER.setupSql();
    }

    @Mod.EventHandler
    public static void shutdownServer(FMLServerStoppingEvent event) {
        SqlLogger.ACTIVE_LOGGER.shutdown();
    }

    @SubscribeEvent
    public void handle(ServerChatEvent event) {
        SqlLogger.ACTIVE_LOGGER.addEvent(event.player, "chat", event.message);

        String username;
        if(nicks.containsKey(event.username)){
            username=nicks.get(event.username);
        } else {
            username=defaultPrefix+event.username+defaultSuffix;
        }

        String text = event.message;
        if ("pause".equals(text)
                && SqlLogger.ACTIVE_LOGGER.pause(event.player)) {
            setChatMessage(event, "\u00A74" + event.message, username);
        } else if ("resume".equals(text)
                && SqlLogger.ACTIVE_LOGGER.resume(event.player)) {
            setChatMessage(event, "\u00A7Aresume", username);
        } else if ("go".equals(text)) {
            if(tryToStartMatch(event))
                setChatMessage(event, "\u00A7Ago", username);
            else
                setChatMessage(event, "go", username);
        } else if ("ready".equals(text) || "status".equals(text)) {
            broadcastMessage(SqlLogger.ACTIVE_LOGGER.getCurrentGameGuess());
            setChatMessage(event, "\u00a72"+text, username);
        } else if ("gg".equals(text)) {
            setChatMessage(event, "\u00A75gg", username);
            broadcastMessage("\u00A76Please end the match - use /win (if you won) or /lose (otherwise)");
        } else {
            setChatMessage(event, text, username);
        }
    }

    private int time = 0;

    @SuppressWarnings("unchecked")
    private static List<EntityPlayerMP> getPlayers(){
        return (List<EntityPlayerMP>) MinecraftServer.getServer()
                        .getConfigurationManager()
                        .playerEntityList;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        if (SqlLogger.ACTIVE_LOGGER.isInGame()) {
            time++;
            //Every 20 ticks, make events
            if (time % 20 == 0) {
                for (EntityPlayerMP p : getPlayers()) {
                    SqlLogger.ACTIVE_LOGGER.addEvent(p, "pos");
                }
            }
            if (time % (20*60) == 0) {
                broadcastMessage("In-game, t+"+(time / (20*60)+" minutes"));
            }
        }
    }

    @SubscribeEvent
    public void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        SqlLogger.ACTIVE_LOGGER.addEvent(event.player, "login");
    }

    @SubscribeEvent
    public void onLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        SqlLogger.ACTIVE_LOGGER.addEvent(event.player, "logout");
    }

    private boolean tryToStartMatch(ServerChatEvent event) {
        @SuppressWarnings("unchecked") List<EntityPlayerMP> players =
                MinecraftServer.getServer()
                        .getConfigurationManager()
                        .playerEntityList;

        List<String> usernames = new ArrayList<>();
        for (EntityPlayerMP player : players) {
            usernames.add(player.getCommandSenderName());
        }

        String message=SqlLogger.ACTIVE_LOGGER.startMatch(usernames);
        broadcastMessage(message);

        return true;
    }

    private static IChatComponent generateUsernameComponent(EntityPlayer p, String nickname) {
        ChatComponentText chatcomponenttext = new ChatComponentText(ScorePlayerTeam.formatPlayerName(p.getTeam(), nickname));
        chatcomponenttext.getChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg " + p.getCommandSenderName() + " "));
        return chatcomponenttext;
    }

    private static void setChatMessage(ServerChatEvent event, String newText, String nick) {
        event.component = new ChatComponentTranslation("chat.type.text",
                generateUsernameComponent(event.player, nick),
                new ChatComponentText(newText));
    }

    public static void broadcastMessage(String message){
        for(EntityPlayerMP player:getPlayers()){
            sendMessage(player,message);
        }
    }

    private static void sendMessage(EntityPlayer player, String message){
        for(String s:message.split("\n")){
            player.addChatMessage(new ChatComponentText(s));
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
            SqlLogger.ACTIVE_LOGGER.addEvent(player, "death", source.damageType, dealerName);
            for(Object playerObj:MinecraftServer.getServer().getConfigurationManager().playerEntityList){
                EntityPlayerMP otherPlayer= (EntityPlayerMP) playerObj;
                if(otherPlayer.getDisplayName().equals(dealerName)) {
                    SqlLogger.ACTIVE_LOGGER.addEvent(otherPlayer, "kill", source.damageType, player.getDisplayName());
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
            SqlLogger.ACTIVE_LOGGER.addEvent(player, "takedmg", source.damageType, dealerName);
            for(Object playerObj:MinecraftServer.getServer().getConfigurationManager().playerEntityList){
                EntityPlayerMP otherPlayer= (EntityPlayerMP) playerObj;
                if(otherPlayer.getDisplayName().equals(dealerName)) {
                    SqlLogger.ACTIVE_LOGGER.addEvent(otherPlayer, "dealdmg", source.damageType, player.getDisplayName());
                    break;
                }
            }
        }
    }

    @SubscribeEvent
    public void handle(PlayerUseItemEvent.Start event) {
        SqlLogger.ACTIVE_LOGGER.addEvent(
                event.entityPlayer,
                "itemStart",
                event.item.getDisplayName(), event.duration + "");

    }

    /* A very spammy event
    @SubscribeEvent
    public void handle(PlayerUseItemEvent.Tick event) {
        SqlLogger.ACTIVE_LOGGER.addEvent(
                event.entityPlayer,
                "itemTick",
                event.item.getDisplayName(), event.duration + "");

    }
    */

    @SubscribeEvent
    public void handle(PlayerUseItemEvent.Stop event) {
        SqlLogger.ACTIVE_LOGGER.addEvent(
                event.entityPlayer,
                "itemStop",
                event.item.getDisplayName(), event.duration + "");

    }

    @SubscribeEvent
    public void handle(PlayerUseItemEvent.Finish event) {
        SqlLogger.ACTIVE_LOGGER.addEvent(
                event.entityPlayer,
                "itemFinish",
                event.item.getDisplayName(), event.duration + "", event.result.getUnlocalizedName());

    }

    @SubscribeEvent
    public void handle(PlayerInteractEvent event) {
        SqlLogger.ACTIVE_LOGGER.addEvent(
                event.entityPlayer,
                "interact",
                event.action.name(), event.world.getBlock(event.x, event.y, event.z).getUnlocalizedName());

    }

    @SubscribeEvent
    public void handle(BlockEvent.BreakEvent event) {
        ItemStack hand = event.getPlayer().getCurrentEquippedItem();
        SqlLogger.ACTIVE_LOGGER.addEvent(
                event.getPlayer(),
                "break",
                event.block.getLocalizedName(),
                hand == null ? "null" : hand.getUnlocalizedName());

    }

    @SubscribeEvent
    public void handle(BlockEvent.PlaceEvent event) {
        SqlLogger.ACTIVE_LOGGER.addEvent(
                event.player,
                "place",
                event.block.getLocalizedName());
    }

}
