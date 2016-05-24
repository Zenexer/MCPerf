package com.earth2me.mcperf.integration.ban;

import me.confuser.banmanager.BmAPI;
import me.confuser.banmanager.data.PlayerData;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Date;

class BanManagerIntegration extends BanIntegration {
    @Override
    protected void onBan(Player player, Player actor, Date expires, String reason) throws SQLException {
        PlayerData bmPlayer = BmAPI.getPlayer(player);
        PlayerData bmActor = actor == null ? BmAPI.getConsole() : BmAPI.getPlayer(actor);

        if (expires == null) {
            BmAPI.ban(bmPlayer, bmActor, reason);
        } else {
            BmAPI.ban(bmPlayer, bmActor, reason, expires.getTime());
        }
    }
}
