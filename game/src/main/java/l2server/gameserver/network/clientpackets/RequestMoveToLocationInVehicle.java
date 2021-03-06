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

import l2server.gameserver.TaskPriority;
import l2server.gameserver.instancemanager.BoatManager;
import l2server.gameserver.model.actor.instance.BoatInstance;
import l2server.gameserver.model.actor.instance.Player;
import l2server.gameserver.network.SystemMessageId;
import l2server.gameserver.network.serverpackets.ActionFailed;
import l2server.gameserver.network.serverpackets.MoveToLocationInVehicle;
import l2server.gameserver.network.serverpackets.StopMoveInVehicle;
import l2server.gameserver.network.serverpackets.SystemMessage;
import l2server.gameserver.templates.item.WeaponType;
import l2server.util.Point3D;

public final class RequestMoveToLocationInVehicle extends L2GameClientPacket {
	
	private int boatId;
	private int targetX;
	private int targetY;
	private int targetZ;
	private int originX;
	private int originY;
	private int originZ;
	
	public TaskPriority getPriority() {
		return TaskPriority.PR_HIGH;
	}
	
	@Override
	protected void readImpl() {
		boatId = readD(); //objectId of boat
		targetX = readD();
		targetY = readD();
		targetZ = readD();
		originX = readD();
		originY = readD();
		originZ = readD();
	}
	
	/* (non-Javadoc)
	 * @see l2server.gameserver.clientpackets.ClientBasePacket#runImpl()
	 */
	@Override
	protected void runImpl() {
		final Player activeChar = getClient().getActiveChar();
		if (activeChar == null) {
			return;
		}
		
		if (targetX == originX && targetY == originY && targetZ == originZ) {
			activeChar.sendPacket(new StopMoveInVehicle(activeChar, boatId));
			return;
		}
		
		if (activeChar.isAttackingNow() && activeChar.getActiveWeaponItem() != null &&
				activeChar.getActiveWeaponItem().getItemType() == WeaponType.BOW) {
			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (activeChar.isSitting() || activeChar.isMovementDisabled()) {
			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (activeChar.getPet() != null) {
			activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.RELEASE_PET_ON_BOAT));
			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (activeChar.isTransformed()) {
			activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CANT_POLYMORPH_ON_BOAT));
			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		final BoatInstance boat;
		if (activeChar.isInBoat()) {
			boat = activeChar.getBoat();
			if (boat.getObjectId() != boatId) {
				activeChar.sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
		} else {
			boat = BoatManager.INSTANCE.getBoat(boatId);
			if (boat == null || !boat.isInsideRadius(activeChar, 300, true, false)) {
				activeChar.sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			activeChar.setVehicle(boat);
		}
		
		final Point3D pos = new Point3D(targetX, targetY, targetZ);
		final Point3D originPos = new Point3D(originX, originY, originZ);
		activeChar.setInVehiclePosition(pos);
		activeChar.broadcastPacket(new MoveToLocationInVehicle(activeChar, pos, originPos));
	}
}
