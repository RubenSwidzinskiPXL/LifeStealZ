package com.zetaplugins.lifestealz.afterlife;

import com.zetaplugins.lifestealz.LifeStealZ;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.CommandSender;

/**
 * Manages the afterlife world where players with 0 hearts are sent.
 */
public class AfterlifeWorldManager {
    private final LifeStealZ plugin;
    private static final String DEFAULT_WORLD_NAME = "afterlife";

    public AfterlifeWorldManager(LifeStealZ plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize the afterlife world respecting config enablement.
     */
    public void init() {
        init(false, null);
    }

    /**
     * Initialize or create the afterlife world, optionally forcing creation even if disabled in config.
     * @param force create even when afterlife.disabled
     * @param seed optional fixed seed (null = random/default)
     */
    public void init(boolean force, Long seed) {
        if (!force && !plugin.getConfig().getBoolean("afterlife.enabled", false)) {
            return;
        }

        String worldName = plugin.getConfig().getString("afterlife.world-name", DEFAULT_WORLD_NAME);
        String envStr = plugin.getConfig().getString("afterlife.environment", "NORMAL").toUpperCase();
        World.Environment environment;
        switch (envStr) {
            case "NETHER":
                environment = World.Environment.NETHER;
                break;
            case "NORMAL":
            default:
                environment = World.Environment.NORMAL;
                break;
        }

        World afterlifeWorld = Bukkit.getWorld(worldName);

        if (afterlifeWorld == null) {
            plugin.getLogger().info("Creating afterlife world: " + worldName + " (environment: " + environment.name() + ")");

            WorldCreator creator = new WorldCreator(worldName);
            creator.type(WorldType.NORMAL);
            creator.environment(environment);
            String generatorMode = plugin.getConfig().getString("afterlife.generator", "default");
            // Only apply custom generator for NORMAL environment
            if (environment == World.Environment.NORMAL && !"default".equalsIgnoreCase(generatorMode)) {
                creator.generator(new AfterlifeChunkGenerator(plugin));
            }
            if (seed != null) {
                creator.seed(seed);
            }

            afterlifeWorld = creator.createWorld();

            if (afterlifeWorld == null) {
                plugin.getLogger().severe("Failed to create afterlife world!");
                return;
            }
        }

        configureWorld(afterlifeWorld);

        int borderSize = plugin.getConfig().getInt("afterlife.border-size", 256);
        // Adjust spawn height if desired
        int spawnY = plugin.getConfig().getInt("afterlife.spawn-y", afterlifeWorld.getSpawnLocation().getBlockY());
        afterlifeWorld.setSpawnLocation(new Location(afterlifeWorld, 0.5, spawnY, 0.5));

        plugin.getLogger().info("Afterlife world initialized: " + worldName + " (env: " + environment.name() + ", border: " + borderSize + "x" + borderSize + ")");
    }

    /**
     * Get the afterlife world instance.
     * @return The afterlife world, or null if not initialized
     */
    public World getWorld() {
        String worldName = plugin.getConfig().getString("afterlife.world-name", DEFAULT_WORLD_NAME);
        return Bukkit.getWorld(worldName);
    }

    /**
     * Get the spawn location in the afterlife world.
     * @return The spawn location or null if world missing
     */
    public Location getSpawnLocation() {
        World world = getWorld();
        if (world == null) {
            return null;
        }
        return getSafeSpawnLocation(world);
    }

    /**
     * Find a safe spawn location, ensuring the player won't suffocate.
     * @param world The afterlife world
     * @return A safe spawn location
     */
    private Location getSafeSpawnLocation(World world) {
        Location spawn = world.getSpawnLocation();
        
        // Add 0.5 to center on block and ensure proper Y positioning
        spawn.setX(spawn.getBlockX() + 0.5);
        spawn.setZ(spawn.getBlockZ() + 0.5);
        
        // Check if current spawn is safe (two air blocks above solid ground)
        if (isSafeLocation(spawn)) {
            return spawn;
        }
        
        // If not safe, search upward for safe location
        for (int y = spawn.getBlockY(); y < world.getMaxHeight() - 2; y++) {
            Location check = spawn.clone();
            check.setY(y);
            if (isSafeLocation(check)) {
                return check;
            }
        }
        
        // Fallback: place player well above ground
        spawn.setY(spawn.getBlockY() + 3);
        return spawn;
    }

    /**
     * Check if a location is safe for player spawning (no suffocation).
     * @param loc Location to check
     * @return true if safe
     */
    private boolean isSafeLocation(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;
        
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        
        // Check feet, body, and head positions
        org.bukkit.block.Block feet = world.getBlockAt(x, y, z);
        org.bukkit.block.Block body = world.getBlockAt(x, y + 1, z);
        
        // Both blocks must be air or passable
        return (feet.isPassable() || feet.getType().isAir()) && 
               (body.isPassable() || body.getType().isAir());
    }

    /**
     * Check if a world is the afterlife world.
     * @param world The world to check
     * @return True if the world is the afterlife world
     */
    public boolean isAfterlifeWorld(World world) {
        if (world == null) {
            return false;
        }
        String worldName = plugin.getConfig().getString("afterlife.world-name", DEFAULT_WORLD_NAME);
        return world.getName().equals(worldName);
    }

    /**
     * Regenerates the afterlife world by unloading, deleting, and recreating it.
     * @param sender Command actor for logging messages; can be null
     * @param seed Optional seed; null uses random
     * @return true if regen succeeded
     */
    public boolean regenerateWorld(CommandSender sender, Long seed) {
        String worldName = plugin.getConfig().getString("afterlife.world-name", DEFAULT_WORLD_NAME);
        World world = Bukkit.getWorld(worldName);

        // Move players out before unloading
        if (world != null) {
            Location fallback = Bukkit.getWorlds().get(0).getSpawnLocation();
            world.getPlayers().forEach(p -> p.teleport(fallback));
            boolean unloaded = Bukkit.unloadWorld(world, false);
            if (!unloaded) {
                if (sender != null) sender.sendMessage("§cFailed to unload afterlife world. Make sure no plugins are locking it.");
                return false;
            }
        }

        if (!deleteWorldFolder(worldName)) {
            if (sender != null) sender.sendMessage("§cFailed to delete afterlife world folder.");
            return false;
        }

        init(true, seed);
        return Bukkit.getWorld(worldName) != null;
    }

    private void configureWorld(World afterlifeWorld) {
        afterlifeWorld.setPVP(plugin.getConfig().getBoolean("afterlife.allow-pvp", false));
        afterlifeWorld.setKeepSpawnInMemory(true);
        afterlifeWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        afterlifeWorld.setGameRule(GameRule.DO_MOB_SPAWNING, plugin.getConfig().getBoolean("afterlife.mob-spawning", true));
        afterlifeWorld.setGameRule(GameRule.KEEP_INVENTORY, true);

        int borderSize = plugin.getConfig().getInt("afterlife.border-size", 256);
        WorldBorder border = afterlifeWorld.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(borderSize);
        border.setWarningDistance(20);
    }

    private boolean deleteWorldFolder(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) return false; // still loaded

        java.io.File folder = new java.io.File(Bukkit.getWorldContainer(), worldName);
        if (!folder.exists()) return true;
        return deleteRecursively(folder);
    }

    private boolean deleteRecursively(java.io.File file) {
        if (file.isDirectory()) {
            java.io.File[] children = file.listFiles();
            if (children != null) {
                for (java.io.File child : children) {
                    if (!deleteRecursively(child)) return false;
                }
            }
        }
        return file.delete();
    }
}
