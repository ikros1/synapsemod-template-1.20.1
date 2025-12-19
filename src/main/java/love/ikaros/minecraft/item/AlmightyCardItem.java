package love.ikaros.minecraft.item;

import love.ikaros.minecraft.sound.ModSoundEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class AlmightyCardItem extends Item {
    public AlmightyCardItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack itemStack = user.getStackInHand(hand);

        // 播放自定义声音（这里使用了玩家升级的声音，你可以换成别的）
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                ModSoundEvents.LING, SoundCategory.PLAYERS, 1.0F, 1.0F);

        if (!world.isClient) {
            // 添加你之前定义的所有 Buff
            applyAlmightyBuffs(user);

            // 如果不是创造模式，使用后消耗掉一个卡片
            if (!user.getAbilities().creativeMode) {
                itemStack.decrement(1);
            }

            // 设置冷却时间（例如 5 秒），防止手速过快导致逻辑混乱
            user.getItemCooldownManager().set(this, 100);
        }

        return TypedActionResult.success(itemStack, world.isClient());
    }

    private void applyAlmightyBuffs(LivingEntity entity) {
        int duration = 24000; // 时长
        // 批量添加效果
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, duration, 8));
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, duration, 64));
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, duration, 64));
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, duration, 64));
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, duration, 4));
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, duration, 4));
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, duration, 4));
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, duration, 4));
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, duration, 64));
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.LUCK, duration, 1024));
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, duration, 64));
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, duration, 64));
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, duration, 64));
    }
}