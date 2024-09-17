package me.matsubara.vehicles.util;

import com.cryptomorin.xseries.reflection.XReflection;
import com.google.common.base.Preconditions;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import me.matsubara.vehicles.VehiclesPlugin;
import net.md_5.bungee.api.ChatColor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.util.Vector;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.scanner.ScannerException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("deprecation")
public final class PluginUtils {

    private static final Color[] COLORS;
    private static final Map<String, Color> COLORS_BY_NAME = new HashMap<>();
    private static final Pattern PATTERN = Pattern.compile("&(#[\\da-fA-F]{6})");
    private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();

    public static final BlockFace[] AXIS = {
            BlockFace.NORTH,
            BlockFace.EAST,
            BlockFace.SOUTH,
            BlockFace.WEST};

    public static final BlockFace[] RADIAL = {
            BlockFace.NORTH,
            BlockFace.NORTH_EAST,
            BlockFace.EAST,
            BlockFace.SOUTH_EAST,
            BlockFace.SOUTH,
            BlockFace.SOUTH_WEST,
            BlockFace.WEST,
            BlockFace.NORTH_WEST};

    private static final Class<?> ENTITY = XReflection.getNMSClass("world.entity", "Entity");
    private static final Class<?> CRAFT_ENTITY = XReflection.getCraftClass("entity.CraftEntity");
    private static final Class<?> CRAFT_META_SKULL = XReflection.getCraftClass("inventory.CraftMetaSkull");

    private static final MethodHandle SET_PROFILE = Reflection.getMethod(CRAFT_META_SKULL, "setProfile", false, GameProfile.class);
    private static final MethodHandle SET_OWNER_PROFILE = SET_PROFILE != null ? null : Reflection.getMethod(SkullMeta.class, "setOwnerProfile", false, PlayerProfile.class);

    private static final MethodHandle getHandle = Reflection.getMethod(Objects.requireNonNull(CRAFT_ENTITY), "getHandle");
    private static final MethodHandle absMoveTo = Reflection.getMethod(
            ENTITY,
            "a",
            MethodType.methodType(void.class, double.class, double.class, double.class, float.class, float.class),
            false,
            false,
            "setLocation");

    static {
        for (Field field : Color.class.getDeclaredFields()) {
            if (!field.getType().equals(Color.class)) continue;

            try {
                COLORS_BY_NAME.put(field.getName(), (Color) field.get(null));
            } catch (IllegalAccessException ignored) {
            }
        }

        COLORS = COLORS_BY_NAME.values().toArray(new Color[0]);
    }

    public static @NotNull BlockFace getFace(float yaw, boolean subCardinal) {
        return (subCardinal ? RADIAL[Math.round(yaw / 45f) & 0x7] : AXIS[Math.round(yaw / 90f) & 0x3]).getOppositeFace();
    }

    public static @NotNull Vector getDirection(@NotNull BlockFace face) {
        int modX = face.getModX(), modY = face.getModY(), modZ = face.getModZ();
        Vector direction = new Vector(modX, modY, modZ);
        if (modX != 0 || modY != 0 || modZ != 0) direction.normalize();
        return direction;
    }

    @Contract("_, _, _ -> new")
    public static @NotNull Vector offsetVector(@NotNull Vector vector, float yawDegrees, float pitchDegrees) {
        double yaw = Math.toRadians(-yawDegrees), pitch = Math.toRadians(-pitchDegrees);

        double cosYaw = Math.cos(yaw), cosPitch = Math.cos(pitch);
        double sinYaw = Math.sin(yaw), sinPitch = Math.sin(pitch);

        double initialX, initialY, initialZ, x, y, z;

        initialX = vector.getX();
        initialY = vector.getY();
        x = initialX * cosPitch - initialY * sinPitch;
        y = initialX * sinPitch + initialY * cosPitch;

        initialZ = vector.getZ();
        initialX = x;
        z = initialZ * cosYaw - initialX * sinYaw;
        x = initialZ * sinYaw + initialX * cosYaw;

        return new Vector(x, y, z);
    }

    public static @Nullable ItemStack createHead(String url, boolean isMCUrl) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);

        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) return null;

        applySkin(meta, url, isMCUrl);
        item.setItemMeta(meta);

        return item;
    }

    public static void applySkin(SkullMeta meta, String texture, boolean isUrl) {
        applySkin(meta, UUID.randomUUID(), texture, isUrl);
    }

    public static void applySkin(SkullMeta meta, UUID uuid, String texture, boolean isUrl) {
        try {
            // If the serialized profile field isn't set, ItemStack#isSimilar() and ItemStack#equals() throw an error.
            if (SET_PROFILE != null) {
                GameProfile profile = new GameProfile(uuid, "");

                String value = isUrl ? new String(Base64.getEncoder().encode(String
                        .format("{textures:{SKIN:{url:\"%s\"}}}", "http://textures.minecraft.net/texture/" + texture)
                        .getBytes())) : texture;

                profile.getProperties().put("textures", new Property("textures", value));
                SET_PROFILE.invoke(meta, profile);
            } else if (SET_OWNER_PROFILE != null) {
                PlayerProfile profile = Bukkit.createPlayerProfile(uuid, "");

                PlayerTextures textures = profile.getTextures();
                String url = isUrl ? "http://textures.minecraft.net/texture/" + texture : getURLFromTexture(texture);
                textures.setSkin(new URL(url));

                profile.setTextures(textures);
                SET_OWNER_PROFILE.invoke(meta, profile);
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    public static String getURLFromTexture(String texture) {
        // String decoded = new String(Base64.getDecoder().decode(texture));
        // return new URL(decoded.substring("{\"textures\":{\"SKIN\":{\"url\":\"".length(), decoded.length() - "\"}}}".length()));

        // Decode B64.
        String decoded = new String(Base64.getDecoder().decode(texture));

        // Get url from json.
        return JsonParser.parseString(decoded).getAsJsonObject()
                .getAsJsonObject("textures")
                .getAsJsonObject("SKIN")
                .get("url")
                .getAsString();
    }

    public static @NotNull String translate(String message) {
        Preconditions.checkArgument(message != null, "Message can't be null.");

        Matcher matcher = PATTERN.matcher(ChatColor.translateAlternateColorCodes('&', message));
        StringBuilder buffer = new StringBuilder();

        while (matcher.find()) {
            matcher.appendReplacement(buffer, ChatColor.of(matcher.group(1)).toString());
        }

        return matcher.appendTail(buffer).toString();
    }

    @Contract("_ -> param1")
    public static @NotNull List<String> translate(@NotNull List<String> messages) {
        messages.replaceAll(PluginUtils::translate);
        return messages;
    }

    public static String[] splitData(String string) {
        String[] split = StringUtils.split(StringUtils.deleteWhitespace(string), ',');
        if (split.length == 0) split = StringUtils.split(string, ' ');
        return split;
    }

    public static Color getColor(@NotNull String string) {
        if (string.equalsIgnoreCase("$RANDOM")) return getRandomColor();

        if (string.matches(PATTERN.pattern())) {
            java.awt.Color temp = ChatColor.of(string.substring(1)).getColor();
            return Color.fromRGB(temp.getRed(), temp.getGreen(), temp.getBlue());
        }

        return COLORS_BY_NAME.get(string);
    }

    public static <T extends Enum<T>> T getRandomFromEnum(@NotNull Class<T> clazz) {
        T[] constants = clazz.getEnumConstants();
        return constants[RANDOM.nextInt(0, constants.length)];
    }

    public static <T extends Enum<T>> T getOrEitherRandomOrNull(Class<T> clazz, @NotNull String name) {
        if (name.equalsIgnoreCase("$RANDOM")) return getRandomFromEnum(clazz);
        return getOrNull(clazz, name);
    }

    public static @Nullable PotionType getValidPotionType(@NotNull String name) {
        if (name.equalsIgnoreCase("$RANDOM")) {
            PotionType type;
            do {
                type = getRandomFromEnum(PotionType.class);
            } while (isInvalidPotionType(type));
            return type;
        }

        PotionType type = getOrNull(PotionType.class, name);
        return isInvalidPotionType(type) ? null : type;
    }

    private static boolean isInvalidPotionType(PotionType type) {
        return type == null
                || type.name().startsWith("LONG_")
                || type.name().startsWith("STRONG_")
                || type.getEffectType() == null;
    }

    public static <T extends Enum<T>> T getOrNull(Class<T> clazz, String name) {
        return getOrDefault(clazz, name, null);
    }

    public static <T extends Enum<T>> T getOrDefault(Class<T> clazz, String name, T defaultValue) {
        try {
            return Enum.valueOf(clazz, name);
        } catch (IllegalArgumentException exception) {
            return defaultValue;
        }
    }

    public static int getRangedAmount(@NotNull String string) {
        String[] data = string.split("-");
        if (data.length == 1) {
            try {
                return Integer.parseInt(data[0]);
            } catch (IllegalArgumentException ignored) {
            }
        } else if (data.length == 2) {
            try {
                int min = Integer.parseInt(data[0]);
                int max = Integer.parseInt(data[1]);
                return RANDOM.nextInt(min, max + 1);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return 1;
    }

    public static Color getRandomColor() {
        return COLORS[RANDOM.nextInt(0, COLORS.length)];
    }

    public static void teleportWithPassengers(@NotNull LivingEntity living, Location targetLocation) {
        if (getHandle == null || absMoveTo == null) return;

        // We can't teleport entities with passengers with the API.
        try {
            Object nmsEntity = getHandle.invoke(CRAFT_ENTITY.cast(living));
            absMoveTo.invoke(
                    nmsEntity,
                    targetLocation.getX(),
                    targetLocation.getY(),
                    targetLocation.getZ(),
                    targetLocation.getYaw(),
                    targetLocation.getPitch());
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    public static @Nullable String itemStackArrayToBase64(ItemStack @NotNull [] items) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            dataOutput.writeInt(items.length);

            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }

            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public static ItemStack @Nullable [] itemStackArrayFromBase64(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

            ItemStack[] items = new ItemStack[dataInput.readInt()];
            for (int i = 0; i < items.length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }

            dataInput.close();
            return items;
        } catch (ReflectiveOperationException | IOException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public static @Nullable FileConfiguration reloadConfig(VehiclesPlugin plugin, @NotNull File file, @Nullable Consumer<File> error) {
        File backup = null;
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            String time = format.format(new Date(System.currentTimeMillis()));

            // When error is null, that means that the file has already regenerated, so we don't need to create a backup.
            if (error != null) {
                backup = new File(file.getParentFile(), file.getName().split("\\.")[0] + "_" + time + ".bak");
                FileUtils.copyFile(file, backup);
            }

            FileConfiguration configuration = new YamlConfiguration();
            configuration.load(file);

            if (backup != null) FileUtils.deleteQuietly(backup);

            return configuration;
        } catch (IOException | InvalidConfigurationException exception) {
            Logger logger = plugin.getLogger();

            logger.severe("An error occurred while reloading the file {" + file.getName() + "}.");
            if (backup != null
                    && exception instanceof InvalidConfigurationException invalid
                    && invalid.getCause() instanceof ScannerException scanner) {
                handleScannerError(backup, scanner.getProblemMark().getLine());
                logger.severe("The file will be restarted and a copy of the old file will be saved indicating which line had an error.");
            } else {
                logger.severe("The file will be restarted and a copy of the old file will be saved.");
            }

            if (error == null) {
                exception.printStackTrace();
                return null;
            }

            // Only replace the file if an exception ocurrs.
            FileUtils.deleteQuietly(file);
            error.accept(file);

            return reloadConfig(plugin, file, null);
        }
    }

    private static void handleScannerError(@NotNull File backup, int line) {
        try {
            Path path = backup.toPath();

            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            lines.set(line, lines.get(line) + " <--------------------< ERROR <--------------------<");

            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    @Contract(pure = true)
    public static boolean is(int check, int @NotNull ... checks) {
        for (int i : checks) {
            if (i == check) return true;
        }
        return false;
    }
}