package fr.craftpick.permsability.storage;

import fr.craftpick.permsability.model.GroupData;
import fr.craftpick.permsability.model.UserData;

import java.util.Map;
import java.util.UUID;

public interface Storage extends AutoCloseable {
    void init() throws Exception;
    UserData loadUser(UUID uniqueId, String username) throws Exception;
    Map<String, GroupData> loadGroups() throws Exception;
    boolean createGroup(String name, int weight) throws Exception;
    boolean deleteGroup(String name) throws Exception;
    void setGroupMeta(String name, String key, String value) throws Exception;
    void setUserPrimaryGroup(UUID uniqueId, String group) throws Exception;
    void setUserPermission(UUID uniqueId, String permission, boolean value, String server, String world, long expiry) throws Exception;
    void unsetUserPermission(UUID uniqueId, String permission, String server, String world) throws Exception;
    void setGroupPermission(String group, String permission, boolean value, String server, String world, long expiry) throws Exception;
    void unsetGroupPermission(String group, String permission, String server, String world) throws Exception;
    void addUserGroup(UUID uniqueId, String group, long expiry) throws Exception;
    void removeUserGroup(UUID uniqueId, String group) throws Exception;
    void addGroupParent(String group, String parent) throws Exception;
    void removeGroupParent(String group, String parent) throws Exception;
    void audit(String actor, String action, String target, String details) throws Exception;
    long getRevision() throws Exception;
    void bumpRevision() throws Exception;
    @Override void close();
}
