package love.ikaros.minecraft.item;

import love.ikaros.minecraft.sound.ModSoundEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class SphereChaosStaffItem extends Item {

    private static final String KEY_CENTER_POS = "CenterPos";
    private static final Random RANDOM = new Random();
    private static final int INTERVAL = 4;
    // 1. 在类中定义你的音效循环周期（例如你的音效是 3 秒长，这里就写 60）
// 请根据你实际音效文件的长度修改这个数值
    private static final int SOUND_LOOP_TICKS = 160;

    // 预先缓存所有可用的方块，避免每 tick 扫描注册表导致卡顿
    private static List<Block> CACHED_BLOCKS = null;

    public SphereChaosStaffItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity miner) {
        if (!world.isClient) {
            ItemStack stack = miner.getStackInHand(Hand.MAIN_HAND);
            if (stack.getItem() instanceof SphereChaosStaffItem) {
                NbtCompound nbt = stack.getOrCreateNbt();
                nbt.put(KEY_CENTER_POS, NbtHelper.fromBlockPos(pos));
                miner.sendMessage(Text.of("§a[系统] 已标定中心点: " + pos.toShortString()), true);
                world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 1.0f, 2.0f);
            }
        }
        return false;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (stack.getOrCreateNbt().contains(KEY_CENTER_POS)) {
            user.setCurrentHand(hand);
            return TypedActionResult.consume(stack);
        } else {
            if (!world.isClient) user.sendMessage(Text.of("§c[错误] 请先左键点击方块标定中心点！"), true);
            return TypedActionResult.fail(stack);
        }
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BRUSH;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (world.isClient || !(user instanceof PlayerEntity player)) return;

        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(KEY_CENTER_POS)) return;

        BlockPos center = NbtHelper.toBlockPos(nbt.getCompound(KEY_CENTER_POS));
        int usedTicks = this.getMaxUseTime(stack) - remainingUseTicks;

        // --- 修复音效重叠逻辑 ---
        // 只有在 usedTicks 为 0（刚开始）或者 达到音效长度周期时才播放
        // 这样音效会首尾相连，而不会每 20 tick 就叠加一个新的层级
        if (usedTicks % SOUND_LOOP_TICKS == 0) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 1.5f, 1.0f);
        }

        // --- 原有的逻辑 ---
        int cycle = usedTicks / INTERVAL;
        int currentRadius = 2 + cycle * 2;
        int innerRadius = currentRadius - 2;

        if (usedTicks > 0 && usedTicks % INTERVAL == 0) {
            clearSphereShell(world, center, currentRadius - 2, Math.max(0, innerRadius - 2));
            // 这里可以保留一个清脆的短音效作为层级切换提示
            world.playSound(null, user.getBlockPos(), ModSoundEvents.NYMPH_WAND_USE, SoundCategory.PLAYERS, 10f, 1f);
        } else {
            applySphereShellEffect(world, center, currentRadius, innerRadius);
        }

        if (usedTicks % 20 == 0) {
            // 移除了这里的重复音效播放
            player.sendMessage(Text.of("§d半径: §f" + currentRadius + " §8| §e周期倒计时: §f" + (INTERVAL - (usedTicks % INTERVAL))), true);
        }
    }

    private void applySphereShellEffect(World world, BlockPos center, int outerRadius, int innerRadius) {
        iterateShell(center, outerRadius, innerRadius, (pos) -> {
            if (world.isInBuildLimit(pos)) {
                BlockState currentState = world.getBlockState(pos);
                // 仅替换非空气方块
                if (!currentState.isAir()) {
                    world.setBlockState(pos, getRandomBlockState(), Block.NOTIFY_LISTENERS);
                }
            }
        });
    }

    private void clearSphereShell(World world, BlockPos center, int outerRadius, int innerRadius) {
        // 1. 先进行原有的方块清除
        iterateShell(center, outerRadius, innerRadius, (pos) -> {
            if (world.isInBuildLimit(pos)) {
                // 移除 BlockEntity 防止容器喷洒物品
                world.removeBlockEntity(pos);
                // 设为空气，并使用 Flag 18 (2|16)
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
            }
        });

        // 2. 清理掉落物实体
        // 创建一个覆盖当前球壳范围的包围盒 (Box)
        // 稍微 expand(1.0) 是为了捕捉到因方块更新弹飞出的掉落物
        Box box = new Box(center).expand(outerRadius + 1.0);

        // 获取该区域内所有的掉落物实体并标记为移除
        List<ItemEntity> items = world.getEntitiesByClass(ItemEntity.class, box, item -> true);
        for (ItemEntity item : items) {
            item.discard(); // 直接从世界中移除实体，不触发掉落逻辑
        }
    }

    private void iterateShell(BlockPos center, int outerRadius, int innerRadius, java.util.function.Consumer<BlockPos> action) {
        int sqOuter = outerRadius * outerRadius;
        int sqInner = innerRadius * innerRadius;

        for (int x = -outerRadius; x <= outerRadius; x++) {
            for (int y = -outerRadius; y <= outerRadius; y++) {
                for (int z = -outerRadius; z <= outerRadius; z++) {
                    int distanceSq = x * x + y * y + z * z;
                    if (distanceSq <= sqOuter && distanceSq > sqInner) {
                        action.accept(center.add(x, y, z));
                    }
                }
            }
        }
    }

    private BlockState getRandomBlockState() {
        if (CACHED_BLOCKS == null) {
            CACHED_BLOCKS = Registries.BLOCK.stream()
                    .filter(b -> {
                        BlockState state = b.getDefaultState();
                        // 1. 排除空气
                        if (state.isAir()) return false;
                        // 2. 必须是完整、不透明的方块（排除掉火把、红石线等）
                        if (!state.isOpaque()) return false;
                        // 3. 核心修复：排除所有带有 BlockEntity 的方块（如箱子、工作站、熔炉）
                        // 这能有效解决 POI 报错和潜在的 BlockEntity 堆积导致的卡顿
                        if (b instanceof net.minecraft.block.BlockEntityProvider) return false;

                        return true;
                    })
                    .collect(Collectors.toList());

            // 如果过滤太狠导致没方块了，保底给个石头
            if (CACHED_BLOCKS.isEmpty()) {
                CACHED_BLOCKS.add(Blocks.STONE);
            }
        }
        return CACHED_BLOCKS.get(RANDOM.nextInt(CACHED_BLOCKS.size())).getDefaultState();
    }
}