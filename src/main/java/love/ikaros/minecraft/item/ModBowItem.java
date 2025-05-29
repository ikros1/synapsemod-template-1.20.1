package love.ikaros.minecraft.item;


import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

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
    public static final int RANGE = 40;

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
                        persistentProjectileEntity.setVelocity(playerEntity, playerEntity.getPitch(), playerEntity.getYaw(), 0.0F, f * 300.0F, 1.0F);
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
                        int maxTnt = Float.valueOf(80*f-20).intValue();
                        maxTnt = Math.max(maxTnt, 0);
                        int maxTime = 10;
                        posStart = persistentProjectileEntity.getBlockPos();
                        while (!persistentProjectileEntity.isNoClip()&&maxTime>0) {
                            Vec3d vec3d = persistentProjectileEntity.getPos();
                            int x =Double.valueOf(vec3d.getX()).intValue();
                            int y =Double.valueOf(vec3d.getY()).intValue();
                            int z =Double.valueOf(vec3d.getZ()).intValue();
                            posEnd = new BlockPos(x, y, z);
                            persistentProjectileEntity.tick();
                            maxTime--;
                        }
                        if(f<1.0F){
                            while (maxTnt>0) {
                                primeTnt(world,posEnd,playerEntity);
                                maxTnt--;
                            }
                        }
                        if(f==1.0F){
                            int startX = posStart.getX();
                            int startY = posStart.getY();
                            int startZ = posStart.getZ();
                            int endX = posEnd.getX();
                            int endY = posEnd.getY();
                            int endZ = posEnd.getZ();

                            double vecX = endX - startX;
                            double vecY = endY - startY;
                            double vecZ = endZ - startZ;

                            double length = Math.sqrt(vecX*vecX + vecY*vecY + vecZ*vecZ);
                            if(length > 0.001) { // 防止零向量
                                vecX /= length;
                                vecY /= length;
                                vecZ /= length;
                            } else { // 若原路径是零向量，默认向上方延长
                                vecY = 1.0;
                            }

                            int extendedEndX = endX + (int)(vecX * 100);
                            int extendedEndY = endY + (int)(vecY * 100);
                            int extendedEndZ = endZ + (int)(vecZ * 100);
                            int dx = extendedEndX - startX;
                            int dy = extendedEndY - startY;
                            int dz = extendedEndZ - startZ;

// 计算方向向量
                            double dirLength = Math.sqrt(dx*dx + dy*dy + dz*dz);
                            double dirX = dx/dirLength;
                            double dirY = dy/dirLength;
                            double dirZ = dz/dirLength;

// 计算垂直方向向量（右方向）
                            double rightX = -dirZ;
                            double rightY = 0;
                            double rightZ = dirX;
                            double rightLength = Math.sqrt(rightX*rightX + rightZ*rightZ);
                            if(rightLength > 0.001) {
                                rightX /= rightLength;
                                rightZ /= rightLength;
                            } else {
                                rightX = 1;
                                rightZ = 0;
                            }

// 计算上方向向量
                            double upX = dirY*rightZ - dirZ*0;
                            double upY = dirZ*rightX - dirX*rightZ;
                            double upZ = dirX*0 - dirY*rightX;
                            double upLength = Math.sqrt(upX*upX + upY*upY + upZ*upZ);
                            if(upLength > 0.001) {
                                upX /= upLength;
                                upY /= upLength;
                                upZ /= upLength;
                            } else {
                                upY = 1;
                            }

// 设置偏移距离为2
                            double offset = 7.0;
                            double rightOffsetX = rightX * offset;
                            double rightOffsetZ = rightZ * offset;
                            double upOffsetX = upX * offset;
                            double upOffsetY = upY * offset;
                            double upOffsetZ = upZ * offset;

                            List<BlockPos> validPosList = new ArrayList<>();
                            int samplePoints = Math.min(1000, Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) * 3);

                            for(int q=0; q<=samplePoints; q++){
                                float t = q/(float)samplePoints;
                                int x = (int)(startX + dx * t);
                                int y = (int)(startY + dy * t);
                                int z = (int)(startZ + dz * t);
                                BlockPos currentPos = new BlockPos(x,y,z);

                                // 保留重要距离判断！新增四方向点也要判断！
                                int manhattanDist = Math.abs(x-startX)+Math.abs(y-startY)+Math.abs(z-startZ);

                                if(manhattanDist > 4*3){
                                    if(validPosList.isEmpty() || !currentPos.equals(validPosList.get(validPosList.size()-1))){
                                        // 添加主路径点
                                        validPosList.add(currentPos);

                                        // 添加四个方向的爆炸点（每个都要判断距离）
                                        addOffsetPointWithCheck(validPosList, currentPos, startX, startY, startZ, rightOffsetX, 0, rightOffsetZ);
                                        addOffsetPointWithCheck(validPosList, currentPos, startX, startY, startZ, -rightOffsetX, 0, -rightOffsetZ);
                                        addOffsetPointWithCheck(validPosList, currentPos, startX, startY, startZ, upOffsetX, upOffsetY, upOffsetZ);
                                        addOffsetPointWithCheck(validPosList, currentPos, startX, startY, startZ, -upOffsetX, -upOffsetY, -upOffsetZ);
                                    }
                                }
                            }

                            int explosionInterval = 1;
                            for(int q=0; q<validPosList.size(); q+=explosionInterval){
                                primeTnt(world, validPosList.get(q), playerEntity);
                            }
                        }
                    }

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

    // 辅助方法：添加偏移点并去重
    private void addOffsetPointWithCheck(List<BlockPos> list, BlockPos base,
                                         int startX, int startY, int startZ,
                                         double dx, double dy, double dz) {
        BlockPos newPos = new BlockPos(
                base.getX() + (int)Math.round(dx),
                base.getY() + (int)Math.round(dy),
                base.getZ() + (int)Math.round(dz)
        );

        // 计算新点的曼哈顿距离
        int manhattanDist = Math.abs(newPos.getX()-startX)
                + Math.abs(newPos.getY()-startY)
                + Math.abs(newPos.getZ()-startZ);

        // 必须满足距离条件才添加
        if(manhattanDist > 6*3 && !list.contains(newPos)) {
            list.add(newPos);
        }
    }

    public static float getPullProgress(int useTicks) {
        float f = useTicks / 20.0F;
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
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack itemStack = user.getStackInHand(hand);
        ItemStack offHandItemStack = user.getStackInHand(Hand.OFF_HAND);
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

    private static void primeTnt(World world, BlockPos pos, @Nullable LivingEntity igniter) {
        if (!world.isClient) {
            TntEntity tntEntity = new TntEntity(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, igniter);
            tntEntity.setFuse(1);
            world.spawnEntity(tntEntity);
            world.playSound(null, tntEntity.getX(), tntEntity.getY(), tntEntity.getZ(), SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.BLOCKS, 1.0F, 1.0F);
            world.emitGameEvent(igniter, GameEvent.PRIME_FUSE, pos);
        }
    }

}
