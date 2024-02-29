package me.matsubara.vehicles.hook;

import lombok.Getter;
import me.matsubara.vehicles.VehiclesPlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

@Getter
public class VaultExtension implements AVExtension<VaultExtension> {

    private net.milkbowl.vault.economy.Economy economy;
    private VehiclesPlugin plugin;
    private boolean enabled;

    @Override
    public VaultExtension init(@NotNull VehiclesPlugin plugin) {
        this.plugin = plugin;

        RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> provider = plugin.getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (provider == null) {
            plugin.getLogger().severe("Vault found, you need to install an economy provider (like EssentialsX, CMI, etc...), disabling economy support...");
            return null;
        }

        Plugin providerPlugin = provider.getPlugin();
        plugin.getLogger().info("Using {" + providerPlugin.getDescription().getFullName() + "} as the economy provider.");

        economy = provider.getProvider();
        enabled = true;
        return this;
    }

    public boolean has(Player player, double money) {
        return economy.has(player, money);
    }

    public boolean takeMoney(Player player, double money) {
        net.milkbowl.vault.economy.EconomyResponse response = economy.withdrawPlayer(player, money);
        if (response.transactionSuccess()) return true;

        plugin.getLogger().warning("It wasn't possible to withdraw $" + money + " to {" + player.getName() + "}.");
        return false;
    }
}