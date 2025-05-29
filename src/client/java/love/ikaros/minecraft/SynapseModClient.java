package love.ikaros.minecraft;

import love.ikaros.minecraft.item.ModItems;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.util.Identifier;

public class SynapseModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		registerModelPredicateProviders();
	}


	public static void registerModelPredicateProviders() {
		//对于 1.21 之前的版本，将 'Identifier.ofVanilla' 替换为 'new Identifier'。
		ModelPredicateProviderRegistry.register(ModItems.APOLLON, new Identifier("pull"), (itemStack, clientWorld, livingEntity, seed) -> {
			if (livingEntity == null) {
				return 0.0F;
			}
			return livingEntity.getActiveItem() != itemStack ? 0.0F : (itemStack.getMaxUseTime() - livingEntity.getItemUseTimeLeft()) / 20.0F;
		});

		ModelPredicateProviderRegistry.register(ModItems.APOLLON, new Identifier("pulling"), (itemStack, clientWorld, livingEntity, seed) -> {
			if (livingEntity == null) {
				return 0.0F;
			}
			return livingEntity.isUsingItem() && livingEntity.getActiveItem() == itemStack ? 1.0F : 0.0F;
		});
	}

}