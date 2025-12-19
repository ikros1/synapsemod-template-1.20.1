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

        double radius = 100.0;
        double angleLimit = 22.5;
        double halfThickness = 3; // 稍微增加判定厚度，确保方块破坏更彻底

        // --- 构建倾斜坐标系 ---
        Vec3d verticalAxis = new Vec3d(0, 1, 0);
        if (Math.abs(lookDir.y) > 0.9) verticalAxis = new Vec3d(1, 0, 0);

        Vec3d rightVec = lookDir.crossProduct(verticalAxis).normalize();
        Vec3d upVec = rightVec.crossProduct(lookDir).normalize();

        double rollRad = Math.toRadians(45);
        // 倾斜平面的法向量（决定厚度方向）
        Vec3d planeNormal = upVec.multiply(Math.cos(rollRad)).add(rightVec.multiply(Math.sin(rollRad))).normalize();
        // 倾斜平面的横向向量（决定扇形展开方向）
        Vec3d planeHorizontal = rightVec.multiply(Math.cos(rollRad)).subtract(upVec.multiply(Math.sin(rollRad))).normalize();

        // --- 1. 实体伤害逻辑 ---
        Box scanBox = new Box(startPos, startPos).expand(radius);
        List<Entity> targets = world.getOtherEntities(user, scanBox);

        for (Entity entity : targets) {
            if (entity instanceof LivingEntity target && target.isAlive()) {
                Vec3d relativePos = target.getBoundingBox().getCenter().subtract(startPos);
                double distance = relativePos.length();

                if (distance <= radius && distance > 0) {
                    double dot = relativePos.normalize().dotProduct(lookDir);
                    double angle = Math.toDegrees(Math.acos(MathHelper.clamp(dot, -1, 1)));

                    if (angle <= angleLimit) {
                        double distanceToPlane = Math.abs(relativePos.dotProduct(planeNormal));
                        if (distanceToPlane <= halfThickness) {
                            target.damage(world.getDamageSources().playerAttack(user), 50.0F);
                            target.takeKnockback(5.0, -lookDir.x, -lookDir.z);
                        }
                    }
                }
            }
        }

        // --- 2. 视觉效果与方块破坏逻辑 ---
        if (world instanceof ServerWorld serverWorld) {
            // 降低步进间距，增加采样密度
            for (double r = 1.5; r <= radius; r += 0.1) {
                for (double a = -angleLimit; a <= angleLimit; a += 5.0) {
                    double radA = Math.toRadians(a);

                    // 计算在该倾斜平面上展开的方向
                    Vec3d sectorDir = lookDir.multiply(Math.cos(radA))
                            .add(planeHorizontal.multiply(Math.sin(radA)));

                    Vec3d impactPoint = startPos.add(sectorDir.multiply(r));

                    // 绘制粒子
                    serverWorld.spawnParticles(
                            ParticleTypes.SOUL_FIRE_FLAME,
                            impactPoint.x, impactPoint.y, impactPoint.z,
                            1, 0.05, 0.05, 0.05, 0.0
                    );

                    // --- 强化版方块破坏 ---
                    // 沿着法线方向（厚度方向）上下探测
                    for (double h = -halfThickness; h <= halfThickness; h += 1.0) {
                        Vec3d finalPos = impactPoint.add(planeNormal.multiply(h));
                        BlockPos bPos = BlockPos.ofFloored(finalPos);

                        BlockState state = world.getBlockState(bPos);
                        // 检查硬度，确保不会破坏基岩
                        if (!state.isAir() && state.getHardness(world, bPos) >= 0) {
                            world.breakBlock(bPos, true, user);
                        }
                    }
                }
            }
        }
    }
}