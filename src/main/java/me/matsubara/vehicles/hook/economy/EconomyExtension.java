package me.matsubara.vehicles.hook.economy;

import me.matsubara.vehicles.hook.AVExtension;
import org.bukkit.entity.Player;

public interface EconomyExtension<T> extends AVExtension<T> {

    boolean isEnabled();

    boolean has(Player player, double money);

    String format(double money);

    boolean takeMoney(Player player, double money);
}