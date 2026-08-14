# PermsAbillity

PermsAbillity is a database-backed permissions manager for Bukkit, Spigot and Paper. It is inspired by the workflow of modern permission managers while using its own implementation and data model.

## Features

- Users, primary groups and extra groups
- Recursive group inheritance with cycle protection
- Positive and negative permission nodes
- Wildcards: `*` and `plugin.*`
- Server and world contexts
- Temporary permissions and temporary group memberships
- Group weights, prefixes and suffixes
- Offline-player editing
- Bukkit `PermissionAttachment` integration
- Automatic refresh on world change and plugin enable
- User/group caches
- Multi-server database revision sync
- Audit log for administrative changes
- Public Java API through Bukkit ServicesManager
- SQLite, MySQL, MariaDB and PostgreSQL
- HikariCP pooling
- Gradle Shadow JAR and GitHub Actions
- Java 8 bytecode with no `api-version` for broad Bukkit compatibility

## Build

```bash
gradle clean shadowJar
```

Output: `build/libs/PermsAbillity-<version>.jar`

## Main commands

```text
/pa user <player> info
/pa user <player> permission set <node> [true|false] [server] [world] [duration]
/pa user <player> permission unset <node> [server] [world]
/pa user <player> parent add|remove <group> [duration]
/pa user <player> primary <group>

/pa group list
/pa group create <name> [weight]
/pa group delete <name>
/pa group <name> info
/pa group <name> permission set|unset <node> ...
/pa group <name> parent add|remove <group>
/pa group <name> meta prefix|suffix|weight <value>

/pa check <player> <permission> [world]
/pa reload
/pa sync
```

Examples:

```text
/pa group create admin 100
/pa group admin permission set * true
/pa group admin meta prefix &c[Admin]&r
/pa user Steve primary admin
/pa user Steve permission set essentials.fly true * * 2h
```

Durations support `s`, `m`, `h`, `d`, `w` and combinations such as `1d12h`.

## Storage

SQLite works without an external database. For a Minecraft network, change `storage.type` to `mysql`, `mariadb` or `postgresql` in `config.yml` and use the same database on each server. `server-name` can then be used for server-specific permission contexts.

## Permission resolution

1. Direct user nodes override group nodes.
2. Exact nodes are more specific than wildcards.
3. Server/world-specific nodes beat global equivalents.
4. Equivalent group nodes use group weight.
5. Expired permissions and memberships are ignored immediately.

## Java API

```java
PermsAbillityApi api = Bukkit.getServicesManager().load(PermsAbillityApi.class);
boolean allowed = api.hasPermission(player, "example.use");
String prefix = api.getPrefix(player);
```

PermsAbillity does not copy LuckPerms code. It provides its own smaller permissions engine with familiar concepts such as groups, contexts, temporary nodes, SQL storage and network synchronization.
