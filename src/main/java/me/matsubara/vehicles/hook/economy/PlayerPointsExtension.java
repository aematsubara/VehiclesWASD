package me.matsubara.vehicles.hook.economy;

import me.matsubara.vehicles.VehiclesPlugin;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.black_ixx.playerpoints.manager.LocaleManager;
import org.black_ixx.playerpoints.util.PointsUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PlayerPointsExtension implements EconomyExtension<PlayerPointsExtension> {

    private VehiclesPlugin plugin;
    private PlayerPointsAPI api;
    private LocaleManager localeManager;

    @Override
    public PlayerPointsExtension init(@NotNull VehiclesPlugin plugin) {
        this.plugin = plugin;
        PlayerPoints instance = PlayerPoints.getInstance();
        this.api = instance.getAPI();
        this.localeManager = instance.getManager(LocaleManager.class);
        plugin.getLogger().info("Using {" + instance.getDescription().getFullName() + "} as the economy provider.");
        return this;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean has(@NotNull Player player, double money) {
        return api.look(player.getUniqueId()) >= money;
    }

    @Override
    public String format(double money) {
        return PointsUtils.formatPoints((int) money) + " " + (money == 1 ? this.currencyNameSingular() : this.currencyNamePlural());
    }

    @Override
    public boolean takeMoney(@NotNull Player player, double money) {
        boolean result = api.take(player.getUniqueId(), (int) money);
        if (result) return true;

        plugin.getLogger().warning("It wasn't possible to withdraw {" + format(money) + "} to {" + player.getName() + "}.");
        return false;
    }

    public String currencyNamePlural() {
        return localeManager.getLocaleMessage("currency-plural");
    }

    public String currencyNameSingular() {
        return localeManager.getLocaleMessage("currency-singular");
    }
}