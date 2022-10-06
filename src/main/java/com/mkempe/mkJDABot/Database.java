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

import java.sql.*;

public class Database {
    private static final String NAME = "mkJDABot.db";
    private static final Database INSTANCE = new Database();
    private Connection connection;

    private Database() {

    }

    public synchronized String getSetting(String setting) {
        try {
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT value FROM settings WHERE name = ?");
            statement.setString(1, setting);

            return statement.executeQuery().getString("value");

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void setSetting(String setting, String value) {
        try {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO settings (name, value) VALUES (?, ?)" +
                            "ON CONFLICT (name) DO UPDATE SET value = ?"
            );
            statement.setString(1, setting);
            statement.setString(2, value);
            statement.setString(3, value);

            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void initSettings() {
        try {
            Statement statement = connection.createStatement();
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS settings(" +
                            "name TEXT PRIMARY KEY NOT NULL," +
                            "value TEXT NOT NULL" +
                            ");"
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void connect() {
        try {
            if (connection != null) throw new IllegalStateException("Already connected");

            connection = DriverManager.getConnection("jdbc:sqlite:" + NAME);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void close() {
        if (connection == null) throw new IllegalStateException("Not connected");

        try {
            connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        connection = null;
    }

    public static Database getInstance() {
        return INSTANCE;
    }

}
