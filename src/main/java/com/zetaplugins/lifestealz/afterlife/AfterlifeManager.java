package com.zetaplugins.lifestealz.afterlife;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import com.zetaplugins.lifestealz.LifeStealZ;
import com.zetaplugins.lifestealz.storage.PlayerData;
import com.zetaplugins.lifestealz.util.MessageUtils;
import net.kyori.adventure.text.Component;

/**
 * Manages afterlife mechanics including sending players to afterlife and releasing them.
 */
public class AfterlifeManager {
    private final LifeStealZ plugin;
    private final AfterlifeWorldManager worldManager;
    private final com.zetaplugins.lifestealz.util.InventoryManager inventoryManager;
    
    public AfterlifeManager(LifeStealZ plugin) {
        this.plugin = plugin;
        this.worldManager = new AfterlifeWorldManager(plugin);
        this.inventoryManager = new com.zetaplugins.lifestealz.util.InventoryManager(plugin);
    }
    
    public AfterlifeWorldManager getWorldManager() {
        return worldManager;
    }
    
    public com.zetaplugins.lifestealz.util.InventoryManager getInventoryManager() {
        return inventoryManager;
    }
    
    /**
     * Send a player to the afterlife.
     * @param player The player to send
     */
    public void sendPlayerToAfterlife(Player player) {
        if (!plugin.getConfig().getBoolean("afterlife.enabled", false)) {
            // Fallback to regular elimination if afterlife is disabled
            return;
        }
        
        PlayerData data = plugin.getStorage().load(player.getUniqueId());
        if (data == null) {
            return;
        }
        
        // Set player state to afterlife
        data.setLifeState(LifeState.AFTERLIFE);
        
        // Calculate release time
        long durationSeconds = plugin.getConfig().getLong("afterlife.duration-seconds", 3600);
        data.setAfterlifeReleaseTime(System.currentTimeMillis() + (durationSeconds * 1000));
        
        // Fix hearts to configured amount while in afterlife
        int fixedHearts = plugin.getConfig().getInt("afterlife.fixed-hearts", plugin.getConfig().getInt("afterlife.revive-hearts", 10));
        data.setMaxHealth(fixedHearts * 2);
        plugin.getStorage().save(data);
        LifeStealZ.setMaxHealth(player, data.getMaxHealth());
        
        // Restore health and handle inventory separation
        player.setHealth(20.0);
        boolean separateInv = plugin.getConfig().getBoolean("afterlife.separate-inventories", true);
        if (separateInv) {
            inventoryManager.saveProfile(player, "main");
            boolean loaded = inventoryManager.loadProfile(player, "afterlife");
            if (!loaded && plugin.getConfig().getBoolean("afterlife.clear-inventory", true)) {
                player.getInventory().clear();
            }
        } else if (plugin.getConfig().getBoolean("afterlife.clear-inventory", true)) {
            player.getInventory().clear();
        }
        
        // Ensure afterlife world exists
        World afterlifeWorld = worldManager.getWorld();
        if (afterlifeWorld == null) {
            Long seed = null;
            try {
                Object val = plugin.getConfig().get("afterlife.seed");
                if (val instanceof Number) seed = ((Number) val).longValue();
            } catch (Exception ignored) {}
            worldManager.init(true, seed);
            afterlifeWorld = worldManager.getWorld();
        }

        // Teleport to afterlife
        if (afterlifeWorld != null) {
            Location spawnLoc = worldManager.getSpawnLocation();
            if (spawnLoc != null) {
                player.teleport(spawnLoc);
            }
        }
        
        // Send message (configurable override)
        String cfgMsg = plugin.getConfig().getString("afterlife.enter-message", "");
        Component message = MessageUtils.getAndFormatMsg(
            false,
            "afterlifeEnter",
            cfgMsg != null && !cfgMsg.isEmpty() ? cfgMsg : "&c☠ You have entered the Afterlife. You will be released in &e%time%&c.",
            new MessageUtils.Replaceable("%time%", formatTime(durationSeconds))
        );
        player.sendMessage(message);
        
        plugin.getLogger().info(player.getName() + " has been sent to the afterlife for " + durationSeconds + " seconds");
    }
    
    /**
     * Release a player from the afterlife.
     * @param player The player to release
     */
    public void releaseFromAfterlife(Player player) {
        PlayerData data = plugin.getStorage().load(player.getUniqueId());
        if (data == null) {
            return;
        }
        
        // Set state back to alive
        data.setLifeState(LifeState.ALIVE);
        data.setAfterlifeReleaseTime(0L);
        
        // Set hearts to revive amount
        int reviveHearts = plugin.getConfig().getInt("afterlife.revive-hearts", 10);
        data.setMaxHealth(reviveHearts * 2);
        data.setHasBeenRevived(data.getHasBeenRevived() + 1);
        
        plugin.getStorage().save(data);
        LifeStealZ.setMaxHealth(player, data.getMaxHealth());
        
        // Save afterlife inventory and restore main inventory if enabled
        if (plugin.getConfig().getBoolean("afterlife.separate-inventories", true)) {
            inventoryManager.saveProfile(player, "afterlife");
            inventoryManager.loadProfile(player, "main");
        }

        // Teleport to main world spawn
        World mainWorld = Bukkit.getWorld(plugin.getConfig().getString("afterlife.return-world", "world"));
        if (mainWorld == null) {
            mainWorld = Bukkit.getWorlds().get(0); // Fallback to first world
        }
        
        Location spawnLoc = mainWorld.getSpawnLocation();
        player.teleport(spawnLoc);
        player.setHealth(Math.min(20.0, data.getMaxHealth()));
        
        // Send message
        Component message = MessageUtils.getAndFormatMsg(
            false,
            "afterlifeRelease",
            "&aYou have been revived! You now have &e%hearts% &ahearts.",
            new MessageUtils.Replaceable("%hearts%", String.valueOf(reviveHearts))
        );
        player.sendMessage(message);
        
        plugin.getLogger().info(player.getName() + " has been released from the afterlife with " + reviveHearts + " hearts");
    }
    
    /**
     * Check and release all players whose afterlife timer has expired.
     */
    public void checkAndReleaseExpiredPlayers() {
        if (!plugin.getConfig().getBoolean("afterlife.enabled", false)) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.getStorage().load(player.getUniqueId());
            if (data == null) {
                continue;
            }
            
            if (data.isAfterlife() && currentTime >= data.getAfterlifeReleaseTime()) {
                releaseFromAfterlife(player);
            }
        }
    }
    
    /**
     * Format seconds into a human-readable time string.
     * @param seconds The number of seconds
     * @return Formatted time string
     */
    private String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }
}
