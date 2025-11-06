package me.matsubara.vehicles;

import com.cryptomorin.xseries.reflection.XReflection;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.EventManager;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.tchristofferson.configupdater.ConfigUpdater;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import lombok.AccessLevel;
import lombok.Getter;
import me.matsubara.vehicles.command.VehiclesCommands;
import me.matsubara.vehicles.data.ActionKeybind;
import me.matsubara.vehicles.data.ShopVehicle;
import me.matsubara.vehicles.files.Config;
import me.matsubara.vehicles.files.Messages;
import me.matsubara.vehicles.files.config.ConfigValue;
import me.matsubara.vehicles.hook.AVExtension;
import me.matsubara.vehicles.hook.EssentialsExtension;
import me.matsubara.vehicles.hook.WGExtension;
import me.matsubara.vehicles.hook.economy.EconomyExtension;
import me.matsubara.vehicles.hook.economy.PlayerPointsExtension;
import me.matsubara.vehicles.hook.economy.VaultExtension;
import me.matsubara.vehicles.listener.EntityListener;
import me.matsubara.vehicles.listener.InventoryListener;
import me.matsubara.vehicles.listener.protocol.UseEntity;
import me.matsubara.vehicles.manager.InputManager;
import me.matsubara.vehicles.manager.StandManager;
import me.matsubara.vehicles.manager.VehicleManager;
import me.matsubara.vehicles.manager.targets.TypeTarget;
import me.matsubara.vehicles.manager.targets.TypeTargetManager;
import me.matsubara.vehicles.util.GlowingEntities;
import me.matsubara.vehicles.util.ItemBuilder;
import me.matsubara.vehicles.util.PluginUtils;
import me.matsubara.vehicles.util.Shape;
import me.matsubara.vehicles.vehicle.Vehicle;
import me.matsubara.vehicles.vehicle.VehicleData;
import me.matsubara.vehicles.vehicle.VehicleType;
import org.apache.commons.lang3.text.WordUtils;
import org.bstats.bukkit.Metrics;
import org.bukkit.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.security.CodeSource;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Getter
public final class VehiclesPlugin extends JavaPlugin {

    private TypeTargetManager typeTargetManager;
    private InputManager inputManager;
    private StandManager standManager;
    private VehicleManager vehicleManager;

    private Messages messages;
    private @Getter(AccessLevel.NONE) GlowingEntities glowingEntities;

    private final ExecutorService pool = new ThreadPoolExecutor(
            0,
            Integer.MAX_VALUE,
            120L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadFactoryBuilder().setNameFormat("vehicleswasd-worker-thread-%d").build());

    private final Map<VehicleType, ItemStack> typeCategoryItem = new EnumMap<>(VehicleType.class);
    private final Map<VehicleType, List<ShopVehicle>> typeVehicles = new EnumMap<>(VehicleType.class);
    private final Map<VehicleType, Shape> recipes = new EnumMap<>(VehicleType.class);
    private final Set<TypeTarget> fuelItems = new HashSet<>();
    private final Set<TypeTarget> breakBlocks = new HashSet<>();
    private final Multimap<String, Material> extraTags = MultimapBuilder.hashKeys().hashSetValues().build();
    private final Map<String, AVExtension<?>> extensions = new HashMap<>();

    private final NamespacedKey vehicleTypeKey = new NamespacedKey(this, "vehicle_type");
    private final NamespacedKey vehicleModelIdKey = new NamespacedKey(this, "vehicle_model_id");
    private final NamespacedKey customizationKey = new NamespacedKey(this, "customization");
    private final NamespacedKey saveDataKey = new NamespacedKey(this, "save_data");
    private final NamespacedKey moneyKey = new NamespacedKey(this, "money");
    private final NamespacedKey itemIdKey = new NamespacedKey(this, "ItemID");
    private final NamespacedKey chairNumbeKey = new NamespacedKey(this, "ChairNumber");
    private final NamespacedKey fuelItemKey = new NamespacedKey(this, "FuelItem");

    EssentialsExtension essentialsExtension;
    EconomyExtension<?> economyExtension;
    WGExtension wgExtension;
    boolean patheticEnabled;

    private static final List<String> GUI_TYPES = List.of("vehicle", "shop", "shop-confirm", "customizations");
    private static final Set<String> SPECIAL_SECTIONS = Sets.newHashSet("extra-tags", "shop");
    private static final Set<String> ECONOMY_PROVIDER = Set.of("Vault", "PlayerPoints");
    private static final int BSTATS_ID = 27888;

    static {
        ConfigurationSerialization.registerClass(VehicleData.class);
    }

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();

        // WG needs to be registered onLoad to be able to register the flags.
        wgExtension = registerExtension(WGExtension.class, "WorldGuard");

        messages = new Messages(this);
        saveDefaultConfig();
        updateConfigs();
    }

    @Override
    public void onEnable() {
        Logger logger = getLogger();

        // Disable the plugin if the server version is older than 1.17.
        PluginManager pluginManager = getServer().getPluginManager();
        if (XReflection.MINOR_NUMBER < 17) {
            logger.severe("This plugin only works from 1.17 and up, disabling...");
            pluginManager.disablePlugin(this);
            return;
        }

        // Disable the plugin if PacketEvents isn't installed.
        if (pluginManager.getPlugin("packetevents") == null) {
            logger.severe("This plugin depends on PacketEvents, disabling...");
            pluginManager.disablePlugin(this);
            return;
        }

        // Enable bStats.
        new Metrics(this, BSTATS_ID);

        // Both listeners.
        UseEntity useEntity = new UseEntity(this);

        // Register protocol events.
        EventManager eventManager = PacketEvents.getAPI().getEventManager();
        eventManager.registerListener(useEntity);

        // Register bukkit events.
        pluginManager.registerEvents(useEntity, this);
        pluginManager.registerEvents(new EntityListener(this), this);
        pluginManager.registerEvents(new InventoryListener(this), this);

        typeTargetManager = new TypeTargetManager(this);
        inputManager = new InputManager(this);
        standManager = new StandManager(this);
        vehicleManager = new VehicleManager(this);

        resetEconomyProvider();
        essentialsExtension = registerExtension(EssentialsExtension.class, "Essentials");

        reloadExtraTags();
        reloadFuelItems();
        reloadBreakBlocks();
        reloadRecipes();

        VehiclesCommands vehiclesCommands = new VehiclesCommands(this);

        PluginCommand command = getCommand("vehicleswasd");
        if (command == null) return;

        command.setExecutor(vehiclesCommands);
        command.setTabCompleter(vehiclesCommands);

        // Enable extensions.
        for (AVExtension<?> extension : extensions.values()) {
            extension.onEnable(this);
        }
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();

        if (vehicleManager == null) return;

        for (Vehicle vehicle : vehicleManager.getVehicles()) {
            vehicle.saveToChunkInternal();
        }

        pool.shutdownNow();
        try {
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                getLogger().warning("Thread pool did not shut down in time, unsaved vehicles may occur!");
            }
        } catch (InterruptedException exception) {
            throw new RuntimeException(exception);
        }
    }

    public GlowingEntities getGlowingEntities() {
        return glowingEntities != null ? glowingEntities : (glowingEntities = new GlowingEntities(this));
    }

    public ActionKeybind getOpenMenuKeybind() {
        return getKeyOrDefault(Config.KEYBINDS_OPEN_MENU, ActionKeybind.LEFT_CLICK);
    }

    public ActionKeybind getShootWeaponKeybind() {
        return getKeyOrDefault(Config.KEYBINDS_SHOOT_WEAPON, ActionKeybind.RIGHT_CLICK);
    }

    private ActionKeybind getKeyOrDefault(@NotNull ConfigValue keybindConfig, ActionKeybind defaultKeybind) {
        return PluginUtils.getOrDefault(ActionKeybind.class,
                keybindConfig.asString(defaultKeybind.name()),
                defaultKeybind);
    }

    public void resetEconomyProvider() {
        // Invalidate before initializing.
        economyExtension = null;

        String provider = Config.ECONOMY_PROVIDER.asString();
        if (provider == null || !ECONOMY_PROVIDER.contains(provider)) {
            getLogger().info("No economy provider found, disabling economy support...");
            return;
        }

        if (provider.equals("Vault")) {
            economyExtension = registerExtension(VaultExtension.class, "Vault");
        } else {
            economyExtension = registerExtension(PlayerPointsExtension.class, "PlayerPoints");
        }
    }

    public void updateConfigs() {
        // Save models first.
        saveFiles("models");

        String pluginFolder = getDataFolder().getPath();

        updateConfig(
                pluginFolder,
                "config.yml",
                file -> reloadConfig(),
                file -> saveDefaultConfig(),
                config -> {
                    fillIgnoredSections(config);
                    return SPECIAL_SECTIONS.stream().filter(config::contains).toList();
                },
                Collections.emptyList());

        updateConfig(
                pluginFolder,
                "messages.yml",
                file -> messages.setConfiguration(YamlConfiguration.loadConfiguration(file)),
                file -> saveResource("messages.yml"),
                config -> Collections.emptyList(),
                Collections.emptyList());
    }

    private void fillIgnoredSections(FileConfiguration config) {
        for (String guiType : GUI_TYPES) {
            ConfigurationSection section = config.getConfigurationSection("gui." + guiType + ".items");
            if (section == null) continue;

            for (String key : section.getKeys(false)) {
                SPECIAL_SECTIONS.add("gui." + guiType + ".items." + key);
            }
        }

        for (VehicleType type : VehicleType.values()) {
            String path = type.toPath();
            SPECIAL_SECTIONS.add("vehicles." + path + ".item");
        }
    }

    public void updateConfig(String folderName,
                             String fileName,
                             Consumer<File> reloadAfterUpdating,
                             Consumer<File> resetConfiguration,
                             Function<FileConfiguration, List<String>> ignoreSection,
                             List<ConfigChanges> changes) {
        File file = new File(folderName, fileName);

        FileConfiguration config = PluginUtils.reloadConfig(this, file, resetConfiguration);
        if (config == null) {
            getLogger().severe("Can't find {" + file.getName() + "}!");
            return;
        }

        for (ConfigChanges change : changes) {
            handleConfigChanges(file, config, change.predicate(), change.consumer(), change.newVersion());
        }

        try {
            ConfigUpdater.update(
                    this,
                    fileName,
                    file,
                    ignoreSection.apply(config));
        } catch (IOException exception) {
            exception.printStackTrace();
        }

        reloadAfterUpdating.accept(file);
    }

    private void handleConfigChanges(@NotNull File file, FileConfiguration config, @NotNull Predicate<FileConfiguration> predicate, Consumer<FileConfiguration> consumer, int newVersion) {
        if (!predicate.test(config)) return;

        int previousVersion = config.getInt("config-version", 0);
        getLogger().info("Updated {%s} config to v{%s} (from v{%s})".formatted(file.getName(), newVersion, previousVersion));

        consumer.accept(config);
        config.set("config-version", newVersion);

        try {
            config.save(file);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    @SuppressWarnings("unused")
    public record ConfigChanges(Predicate<FileConfiguration> predicate,
                                Consumer<FileConfiguration> consumer,
                                int newVersion) {

        public static @NotNull Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private final List<ConfigChanges> changes = new ArrayList<>();

            public Builder addChange(Predicate<FileConfiguration> predicate,
                                     Consumer<FileConfiguration> consumer,
                                     int newVersion) {
                changes.add(new ConfigChanges(predicate, consumer, newVersion));
                return this;
            }

            public List<ConfigChanges> build() {
                return ImmutableList.copyOf(changes);
            }
        }
    }

    public ItemStack createVehicleItem(@NotNull VehicleType type, @Nullable VehicleData data) {
        String modelName = type.toPath();
        ItemBuilder builder = getItem("vehicles." + modelName + ".item")
                .setData(vehicleTypeKey, PersistentDataType.STRING, modelName);

        if (data != null) {
            String shopDisplayName = data.shopDisplayName();

            if (shopDisplayName != null) {
                String lastColors = ChatColor.getLastColors(shopDisplayName);
                String replace = shopDisplayName.replace(lastColors, lastColors + ChatColor.ITALIC);
                builder.addLore(
                        false,
                        getConfig().getString("translations.from-shop", replace).replace("%name%", replace),
                        "&7");
            }

            String lock = getConfig().getString("translations.lock." + (data.locked() ? "locked" : "unlocked"));

            UUID ownerUUID = data.owner();

            String owner;
            if (ownerUUID != null) {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(ownerUUID);
                owner = Objects.requireNonNullElse(offline.getName(), "???");
            } else owner = "???";

            Float fuel = Objects.requireNonNullElse(data.fuel(), 0.0f);

            //noinspection DataFlowIssue
            builder
                    .replace("%owner%", owner)
                    .replace("%type%", getVehicleTypeFormatted(type))
                    .replace("%fuel%", PluginUtils.fixedDouble(fuel))
                    .replace("%max-fuel%", PluginUtils.fixedDouble(getMaxFuel(type)))
                    .replace("%lock%", lock)
                    .setData(saveDataKey, Vehicle.VEHICLE_DATA, data);
        }

        return builder.setAmount(1).build();
    }

    public <T> @Nullable T registerExtension(@NotNull Class<T> extensionClazz, String pluginName) {
        if (getServer().getPluginManager().getPlugin(pluginName) == null) return null;

        try {
            @SuppressWarnings("unchecked") AVExtension<T> extension = (AVExtension<T>) extensionClazz.getConstructor().newInstance();
            extensions.put(pluginName, extension);

            return extension.init(this);
        } catch (NoClassDefFoundError | ReflectiveOperationException ignored) {
            return null;
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void saveFiles(String path) {
        CodeSource source = getClass().getProtectionDomain().getCodeSource();
        if (source == null) return;

        try {
            ZipInputStream zip = new ZipInputStream(source.getLocation().openStream());

            String folderPath = path + "/";

            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (!name.startsWith(folderPath)
                        || name.equals(folderPath)
                        || name.endsWith("/")) continue;

                // Ignore existing files.
                File file = new File(getDataFolder(), name.replace('\\', '/'));
                if (file.exists()) continue;

                saveResource(name, false);
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    public void saveResource(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) saveResource(name, false);
    }

    public void reloadShopItemsIfNeeded() {
        if (!typeCategoryItem.isEmpty() && !typeVehicles.isEmpty()) return;
        reloadShopItems();
    }

    public void reloadShopItems() {
        typeCategoryItem.clear();
        typeVehicles.clear();

        for (VehicleType type : VehicleType.values()) {
            typeCategoryItem.put(type, new ItemBuilder(createVehicleItem(type, null))
                    .clearLore()
                    .build());

            String pathName = type.toPath();

            ConfigurationSection section = getConfig().getConfigurationSection("shop.vehicles." + pathName);
            if (section == null) continue;

            @SuppressWarnings("unchecked") ItemStack vehicleItem = new ItemBuilder(createVehicleItem(type, null))
                    .removeData(vehicleTypeKey)
                    .setLore(Config.SHOP_ITEM_LORE.getValue(List.class))
                    .build();

            List<ShopVehicle> itemList = new ArrayList<>();

            for (String key : section.getKeys(false)) {
                String path = "shop.vehicles." + pathName + "." + key + ".";

                String displayName = getConfig().getString(path + "display-name");
                List<String> customizations = getConfig().getStringList(path + "changes");
                double price = getConfig().getDouble(path + "price");

                List<String> finalCustomizations;
                Map<String, Material> customizationChanges = new HashMap<>();
                if (!customizations.isEmpty()) {
                    finalCustomizations = new ArrayList<>();
                    for (String customization : customizations) {
                        String[] data = customization.split(":");
                        if (data.length != 2) continue;

                        String customizationName = data[0];

                        Material material = PluginUtils.getOrNull(Material.class, data[1]);
                        if (material == null) continue;

                        String nameFromConfig = getConfig().getString("customizations." + pathName + "." + customizationName.toLowerCase(Locale.ROOT) + ".name");
                        finalCustomizations.add(nameFromConfig);

                        customizationChanges.put(customizationName, material);
                    }
                } else finalCustomizations = Collections.emptyList();

                VehicleData data = new VehicleData(null, null, true, null, null, type, null, null, customizationChanges, null);

                ItemBuilder builder = new ItemBuilder(vehicleItem);
                if (displayName != null) builder.setDisplayName(displayName);

                ItemStack item = builder
                        .applyMultiLineLore(finalCustomizations, "%customization-on%", getConfig().getString("translations.no-customization"))
                        .setData(saveDataKey, Vehicle.VEHICLE_DATA, data)
                        .setData(moneyKey, PersistentDataType.DOUBLE, price)
                        .replace("%money%", economyExtension != null && economyExtension.isEnabled() ? economyExtension.format(price) : price)
                        .build();

                itemList.add(new ShopVehicle(key, item, data));
            }

            if (!itemList.isEmpty()) {
                typeVehicles.put(type, itemList);
            }
        }
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();

        // We don't want to use default values.
        getConfig().setDefaults(new MemoryConfiguration());
    }

    public void reloadFuelItems() {
        fuelItems.clear();
        fuelItems.addAll(typeTargetManager.getTargetsFromConfig("fuel-items"));
    }

    public void reloadBreakBlocks() {
        breakBlocks.clear();
        breakBlocks.addAll(typeTargetManager.getTargetsFromConfig("break-blocks.blocks"));
    }

    public void reloadExtraTags() {
        extraTags.clear();

        ConfigurationSection section = getConfig().getConfigurationSection("extra-tags");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            for (TypeTarget typeTarget : typeTargetManager.getTargetsFromConfig("extra-tags." + key)) {
                extraTags.put(key.toLowerCase(Locale.ROOT).replace("-", "_"), typeTarget.getType());
            }
        }
    }

    public void reloadRecipes() {
        for (Shape shape : recipes.values()) {
            Bukkit.removeRecipe(shape.getKey());
        }
        recipes.clear();

        for (VehicleType type : VehicleType.values()) {
            String name = type.toPath();
            String path = "vehicles." + name + ".item.crafting.";

            FileConfiguration config = getConfig();
            boolean shaped = config.getBoolean(path + "shaped");
            List<String> ingredients = config.getStringList(path + "ingredients");
            List<String> shape = config.getStringList(path + "shape");

            recipes.put(type, new Shape(
                    this,
                    name,
                    shaped,
                    ingredients,
                    shape,
                    createVehicleItem(type, null)));
        }
    }

    public ItemBuilder getItem(@NotNull String path) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        FileConfiguration config = getConfig();

        String name = config.getString(path + ".display-name");
        List<String> lore = config.getStringList(path + ".lore");

        String url = config.getString(path + ".url");

        String materialPath = path + ".material";

        String materialName = config.getString(materialPath, "STONE");
        Material material = PluginUtils.getOrNull(Material.class, materialName);

        ItemBuilder builder = new ItemBuilder(material)
                .setData(itemIdKey, PersistentDataType.STRING, path.contains(".") ? path.substring(path.lastIndexOf(".") + 1) : path)
                .setLore(lore);

        if (name != null) builder.setDisplayName(name);

        String amountString = config.getString(path + ".amount");
        if (amountString != null) {
            int amount = PluginUtils.getRangedAmount(amountString);
            builder.setAmount(amount);
        }

        if (material == Material.PLAYER_HEAD && url != null) {
            // Use UUID from path to allow stacking heads.
            UUID itemUUID = UUID.nameUUIDFromBytes(path.getBytes());
            builder.setHead(itemUUID, url, true);
        }

        int modelData = config.getInt(path + ".model-data", Integer.MIN_VALUE);
        if (modelData != Integer.MIN_VALUE) builder.setCustomModelData(modelData);

        for (String enchantmentString : config.getStringList(path + ".enchantments")) {
            if (Strings.isNullOrEmpty(enchantmentString)) continue;
            String[] data = PluginUtils.splitData(enchantmentString);

            Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(data[0].toLowerCase(Locale.ROOT)));

            int level;
            try {
                level = PluginUtils.getRangedAmount(data[1]);
            } catch (IndexOutOfBoundsException | IllegalArgumentException exception) {
                level = 1;
            }

            if (enchantment != null) builder.addEnchantment(enchantment, level);
        }

        for (String flag : config.getStringList(path + ".flags")) {
            builder.addItemFlags(ItemFlag.valueOf(flag.toUpperCase(Locale.ROOT)));
        }

        String tippedArrow = config.getString(path + ".tipped");
        if (tippedArrow != null) {
            PotionType potionType = PluginUtils.getValidPotionType(tippedArrow);
            if (potionType != null) builder.setBasePotionData(potionType);
        }

        Object leather = config.get(path + ".leather-color");
        if (leather instanceof String leatherColor) {
            Color color = PluginUtils.getColor(leatherColor);
            if (color != null) builder.setLeatherArmorMetaColor(color);
        } else if (leather instanceof List<?> list) {
            List<Color> colors = new ArrayList<>();

            for (Object object : list) {
                if (!(object instanceof String string)) continue;
                if (string.equalsIgnoreCase("$RANDOM")) continue;

                Color color = PluginUtils.getColor(string);
                if (color != null) colors.add(color);
            }

            if (!colors.isEmpty()) {
                Color color = colors.get(random.nextInt(0, colors.size()));
                builder.setLeatherArmorMetaColor(color);
            }
        }

        if (config.contains(path + ".firework")) {
            ConfigurationSection section = config.getConfigurationSection(path + ".firework.firework-effects");
            if (section == null) return builder;

            Set<FireworkEffect> effects = new HashSet<>();
            for (String effect : section.getKeys(false)) {
                FireworkEffect.Builder effectBuilder = FireworkEffect.builder();

                String type = config.getString(path + ".firework.firework-effects." + effect + ".type");
                if (type == null) continue;

                FireworkEffect.Type effectType = PluginUtils.getOrEitherRandomOrNull(FireworkEffect.Type.class, type);

                boolean flicker = config.getBoolean(path + ".firework.firework-effects." + effect + ".flicker");
                boolean trail = config.getBoolean(path + ".firework.firework-effects." + effect + ".trail");

                effects.add((effectType != null ?
                        effectBuilder.with(effectType) :
                        effectBuilder)
                        .flicker(flicker)
                        .trail(trail)
                        .withColor(getColors(config, path, effect, "colors"))
                        .withFade(getColors(config, path, effect, "fade-colors"))
                        .build());
            }

            String powerString = config.getString(path + ".firework.power");
            int power = PluginUtils.getRangedAmount(powerString != null ? powerString : "");

            if (!effects.isEmpty()) builder.initializeFirework(power, effects.toArray(new FireworkEffect[0]));
        }

        String damageString = config.getString(path + ".damage");
        if (damageString != null) {
            int maxDurability = builder.build().getType().getMaxDurability();

            int damage;
            if (damageString.equalsIgnoreCase("$RANDOM")) {
                damage = random.nextInt(1, maxDurability);
            } else if (damageString.contains("%")) {
                damage = Math.round(maxDurability * ((float) PluginUtils.getRangedAmount(damageString.replace("%", "")) / 100));
            } else {
                damage = PluginUtils.getRangedAmount(damageString);
            }

            if (damage > 0) builder.setDamage(Math.min(damage, maxDurability));
        }

        return builder;
    }

    private @NotNull Set<Color> getColors(@NotNull FileConfiguration config, String path, String effect, String needed) {
        Set<Color> colors = new HashSet<>();
        for (String colorString : config.getStringList(path + ".firework.firework-effects." + effect + "." + needed)) {
            Color color = PluginUtils.getColor(colorString);
            if (color != null) colors.add(color);
        }
        return colors;
    }

    public @NotNull String getModelFolder() {
        return getDataFolder() + File.separator + "models";
    }

    public @NotNull List<String> typesToString(@NotNull Set<TypeTarget> typeTargets, VehicleType type, boolean isFuel) {
        Set<String> list = new LinkedHashSet<>();

        for (TypeTarget fuelItem : typeTargets) {
            if (fuelItem instanceof TypeTarget.TargetWithCondition condition && !condition.test(type)) continue;

            String fromTag = fuelItem.getFromTag();
            String typeName = getMaterialOrTagName(fromTag != null ?
                    fromTag :
                    fuelItem.getType().name(), fromTag != null);

            list.add(typeName + (isFuel ? " +" + fuelItem.getAmount() : ""));
        }

        return list.stream().sorted().toList();
    }

    @SuppressWarnings("deprecation")
    public @NotNull String getMaterialOrTagName(@NotNull String string, boolean isTag) {
        String formatted = string.toLowerCase(Locale.ROOT).replace("_", "-");
        String fromTranslations = getConfig().getString("translations." + (isTag ? "tag" : "material") + "." + formatted);
        return fromTranslations != null ? fromTranslations : WordUtils.capitalizeFully(formatted.replace("-", " "));
    }

    public String getVehicleTypeFormatted(@NotNull VehicleType type) {
        return getConfig().getString("translations.vehicle." + type.toPath(), type.name());
    }

    public double getMaxFuel(@NotNull VehicleType type) {
        return getConfig().getDouble("vehicles." + type.toPath() + ".fuel.max-fuel", 0.0d);
    }
}