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


import net.dv8tion.jda.api.exceptions.InvalidTokenException;

import java.util.Scanner;

public class Main {
    private static final Scanner scanner = new Scanner(System.in);
    private static Bot bot;
    private static Settings settings;
    private static Database database;

    public static void main(String[] args) {

        database = Database.getInstance();
        bot = Bot.getInstance();

        database.connect();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down...");
            if (bot.isRunning())
                bot.stop();

            database.close();
        }));

        settings = Settings.getInstance();

        String apiKey = settings.get(Settings.API_KEY);

        if (apiKey == null) {
            queryAPIKey();
        }


        boolean notRunning = true;
        while (notRunning) {
            try {
                bot.start();
                notRunning = false;
            } catch (InvalidTokenException e) {
                System.out.println("Invalid API Key");
                queryAPIKey();
            }
        }

        for (; ; ) {
            System.out.print("> ");
            String input = scanner.nextLine();

            switch (input.toLowerCase()) {
                case "help" -> System.out.println("""
                        Commands:
                        start   - Start bot
                        stop    - Stop bot
                        restart - Restart bot
                        key     - Set api key
                        quit    - Stop bot and exit program
                        """
                );
                case "start" -> bot.start();
                case "stop" -> bot.stop();
                case "restart" -> bot.restart();
                case "key" -> queryAPIKey();
                case "exit", "quit" -> System.exit(0);
                default -> System.out.println("Unknown command, try 'help'");
            }
        }
    }

    private static void queryAPIKey() {
        System.out.print("API Key: ");
        settings.set(Settings.API_KEY, scanner.nextLine());
    }
}
