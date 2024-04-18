package me.fauxle.cs;

import me.fauxle.cs.commands.CommandCreateShop;
import me.fauxle.cs.commands.CommandViewShop;
import me.fauxle.db.SpigotLoader;
import me.fauxle.db.api.DatabaseAPI;
import me.fauxle.econ.EconomyPlugin;
import me.fauxle.econ.api.Economy;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockVector;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShopPlugin extends JavaPlugin {

    private Economy economy;
    private DatabaseAPI databaseAPI;
    private Map<UUID, Map<BlockVector, ChestShopMeta>> shopCache;

    @Override
    public void onEnable() {
        this.databaseAPI = getPlugin(SpigotLoader.class).getAPI();
        this.economy = getPlugin(EconomyPlugin.class);
        try (Connection connection = databaseAPI.getConnection(); Statement statement = connection.createStatement()) {

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS chest_shops ( " +
                    "`world` INT NOT NULL , " +
                    "`x` INT NOT NULL , " +
                    "`y` INT NOT NULL , " +
                    "`z` INT NOT NULL , " +
                    "`owner` INT NOT NULL , " +
                    "`buyPrice` INT UNSIGNED NULL DEFAULT NULL , " +
                    "`sellPrice` INT UNSIGNED NULL DEFAULT NULL , " +
                    "`item` TEXT NOT NULL , " +
                    "PRIMARY KEY (`world`, `x`, `y`, `z`), " +
                    "FOREIGN KEY (world) REFERENCES worlds(id) ON DELETE CASCADE ON UPDATE CASCADE, " +
                    "FOREIGN KEY (owner) REFERENCES players(id)) ENGINE=InnoDB;");

            this.reloadShopCache(connection);
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.getCommand("createshop").setExecutor(new CommandCreateShop(this));
        this.getCommand("shopinfo").setExecutor(new CommandViewShop(this));

        this.getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
    }

    private void reloadShopCache(Connection connection) throws Exception {
        this.shopCache = new HashMap<>();
        // TODO Audit every so often to ensure there is actually a sign there (clean-up operation)
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT worlds.uuid,x,y,z,players.uuid,buyPrice,sellPrice,item FROM chest_shops " +
                        "INNER JOIN worlds ON chest_shops.world = worlds.id " +
                        "INNER JOIN players ON chest_shops.owner = players.id")) {
            try (ResultSet rs = query.executeQuery()) {

                UUID worldUID;
                YamlConfiguration config = new YamlConfiguration();

                while (rs.next()) {

                    worldUID = DatabaseAPI.addDashes(rs.getString(1));
                    if (!shopCache.containsKey(worldUID)) {
                        shopCache.put(worldUID, new HashMap<>());
                    }

                    config.loadFromString(rs.getString(8));

                    shopCache.get(worldUID).put(
                            new BlockVector(rs.getInt(2), rs.getInt(3), rs.getInt(4)),
                            new ChestShopMeta(
                                    DatabaseAPI.addDashes(rs.getString(5)),
                                    rs.getObject(6) != null ? rs.getInt(6) : null,
                                    rs.getObject(7) != null ? rs.getInt(7) : null,
                                    config.getItemStack("i", null)
                            )
                    );

                }

            }
        }
    }

    public void deleteShopMeta(Block block) {
        UUID worldUID = block.getWorld().getUID();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();

        // Update our cache
        if (shopCache.containsKey(worldUID)) {
            BlockVector vector = new BlockVector(x, y, z);
            if (shopCache.get(worldUID).remove(vector) != null) {
                if (shopCache.get(worldUID).isEmpty()) {
                    shopCache.remove(worldUID);
                }
            }
        }

        this.getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                try (Connection connection = databaseAPI.getConnection();
                     PreparedStatement query = connection.prepareStatement("DELETE chest_shops FROM chest_shops " +
                             "INNER JOIN worlds ON worlds.id = chest_shops.world " +
                             "WHERE worlds.uuid = ? AND x = ? AND y = ? AND z = ?")) {
                    query.setString(1, DatabaseAPI.removeDashes(worldUID));
                    query.setInt(2, x);
                    query.setInt(3, y);
                    query.setInt(4, z);
                    query.executeUpdate();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void setShopMeta(Block sign, ChestShopMeta shopMeta, boolean writeToDB) {

        World world = sign.getWorld();
        UUID worldUID = world.getUID();
        String worldName = world.getName();
        BlockVector signVector = new BlockVector(sign.getX(), sign.getY(), sign.getZ());

        shopCache.computeIfAbsent(worldUID, k -> new HashMap<>()).put(signVector, shopMeta);

        if (writeToDB) {
            this.getServer().getScheduler().runTaskAsynchronously(this, () -> {
                try (Connection connection = databaseAPI.getConnection();
                     PreparedStatement query = connection.prepareStatement(
                             "INSERT INTO chest_shops (world,x,y,z,owner,buyPrice,sellPrice,item)" +
                                     " VALUES (?,?,?,?,?,?,?,?) " +
                                     "ON DUPLICATE KEY UPDATE owner = VALUES(owner), buyPrice = VALUES(buyPrice), sellPrice = VALUES(sellPrice), item = VALUES(item)")) {

                    query.setInt(1, databaseAPI.getDBWorldID(connection, worldUID, worldName));
                    query.setInt(2, signVector.getBlockX());
                    query.setInt(3, signVector.getBlockY());
                    query.setInt(4, signVector.getBlockZ());
                    query.setInt(5, databaseAPI.getProfile(connection, shopMeta.getOwner()).getID());
                    if (shopMeta.isBuying()) {
                        query.setInt(6, shopMeta.getBuyPrice());
                    } else {
                        query.setNull(6, Types.NULL);
                    }
                    if (shopMeta.isSelling()) {
                        query.setInt(7, shopMeta.getSellPrice());
                    } else {
                        query.setNull(7, Types.NULL);
                    }

                    YamlConfiguration config = new YamlConfiguration();
                    config.set("i", shopMeta.getItem());
                    query.setString(8, config.saveToString());

                    query.executeUpdate();

                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    public ChestShopMeta getShopMeta(Block sign) {
        UUID worldUID = sign.getWorld().getUID();
        if (shopCache.containsKey(worldUID)) {
            return shopCache.get(worldUID)
                    .getOrDefault(new BlockVector(sign.getX(), sign.getY(), sign.getZ()), null);
        } else {
            return null;
        }
    }

    public Economy getEconomy() {
        return economy;
    }

    @Override
    public void onDisable() {
        shopCache.clear();
    }

}
