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

import com.github.kagkarlsson.scheduler.Scheduler;
import com.mkempe.mkJDABot.Listeners.CommandListener;
import com.mkempe.mkJDABot.Listeners.MessageListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class Bot {
    private static final Bot INSTANCE = new Bot();
    private JDA jda;
    private Scheduler scheduler;

    private Bot() {
    }

    public static Bot getInstance() {
        return INSTANCE;
    }

    public void start() {
        if (jda != null) throw new IllegalStateException("Already running");

        System.out.println("Starting bot...");

        JDABuilder builder = JDABuilder.createDefault(Settings.getInstance().get(Settings.API_KEY));
        builder.enableIntents(GatewayIntent.MESSAGE_CONTENT);
        builder.addEventListeners(new MessageListener(), new CommandListener());
        jda = builder.build();

        try {
            jda.awaitReady();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        jda.updateCommands().addCommands(CommandListener.getCommands()).queue();

        Database.getInstance().initScheduler();
        this.scheduler = Scheduler
                .create(Database.getInstance().getDataSource(), NotificationMessage.notifyTask, NotificationMessage.reminderTask)
                .threads(10)
                .build();
        scheduler.start();

        System.out.println("Bot started.");
    }

    public void stop() {
        if (jda == null) throw new IllegalStateException("Not running");

        System.out.println("Stopping bot...");
        scheduler.stop();
        jda.shutdown();
        jda = null;
        System.out.println("Bot stopped.");
    }

    public void restart() {
        stop();
        start();
    }

    public boolean isRunning() {
        return jda != null;
    }

    public JDA getJDA() {
        return jda;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }
}
