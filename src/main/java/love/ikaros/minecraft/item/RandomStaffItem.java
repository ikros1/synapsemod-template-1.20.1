package love.ikaros.minecraft.item;

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

        final int radius =70;
        final int totalLayers = (radius * 2) + 1;
        AtomicInteger completedLayers = new AtomicInteger(0);
        List<NbtCompound> collectedEntries = Collections.synchronizedList(new ArrayList<>());

        player.sendMessage(Text.of("§b[系统] 开始时空记录..."), false);

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

            // 圣十字架参数
            double totalHeight = 15.0;
            double thickness = 0.3; // 半径，即宽度为0.6
            double armY = 10.0;     // 横梁所在高度
            double armLength = 4.0; // 横梁单侧长度

            // 1. 绘制竖向立柱的 4 条棱 (垂直线)
            drawVerticalLine(world, cx - thickness, cz - thickness, baseY, totalHeight);
            drawVerticalLine(world, cx + thickness, cz - thickness, baseY, totalHeight);
            drawVerticalLine(world, cx - thickness, cz + thickness, baseY, totalHeight);
            drawVerticalLine(world, cx + thickness, cz + thickness, baseY, totalHeight);

            // 2. 绘制竖向立柱的顶部和底部方框 (水平线)
            drawRect(world, cx, cz, baseY, thickness, thickness);
            drawRect(world, cx, cz, baseY + totalHeight, thickness, thickness);

            // 3. 绘制横梁框架 (沿 X 轴延伸)
            // 横梁的 4 条长棱
            drawHorizontalLineX(world, cx - armLength, cx + armLength, armY - thickness, cz - thickness);
            drawHorizontalLineX(world, cx - armLength, cx + armLength, armY + thickness, cz - thickness);
            drawHorizontalLineX(world, cx - armLength, cx + armLength, armY - thickness, cz + thickness);
            drawHorizontalLineX(world, cx - armLength, cx + armLength, armY + thickness, cz + thickness);

            // 横梁两端的方框封口
            drawRectSide(world, cx - armLength, armY, cz, thickness);
            drawRectSide(world, cx + armLength, armY, cz, thickness);

            // 4. 交叉点发光增强 (核心位置)
            if (world.random.nextFloat() > 0.7f) {
                world.addParticle(ParticleTypes.GLOW, cx, baseY + armY, cz, 0, 0.1, 0);
            }
        }
    }

    // --- 粒子绘制工具方法 ---

    // 绘制垂直线
    private void drawVerticalLine(World world, double x, double z, double startY, double height) {
        for (double y = 0; y <= height; y += 0.4) {
            world.addParticle(ParticleTypes.END_ROD, x, startY + y, z, 0, 0, 0);
        }
    }

    // 绘制 X 轴水平线
    private void drawHorizontalLineX(World world, double startX, double endX, double y, double z) {
        for (double x = startX; x <= endX; x += 0.4) {
            world.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0, 0);
        }
    }

    // 绘制水平面的矩形框 (用于立柱顶底)
    private void drawRect(World world, double cx, double cz, double y, double tx, double tz) {
        for (double i = -tx; i <= tx; i += 0.2) {
            world.addParticle(ParticleTypes.END_ROD, cx + i, y, cz - tz, 0, 0, 0);
            world.addParticle(ParticleTypes.END_ROD, cx + i, y, cz + tz, 0, 0, 0);
            world.addParticle(ParticleTypes.END_ROD, cx - tx, y, cz + i, 0, 0, 0);
            world.addParticle(ParticleTypes.END_ROD, cx + tx, y, cz + i, 0, 0, 0);
        }
    }

    // 绘制垂直面的矩形框 (用于横梁两端封口)
    private void drawRectSide(World world, double x, double cy, double cz, double t) {
        for (double i = -t; i <= t; i += 0.2) {
            world.addParticle(ParticleTypes.END_ROD, x, cy + i, cz - t, 0, 0, 0);
            world.addParticle(ParticleTypes.END_ROD, x, cy + i, cz + t, 0, 0, 0);
            world.addParticle(ParticleTypes.END_ROD, x, cy - t, cz + i, 0, 0, 0);
            world.addParticle(ParticleTypes.END_ROD, x, cy + t, cz + i, 0, 0, 0);
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
            // 进度 0.0 ~ 1.0
            float progress = MathHelper.clamp(usedTicks / 200.0F, 0.0F, 1.0F);

            // 蓄力越久，触发频率越快
            int currentInterval = (int) MathHelper.lerp(progress, 20.0F, 2.0F);

            if (usedTicks % currentInterval == 0) {
                this.performDynamicRestore(world, player, progress);
            }
        }
    }

    /**
     * 动态还原：边长随蓄力时间从 5 增加到 20
     */
    private void performDynamicRestore(World world, PlayerEntity player, float progress) {
        if (cachedData.isEmpty()) loadCacheFromFile();
        if (cachedData.isEmpty()) return;

        // 计算当前半径：从 2 (边长5) 到 10 (边长20)
        int currentRadius = (int) MathHelper.lerp(progress, 2.0F, 10.0F);

        List<BlockPos> keys = new ArrayList<>(cachedData.keySet());
        BlockPos origin = keys.get(world.random.nextInt(keys.size()));

        // 执行立方体还原
        for (int x = -currentRadius; x <= currentRadius; x++) {
            for (int y = -currentRadius; y <= currentRadius; y++) {
                for (int z = -currentRadius; z <= currentRadius; z++) {
                    BlockPos target = origin.add(x, y, z);
                    BlockState saved = cachedData.get(target);

                    if (world.isInBuildLimit(target)) {
                        // 如果记录中有则还原，没有则设为空气（实现完全回溯）
                        BlockState toPlace = (saved != null) ? saved : Blocks.AIR.getDefaultState();

                        // 性能优化：如果当前方块已经是目标方块，则跳过，减少不必要的包发送
                        if (world.getBlockState(target) != toPlace) {
                            world.setBlockState(target, toPlace);
                        }
                    }
                }
            }
        }

        // 提示当前回溯规模
        int sideLength = (currentRadius * 2) + 1;
        player.sendMessage(Text.of("§d回溯场规模: §f" + sideLength + "x" + sideLength + "x" + sideLength), true);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 4.0F, 0.5F + (progress * 1.5F));
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