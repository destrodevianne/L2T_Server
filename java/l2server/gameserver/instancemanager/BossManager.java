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

package l2server.gameserver.instancemanager;

import lombok.Getter;
import l2server.gameserver.model.actor.instance.L2RaidBossInstance;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Pere
 */
public class BossManager
{
	@Getter private static Map<Integer, L2RaidBossInstance> bosses = new HashMap<>();

	public static BossManager getInstance()
	{
		return SingletonHolder.instance;
	}

	public void registerBoss(L2RaidBossInstance boss)
	{
		bosses.put(boss.getNpcId(), boss);
	}


	public L2RaidBossInstance getBoss(int id)
	{
		return bosses.get(id);
	}

	public boolean isAlive(int id)
	{
		L2RaidBossInstance boss = bosses.get(id);
		return boss != null && !boss.isDead();
	}

	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final BossManager instance = new BossManager();
	}
}
