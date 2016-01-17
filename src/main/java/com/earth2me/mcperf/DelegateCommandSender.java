package com.earth2me.mcperf;

import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

import java.util.Set;

public class DelegateCommandSender implements ConsoleCommandSender {
    private final ConsoleCommandSender underlying;

    public DelegateCommandSender(Server server) {
        this.underlying = server.getConsoleSender();
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public void sendMessage(String s) {
        underlying.sendMessage(s);
    }

    @Override
    public void sendMessage(String[] strings) {
        underlying.sendMessage(strings);
    }

    @Override
    public Server getServer() {
        return underlying.getServer();
    }

    @Override
    public String getName() {
        return underlying.getName();
    }

    @Override
    public boolean isPermissionSet(String s) {
        return underlying.isPermissionSet(s);
    }

    @Override
    public boolean isPermissionSet(Permission permission) {
        return underlying.isPermissionSet(permission);
    }

    @Override
    public boolean hasPermission(String s) {
        return underlying.hasPermission(s);
    }

    @Override
    public boolean hasPermission(Permission permission) {
        return underlying.hasPermission(permission);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String s, boolean b) {
        return underlying.addAttachment(plugin, s, b);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        return underlying.addAttachment(plugin);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String s, boolean b, int i) {
        return underlying.addAttachment(plugin, s, b, i);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, int i) {
        return underlying.addAttachment(plugin, i);
    }

    @Override
    public void removeAttachment(PermissionAttachment permissionAttachment) {
        underlying.removeAttachment(permissionAttachment);
    }

    @Override
    public void recalculatePermissions() {
        underlying.recalculatePermissions();
    }

    @Override
    public Set<PermissionAttachmentInfo> getEffectivePermissions() {
        return underlying.getEffectivePermissions();
    }

    @Override
    public boolean isOp() {
        return underlying.isOp();
    }

    @Override
    public void setOp(boolean b) {
        underlying.setOp(b);
    }

    @Override
    public boolean isConversing() {
        return underlying.isConversing();
    }

    @Override
    public void acceptConversationInput(String s) {
        underlying.acceptConversationInput(s);
    }

    @Override
    public boolean beginConversation(Conversation conversation) {
        return underlying.beginConversation(conversation);
    }

    @Override
    public void abandonConversation(Conversation conversation) {
        underlying.abandonConversation(conversation);
    }

    @Override
    public void abandonConversation(Conversation conversation, ConversationAbandonedEvent conversationAbandonedEvent) {
        underlying.abandonConversation(conversation, conversationAbandonedEvent);
    }

    @Override
    public void sendRawMessage(String s) {
        underlying.sendRawMessage(s);
    }
}
