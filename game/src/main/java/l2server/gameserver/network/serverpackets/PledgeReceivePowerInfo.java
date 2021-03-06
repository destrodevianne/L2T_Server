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

package l2server.gameserver.network.serverpackets;

import l2server.gameserver.model.L2ClanMember;

/**
 * Format : (ch) dSd
 *
 * @author -Wooden-
 */
public class PledgeReceivePowerInfo extends L2GameServerPacket {
	private L2ClanMember member;
	
	public PledgeReceivePowerInfo(L2ClanMember member) {
		this.member = member;
	}
	
	@Override
	protected final void writeImpl() {
		writeD(member.getPowerGrade()); //power grade
		writeS(member.getName());
		writeD(member.getClan().getRankPrivs(member.getPowerGrade())); //privileges
	}
}
