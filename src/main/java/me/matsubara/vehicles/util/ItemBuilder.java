package me.matsubara.vehicles.util;

import com.cryptomorin.xseries.reflection.XReflection;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"unused", "UnusedReturnValue"})
public final class ItemBuilder {

    private final ItemStack item;

    private static final MethodHandle SET_BASE_POTION_TYPE;

    static {
        SET_BASE_POTION_TYPE = XReflection.supports(20, 6) ?
                Reflection.getMethod(PotionMeta.class, "setBasePotionType", PotionType.class) :
                null;
    }

    public ItemBuilder(@NotNull ItemStack item) {
        this.item = item.clone();
    }

    public ItemBuilder(Material material) {
        this(new ItemStack(material));
    }

    public ItemBuilder setType(Material type) {
        item.setType(type);
        return this;
    }

    public ItemBuilder setHead(String texture, boolean isUrl) {
        return setHead(UUID.randomUUID(), texture, isUrl);
    }

    public ItemBuilder setHead(UUID uuid, String texture, boolean isUrl) {
        if (item.getType() != Material.PLAYER_HEAD) {
            setType(Material.PLAYER_HEAD);
        }

        SkullMeta meta = (SkullMeta) item.getItemMeta();
        PluginUtils.applySkin(meta, uuid, texture, isUrl);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setAmount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemBuilder setCustomModelData(int data) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        meta.setCustomModelData(data);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setDamage(int damage) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return this;
        damageable.setDamage(damage);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setOwningPlayer(UUID uuid) {
        return setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
    }

    public ItemBuilder setOwningPlayer(OfflinePlayer player) {
        if (!(item.getItemMeta() instanceof SkullMeta meta)) return this;

        meta.setOwningPlayer(player);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setDisplayName(String displayName) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        meta.displayName(ComponentUtil.deserialize(displayName));
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder clearLore() {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        meta.setLore(null);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setLore(Component... lore) {
        return setLore(Arrays.asList(lore));
    }

    public ItemBuilder setLore(List<Component> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        meta.lore(lore);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder addLore(boolean end, Component... lore) {
        return addLore(end, Arrays.asList(lore));
    }

    public ItemBuilder addLore(Component... lore) {
        return addLore(true, Arrays.asList(lore));
    }

    public ItemBuilder addLore(boolean end, List<Component> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        List<Component> actual = meta.lore();
        if (actual == null) return setLore(lore);

        if (end) actual.addAll(lore);
        else actual.addAll(0, lore);

        return setLore(actual);
    }

    public ItemBuilder addLore(List<Component> lore) {
        return addLore(true, lore);
    }

    public List<Component> getLore() {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore;
        return meta != null && (lore = meta.lore()) != null ? lore : Collections.emptyList();
    }

    public ItemBuilder setLeatherArmorMetaColor(Color color) {
        if (!(item.getItemMeta() instanceof LeatherArmorMeta meta)) return this;

        meta.setColor(color);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder addEnchantment(Enchantment enchantment, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        meta.addEnchant(enchantment, level, true);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder removeEnchantment(Enchantment enchantment) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        meta.removeEnchant(enchantment);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder addItemFlags(ItemFlag... flags) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        meta.addItemFlags(flags);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder removeItemFlags(ItemFlag... flags) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        meta.removeItemFlags(flags);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setBasePotionData(PotionType type) {
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return this;

        if (SET_BASE_POTION_TYPE != null) {
            try {
                SET_BASE_POTION_TYPE.invoke(meta, type);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        } else {
            meta.setBasePotionData(new org.bukkit.potion.PotionData(type));
        }

        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder addPattern(int colorId, String patternCode) {
        return addPattern(DyeColor.values()[colorId], PatternType.getByIdentifier(patternCode));
    }

    public ItemBuilder addPattern(DyeColor color, PatternType patternType) {
        return addPattern(new Pattern(color, patternType));
    }

    public ItemBuilder addPattern(Pattern pattern) {
        if (!(item.getItemMeta() instanceof BannerMeta meta)) return this;

        meta.addPattern(pattern);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setBannerColor(DyeColor color) {
        if (!(item.getItemMeta() instanceof BannerMeta meta)) return this;

        meta.addPattern(new Pattern(color, PatternType.BASE));
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder initializeFirework(int power, FireworkEffect... effects) {
        if (!(item.getItemMeta() instanceof FireworkMeta meta)) return this;

        meta.setPower(power);
        meta.addEffects(effects);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder removeData(NamespacedKey key) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.remove(key);

        item.setItemMeta(meta);
        return this;
    }

    public <T, Z> ItemBuilder setData(NamespacedKey key, PersistentDataType<T, Z> type, @NotNull Z value) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(key, type, value);

        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder replace(String target, @NotNull Object replace) {
        Component text = ComponentUtil.deserialize((replace instanceof Double number ? PluginUtils.fixedDouble(number) : replace).toString());
        return replaceName(target, text).replaceLore(target, text);
    }

    public ItemBuilder replace(UnaryOperator<Component> operator) {
        return replaceName(operator).replaceLore(operator);
    }

    public ItemBuilder replaceName(String target, Component replace) {
        return replaceName(string -> string.replaceText(builder -> builder.match(target).replacement(replace)));
    }

    public ItemBuilder replaceName(UnaryOperator<Component> operator) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        if (meta.hasDisplayName()) {
            meta.displayName(operator.apply(meta.displayName()));
        }

        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder replaceLore(String target, Component replace) {
        return replaceLore(string -> string.replaceText(builder -> builder.matchLiteral(target).replacement(replace)));
    }

    public ItemBuilder replaceLore(UnaryOperator<Component> operator) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        List<Component> lore;
        if (meta.hasLore() && (lore = meta.lore()) != null) {
            lore.replaceAll(operator);
            meta.lore(lore);
        }

        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder applyMultiLineLore(
            List<String> strings,
            String advancedPlaceholder,
            Component noResultLine) {
        List<Component> lore = getLore();
        if (lore.isEmpty()) return this;

        int indexOf = -1;
        for (int i = 0; i < lore.size(); i++) {
            if (ComponentUtil.serialize(lore.get(i)).contains(advancedPlaceholder)) {
                indexOf = i;
                break;
            }
        }

        if (indexOf != -1) {
            Component toReplace = lore.get(indexOf);

            List<Component> newLore = new ArrayList<>();

            if (indexOf > 0) {
                newLore.addAll(lore.subList(0, indexOf));
            }

            if (strings.isEmpty()) {
                newLore.add(toReplace.replaceText(builder -> builder.matchLiteral(advancedPlaceholder).replacement(noResultLine)));
            } else {
                for (String string : strings) {
                    newLore.add(toReplace.replaceText(builder -> builder.matchLiteral(advancedPlaceholder).replacement(string)));
                }
            }

            if (lore.size() > 1 && indexOf < lore.size() - 1) {
                newLore.addAll(lore.subList(indexOf + 1, lore.size()));
            }

            return setLore(newLore);
        }

        return this;
    }

    public ItemStack build() {
        return item;
    }
}