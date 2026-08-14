package fr.craftpick.permsability.command;

import fr.craftpick.permsability.PermsAbillityPlugin;
import fr.craftpick.permsability.storage.Storage;
import fr.craftpick.permsability.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

final class CommandSupport {
    final PermsAbillityPlugin plugin;
    final Storage storage;

    CommandSupport(PermsAbillityPlugin plugin) {
        this.plugin = plugin;
        this.storage = plugin.getStorage();
    }

    boolean isAdmin(CommandSender sender) {
        return sender.hasPermission("permsability.admin") || sender.isOp();
    }

    Target target(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) return new Target(online.getUniqueId(), online.getName(), online);
        OfflinePlayer offline = Bukkit.getOfflinePlayer(input);
        return new Target(offline.getUniqueId(), offline.getName() == null ? input : offline.getName(), null);
    }

    boolean parseBoolean(String value) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("allow") || value.equalsIgnoreCase("yes")) return true;
        if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("deny") || value.equalsIgnoreCase("no")) return false;
        throw new IllegalArgumentException("Boolean must be true/false (or allow/deny)");
    }

    void validateGroupName(String name) {
        if (!name.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("Group names may only contain a-z, 0-9, _ and -");
    }

    void refresh(final Target target) {
        plugin.getPermissionManager().invalidateUser(target.uuid);
        if (target.online != null && target.online.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() { plugin.refreshPlayer(target.online); }
            });
        }
    }

    void reloadGroupsAndPlayers() throws Exception {
        plugin.getPermissionManager().reloadGroups();
        plugin.getPermissionManager().invalidateAllUsers();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() { plugin.refreshAllPlayers(); }
        });
    }

    void async(final CommandSender sender, final CheckedRunnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override public void run() {
                try { task.run(); }
                catch (Exception ex) {
                    plugin.getLogger().warning("PermsAbillity command error: " + ex.getMessage());
                    reply(sender, "&c" + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
                }
            }
        });
    }

    void reply(final CommandSender sender, final String message) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() { sender.sendMessage(plugin.prefix() + Text.color(message)); }
            });
        } else sender.sendMessage(plugin.prefix() + Text.color(message));
    }

    static String join(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(args[i]);
        }
        return builder.toString();
    }

    interface CheckedRunnable { void run() throws Exception; }

    static final class Target {
        final UUID uuid;
        final String name;
        final Player online;
        Target(UUID uuid, String name, Player online) { this.uuid = uuid; this.name = name; this.online = online; }
    }
}
