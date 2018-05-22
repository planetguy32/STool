package me.planetguy.ylcmj;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;

public class QuickCommand extends CommandBase {

    private final String name;
    private final String usage;
    private final ICommandlet impl;
    private final int permLevel;

    public QuickCommand(String name, String usage, ICommandlet impl){
        this.name = name;
        this.usage=usage;
        this.impl=impl;
        permLevel=0;
    }

    public QuickCommand(String name, String usage, ICommandlet impl, int perms){
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
        impl.processCommand(snd, args);
    }
}
