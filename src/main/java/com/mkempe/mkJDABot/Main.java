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
    private static Scanner scanner = new Scanner(System.in);
    private static Bot bot;
    private static Settings settings;

    public static void main(String[] args) {

        Database database = Database.getInstance();
        database.connect();

        settings = Settings.getInstance();

        String apiKey = settings.get(Settings.API_KEY);

        if (apiKey == null) {
            queryAPIKey();
        }

        bot = new Bot();

        boolean notRunning = true;
        while (notRunning) {
            try {
                bot.run();
                notRunning = false;
            } catch (InvalidTokenException e) {
                System.out.println("Invalid API Key");
                queryAPIKey();
            }
        }

        boolean running = true;
        while (running) {
            String input = scanner.nextLine();

            switch (input.toLowerCase()) {
                case "help" -> System.out.println("Commands:\n" +
                        "start - Start bot\n" +
                        "stop  - Stop bot\n" +
                        "key   - Set api key\n" +
                        "quit  - Stop bot and exit program\n"
                );
                case "key" -> queryAPIKey();
                case "start" -> bot.run();
                case "stop" -> bot.stop();
                case "exit", "quit" -> {
                    bot.stop();
                    running = false;
                }
            }
        }


        database.close();
    }

    private static void queryAPIKey() {
        System.out.print("API Key: ");
        settings.set(Settings.API_KEY, scanner.nextLine());
    }
}
