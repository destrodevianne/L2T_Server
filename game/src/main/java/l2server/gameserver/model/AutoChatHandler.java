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

package l2server.gameserver.model;

import l2server.Config;
import l2server.gameserver.ThreadPoolManager;
import l2server.gameserver.model.actor.Creature;
import l2server.gameserver.model.actor.Npc;
import l2server.gameserver.model.actor.instance.DefenderInstance;
import l2server.gameserver.model.actor.instance.Player;
import l2server.gameserver.network.clientpackets.Say2;
import l2server.gameserver.network.serverpackets.CreatureSay;
import l2server.util.Rnd;
import l2server.util.loader.annotations.Load;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

/**
 * Auto Chat Handler
 * <p>
 * Allows NPCs to automatically send messages to nearby players
 * at a set time interval.
 *
 * @author Tempy
 */
public class AutoChatHandler implements SpawnListener {
	private static Logger log = LoggerFactory.getLogger(AutoChatHandler.class.getName());

	private static final int DEFAULT_CHAT_DELAY = 60000; // 60 secs by default

	protected final Map<Integer, AutoChatInstance> registeredChats = new HashMap<>();

	private AutoChatHandler() {
	}
	
	@Load
	public void initialize() {
		L2Spawn.addSpawnListener(this);
		log.info("AutoChatHandler: Loaded " + AutoChatHandler.getInstance().size() + " handlers in total.");
	}

	public void reload() {
		// unregister all registered spawns
		// clear timer
		registeredChats.values().stream().filter(aci -> aci != null).forEachOrdered(aci -> {
			// clear timer
			if (aci.chatTask != null) {
				aci.chatTask.cancel(true);
			}
			this.removeChat(aci);
		});

		// create clean list
		registeredChats.clear();
	}

	public static AutoChatHandler getInstance() {
		return SingletonHolder.instance;
	}

	public int size() {
		return registeredChats.size();
	}

	/**
	 * Registers a globally active auto chat for ALL instances of the given NPC ID.
	 * <BR>
	 * Returns the associated auto chat instance.
	 *
	 * @return AutoChatInstance chatInst
	 */
	public AutoChatInstance registerGlobalChat(int npcId, String[] chatTexts, long chatDelay) {
		return registerChat(npcId, null, chatTexts, chatDelay);
	}

	/**
	 * Registers a NON globally-active auto chat for the given NPC instance, and adds to the currently
	 * assigned chat instance for this NPC ID, otherwise creates a new instance if
	 * a previous one is not found.
	 * <BR>
	 * Returns the associated auto chat instance.
	 *
	 * @return AutoChatInstance chatInst
	 */
	public AutoChatInstance registerChat(Npc npcInst, String[] chatTexts, long chatDelay) {
		return registerChat(npcInst.getNpcId(), npcInst, chatTexts, chatDelay);
	}

	private AutoChatInstance registerChat(int npcId, Npc npcInst, String[] chatTexts, long chatDelay) {
		AutoChatInstance chatInst = null;

		if (chatDelay < 0) {
			chatDelay = DEFAULT_CHAT_DELAY + Rnd.nextInt(DEFAULT_CHAT_DELAY);
		}

		if (registeredChats.containsKey(npcId)) {
			chatInst = registeredChats.get(npcId);
		} else {
			chatInst = new AutoChatInstance(npcId, chatTexts, chatDelay, npcInst == null);
		}

		if (npcInst != null) {
			chatInst.addChatDefinition(npcInst);
		}

		registeredChats.put(npcId, chatInst);

		return chatInst;
	}

	/**
	 * Removes and cancels ALL auto chat definition for the given NPC ID,
	 * and removes its chat instance if it exists.
	 *
	 * @return boolean removedSuccessfully
	 */
	public boolean removeChat(int npcId) {
		AutoChatInstance chatInst = registeredChats.get(npcId);
		return removeChat(chatInst);
	}

	/**
	 * Removes and cancels ALL auto chats for the given chat instance.
	 *
	 * @return boolean removedSuccessfully
	 */
	public boolean removeChat(AutoChatInstance chatInst) {
		if (chatInst == null) {
			return false;
		}

		registeredChats.remove(chatInst.getNPCId());
		chatInst.setActive(false);

		if (Config.DEBUG) {
			log.info("AutoChatHandler: Removed auto chat for NPC ID " + chatInst.getNPCId());
		}

		return true;
	}

	/**
	 * Returns the associated auto chat instance either by the given NPC ID
	 * or object ID.
	 *
	 * @return AutoChatInstance chatInst
	 */
	public AutoChatInstance getAutoChatInstance(int id, boolean byObjectId) {
		if (!byObjectId) {
			return registeredChats.get(id);
		} else {
			for (AutoChatInstance chatInst : registeredChats.values()) {
				if (chatInst.getChatDefinition(id) != null) {
					return chatInst;
				}
			}
		}

		return null;
	}

	/**
	 * Sets the active state of all auto chat instances to that specified,
	 * and cancels the scheduled chat task if necessary.
	 */
	public void setAutoChatActive(boolean isActive) {
		for (AutoChatInstance chatInst : registeredChats.values()) {
			chatInst.setActive(isActive);
		}
	}

	/**
	 * Used in conjunction with a SpawnListener, this method is called every time
	 * an NPC is spawned in the world.
	 * <BR><BR>
	 * If an auto chat instance is set to be "global", all instances matching the registered
	 * NPC ID will be added to that chat instance.
	 */
	@Override
	public void npcSpawned(Npc npc) {
		synchronized (registeredChats) {
			if (npc == null) {
				return;
			}

			int npcId = npc.getNpcId();

			if (registeredChats.containsKey(npcId)) {
				AutoChatInstance chatInst = registeredChats.get(npcId);

				if (chatInst != null && chatInst.isGlobal()) {
					chatInst.addChatDefinition(npc);
				}
			}
		}
	}

	/**
	 * Auto Chat Instance
	 * <BR><BR>
	 * Manages the auto chat instances for a specific registered NPC ID.
	 *
	 * @author Tempy
	 */
	public class AutoChatInstance {
		protected int npcId;
		private long defaultDelay = DEFAULT_CHAT_DELAY;
		private String[] defaultTexts;
		private boolean defaultRandom = false;

		private boolean globalChat = false;
		private boolean isActive;

		private Map<Integer, AutoChatDefinition> chatDefinitions = new HashMap<>();
		protected ScheduledFuture<?> chatTask;

		protected AutoChatInstance(int npcId, String[] chatTexts, long chatDelay, boolean isGlobal) {
			defaultTexts = chatTexts;
			this.npcId = npcId;
			defaultDelay = chatDelay;
			globalChat = isGlobal;

			if (Config.DEBUG) {
				log.info("AutoChatHandler: Registered auto chat for NPC ID " + npcId + " (Global Chat = " + globalChat + ").");
			}

			setActive(true);
		}

		protected AutoChatDefinition getChatDefinition(int objectId) {
			return chatDefinitions.get(objectId);
		}

		protected AutoChatDefinition[] getChatDefinitions() {
			return chatDefinitions.values().toArray(new AutoChatDefinition[chatDefinitions.values().size()]);
		}

		/**
		 * Defines an auto chat for an instance matching this auto chat instance's registered NPC ID,
		 * and launches the scheduled chat task.
		 * <BR>
		 * Returns the object ID for the NPC instance, with which to refer
		 * to the created chat definition.
		 * <BR>
		 * <B>Note</B>: Uses pre-defined default values for texts and chat delays from the chat instance.
		 *
		 * @return int objectId
		 */
		public int addChatDefinition(Npc npcInst) {
			return addChatDefinition(npcInst, null, 0);
		}

		/**
		 * Defines an auto chat for an instance matching this auto chat instance's registered NPC ID,
		 * and launches the scheduled chat task.
		 * <BR>
		 * Returns the object ID for the NPC instance, with which to refer
		 * to the created chat definition.
		 *
		 * @return int objectId
		 */
		public int addChatDefinition(Npc npcInst, String[] chatTexts, long chatDelay) {
			int objectId = npcInst.getObjectId();
			AutoChatDefinition chatDef = new AutoChatDefinition(this, npcInst, chatTexts, chatDelay);
			if (npcInst instanceof DefenderInstance) {
				chatDef.setRandomChat(true);
			}
			chatDefinitions.put(objectId, chatDef);
			return objectId;
		}

		/**
		 * Removes a chat definition specified by the given object ID.
		 *
		 * @return boolean removedSuccessfully
		 */
		public boolean removeChatDefinition(int objectId) {
			if (!chatDefinitions.containsKey(objectId)) {
				return false;
			}

			AutoChatDefinition chatDefinition = chatDefinitions.get(objectId);
			chatDefinition.setActive(false);

			chatDefinitions.remove(objectId);

			return true;
		}

		/**
		 * Tests if this auto chat instance is active.
		 *
		 * @return boolean isActive
		 */
		public boolean isActive() {
			return isActive;
		}

		/**
		 * Tests if this auto chat instance applies to
		 * ALL currently spawned instances of the registered NPC ID.
		 *
		 * @return boolean isGlobal
		 */
		public boolean isGlobal() {
			return globalChat;
		}

		/**
		 * Tests if random order is the DEFAULT for new chat definitions.
		 *
		 * @return boolean isRandom
		 */
		public boolean isDefaultRandom() {
			return defaultRandom;
		}

		/**
		 * Tests if the auto chat definition given by its object ID is set to be random.
		 *
		 * @return boolean isRandom
		 */
		public boolean isRandomChat(int objectId) {
			if (!chatDefinitions.containsKey(objectId)) {
				return false;
			}

			return chatDefinitions.get(objectId).isRandomChat();
		}

		/**
		 * Returns the ID of the NPC type managed by this auto chat instance.
		 *
		 * @return int npcId
		 */
		public int getNPCId() {
			return npcId;
		}

		/**
		 * Returns the number of auto chat definitions stored for this instance.
		 *
		 * @return int definitionCount
		 */
		public int getDefinitionCount() {
			return chatDefinitions.size();
		}

		/**
		 * Returns a list of all NPC instances handled by this auto chat instance.
		 *
		 * @return NpcInstance[] npcInsts
		 */
		public Npc[] getNPCInstanceList() {
			List<Npc> npcInsts = chatDefinitions.values().stream().map(chatDefinition -> chatDefinition.npcInstance).collect(Collectors.toList());

			return npcInsts.toArray(new Npc[npcInsts.size()]);
		}

		/**
		 * A series of methods used to get and set default values for new chat definitions.
		 */
		public long getDefaultDelay() {
			return defaultDelay;
		}

		public String[] getDefaultTexts() {
			return defaultTexts;
		}

		public void setDefaultChatDelay(long delayValue) {
			defaultDelay = delayValue;
		}

		public void setDefaultChatTexts(String[] textsValue) {
			defaultTexts = textsValue;
		}

		public void setDefaultRandom(boolean randValue) {
			defaultRandom = randValue;
		}

		/**
		 * Sets a specific chat delay for the specified auto chat definition given by its object ID.
		 */
		public void setChatDelay(int objectId, long delayValue) {
			AutoChatDefinition chatDef = getChatDefinition(objectId);

			if (chatDef != null) {
				chatDef.setChatDelay(delayValue);
			}
		}

		/**
		 * Sets a specific set of chat texts for the specified auto chat definition given by its object ID.
		 */
		public void setChatTexts(int objectId, String[] textsValue) {
			AutoChatDefinition chatDef = getChatDefinition(objectId);

			if (chatDef != null) {
				chatDef.setChatTexts(textsValue);
			}
		}

		/**
		 * Sets specifically to use random chat order for the auto chat definition given by its object ID.
		 */
		public void setRandomChat(int objectId, boolean randValue) {
			AutoChatDefinition chatDef = getChatDefinition(objectId);

			if (chatDef != null) {
				chatDef.setRandomChat(randValue);
			}
		}

		/**
		 * Sets the activity of ALL auto chat definitions handled by this chat instance.
		 */
		public void setActive(boolean activeValue) {
			if (isActive == activeValue) {
				return;
			}

			isActive = activeValue;

			if (!isGlobal()) {
				for (AutoChatDefinition chatDefinition : chatDefinitions.values()) {
					chatDefinition.setActive(activeValue);
				}

				return;
			}

			if (isActive()) {
				AutoChatRunner acr = new AutoChatRunner(npcId, -1);
				chatTask = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(acr, defaultDelay, defaultDelay);
			} else {
				chatTask.cancel(false);
			}
		}

		/**
		 * Auto Chat Definition
		 * <BR><BR>
		 * Stores information about specific chat data for an instance of the NPC ID
		 * specified by the containing auto chat instance.
		 * <BR>
		 * Each NPC instance of this type should be stored in a subsequent AutoChatDefinition class.
		 *
		 * @author Tempy
		 */
		private class AutoChatDefinition {
			protected int chatIndex = 0;
			protected Npc npcInstance;

			protected AutoChatInstance chatInstance;

			private long chatDelay = 0;
			private String[] chatTexts = null;
			private boolean isActiveDefinition;
			private boolean randomChat;

			protected AutoChatDefinition(AutoChatInstance chatInst, Npc npcInst, String[] chatTexts, long chatDelay) {
				npcInstance = npcInst;

				chatInstance = chatInst;
				randomChat = chatInst.isDefaultRandom();

				this.chatDelay = chatDelay;
				this.chatTexts = chatTexts;

				if (Config.DEBUG) {
					log.info("AutoChatHandler: Chat definition added for NPC ID " + npcInstance.getNpcId() + " (Object ID = " +
							npcInstance.getObjectId() + ").");
				}

				// If global chat isn't enabled for the parent instance,
				// then handle the chat task locally.
				if (!chatInst.isGlobal()) {
					setActive(true);
				}
			}

			protected String[] getChatTexts() {
				if (chatTexts != null) {
					return chatTexts;
				} else {
					return chatInstance.getDefaultTexts();
				}
			}

			private long getChatDelay() {
				if (chatDelay > 0) {
					return chatDelay;
				} else {
					return chatInstance.getDefaultDelay();
				}
			}

			private boolean isActive() {
				return isActiveDefinition;
			}

			boolean isRandomChat() {
				return randomChat;
			}

			void setRandomChat(boolean randValue) {
				randomChat = randValue;
			}

			void setChatDelay(long delayValue) {
				chatDelay = delayValue;
			}

			void setChatTexts(String[] textsValue) {
				chatTexts = textsValue;
			}

			void setActive(boolean activeValue) {
				if (isActive() == activeValue) {
					return;
				}

				if (activeValue) {
					AutoChatRunner acr = new AutoChatRunner(npcId, npcInstance.getObjectId());
					if (getChatDelay() == 0)
					// Schedule it set to 5Ms, isn't error, if use 0 sometine
					// chatDefinition return null in AutoChatRunner
					{
						chatTask = ThreadPoolManager.getInstance().scheduleGeneral(acr, 5);
					} else {
						chatTask = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(acr, getChatDelay(), getChatDelay());
					}
				} else {
					chatTask.cancel(false);
				}

				isActiveDefinition = activeValue;
			}
		}

		/**
		 * Auto Chat Runner
		 * <BR><BR>
		 * Represents the auto chat scheduled task for each chat instance.
		 *
		 * @author Tempy
		 */
		private class AutoChatRunner implements Runnable {
			private int runnerNpcId;
			private int objectId;

			protected AutoChatRunner(int pNpcId, int pObjectId) {
				runnerNpcId = pNpcId;
				objectId = pObjectId;
			}

			@Override
			public synchronized void run() {
				AutoChatInstance chatInst = registeredChats.get(runnerNpcId);
				AutoChatDefinition[] chatDefinitions;

				if (chatInst.isGlobal()) {
					chatDefinitions = chatInst.getChatDefinitions();
				} else {
					AutoChatDefinition chatDef = chatInst.getChatDefinition(objectId);

					if (chatDef == null) {
						log.warn("AutoChatHandler: Auto chat definition is NULL for NPC ID " + npcId + ".");
						return;
					}

					chatDefinitions = new AutoChatDefinition[]{chatDef};
				}

				if (Config.DEBUG) {
					log.info("AutoChatHandler: Running auto chat for " + chatDefinitions.length + " instances of NPC ID " + npcId + "." +
							" (Global Chat = " + chatInst.isGlobal() + ")");
				}

				for (AutoChatDefinition chatDef : chatDefinitions) {
					try {
						Npc chatNpc = chatDef.npcInstance;
						List<Player> nearbyPlayers = new ArrayList<>();
						List<Player> nearbyGMs = new ArrayList<>();

						for (Creature player : chatNpc.getKnownList().getKnownCharactersInRadius(1500)) {
							if (!(player instanceof Player)) {
								continue;
							}

							if (player.isGM()) {
								nearbyGMs.add((Player) player);
							} else {
								nearbyPlayers.add((Player) player);
							}
						}

						int maxIndex = chatDef.getChatTexts().length;
						int lastIndex = Rnd.nextInt(maxIndex);

						String creatureName = chatNpc.getName();
						String text;

						if (!chatDef.isRandomChat()) {
							lastIndex = chatDef.chatIndex + 1;

							if (lastIndex == maxIndex) {
								lastIndex = 0;
							}

							chatDef.chatIndex = lastIndex;
						}

						text = chatDef.getChatTexts()[lastIndex];

						if (text == null) {
							return;
						}

						if (!nearbyPlayers.isEmpty()) {
							int randomPlayerIndex = Rnd.nextInt(nearbyPlayers.size());

							Player randomPlayer = nearbyPlayers.get(randomPlayerIndex);

							if (text.contains("%player_random%")) {
								text = text.replaceAll("%player_random%", randomPlayer.getName());
							}
						}

						if (text == null) {
							return;
						}

						if (!text.contains("%player_")) {
							CreatureSay cs = new CreatureSay(chatNpc.getObjectId(), Say2.ALL_NOT_RECORDED, creatureName, text);

							for (Player nearbyPlayer : nearbyPlayers) {
								nearbyPlayer.sendPacket(cs);
							}
							for (Player nearbyGM : nearbyGMs) {
								nearbyGM.sendPacket(cs);
							}
						}

						if (Config.DEBUG) {
							log.debug("AutoChatHandler: Chat propogation for object ID " + chatNpc.getObjectId() + " (" + creatureName +
									") with text '" + text + "' sent to " + nearbyPlayers.size() + " nearby players.");
						}
					} catch (Exception e) {
						log.warn("Exception on AutoChatRunner.run(): " + e.getMessage(), e);
						return;
					}
				}
			}
		}
	}

	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder {
		protected static final AutoChatHandler instance = new AutoChatHandler();
	}
}
