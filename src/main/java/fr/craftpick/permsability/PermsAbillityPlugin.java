package fr.craftpick.permsability;

import fr.craftpick.permsability.api.PermsAbillityApi;
import fr.craftpick.permsability.command.PermsCommand;
import fr.craftpick.permsability.listener.PlayerListener;
import fr.craftpick.permsability.service.AttachmentManager;
import fr.craftpick.permsability.service.PermissionManager;
import fr.craftpick.permsability.storage.JdbcStorage;
import fr.craftpick.permsability.storage.Storage;
import fr.craftpick.permsability.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.logging.Level;

public final class PermsAbillityPlugin extends JavaPlugin {
    private Storage storage;
    private PermissionManager permissionManager;
    private AttachmentManager attachmentManager;
    private PermsAbillityApi api;
    private volatile long lastRevision;
    private String serverName;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        serverName = getConfig().getString("server-name", "global").toLowerCase(Locale.ROOT);
        try {
            storage = new JdbcStorage(this);
            storage.init();
            permissionManager = new PermissionManager(this, storage);
            permissionManager.reloadGroups();
            attachmentManager = new AttachmentManager(this, permissionManager);
            api = new PermsAbillityApi(this, permissionManager, storage);
            lastRevision = storage.getRevision();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Could not initialize PermsAbillity storage", ex);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        Bukkit.getServicesManager().register(PermsAbillityApi.class, api, this, ServicePriority.Normal);

        PermsCommand command = new PermsCommand(this);
        PluginCommand pluginCommand = getCommand("permsability");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);

        for (Player player : Bukkit.getOnlinePlayers()) refreshPlayer(player);
        startSyncTask();
        getLogger().info("PermsAbillity enabled with " + getConfig().getString("storage.type", "sqlite") + " storage on server context '" + serverName + "'.");
    }

    @Override
    public void onDisable() {
        Bukkit.getServicesManager().unregisterAll(this);
        if (attachmentManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) attachmentManager.remove(player);
        }
        if (storage != null) storage.close();
    }

    public void reloadPlugin() throws Exception {
        reloadConfig();
        serverName = getConfig().getString("server-name", "global").toLowerCase(Locale.ROOT);
        permissionManager.reloadGroups();
        permissionManager.invalidateAllUsers();
        for (Player player : Bukkit.getOnlinePlayers()) refreshPlayer(player);
    }

    public void refreshPlayer(final Player player) {
        if (player == null || !player.isOnline()) return;
        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override public void run() {
                try {
                    permissionManager.invalidateUser(player.getUniqueId());
                    permissionManager.loadUser(player.getUniqueId(), player.getName());
                    Bukkit.getScheduler().runTask(PermsAbillityPlugin.this, new Runnable() {
                        @Override public void run() {
                            if (player.isOnline()) attachmentManager.apply(player);
                        }
                    });
                } catch (Exception ex) {
                    getLogger().log(Level.WARNING, "Failed to refresh permissions for " + player.getName(), ex);
                }
            }
        });
    }

    public void refreshAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) refreshPlayer(player);
    }

    private void startSyncTask() {
        if (!getConfig().getBoolean("sync.enabled", true)) return;
        long seconds = Math.max(1L, getConfig().getLong("sync.poll-seconds", 5L));
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            @Override public void run() {
                try {
                    long revision = storage.getRevision();
                    if (revision == lastRevision) return;
                    lastRevision = revision;
                    permissionManager.reloadGroups();
                    permissionManager.invalidateAllUsers();
                    Bukkit.getScheduler().runTask(PermsAbillityPlugin.this, new Runnable() {
                        @Override public void run() { refreshAllPlayers(); }
                    });
                } catch (Exception ex) {
                    getLogger().log(Level.WARNING, "Database sync poll failed", ex);
                }
            }
        }, seconds * 20L, seconds * 20L);
    }

    public String prefix() {
        return Text.color(getConfig().getString("messages.prefix", "&8[&bPermsAbillity&8] &7"));
    }

    public String getServerName() { return serverName; }
    public Storage getStorage() { return storage; }
    public PermissionManager getPermissionManager() { return permissionManager; }
    public AttachmentManager getAttachmentManager() { return attachmentManager; }
    public PermsAbillityApi getApi() { return api; }
}
