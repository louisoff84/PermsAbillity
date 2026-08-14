package fr.craftpick.permsability.service;

import fr.craftpick.permsability.PermsAbillityPlugin;
import fr.craftpick.permsability.model.GroupData;
import fr.craftpick.permsability.model.UserData;
import fr.craftpick.permsability.storage.Storage;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PermissionManager {
    private final PermsAbillityPlugin plugin;
    private final Storage storage;
    private final Map<UUID, CacheEntry> users = new ConcurrentHashMap<UUID, CacheEntry>();
    private volatile Map<String, GroupData> groups = Collections.emptyMap();
    private final PermissionEngine engine;

    public PermissionManager(PermsAbillityPlugin plugin, Storage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.engine = new PermissionEngine(this);
    }

    public void reloadGroups() throws Exception {
        groups = new ConcurrentHashMap<String, GroupData>(storage.loadGroups());
    }

    public UserData loadUser(UUID uniqueId, String username) throws Exception {
        CacheEntry cached = users.get(uniqueId);
        long now = System.currentTimeMillis();
        long ttl = plugin.getConfig().getLong("cache.user-ttl-seconds", 300L) * 1000L;
        if (cached != null && (ttl <= 0L || cached.loadedAt + ttl > now)) return cached.user;
        UserData loaded = storage.loadUser(uniqueId, username);
        users.put(uniqueId, new CacheEntry(loaded, now));
        return loaded;
    }

    public UserData getCachedUser(UUID uniqueId) {
        CacheEntry entry = users.get(uniqueId);
        return entry == null ? null : entry.user;
    }

    public void invalidateUser(UUID uniqueId) { users.remove(uniqueId); }
    public void invalidateAllUsers() { users.clear(); }

    public GroupData getGroup(String name) {
        return name == null ? null : groups.get(name.toLowerCase(Locale.ROOT));
    }

    public Map<String, GroupData> getGroups() { return Collections.unmodifiableMap(groups); }
    public PermissionEngine getEngine() { return engine; }

    private static final class CacheEntry {
        private final UserData user;
        private final long loadedAt;
        private CacheEntry(UserData user, long loadedAt) { this.user = user; this.loadedAt = loadedAt; }
    }
}
