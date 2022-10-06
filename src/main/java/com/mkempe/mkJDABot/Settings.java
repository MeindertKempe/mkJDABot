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

public class Settings {

    public static final String API_KEY = "api_key";

    private static final Settings INSTANCE = new Settings();
    private final Database database;

    private Settings() {
        database = Database.getInstance();
        database.initSettings();
    }

    public String get(String setting) {
        return database.getSetting(setting);
    }

    public void set(String setting, String value) {
        database.setSetting(setting, value);
    }

    public static Settings getInstance() {
        return INSTANCE;
    }

}
