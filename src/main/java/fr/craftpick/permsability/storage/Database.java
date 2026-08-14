package fr.craftpick.permsability.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.craftpick.permsability.PermsAbillityPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;

final class Database implements AutoCloseable {
    private final PermsAbillityPlugin plugin;
    private final FileConfiguration config;
    private HikariDataSource source;

    Database(PermsAbillityPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    void init() throws Exception {
        HikariConfig hikari = new HikariConfig();
        String type = config.getString("storage.type", "sqlite").toLowerCase(Locale.ROOT);
        if (type.equals("sqlite")) {
            File file = new File(plugin.getDataFolder(), config.getString("storage.sqlite-file", "permsability.db"));
            hikari.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setMaximumPoolSize(1);
            hikari.setMinimumIdle(1);
        } else {
            String host = config.getString("storage.host", "127.0.0.1");
            int port = config.getInt("storage.port", type.equals("postgresql") ? 5432 : 3306);
            String name = config.getString("storage.database", "minecraft");
            boolean ssl = config.getBoolean("storage.ssl", false);
            if (type.equals("mysql")) {
                hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + name + "?useSSL=" + ssl + "&characterEncoding=utf8&serverTimezone=UTC");
                hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
            } else if (type.equals("mariadb")) {
                hikari.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + name + "?useSsl=" + ssl);
                hikari.setDriverClassName("org.mariadb.jdbc.Driver");
            } else if (type.equals("postgresql")) {
                hikari.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + name + "?ssl=" + ssl);
                hikari.setDriverClassName("org.postgresql.Driver");
            } else throw new IllegalArgumentException("Unsupported storage.type: " + type);
            hikari.setUsername(config.getString("storage.username", "root"));
            hikari.setPassword(config.getString("storage.password", ""));
            hikari.setMaximumPoolSize(config.getInt("storage.pool.maximum-size", 10));
            hikari.setMinimumIdle(config.getInt("storage.pool.minimum-idle", 2));
        }
        hikari.setConnectionTimeout(config.getLong("storage.pool.connection-timeout-ms", 5000L));
        hikari.setPoolName("PermsAbillity-Hikari");
        source = new HikariDataSource(hikari);
        createSchema();
        ensureDefaults();
    }

    Connection connection() throws Exception { return source.getConnection(); }

    private void createSchema() throws Exception {
        String[] sql = {
            "CREATE TABLE IF NOT EXISTS pa_users (uuid VARCHAR(36) PRIMARY KEY, username VARCHAR(32) NOT NULL, primary_group VARCHAR(64) NOT NULL)",
            "CREATE TABLE IF NOT EXISTS pa_groups (name VARCHAR(64) PRIMARY KEY, weight INTEGER NOT NULL, prefix VARCHAR(255) NOT NULL, suffix VARCHAR(255) NOT NULL)",
            "CREATE TABLE IF NOT EXISTS pa_user_permissions (uuid VARCHAR(36) NOT NULL, permission VARCHAR(255) NOT NULL, value BOOLEAN NOT NULL, server VARCHAR(64) NOT NULL, world VARCHAR(64) NOT NULL, expiry BIGINT NOT NULL, PRIMARY KEY (uuid, permission, server, world))",
            "CREATE TABLE IF NOT EXISTS pa_group_permissions (group_name VARCHAR(64) NOT NULL, permission VARCHAR(255) NOT NULL, value BOOLEAN NOT NULL, server VARCHAR(64) NOT NULL, world VARCHAR(64) NOT NULL, expiry BIGINT NOT NULL, PRIMARY KEY (group_name, permission, server, world))",
            "CREATE TABLE IF NOT EXISTS pa_user_groups (uuid VARCHAR(36) NOT NULL, group_name VARCHAR(64) NOT NULL, expiry BIGINT NOT NULL, PRIMARY KEY (uuid, group_name))",
            "CREATE TABLE IF NOT EXISTS pa_group_inheritance (group_name VARCHAR(64) NOT NULL, parent_group VARCHAR(64) NOT NULL, PRIMARY KEY (group_name, parent_group))",
            "CREATE TABLE IF NOT EXISTS pa_audit (id VARCHAR(36) PRIMARY KEY, actor VARCHAR(64) NOT NULL, action VARCHAR(64) NOT NULL, target VARCHAR(128) NOT NULL, details VARCHAR(1000) NOT NULL, created_at BIGINT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS pa_sync (id INTEGER PRIMARY KEY, revision BIGINT NOT NULL)"
        };
        try (Connection c = connection(); Statement s = c.createStatement()) {
            for (String statement : sql) s.execute(statement);
        }
    }

    private void ensureDefaults() throws Exception {
        String group = config.getString("permissions.default-group", "default").toLowerCase(Locale.ROOT);
        if (!groupExists(group)) {
            try (Connection c = connection(); PreparedStatement ps = c.prepareStatement("INSERT INTO pa_groups (name, weight, prefix, suffix) VALUES (?, 0, '', '')")) {
                ps.setString(1, group); ps.executeUpdate();
            }
        }
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement("SELECT revision FROM pa_sync WHERE id=1"); ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) try (PreparedStatement insert = c.prepareStatement("INSERT INTO pa_sync (id, revision) VALUES (1, 0)")) { insert.executeUpdate(); }
        }
    }

    boolean groupExists(String group) throws Exception {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement("SELECT name FROM pa_groups WHERE name=?")) {
            ps.setString(1, group.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    void setPermission(String table, String subjectColumn, String subject, String permission, boolean value, String server, String world, long expiry) throws Exception {
        server = context(server); world = context(world); permission = permission.toLowerCase(Locale.ROOT);
        try (Connection c = connection()) {
            deletePermission(c, table, subjectColumn, subject, permission, server, world);
            String sql = "INSERT INTO " + table + " (" + subjectColumn + ", permission, value, server, world, expiry) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, subject); ps.setString(2, permission); ps.setBoolean(3, value); ps.setString(4, server); ps.setString(5, world); ps.setLong(6, expiry); ps.executeUpdate();
            }
        }
        bumpRevision();
    }

    void deletePermission(String table, String subjectColumn, String subject, String permission, String server, String world) throws Exception {
        try (Connection c = connection()) { deletePermission(c, table, subjectColumn, subject, permission.toLowerCase(Locale.ROOT), context(server), context(world)); }
        bumpRevision();
    }

    private void deletePermission(Connection c, String table, String subjectColumn, String subject, String permission, String server, String world) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE " + subjectColumn + "=? AND permission=? AND server=? AND world=?")) {
            ps.setString(1, subject); ps.setString(2, permission); ps.setString(3, server); ps.setString(4, world); ps.executeUpdate();
        }
    }

    int execute(Connection c, String sql, String... values) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) ps.setString(i + 1, values[i]);
            return ps.executeUpdate();
        }
    }

    void audit(String actor, String action, String target, String details) throws Exception {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement("INSERT INTO pa_audit (id, actor, action, target, details, created_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString()); ps.setString(2, trim(actor, 64)); ps.setString(3, trim(action, 64)); ps.setString(4, trim(target, 128)); ps.setString(5, trim(details, 1000)); ps.setLong(6, System.currentTimeMillis()); ps.executeUpdate();
        }
    }

    long revision() throws Exception {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement("SELECT revision FROM pa_sync WHERE id=1"); ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0L; }
    }

    void bumpRevision() throws Exception {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement("UPDATE pa_sync SET revision=revision+1 WHERE id=1")) { ps.executeUpdate(); }
    }

    static String context(String value) { return value == null || value.trim().isEmpty() ? "*" : value.trim().toLowerCase(Locale.ROOT); }
    static String trim(String value, int max) { value = value == null ? "" : value; return value.length() <= max ? value : value.substring(0, max); }

    @Override public void close() { if (source != null) source.close(); }
}
