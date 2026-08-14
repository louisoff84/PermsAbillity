package fr.craftpick.permsability.storage;

import fr.craftpick.permsability.model.GroupData;
import fr.craftpick.permsability.model.PermissionNode;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class GroupRepository {
    private final Database db;
    private final FileConfiguration config;

    GroupRepository(Database db, FileConfiguration config) { this.db = db; this.config = config; }

    Map<String, GroupData> loadAll() throws Exception {
        Map<String, GroupData> result = new LinkedHashMap<String, GroupData>();
        try (Connection c = db.connection(); PreparedStatement groups = c.prepareStatement("SELECT name, weight, prefix, suffix FROM pa_groups"); ResultSet rs = groups.executeQuery()) {
            while (rs.next()) {
                GroupData group = new GroupData(rs.getString("name"), rs.getInt("weight"), rs.getString("prefix"), rs.getString("suffix"));
                result.put(group.getName(), group);
            }
            try (PreparedStatement perms = c.prepareStatement("SELECT group_name, permission, value, server, world, expiry FROM pa_group_permissions"); ResultSet prs = perms.executeQuery()) {
                while (prs.next()) {
                    GroupData group = result.get(prs.getString("group_name").toLowerCase(Locale.ROOT));
                    if (group != null) group.getPermissions().add(new PermissionNode(prs.getString("permission"), prs.getBoolean("value"), prs.getString("server"), prs.getString("world"), prs.getLong("expiry")));
                }
            }
            try (PreparedStatement parents = c.prepareStatement("SELECT group_name, parent_group FROM pa_group_inheritance"); ResultSet irs = parents.executeQuery()) {
                while (irs.next()) {
                    GroupData group = result.get(irs.getString("group_name").toLowerCase(Locale.ROOT));
                    if (group != null) group.getParents().add(irs.getString("parent_group").toLowerCase(Locale.ROOT));
                }
            }
        }
        return result;
    }

    boolean create(String name, int weight) throws Exception {
        name = name.toLowerCase(Locale.ROOT);
        if (db.groupExists(name)) return false;
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement("INSERT INTO pa_groups (name, weight, prefix, suffix) VALUES (?, ?, '', '')")) {
            ps.setString(1, name); ps.setInt(2, weight); ps.executeUpdate();
        }
        db.bumpRevision(); return true;
    }

    boolean delete(String name) throws Exception {
        name = name.toLowerCase(Locale.ROOT);
        String defaultGroup = config.getString("permissions.default-group", "default").toLowerCase(Locale.ROOT);
        if (name.equals(defaultGroup)) return false;
        int changed;
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                db.execute(c, "DELETE FROM pa_group_permissions WHERE group_name=?", name);
                db.execute(c, "DELETE FROM pa_group_inheritance WHERE group_name=? OR parent_group=?", name, name);
                db.execute(c, "DELETE FROM pa_user_groups WHERE group_name=?", name);
                try (PreparedStatement ps = c.prepareStatement("UPDATE pa_users SET primary_group=? WHERE primary_group=?")) { ps.setString(1, defaultGroup); ps.setString(2, name); ps.executeUpdate(); }
                changed = db.execute(c, "DELETE FROM pa_groups WHERE name=?", name);
                c.commit();
            } catch (Exception ex) { c.rollback(); throw ex; }
        }
        if (changed > 0) db.bumpRevision();
        return changed > 0;
    }

    void meta(String group, String key, String value) throws Exception {
        if (!key.equals("prefix") && !key.equals("suffix") && !key.equals("weight")) throw new IllegalArgumentException("Invalid meta key");
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement("UPDATE pa_groups SET " + key + "=? WHERE name=?")) {
            if (key.equals("weight")) ps.setInt(1, Integer.parseInt(value)); else ps.setString(1, value);
            ps.setString(2, group.toLowerCase(Locale.ROOT));
            if (ps.executeUpdate() == 0) throw new IllegalArgumentException("Unknown group: " + group);
        }
        db.bumpRevision();
    }

    void permission(String group, String node, boolean value, String server, String world, long expiry) throws Exception {
        db.setPermission("pa_group_permissions", "group_name", group.toLowerCase(Locale.ROOT), node, value, server, world, expiry);
    }

    void unsetPermission(String group, String node, String server, String world) throws Exception {
        db.deletePermission("pa_group_permissions", "group_name", group.toLowerCase(Locale.ROOT), node, server, world);
    }

    void addParent(String group, String parent) throws Exception {
        group = group.toLowerCase(Locale.ROOT); parent = parent.toLowerCase(Locale.ROOT);
        if (group.equals(parent)) throw new IllegalArgumentException("A group cannot inherit itself");
        if (!db.groupExists(group) || !db.groupExists(parent)) throw new IllegalArgumentException("Unknown group");
        try (Connection c = db.connection()) {
            db.execute(c, "DELETE FROM pa_group_inheritance WHERE group_name=? AND parent_group=?", group, parent);
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO pa_group_inheritance (group_name, parent_group) VALUES (?, ?)")) { ps.setString(1, group); ps.setString(2, parent); ps.executeUpdate(); }
        }
        db.bumpRevision();
    }

    void removeParent(String group, String parent) throws Exception {
        try (Connection c = db.connection()) { db.execute(c, "DELETE FROM pa_group_inheritance WHERE group_name=? AND parent_group=?", group.toLowerCase(Locale.ROOT), parent.toLowerCase(Locale.ROOT)); }
        db.bumpRevision();
    }
}
