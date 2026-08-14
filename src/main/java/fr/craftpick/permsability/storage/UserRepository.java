package fr.craftpick.permsability.storage;

import fr.craftpick.permsability.model.PermissionNode;
import fr.craftpick.permsability.model.UserData;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.UUID;

final class UserRepository {
    private final Database db;
    private final FileConfiguration config;

    UserRepository(Database db, FileConfiguration config) { this.db = db; this.config = config; }

    UserData load(UUID uuid, String username) throws Exception {
        ensure(uuid, username);
        UserData user;
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement("SELECT username, primary_group FROM pa_users WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Missing user row for " + uuid);
                user = new UserData(uuid, rs.getString("username"), rs.getString("primary_group"));
            }
            try (PreparedStatement ps2 = c.prepareStatement("SELECT permission, value, server, world, expiry FROM pa_user_permissions WHERE uuid=?")) {
                ps2.setString(1, uuid.toString());
                try (ResultSet rs = ps2.executeQuery()) { while (rs.next()) user.getPermissions().add(node(rs)); }
            }
            try (PreparedStatement ps3 = c.prepareStatement("SELECT group_name, expiry FROM pa_user_groups WHERE uuid=?")) {
                ps3.setString(1, uuid.toString());
                try (ResultSet rs = ps3.executeQuery()) { while (rs.next()) user.getGroups().put(rs.getString("group_name").toLowerCase(Locale.ROOT), rs.getLong("expiry")); }
            }
        }
        return user;
    }

    private void ensure(UUID uuid, String username) throws Exception {
        String id = uuid.toString();
        String safeName = Database.trim(username == null || username.isEmpty() ? "unknown" : username, 32);
        String defaultGroup = config.getString("permissions.default-group", "default").toLowerCase(Locale.ROOT);
        try (Connection c = db.connection(); PreparedStatement update = c.prepareStatement("UPDATE pa_users SET username=? WHERE uuid=?")) {
            update.setString(1, safeName); update.setString(2, id);
            if (update.executeUpdate() == 0) try (PreparedStatement insert = c.prepareStatement("INSERT INTO pa_users (uuid, username, primary_group) VALUES (?, ?, ?)")) {
                insert.setString(1, id); insert.setString(2, safeName); insert.setString(3, defaultGroup); insert.executeUpdate();
            }
        }
    }

    void primary(UUID uuid, String group) throws Exception {
        group = group.toLowerCase(Locale.ROOT);
        if (!db.groupExists(group)) throw new IllegalArgumentException("Unknown group: " + group);
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement("UPDATE pa_users SET primary_group=? WHERE uuid=?")) {
            ps.setString(1, group); ps.setString(2, uuid.toString()); ps.executeUpdate();
        }
        db.bumpRevision();
    }

    void permission(UUID uuid, String node, boolean value, String server, String world, long expiry) throws Exception {
        db.setPermission("pa_user_permissions", "uuid", uuid.toString(), node, value, server, world, expiry);
    }

    void unsetPermission(UUID uuid, String node, String server, String world) throws Exception {
        db.deletePermission("pa_user_permissions", "uuid", uuid.toString(), node, server, world);
    }

    void addGroup(UUID uuid, String group, long expiry) throws Exception {
        group = group.toLowerCase(Locale.ROOT);
        if (!db.groupExists(group)) throw new IllegalArgumentException("Unknown group: " + group);
        try (Connection c = db.connection()) {
            db.execute(c, "DELETE FROM pa_user_groups WHERE uuid=? AND group_name=?", uuid.toString(), group);
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO pa_user_groups (uuid, group_name, expiry) VALUES (?, ?, ?)")) {
                ps.setString(1, uuid.toString()); ps.setString(2, group); ps.setLong(3, expiry); ps.executeUpdate();
            }
        }
        db.bumpRevision();
    }

    void removeGroup(UUID uuid, String group) throws Exception {
        try (Connection c = db.connection()) { db.execute(c, "DELETE FROM pa_user_groups WHERE uuid=? AND group_name=?", uuid.toString(), group.toLowerCase(Locale.ROOT)); }
        db.bumpRevision();
    }

    private PermissionNode node(ResultSet rs) throws Exception {
        return new PermissionNode(rs.getString("permission"), rs.getBoolean("value"), rs.getString("server"), rs.getString("world"), rs.getLong("expiry"));
    }
}
