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

package com.mkempe.mkJDABot;

import com.github.kagkarlsson.scheduler.task.helper.RecurringTaskWithPersistentSchedule;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.PersistentCronSchedule;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

public record NotificationMessage(String guild, String channel, int channelType, String name, String role, String message, String cron) {
    public static final RecurringTaskWithPersistentSchedule<PersistentCronSchedule> task = Tasks
            .recurringWithPersistentSchedule("notify", PersistentCronSchedule.class)
            .execute((inst, ctx) -> {
                // Task instance id is in format "guildId:channelId:name"
                String[] instId = inst.getId().split(":", 3);
                String guildId = instId[0];
                String channelId = instId[1];
                String name = instId[2];

                // Retrieve message details from database.
                NotificationMessage message = Database.getInstance().getSchedule(guildId, channelId, name);

                Guild guild;
                MessageChannel channel;
                String text;
                Role role;

                //TODO check for existance of channel (might get exception).
                if ((guild = Bot.getInstance().getJDA().getGuildById(message.guild())) == null)
                    return;
                if ((channel = guild.getChannelById(MessageChannel.class, message.channel())) == null)
                    return;
                if ((role = guild.getRoleById(message.role())) == null)
                    return;
                if ((text = message.message()) == null)
                    return;

                // Allow new lines in message.
                text = text.replace("\\n", "\n");

                channel.sendMessage(role.getAsMention() + " " + text).queue();
    });
}
