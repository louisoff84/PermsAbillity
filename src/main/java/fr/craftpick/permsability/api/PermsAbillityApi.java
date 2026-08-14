package fr.craftpick.permsability.api;

import fr.craftpick.permsability.PermsAbillityPlugin;
import fr.craftpick.permsability.model.GroupData;
import fr.craftpick.permsability.model.UserData;
import fr.craftpick.permsability.service.PermissionManager;
import fr.craftpick.permsability.storage.Storage;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PermsAbillityApi {
    private final PermsAbillityPlugin plugin;
    private final PermissionManager manager;
    private final Storage storage;

    public PermsAbillityApi(PermsAbillityPlugin plugin, PermissionManager manager, Storage storage) {
        this.plugin = plugin;
        this.manager = manager;
        this.storage = storage;
    }

    public Boolean checkPermission(Player player, String permission) {
        UserData user = manager.getCachedUser(player.getUniqueId());
        if (user == null) return null;
        return manager.getEngine().resolve(user, permission, plugin.getServerName(), player.getWorld().getName());
    }

    public boolean hasPermission(Player player, String permission) {
        Boolean result = checkPermission(player, permission);
        return result == null ? player.hasPermission(permission) : result.booleanValue();
    }

    public String getPrefix(Player player) {
        UserData user = manager.getCachedUser(player.getUniqueId());
        return user == null ? "" : manager.getEngine().getPrefix(user);
    }

    public String getSuffix(Player player) {
        UserData user = manager.getCachedUser(player.getUniqueId());
        return user == null ? "" : manager.getEngine().getSuffix(user);
    }

    public List<String> getGroups(Player player) {
        UserData user = manager.getCachedUser(player.getUniqueId());
        List<String> result = new ArrayList<String>();
        if (user == null) return result;
        for (GroupData group : manager.getEngine().getEffectiveGroups(user, System.currentTimeMillis())) result.add(group.getName());
        return result;
    }

    public void setUserPermission(UUID uniqueId, String permission, boolean value, String server, String world, long expiry) throws Exception {
        storage.setUserPermission(uniqueId, permission, value, server, world, expiry);
        manager.invalidateUser(uniqueId);
    }
}
