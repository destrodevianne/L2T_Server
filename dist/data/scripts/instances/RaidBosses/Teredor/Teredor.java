package instances.RaidBosses.Teredor;

import ai.group_template.L2AttackableAIScript;
import l2server.Config;
import l2server.gameserver.ai.AttackableAI;
import l2server.gameserver.ai.CtrlIntention;
import l2server.gameserver.ai.NpcWalkerAI;
import l2server.gameserver.datatables.SkillTable;
import l2server.gameserver.instancemanager.InstanceManager;
import l2server.gameserver.instancemanager.InstanceManager.InstanceWorld;
import l2server.gameserver.model.L2NpcWalkerNode;
import l2server.gameserver.model.Location;
import l2server.gameserver.model.Skill;
import l2server.gameserver.model.World;
import l2server.gameserver.model.actor.Attackable;
import l2server.gameserver.model.actor.Creature;
import l2server.gameserver.model.actor.Npc;
import l2server.gameserver.model.actor.instance.Player;
import l2server.gameserver.model.entity.Instance;
import l2server.gameserver.network.SystemMessageId;
import l2server.gameserver.network.serverpackets.SystemMessage;
import l2server.gameserver.util.Util;
import l2server.util.Rnd;

import java.util.ArrayList;
import java.util.List;

/**
 * @author LasTravel
 * <p>
 * Terador Boss - Default mode
 * <p>
 * Source:
 * - http://www.youtube.com/watch?v=fFERpRhW52E
 * - http://www.youtube.com/watch?v=iNMNTe2L3qU
 * - http://www.lineage-realm.com/community/lineage-2-talk/quest-walkthroughs/Trajan-Instance-Guide-killing-teredor
 */

public class Teredor extends L2AttackableAIScript {
	//Quest
	private static final boolean debug = false;
	private static final String qn = "Teredor";

	//Id's
	private static final int templateID = 160;
	private static final int teredor = 25785;
	private static final int filaur = 30535;
	private static final int egg1 = 19023;
	private static final int egg2 = 18996;
	private static final int eliteMillipede = 19015;
	private static final int teredorTransparent1 = 18998;
	private static final int adventureGuildsman = 33385;
	private static final int[] eggMinions = {18993, 19016, 19000, 18995, 18994};
	private static final int[] allMobs = {18993, 19016, 19000, 18995, 18994, 19023, 25785, 18996, 19015, 19024};

	//Skills
	private static final Skill teredorFluInfection = SkillTable.getInstance().getInfo(14113, 1);

	//Spawns
	private static final int[] adventureSpawn = {177228, -186305, -3800, 339};

	//Others
	private static List<L2NpcWalkerNode> route = new ArrayList<L2NpcWalkerNode>();

	//Cords
	private static final Location[] playerEnter =
			{new Location(186933, -173534, -3878), new Location(186787, -173618, -3878), new Location(186907, -173708, -3878),
					new Location(187048, -173699, -3878), new Location(186998, -173579, -3878)};

	private static final int[][] walkRoutes =
			{{177127, -185282, -3804, 19828}, {177138, -184701, -3804, 16417}, {176616, -184448, -3796, 29126}, {176067, -184240, -3804, 28990},
					{176038, -184806, -3804, 48098}, {175494, -185097, -3804, 37891}, {175347, -185711, -3804, 46649},
					{175912, -186311, -3804, 57030}, {176449, -186195, -3804, 1787}};

	private class TeredorWorld extends InstanceWorld {
		private Npc Teredor;
		private boolean bossIsReady;
		private boolean bossIsInPause;
		private NpcWalkerAI teredorWalkAI;
		private AttackableAI teredorAttackAI;

		public TeredorWorld() {
			bossIsReady = true;
			bossIsInPause = true;
		}
	}

	public Teredor(int questId, String name, String descr) {
		super(questId, name, descr);

		addTalkId(adventureGuildsman);
		addTalkId(filaur);
		addStartNpc(filaur);
		addSpellFinishedId(teredor);
		addAggroRangeEnterId(egg1);
		addAggroRangeEnterId(egg2);
		addAggroRangeEnterId(teredorTransparent1);

		for (int id : allMobs) {
			addKillId(id);
			addAttackId(id);
		}

		for (int[] coord : walkRoutes) {
			route.add(new L2NpcWalkerNode(coord[0], coord[1], coord[2], 0, "", true));
		}
	}

	@Override
	public String onSpellFinished(Npc npc, Player player, Skill skill) {
		if (debug) {
			log.warn(getName() + ": onSpellFinished: " + skill.getName());
		}

		InstanceWorld wrld = null;
		if (npc != null) {
			wrld = InstanceManager.getInstance().getWorld(npc.getInstanceId());
		} else if (player != null) {
			wrld = InstanceManager.getInstance().getPlayerWorld(player);
		} else {
			log.warn(getName() + ": onAdvEvent: Unable to get world.");
			return null;
		}

		if (wrld != null && wrld instanceof TeredorWorld) {
			TeredorWorld world = (TeredorWorld) wrld;
			if (npc.getNpcId() == teredor && skill.getId() == 14112) //Teredor Poison
			{
				for (Player players : World.getInstance().getAllPlayers().values()) {
					if (players != null && players.getInstanceId() == world.instanceId) {
						addSpawn(teredorTransparent1, players.getX(), players.getY(), players.getZ(), 0, false, 0, true, world.instanceId);
					}
				}
			}
		}
		return super.onSpellFinished(npc, player, skill);
	}

	@Override
	public final String onAdvEvent(String event, Npc npc, Player player) {
		if (debug) {
			log.warn(getName() + ": onAdvEvent: " + event);
		}

		InstanceWorld wrld = null;
		if (npc != null) {
			wrld = InstanceManager.getInstance().getWorld(npc.getInstanceId());
		} else if (player != null) {
			wrld = InstanceManager.getInstance().getPlayerWorld(player);
		} else {
			log.warn(getName() + ": onAdvEvent: Unable to get world.");
			return null;
		}

		if (wrld != null && wrld instanceof TeredorWorld) {
			TeredorWorld world = (TeredorWorld) wrld;
			if (event.equalsIgnoreCase("stage_1_spawn_boss")) {
				world.Teredor = addSpawn(teredor, 177228, -186305, -3800, 59352, false, 0, false, world.instanceId);

				startBossWalk(world);
			} else if (event.equalsIgnoreCase("stage_all_attack_again")) {
				world.bossIsReady = true;
			} else if (event.equalsIgnoreCase("stage_all_egg")) {
				if (npc != null && !npc.isDead() && npc.getTarget() != null) {
					spawnMinions(world, npc, npc.getTarget().getActingPlayer(), eggMinions[Rnd.get(eggMinions.length)], 1);
				}
			}
		} else if (event.equalsIgnoreCase("enterInstance")) {
			try {
				enterInstance(player);
			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}
		return null;
	}

	@Override
	public final String onAttack(Npc npc, Player attacker, int damage, boolean isPet) {
		if (debug) {
			log.warn(getName() + ": onAttack: " + attacker.getName());
		}

		final InstanceWorld tmpWorld = InstanceManager.getInstance().getWorld(npc.getInstanceId());
		if (tmpWorld instanceof TeredorWorld) {
			final TeredorWorld world = (TeredorWorld) tmpWorld;
			if (npc.getNpcId() == teredor) {
				if (world.bossIsInPause && world.bossIsReady) {
					world.bossIsInPause = false;

					stopBossWalk(world);

					spawnMinions(world, npc, attacker, eliteMillipede, 3);
				} else if (!world.bossIsInPause && world.bossIsReady && (world.status == 0 && npc.getCurrentHp() < npc.getMaxHp() * 0.85 ||
						world.status == 1 && npc.getCurrentHp() < npc.getMaxHp() * 0.50)) {
					world.status++;

					world.bossIsInPause = true;

					world.bossIsReady = false;

					startBossWalk(world);

					startQuestTimer("stage_all_attack_again", 60000, npc, null);
				}
			}
		}
		return super.onAttack(npc, attacker, damage, isPet);
	}

	@Override
	public String onAggroRangeEnter(Npc npc, Player player, boolean isPet) {
		if (debug) {
			log.warn(getName() + ": onAggroRangeEnter: " + player.getName());
		}

		InstanceWorld tmpworld = InstanceManager.getInstance().getWorld(npc.getInstanceId());
		if (tmpworld instanceof TeredorWorld) {
			if ((npc.getNpcId() == egg1 || npc.getNpcId() == egg2) && npc.getDisplayEffect() == 0) {
				if (npc.getNpcId() == egg1) {
					npc.setDisplayEffect(3);
				} else if (npc.getNpcId() == egg2) {
					npc.setDisplayEffect(2);
				}

				npc.setTarget(player);

				//Custom but funny
				if (Rnd.get(100) > 30) {
					npc.doCast(teredorFluInfection);
				}

				startQuestTimer("stage_all_egg", 5000, npc, null); // 5sec?
			} else if (npc.getNpcId() == teredorTransparent1) {
				npc.setTarget(player);
				npc.doCast(teredorFluInfection);
			}
		}
		return super.onAggroRangeEnter(npc, player, isPet);
	}

	@Override
	public String onKill(Npc npc, Player player, boolean isPet) {
		if (debug) {
			log.warn(getName() + ": onKill: " + npc.getName());
		}

		InstanceWorld tmpworld = InstanceManager.getInstance().getWorld(npc.getInstanceId());
		if (tmpworld instanceof TeredorWorld) {
			TeredorWorld world = (TeredorWorld) tmpworld;
			if (npc.getNpcId() == teredor) {
				addSpawn(adventureGuildsman,
						adventureSpawn[0],
						adventureSpawn[1],
						adventureSpawn[2],
						adventureSpawn[3],
						false,
						0,
						false,
						world.instanceId);

				//Check if any char is moving if yes spawn random millipede's
				for (int objId : world.allowed) {
					Player target = World.getInstance().getPlayer(objId);

					if (target != null && target.isOnline() && target.getInstanceId() == world.instanceId && target.isMoving()) {
						spawnMinions(world, npc, target, eliteMillipede, Rnd.get(6));
						break;
					}
				}
				InstanceManager.getInstance().setInstanceReuse(world.instanceId, templateID, 5);
				InstanceManager.getInstance().finishInstance(world.instanceId, true);
			}
		}
		return super.onKill(npc, player, isPet);
	}

	@Override
	public final String onTalk(Npc npc, Player player) {
		if (debug) {
			log.warn(getName() + ": onTalk: " + player.getName());
		}

		if (npc.getNpcId() == filaur) {
			return "Filaur.html";
		} else if (npc.getNpcId() == adventureGuildsman) {
			player.setInstanceId(0);
			player.teleToLocation(85636, -142530, -1336, true);
		}
		return super.onTalk(npc, player);
	}

	private void stopBossWalk(TeredorWorld world) {
		world.Teredor.stopMove(null);
		world.teredorWalkAI.cancelTask();
		world.Teredor.setInvul(false);
		world.teredorAttackAI = new AttackableAI(world.Teredor);
		world.Teredor.setAI(world.teredorAttackAI);
	}

	private void startBossWalk(TeredorWorld world) {
		if (world.Teredor.isCastingNow()) {
			world.Teredor.abortCast();
		}

		world.Teredor.setInvul(true);
		world.Teredor.setRunning(true);
		world.teredorWalkAI = new NpcWalkerAI(world.Teredor);
		world.Teredor.setAI(world.teredorWalkAI);
		world.teredorWalkAI.initializeRoute(route, null);
		world.teredorWalkAI.walkToLocation();
	}

	private void spawnMinions(TeredorWorld world, Npc npc, Creature target, int npcId, int count) {
		if (!Util.checkIfInRange(700, npc, target, true)) {
			return;
		}

		for (int id = 0; id < count; id++) {
			Attackable minion =
					(Attackable) addSpawn(npcId, npc.getX(), npc.getY(), npc.getZ(), npc.getHeading(), false, 0, false, world.instanceId);
			minion.setRunning(true);

			if (target != null) {
				minion.addDamageHate(target, 0, 500);
				minion.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, target);
			}
		}
	}

	private final synchronized void enterInstance(Player player) {
		InstanceWorld world = InstanceManager.getInstance().getPlayerWorld(player);
		if (world != null) {
			if (!(world instanceof TeredorWorld)) {
				player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.ALREADY_ENTERED_ANOTHER_INSTANCE_CANT_ENTER));
				return;
			}

			Instance inst = InstanceManager.getInstance().getInstance(world.instanceId);
			if (inst != null) {
				if (inst.getInstanceEndTime() > 300600 && world.allowed.contains(player.getObjectId())) {
					player.setInstanceId(world.instanceId);
					player.teleToLocation(186933, -173534, -3878, true);
				}
			}
			return;
		} else {
			if (!debug && !InstanceManager.getInstance().checkInstanceConditions(player, templateID, Config.TEREDOR_MIN_PLAYERS, 7, 81, 95)) {
				return;
			}

			final int instanceId = InstanceManager.getInstance().createDynamicInstance(qn + ".xml");
			world = new TeredorWorld();
			world.instanceId = instanceId;
			world.templateId = templateID;
			world.status = 0;

			InstanceManager.getInstance().addWorld(world);

			List<Player> allPlayers = new ArrayList<Player>();
			if (debug) {
				allPlayers.add(player);
			} else {
				allPlayers.addAll(player.getParty().getPartyMembers());
			}

			for (Player enterPlayer : allPlayers) {
				if (enterPlayer == null) {
					continue;
				}

				world.allowed.add(enterPlayer.getObjectId());

				enterPlayer.stopAllEffectsExceptThoseThatLastThroughDeath();
				enterPlayer.setInstanceId(instanceId);
				enterPlayer.teleToLocation(playerEnter[Rnd.get(0, playerEnter.length - 1)], true);
			}

			startQuestTimer("stage_1_spawn_boss", 5000, null, player);

			log.debug(getName() + ": [" + templateID + "] instance started: " + instanceId + " created by player: " + player.getName());
			return;
		}
	}

	public static void main(String[] args) {
		new Teredor(-1, qn, "instances/RaidBosses");
	}
}
