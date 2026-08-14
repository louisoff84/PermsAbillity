package fr.craftpick.permsability.model;

public final class PermissionNode {
    private final String permission;
    private final boolean value;
    private final String server;
    private final String world;
    private final long expiry;

    public PermissionNode(String permission, boolean value, String server, String world, long expiry) {
        this.permission = normalizePermission(permission);
        this.value = value;
        this.server = normalizeContext(server);
        this.world = normalizeContext(world);
        this.expiry = expiry;
    }

    public String getPermission() { return permission; }
    public boolean getValue() { return value; }
    public String getServer() { return server; }
    public String getWorld() { return world; }
    public long getExpiry() { return expiry; }

    public boolean isExpired(long now) {
        return expiry > 0L && expiry <= now;
    }

    public boolean matchesContext(String actualServer, String actualWorld) {
        return contextMatches(server, actualServer) && contextMatches(world, actualWorld);
    }

    public int permissionSpecificity(String requested) {
        String req = normalizePermission(requested);
        if (permission.equals(req)) return 100000 + permission.length();
        if (permission.equals("*")) return 1;
        if (permission.endsWith(".*")) {
            String prefix = permission.substring(0, permission.length() - 1);
            if (req.startsWith(prefix)) return 50000 + prefix.length();
        }
        return -1;
    }

    public int contextSpecificity(String actualServer, String actualWorld) {
        int score = 0;
        if (!"*".equals(server) && server.equalsIgnoreCase(actualServer)) score += 2;
        if (!"*".equals(world) && world.equalsIgnoreCase(actualWorld)) score += 1;
        return score;
    }

    private static boolean contextMatches(String configured, String actual) {
        return "*".equals(configured) || configured.equalsIgnoreCase(actual == null ? "" : actual);
    }

    private static String normalizePermission(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String normalizeContext(String value) {
        if (value == null || value.trim().isEmpty()) return "*";
        return value.trim().toLowerCase();
    }
}
