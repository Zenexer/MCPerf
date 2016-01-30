package com.earth2me.mcperf;

import com.earth2me.mcperf.config.ConfigSetting;
import com.earth2me.mcperf.config.ConfigSettingSetter;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProjectileManager extends Manager {
    @Getter
    @ConfigSetting
    private long projectileCleanupInterval = 30 * 20;
    @Getter
    @Setter
    @ConfigSetting
    private boolean chunkLoadCleanupEnabled = true;
    private BukkitTask projectileCleanupTask;

    public ProjectileManager(Server server, Logger logger, MCPerfPlugin plugin) {
        super(server, logger, plugin);
    }

    @ConfigSettingSetter
    public void setProjectileCleanupInterval(long value) {
        projectileCleanupInterval = value;
        resetProjectileCleanupTask();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!chunkLoadCleanupEnabled) {
            return;
        }

        Entity[] entities = event.getChunk().getEntities();

        for (Entity entity : entities) {
            if (entity instanceof Projectile || entity instanceof Firework) {
                entity.remove();
                break;
            }
        }
    }

    private void resetProjectileCleanupTask() {
        if (projectileCleanupTask != null) {
            projectileCleanupTask.cancel();
            projectileCleanupTask = null;
        }

        if (projectileCleanupInterval > 0) {
            getLogger().log(Level.INFO, String.format("Projectile cleanup running every %d ticks.", projectileCleanupInterval));
            projectileCleanupTask = getServer().getScheduler().runTaskTimer(getPlugin(), this::cleanupProjectiles, projectileCleanupInterval, projectileCleanupInterval);
        } else {
            getLogger().log(Level.INFO, "Projectile cleanup disabled.");
        }
    }

    public void cleanupProjectiles() {
        List<World> worlds = getServer().getWorlds();
        final Entity[][] entities = new Entity[worlds.size()][];

        for (int w = 0; w < worlds.size(); w++) {
            List<Entity> entityList = worlds.get(w).getEntities();
            entities[w] = entityList.toArray(new Entity[entityList.size()]);
        }

        getServer().getScheduler().runTaskAsynchronously(getPlugin(), () -> {
            final List<Entity> removalQueue = new LinkedList<>();

            for (Entity[] we : entities) {
                for (Entity entity : we) {
                    int maxAge;

                    switch (entity.getType()) {
                        case ARMOR_STAND:
                        case BLAZE:
                        case BOAT:
                        case CAVE_SPIDER:
                        case CHICKEN:
                        case COMPLEX_PART:
                        case COW:
                        case CREEPER:
                        case ENDER_CRYSTAL:
                        case ENDER_DRAGON:
                        case ENDER_SIGNAL:
                        case ENDERMAN:
                        case ENDERMITE:
                        case GIANT:
                        case GUARDIAN:
                        case HORSE:
                        case IRON_GOLEM:
                        case ITEM_FRAME:
                        case LEASH_HITCH:
                        case LIGHTNING:
                        case MAGMA_CUBE:
                        case MINECART:
                        case MINECART_CHEST:
                        case MINECART_COMMAND:
                        case MINECART_FURNACE:
                        case MINECART_HOPPER:
                        case MINECART_MOB_SPAWNER:
                        case MINECART_TNT:
                        case MUSHROOM_COW:
                        case OCELOT:
                        case PAINTING:
                        case PIG:
                        case PIG_ZOMBIE:
                        case PLAYER:
                        case RABBIT:
                        case SHEEP:
                        case SILVERFISH:
                        case SKELETON:
                        case SLIME:
                        case SNOWMAN:
                        case SPIDER:
                        case SQUID:
                        case VILLAGER:
                        case WEATHER:
                        case WITCH:
                        case WITHER:
                        case WOLF:
                        case ZOMBIE:
                            continue;

                        case FALLING_BLOCK:
                        case PRIMED_TNT:
                            maxAge = 30 * 20;
                            break;

                        case FIREWORK:
                            maxAge = 45 * 20;
                            break;

                        case GHAST:
                        case BAT:
                            maxAge = 10 * 60 * 20;
                            break;

                        case DROPPED_ITEM:
                        case EXPERIENCE_ORB:
                            maxAge = 8 * 60 * 20;
                            break;

                        case ARROW:
                        case EGG:
                        case ENDER_PEARL:
                        case FIREBALL:
                        case FISHING_HOOK:
                        case SMALL_FIREBALL:
                        case SNOWBALL:
                        case SPLASH_POTION:
                        case THROWN_EXP_BOTTLE:
                        case WITHER_SKULL:
                            maxAge = 20 * 20;
                            break;

                        case UNKNOWN:
                        default:
                            if (entity instanceof Creature && "CraftCreature".equals(entity.getClass().getSimpleName())) {
                                // This happens occasionally, for some weird reason.  What sort of creature is it?
                                // We'll never know.  A placeholder?
                                continue;
                            }

                            getLogger().log(Level.WARNING, "Unknown entity type: " + entity.getClass().getCanonicalName());

                            if (entity instanceof Projectile) {
                                maxAge = 20 * 20;
                            } else {
                                continue;
                            }
                            break;
                    }

                    if (entity.getTicksLived() > maxAge) {
                        removalQueue.add(entity);
                    }
                }
            }

            getServer().getScheduler().callSyncMethod(getPlugin(), () -> {
                removalQueue.forEach(Entity::remove);
                return null;
            });
        });
    }
}
