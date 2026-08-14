package fr.craftpick.permsability.command;

import fr.craftpick.permsability.PermsAbillityPlugin;
import fr.craftpick.permsability.model.UserData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class PermsCommand implements CommandExecutor, TabCompleter {
    private final CommandSupport s;
    private final UserCommandHandler users;
    private final GroupCommandHandler groups;

    public PermsCommand(PermsAbillityPlugin plugin) {
        s = new CommandSupport(plugin);
        users = new UserCommandHandler(s);
        groups = new GroupCommandHandler(s);
    }

    @Override
    public boolean onCommand(final CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        if (!s.isAdmin(sender)) {
            s.reply(sender, "&cYou do not have permission to use this command.");
            return true;
        }

        try {
            String root = args[0].toLowerCase(Locale.ROOT);
            if (root.equals("user")) return users.handle(sender, args);
            if (root.equals("group")) return groups.handle(sender, args);
            if (root.equals("check")) return check(sender, args);
            if (root.equals("reload")) {
                s.plugin.reloadPlugin();
                s.reply(sender, "&aConfiguration and caches reloaded.");
                return true;
            }
            if (root.equals("sync")) {
                s.async(sender, new CommandSupport.CheckedRunnable() {
                    @Override public void run() throws Exception {
                        s.reloadGroupsAndPlayers();
                        s.reply(sender, "&aDatabase state reloaded. Online users are being refreshed.");
                    }
                });
                return true;
            }
        } catch (Exception ex) {
            s.reply(sender, "&c" + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            return true;
        }
        help(sender);
        return true;
    }

    private boolean check(final CommandSender sender, String[] args) {
        if (args.length < 3) throw new IllegalArgumentException("Usage: /pa check <player> <permission> [world]");
        final CommandSupport.Target target = s.target(args[1]);
        final String permission = args[2].toLowerCase(Locale.ROOT);
        final String world = args.length >= 4 ? args[3] : (target.online == null ? "world" : target.online.getWorld().getName());
        s.async(sender, new CommandSupport.CheckedRunnable() {
            @Override public void run() throws Exception {
                UserData user = s.plugin.getPermissionManager().loadUser(target.uuid, target.name);
                Boolean result = s.plugin.getPermissionManager().getEngine().resolve(user, permission, s.plugin.getServerName(), world);
                String state = result == null ? "&eUNSET" : result.booleanValue() ? "&aTRUE" : "&cFALSE";
                s.reply(sender, "&b" + target.name + " &7-> &f" + permission + " &7= " + state + " &8[" + s.plugin.getServerName() + "/" + world + "]");
            }
        });
        return true;
    }

    private void help(CommandSender sender) {
        s.reply(sender, "&b/pa user <player> info");
        s.reply(sender, "&b/pa user <player> permission set <node> [true|false] [server] [world] [duration]");
        s.reply(sender, "&b/pa user <player> permission unset <node> [server] [world]");
        s.reply(sender, "&b/pa user <player> parent add|remove <group> [duration]");
        s.reply(sender, "&b/pa user <player> primary <group>");
        s.reply(sender, "&b/pa group list | create <name> [weight] | delete <name>");
        s.reply(sender, "&b/pa group <name> info | permission ... | parent ... | meta ...");
        s.reply(sender, "&b/pa check <player> <permission> [world] &8| &b/pa reload &8| &b/pa sync");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> values = new ArrayList<String>();
        if (args.length == 1) values.addAll(Arrays.asList("help", "user", "group", "check", "reload", "sync"));
        else if (args[0].equalsIgnoreCase("user")) {
            if (args.length == 2) for (Player player : Bukkit.getOnlinePlayers()) values.add(player.getName());
            if (args.length == 3) values.addAll(Arrays.asList("info", "permission", "parent", "primary"));
            if (args.length == 4 && args[2].equalsIgnoreCase("permission")) values.addAll(Arrays.asList("set", "unset"));
            if (args.length == 4 && args[2].equalsIgnoreCase("parent")) values.addAll(Arrays.asList("add", "remove"));
            if ((args.length == 4 && args[2].equalsIgnoreCase("primary")) || (args.length == 5 && args[2].equalsIgnoreCase("parent"))) values.addAll(s.plugin.getPermissionManager().getGroups().keySet());
            if (args.length == 6 && args[2].equalsIgnoreCase("permission") && args[3].equalsIgnoreCase("set")) values.addAll(Arrays.asList("true", "false"));
        } else if (args[0].equalsIgnoreCase("group")) {
            if (args.length == 2) { values.addAll(Arrays.asList("list", "create", "delete")); values.addAll(s.plugin.getPermissionManager().getGroups().keySet()); }
            if (args.length == 3 && args[1].equalsIgnoreCase("delete")) values.addAll(s.plugin.getPermissionManager().getGroups().keySet());
            if (args.length == 3 && s.plugin.getPermissionManager().getGroup(args[1]) != null) values.addAll(Arrays.asList("info", "permission", "parent", "meta"));
            if (args.length == 4 && args[2].equalsIgnoreCase("permission")) values.addAll(Arrays.asList("set", "unset"));
            if (args.length == 4 && args[2].equalsIgnoreCase("parent")) values.addAll(Arrays.asList("add", "remove"));
            if (args.length == 4 && args[2].equalsIgnoreCase("meta")) values.addAll(Arrays.asList("prefix", "suffix", "weight"));
            if (args.length == 5 && args[2].equalsIgnoreCase("parent")) values.addAll(s.plugin.getPermissionManager().getGroups().keySet());
            if (args.length == 6 && args[2].equalsIgnoreCase("permission") && args[3].equalsIgnoreCase("set")) values.addAll(Arrays.asList("true", "false"));
        }
        String needle = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> filtered = new ArrayList<String>();
        for (String value : values) if (value.toLowerCase(Locale.ROOT).startsWith(needle)) filtered.add(value);
        Collections.sort(filtered);
        return filtered;
    }
}
