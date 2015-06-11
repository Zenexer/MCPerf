package com.earth2me.mcperf;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChatEvent;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@RequiredArgsConstructor
public class MonitorManager implements Listener
{
	private static final int MAX_DISPLAY_RECIPIENTS = 30;

	private final Server server;
	private final Logger logger;

	@SuppressWarnings("deprecation")
	private WeakReference<PlayerChatEvent> previousEvent;
	private Set<WeakReference<Player>> previousRecipients;
	private boolean previousCanceled;

	private void send(String message)
	{
		server.getConsoleSender().sendMessage(message);
	}

	private void send(String... lines)
	{
		server.getConsoleSender().sendMessage(lines);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	public void onPlayerChatLowest(@SuppressWarnings("deprecation") PlayerChatEvent event)
	{
		previousEvent = new WeakReference<>(event);
		previousRecipients = event.getRecipients().stream().<WeakReference<Player>>map(WeakReference::new).collect(Collectors.toSet());

		onPlayerChat(event, true);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
	public void onPlayerChatMonitor(@SuppressWarnings("deprecation") PlayerChatEvent event)
	{
		boolean intercept = previousEvent == null || previousEvent.get() != event || previousRecipients == null;

		if (intercept || !previousCanceled)
		{
			onPlayerChat(event, intercept);
		}

		previousEvent = null;
		previousRecipients = null;
		previousCanceled = false;
	}

	private void onPlayerChat(@SuppressWarnings("deprecation") PlayerChatEvent event, boolean intercept)
	{
		String world = event.getPlayer().getWorld().getName();
		String sender = event.getPlayer().getName();
		String displayName = event.getPlayer().getDisplayName();
		String message = event.getMessage();
		String messageFormat = event.getFormat();
		Set<Player> recipients = event.getRecipients();
		int recipCount = recipients.size();
		int totalCount = server.getOnlinePlayers().size();

		String prefix;
		if (intercept)
		{
			prefix = "(first-chance) ";
		}
		else
		{
			prefix = "(modified)     ";
		}

		boolean canceled =
			event.isCancelled()
			|| "".equals(messageFormat)
			|| messageFormat == null
			|| "".equals(message)
			|| message == null;
		String canceledReason = "";
		if (canceled)
		{
			canceledReason = String.format("Hard-Canceled: %s; Format: \"%s\"; Message Length: %d", event.isCancelled() ? "yes" : "no", messageFormat == null ? "[null]" : messageFormat, message == null ? -1 : message.length());
		}

		if (canceled && !intercept)
		{
			send(prefix + "<<CANCELED>> Chat event canceled by a plugin");
			send(prefix + "             Reason: " + canceledReason);
			return;
		}

		if (intercept || recipCount != previousRecipients.size())
		{
			String format;
			if (recipCount == 0)
			{
				format = "[CUSTOM-CHAT=?/%2$d:%3$s:%4$s] %5$s: %6$s";
			}
			else if (recipCount < totalCount)
			{
				format = "[LIMITED-CHAT=%d/%d:%s:%s] %s: %s";
			}
			else
			{
				format = "[CHAT=%d/%d:%s:%s] %s: %s";
			}

			send(prefix + String.format(format, recipCount, totalCount, world, sender, displayName, message));

			if (!canceled && recipCount < totalCount && recipCount <= MAX_DISPLAY_RECIPIENTS)
			{
				send(prefix + "<<RECIPIENTS> " + Joiner.on(", ").join(recipients.stream().map(OfflinePlayer::getName).toArray()));
			}
		}

		if (intercept && canceled)
		{
			send(prefix + "<<CANCELED>> Chat event canceled prior to plugin event handling");
			send(prefix + "             Reason: " + canceledReason);
			previousCanceled = true;
		}
	}

	/*
	/**
	 * Async chat event; unfortunately it can be intercepted.
	 *
	 * @param event AsyncPlayerChatEvent event details
	 * @deprecated
	 * /
	//@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
	@Deprecated
	public void onAsyncPlayerChat(AsyncPlayerChatEvent event)
	{
		String world = event.getPlayer().getWorld().getName();
		String sender = event.getPlayer().getName();
		String displayName = event.getPlayer().getDisplayName();
		String message = event.getMessage();

		server.getConsoleSender().sendMessage(String.format("[CHAT:%s:%s] %s: %s", world, sender, displayName, message));
	}
	*/

	/*
	private static class ChatEventContext
	{
		private final WeakReference<World> world;
		private final WeakReference<Player> sender;
		private final Set<WeakReference<Player>> recipients;
		@Getter
		private final String message;
		@Getter
		private final boolean isCancelled; // Excluded from hashCode/equals

		public ChatEventContext(Player sender, Set<Player> recipients, String message, boolean isCancelled)
		{
			this.world = new WeakReference<>(sender.getWorld());
			this.sender = new WeakReference<>(sender);
			this.message = message;
			this.recipients = Collections.unmodifiableSet(recipients.stream().<WeakReference<Player>>map(WeakReference::new).collect(Collectors.toSet()));
			this.isCancelled = isCancelled;
		}

		public ChatEventContext(@SuppressWarnings("deprecation") PlayerChatEvent event)
		{
			this(event.getPlayer(), event.getRecipients(), event.getMessage(), event.isCancelled());
		}

		public ChatEventContext(AsyncPlayerChatEvent event)
		{
			this(event.getPlayer(), event.getRecipients(), event.getMessage(), event.isCancelled());
		}

		public World getWorld()
		{
			return world.get();
		}

		public Player getSender()
		{
			return sender.get();
		}

		public Stream<Player> getRecipients()
		{
			return getOriginalRecipients().filter(r -> r != null);
		}

		public int getRecipientCount()
		{
			return (int)getRecipients().count();
		}

		public Stream<Player> getOriginalRecipients()
		{
			return recipients.stream().map(Reference::get);
		}

		public int getOriginalRecipientCount()
		{
			return recipients.size();
		}

		@Override
		public boolean equals(Object obj)
		{
			if (!(obj instanceof ChatEventContext))
			{
				return super.equals(obj);
			}

			ChatEventContext other = (ChatEventContext)obj;

			if (!world.equals(other.world))
			{
				return false;
			}

			if (!sender.equals(other.sender))
			{
				return false;
			}

			// Left off here; I don't think we need this class
			return super.equals(obj);
		}

		@Override
		public int hashCode()
		{
			return super.hashCode();
		}
	}
	*/
}
