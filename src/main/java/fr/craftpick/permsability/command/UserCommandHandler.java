package fr.craftpick.permsability.command;

import fr.craftpick.permsability.model.PermissionNode;
import fr.craftpick.permsability.model.UserData;
import fr.craftpick.permsability.util.DurationParser;
import org.bukkit.command.CommandSender;

import java.util.Locale;

final class UserCommandHandler {
    private final CommandSupport s;

    UserCommandHandler(CommandSupport support) { this.s = support; }

    boolean handle(final CommandSender sender, String[] args) {
        if (args.length < 3) throw new IllegalArgumentException("Usage: /pa user <player> <info|permission|parent|primary> ...");
        final CommandSupport.Target target = s.target(args[1]);
        String action = args[2].toLowerCase(Locale.ROOT);

        if (action.equals("info")) {
            s.async(sender, new CommandSupport.CheckedRunnable() {
                @Override public void run() throws Exception {
                    UserData user = s.plugin.getPermissionManager().loadUser(target.uuid, target.name);
                    s.reply(sender, "&8&m---------------- &b" + target.name + " &8&m----------------");
                    s.reply(sender, "&7UUID: &f" + target.uuid);
                    s.reply(sender, "&7Primary group: &f" + user.getPrimaryGroup());
                    s.reply(sender, "&7Extra groups: &f" + (user.getGroups().isEmpty() ? "none" : user.getGroups().keySet()));
                    s.reply(sender, "&7Direct permissions: &f" + user.getPermissions().size());
                    for (PermissionNode node : user.getPermissions()) {
                        s.reply(sender, " &8- " + (node.getValue() ? "&a" : "&c-") + node.getPermission() + " &8[" + node.getServer() + "/" + node.getWorld() + ", " + DurationParser.describeExpiry(node.getExpiry()) + "]");
                    }
                }
            });
            return true;
        }

        if (action.equals("primary")) {
            if (args.length < 4) throw new IllegalArgumentException("Usage: /pa user <player> primary <group>");
            final String group = args[3].toLowerCase(Locale.ROOT);
            s.async(sender, new CommandSupport.CheckedRunnable() {
                @Override public void run() throws Exception {
                    s.plugin.getPermissionManager().loadUser(target.uuid, target.name);
                    s.storage.setUserPrimaryGroup(target.uuid, group);
                    s.storage.audit(sender.getName(), "USER_PRIMARY", target.uuid.toString(), group);
                    s.refresh(target);
                    s.reply(sender, "&aPrimary group of &f" + target.name + " &ais now &f" + group + "&a.");
                }
            });
            return true;
        }

        if (action.equals("permission")) return permission(sender, target, args);
        if (action.equals("parent")) return parent(sender, target, args);
        throw new IllegalArgumentException("Unknown user action: " + action);
    }

    private boolean permission(final CommandSender sender, final CommandSupport.Target target, String[] args) {
        if (args.length < 5) throw new IllegalArgumentException("Usage: /pa user <player> permission <set|unset> <node> ...");
        String mode = args[3].toLowerCase(Locale.ROOT);
        final String node = args[4].toLowerCase(Locale.ROOT);
        if (node.isEmpty()) throw new IllegalArgumentException("Permission node cannot be empty");

        if (mode.equals("set")) {
            final boolean value = args.length >= 6 ? s.parseBoolean(args[5]) : true;
            final String server = args.length >= 7 ? args[6] : "*";
            final String world = args.length >= 8 ? args[7] : "*";
            final long expiry = args.length >= 9 ? DurationParser.parseExpiry(args[8]) : 0L;
            s.async(sender, new CommandSupport.CheckedRunnable() {
                @Override public void run() throws Exception {
                    s.plugin.getPermissionManager().loadUser(target.uuid, target.name);
                    s.storage.setUserPermission(target.uuid, node, value, server, world, expiry);
                    s.storage.audit(sender.getName(), "USER_PERMISSION_SET", target.uuid.toString(), node + "=" + value + " " + server + "/" + world);
                    s.refresh(target);
                    s.reply(sender, "&aSet &f" + node + "&a = " + value + " for &f" + target.name + "&a.");
                }
            });
            return true;
        }

        if (mode.equals("unset")) {
            final String server = args.length >= 6 ? args[5] : "*";
            final String world = args.length >= 7 ? args[6] : "*";
            s.async(sender, new CommandSupport.CheckedRunnable() {
                @Override public void run() throws Exception {
                    s.storage.unsetUserPermission(target.uuid, node, server, world);
                    s.storage.audit(sender.getName(), "USER_PERMISSION_UNSET", target.uuid.toString(), node + " " + server + "/" + world);
                    s.refresh(target);
                    s.reply(sender, "&aRemoved &f" + node + " &afrom &f" + target.name + "&a.");
                }
            });
            return true;
        }
        throw new IllegalArgumentException("Use set or unset");
    }

    private boolean parent(final CommandSender sender, final CommandSupport.Target target, String[] args) {
        if (args.length < 5) throw new IllegalArgumentException("Usage: /pa user <player> parent <add|remove> <group> [duration]");
        String mode = args[3].toLowerCase(Locale.ROOT);
        final String group = args[4].toLowerCase(Locale.ROOT);
        if (mode.equals("add")) {
            final long expiry = args.length >= 6 ? DurationParser.parseExpiry(args[5]) : 0L;
            s.async(sender, new CommandSupport.CheckedRunnable() {
                @Override public void run() throws Exception {
                    s.plugin.getPermissionManager().loadUser(target.uuid, target.name);
                    s.storage.addUserGroup(target.uuid, group, expiry);
                    s.storage.audit(sender.getName(), "USER_GROUP_ADD", target.uuid.toString(), group);
                    s.refresh(target);
                    s.reply(sender, "&aAdded group &f" + group + " &ato &f" + target.name + "&a.");
                }
            });
            return true;
        }
        if (mode.equals("remove")) {
            s.async(sender, new CommandSupport.CheckedRunnable() {
                @Override public void run() throws Exception {
                    s.storage.removeUserGroup(target.uuid, group);
                    s.storage.audit(sender.getName(), "USER_GROUP_REMOVE", target.uuid.toString(), group);
                    s.refresh(target);
                    s.reply(sender, "&aRemoved group &f" + group + " &afrom &f" + target.name + "&a.");
                }
            });
            return true;
        }
        throw new IllegalArgumentException("Use add or remove");
    }
}
