package fr.craftpick.permsability.service;

import fr.craftpick.permsability.PermsAbillityPlugin;
import fr.craftpick.permsability.model.UserData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AttachmentManager {
    private final PermsAbillityPlugin plugin;
    private final PermissionManager manager;
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<UUID, PermissionAttachment>();

    public AttachmentManager(PermsAbillityPlugin plugin, PermissionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void apply(final Player player) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, new Runnable() { @Override public void run() { apply(player); } });
            return;
        }
        UserData user = manager.getCachedUser(player.getUniqueId());
        if (user == null) return;
        remove(player);
        PermissionAttachment attachment = player.addAttachment(plugin);
        attachments.put(player.getUniqueId(), attachment);
        String server = plugin.getServerName();
        String world = player.getWorld().getName();

        for (Permission permission : Bukkit.getPluginManager().getPermissions()) {
            Boolean value = manager.getEngine().resolve(user, permission.getName(), server, world);
            if (value != null) attachment.setPermission(permission.getName(), value.booleanValue());
        }
        Set<String> concrete = manager.getEngine().getConcretePermissions(user);
        for (String permission : concrete) {
            Boolean value = manager.getEngine().resolve(user, permission, server, world);
            if (value != null) attachment.setPermission(permission, value.booleanValue());
        }
        player.recalculatePermissions();
    }

    public void remove(Player player) {
        PermissionAttachment previous = attachments.remove(player.getUniqueId());
        if (previous != null) {
            try { player.removeAttachment(previous); } catch (IllegalArgumentException ignored) {}
        }
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) apply(player);
    }
}
