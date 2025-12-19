package love.ikaros.minecraft.item;

import love.ikaros.minecraft.sound.ModSoundEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
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
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    ModSoundEvents.CHRYSAOR_SWORD, SoundCategory.PLAYERS, 10F, 1F);
            user.getItemCooldownManager().set(this, 5);
        }
        user.swingHand(hand);
        return TypedActionResult.success(stack);
    }

    private void executeSoulBeam(World world, PlayerEntity user) {
        Vec3d startPos = user.getEyePos();
        Vec3d lookDir = user.getRotationVec(1.0F).normalize();

        // 逻辑判定参数 (50格范围)
        double radius = 50.0;
        double angleLimitDeg = 22.5;
        double angleLimitRad = Math.toRadians(angleLimitDeg);
        double halfThickness = 3.0;

        // --- 构建坐标系 ---
        Vec3d verticalAxis = Math.abs(lookDir.y) > 0.9 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d rightVec = lookDir.crossProduct(verticalAxis).normalize();
        Vec3d upVec = rightVec.crossProduct(lookDir).normalize();

        double rollRad = Math.toRadians(45);
        // 法线：用于 BlockPos 的垂直投影判定
        Vec3d planeNormal = upVec.multiply(Math.cos(rollRad)).add(rightVec.multiply(Math.sin(rollRad))).normalize();
        // 平面横向向量：用于渲染唯一的粒子扇面
        Vec3d planeHorizontal = rightVec.multiply(Math.cos(rollRad)).subtract(upVec.multiply(Math.sin(rollRad))).normalize();

        // --- 1. 实体伤害 ---
        Box scanBox = new Box(startPos, startPos).expand(radius);
        List<Entity> targets = world.getOtherEntities(user, scanBox);
        for (Entity entity : targets) {
            if (entity instanceof LivingEntity target && target.isAlive()) {
                Vec3d relativePos = target.getBoundingBox().getCenter().subtract(startPos);
                if (checkImpact(relativePos, lookDir, planeNormal, radius, angleLimitRad, halfThickness)) {
                    target.damage(world.getDamageSources().playerAttack(user), 1000.0F);
                    target.takeKnockback(5.0, -lookDir.x, -lookDir.z);
                }
            }
        }

        // --- 2. 方块破坏 (AABB 高效遍历) ---
        if (world instanceof ServerWorld serverWorld) {
            BlockPos min = BlockPos.ofFloored(startPos.x - radius, startPos.y - radius, startPos.z - radius);
            BlockPos max = BlockPos.ofFloored(startPos.x + radius, startPos.y + radius, startPos.z + radius);

            for (BlockPos bPos : BlockPos.iterate(min, max)) {
                Vec3d relativePos = Vec3d.ofCenter(bPos).subtract(startPos);
                if (checkImpact(relativePos, lookDir, planeNormal, radius, angleLimitRad, halfThickness)) {
                    BlockState state = world.getBlockState(bPos);
                    if (!state.isAir() && state.getHardness(world, bPos) >= 0) {
                        world.breakBlock(bPos, false, user);
                    }
                }
            }

            // --- 3. 视觉效果：10格内极致密单层扇面 ---
            double visualRadius = 10.0;
            // 径向步进 0.4，角度步进 1.0，形成几乎无缝的火幕
            for (double r = 0.8; r <= visualRadius; r += 1) {
                for (double a = -angleLimitDeg; a <= angleLimitDeg; a += 1.0) {
                    double radA = Math.toRadians(a);
                    // 计算粒子在 45度 切面上的位置
                    Vec3d sectorDir = lookDir.multiply(Math.cos(radA))
                            .add(planeHorizontal.multiply(Math.sin(radA)));

                    Vec3d pPos = startPos.add(sectorDir.multiply(r));

                    // 纯粹单层粒子，无厚度偏移
                    serverWorld.spawnParticles(
                            ParticleTypes.SOUL_FIRE_FLAME,
                            pPos.x, pPos.y, pPos.z,
                            1, 0.0, 0.0, 0.0, 0.0 // delta和speed全0，强制留在生成点
                    );
                }
            }
        }
    }

    private boolean checkImpact(Vec3d relativePos, Vec3d lookDir, Vec3d planeNormal,
                                double radius, double angleLimitRad, double halfThickness) {
        double distSq = relativePos.lengthSquared();
        if (distSq > radius * radius || distSq < 0.25) return false;

        double dist = Math.sqrt(distSq);
        Vec3d normRelative = relativePos.multiply(1.0 / dist);

        // 判定角度是否在扇区内
        double dot = normRelative.dotProduct(lookDir);
        if (Math.acos(MathHelper.clamp(dot, -1, 1)) > angleLimitRad) return false;

        // 判定方块到切面的垂直距离
        double distanceToPlane = Math.abs(relativePos.dotProduct(planeNormal));
        return distanceToPlane <= halfThickness;
    }
}