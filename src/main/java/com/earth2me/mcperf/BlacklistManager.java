package com.earth2me.mcperf;

import com.earth2me.mcperf.config.ConfigSetting;
import com.earth2me.mcperf.config.ConfigSettingSetter;
import com.earth2me.mcperf.ob.ContainsConfig;
import lombok.Getter;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@ContainsConfig
public class BlacklistManager extends Manager {
    private static final int MAX_BLOCK_ID = 255;

    @Getter
    @ConfigSetting
    private Set<Integer> blocks;
    private boolean[] optimizedBlocks = new boolean[MAX_BLOCK_ID];

    public BlacklistManager(String id, Server server, Logger logger, MCPerfPlugin plugin) {
        super(id, server, logger, plugin, false);
    }

    @ConfigSettingSetter
    public void setBlocks(Set<Integer> value) {
        if (value == null) {
            return;
        }

        this.blocks = value;

        boolean[] blocks = new boolean[MAX_BLOCK_ID];
        for (int id : value) {
            if (id <= 0 || id > MAX_BLOCK_ID) {
                continue;
            }
            blocks[id] = true;
        }

        this.optimizedBlocks = blocks;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isEnabled()) {
            return;
        }

        Block block = event.getBlockPlaced();
        // Important for performance
        @SuppressWarnings("deprecation")
        int id = block.getTypeId();

        if (id <= 0 || id >= MAX_BLOCK_ID) {
            getLogger().log(Level.SEVERE, "Found block ID " + id + ", which is less than 0 or greater than " + MAX_BLOCK_ID);
            return;
        }

        if (optimizedBlocks[id]) {
            event.setCancelled(true);
            event.setBuild(false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!isEnabled()) {
            return;
        }

        scanChunk(event.getChunk());
    }

    private void scanChunk(final Chunk chunk) {
        final boolean[] blocks = this.optimizedBlocks;

        getServer().getScheduler().runTaskAsynchronously(getPlugin(), () -> {
            if (!chunk.isLoaded()) {
                return;
            }

            int maxY = chunk.getWorld().getMaxHeight();
            final List<Block> removalQueue = new LinkedList<>();

            try {
                for (int x = 0; x < 16; x++) {
                    // It's important that we go from top to bottom.  Otherwise, cacti and such will
                    // break and drop items.
                    for (int y = maxY; y >= 0; y--) {
                        for (int z = 0; z < 16; z++) {
                            Block block = chunk.getBlock(x, y, z);
                            if (block == null) {
                                continue;
                            }

                            // Needed for performance boost
                            @SuppressWarnings("deprecation") int id = block.getTypeId();

                            // It's efficient for us to compare against zero.
                            if (id == 0) {
                                continue;
                            }

                            if (blocks[id]) {
                                removalQueue.add(block);
                            }
                        }
                    }
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                getLogger().log(Level.SEVERE, "[MCPerf] Found block ID less than 0 or greater than " + MAX_BLOCK_ID, e);
                return;
            }

            if (removalQueue.isEmpty()) {
                return;
            }

            getLogger().log(Level.INFO, String.format("Removing %d blacklisted blocks in chunk (%s, %d, %d)", removalQueue.size(), chunk.getWorld().getName(), chunk.getX(), chunk.getZ()));

            getServer().getScheduler().callSyncMethod(getPlugin(), () -> {
                if (!chunk.isLoaded()) {
                    return null;
                }

                for (Block block : removalQueue) {
                    block.setType(Material.AIR);
                }

                return null;
            });
        });
    }
}
