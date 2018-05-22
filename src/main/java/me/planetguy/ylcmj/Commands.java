package me.planetguy.ylcmj;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;

import java.util.ArrayList;
import java.util.List;

public class Commands {

    public List<ICommand> createCommands(){
        List<ICommand> cmds=new ArrayList<>();
        cmds.add(new QuickCommand(
                "win",
                "Stats: Ends the current game, and notes that you claim victory.",
                new ICommandlet() {
                    @Override
                    public void processCommand(ICommandSender ics, String[] args) throws CommandException {
                        if(ics instanceof EntityPlayer)
                            SqlLogger.win((EntityPlayer)ics);
                    }
                }
        ));

        cmds.add(new QuickCommand(
                "lose",
                "Stats: Ends the current game, and notes that you claim defeat.",
                new ICommandlet() {
                    @Override
                    public void processCommand(ICommandSender ics, String[] args) throws CommandException {
                        if(ics instanceof EntityPlayer)
                            SqlLogger.lose((EntityPlayer)ics);
                    }
                }
        ));

        cmds.add(new QuickCommand(
                "smaap",
                "/smaap <1|2> <map name>: Sets active map and adds player to a team",
                new ICommandlet() {
                    @Override
                    public void processCommand(ICommandSender ics, String[] args) throws CommandException {
                        if(args.length == 3) {
                            String s= CommandBase.getPlayer(ics, args[0]).getDisplayName();
                            System.out.print(s);
                            SqlLogger.assignPlayerToTeam(
                                    s,
                                    Integer.parseInt(args[1]),
                                    args[2]);
                        }
                    }
                }
        ));

        cmds.add(new QuickCommand(
                "hmk",
                "/hmk <radius>: Tells you how many kills happened within the radius",
                new ICommandlet() {
                    @Override
                    public void processCommand(ICommandSender ics, String[] args) throws CommandException {
                        if(ics instanceof EntityPlayer) {

                        }
                    }
                }
        ));


        return cmds;
    }
}
