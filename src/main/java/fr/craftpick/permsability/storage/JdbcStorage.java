package fr.craftpick.permsability.storage;

import fr.craftpick.permsability.PermsAbillityPlugin;
import fr.craftpick.permsability.model.GroupData;
import fr.craftpick.permsability.model.UserData;

import java.util.Map;
import java.util.UUID;

public final class JdbcStorage implements Storage {
    private final PermsAbillityPlugin plugin;
    private Database db;
    private UserRepository users;
    private GroupRepository groups;

    public JdbcStorage(PermsAbillityPlugin plugin) { this.plugin = plugin; }

    @Override public void init() throws Exception {
        db = new Database(plugin);
        db.init();
        users = new UserRepository(db, plugin.getConfig());
        groups = new GroupRepository(db, plugin.getConfig());
    }

    @Override public UserData loadUser(UUID id, String name) throws Exception { return users.load(id, name); }
    @Override public Map<String, GroupData> loadGroups() throws Exception { return groups.loadAll(); }
    @Override public boolean createGroup(String name, int weight) throws Exception { return groups.create(name, weight); }
    @Override public boolean deleteGroup(String name) throws Exception { return groups.delete(name); }
    @Override public void setGroupMeta(String name, String key, String value) throws Exception { groups.meta(name, key, value); }
    @Override public void setUserPrimaryGroup(UUID id, String group) throws Exception { users.primary(id, group); }
    @Override public void setUserPermission(UUID id, String node, boolean value, String server, String world, long expiry) throws Exception { users.permission(id, node, value, server, world, expiry); }
    @Override public void unsetUserPermission(UUID id, String node, String server, String world) throws Exception { users.unsetPermission(id, node, server, world); }
    @Override public void setGroupPermission(String group, String node, boolean value, String server, String world, long expiry) throws Exception { groups.permission(group, node, value, server, world, expiry); }
    @Override public void unsetGroupPermission(String group, String node, String server, String world) throws Exception { groups.unsetPermission(group, node, server, world); }
    @Override public void addUserGroup(UUID id, String group, long expiry) throws Exception { users.addGroup(id, group, expiry); }
    @Override public void removeUserGroup(UUID id, String group) throws Exception { users.removeGroup(id, group); }
    @Override public void addGroupParent(String group, String parent) throws Exception { groups.addParent(group, parent); }
    @Override public void removeGroupParent(String group, String parent) throws Exception { groups.removeParent(group, parent); }
    @Override public void audit(String actor, String action, String target, String details) throws Exception { db.audit(actor, action, target, details); }
    @Override public long getRevision() throws Exception { return db.revision(); }
    @Override public void bumpRevision() throws Exception { db.bumpRevision(); }
    @Override public void close() { if (db != null) db.close(); }
}
