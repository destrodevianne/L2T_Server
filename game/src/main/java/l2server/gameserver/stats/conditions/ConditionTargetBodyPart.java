/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package l2server.gameserver.stats.conditions;

import l2server.gameserver.model.itemcontainer.Inventory;
import l2server.gameserver.stats.Env;
import l2server.gameserver.templates.item.ArmorTemplate;
import l2server.gameserver.templates.item.ItemTemplate;

/**
 * The Class ConditionTargetBodyPart.
 *
 * @author mkizub
 */
public class ConditionTargetBodyPart extends Condition {

	private ArmorTemplate armor;

	/**
	 * Instantiates a new condition target body part.
	 *
	 * @param armor the armor
	 */
	public ConditionTargetBodyPart(ArmorTemplate armor) {
		this.armor = armor;
	}

	/* (non-Javadoc)
	 * @see l2server.gameserver.stats.conditions.Condition#testImpl(l2server.gameserver.stats.Env)
	 */
	@Override
	public boolean testImpl(Env env) {
		// target is attacker
		if (env.target == null) {
			return true;
		}
		int bodypart = env.target.getAttackingBodyPart();
		int armor_part = armor.getBodyPart();
		switch (bodypart) {
			case Inventory.PAPERDOLL_CHEST:
				return (armor_part & (ItemTemplate.SLOT_CHEST | ItemTemplate.SLOT_FULL_ARMOR | ItemTemplate.SLOT_UNDERWEAR)) != 0;
			case Inventory.PAPERDOLL_LEGS:
				return (armor_part & (ItemTemplate.SLOT_LEGS | ItemTemplate.SLOT_FULL_ARMOR)) != 0;
			case Inventory.PAPERDOLL_HEAD:
				return (armor_part & ItemTemplate.SLOT_HEAD) != 0;
			case Inventory.PAPERDOLL_FEET:
				return (armor_part & ItemTemplate.SLOT_FEET) != 0;
			case Inventory.PAPERDOLL_GLOVES:
				return (armor_part & ItemTemplate.SLOT_GLOVES) != 0;
			default:
				return true;
		}
	}
}
