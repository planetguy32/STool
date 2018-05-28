package me.planetguy.stool;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

import java.util.List;

public class QuickCommand extends CommandBase {

    private final String name;
    private final String usage;
    private final ICommandlet impl;
    private final int permLevel;

    QuickCommand(String name, String usage, ICommandlet impl){
        this.name = name;
        this.usage=usage;
        this.impl=impl;
        permLevel=0;
    }

    QuickCommand(String name, String usage, ICommandlet impl, int perms){
        this.name = name;
        this.usage=usage;
        this.impl=impl;
        this.permLevel=perms;
    }

    @Override
    public String getCommandName() {
        return name;
    }

    @Override
    public String getCommandUsage(ICommandSender p_71518_1_) {
        return usage;
    }

    @Override
    public void processCommand(ICommandSender snd, String[] args) throws CommandException {
        String s=impl.processCommand(snd, args);
        if(s!=null)
            snd.addChatMessage(new ChatComponentText(s));
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender p_71519_1_) {
        return true;
    }

    //Assume all indexes are usernames
    //Don't push tab or you'll get a username
    //This is a horrible hack
    @Override
    public List addTabCompletionOptions(ICommandSender cs, String[] args) {
        return getListOfStringsMatchingLastWord(args, MinecraftServer.getServer().getAllUsernames());
    }

}
