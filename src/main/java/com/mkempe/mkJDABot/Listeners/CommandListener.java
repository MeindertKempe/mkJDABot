/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Copyright (c) 2022 Meindert Kempe
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.mkempe.mkJDABot.Listeners;

import com.github.kagkarlsson.scheduler.exceptions.TaskInstanceNotFoundException;
import com.github.kagkarlsson.scheduler.task.TaskInstanceId;
import com.github.kagkarlsson.scheduler.task.schedule.PersistentCronSchedule;
import com.github.kagkarlsson.shaded.cronutils.model.CronType;
import com.github.kagkarlsson.shaded.cronutils.model.definition.CronDefinitionBuilder;
import com.github.kagkarlsson.shaded.cronutils.parser.CronParser;
import com.mkempe.mkJDABot.Bot;
import com.mkempe.mkJDABot.Database;
import com.mkempe.mkJDABot.NotificationMessage;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class CommandListener extends ListenerAdapter {
    Logger logger = LoggerFactory.getLogger(CommandListener.class);

    public static List<CommandData> getCommands() {
        List<CommandData> list = new ArrayList<>();
        list.add(Commands.slash("ping", "Calculate ping of the bot"));
        list.add(
                Commands.slash("schedule", "Schedule sending a message")
                        .addOptions(
                                new OptionData(OptionType.STRING, "message", "Message to send", true, false),
                                new OptionData(OptionType.CHANNEL, "channel", "Channel to notify", true, false),
                                new OptionData(OptionType.STRING, "name", "Name of notification", true, false),
                                new OptionData(OptionType.ROLE, "role", "Role to notify", true, false),
                                new OptionData(OptionType.STRING, "cron", "Cron schedule", true, false)
                        ));
        list.add(
                Commands.slash("unschedule", "Unschedule message")
                        .addOptions(
                                new OptionData(OptionType.CHANNEL, "channel", "Channel of notification", true, false),
                                new OptionData(OptionType.STRING, "name", "Name of notification", true, false)
                        ));
        list.add(
                Commands.slash("showscheduled", "List scheduled messages")
                        .addOptions(
                                new OptionData(OptionType.CHANNEL, "channel", "Channel of notification", false, false),
                                new OptionData(OptionType.STRING, "name", "Name of notification", false, false)
                        ));
        return list;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "ping" -> pingCommand(event);
            case "schedule" -> scheduleCommand(event);
            case "unschedule" -> unscheduleCommand(event);
            case "showscheduled" -> showscheduledCommand(event);
            default -> unknownCommand(event);
        }
    }

    private void showscheduledCommand(SlashCommandInteractionEvent event) {
        event.deferReply().setEphemeral(true).queue();

        if (event.getGuild() == null) {
            event.getHook().sendMessage("Error: not a server").queue();
            return;
        }

        String guild = event.getGuild().getId();
        String channel = null;
        String name = null;

        if (event.getOption("channel") != null)
            channel = event.getOption("channel").getAsChannel().getId();
        if (event.getOption("name") != null)
            name = event.getOption("name").getAsString();

        ArrayList<NotificationMessage> messages = Database.getInstance().getSchedules(guild, channel, name);
        StringBuilder builder = new StringBuilder();
        if (messages.size() == 0) {
            event.getHook().sendMessage("No notifications scheduled").queue();
            return;
        }

        for (NotificationMessage message : messages) {
            builder.append("Name:    ").append(message.name()).append("\n");
            Channel c = Bot.getInstance().getJDA().getChannelById(MessageChannel.class, message.channel());
            Role r = Bot.getInstance().getJDA().getRoleById(message.role());

            builder.append("Channel: ");
            if (c == null)
                builder.append(message.channel());
            else
                builder.append(c.getAsMention());

            builder.append("\n");


            builder.append("Role:    ");
            if (r == null)
                builder.append(message.role());
            else
                builder.append(r.getAsMention());

            builder.append("\n");

            builder.append("Cron:    ").append(message.cron()).append("\n")
                    .append("Message: ").append(message.message().replace("\\n", "\n")).append("\n\n");
        }
        event.getHook().sendMessage(builder.toString()).queue();
    }

    private void unscheduleCommand(SlashCommandInteractionEvent event) {
        event.deferReply().setEphemeral(true).queue();

        Guild guild = event.getGuild();
        Channel channel;
        String name;

        try {
            channel = event.getOption("channel").getAsChannel();
            name = event.getOption("name").getAsString();

            if ((Database.getInstance().getSchedule(guild.getId(), channel.getId(), name) == null))
                event.getHook().sendMessage("Failed: invalid name or channel").queue();

            Database.getInstance().deleteSchedule(guild.getId(), channel.getId(), name);
        } catch (NullPointerException e) {
            event.getHook().sendMessage("Failed: missing option").queue();
            return;
        }

        try {
            Bot.getInstance().getScheduler().cancel(TaskInstanceId.of(
                    NotificationMessage.task.getName(),
                    guild.getId() + ":" + channel.getId() + ":" + name
            ));
        } catch (TaskInstanceNotFoundException e) {
            event.getHook().sendMessage("Possibly failed to stop schedule").queue();
        }

        event.getHook().sendMessage("Removed notification: " + name + "\n" +
                "From channel: " + channel.getAsMention()).queue();
    }

    private void scheduleCommand(SlashCommandInteractionEvent event) {
        event.deferReply().setEphemeral(true).queue();

        Guild guild = event.getGuild();
        Channel channel;
        ChannelType channelType;
        String name;
        Role role;
        String message;
        String cron;

        try {
            channel = event.getOption("channel").getAsChannel();
            channelType = channel.getType();
            name = event.getOption("name").getAsString();
            role = event.getOption("role").getAsRole();
            message = event.getOption("message").getAsString();
            cron = event.getOption("cron").getAsString();

            if (Database.getInstance().getSchedule(guild.getId(), channel.getId(), name) != null) {
                event.getHook().sendMessage("Failed: notification task already exists").queue();
                return;
            }

            if (Bot.getInstance().getJDA().getChannelById(MessageChannel.class, channel.getId()) == null) {
                event.getHook().sendMessage("Failed: select text channel").queue();
                return;
            }

            try {
                CronParser parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.SPRING));
                parser.parse(cron);
            } catch (IllegalArgumentException e) {
                event.getHook().sendMessage("Failed: invalid cron syntax").queue();
                return;
            }

            Database.getInstance().insertSchedule(new NotificationMessage(guild.getId(), channel.getId(), channelType.getId(), name, role.getId(), message, cron));
        } catch (NullPointerException e) {
            event.getHook().sendMessage("Failed: missing option").queue();
            return;
        }


        Bot.getInstance().getScheduler().schedule(NotificationMessage.task.schedulableInstance(
                guild.getId() + ":" + channel.getId() + ":" + name,
                new PersistentCronSchedule(cron)
        ));

        event.getHook().sendMessage(
                "Set notification\n" +
                        "Channel: " + channel.getAsMention() + "\n" +
                        "Role:    " + role.getAsMention() + "\n" +
                        "Message: " + message + "\n" +
                        "Cron:    " + cron
        ).queue();
    }

    private void unknownCommand(SlashCommandInteractionEvent event) {
        event.reply("Unknown command").setEphemeral(true).queue();
    }

    private void pingCommand(SlashCommandInteractionEvent event) {
        long time = System.currentTimeMillis();
        event.reply("Pong!").setEphemeral(true) // reply or acknowledge
                .flatMap(v ->
                        event.getHook().editOriginalFormat("Pong: %d ms", System.currentTimeMillis() - time) // then edit original
                ).queue(); // Queue both reply and edit
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteractionEvent event) {
    }
}
