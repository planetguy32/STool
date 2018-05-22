package me.planetguy.ylcmj;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;

public interface ICommandlet {

    void processCommand(ICommandSender ics, String[] args) throws CommandException;

}
