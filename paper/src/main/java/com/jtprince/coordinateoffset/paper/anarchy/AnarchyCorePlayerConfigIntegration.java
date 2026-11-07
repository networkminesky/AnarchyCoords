package com.jtprince.coordinateoffset.paper.anarchy;

import anarchy.minesky.core.api.MineSkyAPI;
import anarchy.minesky.core.api.playerconfig.PlayerConfigAPI;
import anarchy.minesky.core.api.playerconfig.PlayerConfigBooleanOption;
import anarchy.minesky.core.api.playerconfig.PlayerConfigBooleanOptionSpec;
import anarchy.minesky.core.api.playerconfig.PlayerConfigCategorySpec;
import anarchy.minesky.core.api.playerconfig.PlayerConfigRegistry;
import com.jtprince.coordinateoffset.adapter.OffsetPlayer;
import com.jtprince.coordinateoffset.paper.CoordinateOffsetPaperPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Registers CoordinateOffset options in AnarchyCore {@code /config} and answers whether coordinates should be randomized.
 */
@NullMarked
public final class AnarchyCorePlayerConfigIntegration {
    private final CoordinateOffsetPaperPlugin plugin;
    private @Nullable PlayerConfigBooleanOption randomizeCoordsOption;

    public AnarchyCorePlayerConfigIntegration(CoordinateOffsetPaperPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers category and boolean option when AnarchyCore is present.
     *
     * @return {@code true} if registration succeeded or was already present.
     */
    public boolean tryRegister() {
        if (!MineSkyAPI.isAvailable()) {
            plugin.getLogger().info("AnarchyCore não detectado; opção /config de randomização de coordenadas desativada.");
            return false;
        }

        PlayerConfigAPI configApi;
        try {
            configApi = MineSkyAPI.playerConfig();
        } catch (Throwable t) {
            plugin.getLogger().warning("Falha ao obter PlayerConfigAPI: " + t.getMessage());
            return false;
        }

        PlayerConfigRegistry registry = configApi.registry();

        try {
            registry.registerCategory(
                plugin,
                PlayerConfigCategorySpec.builder(CoordinateOffsetPlayerConfigKeys.CATEGORY_COORDS)
                    .title(Component.text("Coordenadas"))
                    .buttonLabel(Component.text("Coordenadas"))
                    .iconMaterial(Material.COMPASS)
                    .sortOrder(90)
                    .build());
        } catch (IllegalStateException duplicate) {
            // Category already registered (reload / another instance)
        }

        try {
            randomizeCoordsOption = registry.registerBooleanOption(
                plugin,
                PlayerConfigBooleanOptionSpec.builder(
                        CoordinateOffsetPlayerConfigKeys.RANDOMIZE_COORDS,
                        CoordinateOffsetPlayerConfigKeys.CATEGORY_COORDS)
                    .label(Component.text("Randomizar Coordenadas"))
                    .defaultValue(false)
                    .build());
        } catch (IllegalStateException duplicate) {
            randomizeCoordsOption = registry.booleanOption(CoordinateOffsetPlayerConfigKeys.RANDOMIZE_COORDS)
                .orElse(null);
        }

        if (randomizeCoordsOption == null) {
            plugin.getLogger().warning("Não foi possível registrar opção " + CoordinateOffsetPlayerConfigKeys.RANDOMIZE_COORDS);
            return false;
        }

        plugin.getLogger().info("Integração com /config (AnarchyCore) ativa.");
        return true;
    }

    public @Nullable PlayerConfigBooleanOption getRandomizeCoordsOption() {
        return randomizeCoordsOption;
    }

    /**
     * @return {@code true} quando a randomização deve ser aplicada. Com AnarchyCore: opt-in (padrão {@code false}).
     * Sem AnarchyCore: comportamento clássico do plugin (obfuscação ativa).
     */
    public boolean shouldRandomizeCoords(OffsetPlayer player) {
        if (!(player.getPlatformPlayerObject() instanceof Player bukkitPlayer)) {
            return !isAnarchyCoreIntegrationActive();
        }
        return shouldRandomizeCoords(bukkitPlayer);
    }

    public boolean shouldRandomizeCoords(Player player) {
        PlayerConfigBooleanOption option = randomizeCoordsOption;
        if (option != null) {
            try {
                return option.get(player);
            } catch (Throwable t) {
                return false;
            }
        }
        if (!MineSkyAPI.isAvailable()) {
            return true;
        }
        try {
            return MineSkyAPI.playerConfig().getBoolean(CoordinateOffsetPlayerConfigKeys.RANDOMIZE_COORDS, player);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** AnarchyCore carregado ou opção já registrada neste plugin. */
    public boolean isAnarchyCoreIntegrationActive() {
        return randomizeCoordsOption != null || MineSkyAPI.isAvailable();
    }
}
