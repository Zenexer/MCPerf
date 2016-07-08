package com.earth2me.mcperf.integration.mute;

import com.earth2me.mcperf.Util;
import me.confuser.banmanager.BmAPI;
import me.confuser.banmanager.data.PlayerData;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.Duration;

class BanManagerIntegration extends MuteIntegration {
    @Override
    protected void onMute(Player player, Player actor, Duration duration, String reason) throws SQLException {
        PlayerData bmPlayer = BmAPI.getPlayer(player);
        PlayerData bmActor = actor == null ? BmAPI.getConsole() : BmAPI.getPlayer(actor);

        if (duration == null) {
            BmAPI.mute(bmPlayer, bmActor, reason);
        } else {
            BmAPI.mute(bmPlayer, bmActor, reason, Util.durationFromNow(duration));
        }
    }
}
