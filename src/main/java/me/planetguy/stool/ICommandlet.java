package me.planetguy.stool;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;

public interface ICommandlet {

    String processCommand(ICommandSender ics, String[] args) throws CommandException;

}
