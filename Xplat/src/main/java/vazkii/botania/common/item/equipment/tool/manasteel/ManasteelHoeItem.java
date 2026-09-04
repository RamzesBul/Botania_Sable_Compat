/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.item.equipment.tool.manasteel;

import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.state.BlockState;

import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.item.SortableTool;
import vazkii.botania.common.item.equipment.tool.ToolCommons;

public class ManasteelHoeItem extends HoeItem implements SortableTool {

	public ManasteelHoeItem(Properties props) {
		this(BotaniaAPI.instance().getManasteelItemTier(), props, -1f);
	}

	public ManasteelHoeItem(Tier mat, Properties properties, float attackSpeed) { //Todo unsure about this
		super(mat, properties.attributes(ManasteelHoeItem.createAttributes(mat, -mat.getAttackDamageBonus(), attackSpeed)));
	}

	@Override
	public int getSortingPriority(ItemStack stack, BlockState state) {
		return ToolCommons.getToolPriority(stack);
	}
}
