package fr.craftpick.permsability.command;

import fr.craftpick.permsability.model.GroupData;
import fr.craftpick.permsability.model.PermissionNode;
import fr.craftpick.permsability.util.DurationParser;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class GroupCommandHandler {
    private final CommandSupport s;

    GroupCommandHandler(CommandSupport support) { this.s = support; }

    boolean handle(final CommandSender sender, String[] args) {
        if (args.length < 2) throw new IllegalArgumentException("Usage: /pa group <list|create|delete|name> ...");
        String second = args[1].toLowerCase(Locale.ROOT);

        if (second.equals("list")) {
            List<String> names = new ArrayList<String>(s.plugin.getPermissionManager().getGroups().keySet());
            Collections.sort(names);
            s.reply(sender, "&7Groups (&f" + names.size() + "&7): &f" + names);
            return true;
        }

        if (second.equals("create")) {
            if (args.length < 3) throw new IllegalArgumentException("Usage: /pa group create <name> [weight]");
            final String name = args[2].toLowerCase(Locale.ROOT);
            final int weight = args.length >= 4 ? Integer.parseInt(args[3]) : 0;
            s.validateGroupName(name);
            s.async(sender, new CommandSupport.CheckedRunnable() {
                @Override public void run() throws Exception {
                    if (!s.storage.createGroup(name, weight)) throw new IllegalArgumentException("Group already exists: " + name);
                    s.storage.audit(sender.getName(), "GROUP_CREATE", name, "weight=" + weight);
                    s.reloadGroupsAndPlayers();
                    s.reply(sender, "&aCreated group &f" + name + " &awith weight &f" + weight + "&a.");
                }
            });
            return true;
        }

        if (second.equals("delete")) {
            if (args.length < 3) throw new IllegalArgumentException("Usage: /pa group delete <name>");
            final String name = args[2].toLowerCase(Locale.ROOT);
            s.async(sender, new CommandSupport.CheckedRunnable() {
                @Override public void run() throws Exception {
                    if (!s.storage.deleteGroup(name)) throw new IllegalArgumentException("Cannot delete group: " + name);
                    s.storage.audit(sender.getName(), "GROUP_DELETE", name, "");
                    s.reloadGroupsAndPlayers();
                    s.reply(sender, "&aDeleted group &f" + name + "&a.");
                }
            });
            return true;
        }

        final String groupName = second;
        GroupData group = s.plugin.getPermissionManager().getGroup(groupName);
        if (group == null) throw new IllegalArgumentException("Unknown group: " + groupName);

        if (args.length < 3 || args[2].equalsIgnoreCase("info")) {
            s.reply(sender, "&8&m---------------- &b" + groupName + " &8&m----------------");
            s.reply(sender, "&7Weight: &f" + group.getWeight());
            s.reply(sender, "&7Prefix: &f" + group.getPrefix());
            s.reply(sender, "&7Suffix: &f" + group.getSuffix());
            s.reply(sender, "&7Parents: &f" + group.getParents());
            s.reply(sender, "&7Permissions: &f" + group.getPermissions().size());
            for (PermissionNode node : group.getPermissions()) {
                s.reply(sender, " &8- " + (node.getValue() ? "&a" : "&c-") + node.getPermission() + " &8[" + node.getServer() + "/" + node.getWorld() + ", " + DurationParser.describeExpiry(node.getExpiry()) + "]");
            }
            return true;
        }

        String action = args[2].toLowerCase(Locale.ROOT);
        if (action.equals("permission")) return permission(sender, groupName, args);
        if (action.equals("parent")) return parent(sender, groupName, args);
        if (action.equals("meta")) return meta(sender, groupName, args);
        throw new IllegalArgumentException("Unknown group action: " + action);
    }

    private boolean permission(final CommandSender sender, final String group, String[] args) {
        if (args.length < 5) throw new IllegalArgumentException("Usage: /pa group <group> permission <set|unset> <node> ...");
        String mode = args[3].toLowerCase(Locale.ROOT);
        final String node = args[4].toLowerCase(Locale.ROOT);

        if (mode.equals("set")) {
            final boolean value = args.length >= 6 ? s.parseBoolean(args[5]) : true;
            final String server = args.length >= 7 ? args[6] : "*";
            final String world = args.length >= 8 ? args[7] : "*";
            final long expiry = args.length >= 9 ? DurationParser.parseExpiry(args[8]) : 0L;
            s.async(sender, new CommandSupport.CheckedRunnable() {
                @Override public void run() throws Exception {
                    s.storage.setGroupPermission(group, node, value, server, world, expiry);
                    s.storage.audit(sender.getName(), "GROUP_PERMISSION_SET", group, node + "=" + value + " " + server + "/" + world);
                    s.reloadGroupsAndPlayers();
                    s.reply(sender, "&aSet &f" + node + "&a = " + value + " for group &f" + group + "&a.");
                }
            });
            return true;
        }

        if (mode.equals("unset")) {
            final String server = args.length >= 6 ? args[5] : "*";
            final String world = args.length >= 7 ? args[6] : "*";
            s.async(sender, new CommandSupport.CheckedRunnable() {
                @Override public void run() throws Exception {
                    s.storage.unsetGroupPermission(group, node, server, world);
                    s.storage.audit(sender.getName(), "GROUP_PERMISSION_UNSET", group, node + " " + server + "/" + world);
                    s.reloadGroupsAndPlayers();
                    s.reply(sender, "&aRemoved &f" + node + " &afrom group &f" + group + "&a.");
                }
            });
            return true;
        }
        throw new IllegalArgumentException("Use set or unset");
    }

    private boolean parent(final CommandSender sender, final String group, String[] args) {
        if (args.length < 5) throw new IllegalArgumentException("Usage: /pa group <group> parent <add|remove> <parent>");
        String mode = args[3].toLowerCase(Locale.ROOT);
        final String parent = args[4].toLowerCase(Locale.ROOT);
        if (mode.equals("add") && wouldCycle(group, parent, new HashSet<String>())) throw new IllegalArgumentException("That inheritance would create a cycle");

        if (mode.equals("add")) {
            s.async(sender, new CommandSupport.CheckedRunnable() {
                @Override public void run() throws Exception {
                    s.storage.addGroupParent(group, parent);
                    s.storage.audit(sender.getName(), "GROUP_PARENT_ADD", group, parent);
                    s.reloadGroupsAndPlayers();
                    s.reply(sender, "&aGroup &f" + group + " &anow inherits &f" + parent + "&a.");
                }
            });
            return true;
        }

        if (mode.equals("remove")) {
            s.async(sender, new CommandSupport.CheckedRunnable() {
                @Override public void run() throws Exception {
                    s.storage.removeGroupParent(group, parent);
                    s.storage.audit(sender.getName(), "GROUP_PARENT_REMOVE", group, parent);
                    s.reloadGroupsAndPlayers();
                    s.reply(sender, "&aRemoved inheritance &f" + group + " -> " + parent + "&a.");
                }
            });
            return true;
        }
        throw new IllegalArgumentException("Use add or remove");
    }

    private boolean meta(final CommandSender sender, final String group, String[] args) {
        if (args.length < 5) throw new IllegalArgumentException("Usage: /pa group <group> meta <prefix|suffix|weight> <value>");
        final String key = args[3].toLowerCase(Locale.ROOT);
        if (!key.equals("prefix") && !key.equals("suffix") && !key.equals("weight")) throw new IllegalArgumentException("Meta key must be prefix, suffix or weight");
        final String value = key.equals("weight") ? args[4] : CommandSupport.join(args, 4);
        if (key.equals("weight")) Integer.parseInt(value);
        s.async(sender, new CommandSupport.CheckedRunnable() {
            @Override public void run() throws Exception {
                s.storage.setGroupMeta(group, key, value);
                s.storage.audit(sender.getName(), "GROUP_META", group, key + "=" + value);
                s.reloadGroupsAndPlayers();
                s.reply(sender, "&aUpdated &f" + key + " &aof group &f" + group + "&a.");
            }
        });
        return true;
    }

    private boolean wouldCycle(String group, String candidateParent, Set<String> seen) {
        if (candidateParent.equals(group)) return true;
        if (!seen.add(candidateParent)) return false;
        GroupData parent = s.plugin.getPermissionManager().getGroup(candidateParent);
        if (parent == null) return false;
        for (String next : parent.getParents()) if (wouldCycle(group, next, seen)) return true;
        return false;
    }
}
