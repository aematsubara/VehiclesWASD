package me.matsubara.vehicles.util;

import com.cryptomorin.xseries.reflection.XReflection;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import me.matsubara.vehicles.model.stand.StandSettings;
import me.matsubara.vehicles.vehicle.VehicleType;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.*;
import org.bukkit.block.data.type.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("DataFlowIssue")
public class BlockUtils {

    private static final Map<Material, Integer> STATE_ID = new HashMap<>();
    private static final Map<Material, Function<BlockData, Double>> HEIGHTS = new HashMap<>();
    private static final Set<Material> UNDERWATER_BLOCKS = builder()
            .add(Tag.UNDERWATER_BONEMEALS)
            .add(Material.KELP)
            .add(Material.KELP_PLANT)
            .add(Material.WATER)
            .add(Material.TALL_SEAGRASS)
            .add(Material.BUBBLE_COLUMN)
            .get();

    @SuppressWarnings("deprecation")
    public static final Set<Material> HEIGHT_FIX = builder()
            .add(Tag.STAIRS)
            .add(Tag.CARPETS)
            .add(Tag.SLABS)
            .add(Material.WOODEN_SHOVEL)
            .add(Material.STONE_SHOVEL)
            .add(Material.IRON_SHOVEL)
            .add(Material.GOLDEN_SHOVEL)
            .add(Material.DIAMOND_SHOVEL)
            .add(Material.NETHERITE_SHOVEL)
            .get();

    public static final Set<Material> BUGGY_HEIGHTS = builder()
            .add(Material.HOPPER, Material.CAULDRON, Material.COMPOSTER, Material.POINTED_DRIPSTONE, Material.CHORUS_PLANT)
            .add(Tag.DOORS)
            .get();

    public static final Set<Material> HEADS = builder()
            .add(
                    Material.SKELETON_SKULL,
                    Material.WITHER_SKELETON_SKULL,
                    Material.PLAYER_HEAD,
                    Material.ZOMBIE_HEAD,
                    Material.CREEPER_HEAD,
                    Material.DRAGON_HEAD)
            .addString("PIGLIN_HEAD")
            .get();

    // Classes.
    private static final Class<?> BLOCK_ACCESS = Reflection.getNMSClass("world.level", "BlockGetter", "IBlockAccess");
    private static final Class<?> RAY_TRACE = Reflection.getNMSClass("world.level", "ClipContext", "RayTrace");
    private static final Class<?> VEC_3D = Reflection.getNMSClass("world.phys", "Vec3", "Vec3D");
    private static final Class<?> BLOCK_CONTEXT = Reflection.getNMSClass("world.level", "ClipContext$Block", "RayTrace$BlockCollisionOption");
    private static final Class<?> FLUID_CONTEXT = Reflection.getNMSClass("world.level", "ClipContext$Fluid", "RayTrace$FluidCollisionOption");
    private static final Class<?> ENTITY = Reflection.getNMSClass("world.entity", "Entity", "Entity");
    private static final Class<?> CRAFT_WORLD = XReflection.getCraftClass("CraftWorld");
    private static final Class<?> CRAFT_PLAYER = XReflection.getCraftClass("entity.CraftPlayer");
    private static final Class<?> MOVING_OBJECT_POSITION = Reflection.getNMSClass("world.phys", "HitResult", "MovingObjectPosition");
    private static final Class<?> MOVING_OBJECT_POSITION_BLOCK = Reflection.getNMSClass("world.phys", "BlockHitResult", "MovingObjectPositionBlock");
    private static final Class<?> BLOCK_POSITION = Reflection.getNMSClass("core", "BlockPos", "BlockPosition");
    private static final Class<?> BASE_BLOCK_POSITION = Reflection.getNMSClass("core", "Vec3i", "BaseBlockPosition");
    private static final Class<?> CRAFT_BLOCK_DATA = XReflection.getCraftClass("block.data.CraftBlockData");
    private static final Class<?> BLOCK = Reflection.getNMSClass("world.level.block", "Block", "Block");
    private static final Class<?> BLOCK_STATE = Reflection.getNMSClass("world.level.block.state", "BlockState", "IBlockData");

    // Methods.
    private static final MethodHandle GET_VEC3_X = Reflection.getMethod(VEC_3D, "a", MethodType.methodType(double.class), "getX");
    private static final MethodHandle GET_VEC3_Y = Reflection.getMethod(VEC_3D, "b", MethodType.methodType(double.class), "getY");
    private static final MethodHandle GET_VEC3_Z = Reflection.getMethod(VEC_3D, "c", MethodType.methodType(double.class), "getZ");
    private static final MethodHandle CLIP = Reflection.getMethod(BLOCK_ACCESS, "a", MethodType.methodType(MOVING_OBJECT_POSITION_BLOCK, RAY_TRACE), "rayTrace");
    private static final MethodHandle GET_WORLD_HANDLE = Reflection.getMethod(CRAFT_WORLD, "getHandle");
    private static final MethodHandle GET_PLAYER_HANDLE = Reflection.getMethod(CRAFT_PLAYER, "getHandle");
    private static final MethodHandle GET_LOCATION = Reflection.getMethod(MOVING_OBJECT_POSITION, "e", MethodType.methodType(VEC_3D), "g", "getPos");
    private static final MethodHandle GET_BLOCK_POS = Reflection.getMethod(MOVING_OBJECT_POSITION_BLOCK, "a", MethodType.methodType(BLOCK_POSITION), "b", "getBlockPosition");
    private static final MethodHandle GET_POS_X = Reflection.getMethod(BASE_BLOCK_POSITION, "u", MethodType.methodType(int.class), "getX");
    private static final MethodHandle GET_POS_Y = Reflection.getMethod(BASE_BLOCK_POSITION, "v", MethodType.methodType(int.class), "getY");
    private static final MethodHandle GET_POS_Z = Reflection.getMethod(BASE_BLOCK_POSITION, "w", MethodType.methodType(int.class), "getZ");
    private static final MethodHandle GET_STATE = Reflection.getMethod(CRAFT_BLOCK_DATA, "getState");
    private static final MethodHandle GET_ID = Reflection.getMethod(
            BLOCK,
            "getId",
            MethodType.methodType(int.class, BLOCK_STATE),
            true,
            true,
            "i",
            "j",
            "getCombinedId");

    // Constructors.
    private static final MethodHandle clipConstructor = Reflection.getConstructor(RAY_TRACE, VEC_3D, VEC_3D, BLOCK_CONTEXT, FLUID_CONTEXT, ENTITY);
    private static final MethodHandle vec3dConstructor = Reflection.getConstructor(VEC_3D, double.class, double.class, double.class);

    // Fields.
    private static final Object OUTLINE = Reflection.getFieldValue(Reflection.getFieldGetter(BLOCK_CONTEXT, "b"));
    private static final Object NONE = Reflection.getFieldValue(Reflection.getFieldGetter(FLUID_CONTEXT, "a"));

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
                                Material.CAKE)
                        .addString("SCULK_SHRIEKER", "CALIBRATED_SCULK_SENSOR")
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
        // noinspection deprecation, "carpets" is "wool_carpets" since 1.19.
        fillHeights(data -> 0.0625d, builder().add(Material.MOSS_CARPET).add(Tag.CARPETS));

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

    public static @NotNull Location getCorrectLocation(@Nullable UUID driverUUID, VehicleType type, @NotNull Location main, @NotNull StandSettings settings) {
        Location location = main.clone();

        Player driver;
        if (type == VehicleType.TANK
                && settings.getPartName().startsWith("TOP_PART")
                && driverUUID != null
                && (driver = Bukkit.getPlayer(driverUUID)) != null
                && driver.isOnline()) {
            location.setYaw(driver.getLocation().getYaw());
        }

        location.add(PluginUtils.offsetVector(settings.getOffset(), location.getYaw(), location.getPitch()));

        float extraYaw = settings.getExtraYaw();
        if (extraYaw != 0.0f) location.setYaw(yaw(location.getYaw() + extraYaw));

        //For some reason, these items appear lower than they should.
        ItemStack item;
        if (XReflection.supports(21, 2)
                && settings.isSmall()
                && (item = settings.getEquipment().get(EquipmentSlot.HELMET)) != null
                && !item.isEmpty()) {
            Material material = SpigotConversionUtil.toBukkitItemMaterial(item.getType());
            if (HEIGHT_FIX.contains(material)) {
                location.add(0.0d, 0.02125d, 0.0d);
            }
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

    public static @Nullable Vector getClickedPosition(@NotNull PlayerInteractEvent event) {
        if (XReflection.supports(20, 1)) {
            return event.getClickedPosition();
        }

        Player player = event.getPlayer();
        Location location = player.getLocation();

        Vector locationVector = new Vector(
                location.getX(),
                location.getY() + player.getEyeHeight(),
                location.getZ());

        float yaw = location.getYaw();
        float pitch = location.getPitch();

        float yawCos = cos(-yaw * 0.017453292f - 3.1415927f);
        float yawSin = sin(-yaw * 0.017453292f - 3.1415927f);

        float pitchCos = -cos(-pitch * 0.017453292f);
        float pitchSin = sin(-pitch * 0.017453292f);

        float combinedCos = yawSin * pitchCos;
        float combinedSinCos = yawCos * pitchCos;

        double maxDistance = player.getGameMode() == GameMode.CREATIVE ? 5.0d : 4.5d;

        Vector direction = locationVector.clone().add(new Vector(
                (double) combinedCos * maxDistance,
                (double) pitchSin * maxDistance,
                (double) combinedSinCos * maxDistance));

        World world = player.getWorld();

        try {
            Object craftWorld = CRAFT_WORLD.cast(world);
            Object nmsWorld = GET_WORLD_HANDLE.invoke(craftWorld);

            Object result = CLIP.invoke(nmsWorld, clipConstructor.invoke(
                    vec3dConstructor.invoke(locationVector.getX(), locationVector.getY(), locationVector.getZ()),
                    vec3dConstructor.invoke(direction.getX(), direction.getY(), direction.getZ()),
                    OUTLINE,
                    NONE,
                    GET_PLAYER_HANDLE.invoke(CRAFT_PLAYER.cast(player))));

            Object blockPos = GET_BLOCK_POS.invoke(result);
            int blockPosX = (int) GET_POS_X.invoke(blockPos);
            int blockPosY = (int) GET_POS_Y.invoke(blockPos);
            int blockPosZ = (int) GET_POS_Z.invoke(blockPos);

            Object position = GET_LOCATION.invoke(result);
            return new Vector(
                    (double) GET_VEC3_X.invoke(position),
                    (double) GET_VEC3_Y.invoke(position),
                    (double) GET_VEC3_Z.invoke(position))
                    .subtract(new Vector(
                            blockPosX,
                            blockPosY,
                            blockPosZ));
        } catch (Throwable throwable) {
            return null;
        }
    }

    public static int getBlockStateId(@NotNull Block block) {
        Material type = block.getType();

        Integer id = STATE_ID.get(type);
        if (id != null) return id;

        try {
            Object data = CRAFT_BLOCK_DATA.cast(block.getBlockData());
            Object state = GET_STATE.invoke(data);

            int temp = (int) GET_ID.invoke(state);
            STATE_ID.put(type, temp);

            return temp;
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return -1;
        }
    }

    private static final float[] SIN = make(new float[65536], (floats) -> {
        for (int i = 0; i < floats.length; i++) {
            floats[i] = (float) Math.sin((double) i * Math.PI * 2.0d / 65536.0d);
        }
    });

    @Contract("_, _ -> param1")
    private static <T> T make(T object, @NotNull Consumer<T> action) {
        action.accept(object);
        return object;
    }

    private static float sin(float value) {
        return SIN[(int) (value * 10430.378f) & '\uffff'];
    }

    private static float cos(float value) {
        return SIN[(int) (value * 10430.378f + 16384.0f) & '\uffff'];
    }

    public static float yaw(float yaw) {
        float temp = yaw % 360.0f;
        return yaw < 0.0f ? temp + 360.0f : temp;
    }

    public static @NotNull MaterialBuilder builder() {
        return new MaterialBuilder();
    }

    public static class MaterialBuilder {

        private final Set<Material> materials = new HashSet<>();

        private MaterialBuilder() {
        }

        public MaterialBuilder add(Material... materials) {
            return add(Arrays.asList(materials));
        }

        public MaterialBuilder add(@NotNull Tag<Material> tag) {
            return add(tag.getValues());
        }

        public MaterialBuilder add(Collection<Material> materials) {
            this.materials.addAll(materials);
            return this;
        }

        public MaterialBuilder addString(String... materials) {
            return addString(Arrays.asList(materials));
        }

        public MaterialBuilder addString(@NotNull Collection<String> materials) {
            this.materials.addAll(materials.stream()
                    .map(string -> PluginUtils.getOrNull(Material.class, string))
                    .filter(Objects::nonNull)
                    .toList());
            return this;
        }

        public Set<Material> get() {
            return Collections.unmodifiableSet(materials);
        }
    }
}