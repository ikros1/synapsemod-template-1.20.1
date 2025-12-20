package love.ikaros.minecraft.item;


import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import love.ikaros.minecraft.entity.ModTntEntity;
import love.ikaros.minecraft.sound.ModSoundEvents;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.*;
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
    public static final int TICKS_PER_SECOND = 20;
    public static final int RANGE = 20;

    public ModBowItem(Item.Settings settings) {
        super(settings);
    }


    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        BlockPos posStart = new BlockPos(0, 0, 0);
        BlockPos posEnd = new BlockPos(0, 0, 0);
        if (user instanceof PlayerEntity playerEntity) {
            boolean bl = playerEntity.getAbilities().creativeMode;
            ItemStack itemStack = user.getStackInHand(Hand.OFF_HAND);
            if ((!itemStack.isEmpty() && itemStack.isOf(ModItems.APOLLON_ARROWS))|| bl) {
                if (itemStack.isEmpty()) {
                    itemStack = new ItemStack(ModItems.APOLLON_ARROWS);
                }
                int i = this.getMaxUseTime(stack) - remainingUseTicks;
                float f = getPullProgress(i);
                if (!(f < 0.1)) {
                    boolean bl2 = bl && itemStack.isOf(ModItems.APOLLON_ARROWS);
                    if (!world.isClient) {
                        ArrowItem arrowItem = (ArrowItem)(itemStack.getItem() instanceof ArrowItem ? itemStack.getItem() : Items.ARROW);
                        PersistentProjectileEntity persistentProjectileEntity =  arrowItem.createArrow(world, itemStack, playerEntity);
                        persistentProjectileEntity.setVelocity(playerEntity, playerEntity.getPitch(), playerEntity.getYaw(), 0.0F, f * 400.0F, 1.0F);
                        if (f == 1.0F) {
                            persistentProjectileEntity.setCritical(true);
                        }

                        int j = EnchantmentHelper.getLevel(Enchantments.POWER, stack);
                        if (j > 0) {
                            persistentProjectileEntity.setDamage(persistentProjectileEntity.getDamage() + j * 0.5 + 0.5);
                        }

                        int k = EnchantmentHelper.getLevel(Enchantments.PUNCH, stack);
                        if (k > 0) {
                            persistentProjectileEntity.setPunch(k);
                        }

                        if (EnchantmentHelper.getLevel(Enchantments.FLAME, stack) > 0) {
                            persistentProjectileEntity.setOnFireFor(100);
                        }

                        stack.damage(1, playerEntity, p -> p.sendToolBreakStatus(playerEntity.getActiveHand()));
                        if (bl2 || playerEntity.getAbilities().creativeMode && (itemStack.isOf(Items.SPECTRAL_ARROW) || itemStack.isOf(Items.TIPPED_ARROW))) {
                            persistentProjectileEntity.pickupType = PersistentProjectileEntity.PickupPermission.CREATIVE_ONLY;
                        }

                        world.spawnEntity(persistentProjectileEntity);
                        int maxTime = 10;
                        posStart = persistentProjectileEntity.getBlockPos();
                        while (!persistentProjectileEntity.isNoClip()&&maxTime>0) {
                            Vec3d vec3d = persistentProjectileEntity.getPos();

                            // 在模拟轨迹的每个点生成灵魂火
                            ((net.minecraft.server.world.ServerWorld)world).spawnParticles(
                                    net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                                    vec3d.x, vec3d.y, vec3d.z,
                                    5,        // 每次生成1个
                                    0.02, 0.02, 0.02, // 极其微小的随机偏移
                                    0.01      // 速度
                            );
                            int x =Double.valueOf(vec3d.getX()).intValue();
                            int y =Double.valueOf(vec3d.getY()).intValue();
                            int z =Double.valueOf(vec3d.getZ()).intValue();
                            posEnd = new BlockPos(x, y, z);
                            //System.out.println(posEnd);
                            persistentProjectileEntity.tick();
                            maxTime--;
                        }

                        if(0.2F<f&&f<0.5F){
                            primeTnt(world,posEnd,playerEntity);
                        }
                        if(f>=0.5F){
                            // 计算方向向量（从起点到终点）
                            Vec3d direction = new Vec3d(
                                    posEnd.getX() - posStart.getX(),
                                    posEnd.getY() - posStart.getY(),
                                    posEnd.getZ() - posStart.getZ()
                            ).normalize();

                            // 设置参数
                            double step = 40.0;   // 每个爆炸点的间隔
                            int safeDistance = 52; // 安全距离
                            int maxCount = 80;     // 最大爆炸点数

                            // 从起点偏移安全距离开始（加0.5让坐标居中）
                            Vec3d currentPos = new Vec3d(
                                    posStart.getX() + 0.5 + direction.x * safeDistance,
                                    posStart.getY() + 0.5 + direction.y * safeDistance,
                                    posStart.getZ() + 0.5 + direction.z * safeDistance
                            );

                            int spawned = 0;
                            while(spawned < maxCount){
                                // 生成TNT
                                primeMaxTnt(world, new BlockPos(
                                        (int)Math.floor(currentPos.x),
                                        (int)Math.floor(currentPos.y),
                                        (int)Math.floor(currentPos.z)
                                ), playerEntity);

                                // 向下个位置移动
                                currentPos = currentPos.add(direction.multiply(step));
                                spawned++;

                                // 射程保护（RANGE=40时最大800格）
                                if(currentPos.distanceTo(Vec3d.of(posStart)) > 80*(f-0.5) * step) break;
                            }
                            //System.out.println("zui hou"+posEnd);
                            primeMaxTnt(world,posEnd,playerEntity);
                        }
                    }
                    if(f==1.0F){
                        world.playSound(
                                null,
                                playerEntity.getX(),
                                playerEntity.getY(),
                                playerEntity.getZ(),
                                ModSoundEvents.APOLLON_ARROW_SHOOT,
                                SoundCategory.PLAYERS,
                                1.0F,
                                1.0F / (world.getRandom().nextFloat() * 0.4F + 1.2F) + f * 0.5F
                        );
                    }else {
                        world.playSound(
                                null,
                                playerEntity.getX(),
                                playerEntity.getY(),
                                playerEntity.getZ(),
                                SoundEvents.ENTITY_ARROW_SHOOT,
                                SoundCategory.PLAYERS,
                                1.0F,
                                1.0F / (world.getRandom().nextFloat() * 0.4F + 1.2F) + f * 0.5F
                        );
                    }


                    if (!bl2 && !playerEntity.getAbilities().creativeMode) {
                        itemStack.decrement(1);
                        if (itemStack.isEmpty()) {
                            playerEntity.getInventory().removeOne(itemStack);
                        }
                    }

                    playerEntity.incrementStat(Stats.USED.getOrCreateStat(this));
                }
            }
        }
    }

    public static float getPullProgress(int useTicks) {
        float f = useTicks / 40.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        if (f > 1.0F) {
            f = 1.0F;
        }
        return f;
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
                net.minecraft.server.world.ServerWorld serverWorld = (net.minecraft.server.world.ServerWorld) world;

                float yaw = player.getYaw();
                float[] wingAngles = {yaw + 135f, yaw + 225f};

                for (float baseAngle : wingAngles) {
                    // 增加粒子密度
                    for (int i = 0; i < 20; i++) {
                        double radians = Math.toRadians(baseAngle);
                        double dirX = -Math.sin(radians);
                        double dirZ = Math.cos(radians);

                        // --- 消失距离控制逻辑 ---
                        // 1. 既然不能直接删掉远程粒子，我们就让粒子在 [0, 2] 格范围内随机位置生成
                        // 2. 然后给它一个极短的寿命（SOUL_FIRE_FLAME 寿命较固定，所以我们控制速度）

                        double spawnDist = world.random.nextDouble() * 2.0; // 随机分布在0-2格内
                        double offsetX = dirX * spawnDist;
                        double offsetZ = dirZ * spawnDist;

                        // 高度依然保持
                        double offsetY = 0.5 + world.random.nextDouble() * 1.5;

                        // 速度设小一点，或者让它几乎不移动，只在原地闪烁
                        // 这样粒子看起来就像是在 2 格范围内凭空出现并闪烁消失
                        double speedMultiplier = 0.1;
                        double vx = dirX * speedMultiplier;
                        double vz = dirZ * speedMultiplier;

                        serverWorld.spawnParticles(
                                net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                                player.getX() + offsetX,
                                player.getY() + offsetY,
                                player.getZ() + offsetZ,
                                0,
                                vx,
                                0.02,   // 微微上升
                                vz,
                                0.5     // 速度系数
                        );
                    }
                }

                // --- 2. 新增：箭头着火效果 ---
                // 获取玩家视线方向向量 (长度为1)
                Vec3d lookVec = player.getRotationVec(1.0F);

                // 计算箭头的空间坐标：
                // 眼睛位置 + 视线方向 * 距离(约0.7格)
                // 稍微向下偏移一点 (y - 0.2)，因为弓箭通常拿在胸口高度
                double arrowX = player.getX() + lookVec.x * 1+0.5;
                double arrowY = player.getEyeY() + lookVec.y * 1 - 0.2;
                double arrowZ = player.getZ() + lookVec.z * 1;

                // 在箭头位置生成一小团密集的灵魂火
                for (int j = 0; j < 5; j++) {
                    serverWorld.spawnParticles(
                            net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                            arrowX + (world.random.nextDouble() - 0.5) * 0.1, // 微小抖动让火团更真实
                            arrowY + (world.random.nextDouble() - 0.5) * 0.1,
                            arrowZ + (world.random.nextDouble() - 0.5) * 0.1,
                            1,      // count
                            0.02, 0.02, 0.02, // 扩散速度
                            0.01    // 速度乘数
                    );
                }

                if (world.getTime() % 4 == 0) {
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            net.minecraft.sound.SoundEvents.BLOCK_FIRE_AMBIENT,
                            net.minecraft.sound.SoundCategory.PLAYERS, 0.5f, 2.0f);
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
        ItemStack offHandItemStack = user.getStackInHand(Hand.OFF_HAND);

        user.playSound(SoundEvents.ITEM_SPYGLASS_USE, 1.0F, 1.0F);

        // user.incrementStat(Stats.USED.getOrCreateStat(this));

        //boolean bl = !getProjectileType(itemStack,user).isEmpty();
        if (!user.getAbilities().creativeMode && !offHandItemStack.isOf(ModItems.APOLLON_ARROWS)) {
            //System.out.println("fen1");
            return TypedActionResult.fail(itemStack);
        } else {
            //System.out.println("fen2");
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
