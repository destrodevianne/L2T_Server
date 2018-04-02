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

package l2server.gameserver.handler;

import l2server.gameserver.model.actor.instance.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class ...
 *
 * @version $Revision: 1.1.4.2 $ $Date: 2005/03/27 15:30:09 $
 */
public interface IAdminCommandHandler {
	Logger log = LoggerFactory.getLogger(IAdminCommandHandler.class.getName());
	
	/**
	 * this is the worker method that is called when someone uses an admin command.
	 *
	 * @return command success
	 */
	boolean useAdminCommand(String command, Player activeChar);

	/**
	 * this method is called at initialization to register all the item ids automatically
	 *
	 * @return all known itemIds
	 */
	String[] getAdminCommandList();
}
