package me.planetguy.ylcmj;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

class CommandReplay extends CommandBase
{

    private static HashMap<String, Iterator> replayRequests=new HashMap<>();

    private static int time=0;

    public static void tickReplays(){
        time++;
        //2 per 3 seconds
        if(time % 20 == 0){
            List<String> deadRequests=new ArrayList<>();
            for(String s:replayRequests.keySet()){
                System.out.println(s);
                EntityPlayerMP player=MinecraftServer.getServer().getConfigurationManager().func_152612_a(s);
                System.out.println(player);
                Iterator<Object> iter=replayRequests.get(s);
                System.out.println(iter);
                if(player != null && iter != null && iter.hasNext()) {
                    Object o=iter.next();
                    if(o instanceof Vec3) {
                        Vec3 newPos = (Vec3) o;
                        player.setPositionAndUpdate(newPos.xCoord, newPos.yCoord, newPos.zCoord);
                    } else if(o instanceof String){
                        player.addChatMessage(new ChatComponentText("Event: "+o));
                    }
                } else {
                    deadRequests.add(s);
                }
            }
            for(String s:deadRequests)
                replayRequests.remove(s);
        }
    }

    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getCommandName() {
        return "sreplay";
    }

    @Override
    public String getCommandUsage(ICommandSender p_71518_1_) {
        return "/sreplay <match> <player>: Teleports you through the positions the named player stood in";
    }

    public List addTabCompletionOptions(ICommandSender sender, String[] args)
    {
        if(args.length == 1)
            return getListOfStringsMatchingLastWord(
                    args,
                    SqlLogger.getMatchIds().toArray(new String[0]));
        else if(args.length == 2){
            return getListOfStringsMatchingLastWord(args, MinecraftServer.getServer().getAllUsernames());
        } else{
            return new ArrayList<>();
        }
    }

    public boolean isUsernameIndex(String[] args, int i)
    {
        return i == 1;
    }

    @Override
    public void processCommand(ICommandSender ics, String[] args) throws CommandException {
        if(args.length==2 && ics instanceof EntityPlayerMP){
            replayRequests.put(((EntityPlayerMP) ics).getDisplayName(),
                    SqlLogger.getPosEvents(Long.parseLong(args[0]), args[1]).iterator());
            System.out.println(replayRequests);
        }
    }
}
