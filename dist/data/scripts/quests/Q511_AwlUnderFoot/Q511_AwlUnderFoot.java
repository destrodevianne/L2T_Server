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

package quests.Q511_AwlUnderFoot;

import l2server.gameserver.ThreadPoolManager;
import l2server.gameserver.instancemanager.InstanceManager;
import l2server.gameserver.instancemanager.InstanceManager.InstanceWorld;
import l2server.gameserver.model.L2Party;
import l2server.gameserver.model.actor.Npc;
import l2server.gameserver.model.actor.Playable;
import l2server.gameserver.model.actor.instance.Player;
import l2server.gameserver.model.actor.instance.RaidBossInstance;
import l2server.gameserver.model.entity.Fort;
import l2server.gameserver.model.entity.Instance;
import l2server.gameserver.model.quest.Quest;
import l2server.gameserver.model.quest.QuestState;
import l2server.gameserver.model.quest.State;
import l2server.gameserver.network.SystemMessageId;
import l2server.gameserver.network.serverpackets.SystemMessage;
import l2server.gameserver.stats.SkillHolder;
import l2server.gameserver.util.Util;
import l2server.util.Rnd;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Gigiikun
 */
public final class Q511_AwlUnderFoot extends Quest {
	private class FAUWorld extends InstanceWorld {
	}

	public static class FortDungeon {
		private final int INSTANCEID;
		private long reEnterTime = 0;

		public FortDungeon(int iId) {
			INSTANCEID = iId;
		}

		public int getInstanceId() {
			return INSTANCEID;
		}

		public long getReEnterTime() {
			return reEnterTime;
		}

		public void setReEnterTime(long time) {
			reEnterTime = time;
		}
	}

	private static final String qn = "Q511_AwlUnderFoot";
	private static final boolean debug = false;
	private static final long REENTERTIME = 14400000;
	private static final long RAID_SPAWN_DELAY = 120000;

	private Map<Integer, FortDungeon> fortDungeons = new HashMap<Integer, FortDungeon>(21);

	// QUEST ITEMS
	private static final int DL_MARK = 9797;

	// REWARDS
	private static final int KNIGHT_EPALUETTE = 9912;

	// MONSTER TO KILL -- Only last 3 Raids (lvl ordered) give DL_MARK
	private static final int[] RAIDS1 = {25572, 25575, 25578};
	private static final int[] RAIDS2 = {25579, 25582, 25585, 25588};
	private static final int[] RAIDS3 = {25589, 25592, 25593};

	private static final SkillHolder RAID_CURSE = new SkillHolder(5456, 1);

	private String checkConditions(Player player) {
		if (debug) {
			return null;
		}
		L2Party party = player.getParty();
		if (party == null) {
			return "FortressWarden-03.htm";
		}
		if (party.getLeader() != player) {
			return getHtm(player.getHtmlPrefix(), "FortressWarden-04.htm").replace("%leader%", party.getLeader().getName());
		}
		for (Player partyMember : party.getPartyMembers()) {
			QuestState st = partyMember.getQuestState(qn);
			if (st == null || st.getInt("cond") < 1) {
				return getHtm(player.getHtmlPrefix(), "FortressWarden-05.htm").replace("%player%", partyMember.getName());
			}
			if (!Util.checkIfInRange(1000, player, partyMember, true)) {
				return getHtm(player.getHtmlPrefix(), "FortressWarden-06.htm").replace("%player%", partyMember.getName());
			}
		}
		return null;
	}

	private void teleportPlayer(Player player, int[] coords, int instanceId) {
		player.setInstanceId(instanceId);
		player.teleToLocation(coords[0], coords[1], coords[2]);
	}

	protected String enterInstance(Player player, String template, int[] coords, FortDungeon dungeon, String ret) {
		//check for existing instances for this player
		InstanceWorld world = InstanceManager.getInstance().getPlayerWorld(player);
		//existing instance
		if (world != null) {
			if (!(world instanceof FAUWorld)) {
				player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.ALREADY_ENTERED_ANOTHER_INSTANCE_CANT_ENTER));
				return "";
			}
			teleportPlayer(player, coords, world.instanceId);
			return "";
		}
		//New instance
		else {
			if (ret != null) {
				return ret;
			}
			ret = checkConditions(player);
			if (ret != null) {
				return ret;
			}
			L2Party party = player.getParty();
			int instanceId = InstanceManager.getInstance().createDynamicInstance(template);
			Instance ins = InstanceManager.getInstance().getInstance(instanceId);
			ins.setSpawnLoc(new int[]{player.getX(), player.getY(), player.getZ()});
			world = new FAUWorld();
			world.instanceId = instanceId;
			world.templateId = dungeon.getInstanceId();
			world.status = 0;
			dungeon.setReEnterTime(System.currentTimeMillis() + REENTERTIME);
			InstanceManager.getInstance().addWorld(world);
			log.info("Fortress AwlUnderFoot started " + template + " Instance: " + instanceId + " created by player: " + player.getName());
			ThreadPoolManager.getInstance().scheduleGeneral(new spawnRaid((FAUWorld) world), RAID_SPAWN_DELAY);

			// teleport players
			if (player.getParty() == null) {
				teleportPlayer(player, coords, instanceId);
				world.allowed.add(player.getObjectId());
			} else {
				for (Player partyMember : party.getPartyMembers()) {
					teleportPlayer(partyMember, coords, instanceId);
					world.allowed.add(partyMember.getObjectId());
					if (partyMember.getQuestState(qn) == null) {
						newQuestState(partyMember);
					}
				}
			}
			return getHtm(player.getHtmlPrefix(), "FortressWarden-08.htm").replace("%clan%", player.getClan().getName());
		}
	}

	private class spawnRaid implements Runnable {
		private FAUWorld world;

		public spawnRaid(FAUWorld world) {
			this.world = world;
		}

		@Override
		public void run() {
			try {
				int spawnId;
				if (world.status == 0) {
					spawnId = RAIDS1[Rnd.get(RAIDS1.length)];
				} else if (world.status == 1) {
					spawnId = RAIDS2[Rnd.get(RAIDS2.length)];
				} else {
					spawnId = RAIDS3[Rnd.get(RAIDS3.length)];
				}
				Npc raid = addSpawn(spawnId, 53319, 245814, -6576, 0, false, 0, false, world.instanceId);
				if (raid instanceof RaidBossInstance) {
					((RaidBossInstance) raid).setUseRaidCurse(false);
				}
			} catch (Exception e) {
				log.warn("Fortress AwlUnderFoot Raid Spawn error: " + e);
			}
		}
	}

	private String checkFortCondition(Player player, Npc npc, boolean isEnter) {
		Fort fortress = npc.getFort();
		FortDungeon dungeon = fortDungeons.get(npc.getNpcId());
		if (player == null || fortress == null || dungeon == null) {
			return "FortressWarden-01.htm";
		}
		if (player.getClan() == null || player.getClan().getHasFort() != fortress.getFortId()) {
			return "FortressWarden-01.htm";
		} else if (fortress.getFortState() == 0) {
			return "FortressWarden-02a.htm";
		} else if (fortress.getFortState() == 2) {
			return "FortressWarden-02b.htm";
		} else if (isEnter && dungeon.getReEnterTime() > System.currentTimeMillis()) {
			return "FortressWarden-07.htm";
		}

		return null;
	}

	private void rewardPlayer(Player player) {
		QuestState st = player.getQuestState(qn);
		if (st.getInt("cond") == 1) {
			st.giveItems(DL_MARK, 140);
			st.playSound("ItemSound.quest_itemget");
		}
	}

	@Override
	public String onAdvEvent(String event, Npc npc, Player player) {
		String htmltext = event;
		if (event.equalsIgnoreCase("enter")) {
			int[] tele = new int[3];
			tele[0] = 53322;
			tele[1] = 246380;
			tele[2] = -6580;
			return enterInstance(player, "fortdungeon.xml", tele, fortDungeons.get(npc.getNpcId()), checkFortCondition(player, npc, true));
		}
		QuestState st = player.getQuestState(qn);
		if (st == null) {
			st = newQuestState(player);
		}

		int cond = st.getInt("cond");
		if (event.equalsIgnoreCase("FortressWarden-10.htm")) {
			if (cond == 0) {
				st.set("cond", "1");
				st.setState(State.STARTED);
				st.playSound("ItemSound.quest_accept");
			}
		} else if (event.equalsIgnoreCase("FortressWarden-15.htm")) {
			st.playSound("ItemSound.quest_finish");
			st.exitQuest(true);
		}
		return htmltext;
	}

	@Override
	public String onTalk(Npc npc, Player player) {
		String htmltext = Quest.getNoQuestMsg(player);
		QuestState st = player.getQuestState(qn);
		String ret = checkFortCondition(player, npc, false);
		if (ret != null) {
			return ret;
		} else if (st != null) {
			int npcId = npc.getNpcId();
			int cond = 0;
			if (st.getState() == State.CREATED) {
				st.set("cond", "0");
			} else {
				cond = st.getInt("cond");
			}
			if (fortDungeons.containsKey(npcId) && cond == 0) {
				if (player.getLevel() >= 60) {
					htmltext = "FortressWarden-09.htm";
				} else {
					htmltext = "FortressWarden-00.htm";
					st.exitQuest(true);
				}
			} else if (fortDungeons.containsKey(npcId) && cond > 0 && st.getState() == State.STARTED) {
				long count = st.getQuestItemsCount(DL_MARK);
				if (cond == 1 && count > 0) {
					htmltext = "FortressWarden-14.htm";
					st.takeItems(DL_MARK, count);
					st.rewardItems(KNIGHT_EPALUETTE, count);
				} else if (cond == 1 && count == 0) {
					htmltext = "FortressWarden-10.htm";
				}
			}
		}
		return htmltext;
	}

	@Override
	public String onAttack(Npc npc, Player player, int damage, boolean isPet) {
		Playable attacker = isPet ? player.getPet() : player;
		if (attacker == null) {
			attacker = player.getSummon(0);
		}
		if (attacker.getLevel() - npc.getLevel() >= 9) {
			if (attacker.getBuffCount() > 0 || attacker.getDanceCount() > 0) {
				npc.setTarget(attacker);
				npc.doSimultaneousCast(RAID_CURSE.getSkill());
			} else if (player.getParty() != null) {
				for (Player pmember : player.getParty().getPartyMembers()) {
					if (pmember.getBuffCount() > 0 || pmember.getDanceCount() > 0) {
						npc.setTarget(pmember);
						npc.doSimultaneousCast(RAID_CURSE.getSkill());
					}
				}
			}
		}
		return super.onAttack(npc, player, damage, isPet);
	}

	@Override
	public String onKill(Npc npc, Player player, boolean isPet) {
		InstanceWorld tmpworld = InstanceManager.getInstance().getWorld(npc.getInstanceId());
		if (tmpworld instanceof FAUWorld) {
			FAUWorld world = (FAUWorld) tmpworld;
			if (Util.contains(RAIDS3, npc.getNpcId())) {
				if (player.getParty() != null) {
					for (Player pl : player.getParty().getPartyMembers()) {
						rewardPlayer(pl);
					}
				} else {
					rewardPlayer(player);
				}

				Instance instanceObj = InstanceManager.getInstance().getInstance(world.instanceId);
				instanceObj.setDuration(360000);
				instanceObj.removeNpcs();
			} else {
				world.status++;
				ThreadPoolManager.getInstance().scheduleGeneral(new spawnRaid(world), RAID_SPAWN_DELAY);
			}
		}
		return null;
	}

	public Q511_AwlUnderFoot(int questId, String name, String descr) {
		super(questId, name, descr);
		fortDungeons.put(35666, new FortDungeon(22));
		fortDungeons.put(35698, new FortDungeon(23));
		fortDungeons.put(35735, new FortDungeon(24));
		fortDungeons.put(35767, new FortDungeon(25));
		fortDungeons.put(35804, new FortDungeon(26));
		fortDungeons.put(35835, new FortDungeon(27));
		fortDungeons.put(35867, new FortDungeon(28));
		fortDungeons.put(35904, new FortDungeon(29));
		fortDungeons.put(35936, new FortDungeon(30));
		fortDungeons.put(35974, new FortDungeon(31));
		fortDungeons.put(36011, new FortDungeon(32));
		fortDungeons.put(36043, new FortDungeon(33));
		fortDungeons.put(36081, new FortDungeon(34));
		fortDungeons.put(36118, new FortDungeon(35));
		fortDungeons.put(36149, new FortDungeon(36));
		fortDungeons.put(36181, new FortDungeon(37));
		fortDungeons.put(36219, new FortDungeon(38));
		fortDungeons.put(36257, new FortDungeon(39));
		fortDungeons.put(36294, new FortDungeon(40));
		fortDungeons.put(36326, new FortDungeon(41));
		fortDungeons.put(36364, new FortDungeon(42));

		for (int i : fortDungeons.keySet()) {
			addStartNpc(i);
			addTalkId(i);
		}

		for (int i : RAIDS1) {
			addKillId(i);
		}
		for (int i : RAIDS2) {
			addKillId(i);
		}
		for (int i : RAIDS3) {
			addKillId(i);
		}

		for (int i = 25572; i <= 25595; i++) {
			addAttackId(i);
		}
	}

	public static void main(String[] args) {
		// now call the constructor (starts up the)
		new Q511_AwlUnderFoot(511, qn, "instances");
	}
}
