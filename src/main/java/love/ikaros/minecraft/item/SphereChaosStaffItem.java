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
    private static final int INTERVAL = 4; // 变换频率

    // 音效长度：8秒 * 20 ticks = 160
    private static final int SOUND_LOOP_TICKS = 160;

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
                // 左键标定音效
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
        return UseAction.BOW;
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

        // 计算已使用时长（从0开始增加）
        int usedTicks = this.getMaxUseTime(stack) - remainingUseTicks;

        // --- 核心修复：长音效循环逻辑 ---
        // 每 160 ticks (8秒) 触发一次。
        // usedTicks 从 0 开始，所以第一次右键会立即触发播放。
        if (usedTicks % SOUND_LOOP_TICKS == 0) {
            // 这里建议音量设为 1.0f。10f 会导致全地图都能听到，且在耳边非常吵。
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    ModSoundEvents.NYMPH_WAND_USE, SoundCategory.PLAYERS, 10.0f, 1.0f);
        }

        // --- 逻辑计算 ---
        int cycle = usedTicks / INTERVAL;
        int currentRadius = 2 + cycle * 2;
        int innerRadius = currentRadius - 2;

        // 每隔 INTERVAL (4 tick) 进行一次清理和提示
        if (usedTicks > 0 && usedTicks % INTERVAL == 0) {
            clearSphereShell(world, center, currentRadius - 2, Math.max(0, innerRadius - 2));

            // 如果你觉得短促的 ModSoundEvents 也吵，可以调低其音量 (0.3f) 或者移除。
            // world.playSound(null, user.getBlockPos(), ModSoundEvents.NYMPH_WAND_USE, SoundCategory.PLAYERS, 0.5f, 1.2f);
        } else {
            applySphereShellEffect(world, center, currentRadius, innerRadius);
        }

        // 每秒发送一次状态栏信息
        if (usedTicks % 20 == 0) {
            player.sendMessage(Text.of("§d当前半径: §f" + currentRadius), true);
        }
    }

    private void applySphereShellEffect(World world, BlockPos center, int outerRadius, int innerRadius) {
        iterateShell(center, outerRadius, innerRadius, (pos) -> {
            if (world.isInBuildLimit(pos)) {
                BlockState currentState = world.getBlockState(pos);
                if (!currentState.isAir()) {
                    world.setBlockState(pos, getRandomBlockState(), Block.NOTIFY_LISTENERS);
                }
            }
        });
    }

    private void clearSphereShell(World world, BlockPos center, int outerRadius, int innerRadius) {
        iterateShell(center, outerRadius, innerRadius, (pos) -> {
            if (world.isInBuildLimit(pos)) {
                world.removeBlockEntity(pos);
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
            }
        });

        Box box = new Box(center).expand(outerRadius + 1.0);
        List<ItemEntity> items = world.getEntitiesByClass(ItemEntity.class, box, item -> true);
        for (ItemEntity item : items) {
            item.discard();
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
                        if (state.isAir()) return false;
                        if (!state.isOpaque()) return false;
                        if (b instanceof net.minecraft.block.BlockEntityProvider) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
            if (CACHED_BLOCKS.isEmpty()) CACHED_BLOCKS.add(Blocks.STONE);
        }
        return CACHED_BLOCKS.get(RANDOM.nextInt(CACHED_BLOCKS.size())).getDefaultState();
    }
}