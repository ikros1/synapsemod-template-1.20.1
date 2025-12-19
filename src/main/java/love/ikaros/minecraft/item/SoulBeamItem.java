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
 * 终极灵魂剑气 - 倾斜扇形斩
 * 形状：50格半径的4/1圆（扇形），厚度2格，整体沿视线轴旋转45度
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
            // 2秒冷却
            user.getItemCooldownManager().set(this, 40);
        }

        user.swingHand(hand);
        return TypedActionResult.success(stack);
    }

    private void executeSoulBeam(World world, PlayerEntity user) {
        // --- 1. 基础向量参数 ---
        Vec3d startPos = user.getEyePos();
        Vec3d lookDir = user.getRotationVec(1.0F).normalize(); // 中轴向量 (Forward)

        double radius = 50.0;     // 扇形半径
        double angleLimit = 22.5; // 45度角的一半（中轴向两侧各偏22.5度）
        double halfThickness = 1.0; // 总厚度2格，所以半厚度为1

        // --- 2. 构建倾斜坐标系 ---
        // 找到一个垂直于视线的右向量
        Vec3d verticalAxis = new Vec3d(0, 1, 0);
        if (Math.abs(lookDir.y) > 0.9) {
            verticalAxis = new Vec3d(1, 0, 0); // 防止直视天空时的奇异点
        }
        Vec3d rightVec = lookDir.crossProduct(verticalAxis).normalize();
        Vec3d upVec = rightVec.crossProduct(lookDir).normalize();

        // 将坐标轴绕 lookDir 旋转 45 度 (弧度制)
        double rollRad = Math.toRadians(45);
        // 计算倾斜平面的法向量 (Normal Vector)，用于判断“厚度”
        Vec3d planeNormal = upVec.multiply(Math.cos(rollRad)).add(rightVec.multiply(Math.sin(rollRad))).normalize();
        // 计算倾斜平面的横向展开向量 (用于计算扇形展开)
        Vec3d planeHorizontal = rightVec.multiply(Math.cos(rollRad)).subtract(upVec.multiply(Math.sin(rollRad))).normalize();

        // --- 3. 实体检测 ---
        // 先用 AABB 粗略筛选 50 格内的实体提高性能
        Box scanBox = new Box(startPos, startPos).expand(radius);
        List<Entity> targets = world.getOtherEntities(user, scanBox);

        for (Entity entity : targets) {
            if (entity instanceof LivingEntity target && target.isAlive()) {
                // 实体中心点相对于玩家位置
                Vec3d relativePos = target.getBoundingBox().getCenter().subtract(startPos);
                double distance = relativePos.length();

                if (distance <= radius && distance > 0) {
                    // A. 角度判定 (利用点积计算与视线中轴的夹角)
                    double dot = relativePos.normalize().dotProduct(lookDir);
                    double angle = Math.toDegrees(Math.acos(MathHelper.clamp(dot, -1, 1)));

                    if (angle <= angleLimit) {
                        // B. 厚度判定 (实体在平面法线方向上的投影距离)
                        double distanceToPlane = Math.abs(relativePos.dotProduct(planeNormal));

                        if (distanceToPlane <= halfThickness) {
                            // 命中逻辑
                            target.damage(world.getDamageSources().playerAttack(user), 50.0F);
                            target.takeKnockback(5.0, -lookDir.x, -lookDir.z);
                        }
                    }
                }
            }
        }

        // --- 4. 视觉效果与方块破坏 ---
        if (world instanceof ServerWorld serverWorld) {
            // 步进渲染：r 代表半径距离，a 代表扇形张开角度
            for (double r = 2; r <= radius; r += 2.5) {
                // 在每一个半径圆弧上生成粒子
                for (double a = -angleLimit; a <= angleLimit; a += 4.0) {
                    double radA = Math.toRadians(a);

                    // 计算在该倾斜平面上展开的粒子位置
                    // 组合公式：中轴方向 + 倾斜平面的横向方向
                    Vec3d particleDir = lookDir.multiply(Math.cos(radA))
                            .add(planeHorizontal.multiply(Math.sin(radA)));

                    Vec3d pos = startPos.add(particleDir.multiply(r));

                    // 召唤灵魂火粒子
                    serverWorld.spawnParticles(
                            ParticleTypes.SOUL_FIRE_FLAME,
                            pos.x, pos.y, pos.z,
                            1, 0.0, 0.0, 0.0, 0.0
                    );

                    // 破坏方块 (为了性能，每 5 格半径才执行一次方块扫描)
                    if (r % 5 == 0) {
                        BlockPos bPos = BlockPos.ofFloored(pos);
                        // 扫描厚度范围内的方块
                        for (int h = -1; h <= 1; h++) {
                            BlockPos targetBlock = bPos.add(
                                    (int)(planeNormal.x * h),
                                    (int)(planeNormal.y * h),
                                    (int)(planeNormal.z * h)
                            );
                            BlockState state = world.getBlockState(targetBlock);
                            if (!state.isAir() && state.getHardness(world, targetBlock) >= 0) {
                                world.breakBlock(targetBlock, true, user);
                            }
                        }
                    }
                }
            }
        }
    }
}