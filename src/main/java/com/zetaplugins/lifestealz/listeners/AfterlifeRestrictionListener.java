package com.zetaplugins.lifestealz.listeners;

import com.zetaplugins.lifestealz.LifeStealZ;
import com.zetaplugins.lifestealz.storage.PlayerData;
import com.zetaplugins.zetacore.annotations.AutoRegisterListener;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.List;

/**
 * Restricts player movement and commands while in the afterlife.
 */
@AutoRegisterListener
public final class AfterlifeRestrictionListener implements Listener {
    private final LifeStealZ plugin;
    
    public AfterlifeRestrictionListener(LifeStealZ plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!plugin.getConfig().getBoolean("afterlife.enabled", false)) {
            return;
        }
        
        Player player = event.getPlayer();
        PlayerData data = plugin.getStorage().load(player.getUniqueId());
        
        if (data == null || !data.isAfterlife()) {
            return;
        }
        
        // Prevent teleporting out of afterlife world
        if (event.getTo() != null && !plugin.getAfterlifeManager().getWorldManager().isAfterlifeWorld(event.getTo().getWorld())) {
            // Allow teleportation only by plugin (for release)
            if (event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) {
                event.setCancelled(true);
                player.sendMessage("§7You cannot leave the Afterlife until your time expires.");
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (!plugin.getConfig().getBoolean("afterlife.enabled", false)) {
            return;
        }
        
        Player player = event.getPlayer();
        PlayerData data = plugin.getStorage().load(player.getUniqueId());
        
        if (data == null || !data.isAfterlife()) {
            return;
        }
        
        // Block portal usage in afterlife; special message for end portals
        event.setCancelled(true);
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            player.sendMessage("§7End portals are disabled in the Afterlife.");
        } else {
            player.sendMessage("§7Portals are disabled in the Afterlife.");
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("afterlife.enabled", false)) {
            return;
        }
        
        Player player = event.getPlayer();
        PlayerData data = plugin.getStorage().load(player.getUniqueId());
        
        if (data == null || !data.isAfterlife()) {
            return;
        }
        
        // Allow admins and ops to use any command
        if (player.isOp() || player.hasPermission("lifestealz.admin")) {
            return;
        }
        
        String command = event.getMessage().toLowerCase().split(" ")[0];
        
        // Get allowed commands from config
        List<String> allowedCommands = plugin.getConfig().getStringList("afterlife.allowed-commands");
        
        // Check if command is allowed
        boolean isAllowed = false;
        for (String allowed : allowedCommands) {
            if (command.equals(allowed.toLowerCase())) {
                isAllowed = true;
                break;
            }
        }
        
        if (!isAllowed) {
            event.setCancelled(true);
            player.sendMessage("§7You cannot use this command in the Afterlife.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!plugin.getConfig().getBoolean("afterlife.enabled", false)) {
            return;
        }

        Player player = event.getPlayer();
        PlayerData data = plugin.getStorage().load(player.getUniqueId());

        if (data == null || !data.isAfterlife()) {
            return;
        }

        // Force respawn location to afterlife world spawn
        var spawn = plugin.getAfterlifeManager().getWorldManager().getSpawnLocation();
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }
}
