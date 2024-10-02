package me.matsubara.vehicles.data;

import lombok.Getter;
import net.md_5.bungee.api.chat.Keybinds;

@Getter
public enum ActionKeybind {
    LEFT_CLICK(Keybinds.ATTACK),
    RIGHT_CLICK(Keybinds.USE);

    private final String keybind;

    ActionKeybind(String keybind) {
        this.keybind = keybind;
    }
}