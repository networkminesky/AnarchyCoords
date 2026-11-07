package com.jtprince.coordinateoffset.paper.anarchy;

import com.jtprince.coordinateoffset.CoordinateOffsetCore;
import com.jtprince.coordinateoffset.paper.CoordinateOffsetPaperPlugin;
import com.jtprince.coordinateoffset.paper.adapter.PaperLocation;
import com.jtprince.coordinateoffset.paper.adapter.PaperOffsetPlayer;
import com.jtprince.coordinateoffset.provider.OffsetProviderContext;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Polls per-player /config changes and reapplies offsets on the player's region thread (Folia-safe).
 */
@NullMarked
public final class PlayerConfigToggleWatcher {
    private static final long POLL_PERIOD_TICKS = 20L;

    private final CoordinateOffsetPaperPlugin plugin;
    private final CoordinateOffsetCore core;
    private final AnarchyCorePlayerConfigIntegration playerConfig;
    private final Map<UUID, Boolean> lastRandomizeState = new ConcurrentHashMap<>();
    private @Nullable ScheduledTask pollTask;

    public PlayerConfigToggleWatcher(
        CoordinateOffsetPaperPlugin plugin,
        CoordinateOffsetCore core,
        AnarchyCorePlayerConfigIntegration playerConfig
    ) {
        this.plugin = plugin;
        this.core = core;
        this.playerConfig = playerConfig;
    }

    public boolean pollTaskIsInactive() {
        return pollTask == null;
    }

    public void start() {
        if (pollTask != null) {
            return;
        }
        if (playerConfig.getRandomizeCoordsOption() == null && !pollTaskIsNeeded()) {
            return;
        }
        pollTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
            (Plugin) plugin,
            task -> pollOnlinePlayers(),
            POLL_PERIOD_TICKS,
            POLL_PERIOD_TICKS
        );
    }

    private boolean pollTaskIsNeeded() {
        try {
            return anarchy.minesky.core.api.MineSkyAPI.isAvailable();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void stop() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
        lastRandomizeState.clear();
    }

    public void onPlayerJoined(Player player) {
        seed(player);
        if (playerConfig.shouldRandomizeCoords(player)) {
            player.getScheduler().run(
                (Plugin) plugin,
                task -> applyPreferenceChange(player),
                null
            );
        }
    }

    public void seed(Player player) {
        lastRandomizeState.put(player.getUniqueId(), playerConfig.shouldRandomizeCoords(player));
    }

    public void forget(Player player) {
        lastRandomizeState.remove(player.getUniqueId());
    }

    private void pollOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean current = playerConfig.shouldRandomizeCoords(player);
            UUID id = player.getUniqueId();
            Boolean previous = lastRandomizeState.get(id);

            if (previous != null && previous.booleanValue() != current) {
                lastRandomizeState.put(id, current);
                player.getScheduler().run(
                    (Plugin) plugin,
                    task -> applyPreferenceChange(player),
                    null
                );
            } else {
                lastRandomizeState.put(id, current);
            }
        }
        lastRandomizeState.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    /**
     * Reaplica o offset conforme a preferência atual (útil após registro no AnarchyCore ou mudança no /config).
     */
    public void applyPreferenceChange(Player player) {
        PaperOffsetPlayer offsetPlayer = new PaperOffsetPlayer(player);
        var change = core.getOffsetHolder().generateNextOffset(
            offsetPlayer,
            null,
            new PaperLocation(player.getLocation()),
            OffsetProviderContext.ProvideReason.PLUGIN_REGENERATE
        );
        if (change.offsetChanged() || playerConfig.shouldRandomizeCoords(player)) {
            core.getAdapter().getOffsetSwapper().forceOffsetSwap(offsetPlayer);
        }
    }

    public void syncAllOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            seed(player);
            player.getScheduler().run(
                (Plugin) plugin,
                task -> applyPreferenceChange(player),
                null
            );
        }
    }
}
