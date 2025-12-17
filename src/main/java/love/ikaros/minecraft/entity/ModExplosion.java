package love.ikaros.minecraft.entity;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.enchantment.ProtectionEnchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.explosion.EntityExplosionBehavior;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ModExplosion extends Explosion {
    private static final ExplosionBehavior DEFAULT_BEHAVIOR = new ExplosionBehavior();
    private static final int field_30960 = 16;
    private final boolean createFire;
    private final Explosion.DestructionType destructionType;
    private final Random random = Random.create();
    private final World world;
    private final double x;
    private final double y;
    private final double z;
    @Nullable
    private final Entity entity;
    private final float power;
    private final DamageSource damageSource;
    private final ExplosionBehavior behavior;
    private final ObjectArrayList<BlockPos> affectedBlocks = new ObjectArrayList<>();
    private final Map<PlayerEntity, Vec3d> affectedPlayers = Maps.<PlayerEntity, Vec3d>newHashMap();

    public ModExplosion(World world, @Nullable Entity entity, double x, double y, double z, float power, List<BlockPos> affectedBlocks) {
        this(world, entity, x, y, z, power, false, Explosion.DestructionType.DESTROY_WITH_DECAY, affectedBlocks);
    }

    public ModExplosion(
            World world,
            @Nullable Entity entity,
            double x,
            double y,
            double z,
            float power,
            boolean createFire,
            Explosion.DestructionType destructionType,
            List<BlockPos> affectedBlocks
    ) {
        this(world, entity, x, y, z, power, createFire, destructionType);
        this.affectedBlocks.addAll(affectedBlocks);
    }

    public ModExplosion(
            World world, @Nullable Entity entity, double x, double y, double z, float power, boolean createFire, Explosion.DestructionType destructionType
    ) {
        this(world, entity, null, null, x, y, z, power, createFire, destructionType);
    }

    public ModExplosion(
            World world,
            @Nullable Entity entity,
            @Nullable DamageSource damageSource,
            @Nullable ExplosionBehavior behavior,
            double x,
            double y,
            double z,
            float power,
            boolean createFire,
            Explosion.DestructionType destructionType
    ) {
        super(world,
                entity,
                damageSource,
                behavior,
                x,
                y,
                z,
                power,
                createFire,
                destructionType);
        this.world = world;
        this.entity = entity;
        this.power = power;
        this.x = x;
        this.y = y;
        this.z = z;
        this.createFire = createFire;
        this.destructionType = destructionType;
        this.damageSource = damageSource == null ? world.getDamageSources().explosion(this) : damageSource;
        this.behavior = behavior == null ? this.chooseBehavior(entity) : behavior;
    }

    private ExplosionBehavior chooseBehavior(@Nullable Entity entity) {
        return (ExplosionBehavior) (entity == null ? DEFAULT_BEHAVIOR : new EntityExplosionBehavior(entity));
    }

    public static float getExposure(Vec3d source, Entity entity) {
        Box box = entity.getBoundingBox();
        double d = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double e = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double f = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        double g = (1.0 - Math.floor(1.0 / d) * d) / 2.0;
        double h = (1.0 - Math.floor(1.0 / f) * f) / 2.0;
        if (!(d < 0.0) && !(e < 0.0) && !(f < 0.0)) {
            int i = 0;
            int j = 0;

            for (double k = 0.0; k <= 1.0; k += d) {
                for (double l = 0.0; l <= 1.0; l += e) {
                    for (double m = 0.0; m <= 1.0; m += f) {
                        double n = MathHelper.lerp(k, box.minX, box.maxX);
                        double o = MathHelper.lerp(l, box.minY, box.maxY);
                        double p = MathHelper.lerp(m, box.minZ, box.maxZ);
                        Vec3d vec3d = new Vec3d(n + g, o, p + h);
                        if (entity.getWorld().raycast(new RaycastContext(vec3d, source, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity)).getType()
                                == HitResult.Type.MISS) {
                            i++;
                        }

                        j++;
                    }
                }
            }

            return (float) i / j;
        } else {
            return 0.0F;
        }
    }

    public void collectBlocksAndDamageEntities() {
        this.world.emitGameEvent(this.entity, GameEvent.EXPLODE, new Vec3d(this.x, this.y, this.z));
        Set<BlockPos> set = Sets.<BlockPos>newHashSet();
        int i = 16;

//        for (int j = 0; j < 16; j++) {
//            for (int k = 0; k < 16; k++) {
//                for (int l = 0; l < 16; l++) {
//                    if (j == 0 || j == 15 || k == 0 || k == 15 || l == 0 || l == 15) {
//                        double d = j / 15.0F * 2.0F - 1.0F;
//                        double e = k / 15.0F * 2.0F - 1.0F;
//                        double f = l / 15.0F * 2.0F - 1.0F;
//                        double g = Math.sqrt(d * d + e * e + f * f);
//                        d /= g;
//                        e /= g;
//                        f /= g;
//                        float h = this.power * (0.7F + this.world.random.nextFloat() * 0.6F);
//                        double m = this.x;
//                        double n = this.y;
//                        double o = this.z;
//
//                        for (float p = 0.3F; h > 0.0F; h -= 0.22500001F) {
//                            BlockPos blockPos = BlockPos.ofFloored(m, n, o);
//                            BlockState blockState = this.world.getBlockState(blockPos);
//                            FluidState fluidState = this.world.getFluidState(blockPos);
//                            if (!this.world.isInBuildLimit(blockPos)) {
//                                break;
//                            }
//
//                            Optional<Float> optional = this.behavior.getBlastResistance(this, this.world, blockPos, blockState, fluidState);
//                            if (optional.isPresent()) {
//                                h -= (optional.get() + 0.3F) * 0.3F;
//                            }
//
//                            if (h > 0.0F && this.behavior.canDestroyBlock(this, this.world, blockPos, blockState, h)) {
//                                set.add(blockPos);
//                            }
//
//                            m += d * 0.3F;
//                            n += e * 0.3F;
//                            o += f * 0.3F;
//                        }
//                    }
//                }
//            }
//        }

        float radius = this.power; // 爆炸影响半径（可根据需求调整）
        int radiusCeil = (int) Math.ceil(radius);

// 遍历以爆炸点为中心的立方体区域
        for (int x = -radiusCeil; x <= radiusCeil; x++) {
            for (int y = -radiusCeil; y <= radiusCeil; y++) {
                for (int z = -radiusCeil; z <= radiusCeil; z++) {
                    BlockPos pos = new BlockPos(
                            (int)this.x + x,
                            (int)this.y + y,
                            (int)this.z + z
                    );

                    // 只处理球形范围内的方块
                    double distance = Math.sqrt(x*x + y*y + z*z);
                    if (distance > radius) continue;

                    // 计算该位置的爆炸影响强度（随距离衰减）
                    float strength = this.power * (1 - (float)(distance / radius));

                    // 检查方块抗性是否低于当前强度
                    BlockState state = world.getBlockState(pos);
                    float resistance = state.getBlock().getBlastResistance();
                    if (strength > resistance) {
                        this.affectedBlocks.add(pos);
                    }
                }
            }
        }


        //this.affectedBlocks.addAll(set);
        float q = this.power;
        int k = MathHelper.floor(this.x - q - 1.0);
        int lx = MathHelper.floor(this.x + q + 1.0);
        int r = MathHelper.floor(this.y - q - 1.0);
        int s = MathHelper.floor(this.y + q + 1.0);
        int t = MathHelper.floor(this.z - q - 1.0);
        int u = MathHelper.floor(this.z + q + 1.0);
        List<Entity> list = this.world.getOtherEntities(this.entity, new Box(k, r, t, lx, s, u));
        Vec3d vec3d = new Vec3d(this.x, this.y, this.z);

        for (int v = 0; v < list.size(); v++) {
            Entity entity = (Entity) list.get(v);
            if (!entity.isImmuneToExplosion()) {
                double w = Math.sqrt(entity.squaredDistanceTo(vec3d)) / q;
                if (w <= 1.0) {
                    double x = entity.getX() - this.x;
                    double y = (entity instanceof TntEntity ? entity.getY() : entity.getEyeY()) - this.y;
                    double z = entity.getZ() - this.z;
                    double aa = Math.sqrt(x * x + y * y + z * z);
                    if (aa != 0.0) {
                        x /= aa;
                        y /= aa;
                        z /= aa;
                        //double ab = getExposure(vec3d, entity);
                        //double ac = (1.0 - w) * ab;
                        double ac = (1.0 - w);
                        //entity.damage(this.getDamageSource(), (int) ((ac * ac + ac) / 2.0 * 7.0 * q + 1.0));
                        float damage = (float) (ac * 7.0 * q + 1.0);
                        entity.damage(this.getDamageSource(), damage);
                        double ad;
                        if (entity instanceof LivingEntity livingEntity) {
                            ad = ProtectionEnchantment.transformExplosionKnockback(livingEntity, ac);
                        } else {
                            ad = ac;
                        }

                        x *= ad;
                        y *= ad;
                        z *= ad;
                        Vec3d vec3d2 = new Vec3d(x, y, z);
                        entity.setVelocity(entity.getVelocity().add(vec3d2));
                        if (entity instanceof PlayerEntity playerEntity && !playerEntity.isSpectator() && (!playerEntity.isCreative() || !playerEntity.getAbilities().flying)) {
                            this.affectedPlayers.put(playerEntity, vec3d2);
                        }
                    }
                }
            }
        }
    }

    /**
     * @param particles whether this explosion should emit explosion or explosion emitter particles around the source of the explosion
     */
    public void affectWorld(boolean particles) {

        boolean bl = this.shouldDestroy();
        if (particles) {
            if (!(this.power < 2.0F) && bl) {
                this.world.addParticle(ParticleTypes.EXPLOSION_EMITTER, this.x, this.y, this.z, 1.0, 0.0, 0.0);
            } else {
                this.world.addParticle(ParticleTypes.EXPLOSION, this.x, this.y, this.z, 1.0, 0.0, 0.0);
            }
        }

        if (bl) {
            ObjectArrayList<Pair<ItemStack, BlockPos>> objectArrayList = new ObjectArrayList<>();
            boolean bl2 = this.getCausingEntity() instanceof PlayerEntity;
            Util.shuffle(this.affectedBlocks, this.world.random);

            for (BlockPos blockPos : this.affectedBlocks) {
                BlockState blockState = this.world.getBlockState(blockPos);
                Block block = blockState.getBlock();
                if (!blockState.isAir()) {
                    BlockPos blockPos2 = blockPos.toImmutable();
                    this.world.getProfiler().push("explosion_blocks");
//                    if (block.shouldDropItemsOnExplosion(this) && this.world instanceof ServerWorld serverWorld) {
//                        BlockEntity blockEntity = blockState.hasBlockEntity() ? this.world.getBlockEntity(blockPos) : null;
//                        LootContextParameterSet.Builder builder = new LootContextParameterSet.Builder(serverWorld)
//                                .add(LootContextParameters.ORIGIN, Vec3d.ofCenter(blockPos))
//                                .add(LootContextParameters.TOOL, ItemStack.EMPTY)
//                                .addOptional(LootContextParameters.BLOCK_ENTITY, blockEntity)
//                                .addOptional(LootContextParameters.THIS_ENTITY, this.entity);
//                        if (this.destructionType == Explosion.DestructionType.DESTROY_WITH_DECAY) {
//                            builder.add(LootContextParameters.EXPLOSION_RADIUS, this.power);
//                        }
//
//                        blockState.onStacksDropped(serverWorld, blockPos, ItemStack.EMPTY, bl2);
//                        blockState.getDroppedStacks(builder).forEach(stack -> tryMergeStack(objectArrayList, stack, blockPos2));
//                    }

                    this.world.setBlockState(blockPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                    block.onDestroyedByExplosion(this.world, blockPos, this);
                    this.world.getProfiler().pop();
                }
            }

            for (Pair<ItemStack, BlockPos> pair : objectArrayList) {
                Block.dropStack(this.world, pair.getSecond(), pair.getFirst());
            }
        }

        if (this.createFire) {
            for (BlockPos blockPos3 : this.affectedBlocks) {
                if (this.random.nextInt(3) == 0
                        && this.world.getBlockState(blockPos3).isAir()
                        && this.world.getBlockState(blockPos3.down()).isOpaqueFullCube(this.world, blockPos3.down())) {
                    this.world.setBlockState(blockPos3, AbstractFireBlock.getState(this.world, blockPos3));
                }
            }
        }
    }

    public boolean shouldDestroy() {
        return this.destructionType != Explosion.DestructionType.KEEP;
    }

    private static void tryMergeStack(ObjectArrayList<Pair<ItemStack, BlockPos>> stacks, ItemStack stack, BlockPos pos) {
        int i = stacks.size();

        for (int j = 0; j < i; j++) {
            Pair<ItemStack, BlockPos> pair = stacks.get(j);
            ItemStack itemStack = pair.getFirst();
            if (ItemEntity.canMerge(itemStack, stack)) {
                ItemStack itemStack2 = ItemEntity.merge(itemStack, stack, 16);
                stacks.set(j, Pair.of(itemStack2, pair.getSecond()));
                if (stack.isEmpty()) {
                    return;
                }
            }
        }

        stacks.add(Pair.of(stack, pos));
    }

    public DamageSource getDamageSource() {
        return this.damageSource;
    }

    public Map<PlayerEntity, Vec3d> getAffectedPlayers() {
        return this.affectedPlayers;
    }

    @Nullable
    public LivingEntity getCausingEntity() {
        if (this.entity == null) {
            return null;
        } else if (this.entity instanceof TntEntity tntEntity) {
            return tntEntity.getOwner();
        } else if (this.entity instanceof LivingEntity livingEntity) {
            return livingEntity;
        } else {
            return this.entity instanceof ProjectileEntity projectileEntity && projectileEntity.getOwner() instanceof LivingEntity livingEntity2 ? livingEntity2 : null;
        }
    }

    @Nullable
    public Entity getEntity() {
        return this.entity;
    }

    public void clearAffectedBlocks() {
        this.affectedBlocks.clear();
    }

    public List<BlockPos> getAffectedBlocks() {
        return this.affectedBlocks;
    }

    public static enum DestructionType {
        KEEP,
        DESTROY,
        DESTROY_WITH_DECAY;
    }
}
