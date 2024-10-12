package me.matsubara.vehicles.data;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import me.matsubara.vehicles.util.PluginUtils;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class SoundWrapper {

    private Sound sound;
    private float volume = Float.MIN_VALUE;
    private float pitch = Float.MIN_VALUE;
    private boolean valid;

    public SoundWrapper(String soundName) {
        if (Strings.isNullOrEmpty(soundName)) return;

        String[] split = PluginUtils.splitData(soundName);
        if (split == null || split.length == 0) return;

        Sound sound = PluginUtils.getOrNull(Sound.class, split[0]);
        if (sound == null) return;

        float volume = getValidFloat(split, 1);
        float pitch = getValidFloat(split, 2);

        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;

        valid = volume != Float.MIN_VALUE && pitch != Float.MIN_VALUE;
    }

    public void playAt(@NotNull Location location) {
        if (!valid) return;

        World world = location.getWorld();
        Preconditions.checkNotNull(world);

        world.playSound(location, sound, volume, pitch);
    }

    @Contract(pure = true)
    private float getValidFloat(String @NotNull [] data, int index) {
        try {
            return Float.parseFloat(data[index]);
        } catch (NumberFormatException | IndexOutOfBoundsException exception) {
            return 1.0f;
        }
    }
}