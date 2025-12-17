package love.ikaros.minecraft.item;

import love.ikaros.minecraft.sound.ModSoundEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
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

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public RandomStaffItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity miner) {
        if (!world.isClient) {
            if (IS_RECORDING.get()) {
                miner.sendMessage(Text.of("§c[系统] 正在持久化，请稍候..."), true);
            } else {
                startParallelRecord(world, pos, miner);
            }
        }
        return false;
    }

    private void startParallelRecord(World world, BlockPos center, PlayerEntity player) {
        IS_RECORDING.set(true);
        recordCenter = center;

        final int radius = 70;
        final int totalLayers = (radius * 2) + 1;
        AtomicInteger completedLayers = new AtomicInteger(0);
        List<NbtCompound> collectedEntries = Collections.synchronizedList(new ArrayList<>());

        player.sendMessage(Text.of("§b[系统] 开始时空记录..."), false);
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

            // --- 十字架结构参数 (基于触发点向上) ---
            double totalHeight = 40.0;    // 总高度
            double halfWidth = 2.0;       // 截面半径为2，总宽4
            double armYCenter = 28.0;     // 横梁中心点的高度
            double armHalfHeight = 2.0;   // 横梁半高
            double armLength = 15.0;      // 横梁从中心向单侧延伸的长度

            // 1. 绘制垂直主柱 (4x4 截面轮廓)
            // 采用步长 0.8 减少粒子堆积，防止超过客户端渲染上限
            for (double y = 0; y <= totalHeight; y += 0.8) {
                // 绘制立柱的 4 条主棱边
                spawnThickParticle(world, cx - halfWidth, baseY + y, cz - halfWidth);
                spawnThickParticle(world, cx + halfWidth, baseY + y, cz - halfWidth);
                spawnThickParticle(world, cx - halfWidth, baseY + y, cz + halfWidth);
                spawnThickParticle(world, cx + halfWidth, baseY + y, cz + halfWidth);

                // 每隔 4 格画一个横向加固框，增强体积感
                if (y % 4 == 0) {
                    drawRectFrame(world, cx, cz, baseY + y, halfWidth, halfWidth);
                }
            }

            // 2. 绘制横梁 (4x4 截面轮廓)
            // 绘制横梁的四个水平长棱
            for (double xOff = -armLength; xOff <= armLength; xOff += 0.8) {
                // 跳过主柱重叠区域以优化性能
                if (Math.abs(xOff) > halfWidth) {
                    double yTop = baseY + armYCenter + armHalfHeight;
                    double yBottom = baseY + armYCenter - armHalfHeight;

                    spawnThickParticle(world, cx + xOff, yTop, cz - halfWidth);
                    spawnThickParticle(world, cx + xOff, yTop, cz + halfWidth);
                    spawnThickParticle(world, cx + xOff, yBottom, cz - halfWidth);
                    spawnThickParticle(world, cx + xOff, yBottom, cz + halfWidth);

                    // 横梁末端封口
                    if (Math.abs(xOff) >= armLength - 0.5) {
                        drawRectFrame(world, cx + xOff, cz, baseY + armYCenter, halfWidth, halfWidth);
                    }
                }
            }

            // 3. 核心交汇处强化特效
            if (world.random.nextFloat() > 0.6f) {
                world.addParticle(ParticleTypes.FLASH, cx, baseY + armYCenter, cz, 0, 0, 0);
            }
        }
    }

    /**
     * 在指定 Y 高度绘制一个 4x4 的水平矩形框
     */
    private void drawRectFrame(World world, double cx, double cz, double y, double rx, double rz) {
        for (double i = -rx; i <= rx; i += 1.0) {
            spawnThickParticle(world, cx + i, y, cz - rz);
            spawnThickParticle(world, cx + i, y, cz + rz);
            spawnThickParticle(world, cx - rx, y, cz + i);
            spawnThickParticle(world, cx + rx, y, cz + i);
        }
    }

    /**
     * 粒子生成核心：使用 END_ROD 构筑形状，SOUL_FIRE_FLAME 增加亮度并延长视觉停留
     */
    private void spawnThickParticle(World world, double x, double y, double z) {
        // 基础形状粒子
        world.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0, 0);

        // 灵魂火粒子：停留时间长，且有微弱的升腾感
        if (world.random.nextFloat() > 0.90f) {
            world.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0, 0.01, 0);
        }
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient && IS_RECORDING.get()) return TypedActionResult.fail(user.getStackInHand(hand));
        user.setCurrentHand(hand);
        return TypedActionResult.consume(user.getStackInHand(hand));
    }

    @Override
    public UseAction getUseAction(ItemStack stack) { return UseAction.BRUSH; }

    @Override
    public int getMaxUseTime(ItemStack stack) { return 72000; }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!world.isClient && user instanceof PlayerEntity player && !IS_RECORDING.get()) {
            int usedTicks = this.getMaxUseTime(stack) - remainingUseTicks;
            float progress = MathHelper.clamp(usedTicks / 200.0F, 0.0F, 1.0F);
            int currentInterval = (int) MathHelper.lerp(progress, 20.0F, 2.0F);

            if (usedTicks % currentInterval == 0) {
                this.performDynamicRestore(world, player, progress);
            }
        }
    }

    private void performDynamicRestore(World world, PlayerEntity player, float progress) {
        if (cachedData.isEmpty()) loadCacheFromFile();
        if (cachedData.isEmpty()) return;

        ItemStack stack = player.getMainHandStack();
        // 确保扣除的是法杖本身（防止玩家在蓄力过程中切换手持物）
        if (!(stack.getItem() instanceof RandomStaffItem)) {
            stack = player.getOffHandStack();
        }

        if (stack.getItem() instanceof RandomStaffItem) {
            // 每次执行回溯扣除 1 点耐久
            // 参数说明：扣除数量，随机源，执行扣除的玩家（用于触发损毁效果）
            stack.damage(1, player, (p) -> p.sendToolBreakStatus(player.getActiveHand()));

            // 如果耐久用完了，停止后续逻辑（damage方法会自动销毁物品，这里做个保险）
            if (stack.getDamage() >= stack.getMaxDamage()) {
                player.sendMessage(Text.of("§c[警告] 法杖能量已耗尽！"), true);
                return;
            }
        }

        int currentRadius = (int) MathHelper.lerp(progress, 2.0F, 10.0F);
        List<BlockPos> keys = new ArrayList<>(cachedData.keySet());
        BlockPos origin = keys.get(world.random.nextInt(keys.size()));

        for (int x = -currentRadius; x <= currentRadius; x++) {
            for (int y = -currentRadius; y <= currentRadius; y++) {
                for (int z = -currentRadius; z <= currentRadius; z++) {
                    BlockPos target = origin.add(x, y, z);
                    BlockState saved = cachedData.get(target);
                    if (world.isInBuildLimit(target)) {
                        BlockState toPlace = (saved != null) ? saved : Blocks.AIR.getDefaultState();
                        if (world.getBlockState(target) != toPlace) {
                            world.setBlockState(target, toPlace);
                        }
                    }
                }
            }
        }
        player.sendMessage(Text.of("§d回溯场规模: §f" + ((currentRadius * 2) + 1)), true);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSoundEvents.HIYOLI_WAND_USE, SoundCategory.PLAYERS, 4.0F, 1F);
    }

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