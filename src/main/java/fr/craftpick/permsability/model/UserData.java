package fr.craftpick.permsability.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class UserData {
    private final UUID uniqueId;
    private String username;
    private String primaryGroup;
    private final List<PermissionNode> permissions = new ArrayList<PermissionNode>();
    private final Map<String, Long> groups = new LinkedHashMap<String, Long>();

    public UserData(UUID uniqueId, String username, String primaryGroup) {
        this.uniqueId = uniqueId;
        this.username = username;
        this.primaryGroup = primaryGroup == null ? "default" : primaryGroup.toLowerCase();
    }

    public UUID getUniqueId() { return uniqueId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPrimaryGroup() { return primaryGroup; }
    public void setPrimaryGroup(String primaryGroup) { this.primaryGroup = primaryGroup.toLowerCase(); }
    public List<PermissionNode> getPermissions() { return permissions; }
    public Map<String, Long> getGroups() { return groups; }
}
