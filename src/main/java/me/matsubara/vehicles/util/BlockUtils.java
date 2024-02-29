package me.matsubara.vehicles.util;

import me.matsubara.vehicles.model.stand.StandSettings;
import me.matsubara.vehicles.vehicle.VehicleType;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.*;
import org.bukkit.block.data.type.*;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

public class BlockUtils {

    private static final Map<Material, Function<BlockData, Double>> HEIGHTS = new HashMap<>();
    private static final Set<Material> UNDERWATER_BLOCKS = builder()
            .add(Tag.UNDERWATER_BONEMEALS)
            .add(Material.KELP)
            .add(Material.KELP_PLANT)
            .add(Material.WATER)
            .add(Material.TALL_SEAGRASS)
            .add(Material.BUBBLE_COLUMN)
            .get();

    public static final Set<Material> BUGGY_HEIGHTS = builder()
            .add(Material.HOPPER, Material.CAULDRON, Material.COMPOSTER, Material.POINTED_DRIPSTONE, Material.CHORUS_PLANT)
            .add(Tag.DOORS)
            .get();
    public static final Set<Material> HEADS = builder()
            .add(Material.SKELETON_SKULL,
                    Material.WITHER_SKELETON_SKULL,
                    Material.PLAYER_HEAD,
                    Material.ZOMBIE_HEAD,
                    Material.CREEPER_HEAD,
                    Material.DRAGON_HEAD,
                    Material.PIGLIN_HEAD)
            .get();

    static {
        // https://minecraft.fandom.com/wiki/Solid_block

        // 0.9375 (includes no-tilt big dripleaf and bell attached to a wall)
        fillHeights(data -> 0.9375d, Material.DIRT_PATH, Material.FARMLAND, Material.HONEY_BLOCK, Material.CACTUS);

        // 0.875 (includes grindstone attached to a wall)
        fillHeights(data -> 0.875d, builder()
                .add(Material.CHEST, Material.ENDER_CHEST, Material.TRAPPED_CHEST, Material.BREWING_STAND, Material.LECTERN)
                .add(Tag.CANDLE_CAKES));

        // 0.75
        fillHeights(data -> 0.75d, Material.ENCHANTING_TABLE);

        // 0.6875
        fillHeights(data -> 0.6875d, Material.CONDUIT);

        // 0.5625
        fillHeights(data -> 0.5625d, builder()
                .add(Material.STONECUTTER)
                .add(Tag.BEDS));

        // 0.5 (except for TOP or DOUBLE slabs, they use 1)
        fillHeights(
                data -> data instanceof Slab slabData ? (slabData.getType() == Slab.Type.BOTTOM ? 0.5d : 1.0d) : 0.5d,
                builder().add(
                                Material.SCULK_SENSOR,
                                Material.CALIBRATED_SCULK_SENSOR,
                                Material.SCULK_SHRIEKER,
                                Material.CAKE)
                        .add(HEADS)
                        .add(Tag.SLABS));

        // 0.4375 (includes 4 pickles)
        fillHeights(data -> 0.4375d, builder()
                .add(Material.TURTLE_EGG)
                .add(Tag.CAMPFIRES));

        // 0.375 (except for 4 pickles)
        fillHeights(
                data -> data instanceof SeaPickle pickle ? (pickle.getPickles() == pickle.getMaximumPickles() ? 0.4375d : 0.375d) : 0.375d,
                builder()
                        .add(Material.FLOWER_POT, Material.DAYLIGHT_DETECTOR, Material.SEA_PICKLE)
                        .add(Tag.CANDLES));

        // 0.125
        fillHeights(data -> 0.125d, Material.COMPARATOR, Material.REPEATER);

        // 0.09375
        fillHeights(data -> 0.09375d, Material.LILY_PAD);

        // 0.0625
        fillHeights(data -> 0.0625d, builder().add(Material.MOSS_CARPET).add(Tag.WOOL_CARPETS));

        // All mixed below.

        fillHeights(data -> {
            if (data instanceof BigDripleaf dripleaf) {
                return switch (dripleaf.getTilt()) {
                    case NONE, UNSTABLE -> 0.9375d;
                    case PARTIAL -> 0.8125d;
                    case FULL -> 0.0d;
                };
            }
            return Double.MIN_VALUE;
        }, Material.BIG_DRIPLEAF);

        fillHeights(data -> {
            if (data instanceof EndPortalFrame frame) {
                return frame.hasEye() ? 1.0d : 0.8125d;
            }
            return Double.MIN_VALUE;
        }, Material.END_PORTAL_FRAME);

        fillHeights(data -> {
            if (data instanceof Directional directional) {
                return ArrayUtils.contains(PluginUtils.AXIS, directional.getFacing()) ? 0.625d : 1.0d;
            }
            return Double.MIN_VALUE;
        }, Material.LIGHTNING_ROD, Material.END_ROD);

        fillHeights(data -> {
            if (data instanceof Chain chain) {
                return chain.getAxis() != Axis.Y ? 0.59375d : 1.0d;
            }
            return Double.MIN_VALUE;
        }, Material.CHAIN);

        fillHeights(data -> {
            if (data instanceof Bell bell) {
                return bell.getAttachment().name().contains("WALL") ? 0.9375d : 1.0d;
            }
            return Double.MIN_VALUE;
        }, Material.BELL);

        fillHeights(data -> {
            if (data instanceof Grindstone grindstone) {
                return grindstone.getAttachedFace() == FaceAttachable.AttachedFace.WALL ? 0.875d : 1.0d;
            }
            return Double.MIN_VALUE;
        }, Material.GRINDSTONE);

        fillHeights(data -> {
            if (data instanceof Snow snow) {
                return (snow.getLayers() - 1) * 0.125d;
            }
            return Double.MIN_VALUE;
        }, Material.SNOW);

        fillHeights(data -> {
            if (data instanceof TrapDoor door && !door.isOpen()) {
                return door.getHalf() == Bisected.Half.TOP ? 1.0d : 0.1875d;
            }
            return Double.MIN_VALUE;
        }, Tag.TRAPDOORS.getValues());

        fillHeights(
                data -> data instanceof Gate gate && gate.isOpen() ? 0.0d : 1.5d,
                builder()
                        .add(Tag.WALLS)
                        .add(Tag.FENCES)
                        .add(Tag.FENCE_GATES));

        handleAmethyst(Material.SMALL_AMETHYST_BUD, 0.75d, 0.1875d);
        handleAmethyst(Material.MEDIUM_AMETHYST_BUD, 0.8125, 0.25d);
        handleAmethyst(Material.LARGE_AMETHYST_BUD, 0.8125, 0.3125d);
        handleAmethyst(Material.AMETHYST_CLUSTER, 0.8125d, 0.4375d);
    }

    public static boolean isBlockFromWater(@NotNull Block block) {
        return (block.getBlockData() instanceof Waterlogged logged && logged.isWaterlogged()) || UNDERWATER_BLOCKS.contains(block.getType());
    }

    public static Double getMaterialHeight(@NotNull Block block) {
        Function<BlockData, Double> function = HEIGHTS.get(block.getType());
        return function != null ? function.apply(block.getBlockData()) : Double.MIN_VALUE;
    }

    private static void fillHeights(Function<BlockData, Double> function, Material... materials) {
        fillHeights(function, Arrays.asList(materials));
    }

    private static void fillHeights(Function<BlockData, Double> function, @NotNull MaterialBuilder builder) {
        fillHeights(function, builder.get());
    }

    private static void fillHeights(Function<BlockData, Double> function, @NotNull Collection<Material> materials) {
        for (Material material : materials) {
            HEIGHTS.put(material, function);
        }
    }

    private static void handleAmethyst(Material material, double wallHeight, double otherHeight) {
        fillHeights(data -> data instanceof Directional directional ?
                        (ArrayUtils.contains(PluginUtils.AXIS, directional.getFacing()) ? wallHeight : otherHeight) :
                        Double.MIN_VALUE,
                material);
    }

    @Contract("_ -> new")
    private static @NotNull org.bukkit.util.Vector offsetFromSettings(@NotNull StandSettings settings) {
        return new org.bukkit.util.Vector(
                settings.getXOffset() + settings.getExternalX(),
                settings.getYOffset(),
                settings.getZOffset() + settings.getExternalZ());
    }

    public static @NotNull Location getCorrectLocation(@Nullable UUID driverUUID, VehicleType type, @NotNull Location main, StandSettings settings) {
        Vector offset = offsetFromSettings(settings);

        Location location = main.clone();

        Player driver;
        if (type == VehicleType.TANK
                && settings.getPartName().startsWith("TOP_PART")
                && driverUUID != null
                && (driver = Bukkit.getPlayer(driverUUID)) != null
                && driver.isOnline()) {
            location.setYaw(driver.getLocation().getYaw());
        }

        location.add(PluginUtils.offsetVector(offset, location.getYaw(), location.getPitch()));

        float extraYaw = settings.getExtraYaw();
        if (extraYaw != 0.0f) location.setYaw(yaw(location.getYaw() + extraYaw));

        // Llama height is different from armor stand ones.
        if (settings.getPartName().startsWith("CHAIR")) {
            location.subtract(0.0d, 0.45d, 0.0d)
                    .add(PluginUtils.offsetVector(
                            new Vector(0.0d, 0.0d, 0.3125d),
                            location.getYaw(),
                            location.getPitch()));
        }

        return location;
    }

    public static @Nullable Location getHighestLocation(@NotNull Location location) {
        World world = location.getWorld();
        Objects.requireNonNull(world);

        Block highest = world.getHighestBlockAt(location);

        if (highest.getY() > location.getBlockY()) {
            if (highest.getY() < world.getMinHeight()) return null;

            while (true) {
                if (highest.getY() < world.getMinHeight()) return null;

                highest = highest.getRelative(BlockFace.DOWN);

                if (!highest.isPassable()) {
                    Block top = highest.getRelative(BlockFace.UP);
                    if (top.isPassable() && top.getRelative(BlockFace.UP).isPassable()) {
                        location.setY(highest.getY() + 1);
                        return location;
                    }
                }
            }
        } else {
            location.setY(highest.getY() + 1);
            return location;
        }
    }

    public static float yaw(float yaw) {
        float temp = yaw % 360.0f;
        return yaw < 0.0f ? temp + 360.0f : temp;
    }

    private static @NotNull MaterialBuilder builder() {
        return new MaterialBuilder();
    }

    public static class MaterialBuilder {

        private final Set<Material> materials = new HashSet<>();

        private MaterialBuilder() {
        }

        public MaterialBuilder add(Material... materials) {
            Collections.addAll(this.materials, materials);
            return this;
        }

        public MaterialBuilder add(@NotNull Tag<Material> tag) {
            return add(tag.getValues());
        }

        public MaterialBuilder add(Collection<Material> materials) {
            this.materials.addAll(materials);
            return this;
        }

        public Set<Material> get() {
            return Collections.unmodifiableSet(materials);
        }
    }
}