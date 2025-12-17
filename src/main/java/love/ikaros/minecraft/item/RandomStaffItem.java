package love.ikaros.minecraft.item;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.UseAction; // 注意：在某些版本中可能在 net.minecraft.item.UseAction
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

public class RandomStaffItem extends Item {

    public RandomStaffItem(Settings settings) {
        super(settings);
    }

    // 右键按住时的动作效果
    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BRUSH;
    }

    // 设置最大使用时间，72000刻约等于1小时，确保可以一直按着
    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        // 必须调用此方法，否则 usageTick 不会触发
        user.setCurrentHand(hand);
        return TypedActionResult.consume(user.getStackInHand(hand));
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!world.isClient && user instanceof PlayerEntity player) {
            // 计算已使用时长（1秒 = 20 ticks）
            int usedTicks = this.getMaxUseTime(stack) - remainingUseTicks;

            // --- 加速逻辑 ---
            // 目标：5秒(100ticks)内从 2秒(40ticks)间隔 衰减到 0.2秒(4ticks)间隔
            float progress = MathHelper.clamp(usedTicks / 200.0F, 0.0F, 1.0F);
            int currentInterval = (int) MathHelper.lerp(progress, 20.0F, 1.0F);

            // 检查是否到达触发间隔
            if (usedTicks % currentInterval == 0) {
                this.performMagic(world, player, progress);
            }
        }
    }

    private void performMagic(World world, PlayerEntity player, float progress) {
        Random random = world.getRandom();

        // 1. 在 200 格范围内随机选一个起点
        int range = 100;
        int rx = random.nextInt(range) - (range / 2);
        int ry = random.nextInt(40) - 20; // 纵向范围控制在正负20
        int rz = random.nextInt(range) - (range / 2);

        BlockPos startPos = player.getBlockPos().add(rx, ry, rz);

        // 2. 随机选取一种方块
        Block[] potentialBlocks = {
                Blocks.DIAMOND_BLOCK,
                Blocks.GOLD_BLOCK,
                Blocks.EMERALD_BLOCK,
                Blocks.LAPIS_BLOCK,
                Blocks.GLOWSTONE,
                Blocks.GLASS,
                Blocks.AMETHYST_BLOCK
        };
        BlockState randomState = potentialBlocks[random.nextInt(potentialBlocks.length)].getDefaultState();

        // 3. 沿三轴正方向延伸 5 格，形成 5x5x5 立方体
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    BlockPos targetPos = startPos.add(x, y, z);
                    if (world.isInBuildLimit(targetPos)) {
                        world.setBlockState(targetPos, randomState);
                    }
                }
            }
        }

        // 4. 音效反馈：音调随进度（progress）提高而变高，产生蓄力感
        float pitch = 0.5F + (progress * 1.5F); // 音调从 0.5 升到 2.0
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0F, pitch);
    }
}