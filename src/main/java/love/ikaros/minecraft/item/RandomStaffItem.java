package love.ikaros.minecraft.item;

import love.ikaros.minecraft.sound.ModSoundEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class RandomStaffItem extends Item {

    private static final String DATA_FILE = "world_backup.nbt";
    private static final AtomicBoolean IS_RECORDING = new AtomicBoolean(false);
    private static BlockPos recordCenter = null;

    private final Map<BlockPos, BlockState> cachedData = new ConcurrentHashMap<>();
    private volatile Map<BlockPos, BlockState> tempRestoreMap = null;

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public RandomStaffItem(Settings settings) {
        super(settings);
    }

    /**
     * 左键点击方块逻辑：保持原版效果（开启时空记录）
     */
    @Override
    public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity miner) {
        if (!world.isClient) {
            if (IS_RECORDING.get()) {
                miner.sendMessage(Text.of("§c[墨忒耳] 正在持久化，请稍候..."), true);
            } else {
                startParallelRecord(world, pos, miner);
            }
        }
        return false;
    }

    /**
     * 右键逻辑：区分 Shift 切换天气 和 普通长按回溯
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        // 新增：如果是 Shift (潜行) + 右键，则切换天气
        if (user.isSneaking()) {
            if (!world.isClient) {
                cycleWeather(world, user);
            }
            return TypedActionResult.success(stack);
        }

        // 原有逻辑：长按回溯
        if (!world.isClient) {
            if (IS_RECORDING.get()) return TypedActionResult.fail(stack);
            if (cachedData.isEmpty()) loadCacheFromFile();
            if (!cachedData.isEmpty()) {
                tempRestoreMap = new ConcurrentHashMap<>(cachedData);
            }
        }
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    /**
     * 私有逻辑：天气按顺序切换 (晴天 -> 雨天 -> 雷雨 -> 循环)
     */
    private void cycleWeather(World world, PlayerEntity player) {
        if (world instanceof ServerWorld serverWorld) {
            String weatherName;

            if (serverWorld.isThundering()) {
                // 当前雷雨 -> 变晴
                serverWorld.setWeather(120000, 0, false, false);
                weatherName = "§e晴天";
            } else if (serverWorld.isRaining()) {
                // 当前雨天 -> 变雷雨
                serverWorld.setWeather(0, 120000, true, true);
                weatherName = "§8雷雨";
            } else {
                // 当前晴天 -> 变雨天
                serverWorld.setWeather(0, 120000, true, false);
                weatherName = "§b雨天";
            }

            player.sendMessage(Text.of("§6[墨忒耳] §f气候已扭转至: " + weatherName), true);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8F, 0.5F);
        }
    }

    private void startParallelRecord(World world, BlockPos center, PlayerEntity player) {
        IS_RECORDING.set(true);
        recordCenter = center;

        final int radius = 70;
        final int totalLayers = (radius * 2) + 1;
        AtomicInteger completedLayers = new AtomicInteger(0);
        List<NbtCompound> collectedEntries = Collections.synchronizedList(new ArrayList<>());

        player.sendMessage(Text.of("§b[墨忒耳] 开始时空记录..."), false);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSoundEvents.HIYOLI_WAND_SET, SoundCategory.PLAYERS, 10.0F, 1F);

        CompletableFuture.runAsync(() -> {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int yOffset = -radius; yOffset <= radius; yOffset++) {
                final int currentY = yOffset;
                futures.add(CompletableFuture.runAsync(() -> {
                    for (int x = -radius; x <= radius; x++) {
                        for (int z = -radius; z <= radius; z++) {
                            BlockPos targetPos = center.add(x, currentY, z);
                            BlockState blockState = world.getBlockState(targetPos);
                            if (!blockState.isAir()) {
                                NbtCompound entry = new NbtCompound();
                                entry.put("pos", NbtHelper.fromBlockPos(targetPos));
                                entry.put("state", NbtHelper.fromBlockState(blockState));
                                collectedEntries.add(entry);
                            }
                        }
                    }
                    int finished = completedLayers.incrementAndGet();
                    player.sendMessage(Text.of(String.format("§e进度: §a%.1f%%", (finished / (float) totalLayers) * 100)), true);
                }, EXECUTOR));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
                try {
                    NbtCompound root = new NbtCompound();
                    NbtList nbtList = new NbtList();
                    nbtList.addAll(collectedEntries);
                    root.put("Data", nbtList);
                    NbtIo.writeCompressed(root, new File(DATA_FILE));
                    IS_RECORDING.set(false);
                    loadCacheFromFile();
                    player.sendMessage(Text.of("§6[完成] 记录完成"), false);
                } catch (IOException e) {
                    IS_RECORDING.set(false);
                }
            });
        });
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient && IS_RECORDING.get() && recordCenter != null) {
            double cx = recordCenter.getX() + 0.5;
            double baseY = recordCenter.getY();
            double cz = recordCenter.getZ() + 0.5;

            double totalHeight = 40.0;
            double halfWidth = 2.0;
            double armYCenter = 28.0;
            double armHalfHeight = 2.0;
            double armLength = 15.0;

            for (double y = 0; y <= totalHeight; y += 0.8) {
                spawnThickParticle(world, cx - halfWidth, baseY + y, cz - halfWidth);
                spawnThickParticle(world, cx + halfWidth, baseY + y, cz - halfWidth);
                spawnThickParticle(world, cx - halfWidth, baseY + y, cz + halfWidth);
                spawnThickParticle(world, cx + halfWidth, baseY + y, cz + halfWidth);
                if (y % 4 == 0) drawRectFrame(world, cx, cz, baseY + y, halfWidth, halfWidth);
            }

            for (double xOff = -armLength; xOff <= armLength; xOff += 0.8) {
                if (Math.abs(xOff) > halfWidth) {
                    double yTop = baseY + armYCenter + armHalfHeight;
                    double yBottom = baseY + armYCenter - armHalfHeight;
                    spawnThickParticle(world, cx + xOff, yTop, cz - halfWidth);
                    spawnThickParticle(world, cx + xOff, yTop, cz + halfWidth);
                    spawnThickParticle(world, cx + xOff, yBottom, cz - halfWidth);
                    spawnThickParticle(world, cx + xOff, yBottom, cz + halfWidth);
                    if (Math.abs(xOff) >= armLength - 0.5) drawRectFrame(world, cx + xOff, cz, baseY + armYCenter, halfWidth, halfWidth);
                }
            }
            if (world.random.nextFloat() > 0.6f) world.addParticle(ParticleTypes.FLASH, cx, baseY + armYCenter, cz, 0, 0, 0);
        }
    }

    private void drawRectFrame(World world, double cx, double cz, double y, double rx, double rz) {
        for (double i = -rx; i <= rx; i += 1.0) {
            spawnThickParticle(world, cx + i, y, cz - rz);
            spawnThickParticle(world, cx + i, y, cz + rz);
            spawnThickParticle(world, cx - rx, y, cz + i);
            spawnThickParticle(world, cx + rx, y, cz + i);
        }
    }

    private void spawnThickParticle(World world, double x, double y, double z) {
        world.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0, 0);
        if (world.random.nextFloat() > 0.90f) world.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0, 0.01, 0);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!world.isClient && user instanceof PlayerEntity player) {
            Map<BlockPos, BlockState> currentTempMap = this.tempRestoreMap;
            if (currentTempMap != null) {
                int usedTicks = this.getMaxUseTime(stack) - remainingUseTicks;
                int batchSize = 10 + (usedTicks / 20) * 400;
                performDynamicRandomRestore(world, player, stack, batchSize, currentTempMap);
            }
        }
    }

    private void performDynamicRandomRestore(World world, PlayerEntity player, ItemStack stack, int amount, Map<BlockPos, BlockState> currentMap) {
        if (currentMap.isEmpty()) {
            player.sendMessage(Text.of("§a[完成] 时空回溯完成！"), true);
            this.tempRestoreMap = null;
            return;
        }

        stack.damage(1, player, (p) -> p.sendToolBreakStatus(player.getActiveHand()));

        Random random = world.getRandom();
        List<BlockPos> keys = new ArrayList<>(currentMap.keySet());
        int actualToRestore = Math.min(amount, keys.size());

        for (int i = 0; i < actualToRestore; i++) {
            if (keys.isEmpty()) break;
            int index = random.nextInt(keys.size());
            BlockPos targetPos = keys.remove(index);
            BlockState savedState = currentMap.remove(targetPos);
            if (savedState != null && world.isInBuildLimit(targetPos)) {
                if (world.getBlockState(targetPos) != savedState) {
                    world.setBlockState(targetPos, savedState);
                }
            }
        }

        int total = cachedData.size();
        if (total > 0) {
            double progress = (double) (total - currentMap.size()) / total * 100;
            player.sendMessage(Text.of(String.format("§d时空回溯中: §f%.2f%% §8(速度: %d/t)", progress, actualToRestore)), true);
        }

        if (world.getTime() % 4 == 0) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSoundEvents.HIYOLI_WAND_USE, SoundCategory.PLAYERS, 0.5F, 1.2F);
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        this.tempRestoreMap = null;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) { return UseAction.BOW; }

    @Override
    public int getMaxUseTime(ItemStack stack) { return 72000; }

    private void loadCacheFromFile() {
        File file = new File(DATA_FILE);
        if (!file.exists()) return;
        try {
            NbtCompound root = NbtIo.readCompressed(file);
            NbtList list = root.getList("Data", 10);
            synchronized (cachedData) {
                cachedData.clear();
                for (int i = 0; i < list.size(); i++) {
                    NbtCompound entry = list.getCompound(i);
                    cachedData.put(NbtHelper.toBlockPos(entry.getCompound("pos")),
                            NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), entry.getCompound("state")));
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
    }
}