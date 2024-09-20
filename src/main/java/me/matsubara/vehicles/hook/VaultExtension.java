package me.matsubara.vehicles.hook;

import lombok.Getter;
import me.matsubara.vehicles.VehiclesPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

public class VaultExtension implements EconomyExtension<VaultExtension> {

    private Economy economy;
    private VehiclesPlugin plugin;
    private @Getter boolean enabled;

    @Override
    public VaultExtension init(@NotNull VehiclesPlugin plugin) {
        this.plugin = plugin;

        RegisteredServiceProvider<Economy> provider = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (provider == null) {
            plugin.getLogger().severe("Vault found, you need to install an economy provider (EssentialsX, CMI, PlayerPoints, etc...), disabling economy support...");
            return null;
        }

        Plugin providerPlugin = provider.getPlugin();
        plugin.getLogger().info("Using {" + providerPlugin.getDescription().getFullName() + "} as the economy provider.");

        economy = provider.getProvider();
        enabled = true;
        return this;
    }

    @Override
    public boolean has(Player player, double money) {
        return economy.has(player, money);
    }

    @Override
    public String format(double money) {
        return economy.format(money);
    }

    @Override
    public boolean takeMoney(Player player, double money) {
        EconomyResponse response = economy.withdrawPlayer(player, money);
        if (response.transactionSuccess()) return true;

        plugin.getLogger().warning("It wasn't possible to withdraw {" + format(money) + "} to {" + player.getName() + "}.");
        return false;
    }
}