package com.zetaplugins.lifestealz.commands.MainCommand.subcommands;

import com.zetaplugins.lifestealz.LifeStealZ;
import com.zetaplugins.lifestealz.commands.SubCommand;
import com.zetaplugins.lifestealz.util.MessageUtils;
import com.zetaplugins.lifestealz.util.commands.CommandUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AfterlifeSubCommand implements SubCommand {
    private final LifeStealZ plugin;

    public AfterlifeSubCommand(LifeStealZ plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.formatMsg("&cUsage: /lifestealz afterlife <generate|tp|regen|info|invload|invclear>"));
            return true;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "generate":
                if (!sender.hasPermission("lifestealz.admin.afterlife.generate")) {
                    CommandUtils.throwPermissionError(sender);
                    return false;
                }
                plugin.getAfterlifeManager().getWorldManager().init(true, parseSeed(args, 2));
                sender.sendMessage(MessageUtils.formatMsg("&aAfterlife world generated/initialized."));
                return true;

            case "tp":
            case "visit":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(MessageUtils.formatMsg("&cOnly players can use this."));
                    return true;
                }
                if (!sender.hasPermission("lifestealz.admin.afterlife.tp")) {
                    CommandUtils.throwPermissionError(sender);
                    return false;
                }
                Player player = (Player) sender;
                plugin.getAfterlifeManager().getWorldManager().init(true, null);
                World world = plugin.getAfterlifeManager().getWorldManager().getWorld();
                if (world == null) {
                    sender.sendMessage(MessageUtils.formatMsg("&cAfterlife world could not be loaded."));
                    return true;
                }
                Location spawn = plugin.getAfterlifeManager().getWorldManager().getSpawnLocation();
                if (spawn == null) spawn = world.getSpawnLocation();
                player.teleport(spawn);
                sender.sendMessage(MessageUtils.formatMsg("&aTeleported to the afterlife world."));
                return true;

            case "regen":
            case "regenerate":
                if (!sender.hasPermission("lifestealz.admin.afterlife.regen")) {
                    CommandUtils.throwPermissionError(sender);
                    return false;
                }
                if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
                    sender.sendMessage(MessageUtils.formatMsg("&cThis will delete and recreate the afterlife world. Use: /lifestealz afterlife regen confirm [seed]"));
                    return true;
                }
                Long seed = parseSeed(args, 3);
                boolean ok = plugin.getAfterlifeManager().getWorldManager().regenerateWorld(sender, seed);
                sender.sendMessage(MessageUtils.formatMsg(ok ? "&aAfterlife world regenerated." : "&cAfterlife world regeneration failed."));
                return true;

            case "info":
                if (!sender.hasPermission("lifestealz.admin.afterlife.info")) {
                    CommandUtils.throwPermissionError(sender);
                    return false;
                }
                String worldName = plugin.getConfig().getString("afterlife.world-name", "afterlife");
                World w = Bukkit.getWorld(worldName);
                boolean exists = w != null;
                int players = exists ? w.getPlayers().size() : 0;
                Component info = MessageUtils.formatMsg("&7Afterlife world: &f" + worldName + " &8| exists: " + (exists ? "&aYES" : "&cNO") + (exists ? " &8| players: &f" + players : ""));
                sender.sendMessage(info);
                return true;

            case "invload":
                if (!sender.hasPermission("lifestealz.admin.afterlife.invload")) {
                    CommandUtils.throwPermissionError(sender);
                    return false;
                }
                if (args.length < 4) {
                    sender.sendMessage(MessageUtils.formatMsg("&cUsage: /lifestealz afterlife invload <player> <main|afterlife>"));
                    return true;
                }
                Player targetLoad = Bukkit.getPlayerExact(args[2]);
                if (targetLoad == null) {
                    sender.sendMessage(MessageUtils.formatMsg("&cPlayer not found: " + args[2]));
                    return true;
                }
                String profileLoad = args[3].toLowerCase();
                boolean okLoad = plugin.getAfterlifeManager().getInventoryManager().loadProfile(targetLoad, profileLoad);
                sender.sendMessage(MessageUtils.formatMsg(okLoad ? "&aLoaded inventory profile '&f" + profileLoad + "&a' for &f" + targetLoad.getName() : "&cNo stored inventory for '&f" + profileLoad + "&c'."));
                return true;

            case "invclear":
                if (!sender.hasPermission("lifestealz.admin.afterlife.invclear")) {
                    CommandUtils.throwPermissionError(sender);
                    return false;
                }
                if (args.length < 4) {
                    sender.sendMessage(MessageUtils.formatMsg("&cUsage: /lifestealz afterlife invclear <player> <main|afterlife>"));
                    return true;
                }
                Player targetClear = Bukkit.getPlayerExact(args[2]);
                if (targetClear == null) {
                    sender.sendMessage(MessageUtils.formatMsg("&cPlayer not found: " + args[2]));
                    return true;
                }
                String profileClear = args[3].toLowerCase();
                plugin.getAfterlifeManager().getInventoryManager().clearStoredProfile(targetClear.getUniqueId(), profileClear);
                sender.sendMessage(MessageUtils.formatMsg("&aCleared stored inventory profile '&f" + profileClear + "&a' for &f" + targetClear.getName()));
                return true;

            default:
                sender.sendMessage(MessageUtils.formatMsg("&cUnknown subcommand. Usage: /lifestealz afterlife <generate|tp|regen|info|invload|invclear>"));
                return true;
        }
    }

    private Long parseSeed(String[] args, int index) {
        if (args.length <= index) return null;
        try {
            return Long.parseLong(args[index]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String getUsage() {
        return "/lifestealz afterlife <generate|tp|regen|info|invload|invclear>";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("lifestealz.admin.afterlife.generate")
            || sender.hasPermission("lifestealz.admin.afterlife.tp")
            || sender.hasPermission("lifestealz.admin.afterlife.regen")
            || sender.hasPermission("lifestealz.admin.afterlife.info")
            || sender.hasPermission("lifestealz.admin.afterlife.invload")
            || sender.hasPermission("lifestealz.admin.afterlife.invclear");
    }
}
