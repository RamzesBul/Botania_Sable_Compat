/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.item.equipment.tool.manasteel;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.state.BlockState;

import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.item.SortableTool;
import vazkii.botania.common.item.equipment.tool.ToolCommons;

public class ManasteelShovelItem extends ShovelItem implements SortableTool {

	public ManasteelShovelItem(Properties props) {
		this(BotaniaAPI.instance().getManasteelItemTier(), props);
	}

	public ManasteelShovelItem(Tier mat, Properties props) {
		super(mat, props.attributes(ManasteelShovelItem.createAttributes(mat, 1.5F, -3.0F)));
	}

	@Override
	public int getSortingPriority(ItemStack stack, BlockState state) {
		return ToolCommons.getToolPriority(stack);
	}
}
