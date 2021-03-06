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

import l2server.DatabasePool;
import l2server.gameserver.datatables.SpawnTable;
import l2server.gameserver.model.L2Spawn;
import l2server.gameserver.model.actor.Npc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Pere
 */
public class SpawnDataManager {
	private static Logger log = LoggerFactory.getLogger(SpawnDataManager.class.getName());

	public class DbSpawnData {
		public long respawnTime;
		public int currentHp;
		public int currentMp;
	}
	
	private Map<String, DbSpawnData> dbSpawnData = new HashMap<>();
	
	public SpawnDataManager() {
		loadDbSpawnData();
	}
	
	public void loadDbSpawnData() {
		Connection con = null;
		PreparedStatement statement = null;
		try {
			con = DatabasePool.getInstance().getConnection();
			
			statement = con.prepareStatement("SELECT name, respawn_time, current_hp, current_mp FROM spawn_data");
			ResultSet rset = statement.executeQuery();
			while (rset.next()) {
				DbSpawnData dbsd = new DbSpawnData();
				dbsd.respawnTime = rset.getLong("respawn_time");
				dbsd.currentHp = rset.getInt("current_hp");
				dbsd.currentMp = rset.getInt("current_mp");
				
				dbSpawnData.put(rset.getString("name"), dbsd);
			}
			
			rset.close();
			statement.close();
			
			statement = con.prepareStatement("DELETE FROM spawn_data WHERE respawn_time < ?");
			statement.setLong(1, System.currentTimeMillis());
			statement.execute();
			statement.close();
		} catch (Exception e) {
			log.warn("Error while loading database spawn data: " + e.getMessage(), e);
		} finally {
			DatabasePool.close(con);
		}
	}
	
	public DbSpawnData popDbSpawnData(String dbName) {
		DbSpawnData dbsd = dbSpawnData.get(dbName);
		dbSpawnData.remove(dbName);
		return dbsd;
	}
	
	public void saveDbSpawnData() {
		for (L2Spawn spawn : SpawnTable.getInstance().getSpawnTable()) {
			if (spawn.getDbName() != null && !spawn.getDbName().isEmpty()) {
				updateDbSpawnData(spawn);
			}
		}
	}
	
	public void updateDbSpawnData(L2Spawn spawn) {
		Npc npc = spawn.getNpc();
		if (spawn.getNextRespawn() == 0 && npc.getCurrentHp() == npc.getMaxHp() && npc.getCurrentMp() == npc.getMaxMp() ||
				spawn.getX() == 0 && spawn.getY() == 0 && npc.getCurrentHp() == 0) {
			return;
		}
		
		Connection con = null;
		PreparedStatement statement = null;
		try {
			con = DatabasePool.getInstance().getConnection();
			statement = con.prepareStatement("REPLACE INTO spawn_data (name, respawn_time, current_hp, current_mp) VALUES (?, ?, ?, ?)");
			statement.setString(1, spawn.getDbName());
			statement.setLong(2, spawn.getNextRespawn());
			statement.setDouble(3, npc.getCurrentHp());
			statement.setDouble(4, npc.getCurrentMp());
			statement.executeUpdate();
			statement.close();
		} catch (SQLException e) {
			log.warn("SQL error while updating spawn to database: " + e.getMessage(), e);
		} finally {
			DatabasePool.close(con);
		}
	}
	
	public static SpawnDataManager getInstance() {
		return SingletonHolder.instance;
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder {
		protected static final SpawnDataManager instance = new SpawnDataManager();
	}
}
