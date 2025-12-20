package love.ikaros.minecraft.logic;

import love.ikaros.minecraft.item.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;

public class FlightHandler {

    public static void register() {
        // 注册服务端 Tick 事件
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (PlayerEntity player : server.getPlayerManager().getPlayerList()) {
                // 检查玩家胸口是否穿着你自定义的物品
                // 假设你在 ModItems 里注册的翅膀叫 SUPER_WINGS
                ItemStack chestStack = player.getEquippedStack(EquipmentSlot.CHEST);

                String itemId = Registries.ITEM.getId(chestStack.getItem()).toString();
                if (itemId.equals("synapsemod:tool/super_wing")) { // 这里的字符串必须和你注册时的 ID 一模一样
                    if (player.isFallFlying()) {
                        applyViolence(player);
                    }
                }
            }
        });
    }

    private static void applyViolence(PlayerEntity player) {
        // 1. 获取准星方向（单位向量，长度为 1）
        Vec3d look = player.getRotationVec(1.0F);

        // 2. 获取当前速度
        Vec3d velocity = player.getVelocity();

        // 3. 计算当前的速率（速度的大小）
        double currentSpeed = velocity.length();

        // --- 暴力逻辑开始 ---

        // 推力大小（这就是你想要的“加速度”）
        double acceleration = 0.1;

        // 速度限制：防止飞得太快导致客户端崩溃（4.0 约等于 80米/秒，极快了）
        double maxSpeed = 100.0;

        if (currentSpeed < maxSpeed) {
            // 关键：沿着准星方向施加加速度，而不是死板地加在坐标轴上
            // 这样即使你转弯，加速度也会顺着你的新方向推你
            velocity = velocity.add(look.multiply(acceleration));
        }

        // 4. 可选：方向引导（让翅膀操控更灵敏）
        // 如果你想让转弯更丝滑，可以让速度向量向准星方向微微偏移
        if (currentSpeed > 0.2) {
            // 这个系数（0.05）决定了操控感：值越大，转向越灵敏，越像飞机；值越小，越有惯性
            double steeringSensitivity = 0.5;
            velocity = velocity.lerp(look.multiply(currentSpeed), steeringSensitivity);
        }

        // 5. 应用新速度
        player.setVelocity(velocity);
        player.velocityModified = true;

        // 消除摔落伤害
        //player.onLanding();
    }
}