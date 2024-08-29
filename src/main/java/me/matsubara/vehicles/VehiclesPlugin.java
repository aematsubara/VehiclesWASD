package me.matsubara.vehicles;

import com.cryptomorin.xseries.reflection.XReflection;
import com.github.retrooper.packetevents.PacketEvents;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import com.tchristofferson.configupdater.ConfigUpdater;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import lombok.Getter;
import me.matsubara.vehicles.command.VehiclesCommands;
import me.matsubara.vehicles.files.Config;
import me.matsubara.vehicles.files.Messages;
import me.matsubara.vehicles.hook.AVExtension;
import me.matsubara.vehicles.hook.EssentialsExtension;
import me.matsubara.vehicles.hook.VaultExtension;
import me.matsubara.vehicles.hook.WGExtension;
import me.matsubara.vehicles.listener.InventoryListener;
import me.matsubara.vehicles.listener.PlayerListener;
import me.matsubara.vehicles.listener.protocol.UseEntity;
import me.matsubara.vehicles.manager.InputManager;
import me.matsubara.vehicles.manager.StandManager;
import me.matsubara.vehicles.manager.VehicleManager;
import me.matsubara.vehicles.manager.targets.TypeTarget;
import me.matsubara.vehicles.manager.targets.TypeTargetManager;
import me.matsubara.vehicles.util.ItemBuilder;
import me.matsubara.vehicles.util.PluginUtils;
import me.matsubara.vehicles.util.Shape;
import me.matsubara.vehicles.vehicle.Vehicle;
import me.matsubara.vehicles.vehicle.VehicleData;
import me.matsubara.vehicles.vehicle.VehicleType;
import org.bukkit.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
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
import org.patheloper.mapping.PatheticMapper;

import java.io.File;
import java.io.IOException;
import java.security.CodeSource;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Getter
public final class VehiclesPlugin extends JavaPlugin {

    private TypeTargetManager typeTargetManager;
    private InputManager inputManager;
    private StandManager standManager;
    private VehicleManager vehicleManager;

    private Messages messages;

    private final Map<VehicleType, ItemStack> typeCategoryItem = new EnumMap<>(VehicleType.class);
    private final Map<VehicleType, List<ItemStack>> typeVehicles = new EnumMap<>(VehicleType.class);
    private final Map<VehicleType, Shape> vehicleCrafting = new EnumMap<>(VehicleType.class);
    private final Set<TypeTarget> fuelItems = new HashSet<>();
    private final Multimap<String, Material> extraTags = MultimapBuilder.hashKeys().hashSetValues().build();
    private final List<AVExtension<?>> extensions = new ArrayList<>();

    private final NamespacedKey vehicleTypeKey = new NamespacedKey(this, "vehicle_type");
    private final NamespacedKey vehicleModelIdKey = new NamespacedKey(this, "vehicle_model_id");
    private final NamespacedKey customizationKey = new NamespacedKey(this, "customization");
    private final NamespacedKey saveDataKey = new NamespacedKey(this, "save_data");
    private final NamespacedKey moneyKey = new NamespacedKey(this, "money");
    private final NamespacedKey itemIdKey = new NamespacedKey(this, "ItemID");
    private final NamespacedKey chairNumbeKey = new NamespacedKey(this, "ChairNumber");

    EssentialsExtension essentialsExtension;
    VaultExtension vaultExtension;
    boolean patheticEnabled;

    private static final List<String> GUI_TYPES = List.of("vehicle", "shop", "shop-confirm", "customizations");
    private static final Set<String> SPECIAL_SECTIONS = Sets.newHashSet("extra-tags");

    static {
        ConfigurationSerialization.registerClass(VehicleData.class);
    }

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings()
                .reEncodeByDefault(true)
                .checkForUpdates(false);
        PacketEvents.getAPI().load();

        // WG needs to be registered onLoad to be able to register the flags.
        registerExtension(WGExtension.class, "WorldGuard");
    }

    @Override
    public void onEnable() {
        // Disable the plugin if the server version is older than 1.17.
        PluginManager pluginManager = getServer().getPluginManager();
        if (XReflection.MINOR_NUMBER < 17) {
            getLogger().severe("This plugin only works from 1.17 and up, disabling...");
            pluginManager.disablePlugin(this);
            return;
        }

        // Disable the plugin if ProtocolLib isn't installed.
        if (pluginManager.getPlugin("packetevents") == null) {
            getLogger().severe("This plugin depends on PacketEvents, disabling...");
            pluginManager.disablePlugin(this);
            return;
        }

        // Enable extensions.
        for (AVExtension<?> extension : extensions) {
            extension.onEnable(this);
        }

        // Before using Pathetic, you need to initialize it.
        PatheticMapper.initialize(this);

        vaultExtension = registerExtension(VaultExtension.class, "Vault");
        essentialsExtension = registerExtension(EssentialsExtension.class, "Essentials");

        // Register protocol events.
        PacketEvents.getAPI().getEventManager().registerListener(new UseEntity(this));

        // Register bukkit events.
        pluginManager.registerEvents(new InventoryListener(this), this);
        pluginManager.registerEvents(new PlayerListener(this), this);

        typeTargetManager = new TypeTargetManager(this);
        inputManager = new InputManager(this);
        standManager = new StandManager(this);
        vehicleManager = new VehicleManager(this);

        messages = new Messages(this);

        saveDefaultConfig();
        saveFiles("models");
        updateConfigs();

        reloadShopItems();
        reloadFuelItems();
        reloadExtraTags();
        reloadCraftings();

        VehiclesCommands vehiclesCommands = new VehiclesCommands(this);

        PluginCommand command = getCommand("vehicleswasd");
        if (command == null) return;

        command.setExecutor(vehiclesCommands);
        command.setTabCompleter(vehiclesCommands);
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();

        if (vehicleManager == null) return;

        for (Vehicle vehicle : vehicleManager.getVehicles()) {
            vehicle.saveToChunk();
        }
    }

    public void updateConfigs() {
        String pluginFolder = getDataFolder().getPath();

        updateConfig(
                pluginFolder,
                "config.yml",
                file -> reloadConfig(),
                file -> saveDefaultConfig(),
                config -> {
                    fillIgnoredSections(config);
                    return SPECIAL_SECTIONS.stream().filter(config::contains).toList();
                }, Collections.emptyList());

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
            String path = type.toConfigPath();
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

    public ItemStack createVehicleItem(@NotNull String modelName, @Nullable VehicleData data) {
        ItemBuilder builder = getItem("vehicles." + modelName.replace("_", "-") + ".item")
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

            builder.setData(saveDataKey, Vehicle.VEHICLE_DATA, data);
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    public <T> @Nullable T registerExtension(@NotNull Class<T> extensionClazz, String pluginName) {
        if (getServer().getPluginManager().getPlugin(pluginName) == null) {
            getLogger().info(pluginName + " not found.");
            return null;
        }

        try {
            AVExtension<T> extension = (AVExtension<T>) extensionClazz.getConstructor().newInstance();
            extensions.add(extension);

            return extension.init(this);
        } catch (NoClassDefFoundError | ReflectiveOperationException ignored) {
            return null;
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void saveFiles(String path) {
        if (new File(getDataFolder(), path).isDirectory()) return;

        CodeSource source = getClass().getProtectionDomain().getCodeSource();
        if (source == null) return;

        try {
            ZipInputStream zip = new ZipInputStream(source.getLocation().openStream());

            String folderPath = path + "/";

            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith(folderPath) && !name.equals(folderPath) && !name.endsWith("/")) {
                    saveResource(name, false);
                }
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    public void saveResource(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) saveResource(name, false);
    }

    public void reloadShopItems() {
        typeCategoryItem.clear();
        typeVehicles.clear();

        for (VehicleType type : VehicleType.values()) {
            typeCategoryItem.put(type, new ItemBuilder(createVehicleItem(type.toFilePath(), null))
                    .clearLore()
                    .build());

            ConfigurationSection section = getConfig().getConfigurationSection("shop.vehicles." + type.toConfigPath());
            if (section == null) continue;

            ItemStack vehicleItem = new ItemBuilder(createVehicleItem(type.toFilePath(), null))
                    .removeData(vehicleTypeKey)
                    .setLore(Config.SHOP_ITEM_LORE.asStringList())
                    .build();

            List<ItemStack> itemList = new ArrayList<>();

            for (String key : section.getKeys(false)) {
                String displayName = getConfig().getString("shop.vehicles." + type.toConfigPath() + "." + key + ".display-name");
                List<String> customizations = getConfig().getStringList("shop.vehicles." + type.toConfigPath() + "." + key + ".changes");
                double price = getConfig().getDouble("shop.vehicles." + type.toConfigPath() + "." + key + ".price");

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

                        String nameFromConfig = getConfig().getString("customizations." + type.toConfigPath() + "." + customizationName.toLowerCase() + ".name");
                        finalCustomizations.add(nameFromConfig);

                        customizationChanges.put(customizationName, material);
                    }
                } else finalCustomizations = Collections.emptyList();

                VehicleData data = new VehicleData(null, null, true, null, null, type, null, null, customizationChanges);

                ItemBuilder builder = new ItemBuilder(vehicleItem);
                if (displayName != null) builder.setDisplayName(displayName);

                itemList.add(builder
                        .applyMultiLineLore(finalCustomizations, "%customization-on%", getConfig().getString("translations.no-customization"))
                        .setData(saveDataKey, Vehicle.VEHICLE_DATA, data)
                        .setData(moneyKey, PersistentDataType.DOUBLE, price)
                        .replace("%money%", price)
                        .build());
            }

            if (!itemList.isEmpty()) {
                typeVehicles.put(type, itemList);
            }
        }
    }

    public void reloadFuelItems() {
        fuelItems.clear();
        fuelItems.addAll(typeTargetManager.getTargetsFromConfig("fuel-items"));
    }

    public void reloadExtraTags() {
        extraTags.clear();

        ConfigurationSection section = getConfig().getConfigurationSection("extra-tags");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            for (TypeTarget typeTarget : typeTargetManager.getTargetsFromConfig("extra-tags." + key)) {
                extraTags.put(key.toLowerCase().replace("-", "_"), typeTarget.getType());
            }
        }
    }

    public void reloadCraftings() {
        for (Shape shape : vehicleCrafting.values()) {
            Bukkit.removeRecipe(shape.getKey());
        }
        vehicleCrafting.clear();

        for (VehicleType type : VehicleType.values()) {
            String path = "vehicles." + type.toConfigPath() + ".item";
            vehicleCrafting.put(type, new Shape(
                    this,
                    type.toFilePath(),
                    getConfig().getBoolean(path + ".crafting.shaped"),
                    getConfig().getStringList(path + ".crafting.ingredients"),
                    getConfig().getStringList(path + ".crafting.shape"),
                    createVehicleItem(type.toFilePath(), null)));
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

            Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(data[0].toLowerCase()));

            int level;
            try {
                level = PluginUtils.getRangedAmount(data[1]);
            } catch (IndexOutOfBoundsException | IllegalArgumentException exception) {
                level = 1;
            }

            if (enchantment != null) builder.addEnchantment(enchantment, level);
        }

        for (String flag : config.getStringList(path + ".flags")) {
            builder.addItemFlags(ItemFlag.valueOf(flag.toUpperCase()));
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

    public List<String> getModelList() {
        File modelsFolder = new File(getModelFolder());
        if (!modelsFolder.exists()) return Collections.emptyList();

        String[] files = modelsFolder.list((directory, name) -> name.endsWith(".yml"));
        return files != null ? Arrays.stream(files).map(name -> name.replace(".yml", "")).toList() : Collections.emptyList();
    }

    public boolean validModel(String name) {
        return new File(getModelFolder(), name + ".yml").exists();
    }

    public @NotNull String getModelFolder() {
        return getDataFolder() + File.separator + "models";
    }

    public @NotNull List<String> typesToString(@NotNull Set<TypeTarget> typeTargets, VehicleType type, boolean isFuel) {
        Set<String> list = new LinkedHashSet<>();

        for (TypeTarget fuelItem : typeTargets) {
            if (fuelItem instanceof TypeTarget.TargetWithCondition condition && !condition.test(type)) continue;

            String typeName;
            if (fuelItem.getFromTag() != null) {
                typeName = getMaterialOrTagName(fuelItem.getFromTag().toLowerCase().replace("_", "-"), true);
            } else {
                typeName = getMaterialOrTagName(fuelItem.getType().name().toLowerCase().replace("_", "-"), false);
            }

            list.add(typeName + (isFuel ? " +" + fuelItem.getAmount() : ""));
        }

        return list.stream().sorted().toList();
    }

    private String getMaterialOrTagName(String string, boolean isTag) {
        String fromTranslations = getConfig().getString("translations." + (isTag ? "tag" : "material") + "." + string);
        return fromTranslations != null ? fromTranslations : string;
    }

    public String getVehicleTypeFormatted(@NotNull VehicleType type) {
        return getConfig().getString("translations.vehicle." + type.toConfigPath(), type.name());
    }
}