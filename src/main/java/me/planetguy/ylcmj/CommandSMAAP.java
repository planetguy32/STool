package me.planetguy.ylcmj;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;

class CommandSMAAP extends CommandBase
{
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getCommandName() {
        return "smaap";
    }

    @Override
    public String getCommandUsage(ICommandSender p_71518_1_) {
        return "/smaap <1|2> <map name>: Sets active map and adds player to a team";
    }

    @Override
    public void processCommand(ICommandSender ics, String[] args) throws CommandException {
        if(args.length == 3) {
            String s=getPlayer(ics, args[0]).getDisplayName();
            System.out.print(s);
            SqlLogger.assignPlayerToTeam(
                    s,
                    Integer.parseInt(args[1]),
                    args[2]);
        }
    }
}
