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
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

public class CommandListener extends ListenerAdapter {
    private final Logger logger = LoggerFactory.getLogger(CommandListener.class);

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
//TODO find method to stop tasks (cancel doesn't work during task execution).
//                                new OptionData(OptionType.INTEGER, "count", "Number of times to repeat", false, false)
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
        list.add(
                Commands.slash("reminder", "Schedule one time reminder")
                        .addOptions(
                                new OptionData(OptionType.STRING, "message", "Message to send", true, false),
                                new OptionData(OptionType.CHANNEL, "channel", "Channel to notify", true, false),
                                new OptionData(OptionType.STRING, "name", "Name of notification", true, false),
                                new OptionData(OptionType.ROLE, "role", "Role to notify", true, false),
                                new OptionData(OptionType.INTEGER, "year", "Year", false, false),
                                new OptionData(OptionType.INTEGER, "month", "Month", false, false),
                                new OptionData(OptionType.INTEGER, "day", "Day", false, false),
                                new OptionData(OptionType.INTEGER, "hour", "Day", false, false),
                                new OptionData(OptionType.INTEGER, "minute", "Day", false, false)
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
            case "reminder" -> reminderCommand(event);
            default -> unknownCommand(event);
        }
    }

    private void showscheduledCommand(SlashCommandInteractionEvent event) {
        event.deferReply().setEphemeral(true).queue();

        Guild guild;
        if ((guild = event.getGuild()) == null) {
            event.getHook().sendMessage("Failed: command only works on servers").queue();
            return;
        }

        String channel = null;
        String name = null;

        for (OptionMapping option : event.getOptions()) {
            switch (option.getName()) {
                case "channel" -> channel = option.getAsChannel().getId();
                case "name" -> name = option.getAsString();
            }
        }

        ArrayList<NotificationMessage> messages = Database.getInstance().getSchedules(guild.getId(), channel, name);
        StringBuilder builder = new StringBuilder();
        if (messages.size() == 0) {
            event.getHook().sendMessage("No notifications scheduled").queue();
            return;
        }

        for (NotificationMessage message : messages) {
            builder.append("Name:      ").append(message.name()).append("\n");
            Channel c = Bot.getInstance().getJDA().getChannelById(MessageChannel.class, message.channel());
            Role r = Bot.getInstance().getJDA().getRoleById(message.role());

            builder.append("Channel:   ");
            if (c == null) builder.append(message.channel());
            else builder.append(c.getAsMention());
            builder.append("\n");

            builder.append("Role:      ");
            if (r == null) builder.append(message.role());
            else builder.append(r.getAsMention());
            builder.append("\n");

            builder.append("Scheduled: ").append(message.schedule()).append("\n");
            if (message.countMax() != NotificationMessage.COUNTNONE)
                builder.append("Count:     ").append(message.count()).append("/").append(message.countMax());
            builder.append("Message:   ").append(message.message().replace("\\n", "\n")).append("\n\n");
        }
        event.getHook().sendMessage(builder.toString()).queue();
    }

    private void scheduleCommand(SlashCommandInteractionEvent event) {
        event.deferReply().setEphemeral(true).queue();

        Guild guild;
        if ((guild = event.getGuild()) == null) {
            event.getHook().sendMessage("Failed: command only works on servers").queue();
            return;
        }

        Channel channel = null;
        ChannelType channelType = null;
        String name = null;
        Role role = null;
        String message = null;
        String cron = null;
        int countMax = NotificationMessage.COUNTNONE;

        for (OptionMapping option : event.getOptions()) {
            switch (option.getName()) {
                case "message" -> message = option.getAsString();
                case "channel" -> {
                    channel = option.getAsChannel();
                    channelType = channel.getType();
                }
                case "name" -> name = option.getAsString();
                case "role" -> role = option.getAsRole();
                case "cron" -> cron = option.getAsString();
//                case "count" -> countMax = option.getAsInt();
            }
        }

        if (channel == null || channelType == null || name == null || role == null || message == null || cron == null) {
            event.getHook().sendMessage("Failed: missing options").queue();
            return;
        }

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

        Database.getInstance().insertSchedule(new NotificationMessage(guild.getId(), channel.getId(), channelType.getId(), name, role.getId(), message, cron, countMax, 0));

        Bot.getInstance().getScheduler().schedule(NotificationMessage.notifyTask.schedulableInstance(
                guild.getId() + ":" + channel.getId() + ":" + name,
                new PersistentCronSchedule(cron)
        ));

        event.getHook().sendMessage(
                "Set notification\n" +
                        "Channel: " + channel.getAsMention() + "\n" +
                        "Role:    " + role.getAsMention() + "\n" +
                        "Message: " + message + "\n" +
                        "Cron:    " + cron +
                        (countMax == NotificationMessage.COUNTNONE ? "" : "\nCount:   " + countMax)
        ).queue();
    }

    private void unscheduleCommand(SlashCommandInteractionEvent event) {
        event.deferReply().setEphemeral(true).queue();

        Guild guild;

        if ((guild = event.getGuild()) == null) {
            event.getHook().sendMessage("Failed: command only works on servers").queue();
            return;
        }

        Channel channel = null;
        String name = null;

        for (OptionMapping option : event.getOptions()) {
            switch (option.getName()) {
                case "channel" -> channel = option.getAsChannel();
                case "name" -> name = option.getAsString();
            }
        }

        if (channel == null || name == null) {
            event.getHook().sendMessage("Failed: missing option").queue();
            return;
        }

        if ((Database.getInstance().getSchedule(guild.getId(), channel.getId(), name) == null)) {
            event.getHook().sendMessage("Failed: invalid name or channel").queue();
            return;
        }

        Database.getInstance().deleteSchedule(guild.getId(), channel.getId(), name);

        try {
            Bot.getInstance().getScheduler().cancel(TaskInstanceId.of(
                    NotificationMessage.notifyTask.getName(),
                    guild.getId() + ":" + channel.getId() + ":" + name
            ));
        } catch (TaskInstanceNotFoundException e) {
            event.getHook().sendMessage("Warning: Possibly failed to stop schedule\n" +
                    "Removed notification: " + name + "\n" +
                    "From channel: " + channel.getAsMention()).queue();
            return;
        }

        event.getHook().sendMessage("Removed notification: " + name + "\n" +
                "From channel: " + channel.getAsMention()).queue();
    }

    private void reminderCommand(SlashCommandInteractionEvent event) {
        event.deferReply().setEphemeral(true).queue();

        Guild guild;
        if ((guild = event.getGuild()) == null) {
            event.getHook().sendMessage("Failed: command only works on servers").queue();
            return;
        }

        Channel channel = null;
        ChannelType channelType = null;
        String name = null;
        Role role = null;
        String message = null;
        ZoneId zone = null;

        Integer year = null;
        Integer month = null;
        Integer day = null;
        Integer hour = null;
        Integer minute = null;

        for (OptionMapping option : event.getOptions()) {
            switch (option.getName()) {
                case "channel" -> {
                    channel = option.getAsChannel();
                    channelType = channel.getType();
                }
                case "name" -> name = option.getAsString();
                case "role" -> role = option.getAsRole();
                case "message" -> message = option.getAsString();
                case "year" -> year = option.getAsInt();
                case "month" -> month = option.getAsInt();
                case "day" -> day = option.getAsInt();
                case "hour" -> hour = option.getAsInt();
                case "minute" -> minute = option.getAsInt();
            }
        }

        if (channel == null || channelType == null || name == null || role == null || message == null) {
            event.getHook().sendMessage("Failed: missing option").queue();
            return;
        }

        zone = ZoneId.systemDefault();

        ZonedDateTime timeNow = ZonedDateTime.now(zone);
        year = year == null ? timeNow.getYear() : year;
        month = month == null ? timeNow.getMonthValue() : month;
        day = day == null ? timeNow.getDayOfMonth() : day;
        hour = hour == null ? timeNow.getHour() : hour;
        minute = minute == null ? timeNow.getMinute() : minute;

        if (Database.getInstance().getSchedule(guild.getId(), channel.getId(), name) != null) {
            event.getHook().sendMessage("Failed: notification task already exists").queue();
            return;
        }

        if (Bot.getInstance().getJDA().getChannelById(MessageChannel.class, channel.getId()) == null) {
            event.getHook().sendMessage("Failed: select text channel").queue();
            return;
        }

        ZonedDateTime time;
        try {
            time = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone);
        } catch (DateTimeException e) {
            event.getHook().sendMessage("Failed: invalid time").queue();
            return;
        }

        Database.getInstance().insertSchedule(new NotificationMessage(guild.getId(), channel.getId(), channelType.getId(), name, role.getId(), message, time.toString(), NotificationMessage.COUNTNONE, 0));

        Bot.getInstance().getScheduler().schedule(
                NotificationMessage.reminderTask.instance(guild.getId() + ":" + channel.getId() + ":" + name),
                time.toInstant()
        );

        event.getHook().sendMessage(
                "Set notification\n" +
                        "Channel:   " + channel.getAsMention() + "\n" +
                        "Role:      " + role.getAsMention() + "\n" +
                        "Message:   " + message + "\n" +
                        "Scheduled: " + time
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
