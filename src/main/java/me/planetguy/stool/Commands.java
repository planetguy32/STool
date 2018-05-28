package me.planetguy.stool;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Commands {

    private static int parseOrDefault(String text, int defaultVal) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    public static List<ICommand> createCommands() {
        List<ICommand> cmds = new ArrayList<>();
        cmds.add(new QuickCommand(
                "win",
                "Stats: Ends the current game, and notes that you claim victory.",
                new ICommandlet() {
                    @Override
                    public String processCommand(ICommandSender ics, String[] args) throws CommandException {
                        System.out.println("Win");
                        if (ics instanceof EntityPlayer)
                            SqlLogger.ACTIVE_LOGGER.win((EntityPlayer) ics);
                        return null;
                    }
                }
        ));

        cmds.add(new QuickCommand(
                "lose",
                "Stats: Ends the current game, and notes that you claim defeat.",
                new ICommandlet() {
                    @Override
                    public String processCommand(ICommandSender ics, String[] args) throws CommandException {
                        if (ics instanceof EntityPlayer)
                            SqlLogger.ACTIVE_LOGGER.lose((EntityPlayer) ics);
                        return null;
                    }
                }
        ));

        cmds.add(new QuickCommand(
                "stalemate",
                "Stats: Ends the current game, and notes the game as a draw",
                new ICommandlet() {
                    @Override
                    public String processCommand(ICommandSender ics, String[] args) throws CommandException {
                        SqlLogger.ACTIVE_LOGGER.stalemate((EntityPlayer) ics);
                        return null;
                    }
                }
        ));

        cmds.add(new QuickCommand(
                "invalid",
                "Stats: Ends the current game, and notes the game as invalid",
                new ICommandlet() {
                    @Override
                    public String processCommand(ICommandSender ics, String[] args) throws CommandException {
                        SqlLogger.ACTIVE_LOGGER.invalid((EntityPlayer) ics);
                        return null;
                    }
                }
        ));

        cmds.add(new QuickCommand(
                "smaap",
                "/smaap <player> <1|2> <map name>: Sets active map and adds player to a team",
                new ICommandlet() {
                    @Override
                    public String processCommand(ICommandSender ics, String[] args) throws CommandException {
                        System.out.println(args.length + " args");
                        if (args.length == 3) {
                            String s = CommandBase.getPlayer(ics, args[0]).getDisplayName();
                            System.out.print(s);
                            SqlLogger.ACTIVE_LOGGER.assignPlayerToTeam(
                                    s,
                                    Integer.parseInt(args[1]),
                                    args[2]);
                            return "Looks good";
                        }
                        return "Needs 3 arguments: ";
                    }
                },
                2
        ));

        addRadiusChecker(cmds,
                "hmp",
                "/hmp <radius>: Tells you how many times a player was present within the radius",
                "pos",
                " heartbeats within "
        );

        addRadiusChecker(cmds,
                "hmd",
                "/hmd <radius>: Tells you how many deaths have happened within the radius",
                "death",
                " deaths within "
        );

        addRadiusChecker(cmds,
                "hmk",
                "/hmk <radius>: Tells you how many kills have been dealt by someone within the radius",
                "kill",
                " kills within "
        );

        addRadiusChecker(cmds,
                "hmt",
                "/hmt <radius>: Tells you how many times damage was taken within the radius",
                "takedmg",
                " times within "
        );

        addRadiusChecker(cmds,
                "hmi",
                "/hmi <radius>: Tells you how many times damage was inflicted from within the radius",
                "dealdmg",
                " times within "
        );

        cmds.add(new QuickCommand(
                "map",
                "/map <mapname>: Sets the name of the next map, for stats purposes",
                new ICommandlet() {
                    @Override
                    public String processCommand(ICommandSender ics, String[] args) throws CommandException {
                        if (args.length == 1) {
                            SqlLogger.ACTIVE_LOGGER.setMap(args[0]);
                        }
                        return "\u00A76Set map to " + args[0];
                    }
                }
        ));

        cmds.add(new QuickCommand(
                "team1",
                "/team1 players...: Adds all the players to the game",
                new ICommandlet() {
                    @Override
                    public String processCommand(ICommandSender ics, String[] args) throws CommandException {
                        try {
                            return "\u00A76Team 1 is now " + SqlLogger.ACTIVE_LOGGER.adjustTeam(1, args, (EntityPlayer) ics);
                        }catch(SQLException e){
                            return "\u00A76Teams may be corrupted!";
                        }
                    }
                }
        ));

        cmds.add(new QuickCommand(
                "team2",
                "/team2 players...: Sets team 2 to the typed list of players",
                new ICommandlet() {
                    @Override
                    public String processCommand(ICommandSender ics, String[] args) throws CommandException {
                        try {
                            return "\u00A76Team 2 is now " + SqlLogger.ACTIVE_LOGGER.adjustTeam(2, args, (EntityPlayer) ics);
                        } catch (SQLException e) {
                            return "\u00A76Teams may be corrupted!";
                        }
                    }
                }
        ));

        cmds.add(new QuickCommand(
                "unpause",
                "/unpause: Forces the game to no longer be paused",
                new ICommandlet() {
                    @Override
                    public String processCommand(ICommandSender ics, String[] args) throws CommandException {
                        if(ics instanceof EntityPlayer){
                            SqlLogger.ACTIVE_LOGGER.forceResume((EntityPlayer) ics);
                        }
                        return "\u00A76Force unpaused the game";
                    }
                }
        ));

        /*
        cmds.add(new QuickCommand(
                "nick",
                "/nick <nickname>: Sets your own nickname",
                new ICommandlet() {
                    @Override
                    public String processCommand(ICommandSender ics, String[] args) throws CommandException {
                        String nick = StringEscapeUtils.unescapeJava(String.join(" ", args));
                        Stool.updateNickname(ics.getCommandSenderName(), nick);
                        return "Nickname set to "+Stool.nicks.get(ics.getCommandSenderName());
                    }
                }
        ));
        */

        return cmds;
    }

    private static void addRadiusChecker(List<ICommand> cmds, final String name,
                                         final String message, final String evtype, final String s1) {
        cmds.add(new QuickCommand(
                name,
                message,
                new ICommandlet() {
                    @Override
                    public String processCommand(ICommandSender ics, String[] args) throws CommandException {
                        if (ics instanceof EntityPlayer) {
                            EntityPlayer p = (EntityPlayer) ics;
                            int radius = 10;
                            if (args.length == 1)
                                radius = parseOrDefault(args[0], radius);
                            int i = SqlLogger.ACTIVE_LOGGER.countEventsInBounds(evtype, p.boundingBox.expand(radius, radius, radius));
                            return i + s1 + radius + " blocks";
                        }
                        return "NYI for non-players";
                    }
                }
        ));
    }
}
