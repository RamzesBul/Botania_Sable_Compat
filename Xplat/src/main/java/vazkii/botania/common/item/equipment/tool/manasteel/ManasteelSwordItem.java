/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.item.equipment.tool.manasteel;

import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

import vazkii.botania.api.BotaniaAPI;

public class ManasteelSwordItem extends SwordItem {

	public ManasteelSwordItem(Properties props) {
		this(BotaniaAPI.instance().getManasteelItemTier(), props);
	}

	public ManasteelSwordItem(Tier mat, Properties props) {
		this(mat, 3, -2.4F, props);
	}

	public ManasteelSwordItem(Tier mat, int attackDamage, float attackSpeed, Properties props) {
		super(mat, props.attributes(ManasteelSwordItem.createAttributes(mat, attackDamage, attackSpeed)));
	}

}
