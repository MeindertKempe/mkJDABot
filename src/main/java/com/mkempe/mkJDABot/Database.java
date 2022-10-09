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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.Channel;

import javax.sql.DataSource;
import java.sql.*;

public class Database {
    private static final String NAME = "mkJDABot.db";
    private static final Database INSTANCE = new Database();
    private HikariDataSource dataSource;

    private Database() {

    }

    public String getSetting(String setting) {
        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT value FROM settings WHERE name = ?");
            statement.setString(1, setting);

            return statement.executeQuery().getString("value");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setSetting(String setting, String value) {
        try (Connection connection = dataSource.getConnection()) {
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

    public void initSettings() {
        try (Connection connection = dataSource.getConnection()) {
            Statement statement = connection.createStatement();
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS settings(
                    name TEXT PRIMARY KEY NOT NULL,
                    value TEXT NOT NULL
                    );
                    """
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void initScheduler() {
        try (Connection connection = dataSource.getConnection()) {
            Statement statement = connection.createStatement();
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS scheduled_tasks
                    (
                        task_name            TEXT     NOT NULL,
                        task_instance        TEXT     NOT NULL,
                        task_data            BLOB,
                        execution_time       DATETIME NOT NULL,
                        picked               BOOLEAN  NOT NULL,
                        picked_by            TEXT,
                        last_success         DATETIME,
                        last_failure         DATETIME,
                        consecutive_failures INTEGER,
                        last_heartbeat       DATETIME,
                        version              INTEGER  NOT NULL,
                        PRIMARY KEY (task_name, task_instance)
                    );

                    CREATE INDEX execution_time_idx ON scheduled_tasks (execution_time);
                    CREATE INDEX last_heartbeat_idx ON scheduled_tasks (last_heartbeat);
                    """
            );

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS notifications
                    (
                        guild        TEXT NOT NULL,
                        channel      TEXT NOT NULL,
                        channel_type INTEGER NOT NULL,
                        name         TEXT NOT NULL,
                        role         TEXT,
                        message      TEXT NOT NULL,
                        PRIMARY KEY (guild, channel, name)
                    );
                    """
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public void connect() {
        if (dataSource != null) throw new IllegalStateException("Already connected");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + NAME);
//        config.setDataSourceClassName("org.sqlite.SQLiteDataSource");
        config.setMaximumPoolSize(1);
        this.dataSource = new HikariDataSource(config);

    }

    public void close() {
        if (dataSource == null) throw new IllegalStateException("Not connected");

        dataSource.close();
        dataSource = null;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public static Database getInstance() {
        return INSTANCE;
    }

    public void insertSchedule(NotificationMessage message) {
        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO notifications
                    (guild, channel, channel_type, name, role, message)
                    VALUES (?, ?, ?, ?, ?, ?);
                    """
            );
            statement.setString(1, message.guild());
            statement.setString(2, message.channel());
            statement.setInt(3, message.channelType());
            statement.setString(4, message.name());
            statement.setString(5, message.role());
            statement.setString(6, message.message());

            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteSchedule(String guild, String channel, String name) {
        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM notifications WHERE
                    guild = ? AND
                    channel_type = ? AND
                    name = ?;
                    """
            );
            statement.setString(1, guild);
            statement.setString(2, channel);
            statement.setString(3, name);

            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public NotificationMessage getSchedule(String guild, String channel, String name) {
        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("""
                    SELECT * FROM notifications WHERE
                    guild = ? AND
                    channel = ? AND
                    name = ?;
                    """
            );
            statement.setString(1, guild);
            statement.setString(2, channel);
            statement.setString(3, name);

            ResultSet results = statement.executeQuery();

            if (!results.next()) return null;

            return new NotificationMessage(
                    results.getString("guild"),
                    results.getString("channel"),
                    results.getInt("channel_type"),
                    results.getString("name"),
                    results.getString("role"),
                    results.getString("message")

            );

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
