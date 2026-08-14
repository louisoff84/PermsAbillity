package fr.craftpick.permsability.service;

import fr.craftpick.permsability.model.GroupData;
import fr.craftpick.permsability.model.PermissionNode;
import fr.craftpick.permsability.model.UserData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PermissionEngine {
    private final PermissionManager manager;

    public PermissionEngine(PermissionManager manager) {
        this.manager = manager;
    }

    public Boolean resolve(UserData user, String permission, String server, String world) {
        long now = System.currentTimeMillis();
        NodeResult direct = bestNode(user.getPermissions(), permission, server, world, now);
        if (direct != null) return direct.value;

        List<GroupData> groups = getEffectiveGroups(user, now);
        NodeResult best = null;
        int bestWeight = Integer.MIN_VALUE;
        for (GroupData group : groups) {
            NodeResult candidate = bestNode(group.getPermissions(), permission, server, world, now);
            if (candidate == null) continue;
            if (best == null || candidate.score > best.score || (candidate.score == best.score && group.getWeight() > bestWeight)) {
                best = candidate;
                bestWeight = group.getWeight();
            }
        }
        return best == null ? null : best.value;
    }

    public List<GroupData> getEffectiveGroups(UserData user, long now) {
        Set<String> names = new LinkedHashSet<String>();
        names.add(user.getPrimaryGroup());
        for (Map.Entry<String, Long> entry : user.getGroups().entrySet()) {
            if (entry.getValue() <= 0L || entry.getValue() > now) names.add(entry.getKey());
        }

        Set<String> expanded = new LinkedHashSet<String>();
        for (String name : names) collect(name, expanded, new HashSet<String>());
        List<GroupData> groups = new ArrayList<GroupData>();
        for (String name : expanded) {
            GroupData group = manager.getGroup(name);
            if (group != null) groups.add(group);
        }
        Collections.sort(groups, new Comparator<GroupData>() {
            @Override public int compare(GroupData a, GroupData b) { return Integer.compare(b.getWeight(), a.getWeight()); }
        });
        return groups;
    }

    public Set<String> getConcretePermissions(UserData user) {
        Set<String> permissions = new LinkedHashSet<String>();
        for (PermissionNode node : user.getPermissions()) if (!node.getPermission().contains("*")) permissions.add(node.getPermission());
        for (GroupData group : getEffectiveGroups(user, System.currentTimeMillis())) {
            for (PermissionNode node : group.getPermissions()) if (!node.getPermission().contains("*")) permissions.add(node.getPermission());
        }
        return permissions;
    }

    public String getPrefix(UserData user) {
        for (GroupData group : getEffectiveGroups(user, System.currentTimeMillis())) if (!group.getPrefix().isEmpty()) return group.getPrefix();
        return "";
    }

    public String getSuffix(UserData user) {
        for (GroupData group : getEffectiveGroups(user, System.currentTimeMillis())) if (!group.getSuffix().isEmpty()) return group.getSuffix();
        return "";
    }

    private void collect(String groupName, Set<String> result, Set<String> path) {
        if (groupName == null || path.contains(groupName)) return;
        path.add(groupName);
        GroupData group = manager.getGroup(groupName);
        if (group == null) return;
        result.add(group.getName());
        for (String parent : group.getParents()) collect(parent, result, new HashSet<String>(path));
    }

    private NodeResult bestNode(List<PermissionNode> nodes, String permission, String server, String world, long now) {
        NodeResult best = null;
        for (PermissionNode node : nodes) {
            if (node.isExpired(now) || !node.matchesContext(server, world)) continue;
            int permissionScore = node.permissionSpecificity(permission);
            if (permissionScore < 0) continue;
            int score = permissionScore * 10 + node.contextSpecificity(server, world);
            if (best == null || score > best.score) best = new NodeResult(node.getValue(), score);
        }
        return best;
    }

    private static final class NodeResult {
        private final boolean value;
        private final int score;
        private NodeResult(boolean value, int score) { this.value = value; this.score = score; }
    }
}
