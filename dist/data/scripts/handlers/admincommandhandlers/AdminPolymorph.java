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

package handlers.admincommandhandlers;

import l2server.gameserver.handler.IAdminCommandHandler;
import l2server.gameserver.instancemanager.TransformationManager;
import l2server.gameserver.model.WorldObject;
import l2server.gameserver.model.actor.Creature;
import l2server.gameserver.model.actor.instance.Player;
import l2server.gameserver.network.SystemMessageId;
import l2server.gameserver.network.serverpackets.MagicSkillUse;
import l2server.gameserver.network.serverpackets.SetupGauge;
import l2server.gameserver.network.serverpackets.SystemMessage;

import java.util.StringTokenizer;

/**
 * This class handles following admin commands: polymorph
 *
 * @version $Revision: 1.2.2.1.2.4 $ $Date: 2007/07/31 10:05:56 $
 */
public class AdminPolymorph implements IAdminCommandHandler {
	private static final String[] ADMIN_COMMANDS =
			{"admin_polymorph", "admin_unpolymorph", "admin_polymorph_menu", "admin_unpolymorph_menu", "admin_transform", "admin_untransform",
					"admin_transform_menu", "admin_untransform_menu",};

	@Override
	public boolean useAdminCommand(String command, Player activeChar) {
		if (activeChar.isMounted()) {
			activeChar.sendMessage("You can't transform while mounted, please dismount and try again.");
			return false;
		}

		if (command.startsWith("admin_untransform")) {
			WorldObject obj = activeChar.getTarget();
			if (obj instanceof Creature) {
				((Creature) obj).stopTransformation(true);
			} else {
				activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.INCORRECT_TARGET));
			}
		} else if (command.startsWith("admin_transform")) {
			WorldObject obj = activeChar.getTarget();
			if (obj instanceof Player) {
				Player cha = (Player) obj;

				String[] parts = command.split(" ");
				if (parts.length >= 2) {
					try {
						int id = Integer.parseInt(parts[1]);
						if (!TransformationManager.getInstance().transformPlayer(id, cha)) {
							cha.sendMessage("Unknow transformation id: " + id);
						}
					} catch (NumberFormatException e) {
						activeChar.sendMessage("Usage: //transform <id>");
					}
				} else if (parts.length == 1) {
					cha.unTransform(true);
				} else {
					activeChar.sendMessage("Usage: //transform <id>");
				}
			} else {
				activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.INCORRECT_TARGET));
			}
		}
		if (command.startsWith("admin_polymorph")) {
			StringTokenizer st = new StringTokenizer(command);
			WorldObject target = activeChar.getTarget();
			try {
				st.nextToken();
				String p1 = st.nextToken();
				if (st.hasMoreTokens()) {
					String p2 = st.nextToken();
					doPolymorph(activeChar, target, p2, p1);
				} else {
					doPolymorph(activeChar, target, p1, "npc");
				}
			} catch (Exception e) {
				activeChar.sendMessage("Usage: //polymorph [type] <id>");
			}
		} else if (command.equals("admin_unpolymorph")) {
			doUnpoly(activeChar, activeChar.getTarget());
		}
		if (command.contains("_menu")) {
			showMainPage(activeChar, command);
		}

		return true;
	}

	@Override
	public String[] getAdminCommandList() {
		return ADMIN_COMMANDS;
	}

	private void doPolymorph(Player activeChar, WorldObject obj, String id, String type) {
		if (obj != null) {
			obj.getPoly().setPolyInfo(type, id);
			//animation
			if (obj instanceof Creature) {
				Creature Char = (Creature) obj;
				MagicSkillUse msk = new MagicSkillUse(Char, 1008, 1, 4000, 0);
				Char.broadcastPacket(msk);
				SetupGauge sg = new SetupGauge(0, 4000);
				Char.sendPacket(sg);
			}
			//end of animation
			obj.decayMe();
			obj.spawnMe(obj.getX(), obj.getY(), obj.getZ());
			activeChar.sendMessage("Polymorph succeed");
		} else {
			activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.INCORRECT_TARGET));
		}
	}

	private void doUnpoly(Player activeChar, WorldObject target) {
		if (target != null) {
			target.getPoly().setPolyInfo(null, "1");
			target.decayMe();
			target.spawnMe(target.getX(), target.getY(), target.getZ());
			activeChar.sendMessage("Unpolymorph succeed");
		} else {
			activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.INCORRECT_TARGET));
		}
	}

	private void showMainPage(Player activeChar, String command) {
		if (command.contains("transform")) {
			AdminHelpPage.showHelpPage(activeChar, "transform.htm");
		} else if (command.contains("abnormal")) {
			AdminHelpPage.showHelpPage(activeChar, "abnormal.htm");
		} else {
			AdminHelpPage.showHelpPage(activeChar, "effects_menu.htm");
		}
	}
}
