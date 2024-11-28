package me.matsubara.vehicles.model.stand;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;

public interface IStand {

    void spawn(Player player);

    void destroy(Player player);

    void destroy();

    StandSettings getSettings();

    int getId();

    Location getLocation();

    void setLocation(Location location);

    void sendMetadata(Collection<Player> players);

    void sendEquipment(Collection<Player> players);

    void teleport(Collection<Player> players, Location to);

    void teleport(Player player, Location to);
}