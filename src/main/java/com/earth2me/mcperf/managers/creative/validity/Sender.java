package com.earth2me.mcperf.managers.creative.validity;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

public final class Sender {
    @Getter
    private final Player player;
    @Getter
    private final HumanEntity human;
    @Getter
    private final String name;
    @Getter
    @Setter
    private boolean clean = false;

    public Sender(Player player) {
        this.player = player;
        this.human = player;
        if (player == null) {
            this.name = "[unknown:Player]";
        } else {
            this.name = player.getName();
            assert this.name != null;
        }
    }

    public Sender(HumanEntity human) {
        this.human = human;

        if (human == null) {
            this.player = null;
            this.name = "[unknown:HumanEntity]";
            return;
        }

        this.name = human.getName();
        assert this.name != null;

        if (human instanceof Player) {
            this.player = (Player) human;
        } else if (human.getUniqueId() != null) {
            this.player = Bukkit.getServer().getPlayer(human.getUniqueId());
        } else {
            this.player = null;
        }
    }

    private Sender(String name) {
        assert name != null;

        this.player = null;
        this.human = null;
        this.name = name;
    }

    public static Sender anon(Event event) {
        return new Sender(String.format("[event:%s]", event.getEventName()));
    }

    public boolean isAnonymous() {
        if (human == null) {
            assert player == null;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        assert name != null;
        return name;
    }
}
