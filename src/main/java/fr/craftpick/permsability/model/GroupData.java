package fr.craftpick.permsability.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GroupData {
    private final String name;
    private int weight;
    private String prefix;
    private String suffix;
    private final List<PermissionNode> permissions = new ArrayList<PermissionNode>();
    private final Set<String> parents = new LinkedHashSet<String>();

    public GroupData(String name, int weight, String prefix, String suffix) {
        this.name = name.toLowerCase();
        this.weight = weight;
        this.prefix = prefix == null ? "" : prefix;
        this.suffix = suffix == null ? "" : suffix;
    }

    public String getName() { return name; }
    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }
    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix == null ? "" : prefix; }
    public String getSuffix() { return suffix; }
    public void setSuffix(String suffix) { this.suffix = suffix == null ? "" : suffix; }
    public List<PermissionNode> getPermissions() { return permissions; }
    public Set<String> getParents() { return parents; }
}
