package love.ikaros.minecraft.item;

import java.util.function.Predicate;

import love.ikaros.minecraft.entity.ModTntEntity;
import love.ikaros.minecraft.sound.ModSoundEvents;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

public class ModBowItem extends RangedWeaponItem implements Vanishable {

    public ModBowItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!(user instanceof PlayerEntity playerEntity)) return;

        boolean isCreative = playerEntity.getAbilities().creativeMode;
        ItemStack ammoStack = user.getStackInHand(Hand.OFF_HAND);

        // 判定弹药逻辑
        if ((!ammoStack.isEmpty() && ammoStack.isOf(ModItems.APOLLON_ARROWS)) || isCreative) {
            if (ammoStack.isEmpty()) {
                ammoStack = new ItemStack(ModItems.APOLLON_ARROWS);
            }

            int useDuration = this.getMaxUseTime(stack) - remainingUseTicks;
            float f = getPullProgress(useDuration);

            if (f < 0.1F) return;

            if (!world.isClient) {
                ServerWorld serverWorld = (ServerWorld) world;

                // 1. 生成并射出实体箭
                ArrowItem arrowItem = (ArrowItem) (ammoStack.getItem() instanceof ArrowItem ? ammoStack.getItem() : Items.ARROW);
                PersistentProjectileEntity arrow = arrowItem.createArrow(world, ammoStack, playerEntity);

                // 设置极高的初速度
                arrow.setVelocity(playerEntity, playerEntity.getPitch(), playerEntity.getYaw(), 0.0F, f * 400.0F, 1.0F);
                if (f == 1.0F) arrow.setCritical(true);

                // 处理附魔逻辑
                int power = EnchantmentHelper.getLevel(Enchantments.POWER, stack);
                if (power > 0) arrow.setDamage(arrow.getDamage() + power * 0.5 + 0.5);
                int punch = EnchantmentHelper.getLevel(Enchantments.PUNCH, stack);
                if (punch > 0) arrow.setPunch(punch);
                if (EnchantmentHelper.getLevel(Enchantments.FLAME, stack) > 0) arrow.setOnFireFor(100);

                stack.damage(1, playerEntity, p -> p.sendToolBreakStatus(playerEntity.getActiveHand()));
                if (isCreative) arrow.pickupType = PersistentProjectileEntity.PickupPermission.CREATIVE_ONLY;

                world.spawnEntity(arrow);

                // --- 2. 核心改进：基于向量的高密度粒子轨迹 ---
                Vec3d startPos = playerEntity.getEyePos();
                Vec3d lookVec = playerEntity.getRotationVec(1.0F);

                // 计算射程：f=1.0时最大约800格
                double maxParticleDist = f * 800.0;
                int particleCount = 500;
                double step = maxParticleDist / particleCount;

                for (int i = 0; i < particleCount; i++) {
                    Vec3d pPos = startPos.add(lookVec.multiply(i * step));
                    serverWorld.spawnParticles(
                            net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                            pPos.x, pPos.y, pPos.z,
                            1, 0.02, 0.02, 0.02, 0.01
                    );
                }

                // 计算逻辑终点 posEnd
                Vec3d endVec = startPos.add(lookVec.multiply(maxParticleDist));
                BlockPos posStart = playerEntity.getBlockPos();
                BlockPos posEnd = new BlockPos((int) endVec.x, (int) endVec.y, (int) endVec.z);

                // --- 3. TNT 链式爆炸逻辑 ---
                if (f > 0.2F && f < 0.5F) {
                    primeTnt(world, posEnd, playerEntity);
                } else if (f >= 0.5F) {
                    Vec3d direction = lookVec.normalize();
                    double explosionInterval = 40.0;
                    int safeDist = 52;
                    int maxExplosions = 80;

                    Vec3d currentTntPos = startPos.add(direction.multiply(safeDist));
                    int spawned = 0;
                    while (spawned < maxExplosions) {
                        primeMaxTnt(world, new BlockPos((int) currentTntPos.x, (int) currentTntPos.y, (int) currentTntPos.z), playerEntity);
                        currentTntPos = currentTntPos.add(direction.multiply(explosionInterval));
                        spawned++;

                        // 动态射程保护
                        if (currentTntPos.distanceTo(startPos) > 80 * (f - 0.5) * explosionInterval) break;
                    }
                    primeMaxTnt(world, posEnd, playerEntity);
                }
            }

            // 音效处理
            float pitch = 1.0F / (world.getRandom().nextFloat() * 0.4F + 1.2F) + f * 0.5F;
            if (f == 1.0F) {
                world.playSound(null, playerEntity.getX(), playerEntity.getY(), playerEntity.getZ(), ModSoundEvents.APOLLON_ARROW_SHOOT, SoundCategory.PLAYERS, 1.0F, pitch);
            } else {
                world.playSound(null, playerEntity.getX(), playerEntity.getY(), playerEntity.getZ(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.0F, pitch);
            }

            // 消耗弹药
            if (!isCreative) {
                ammoStack.decrement(1);
                if (ammoStack.isEmpty()) playerEntity.getInventory().removeOne(ammoStack);
            }
            playerEntity.incrementStat(Stats.USED.getOrCreateStat(this));
        }
    }

    public static float getPullProgress(int useTicks) {
        float f = useTicks / 40.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        return Math.min(f, 1.0F);
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!world.isClient && user instanceof PlayerEntity player) {
            int useDuration = this.getMaxUseTime(stack) - remainingUseTicks;
            float pullProgress = getPullProgress(useDuration);

            if (pullProgress >= 1.0F) {
                ServerWorld serverWorld = (ServerWorld) world;
                float yaw = player.getYaw();
                float[] wingAngles = {yaw + 135f, yaw + 225f};

                for (float baseAngle : wingAngles) {
                    for (int i = 0; i < 20; i++) {
                        double radians = Math.toRadians(baseAngle);
                        double dirX = -Math.sin(radians);
                        double dirZ = Math.cos(radians);
                        double spawnDist = world.random.nextDouble() * 2.0;
                        double offsetX = dirX * spawnDist;
                        double offsetZ = dirZ * spawnDist;
                        double offsetY = 0.5 + world.random.nextDouble() * 1.5;

                        serverWorld.spawnParticles(
                                net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                                player.getX() + offsetX, player.getY() + offsetY, player.getZ() + offsetZ,
                                0, dirX * 0.1, 0.02, dirZ * 0.1, 0.5
                        );
                    }
                }

                // 箭头着火视觉效果
                Vec3d lookVec = player.getRotationVec(1.0F);
                double arrowX = player.getX() + lookVec.x * 1 + 0.5;
                double arrowY = player.getEyeY() + lookVec.y * 1 - 0.2;
                double arrowZ = player.getZ() + lookVec.z * 1;

                for (int j = 0; j < 5; j++) {
                    serverWorld.spawnParticles(
                            net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                            arrowX + (world.random.nextDouble() - 0.5) * 0.1,
                            arrowY + (world.random.nextDouble() - 0.5) * 0.1,
                            arrowZ + (world.random.nextDouble() - 0.5) * 0.1,
                            1, 0.02, 0.02, 0.02, 0.01
                    );
                }

                if (world.getTime() % 4 == 0) {
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.PLAYERS, 0.5f, 2.0f);
                }
            }
        }
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack itemStack = user.getStackInHand(hand);
        ItemStack offHand = user.getStackInHand(Hand.OFF_HAND);
        user.playSound(SoundEvents.ITEM_SPYGLASS_USE, 1.0F, 1.0F);

        if (!user.getAbilities().creativeMode && !offHand.isOf(ModItems.APOLLON_ARROWS)) {
            return TypedActionResult.fail(itemStack);
        } else {
            user.setCurrentHand(hand);
            return TypedActionResult.consume(itemStack);
        }
    }

    @Override
    public Predicate<ItemStack> getProjectiles() {
        return BOW_PROJECTILES;
    }

    @Override
    public int getRange() {
        return 40;
    }

    private static void primeMaxTnt(World world, BlockPos pos, @Nullable LivingEntity igniter) {
        if (!world.isClient) {
            ModTntEntity tntEntity = new ModTntEntity(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, igniter);
            tntEntity.setFuse(1);
            tntEntity.power = 50;
            world.spawnEntity(tntEntity);
            world.emitGameEvent(igniter, GameEvent.PRIME_FUSE, pos);
        }
    }

    private static void primeTnt(World world, BlockPos pos, @Nullable LivingEntity igniter) {
        if (!world.isClient) {
            ModTntEntity tntEntity = new ModTntEntity(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, igniter);
            tntEntity.setFuse(1);
            world.spawnEntity(tntEntity);
            world.emitGameEvent(igniter, GameEvent.PRIME_FUSE, pos);
        }
    }
}