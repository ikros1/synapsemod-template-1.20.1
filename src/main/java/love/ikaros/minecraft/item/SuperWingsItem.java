package love.ikaros.minecraft.item;

import net.fabricmc.fabric.api.entity.event.v1.FabricElytraItem;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;

/**
 * 继承原版 ElytraItem 保证基础逻辑
 * 实现 FabricElytraItem 接口，这是在 Fabric 中让自定义物品能滑翔的“通行证”
 */
public class SuperWingsItem extends ElytraItem implements FabricElytraItem {

    public SuperWingsItem(Settings settings) {
        super(settings);
    }

    // 你可以根据需要在这里重写修复材料
    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return super.canRepair(stack, ingredient);
    }

    // 如果你想让这个翅膀在耐久耗尽前依然可用，可以重写 isUsable
    public static boolean isUsable(ItemStack stack) {
        return stack.getDamage() < stack.getMaxDamage() - 1;
    }
}