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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 魔改剑气类
 * 功能：右键发出50格灵魂火剑气，破坏方块，造成50点伤害并击退。
 */
public class SoulBeamItem extends Item {

    public SoulBeamItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        // 仅在服务端执行逻辑，避免客户端与服务端不同步（如方块消失了但又卡回来）
        if (!world.isClient) {
            executeSoulBeam(world, user);

            // 设置 2 秒冷却，防止连续使用导致服务器计算压力过大
            user.getItemCooldownManager().set(this, 40);
        }

        // 播放玩家挥手动画
        user.swingHand(hand);
        return TypedActionResult.success(stack);
    }

    private void executeSoulBeam(World world, PlayerEntity user) {
        Vec3d lookDir = user.getRotationVec(1.0F); // 获取玩家视野方向向量
        Vec3d startPos = user.getEyePos();         // 获取起始位置（眼睛高度）

        // 记录在这一发剑气中已经被伤害过的生物，防止 0.5 格步进导致重复伤害
        List<Entity> hitEntities = new ArrayList<>();

        // 剑气蔓延 50 格
        for (double d = 1; d <= 50; d += 0.5) {
            Vec3d currentPos = startPos.add(lookDir.multiply(d));
            BlockPos blockPos = BlockPos.ofFloored(currentPos);

            // --- 1. 粒子效果 (灵魂火) ---
            if (world instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(
                        ParticleTypes.SOUL_FIRE_FLAME,
                        currentPos.x, currentPos.y, currentPos.z,
                        5,          // 数量
                        0.2, 0.2, 0.2, // 在 0.2 范围内发散，让剑气看起来更粗
                        0.05        // 粒子速度
                );
            }

            // --- 2. 方块破坏逻辑 ---
            BlockState state = world.getBlockState(blockPos);
            // 只有非空且硬度不为 -1 (基岩/传送门) 的方块才会被破坏
            if (!state.isAir() && state.getHardness(world, blockPos) >= 0) {
                world.breakBlock(blockPos, true, user);
            }

            // --- 3. 伤害与击退逻辑 ---
            // 修复 Box 构造：基于当前点坐标创建一个 3x3x3 的探测区域
            Box area = new Box(
                    currentPos.x - 1.5, currentPos.y - 1.5, currentPos.z - 1.5,
                    currentPos.x + 1.5, currentPos.y + 1.5, currentPos.z + 1.5
            );

            List<Entity> targets = world.getOtherEntities(user, area);
            for (Entity entity : targets) {
                if (entity instanceof LivingEntity target && !hitEntities.contains(target)) {
                    // 确保不会伤到自己
                    if (target == user) continue;

                    // 造成 50 点伤害 (25 颗心)
                    target.damage(world.getDamageSources().playerAttack(user), 50.0F);

                    // 强力击退：沿剑气前进方向推开，力度为 2.0
                    target.takeKnockback(8.0, -lookDir.x, -lookDir.z);

                    // 标记该生物，防止在 50 格路径中被后续循环重复伤害
                    hitEntities.add(target);
                }
            }
        }
    }
}