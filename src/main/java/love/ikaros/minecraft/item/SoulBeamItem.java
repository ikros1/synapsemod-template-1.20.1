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

public class SoulBeamItem extends Item {

    public SoulBeamItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient) {
            executeSoulBeam(world, user);
            user.getItemCooldownManager().set(this, 5);
        }
        user.swingHand(hand);
        return TypedActionResult.success(stack);
    }

    private void executeSoulBeam(World world, PlayerEntity user) {
        Vec3d startPos = user.getEyePos();
        Vec3d lookDir = user.getRotationVec(1.0F).normalize();

        // 判定参数
        double radius = 100.0;
        double angleLimitRad = Math.toRadians(22.5); // 扇形半角
        double halfThickness = 3.0; // 影响面厚度的一半

        // --- 构建局部坐标系 (用于计算到倾斜平面的距离) ---
        Vec3d verticalAxis = Math.abs(lookDir.y) > 0.9 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d rightVec = lookDir.crossProduct(verticalAxis).normalize();
        Vec3d upVec = rightVec.crossProduct(lookDir).normalize();

        // 45度倾斜平面的法向量
        double rollRad = Math.toRadians(45);
        Vec3d planeNormal = upVec.multiply(Math.cos(rollRad))
                .add(rightVec.multiply(Math.sin(rollRad)))
                .normalize();

        // --- 1. 实体处理 ---
        // 使用标准的 AABB 预筛选提升性能
        Box scanBox = new Box(startPos, startPos).expand(radius);
        List<Entity> targets = world.getOtherEntities(user, scanBox);

        for (Entity entity : targets) {
            if (entity instanceof LivingEntity target && target.isAlive()) {
                Vec3d relativePos = target.getBoundingBox().getCenter().subtract(startPos);
                if (checkImpact(relativePos, lookDir, planeNormal, radius, angleLimitRad, halfThickness)) {
                    target.damage(world.getDamageSources().playerAttack(user), 50.0F);
                    target.takeKnockback(5.0, -lookDir.x, -lookDir.z);
                }
            }
        }

        // --- 2. 方块处理 (核心优化：遍历 AABB) ---
        if (world instanceof ServerWorld serverWorld) {
            // 获取方块遍历的边界
            BlockPos min = BlockPos.ofFloored(startPos.x - radius, startPos.y - radius, startPos.z - radius);
            BlockPos max = BlockPos.ofFloored(startPos.x + radius, startPos.y + radius, startPos.z + radius);

            for (BlockPos bPos : BlockPos.iterate(min, max)) {
                // 计算方块中心相对于起始点的向量
                Vec3d relativePos = Vec3d.ofCenter(bPos).subtract(startPos);

                if (checkImpact(relativePos, lookDir, planeNormal, radius, angleLimitRad, halfThickness)) {
                    BlockState state = world.getBlockState(bPos);

                    // 过滤掉不可破坏方块和空气
                    if (!state.isAir() && state.getHardness(world, bPos) >= 0) {
                        world.breakBlock(bPos, false, user);

                        // 仅在破坏方块的地方低概率生成粒子，减少视觉污染和性能消耗
                        if (world.random.nextFloat() < 0.1) {
                            serverWorld.spawnParticles(
                                    ParticleTypes.SOUL_FIRE_FLAME,
                                    bPos.getX() + 0.5, bPos.getY() + 0.5, bPos.getZ() + 0.5,
                                    1, 0.1, 0.1, 0.1, 0.0
                            );
                        }
                    }
                }
            }
        }
    }

    /**
     * 判定目标点是否在灵魂激流的影响范围内
     * @param relativePos 目标点相对于起始点的向量
     * @param lookDir 玩家朝向
     * @param planeNormal 倾斜平面的法线
     */
    private boolean checkImpact(Vec3d relativePos, Vec3d lookDir, Vec3d planeNormal,
                                double radius, double angleLimitRad, double halfThickness) {

        double distSq = relativePos.lengthSquared();

        // 1. 距离判定 (平方比较，快)
        if (distSq > radius * radius || distSq < 0.25) return false;

        double dist = Math.sqrt(distSq);
        Vec3d normRelative = relativePos.multiply(1.0 / dist);

        // 2. 角度判定 (点积计算夹角)
        double dot = normRelative.dotProduct(lookDir);
        // 使用 acos 获取实际夹角，需 clamp 保证输入范围
        if (Math.acos(MathHelper.clamp(dot, -1, 1)) > angleLimitRad) return false;

        // 3. 厚度判定 (点积计算在法线方向上的投影长度)
        double distanceToPlane = Math.abs(relativePos.dotProduct(planeNormal));
        return distanceToPlane <= halfThickness;
    }
}