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
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class SphereChaosStaffItem extends Item {

    private static final Random RANDOM = new Random();
    private static final int INTERVAL = 4; // 变换频率
    private static final int SOUND_LOOP_TICKS = 160; // 音效循环周期

    private static List<Block> CACHED_BLOCKS = null;

    public SphereChaosStaffItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        // 不再检查 NBT，直接允许使用
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
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

        // --- 核心改动：实时自身为中心 ---
        BlockPos center = user.getBlockPos();
        int usedTicks = this.getMaxUseTime(stack) - remainingUseTicks;

        // 循环播放背景音效
        if (usedTicks % SOUND_LOOP_TICKS == 0) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    ModSoundEvents.NYMPH_WAND_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        }

        // --- 半径逻辑计算 ---
        int cycle = usedTicks / INTERVAL;
        int currentRadius = 2 + cycle * 2;
        int innerRadius = Math.max(0, currentRadius - 2);

        if (usedTicks > 0 && usedTicks % INTERVAL == 0) {
            // 1. 清理上一层的掉落物和旧方块
            clearSphereShell(world, center, innerRadius, Math.max(0, innerRadius - 2));

            // 2. 在当前变换的外边缘添加灵魂火粒子
            spawnSoulFireParticles((ServerWorld) world, center, currentRadius);

            // 提示
            if (usedTicks % 20 == 0) {
                player.sendMessage(Text.of("§b混沌扩张半径: §f" + currentRadius), true);
            }
        } else {
            // 持续进行方块变换
            applySphereShellEffect(world, center, currentRadius, innerRadius);
        }
    }

    /**
     * 在球体边缘生成灵魂火粒子
     */
    private void spawnSoulFireParticles(ServerWorld world, BlockPos center, int radius) {
        // 在球面上随机取点生成粒子，避免每个方块都生成导致卡顿
        int particleCount = radius * 100; // 粒子数量随半径增加
        for (int i = 0; i < particleCount; i++) {
            double u = RANDOM.nextDouble();
            double v = RANDOM.nextDouble();
            double theta = 2 * Math.PI * u;
            double phi = Math.acos(2 * v - 1);

            double x = center.getX() + 0.5 + (radius * Math.sin(phi) * Math.cos(theta));
            double y = center.getY() + 0.5 + (radius * Math.sin(phi) * Math.sin(theta));
            double z = center.getZ() + 0.5 + (radius * Math.cos(phi));

            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 1, 0.1, 0.1, 0.1, 0.05);
        }
    }

    private void applySphereShellEffect(World world, BlockPos center, int outerRadius, int innerRadius) {
        iterateShell(center, outerRadius, innerRadius, (pos) -> {
            if (world.isInBuildLimit(pos)) {
                BlockState currentState = world.getBlockState(pos);
                // 仅变换非空气且非动态更新的方块
                if (!currentState.isAir() && RANDOM.nextInt(3) == 0) {
                    world.setBlockState(pos, getRandomBlockState(), Block.NOTIFY_LISTENERS);
                }
            }
        });
    }

    private void clearSphereShell(World world, BlockPos center, int outerRadius, int innerRadius) {
        iterateShell(center, outerRadius, innerRadius, (pos) -> {
            if (world.isInBuildLimit(pos)) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
            }
        });

        // 清理掉落物
        Box box = new Box(center).expand(outerRadius + 1.0);
        List<ItemEntity> items = world.getEntitiesByClass(ItemEntity.class, box, item -> true);
        items.forEach(ItemEntity::discard);
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
                        return !state.isAir() && state.isOpaque() && !(b instanceof net.minecraft.block.BlockEntityProvider);
                    })
                    .collect(Collectors.toList());
            if (CACHED_BLOCKS.isEmpty()) CACHED_BLOCKS.add(Blocks.STONE);
        }
        return CACHED_BLOCKS.get(RANDOM.nextInt(CACHED_BLOCKS.size())).getDefaultState();
    }
}