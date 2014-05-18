/*
 * Copyright (C) 2014 eccentric_nz
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package me.eccentric_nz.xpkeeper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.util.FileUtil;

/**
 *
 * @author eccentric_nz
 */
public class XPKeeperUUIDConverter {

    private final XPKeeper plugin;
    private final XPKdatabase service = XPKdatabase.getInstance();
    private final Connection connection = service.getConnection();
    private final List<String> players = new ArrayList<String>();

    public XPKeeperUUIDConverter(XPKeeper plugin) {
        this.plugin = plugin;
    }

    public boolean convert() {
        // get if server is online-mode=true (as required to retrieve correct player UUIDs)
        if (!getOnlineMode()) {
            System.out.println(ChatColor.RED + "UUID conversion requires the server online-mode to be TRUE!");
            return false;
        }
        // backup database
        System.out.println("[XPKeeper] Backing up XPKeeper database...");
        File oldFile = new File(plugin.getDataFolder() + File.separator + "XPKeeper.db");
        File newFile = new File(plugin.getDataFolder() + File.separator + "XPKeeper_" + System.currentTimeMillis() + ".db");
        FileUtil.copy(oldFile, newFile);
        // get all TARDIS owners from database
        Statement statement = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        String query = "SELECT DISTINCT player FROM xpk";
        String inventories_update = "UPDATE xpk SET uuid = ? WHERE player = ?";
        int count = 0;
        try {
            statement = connection.createStatement();
            rs = statement.executeQuery(query);
            if (rs.isBeforeFirst()) {
                while (rs.next()) {
                    if (!rs.getString("player").isEmpty()) {
                        players.add(rs.getString("player"));
                    }
                }
                XPKUUIDFetcher fetcher = new XPKUUIDFetcher(players);
                // get UUIDs
                Map<String, UUID> response = null;
                try {
                    response = fetcher.call();
                } catch (Exception e) {
                    System.err.println("[XPKeeper] Exception while running XPKUUIDFetcher: " + e.getMessage());
                    return false;
                }
                if (response != null) {
                    // update all XPKeeper players to UUIDs
                    ps = connection.prepareStatement(inventories_update);
                    for (Map.Entry<String, UUID> map : response.entrySet()) {
                        ps.setString(1, map.getValue().toString());
                        ps.setString(2, map.getKey());
                        count += ps.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[XPKeeper] ResultSet error for UUID updating! " + e.getMessage());
            return false;
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
                if (statement != null) {
                    statement.close();
                }
                if (ps != null) {
                    ps.close();
                }
            } catch (SQLException e) {
                System.err.println("[XPKeeper] Error closing tardis table! " + e.getMessage());
            }
        }
        System.out.println("[XPKeeper] Converted " + count + " player names to UUIDs.");
        return true;
    }

    /**
     * Gets the server default resource pack. Will use the Minecraft default
     * pack if none is specified. Until Minecraft/Bukkit lets us set the RP back
     * to Default, we'll have to host it on DropBox
     *
     * @return The server specified texture pack.
     */
    public boolean getOnlineMode() {
        FileInputStream in = null;
        try {
            Properties properties = new Properties();
            String path = "server.properties";
            in = new FileInputStream(path);
            properties.load(in);
            String online = properties.getProperty("online-mode");
            return (online != null && online.equalsIgnoreCase("true"));
        } catch (FileNotFoundException ex) {
            System.err.println("[XPKeeper] Could not find server.properties!");
            return false;
        } catch (IOException ex) {
            System.err.println("[XPKeeper] Could not read server.properties!");
            return false;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ex) {
                System.err.println("[XPKeeper] Could not close server.properties!");
            }
        }
    }
}
