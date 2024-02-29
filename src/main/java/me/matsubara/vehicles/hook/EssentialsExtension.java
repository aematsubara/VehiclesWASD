package me.matsubara.vehicles.hook;

import me.matsubara.vehicles.VehiclesPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class EssentialsExtension implements AVExtension<EssentialsExtension> {

    private com.earth2me.essentials.Essentials essentials;

    @Override
    public EssentialsExtension init(@NotNull VehiclesPlugin plugin) {
        essentials = (com.earth2me.essentials.Essentials) plugin.getServer().getPluginManager().getPlugin("Essentials");
        return this;
    }

    public List<String> getHomes(Player player) {
        com.earth2me.essentials.User user = essentials.getUser(player);
        return user.getHomes();
    }

    public Location getHome(Player player, String name) {
        com.earth2me.essentials.User user = essentials.getUser(player);
        return user.getHome(name);
    }
}