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

package l2server.gameserver.network.clientpackets;

import l2server.Config;
import l2server.gameserver.model.actor.instance.Player;
import l2server.gameserver.network.serverpackets.ExPrivateStoreSetWholeMsg;
import l2server.gameserver.util.Util;

/**
 * @author KenM
 */
public class SetPrivateStoreWholeMsg extends L2GameClientPacket {
	private static final int MAX_MSG_LENGTH = 29;
	
	private String msg;
	
	/**
	 * @see L2GameClientPacket#readImpl()
	 */
	@Override
	protected void readImpl() {
		msg = readS();
	}
	
	/**
	 * @see L2GameClientPacket#runImpl()
	 */
	@Override
	protected void runImpl() {
		final Player player = getClient().getActiveChar();
		if (player == null || player.getSellList() == null) {
			return;
		}
		
		if (msg != null && msg.length() > MAX_MSG_LENGTH) {
			Util.handleIllegalPlayerAction(player,
					"Player " + player.getName() + " tried to overflow private store whole message",
					Config.DEFAULT_PUNISH);
			return;
		}
		
		player.getSellList().setTitle(msg);
		sendPacket(new ExPrivateStoreSetWholeMsg(player));
	}
}
