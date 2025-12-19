package love.ikaros.minecraft.item;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * 强化版灵魂剑气
 * 范围：一个宽度为 6，高度为 3，长度为 50 的倾斜长方体
 */
public class SoulBeamItem extends Item {

    public SoulBeamItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient) {
            executeSoulBeam(world, user);
            user.getItemCooldownManager().set(this, 40);
        }
        user.swingHand(hand);
        return TypedActionResult.success(stack);
    }

    private void executeSoulBeam(World world, PlayerEntity user) {
        Vec3d startPos = user.getEyePos();
        Vec3d lookDir = user.getRotationVec(1.0F);

        // 1. 定义剑气的几何参数
        double length = 50.0;
        double width = 6.0;   // 左右各 3 格
        double height = 3.0;  // 上下各 1.5 格

        // 2. 计算一个包围这个“倾斜长方形”的最大 AABB 盒子，用于初步筛选实体
        // 这样可以避免直接遍历全图实体，性能更高
        Box scanArea = new Box(startPos, startPos.add(lookDir.multiply(length))).expand(width);
        List<Entity> entities = world.getOtherEntities(user, scanArea);

        // 3. 处理伤害逻辑
        for (Entity entity : entities) {
            if (entity instanceof LivingEntity target && target.isAlive()) {
                // 计算实体相对于玩家的位置向量
                Vec3d relativePos = target.getPos().subtract(startPos);

                // 使用点积判断实体是否在长方形内
                // dotProduct = 实体在玩家正前方的投影长度
                double dotProduct = relativePos.dotProduct(lookDir);

                if (dotProduct > 0 && dotProduct < length) {
                    // 计算实体距离剑气中心线的垂直距离
                    Vec3d projection = lookDir.multiply(dotProduct);
                    Vec3d perpendicular = relativePos.subtract(projection);

                    // 判断宽度和高度是否在范围内 (水平距离和垂直距离)
                    if (Math.abs(perpendicular.x) < width / 2 && Math.abs(perpendicular.y) < height && Math.abs(perpendicular.z) < width / 2) {
                        target.damage(world.getDamageSources().playerAttack(user), 50.0F);
                        target.takeKnockback(3.0, -lookDir.x, -lookDir.z);
                    }
                }
            }
        }

        // 4. 处理方块破坏和粒子（为了视觉效果，这里依然需要简单的步进循环）
        if (world instanceof ServerWorld serverWorld) {
            // 我们每隔 2 格生成一个“面”的粒子，这样看起来更像一个巨大的冲击波
            for (double d = 2; d <= length; d += 2.0) {
                Vec3d center = startPos.add(lookDir.multiply(d));

                // 在每一段横截面上随机生成粒子
                serverWorld.spawnParticles(
                        ParticleTypes.SOUL_FIRE_FLAME,
                        center.x, center.y, center.z,
                        20,         // 每一段产生的粒子数
                        width / 2, height / 2, width / 2, // 粒子的散布范围，对应剑气的宽度高度
                        0.1
                );

                // 破坏中心线附近的方块
                BlockPos bPos = BlockPos.ofFloored(center);
                for (BlockPos targetPos : BlockPos.iterate(bPos.add(-2, -1, -2), bPos.add(2, 1, 2))) {
                    BlockState state = world.getBlockState(targetPos);
                    if (!state.isAir() && state.getHardness(world, targetPos) >= 0) {
                        world.breakBlock(targetPos, true, user);
                    }
                }
            }
        }
    }
}