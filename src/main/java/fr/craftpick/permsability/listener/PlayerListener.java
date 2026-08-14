package fr.craftpick.permsability.listener;

import fr.craftpick.permsability.PermsAbillityPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginEnableEvent;

import java.util.logging.Level;

public final class PlayerListener implements Listener {
    private final PermsAbillityPlugin plugin;

    public PlayerListener(PermsAbillityPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            plugin.getPermissionManager().loadUser(event.getUniqueId(), event.getName());
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Could not preload permissions for " + event.getName(), ex);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(PlayerLoginEvent event) {
        plugin.getAttachmentManager().apply(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        plugin.getAttachmentManager().apply(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getAttachmentManager().remove(event.getPlayer());
        plugin.getPermissionManager().invalidateUser(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin() == plugin || !plugin.getConfig().getBoolean("permissions.refresh-on-plugin-enable", true)) return;
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() { plugin.getAttachmentManager().refreshAll(); }
        }, 1L);
    }
}
