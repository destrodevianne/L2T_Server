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

package l2server.gameserver.model.actor.instance;

import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntLongHashMap;
import gov.nasa.worldwind.formats.dds.DDSConverter;
import l2server.Config;
import l2server.L2DatabaseFactory;
import l2server.gameserver.*;
import l2server.gameserver.ai.CreatureAI;
import l2server.gameserver.ai.CtrlIntention;
import l2server.gameserver.ai.PlayerAI;
import l2server.gameserver.ai.SummonAI;
import l2server.gameserver.cache.HtmCache;
import l2server.gameserver.cache.WarehouseCacheManager;
import l2server.gameserver.communitybbs.BB.Forum;
import l2server.gameserver.communitybbs.Manager.ForumsBBSManager;
import l2server.gameserver.datatables.*;
import l2server.gameserver.events.Curfew;
import l2server.gameserver.events.RankingKillInfo;
import l2server.gameserver.events.chess.ChessEvent;
import l2server.gameserver.events.chess.ChessEvent.ChessState;
import l2server.gameserver.events.instanced.EventInstance;
import l2server.gameserver.events.instanced.EventInstance.EventState;
import l2server.gameserver.events.instanced.EventInstance.EventType;
import l2server.gameserver.events.instanced.EventTeam;
import l2server.gameserver.events.instanced.EventTeleporter;
import l2server.gameserver.events.instanced.EventsManager;
import l2server.gameserver.events.instanced.types.StalkedStalkers;
import l2server.gameserver.handler.IItemHandler;
import l2server.gameserver.handler.ISkillHandler;
import l2server.gameserver.handler.ItemHandler;
import l2server.gameserver.handler.SkillHandler;
import l2server.gameserver.idfactory.IdFactory;
import l2server.gameserver.instancemanager.*;
import l2server.gameserver.instancemanager.HandysBlockCheckerManager.ArenaParticipantsHolder;
import l2server.gameserver.instancemanager.MainTownManager.MainTownInfo;
import l2server.gameserver.instancemanager.arena.Fight;
import l2server.gameserver.instancemanager.arena.Fighter;
import l2server.gameserver.model.*;
import l2server.gameserver.model.Item;
import l2server.gameserver.model.L2FlyMove.L2FlyMoveChoose;
import l2server.gameserver.model.L2FlyMove.L2FlyMoveOption;
import l2server.gameserver.model.L2Party.messageType;
import l2server.gameserver.model.actor.*;
import l2server.gameserver.model.actor.appearance.PcAppearance;
import l2server.gameserver.model.actor.knownlist.PcKnownList;
import l2server.gameserver.model.actor.position.PcPosition;
import l2server.gameserver.model.actor.stat.PcStat;
import l2server.gameserver.model.actor.status.PcStatus;
import l2server.gameserver.model.base.Experience;
import l2server.gameserver.model.base.PlayerClass;
import l2server.gameserver.model.base.Race;
import l2server.gameserver.model.base.SubClass;
import l2server.gameserver.model.entity.*;
import l2server.gameserver.model.entity.ClanWarManager.ClanWar;
import l2server.gameserver.model.entity.ClanWarManager.ClanWar.WarState;
import l2server.gameserver.model.itemcontainer.*;
import l2server.gameserver.model.multisell.PreparedListContainer;
import l2server.gameserver.model.olympiad.*;
import l2server.gameserver.model.quest.GlobalQuest;
import l2server.gameserver.model.quest.Quest;
import l2server.gameserver.model.quest.QuestState;
import l2server.gameserver.model.quest.State;
import l2server.gameserver.model.zone.ZoneType;
import l2server.gameserver.model.zone.type.BossZone;
import l2server.gameserver.model.zone.type.NoRestartZone;
import l2server.gameserver.model.zone.type.TownZone;
import l2server.gameserver.network.L2GameClient;
import l2server.gameserver.network.SystemMessageId;
import l2server.gameserver.network.clientpackets.Say2;
import l2server.gameserver.network.serverpackets.*;
import l2server.gameserver.network.serverpackets.StatusUpdate.StatusUpdateDisplay;
import l2server.gameserver.stats.*;
import l2server.gameserver.stats.funcs.*;
import l2server.gameserver.stats.skills.SkillSiegeFlag;
import l2server.gameserver.stats.skills.SkillSummon;
import l2server.gameserver.stats.skills.SkillTrap;
import l2server.gameserver.taskmanager.AttackStanceTaskManager;
import l2server.gameserver.templates.chars.NpcTemplate;
import l2server.gameserver.templates.chars.PcTemplate;
import l2server.gameserver.templates.item.*;
import l2server.gameserver.templates.skills.*;
import l2server.gameserver.util.Broadcast;
import l2server.gameserver.util.FloodProtectors;
import l2server.gameserver.util.IllegalPlayerAction;
import l2server.gameserver.util.Util;
import l2server.util.Point3D;
import l2server.util.Rnd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class represents all player characters in the world.
 * There is always a client-thread connected to this (except if a player-store is activated upon logout).<BR><BR>
 *
 * @version $Revision: 1.66.2.41.2.33 $ $Date: 2005/04/11 10:06:09 $
 */
public class Player extends Playable {
	private static Logger log = LoggerFactory.getLogger(Player.class.getName());


	// Character Skill SQL String Definitions:
	private static final String RESTORE_SKILLS_FOR_CHAR = "SELECT skill_id,skill_level FROM character_skills WHERE charId=? AND class_index=?";
	private static final String ADD_NEW_SKILL = "REPLACE INTO character_skills (charId,skill_id,skill_level,class_index) VALUES (?,?,?,?)";
	private static final String UPDATE_CHARACTER_SKILL_LEVEL =
			"UPDATE character_skills SET skill_level=? WHERE skill_id=? AND charId=? AND class_index=?";
	private static final String DELETE_SKILL_FROM_CHAR = "DELETE FROM character_skills WHERE skill_id=? AND charId=? AND class_index=?";
	private static final String DELETE_CHAR_SKILLS = "DELETE FROM character_skills WHERE charId=? AND class_index=?";
	
	// Character Skill Save SQL String Definitions:
	private static final String ADD_SKILL_SAVE =
			"INSERT INTO character_skills_save (charId,skill_id,skill_level,effect_count,effect_cur_time,reuse_delay,systime,restore_type,class_index,buff_index) VALUES (?,?,?,?,?,?,?,?,?,?)";
	private static final String RESTORE_SKILL_SAVE =
			"SELECT skill_id,skill_level,effect_count,effect_cur_time, reuse_delay, systime, restore_type FROM character_skills_save WHERE charId=? AND class_index=? ORDER BY buff_index ASC";
	private static final String DELETE_SKILL_SAVE = "DELETE FROM character_skills_save WHERE charId=? AND class_index=?";
	
	// Character Character SQL String Definitions:
	private static final String INSERT_CHARACTER =
			"INSERT INTO characters (account_name,charId,char_name,level,maxHp,curHp,maxCp,curCp,maxMp,curMp,face,hairStyle,hairColor,sex,exp,sp,reputation,fame,pvpkills,pkkills,clanid,templateId,classid,deletetime,cancraft,title,title_color,accesslevel,online,clan_privs,wantspeace,base_class,newbie,nobless,power_grade,createTime) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
	private static final String UPDATE_CHARACTER =
			"UPDATE characters SET level=?,temporaryLevel=?,maxHp=?,curHp=?,maxCp=?,curCp=?,maxMp=?,curMp=?,face=?,hairStyle=?,hairColor=?,sex=?,heading=?,x=?,y=?,z=?,exp=?,expBeforeDeath=?,sp=?,reputation=?,fame=?,pvpkills=?,pkkills=?,clanid=?,templateId=?,classid=?,deletetime=?,title=?,title_color=?,accesslevel=?,online=?,clan_privs=?,wantspeace=?,base_class=?,onlinetime=?,punish_level=?,punish_timer=?,newbie=?,nobless=?,power_grade=?,subpledge=?,lvl_joined_academy=?,apprentice=?,sponsor=?,varka_ketra_ally=?,clan_join_expiry_time=?,clan_create_expiry_time=?,char_name=?,bookmarkslot=?,show_hat=?,race_app=? WHERE charId=?";
	private static final String RESTORE_CHARACTER =
			"SELECT account_name, charId, char_name, level, temporaryLevel, curHp, curCp, curMp, face, hairStyle, hairColor, sex, heading, x, y, z, exp, expBeforeDeath, sp, reputation, fame, pvpkills, pkkills, clanid, templateId, classid, deletetime, cancraft, title, title_color, accesslevel, online, char_slot, lastAccess, clan_privs, wantspeace, base_class, onlinetime, punish_level, punish_timer, newbie, nobless, power_grade, subpledge, lvl_joined_academy, apprentice, sponsor, varka_ketra_ally,clan_join_expiry_time,clan_create_expiry_time,bookmarkslot,createTime,show_hat,race_app FROM characters WHERE charId=?";
	
	// Character Teleport Bookmark:
	private static final String INSERT_TP_BOOKMARK = "INSERT INTO character_tpbookmark (charId,Id,x,y,z,icon,tag,name) VALUES (?,?,?,?,?,?,?,?)";
	private static final String UPDATE_TP_BOOKMARK = "UPDATE character_tpbookmark SET icon=?,tag=?,name=? WHERE charId=? AND Id=?";
	private static final String RESTORE_TP_BOOKMARK = "SELECT Id,x,y,z,icon,tag,name FROM character_tpbookmark WHERE charId=?";
	private static final String DELETE_TP_BOOKMARK = "DELETE FROM character_tpbookmark WHERE charId=? AND Id=?";
	
	// Character Subclass SQL String Definitions:
	private static final String RESTORE_CHAR_SUBCLASSES =
			"SELECT class_id,exp,sp,level,class_index,is_dual,certificates FROM character_subclasses WHERE charId=? ORDER BY class_index ASC";
	private static final String ADD_CHAR_SUBCLASS =
			"INSERT INTO character_subclasses (charId,class_id,exp,sp,level,class_index,certificates) VALUES (?,?,?,?,?,?,?)";
	private static final String UPDATE_CHAR_SUBCLASS =
			"UPDATE character_subclasses SET exp=?,sp=?,level=?,class_id=?,is_dual=?,certificates=? WHERE charId=? AND class_index =?";
	private static final String DELETE_CHAR_SUBCLASS = "DELETE FROM character_subclasses WHERE charId=? AND class_index=?";
	
	// Character Henna SQL String Definitions:
	private static final String RESTORE_CHAR_HENNAS = "SELECT slot,symbol_id,expiry_time FROM character_hennas WHERE charId=? AND class_index=?";
	private static final String ADD_CHAR_HENNA = "INSERT INTO character_hennas (charId,symbol_id,slot,class_index,expiry_time) VALUES (?,?,?,?,?)";
	private static final String DELETE_CHAR_HENNA = "DELETE FROM character_hennas WHERE charId=? AND slot=? AND class_index=?";
	private static final String DELETE_CHAR_HENNAS = "DELETE FROM character_hennas WHERE charId=? AND class_index=?";
	
	// Character Shortcut SQL String Definitions:
	private static final String DELETE_CHAR_SHORTCUTS = "DELETE FROM character_shortcuts WHERE charId=? AND class_index=?";
	
	// Character Transformation SQL String Definitions:
	private static final String SELECT_CHAR_TRANSFORM = "SELECT transform_id FROM characters WHERE charId=?";
	private static final String UPDATE_CHAR_TRANSFORM = "UPDATE characters SET transform_id=? WHERE charId=?";
	
	// Character zone restart time SQL String Definitions - L2Master mod
	private static final String DELETE_ZONE_RESTART_LIMIT = "DELETE FROM character_norestart_zone_time WHERE charId = ?";
	private static final String LOAD_ZONE_RESTART_LIMIT = "SELECT time_limit FROM character_norestart_zone_time WHERE charId = ?";
	private static final String UPDATE_ZONE_RESTART_LIMIT = "REPLACE INTO character_norestart_zone_time (charId, time_limit) VALUES (?,?)";
	
	// Character account data SQL String Definitions:
	private static final String UPDATE_ACCOUNT_GSDATA = "UPDATE account_gsdata SET value=? WHERE account_name=? AND var=?";
	private static final String RESTORE_ACCOUNT_GSDATA = "SELECT value FROM account_gsdata WHERE account_name=? AND var=?;";
	
	public static final int REQUEST_TIMEOUT = 15;
	public static final int STORE_PRIVATE_NONE = 0;
	public static final int STORE_PRIVATE_SELL = 1;
	public static final int STORE_PRIVATE_BUY = 3;
	public static final int STORE_PRIVATE_MANUFACTURE = 5;
	public static final int STORE_PRIVATE_PACKAGE_SELL = 8;
	public static final int STORE_PRIVATE_CUSTOM_SELL = 10;
	
	/**
	 * The table containing all minimum level needed for each Expertise (None, D, C, B, A, S, S80, S84)
	 */
	private static final int[] EXPERTISE_LEVELS = {SkillTreeTable.getInstance().getExpertiseLevel(0), //NONE
			SkillTreeTable.getInstance().getExpertiseLevel(1), //D
			SkillTreeTable.getInstance().getExpertiseLevel(2), //C
			SkillTreeTable.getInstance().getExpertiseLevel(3), //B
			SkillTreeTable.getInstance().getExpertiseLevel(4), //A
			SkillTreeTable.getInstance().getExpertiseLevel(5), //S
			SkillTreeTable.getInstance().getExpertiseLevel(6), //S80
			SkillTreeTable.getInstance().getExpertiseLevel(7), //S84
			SkillTreeTable.getInstance().getExpertiseLevel(8), //R
			SkillTreeTable.getInstance().getExpertiseLevel(9), //R90
			SkillTreeTable.getInstance().getExpertiseLevel(10) //R99
	};
	
	private static final int[] COMMON_CRAFT_LEVELS = {5, 20, 28, 36, 43, 49, 55, 62, 70};
	
	private L2GameClient client;
	
	private String accountName;
	private long deleteTimer;
	private long creationTime;
	
	private volatile boolean isOnline = false;
	private long onlineTime;
	private long onlineBeginTime;
	private long lastAccess;
	private long uptime;
	private long zoneRestartLimitTime = 0;
	
	private final ReentrantLock subclassLock = new ReentrantLock();
	protected int templateId;
	protected int baseClass;
	protected int activeClass;
	protected int classIndex = 0;
	private PlayerClass currentClass;
	
	/**
	 * data for mounted pets
	 */
	private int controlItemId;
	private L2PetData data;
	private L2PetLevelData leveldata;
	private int curFeed;
	protected Future<?> mountFeedTask;
	private ScheduledFuture<?> dismountTask;
	private boolean petItems = false;
	
	/**
	 * The list of sub-classes this character has.
	 */
	private Map<Integer, SubClass> subClasses;
	
	private PcAppearance appearance;
	
	/**
	 * The Identifier of the Player
	 */
	private int charId = 0x00030b7a;
	
	/**
	 * The Experience of the Player before the last Death Penalty
	 */
	private long expBeforeDeath;
	
	/**
	 * The Reputation of the Player (if lower than 0, the name of the Player appears in red, otherwise if greater the name appears in red)
	 */
	private int reputation;
	
	/**
	 * The number of player killed during a PvP (the player killed was PvP Flagged)
	 */
	private int pvpKills;
	
	/**
	 * The PK counter of the Player (= Number of non PvP Flagged player killed)
	 */
	private int pkKills;
	
	/**
	 * The PvP Flag state of the Player (0=White, 1=Purple)
	 */
	private byte pvpFlag;
	
	/**
	 * The Fame of this Player
	 */
	private int fame;
	private ScheduledFuture<?> fameTask;
	
	private ScheduledFuture<?> teleportWatchdog;
	
	/**
	 * The Siege state of the Player
	 */
	private byte siegeState = 0;
	
	/**
	 * The id of castle/fort which the Player is registered for siege
	 */
	private int siegeSide = 0;
	
	private int curWeightPenalty = 0;
	
	private int lastCompassZone; // the last compass zone update send to the client
	
	private final L2ContactList contactList = new L2ContactList(this);
	
	private int bookmarkslot = 0; // The Teleport Bookmark Slot
	
	private List<TeleportBookmark> tpbookmark = new ArrayList<>();
	
	private PunishLevel punishLevel = PunishLevel.NONE;
	private long punishTimer = 0;
	private ScheduledFuture<?> punishTask;
	
	public enum PunishLevel {
		NONE(0, ""),
		CHAT(1, "chat banned"),
		JAIL(2, "jailed"),
		CHAR(3, "banned"),
		ACC(4, "banned");
		
		private final int punValue;
		private final String punString;
		
		PunishLevel(int value, String string) {
			punValue = value;
			punString = string;
		}
		
		public int value() {
			return punValue;
		}
		
		public String string() {
			return punString;
		}
	}
	
	/**
	 * Olympiad
	 */
	private boolean inOlympiadMode = false;
	private boolean OlympiadStart = false;
	private int olympiadGameId = -1;
	private int olympiadSide = -1;
	public int olyBuff = 0;
	
	/**
	 * Duel
	 */
	private boolean isInDuel = false;
	private int duelState = Duel.DUELSTATE_NODUEL;
	private int duelId = 0;
	private SystemMessageId noDuelReason = SystemMessageId.THERE_IS_NO_OPPONENT_TO_RECEIVE_YOUR_CHALLENGE_FOR_A_DUEL;
	
	/**
	 * Boat and AirShip
	 */
	private Vehicle vehicle = null;
	private Point3D inVehiclePosition;
	
	public ScheduledFuture<?> taskforfish;
	private int mountType;
	private int mountNpcId;
	private int mountLevel;
	/**
	 * Store object used to summon the strider you are mounting
	 **/
	private int mountObjectID = 0;
	
	public int telemode = 0;
	
	private boolean inCrystallize;
	private boolean inCraftMode;
	
	private long offlineShopStart = 0;
	
	private L2Transformation transformation;
	private int transformationId = 0;
	
	/**
	 * The table containing all L2RecipeList of the Player
	 */
	private Map<Integer, L2RecipeList> dwarvenRecipeBook = new HashMap<>();
	private Map<Integer, L2RecipeList> commonRecipeBook = new HashMap<>();
	
	/**
	 * Premium Items
	 */
	private Map<Integer, L2PremiumItem> premiumItems = new HashMap<>();
	
	/**
	 * True if the Player is sitting
	 */
	private boolean waitTypeSitting;
	
	/**
	 * Location before entering Observer Mode
	 */
	private int lastX;
	private int lastY;
	private int lastZ;
	private boolean observerMode = false;
	
	/**
	 * Stored from last ValidatePosition
	 **/
	private Point3D lastServerPosition = new Point3D(0, 0, 0);
	
	/**
	 * The number of recommendation obtained by the Player
	 */
	private int recomHave; // how much I was recommended by others
	/**
	 * The number of recommendation that the Player can give
	 */
	private int recomLeft; // how many recommendations I can give to others
	/**
	 * Recommendation Bonus task
	 **/
	private ScheduledFuture<?> recoBonusTask;
	/**
	 * Recommendation task
	 **/
	private ScheduledFuture<?> recoGiveTask;
	/**
	 * Recommendation Two Hours bonus
	 **/
	private boolean recoTwoHoursGiven = false;
	
	private PcInventory inventory = new PcInventory(this);
	private final PcAuction auctionInventory = new PcAuction(this);
	private PcWarehouse warehouse;
	private PcRefund refund;
	
	private PetInventory petInv;
	
	/**
	 * The Private Store type of the Player (STORE_PRIVATE_NONE=0, STORE_PRIVATE_SELL=1, sellmanage=2, STORE_PRIVATE_BUY=3, buymanage=4, STORE_PRIVATE_MANUFACTURE=5)
	 */
	private int privatestore;
	
	private TradeList activeTradeList;
	private ItemContainer activeWarehouse;
	private L2ManufactureList createList;
	private TradeList sellList;
	private TradeList buyList;
	
	// Multisell
	private PreparedListContainer currentMultiSell = null;
	
	/**
	 * Bitmask used to keep track of one-time/newbie quest rewards
	 */
	private int newbie;
	
	private boolean noble = false;
	private boolean hero = false;
	private boolean isCoCWinner = false;
	
	private boolean hasIdentityCrisis = false;
	
	public boolean hasIdentityCrisis() {
		return hasIdentityCrisis;
	}
	
	public void setHasIdentityCrisis(boolean hasIdentityCrisis) {
		this.hasIdentityCrisis = hasIdentityCrisis;
	}
	
	/**
	 * The L2FolkInstance corresponding to the last Folk wich one the player talked.
	 */
	private Npc lastFolkNpc = null;
	
	/**
	 * Last NPC Id talked on a quest
	 */
	private int questNpcObject = 0;
	
	/**
	 * The table containing all Quests began by the Player
	 */
	private Map<String, QuestState> quests = new HashMap<>();
	
	/**
	 * The list containing all shortCuts of this Player
	 */
	private ShortCuts shortCuts = new ShortCuts(this);
	
	/**
	 * The list containing all macroses of this Player
	 */
	private MacroList macroses = new MacroList(this);
	
	private List<Player> snoopListener = new ArrayList<>();
	private List<Player> snoopedPlayer = new ArrayList<>();
	
	/**
	 * The player stores who is observing his land rates
	 */
	private ArrayList<Player> landrateObserver = new ArrayList<>();
	/**
	 * The GM stores player references whose land rates he observes
	 */
	private ArrayList<Player> playersUnderLandrateObservation = new ArrayList<>();
	/**
	 * This flag shows land rate observation activity. Means - either observing or under observation
	 */
	private boolean landrateObservationActive = false;
	
	// hennas
	private final HennaTemplate[] henna = new HennaTemplate[4];
	private int hennaSTR;
	private int hennaINT;
	private int hennaDEX;
	private int hennaMEN;
	private int hennaWIT;
	private int hennaCON;
	private int hennaLUC;
	private int hennaCHA;
	private int[] hennaElem = new int[6];
	
	/**
	 * The pet of the Player
	 */
	private PetInstance pet = null;
	private List<SummonInstance> summons = new CopyOnWriteArrayList<>();
	private SummonInstance activeSummon = null;
	private boolean summonsInDefendingMode = false;
	/**
	 * The buff set of a died servitor
	 */
	private Abnormal[] summonBuffs = null;
	/**
	 * NPC id of died servitor
	 */
	private int lastSummonId = 0;
	/**
	 * The DecoyInstance of the Player
	 */
	private DecoyInstance decoy = null;
	/**
	 * The Trap of the Player
	 */
	private Trap trap = null;
	/**
	 * The L2Agathion of the Player
	 */
	private int agathionId = 0;
	// apparently, a Player CAN have both a summon AND a tamed beast at the same time!!
	// after Freya players can control more than one tamed beast
	private List<TamedBeastInstance> tamedBeast = null;
	
	// client radar
	//TODO: This needs to be better intergrated and saved/loaded
	private L2Radar radar;
	
	// Party matching
	// private int partymatching = 0;
	private int partyroom = 0;
	// private int partywait = 0;
	
	// Clan related attributes
	/**
	 * The Clan Identifier of the Player
	 */
	private int clanId;
	
	/**
	 * The Clan object of the Player
	 */
	private L2Clan clan;
	
	/**
	 * Apprentice and Sponsor IDs
	 */
	private int apprentice = 0;
	private int sponsor = 0;
	
	private long clanJoinExpiryTime;
	private long clanCreateExpiryTime;
	
	private int powerGrade = 0;
	private int clanPrivileges = 0;
	
	/**
	 * Player's pledge class (knight, Baron, etc.)
	 */
	private int pledgeClass = 0;
	private int pledgeType = 0;
	
	/**
	 * Level at which the player joined the clan as an academy member
	 */
	private int lvlJoinedAcademy = 0;
	
	private int wantsPeace = 0;
	
	// Breath of Shilen Debuff Level (Works as the new Death Penalty system)
	private int breathOfShilenDebuffLevel = 0;
	
	// charges
	private AtomicInteger charges = new AtomicInteger();
	private ScheduledFuture<?> chargeTask = null;
	
	// Absorbed Souls
	private int souls = 0;
	private ScheduledFuture<?> soulTask = null;
	
	private L2AccessLevel accessLevel;
	
	private boolean messageRefusal = false; // message refusal mode
	
	private boolean silenceMode = false; // silence mode
	private boolean dietMode = false; // ignore weight penalty
	private boolean tradeRefusal = false; // Trade refusal
	private boolean exchangeRefusal = false; // Exchange refusal
	
	private L2Party party;
	
	// this is needed to find the inviting player for Party response
	// there can only be one active party request at once
	private Player activeRequester;
	private long requestExpireTime = 0;
	private L2Request request = new L2Request(this);
	private Item arrowItem;
	private Item boltItem;
	
	// Used for protection after teleport
	private long protectEndTime = 0;
	
	public boolean isSpawnProtected() {
		return protectEndTime > TimeController.getGameTicks();
	}
	
	private long teleportProtectEndTime = 0;
	
	public boolean isTeleportProtected() {
		return teleportProtectEndTime > TimeController.getGameTicks() &&
				(event == null || event.isType(EventType.Survival) || event.isType(EventType.VIP) || event.isType(EventType.StalkedSalkers));
	}
	
	// protects a char from agro mobs when getting up from fake death
	private long recentFakeDeathEndTime = 0;
	private boolean isFakeDeath;
	
	/**
	 * The fists WeaponTemplate of the Player (used when no weapon is equiped)
	 */
	private WeaponTemplate fistsWeaponItem;
	
	private final Map<Integer, String> chars = new HashMap<>();
	
	//private byte updateKnownCounter = 0;
	
	/**
	 * The current higher Expertise of the Player (None=0, D=1, C=2, B=3, A=4, S=5, S80=6, S84=7)
	 */
	private int expertiseIndex; // index in EXPERTISE_LEVELS
	private int expertiseArmorPenalty = 0;
	private int expertiseWeaponPenalty = 0;
	
	private boolean isEnchanting = false;
	private Item activeEnchantItem = null;
	private Item activeEnchantSupportItem = null;
	private Item activeEnchantAttrItem = null;
	private long activeEnchantTimestamp = 0;
	
	protected boolean inventoryDisable = false;
	
	protected Map<Integer, CubicInstance> cubics = new ConcurrentHashMap<>();
	
	/**
	 * Active shots.
	 */
	private final Item[] activeSoulShots = new Item[4];
	private final boolean[] disabledShoulShots = new boolean[4];
	
	public final ReentrantLock consumableLock = new ReentrantLock();
	
	private byte handysBlockCheckerEventArena = -1;
	
	/**
	 * new loto ticket
	 **/
	private int loto[] = new int[5];
	//public static int _loto_nums[] = {0,1,2,3,4,5,6,7,8,9,};
	/**
	 * new race ticket
	 **/
	private int race[] = new int[2];
	
	private final BlockList blockList = new BlockList(this);
	
	private int team = 0;
	
	/**
	 * lvl of alliance with ketra orcs or varka silenos, used in quests and aggro checks
	 * [-5,-1] varka, 0 neutral, [1,5] ketra
	 */
	private int alliedVarkaKetra = 0;
	
	private L2Fishing fishCombat;
	private boolean fishing = false;
	private int fishx = 0;
	private int fishy = 0;
	private int fishz = 0;
	
	private int[] transformAllowedSkills = {};
	private ScheduledFuture<?> taskRentPet;
	private ScheduledFuture<?> taskWater;
	
	/**
	 * Bypass validations
	 */
	private final List<String> validBypass = new ArrayList<>();
	private final List<String> validBypass2 = new ArrayList<>();
	
	private Forum forumMail;
	private Forum forumMemo;
	
	/**
	 * Current skill in use. Note that Creature has lastSkillCast, but
	 * this has the button presses
	 */
	private SkillDat currentSkill;
	private SkillDat currentPetSkill;
	
	/**
	 * Skills queued because a skill is already in progress
	 */
	private SkillDat queuedSkill;
	
	private int cursedWeaponEquippedId = 0;
	private boolean combatFlagEquippedId = false;
	
	private boolean reviveRequested = false;
	private double revivePower = 0;
	private boolean revivePet = false;
	
	private double cpUpdateIncCheck = .0;
	private double cpUpdateDecCheck = .0;
	private double cpUpdateInterval = .0;
	private double mpUpdateIncCheck = .0;
	private double mpUpdateDecCheck = .0;
	private double mpUpdateInterval = .0;
	
	private boolean isRidingStrider = false;
	private boolean isFlyingMounted = false;
	
	/**
	 * Char Coords from Client
	 */
	private int clientX;
	private int clientY;
	private int clientZ;
	private int clientHeading;
	
	// during fall validations will be disabled for 10 ms.
	private static final int FALLING_VALIDATION_DELAY = 10000;
	private volatile long fallingTimestamp = 0;
	
	private int multiSocialTarget = 0;
	private int multiSociaAction = 0;
	
	private int movieId = 0;
	
	private String adminConfirmCmd = null;
	
	private volatile long lastItemAuctionInfoRequest = 0;
	
	private Future<?> PvPRegTask;
	
	private long pvpFlagLasts;
	
	private long fightStanceTime;
	
	public long getFightStanceTime() {
		return fightStanceTime;
	}
	
	public void setFightStanceTime(long time) {
		fightStanceTime = time;
	}
	
	public void setPvpFlagLasts(long time) {
		pvpFlagLasts = time;
	}
	
	public long getPvpFlagLasts() {
		return pvpFlagLasts;
	}
	
	public void startPvPFlag() {
		updatePvPFlag(1);
		
		PvPRegTask = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new PvPFlag(), 1000, 1000);
	}
	
	public void stopPvpRegTask() {
		if (PvPRegTask != null) {
			PvPRegTask.cancel(true);
		}
	}
	
	public void stopPvPFlag() {
		stopPvpRegTask();
		
		updatePvPFlag(0);
		
		PvPRegTask = null;
	}
	
	/**
	 * Task lauching the function stopPvPFlag()
	 */
	private class PvPFlag implements Runnable {
		public PvPFlag() {
		
		}
		
		@Override
		public void run() {
			try {
				if (System.currentTimeMillis() > getPvpFlagLasts()) {
					stopPvPFlag();
				} else if (System.currentTimeMillis() > getPvpFlagLasts() - 20000) {
					updatePvPFlag(2);
				} else {
					updatePvPFlag(1);
					// Start a new PvP timer check
					//checkPvPFlag();
				}
			} catch (Exception e) {
				log.warn("error in pvp flag task:", e);
			}
		}
	}
	
	// Character UI
	private L2UIKeysSettings uiKeySettings;
	
	// Damage that the character gave in olys
	private int olyGivenDmg = 0;
	
	private EventTeam ctfFlag = null;
	private int eventPoints;
	
	private final int EVENT_SAVED_EFFECTS_SIZE = 60;
	private int eventSavedTime = 0;
	private Point3D eventSavedPosition = null;
	private Abnormal[] eventSavedEffects = new Abnormal[EVENT_SAVED_EFFECTS_SIZE];
	private Abnormal[] eventSavedSummonEffects = new Abnormal[EVENT_SAVED_EFFECTS_SIZE];
	// TODO for new summon system
	
	// Has written .itemid?
	private boolean isItemId = false;
	
	// Title color
	private String titleColor;
	
	//LasTravel
	private Map<String, Boolean> playerConfigs = new HashMap<>();
	private String publicIP;
	private String internalIP;
	
	//Captcha
	private String captcha;
	private int botLevel = 0;
	
	// CokeMobs
	private boolean mobSummonRequest = false;
	private Item mobSummonItem;
	private boolean mobSummonExchangeRequest = false;
	//private MobSummonInstance mobSummonExchange;
	
	// Tenkai Events
	private EventInstance event;
	private boolean wasInEvent = false;
	
	// .noexp Command
	private boolean noExp = false;
	
	// .landrates Command
	private boolean landRates = false;
	
	// .stabs Command
	private boolean stabs = false;
	
	// had Store Activity?
	private boolean hadStoreActivity = false;
	
	// Stalker Hints Task
	private StalkerHintsTask stalkerHintsTask;
	
	// Chess
	private boolean chessChallengeRequest = false;
	private Player chessChallenger;
	
	// Magic Gem
	private L2Spawn[] npcServitors = new L2Spawn[4];
	
	// Images
	private List<Integer> receivedImages = new ArrayList<>();
	
	// Event disarm
	private boolean eventDisarmed = false;
	
	// .sell stuff
	private TradeList customSellList;
	private boolean isAddSellItem = false;
	private int addSellPrice = -1;
	
	/**
	 * Herbs Task Time
	 **/
	private int herbstask = 0;
	
	/**
	 * Task for Herbs
	 */
	private class HerbTask implements Runnable {
		private String process;
		private int itemId;
		private long count;
		private WorldObject reference;
		private boolean sendMessage;
		
		HerbTask(String process, int itemId, long count, WorldObject reference, boolean sendMessage) {
			this.process = process;
			this.itemId = itemId;
			this.count = count;
			this.reference = reference;
			this.sendMessage = sendMessage;
		}
		
		@Override
		public void run() {
			try {
				addItem(process, itemId, count, reference, sendMessage);
			} catch (Exception e) {
				log.warn("", e);
			}
		}
	}
	
	/**
	 * ShortBuff clearing Task
	 */
	ScheduledFuture<?> shortBuffTask = null;
	
	private class ShortBuffTask implements Runnable {
		@Override
		public void run() {
			Player.this.sendPacket(new ShortBuffStatusUpdate(0, 0, 0));
			setShortBuffTaskSkillId(0);
		}
	}
	
	// L2JMOD Wedding
	private boolean married = false;
	private int partnerId = 0;
	private int coupleId = 0;
	private boolean engagerequest = false;
	private int engageid = 0;
	private boolean marryrequest = false;
	private boolean marryaccepted = false;
	
	/**
	 * Skill casting information (used to queue when several skills are cast in a short time)
	 **/
	public static class SkillDat {
		private Skill skill;
		private boolean ctrlPressed;
		private boolean shiftPressed;
		
		protected SkillDat(Skill skill, boolean ctrlPressed, boolean shiftPressed) {
			this.skill = skill;
			this.ctrlPressed = ctrlPressed;
			this.shiftPressed = shiftPressed;
		}
		
		public boolean isCtrlPressed() {
			return ctrlPressed;
		}
		
		public boolean isShiftPressed() {
			return shiftPressed;
		}
		
		public Skill getSkill() {
			return skill;
		}
		
		public int getSkillId() {
			return getSkill() != null ? getSkill().getId() : -1;
		}
	}
	
	//summon friend
	private SummonRequest summonRequest = new SummonRequest();
	
	private static class SummonRequest {
		private Player target = null;
		private Skill skill = null;
		
		public void setTarget(Player destination, Skill skill) {
			target = destination;
			this.skill = skill;
		}
		
		public Player getTarget() {
			return target;
		}
		
		public Skill getSkill() {
			return skill;
		}
	}
	
	// open/close gates
	private GatesRequest gatesRequest = new GatesRequest();
	
	private static class GatesRequest {
		private DoorInstance target = null;
		
		public void setTarget(DoorInstance door) {
			target = door;
		}
		
		public DoorInstance getDoor() {
			return target;
		}
	}
	
	/**
	 * Create a new Player and add it in the characters table of the database.<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Create a new Player with an account name </li>
	 * <li>Set the name, the Hair Style, the Hair Color and  the Face type of the Player</li>
	 * <li>Add the player in the characters table of the database</li><BR><BR>
	 *
	 * @param objectId    Identifier of the object to initialized
	 * @param template    The PcTemplate to apply to the Player
	 * @param accountName The name of the Player
	 * @param name        The name of the Player
	 * @param hairStyle   The hair style Identifier of the Player
	 * @param hairColor   The hair color Identifier of the Player
	 * @param face        The face type Identifier of the Player
	 * @return The Player added to the database or null
	 */
	public static Player create(int objectId,
	                            PcTemplate template,
	                            String accountName,
	                            String name,
	                            byte hairStyle,
	                            byte hairColor,
	                            byte face,
	                            boolean sex,
	                            int classId) {
		// Create a new Player with an account name
		PcAppearance app = new PcAppearance(face, hairColor, hairStyle, sex);
		Player player = new Player(objectId, template, accountName, app);
		
		// Set the name of the Player
		player.setName(name);
		
		// Set Character's create time
		player.setCreateTime(System.currentTimeMillis());
		
		player.templateId = template.getId();
		
		// Set the base class ID to that of the actual class ID.
		player.setBaseClass(classId);
		player.currentClass = PlayerClassTable.getInstance().getClassById(classId);
		// Kept for backwards compabitility.
		player.setNewbie(1);
		// Add the player in the characters table of the database
		boolean ok = player.createDb();
		
		if (!ok) {
			return null;
		}
		
		return player;
	}
	
	public static Player createDummyPlayer(int objectId, String name) {
		// Create a new Player with an account name
		Player player = new Player(objectId);
		player.setName(name);
		
		return player;
	}
	
	public String getAccountName() {
		if (getClient() == null) {
			return getAccountNamePlayer();
		}
		return getClient().getAccountName();
	}
	
	public String getAccountNamePlayer() {
		return accountName;
	}
	
	public Map<Integer, String> getAccountChars() {
		return chars;
	}
	
	public int getRelation(Player target) {
		int result = 0;
		
		if (getClan() != null) {
			result |= RelationChanged.RELATION_CLAN_MEMBER;
			if (getClan() == target.getClan()) {
				result |= RelationChanged.RELATION_CLAN_MATE;
			}
			if (getAllyId() != 0) {
				result |= RelationChanged.RELATION_ALLY_MEMBER;
			}
		}
		if (isClanLeader()) {
			result |= RelationChanged.RELATION_LEADER;
		}
		if (getParty() != null && getParty() == target.getParty()) {
			result |= RelationChanged.RELATION_HAS_PARTY;
			for (int i = 0; i < getParty().getPartyMembers().size(); i++) {
				if (getParty().getPartyMembers().get(i) != this) {
					continue;
				}
				switch (i) {
					case 0:
						result |= RelationChanged.RELATION_PARTYLEADER; // 0x10
						break;
					case 1:
						result |= RelationChanged.RELATION_PARTY4; // 0x8
						break;
					case 2:
						result |= RelationChanged.RELATION_PARTY3 + RelationChanged.RELATION_PARTY2 + RelationChanged.RELATION_PARTY1; // 0x7
						break;
					case 3:
						result |= RelationChanged.RELATION_PARTY3 + RelationChanged.RELATION_PARTY2; // 0x6
						break;
					case 4:
						result |= RelationChanged.RELATION_PARTY3 + RelationChanged.RELATION_PARTY1; // 0x5
						break;
					case 5:
						result |= RelationChanged.RELATION_PARTY3; // 0x4
						break;
					case 6:
						result |= RelationChanged.RELATION_PARTY2 + RelationChanged.RELATION_PARTY1; // 0x3
						break;
					case 7:
						result |= RelationChanged.RELATION_PARTY2; // 0x2
						break;
					case 8:
						result |= RelationChanged.RELATION_PARTY1; // 0x1
						break;
				}
			}
		}
		if (getSiegeState() != 0) {
			result |= RelationChanged.RELATION_INSIEGE;
			if (getSiegeState() != target.getSiegeState()) {
				result |= RelationChanged.RELATION_ENEMY;
			} else {
				result |= RelationChanged.RELATION_ALLY;
			}
			if (getSiegeState() == 1) {
				result |= RelationChanged.RELATION_ATTACKER;
			}
		}
		if (getClan() != null && target.getClan() != null) {
			if ((target.getPledgeType() != L2Clan.SUBUNIT_ACADEMY || target.getLevel() > 70) &&
					(getPledgeType() != L2Clan.SUBUNIT_ACADEMY || getLevel() > 70)) {
				if (target.getClan().getClansAtWarQueue().contains(getClan()) && getClan().getClansAtWarQueue().contains(target.getClan())) {
					result |= RelationChanged.RELATION_WAR_STARTED;
				}
				if (target.getClan().getStartedWarList().contains(getClan()) && getClan().getStartedWarList().contains(target.getClan())) {
					result |= RelationChanged.RELATION_WAR_ABOUT_TO_BEGIN;
					result |= RelationChanged.RELATION_WAR_STARTED;
				}
			}
		}
		if (getBlockCheckerArena() != -1) {
			result |= RelationChanged.RELATION_INSIEGE;
			ArenaParticipantsHolder holder = HandysBlockCheckerManager.getInstance().getHolder(getBlockCheckerArena());
			if (holder.getPlayerTeam(this) == 0) {
				result |= RelationChanged.RELATION_ENEMY;
			} else {
				result |= RelationChanged.RELATION_ALLY;
			}
			result |= RelationChanged.RELATION_ATTACKER;
		}
		return result;
	}
	
	/**
	 * Retrieve a Player from the characters table of the database and add it in allObjects of the L2world (call restore method).<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Retrieve the Player from the characters table of the database </li>
	 * <li>Add the Player object in allObjects </li>
	 * <li>Set the x,y,z position of the Player and make it invisible</li>
	 * <li>Update the overloaded status of the Player</li><BR><BR>
	 *
	 * @param objectId Identifier of the object to initialized
	 * @return The Player loaded from the database
	 */
	public static Player load(int objectId) {
		return restore(objectId);
	}
	
	private void initPcStatusUpdateValues() {
		cpUpdateInterval = getMaxCp() / 352.0;
		cpUpdateIncCheck = getMaxCp();
		cpUpdateDecCheck = getMaxCp() - cpUpdateInterval;
		mpUpdateInterval = getMaxMp() / 352.0;
		mpUpdateIncCheck = getMaxMp();
		mpUpdateDecCheck = getMaxMp() - mpUpdateInterval;
	}
	
	/**
	 * Constructor of Player (use Creature constructor).<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Call the Creature constructor to create an empty skills slot and copy basic Calculator set to this Player </li>
	 * <li>Set the name of the Player</li><BR><BR>
	 * <p>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method SET the level of the Player to 1</B></FONT><BR><BR>
	 *
	 * @param objectId    Identifier of the object to initialized
	 * @param template    The PcTemplate to apply to the Player
	 * @param accountName The name of the account including this Player
	 */
	protected Player(int objectId, PcTemplate template, String accountName, PcAppearance app) {
		super(objectId, template);
		setInstanceType(InstanceType.L2PcInstance);
		super.initCharStatusUpdateValues();
		initPcStatusUpdateValues();
		
		this.accountName = accountName;
		app.setOwner(this);
		appearance = app;
		
		// Create an AI
		getAI();
		
		// Create a L2Radar object
		radar = new L2Radar(this);
		
		temporarySkills = new ConcurrentHashMap<>();
	}
	
	private Player(int objectId) {
		super(objectId, null);
		setInstanceType(InstanceType.L2PcInstance);
		super.initCharStatusUpdateValues();
		initPcStatusUpdateValues();
		
		temporarySkills = new ConcurrentHashMap<>();
	}
	
	@Override
	public final PcKnownList getKnownList() {
		return (PcKnownList) super.getKnownList();
	}
	
	@Override
	public void initKnownList() {
		setKnownList(new PcKnownList(this));
	}
	
	@Override
	public final PcStat getStat() {
		return (PcStat) super.getStat();
	}
	
	@Override
	public void initCharStat() {
		setStat(new PcStat(this));
	}
	
	@Override
	public final PcStatus getStatus() {
		return (PcStatus) super.getStatus();
	}
	
	@Override
	public void initCharStatus() {
		setStatus(new PcStatus(this));
	}
	
	@Override
	public PcPosition getPosition() {
		return (PcPosition) super.getPosition();
	}
	
	@Override
	public void initPosition() {
		setObjectPosition(new PcPosition(this));
	}
	
	public final PcAppearance getAppearance() {
		return appearance;
	}
	
	public final PcTemplate getOriginalBaseTemplate() {
		return CharTemplateTable.getInstance().getTemplate(templateId);
	}
	
	/**
	 * Return the base PcTemplate link to the Player.<BR><BR>
	 */
	public final PcTemplate getBaseTemplate() {
		if (temporaryTemplate != null) {
			return temporaryTemplate;
		}
		
		return CharTemplateTable.getInstance().getTemplate(templateId);
	}
	
	private int raceAppearance = -1;
	
	public final PcTemplate getVisibleTemplate() {
		if (raceAppearance < 0) {
			return getBaseTemplate();
		}
		
		return CharTemplateTable.getInstance().getTemplate(raceAppearance);
	}
	
	public final void setRaceAppearance(int app) {
		raceAppearance = app;
	}
	
	public final int getRaceAppearance() {
		return raceAppearance;
	}
	
	/**
	 * Return the PcTemplate link to the Player.
	 */
	@Override
	public final PcTemplate getTemplate() {
		if (temporaryTemplate != null) {
			return temporaryTemplate;
		}
		
		return (PcTemplate) super.getTemplate();
	}
	
	@Override
	protected CreatureAI initAI() {
		return new PlayerAI(this);
	}
	
	/**
	 * Return the Level of the Player.
	 */
	@Override
	public final int getLevel() {
		if (temporaryLevel != 0) {
			return temporaryLevel;
		}
		
		return getStat().getLevel();
	}
	
	/**
	 * For skill learning purposes only. It will return the base class level if the player is on a subclass
	 */
	public final int getDualLevel() {
		if (isSubClassActive()) {
			return getStat().getBaseClassLevel();
		}
		
		if (subClasses != null) {
			for (SubClass sub : subClasses.values()) {
				if (sub.isDual()) {
					return sub.getLevel();
				}
			}
		}
		
		return 1;
	}
	
	/**
	 * Return the newbie rewards state of the Player.<BR><BR>
	 */
	public int getNewbie() {
		return newbie;
	}
	
	/**
	 * Set the newbie rewards state of the Player.<BR><BR>
	 *
	 * @param newbieRewards The Identifier of the newbie state<BR><BR>
	 */
	public void setNewbie(int newbieRewards) {
		newbie = newbieRewards;
	}
	
	public void setBaseClass(int baseClass) {
		this.baseClass = baseClass;
	}
	
	public void addRaceSkills() {
		// Ertheias get their race skills at lvl 1
		if (getRace() == Race.Ertheia && getLevel() == 1) {
			for (int skillId : getTemplate().getSkillIds()) {
				addSkill(SkillTable.getInstance().getInfo(skillId, 1), true);
			}
		} else if (getLevel() >= 85 && (getRace() == Race.Ertheia && getBaseClass() >= 188 || getRace() != Race.Ertheia && getBaseClass() >= 139)) {
			for (int skillId : getTemplate().getSkillIds()) {
				int playerSkillLevel = getSkillLevelHash(skillId);
				int maxSkillLevel = SkillTable.getInstance().getMaxLevel(skillId);
				
				if (playerSkillLevel < maxSkillLevel) {
					addSkill(SkillTable.getInstance().getInfo(skillId, maxSkillLevel), true);
				}
			}
		}
	}
	
	public void setBaseClass(PlayerClass cl) {
		baseClass = cl.getId();
	}
	
	public boolean isInStoreMode() {
		return getPrivateStoreType() > 0;
	}
	
	//	public boolean isInCraftMode() { return (getPrivateStoreType() == STORE_PRIVATE_MANUFACTURE); }
	
	public boolean isInCraftMode() {
		return inCraftMode;
	}
	
	public void isInCraftMode(boolean b) {
		inCraftMode = b;
	}
	
	/**
	 * Manage Logout Task: <li>Remove player from world <BR>
	 * </li> <li>Save player data into DB <BR>
	 * </li> <BR>
	 * <BR>
	 */
	public void logout() {
		logout(true);
	}
	
	/**
	 * Manage Logout Task: <li>Remove player from world <BR>
	 * </li> <li>Save player data into DB <BR>
	 * </li> <BR>
	 * <BR>
	 *
	 */
	public void logout(boolean closeClient) {
		try {
			closeNetConnection(closeClient);
		} catch (Exception e) {
			log.warn("Exception on logout(): " + e.getMessage(), e);
		}
	}
	
	/**
	 * Return a table containing all Common L2RecipeList of the Player.<BR><BR>
	 */
	public L2RecipeList[] getCommonRecipeBook() {
		return commonRecipeBook.values().toArray(new L2RecipeList[commonRecipeBook.values().size()]);
	}
	
	/**
	 * Return a table containing all Dwarf L2RecipeList of the Player.<BR><BR>
	 */
	public L2RecipeList[] getDwarvenRecipeBook() {
		return dwarvenRecipeBook.values().toArray(new L2RecipeList[dwarvenRecipeBook.values().size()]);
	}
	
	/**
	 * Add a new L2RecipList to the table commonrecipebook containing all L2RecipeList of the Player <BR><BR>
	 *
	 * @param recipe The L2RecipeList to add to the recipebook
	 */
	public void registerCommonRecipeList(L2RecipeList recipe, boolean saveToDb) {
		if (recipe == null) {
			return;
		}
		
		commonRecipeBook.put(recipe.getId(), recipe);
		
		if (saveToDb) {
			insertNewRecipeData(recipe.getId(), false);
		}
	}
	
	/**
	 * Add a new L2RecipList to the table recipebook containing all L2RecipeList of the Player <BR><BR>
	 *
	 * @param recipe The L2RecipeList to add to the recipebook
	 */
	public void registerDwarvenRecipeList(L2RecipeList recipe, boolean saveToDb) {
		if (recipe == null) {
			return;
		}
		
		dwarvenRecipeBook.put(recipe.getId(), recipe);
		
		if (saveToDb) {
			insertNewRecipeData(recipe.getId(), true);
		}
	}
	
	/**
	 * @return <b>TRUE</b> if player has the recipe on Common or Dwarven Recipe book else returns <b>FALSE</b>
	 */
	public boolean hasRecipeList(int recipeId) {
        if (dwarvenRecipeBook.containsKey(recipeId)) {
            return true;
        } else {
            return commonRecipeBook.containsKey(recipeId);
        }
	}
	
	/**
	 * Tries to remove a L2RecipList from the table DwarvenRecipeBook or from table CommonRecipeBook, those table contain all L2RecipeList of the Player <BR><BR>
	 */
	public void unregisterRecipeList(int recipeId) {
		if (dwarvenRecipeBook.remove(recipeId) != null) {
			deleteRecipeData(recipeId, true);
		} else if (commonRecipeBook.remove(recipeId) != null) {
			deleteRecipeData(recipeId, false);
		} else {
			log.warn("Attempted to remove unknown RecipeList: " + recipeId);
		}
		
		L2ShortCut[] allShortCuts = getAllShortCuts();
		
		for (L2ShortCut sc : allShortCuts) {
			if (sc != null && sc.getId() == recipeId && sc.getType() == L2ShortCut.TYPE_RECIPE) {
				deleteShortCut(sc.getSlot(), sc.getPage());
			}
		}
	}
	
	private void insertNewRecipeData(int recipeId, boolean isDwarf) {
		Connection con = null;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("INSERT INTO character_recipebook (charId, id, classIndex, type) VALUES(?,?,?,?)");
			statement.setInt(1, getObjectId());
			statement.setInt(2, recipeId);
			statement.setInt(3, isDwarf ? classIndex : 0);
			statement.setInt(4, isDwarf ? 1 : 0);
			statement.execute();
			statement.close();
		} catch (SQLException e) {
			log.error("SQL exception while inserting recipe: " + recipeId + " from character " + getObjectId(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	private void deleteRecipeData(int recipeId, boolean isDwarf) {
		Connection con = null;
		
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("DELETE FROM character_recipebook WHERE charId=? AND id=? AND classIndex=?");
			statement.setInt(1, getObjectId());
			statement.setInt(2, recipeId);
			statement.setInt(3, isDwarf ? classIndex : 0);
			statement.execute();
			statement.close();
		} catch (SQLException e) {
				log.error("SQL exception while deleting recipe: " + recipeId + " from character " + getObjectId(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * Returns the Id for the last talked quest NPC.<BR><BR>
	 */
	public int getLastQuestNpcObject() {
		return questNpcObject;
	}
	
	public void setLastQuestNpcObject(int npcId) {
		questNpcObject = npcId;
	}
	
	/**
	 * Return the QuestState object corresponding to the quest name.<BR><BR>
	 *
	 * @param quest The name of the quest
	 */
	public QuestState getQuestState(String quest) {
		return quests.get(quest);
	}
	
	/**
	 * Add a QuestState to the table quest containing all quests began by the Player.<BR><BR>
	 *
	 * @param qs The QuestState to add to quest
	 */
	public void setQuestState(QuestState qs) {
		quests.put(qs.getQuestName(), qs);
	}
	
	/**
	 * Remove a QuestState from the table quest containing all quests began by the Player.<BR><BR>
	 *
	 * @param quest The name of the quest
	 */
	public void delQuestState(String quest) {
		quests.remove(quest);
	}
	
	private QuestState[] addToQuestStateArray(QuestState[] questStateArray, QuestState state) {
		int len = questStateArray.length;
		QuestState[] tmp = new QuestState[len + 1];
		System.arraycopy(questStateArray, 0, tmp, 0, len);
		tmp[len] = state;
		return tmp;
	}
	
	/**
	 * Return a table containing all Quest in progress from the table quests.<BR><BR>
	 */
	public Quest[] getAllActiveQuests() {
		ArrayList<Quest> quests = new ArrayList<>();
		
		for (QuestState qs : this.quests.values()) {
			if (qs == null) {
				continue;
			}
			
			if (qs.getQuest() == null) {
				continue;
			}
			
			int questId = qs.getQuest().getQuestIntId();
			if (questId > 19999 || questId < 1) {
				continue;
			}
			
			if (!qs.isStarted() && !Config.DEVELOPER) {
				continue;
			}
			
			quests.add(qs.getQuest());
		}
		
		return quests.toArray(new Quest[quests.size()]);
	}
	
	/**
	 * Return a table containing all QuestState to modify after a Attackable killing.<BR><BR>
	 */
	public QuestState[] getQuestsForAttacks(Npc npc) {
		// Create a QuestState table that will contain all QuestState to modify
		QuestState[] states = null;
		
		// Go through the QuestState of the Player quests
		for (Quest quest : npc.getTemplate().getEventQuests(Quest.QuestEventType.ON_ATTACK)) {
			// Check if the Identifier of the Attackable attck is needed for the current quest
			if (getQuestState(quest.getName()) != null) {
				// Copy the current Player QuestState in the QuestState table
				if (states == null) {
					states = new QuestState[]{getQuestState(quest.getName())};
				} else {
					states = addToQuestStateArray(states, getQuestState(quest.getName()));
				}
			}
		}
		
		// Return a table containing all QuestState to modify
		return states;
	}
	
	/**
	 * Return a table containing all QuestState to modify after a Attackable killing.<BR><BR>
	 */
	public QuestState[] getQuestsForKills(Npc npc) {
		// Create a QuestState table that will contain all QuestState to modify
		QuestState[] states = null;
		
		// Go through the QuestState of the Player quests
		for (Quest quest : npc.getTemplate().getEventQuests(Quest.QuestEventType.ON_KILL)) {
			// Check if the Identifier of the Attackable killed is needed for the current quest
			if (getQuestState(quest.getName()) != null) {
				// Copy the current Player QuestState in the QuestState table
				if (states == null) {
					states = new QuestState[]{getQuestState(quest.getName())};
				} else {
					states = addToQuestStateArray(states, getQuestState(quest.getName()));
				}
			}
		}
		
		// Return a table containing all QuestState to modify
		return states;
	}
	
	/**
	 * Return a table containing all QuestState from the table quests in which the Player must talk to the NPC.<BR><BR>
	 *
	 * @param npcId The Identifier of the NPC
	 */
	public QuestState[] getQuestsForTalk(int npcId) {
		// Create a QuestState table that will contain all QuestState to modify
		QuestState[] states = null;
		
		// Go through the QuestState of the Player quests
		Quest[] quests = NpcTable.getInstance().getTemplate(npcId).getEventQuests(Quest.QuestEventType.ON_TALK);
		if (quests != null) {
			for (Quest quest : quests) {
				if (quest != null) {
					// Copy the current Player QuestState in the QuestState table
					if (getQuestState(quest.getName()) != null) {
						if (states == null) {
							states = new QuestState[]{getQuestState(quest.getName())};
						} else {
							states = addToQuestStateArray(states, getQuestState(quest.getName()));
						}
					}
				}
			}
		}
		
		// Return a table containing all QuestState to modify
		return states;
	}
	
	public QuestState processQuestEvent(String quest, String event) {
		QuestState retval = null;
		if (event == null) {
			event = "";
		}
		QuestState qs = getQuestState(quest);
		if (qs == null && event.length() == 0) {
			return retval;
		}
		if (qs == null) {
			Quest q = QuestManager.getInstance().getQuest(quest);
			if (q == null) {
				return retval;
			}
			qs = q.newQuestState(this);
		}
		if (qs != null) {
			if (getLastQuestNpcObject() > 0) {
				WorldObject object = World.getInstance().findObject(getLastQuestNpcObject());
				if (object instanceof Npc && isInsideRadius(object, ((Npc) object).getInteractionDistance(), false, false)) {
					Npc npc = (Npc) object;
					QuestState[] states = getQuestsForTalk(npc.getNpcId());
					
					if (states != null) {
						for (QuestState state : states) {
							if (state.getQuest().getName().equals(qs.getQuest().getName())) {
								if (qs.getQuest().notifyEvent(event, npc, this)) {
									showQuestWindow(quest, State.getStateName(qs.getState()));
								}
								
								retval = qs;
							}
						}
					}
				}
			}
		}
		
		return retval;
	}
	
	private void showQuestWindow(String questId, String stateId) {
		String path = Config.DATA_FOLDER + "scripts/quests/" + questId + "/" + stateId + ".htm";
		String content = HtmCache.getInstance().getHtm(getHtmlPrefix(), path); //TODO path for quests html
		
		if (content != null) {
			if (Config.DEBUG) {
				log.debug("Showing quest window for quest " + questId + " state " + stateId + " html path: " + path);
			}
			
			NpcHtmlMessage npcReply = new NpcHtmlMessage(5);
			npcReply.setHtml(content);
			sendPacket(npcReply);
		}
		
		sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	/**
	 * List of all QuestState instance that needs to be notified of this Player's or its pet's death
	 */
	private List<QuestState> notifyQuestOfDeathList;
	
	/**
	 * Add QuestState instance that is to be notified of Player's death.<BR><BR>
	 *
	 * @param qs The QuestState that subscribe to this event
	 */
	public void addNotifyQuestOfDeath(QuestState qs) {
		if (qs == null) {
			return;
		}
		
		if (!getNotifyQuestOfDeath().contains(qs)) {
			getNotifyQuestOfDeath().add(qs);
		}
	}
	
	/**
	 * Remove QuestState instance that is to be notified of Player's death.<BR><BR>
	 *
	 * @param qs The QuestState that subscribe to this event
	 */
	public void removeNotifyQuestOfDeath(QuestState qs) {
		if (qs == null || notifyQuestOfDeathList == null) {
			return;
		}
		
		notifyQuestOfDeathList.remove(qs);
	}
	
	/**
	 * Return a list of QuestStates which registered for notify of death of this Player.<BR><BR>
	 */
	public final List<QuestState> getNotifyQuestOfDeath() {
		if (notifyQuestOfDeathList == null) {
			synchronized (this) {
				if (notifyQuestOfDeathList == null) {
					notifyQuestOfDeathList = new ArrayList<>();
				}
			}
		}
		
		return notifyQuestOfDeathList;
	}
	
	public final boolean isNotifyQuestOfDeathEmpty() {
		return notifyQuestOfDeathList == null || notifyQuestOfDeathList.isEmpty();
	}
	
	/**
	 * Return a table containing all L2ShortCut of the Player.<BR><BR>
	 */
	public L2ShortCut[] getAllShortCuts() {
		return shortCuts.getAllShortCuts();
	}
	
	public ShortCuts getShortCuts() {
		return shortCuts;
	}
	
	/**
	 * Return the L2ShortCut of the Player corresponding to the position (page-slot).<BR><BR>
	 *
	 * @param slot The slot in wich the shortCuts is equiped
	 * @param page The page of shortCuts containing the slot
	 */
	public L2ShortCut getShortCut(int slot, int page) {
		return shortCuts.getShortCut(slot, page);
	}
	
	/**
	 * Add a L2shortCut to the Player shortCuts<BR><BR>
	 */
	public void registerShortCut(L2ShortCut shortcut) {
		shortCuts.registerShortCut(shortcut);
	}
	
	/**
	 * Delete the L2ShortCut corresponding to the position (page-slot) from the Player shortCuts.<BR><BR>
	 */
	public void deleteShortCut(int slot, int page) {
		shortCuts.deleteShortCut(slot, page);
	}
	
	/**
	 * Updates the shortcut bars with the new skill.
	 *
	 * @param objId the item objectId to search and update.
	 */
	public void updateItemShortCuts(int objId) {
		shortCuts.updateItemShortcuts(objId);
	}
	
	/**
	 * Updates the shortcut bars with the new skill.
	 *
	 * @param skillId    the skill Id to search and update.
	 * @param skillLevel the skill level to update.
	 */
	public void updateSkillShortcuts(int skillId, int skillLevel) {
		shortCuts.updateSkillShortcuts(skillId, skillLevel);
	}
	
	/**
	 * Add a L2Macro to the Player macroses<BR><BR>
	 */
	public void registerMacro(L2Macro macro) {
		macroses.registerMacro(macro);
	}
	
	/**
	 * Delete the L2Macro corresponding to the Identifier from the Player macroses.<BR><BR>
	 */
	public void deleteMacro(int id) {
		macroses.deleteMacro(id);
	}
	
	/**
	 * Return all L2Macro of the Player.<BR><BR>
	 */
	public MacroList getMacroses() {
		return macroses;
	}
	
	/**
	 * Set the siege state of the Player.<BR><BR>
	 * 1 = attacker, 2 = defender, 0 = not involved
	 */
	public void setSiegeState(byte siegeState) {
		this.siegeState = siegeState;
	}
	
	/**
	 * Get the siege state of the Player.<BR><BR>
	 * 1 = attacker, 2 = defender, 0 = not involved
	 */
	public byte getSiegeState() {
		return siegeState;
	}
	
	/**
	 * Set the siege Side of the Player.<BR><BR>
	 */
	public void setSiegeSide(int val) {
		siegeSide = val;
	}
	
	public boolean isRegisteredOnThisSiegeField(int val) {
		return !(siegeSide != val && (siegeSide < 81 || siegeSide > 89));
	}
	
	public int getSiegeSide() {
		return siegeSide;
	}
	
	/**
	 * Set the PvP Flag of the Player.<BR><BR>
	 */
	public void setPvpFlag(int pvpFlag) {
		this.pvpFlag = (byte) pvpFlag;
	}
	
	@Override
	public byte getPvpFlag() {
		return pvpFlag;
	}
	
	@Override
	public void updatePvPFlag(int value) {
		if (getPvpFlag() == value) {
			return;
		}
		
		setPvpFlag(value);
		
		sendPacket(new UserInfo(this));
		
		// If this player has a pet update the pets pvp flag as well
		if (getPet() != null) {
			sendPacket(new RelationChanged(getPet(), getRelation(this), false));
		}
		for (SummonInstance summon : getSummons()) {
			if (summon instanceof MobSummonInstance) {
				summon.unSummon(this);
				continue;
			}
			
			sendPacket(new RelationChanged(summon, getRelation(this), false));
		}
		
		Collection<Player> plrs = getKnownList().getKnownPlayers().values();
		//synchronized (getKnownList().getKnownPlayers())
		{
			for (Player target : plrs) {
				target.sendPacket(new RelationChanged(this, getRelation(target), isAutoAttackable(target)));
				if (getPet() != null) {
					target.sendPacket(new RelationChanged(getPet(), getRelation(target), isAutoAttackable(target)));
				}
			}
		}
	}
	
	@Override
	public void revalidateZone(boolean force) {
		// Cannot validate if not in  a world region (happens during teleport)
		if (getWorldRegion() == null) {
			return;
		}
		
		// This function is called too often from movement code
		if (force) {
			zoneValidateCounter = 4;
		} else {
			zoneValidateCounter--;
			if (zoneValidateCounter < 0) {
				zoneValidateCounter = 4;
			} else {
				return;
			}
		}
		
		getWorldRegion().revalidateZones(this);
		
		if (Config.ALLOW_WATER) {
			checkWaterState();
		}
		
		if (isInsideZone(ZONE_ALTERED)) {
			if (lastCompassZone == ExSetCompassZoneCode.ALTEREDZONE) {
				return;
			}
			lastCompassZone = ExSetCompassZoneCode.ALTEREDZONE;
			ExSetCompassZoneCode cz = new ExSetCompassZoneCode(ExSetCompassZoneCode.ALTEREDZONE);
			sendPacket(cz);
		} else if (isInsideZone(ZONE_SIEGE)) {
			if (lastCompassZone == ExSetCompassZoneCode.SIEGEWARZONE2) {
				return;
			}
			lastCompassZone = ExSetCompassZoneCode.SIEGEWARZONE2;
			ExSetCompassZoneCode cz = new ExSetCompassZoneCode(ExSetCompassZoneCode.SIEGEWARZONE2);
			sendPacket(cz);
		} else if (isInsideZone(ZONE_PVP)) {
			if (lastCompassZone == ExSetCompassZoneCode.PVPZONE) {
				return;
			}
			lastCompassZone = ExSetCompassZoneCode.PVPZONE;
			ExSetCompassZoneCode cz = new ExSetCompassZoneCode(ExSetCompassZoneCode.PVPZONE);
			sendPacket(cz);
		} else if (isInsideZone(ZONE_PEACE)) {
			if (lastCompassZone == ExSetCompassZoneCode.PEACEZONE) {
				return;
			}
			lastCompassZone = ExSetCompassZoneCode.PEACEZONE;
			ExSetCompassZoneCode cz = new ExSetCompassZoneCode(ExSetCompassZoneCode.PEACEZONE);
			sendPacket(cz);
		} else {
			if (lastCompassZone == ExSetCompassZoneCode.GENERALZONE) {
				return;
			}
			if (lastCompassZone == ExSetCompassZoneCode.SIEGEWARZONE2 && !isDead()) {
				updatePvPStatus();
			}
			lastCompassZone = ExSetCompassZoneCode.GENERALZONE;
			ExSetCompassZoneCode cz = new ExSetCompassZoneCode(ExSetCompassZoneCode.GENERALZONE);
			sendPacket(cz);
		}
	}
	
	/**
	 * Return True if the Player can Craft Dwarven Recipes.<BR><BR>
	 */
	public boolean hasDwarvenCraft() {
		return getSkillLevelHash(Skill.SKILL_CREATE_DWARVEN) >= 1;
	}
	
	public int getDwarvenCraft() {
		return getSkillLevelHash(Skill.SKILL_CREATE_DWARVEN);
	}
	
	/**
	 * @return True if the Player can Craft Dwarven Recipes.
	 */
	public boolean canCrystallize() {
		return getSkillLevelHash(Skill.SKILL_CRYSTALLIZE) >= 1;
	}
	
	/**
	 * Return True if the Player can Craft Dwarven Recipes.<BR><BR>
	 */
	public boolean hasCommonCraft() {
		return getSkillLevelHash(Skill.SKILL_CREATE_COMMON) >= 1;
	}
	
	public int getCommonCraft() {
		return getSkillLevelHash(Skill.SKILL_CREATE_COMMON);
	}
	
	/**
	 * Return the PK counter of the Player.<BR><BR>
	 */
	public int getPkKills() {
		return pkKills;
	}
	
	/**
	 * Set the PK counter of the Player.<BR><BR>
	 */
	public void setPkKills(int pkKills) {
		this.pkKills = pkKills;
	}
	
	/**
	 * Return the deleteTimer of the Player.<BR><BR>
	 */
	public long getDeleteTimer() {
		return deleteTimer;
	}
	
	/**
	 * Set the deleteTimer of the Player.<BR><BR>
	 */
	public void setDeleteTimer(long deleteTimer) {
		this.deleteTimer = deleteTimer;
	}
	
	/**
	 * Return the current weight of the Player.<BR><BR>
	 */
	public int getCurrentLoad() {
		return inventory.getTotalWeight();
	}
	
	/**
	 * For Friend Memo
	 */
	private HashMap<Integer, String> friendMemo = new HashMap<>();
	
	public String getFriendMemo(int objId) {
		if (friendMemo != null && friendMemo.containsKey(objId)) {
			return friendMemo.get(objId);
		}
		
		return "";
	}
	
	public void addFriendMemo(int objId, String memo) {
		if (friendMemo != null && friendMemo.containsKey(objId)) {
			friendMemo.remove(objId);
		}
		friendMemo.put(objId, memo);
	}
	
	public void removeFriendMemo(int objId) {
		if (friendMemo != null && friendMemo.containsKey(objId)) {
			friendMemo.remove(objId);
		}
	}
	
	public void broadcastToOnlineFriends(L2GameServerPacket packet) {
		for (int objId : getFriendList()) {
			Player friend;
			if (World.getInstance().getPlayer(objId) != null) {
				friend = World.getInstance().getPlayer(objId);
				friend.sendPacket(packet);
			}
		}
	}
	
	/**
	 * For Block Memo
	 */
	public HashMap<Integer, String> blockMemo = new HashMap<>();
	
	public String getBlockMemo(int objId) {
		if (blockMemo != null) {
			if (blockMemo.containsKey(objId)) {
				return blockMemo.get(objId);
			}
		}
		
		return "";
	}
	
	public void addBlockMemo(int objId, String memo) {
		if (blockMemo != null) {
			if (blockMemo.containsKey(objId)) {
				blockMemo.remove(objId);
			}
		}
		blockMemo.put(objId, memo);
	}
	
	public void removeBlockMemo(int objId) {
		if (blockMemo != null) {
			if (blockMemo.containsKey(objId)) {
				blockMemo.remove(objId);
			}
		}
	}
	
	/**
	 * Return the number of recommandation obtained by the Player.<BR><BR>
	 */
	public int getRecomHave() {
		return recomHave;
	}
	
	/**
	 * Increment the number of recommandation obtained by the Player (Max : 255).<BR><BR>
	 */
	protected void incRecomHave() {
		if (recomHave < 255) {
			recomHave++;
		}
	}
	
	/**
	 * Set the number of recommandation obtained by the Player (Max : 255).<BR><BR>
	 */
	public void setRecomHave(int value) {
		if (value > 255) {
			recomHave = 255;
		} else if (value < 0) {
			recomHave = 0;
		} else {
			recomHave = value;
		}
	}
	
	/**
	 * Set the number of recommandation obtained by the Player (Max : 255).<BR><BR>
	 */
	public void setRecomLeft(int value) {
		if (value > 255) {
			recomLeft = 255;
		} else if (value < 0) {
			recomLeft = 0;
		} else {
			recomLeft = value;
		}
	}
	
	/**
	 * Return the number of recommandation that the Player can give.<BR><BR>
	 */
	public int getRecomLeft() {
		return recomLeft;
	}
	
	/**
	 * Increment the number of recommandation that the Player can give.<BR><BR>
	 */
	protected void decRecomLeft() {
		if (recomLeft > 0) {
			recomLeft--;
		}
	}
	
	public void giveRecom(Player target) {
		target.incRecomHave();
		decRecomLeft();
	}
	
	/**
	 * Set the exp of the Player before a death
	 *
	 */
	public void setExpBeforeDeath(long exp) {
		expBeforeDeath = exp;
	}
	
	public long getExpBeforeDeath() {
		return expBeforeDeath;
	}
	
	/**
	 * Return the reputation of the Player.<BR><BR>
	 */
	@Override
	public int getReputation() {
		return reputation;
	}
	
	/**
	 * Set the Reputation of the Player and send a Server->Client packet StatusUpdate (broadcast).<BR><BR>
	 */
	public void setReputation(int reputation) {
		//if (reputation < 0) reputation = 0;
		if (reputation == 0 && reputation < 0) {
			Collection<WorldObject> objs = getKnownList().getKnownObjects().values();
			//synchronized (getKnownList().getKnownObjects())
			{
				for (WorldObject object : objs) {
					if (!(object instanceof GuardInstance)) {
						continue;
					}
					
					if (((GuardInstance) object).getAI().getIntention() == CtrlIntention.AI_INTENTION_IDLE) {
						((GuardInstance) object).getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE, null);
					}
				}
			}
		} else if (reputation >= 0 && reputation == 0) {
			// Send a Server->Client StatusUpdate packet with Reputation and PvP Flag to the Player and all Player to inform (broadcast)
			setReputationFlag(0);
		}
		
		this.reputation = reputation;
		broadcastReputation();
	}
	
	/**
	 * The Map for know which chaotic characters have killed before
	 **/
	private TIntLongHashMap chaoticCharactersKilledBefore = new TIntLongHashMap();
	
	/**
	 * Update reputation for kill a chaotic player
	 **/
	private void updateReputationForKillChaoticPlayer(Player target) {
		long timeRightNow = System.currentTimeMillis(), timeThatCharacterKilledBefore = 0;
		if (chaoticCharactersKilledBefore.contains(target.getObjectId())) {
			timeThatCharacterKilledBefore = chaoticCharactersKilledBefore.get(target.getObjectId());
			if ((timeRightNow - timeThatCharacterKilledBefore) * 1000 >= Config.REPUTATION_SAME_KILL_INTERVAL) // Seconds
			{
				setReputation(getReputation() + Config.REPUTATION_ACQUIRED_FOR_CHAOTIC_KILL);
				
				if (chaoticCharactersKilledBefore.size() >= 10) {
					chaoticCharactersKilledBefore.clear();
				} else {
					chaoticCharactersKilledBefore.remove(target.getObjectId());
				}
				chaoticCharactersKilledBefore.put(target.getObjectId(), timeRightNow);
			}
		} else {
			if (chaoticCharactersKilledBefore.size() >= 10) {
				chaoticCharactersKilledBefore.clear();
			}
			
			setReputation(getReputation() + Config.REPUTATION_ACQUIRED_FOR_CHAOTIC_KILL);
			
			chaoticCharactersKilledBefore.put(target.getObjectId(), timeRightNow);
		}
	}
	
	/** **/
	public void updateReputationForHunting(long exp, int sp) {
		if (reputation >= 0) {
			return;
		}
		
		int lvlSq = getLevel() * getLevel();
		int expDivider = Config.REPUTATION_REP_PER_EXP_MULTIPLIER * lvlSq;
		int spDivider = Config.REPUTATION_REP_PER_SP_MULTIPLIER * lvlSq;
		int reputationToAdd = (int) (exp / expDivider + sp / spDivider);
		int reputation = this.reputation + reputationToAdd;
		// Check if we went over 0 or an integer overflow happened
		if (reputation > 0 || reputation < this.reputation) {
			reputation = 0;
		}
		
		setReputation(reputation);
	}
	
	/**
	 * Return the max weight that the Player can load.<BR><BR>
	 */
	public int getMaxLoad() {
		// Weight Limit = (CON Modifier*69000)*Skills
		// Source http://l2p.bravehost.com/weightlimit.html (May 2007)
		// Fitted exponential curve to the data
		int con = getCON();
		double baseLoad;
		if (con < 1) {
			baseLoad = 31000;
		} else if (con > 59) {
			baseLoad = 176000;
		} else {
			baseLoad = Math.pow(1.029993928, con) * 30495.627366;
		}
		
		return (int) calcStat(Stats.MAX_LOAD, baseLoad * Config.ALT_WEIGHT_LIMIT, this, null);
	}
	
	public int getExpertiseArmorPenalty() {
		return expertiseArmorPenalty;
	}
	
	public int getExpertiseWeaponPenalty() {
		return expertiseWeaponPenalty;
	}
	
	public int getWeightPenalty() {
		if (dietMode) {
			return 0;
		}
		
		return curWeightPenalty;
	}
	
	/**
	 * Update the overloaded status of the Player.<BR><BR>
	 */
	public void refreshOverloaded() {
		int maxLoad = getMaxLoad();
		if (maxLoad > 0) {
			long weightproc = (long) getCurrentLoad() * 1000 / maxLoad;
			int newWeightPenalty;
			if (weightproc < 500 || dietMode) {
				newWeightPenalty = 0;
			} else if (weightproc < 666) {
				newWeightPenalty = 1;
			} else if (weightproc < 800) {
				newWeightPenalty = 2;
			} else if (weightproc < 1000) {
				newWeightPenalty = 3;
			} else {
				newWeightPenalty = 4;
			}
			
			if (curWeightPenalty != newWeightPenalty) {
				curWeightPenalty = newWeightPenalty;
				if (newWeightPenalty > 0 && !dietMode) {
					super.addSkill(SkillTable.getInstance().getInfo(4270, newWeightPenalty));
					setIsOverloaded(getCurrentLoad() > maxLoad);
				} else {
					super.removeSkill(getKnownSkill(4270));
					setIsOverloaded(false);
				}
				sendPacket(new UserInfo(this));
				sendPacket(new ExUserLoad(this));
				sendPacket(new EtcStatusUpdate(this));
				broadcastPacket(new CharInfo(this));
			}
		}
	}
	
	public void refreshExpertisePenalty() {
		if (!Config.EXPERTISE_PENALTY) {
			return;
		}
		
		int armorPenalty = 0;
		int weaponPenalty = 0;
		
		for (Item item : getInventory().getItems()) {
			if (item != null && item.isEquipped() && item.getItemType() != EtcItemType.ARROW && item.getItemType() != EtcItemType.BOLT) {
				int crystaltype = item.getItem().getCrystalType();
				
				if (crystaltype > getExpertiseIndex()) {
					if (item.isWeapon() && crystaltype > weaponPenalty) {
						weaponPenalty = crystaltype;
					} else if (crystaltype > armorPenalty) {
						armorPenalty = crystaltype;
					}
				}
			}
		}
		
		boolean changed = false;
		
		// calc armor penalty
		armorPenalty = armorPenalty - getExpertiseIndex();
		
		if (armorPenalty < 0) {
			armorPenalty = 0;
		} else if (armorPenalty > 4) {
			armorPenalty = 4;
		}
		
		if (getExpertiseArmorPenalty() != armorPenalty || getSkillLevelHash(6213) != armorPenalty) {
			expertiseArmorPenalty = armorPenalty;
			
			if (expertiseArmorPenalty > 0) {
				super.addSkill(SkillTable.getInstance().getInfo(6213, expertiseArmorPenalty)); // level used to be newPenalty
			} else {
				super.removeSkill(getKnownSkill(6213));
			}
			
			changed = true;
		}
		
		// calc weapon penalty
		weaponPenalty = weaponPenalty - getExpertiseIndex();
		if (weaponPenalty < 0) {
			weaponPenalty = 0;
		} else if (weaponPenalty > 4) {
			weaponPenalty = 4;
		}
		
		if (getExpertiseWeaponPenalty() != weaponPenalty || getSkillLevelHash(6209) != weaponPenalty) {
			expertiseWeaponPenalty = weaponPenalty;
			
			if (expertiseWeaponPenalty > 0) {
				super.addSkill(SkillTable.getInstance().getInfo(6209, expertiseWeaponPenalty)); // level used to be newPenalty
			} else {
				super.removeSkill(getKnownSkill(6209));
			}
			
			changed = true;
		}
		
		if (changed) {
			sendPacket(new EtcStatusUpdate(this));
		}
	}
	
	public void checkIfWeaponIsAllowed() {
		// Override for Gamemasters
		if (isGM()) {
			return;
		}
		
		// Iterate through all effects currently on the character.
		for (Abnormal currenteffect : getAllEffects()) {
			Skill effectSkill = currenteffect.getSkill();
			
			// Ignore all buff skills that are party related (ie. songs, dances) while still remaining weapon dependant on cast though.
			if (!effectSkill.isOffensive() &&
					!(effectSkill.getTargetType() == SkillTargetType.TARGET_PARTY && effectSkill.getSkillType() == SkillType.BUFF)) {
				// Check to rest to assure current effect meets weapon requirements.
				if (!effectSkill.getWeaponDependancy(this)) {
					SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S1_CANNOT_BE_USED);
					sm.addSkillName(effectSkill);
					sendPacket(sm);
					
					if (Config.DEBUG) {
						log.info("   | Skill " + effectSkill.getName() + " has been disabled for (" + getName() +
								"); Reason: Incompatible Weapon Type.");
					}
					
					currenteffect.exit();
				}
			}
		}
	}
	
	public void checkSShotsMatch(Item equipped, Item unequipped) {
		if (unequipped == null) {
			return;
		}
		
		unequipped.setChargedSoulShot(Item.CHARGED_NONE);
		unequipped.setChargedSpiritShot(Item.CHARGED_NONE);
		
		// on retail auto shots never disabled on uneqip
		/*if (unequipped.getItem().getType2() == ItemTemplate.TYPE2_WEAPON &&
                (equipped == null ? true : equipped.getItem().getItemGradeSPlus() != unequipped.getItem().getItemGradeSPlus()))
		{
			disableAutoShotByCrystalType(unequipped.getItem().getItemGradeSPlus());
		}*/
	}
	
	public void useEquippableItem(Item item, boolean abortAttack) {
		// Equip or unEquip
		Item[] items = null;
		final boolean isEquiped = item.isEquipped();
		final int oldInvLimit = getInventoryLimit();
		SystemMessage sm = null;
		if ((item.getItem().getBodyPart() & ItemTemplate.SLOT_MULTI_ALLWEAPON) != 0) {
			Item old = getInventory().getPaperdollItem(Inventory.PAPERDOLL_RHAND);
			checkSShotsMatch(item, old);
		}
		
		if (isEquiped) {
			if (item.getEnchantLevel() > 0) {
				sm = SystemMessage.getSystemMessage(SystemMessageId.EQUIPMENT_S1_S2_REMOVED);
				sm.addNumber(item.getEnchantLevel());
				sm.addItemName(item);
			} else {
				sm = SystemMessage.getSystemMessage(SystemMessageId.S1_DISARMED);
				sm.addItemName(item);
			}
			sendPacket(sm);
			
			int slot = getInventory().getSlotFromItem(item);
			// we cant unequip talisman/jewel by body slot
			if (slot == ItemTemplate.SLOT_DECO || slot == ItemTemplate.SLOT_JEWELRY) {
				items = getInventory().unEquipItemInSlotAndRecord(item.getLocationSlot());
			} else {
				items = getInventory().unEquipItemInBodySlotAndRecord(slot);
			}
			
			switch (item.getItemId()) {
				case 15393:
					setPremiumItemId(0);
					break;
				default:
					break;
			}
		} else {
			items = getInventory().equipItemAndRecord(item);
			
			if (item.isEquipped()) {
				if (item.getEnchantLevel() > 0) {
					sm = SystemMessage.getSystemMessage(SystemMessageId.S1_S2_EQUIPPED);
					sm.addNumber(item.getEnchantLevel());
					sm.addItemName(item);
				} else {
					sm = SystemMessage.getSystemMessage(SystemMessageId.S1_EQUIPPED);
					sm.addItemName(item);
				}
				sendPacket(sm);
				
				// Consume mana - will start a task if required; returns if item is not a shadow item
				item.decreaseMana(false);
				
				if ((item.getItem().getBodyPart() & ItemTemplate.SLOT_MULTI_ALLWEAPON) != 0) {
					rechargeAutoSoulShot(true, true, false);
				}
				
				switch (item.getItemId()) {
					case 15393:
						getStat().setVitalityPoints(140000, false, true);
						setPremiumItemId(item.getItemId());
						break;
					default:
						break;
				}
			} else {
				sendPacket(SystemMessageId.CANNOT_EQUIP_ITEM_DUE_TO_BAD_CONDITION);
			}
		}
		
		if (!isUpdateLocked()) {
			refreshExpertisePenalty();
			
			broadcastUserInfo();
			
			InventoryUpdate iu = new InventoryUpdate();
			iu.addItems(Arrays.asList(items));
			sendPacket(iu);
			
			if (abortAttack) {
				abortAttack();
			}
			
			if (getInventoryLimit() != oldInvLimit) {
				sendPacket(new ExStorageMaxCount(this));
			}
		}
	}
	
	/**
	 * Return the the PvP Kills of the Player (Number of player killed during a PvP).<BR><BR>
	 */
	public int getPvpKills() {
		return pvpKills;
	}
	
	/**
	 * Set the the PvP Kills of the Player (Number of player killed during a PvP).<BR><BR>
	 */
	public void setPvpKills(int pvpKills) {
		this.pvpKills = pvpKills;
	}
	
	/**
	 * Return the Fame of this Player <BR><BR>
	 *
	 */
	public int getFame() {
		return fame;
	}
	
	/**
	 * Set the Fame of this L2PcInstane <BR><BR>
	 *
	 */
	public void setFame(int fame) {
		if (fame > Config.MAX_PERSONAL_FAME_POINTS) {
			this.fame = Config.MAX_PERSONAL_FAME_POINTS;
		} else {
			this.fame = fame;
		}
	}
	
	/**
	 * Return the ClassId object of the Player contained in PcTemplate.<BR><BR>
	 */
	public int getClassId() {
		if (temporaryClassId != 0) {
			return temporaryClassId;
		}
		
		return getCurrentClass().getId();
	}
	
	/**
	 * Set the template of the Player.<BR><BR>
	 *
	 * @param id The Identifier of the PcTemplate to set to the Player
	 */
	public void setClassId(int id) {
		if (!subclassLock.tryLock()) {
			return;
		}
		
		try {
			if (getLvlJoinedAcademy() != 0 && clan != null && PlayerClassTable.getInstance().getClassById(id).getLevel() <= 76) {
				if (getLvlJoinedAcademy() <= 36) {
					clan.addReputationScore(Config.JOIN_ACADEMY_MAX_REP_SCORE, true);
				} else if (getLvlJoinedAcademy() >= 84) {
					clan.addReputationScore(Config.JOIN_ACADEMY_MIN_REP_SCORE, true);
				} else {
					clan.addReputationScore(Config.JOIN_ACADEMY_MAX_REP_SCORE - (getLvlJoinedAcademy() - 16) * 20, true);
				}
				setLvlJoinedAcademy(0);
				//oust pledge member from the academy, cuz he has finished his 2nd class transfer
				SystemMessage msg = SystemMessage.getSystemMessage(SystemMessageId.CLAN_MEMBER_S1_EXPELLED);
				msg.addPcName(this);
				clan.broadcastToOnlineMembers(msg);
				clan.broadcastToOnlineMembers(new PledgeShowMemberListDelete(getName()));
				clan.removeClanMember(getObjectId(), 0);
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.ACADEMY_MEMBERSHIP_TERMINATED));
				
				// receive graduation gift
				getInventory().addItem("Gift", 8181, 1, this, null); // give academy circlet
			}
			
			if (isSubClassActive()) {
				getSubClasses().get(classIndex).setClassId(id);
			}
			
			if (PlayerClassTable.getInstance().getClassById(id).getLevel() != 85) {
				setTarget(this);
				broadcastPacket(new MagicSkillUse(this, 5103, 1, 1000, 0));
			}
			setClassTemplate(id);
			if (getCurrentClass().getLevel() == 85) {
				sendPacket(new PlaySound("ItemSound.quest_fanfare_2"));
			} else if (getCurrentClass().getLevel() == 76) {
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.THIRD_CLASS_TRANSFER));
				if (getLevel() >= 85) {
					PlayerClass cl = PlayerClassTable.getInstance().getClassById(getClassId());
					if (cl.getAwakeningClassId() != -1) {
						sendPacket(new ExCallToChangeClass(cl.getId(), false));
					}
				}
			} else {
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CLASS_TRANSFER));
			}
			
			// Give Mentee Certificate
			if (getCurrentClass().getLevel() == 40 && getClassId() == getBaseClass()) {
				getInventory().addItem("Mentee's Certificate", 33800, 1, this, null); // give academy circlet
			}
			
			// Update class icon in party and clan
			if (isInParty()) {
				getParty().broadcastToPartyMembers(new PartySmallWindowUpdate(this));
			}
			
			if (getClan() != null) {
				getClan().broadcastToOnlineMembers(new PledgeShowMemberListUpdate(this));
			}
			
			sendPacket(new ExMentorList(this));
			
			if (getFriendList().size() > 0) {
				for (int i : getFriendList()) {
					if (World.getInstance().getPlayer(i) != null) {
						Player player = World.getInstance().getPlayer(i);
						player.sendPacket(new FriendList(player));
						player.sendPacket(new FriendPacket(true, getObjectId(), player));
					}
				}
			}
			if (isMentee()) {
				Player mentor = World.getInstance().getPlayer(getMentorId());
				if (mentor != null) {
					mentor.sendPacket(new ExMentorList(mentor));
				}
			} else if (isMentor()) {
				for (int objId : getMenteeList()) {
					Player mentee = World.getInstance().getPlayer(objId);
					if (mentee != null) {
						mentee.sendPacket(new ExMentorList(mentee));
					}
				}
			}
			
			rewardSkills();
			if (!isGM() && Config.DECREASE_SKILL_LEVEL) {
				checkPlayerSkills();
			}
			
			for (int i = 0; i < 3; i++) {
				if (!getCurrentClass().getAllowedDyes().contains(henna[i])) {
					removeHenna(i + 1);
				}
			}
			
			if (getIsSummonsInDefendingMode()) {
				setIsSummonsInDefendingMode(false);
			}
			
			int gearGrade = getGearGradeForCurrentLevel();
			
			int[][] gearPreset = getGearPreset(getClassId(), gearGrade);
			
			if (gearPreset != null) {
				equipGearPreset(gearPreset);
			}
		} finally {
			subclassLock.unlock();
		}
	}
	
	/**
	 * Return the Experience of the Player.
	 */
	public long getExp() {
		return getStat().getExp();
	}
	
	public void setActiveEnchantAttrItem(Item stone) {
		activeEnchantAttrItem = stone;
	}
	
	public Item getActiveEnchantAttrItem() {
		return activeEnchantAttrItem;
	}
	
	public void setActiveEnchantItem(Item scroll) {
		// If we dont have a Enchant Item, we are not enchanting.
		if (scroll == null) {
			setActiveEnchantSupportItem(null);
			setActiveEnchantTimestamp(0);
			setIsEnchanting(false);
		}
		activeEnchantItem = scroll;
	}
	
	public Item getActiveEnchantItem() {
		return activeEnchantItem;
	}
	
	public void setActiveEnchantSupportItem(Item item) {
		activeEnchantSupportItem = item;
	}
	
	public Item getActiveEnchantSupportItem() {
		return activeEnchantSupportItem;
	}
	
	public long getActiveEnchantTimestamp() {
		return activeEnchantTimestamp;
	}
	
	public void setActiveEnchantTimestamp(long val) {
		activeEnchantTimestamp = val;
	}
	
	public void setIsEnchanting(boolean val) {
		isEnchanting = val;
	}
	
	public boolean isEnchanting() {
		return isEnchanting;
	}
	
	/**
	 * Set the fists weapon of the Player (used when no weapon is equiped).<BR><BR>
	 *
	 * @param weaponItem The fists WeaponTemplate to set to the Player
	 */
	public void setFistsWeaponItem(WeaponTemplate weaponItem) {
		fistsWeaponItem = weaponItem;
	}
	
	/**
	 * Return the fists weapon of the Player (used when no weapon is equiped).<BR><BR>
	 */
	public WeaponTemplate getFistsWeaponItem() {
		return fistsWeaponItem;
	}
	
	/**
	 * Return the fists weapon of the Player Class (used when no weapon is equiped).<BR><BR>
	 */
	public WeaponTemplate findFistsWeaponItem(int classId) {
		WeaponTemplate weaponItem = null;
		if (classId >= 0x00 && classId <= 0x09) {
			//human Fighter fists
			ItemTemplate temp = ItemTable.getInstance().getTemplate(246);
			weaponItem = (WeaponTemplate) temp;
		} else if (classId >= 0x0a && classId <= 0x11) {
			//human mage fists
			ItemTemplate temp = ItemTable.getInstance().getTemplate(251);
			weaponItem = (WeaponTemplate) temp;
		} else if (classId >= 0x12 && classId <= 0x18) {
			//elven Fighter fists
			ItemTemplate temp = ItemTable.getInstance().getTemplate(244);
			weaponItem = (WeaponTemplate) temp;
		} else if (classId >= 0x19 && classId <= 0x1e) {
			//elven mage fists
			ItemTemplate temp = ItemTable.getInstance().getTemplate(249);
			weaponItem = (WeaponTemplate) temp;
		} else if (classId >= 0x1f && classId <= 0x25) {
			//dark elven Fighter fists
			ItemTemplate temp = ItemTable.getInstance().getTemplate(245);
			weaponItem = (WeaponTemplate) temp;
		} else if (classId >= 0x26 && classId <= 0x2b) {
			//dark elven mage fists
			ItemTemplate temp = ItemTable.getInstance().getTemplate(250);
			weaponItem = (WeaponTemplate) temp;
		} else if (classId >= 0x2c && classId <= 0x30) {
			//orc Fighter fists
			ItemTemplate temp = ItemTable.getInstance().getTemplate(248);
			weaponItem = (WeaponTemplate) temp;
		} else if (classId >= 0x31 && classId <= 0x34) {
			//orc mage fists
			ItemTemplate temp = ItemTable.getInstance().getTemplate(252);
			weaponItem = (WeaponTemplate) temp;
		} else if (classId >= 0x35 && classId <= 0x39) {
			//dwarven fists
			ItemTemplate temp = ItemTable.getInstance().getTemplate(247);
			weaponItem = (WeaponTemplate) temp;
		}
		
		return weaponItem;
	}
	
	/**
	 * Give Expertise skill of this level and remove beginner Lucky skill.<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Get the Level of the Player </li>
	 * <li>If Player Level is 5, remove beginner Lucky skill </li>
	 * <li>Add the Expertise skill corresponding to its Expertise level</li>
	 * <li>Update the overloaded status of the Player</li><BR><BR>
	 * <p>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T give other free skills (SP needed = 0)</B></FONT><BR><BR>
	 */
	public void rewardSkills() {
		// Get the Level of the Player
		int lvl = getLevel();
		
		// Remove beginner Lucky skill
		if (lvl == 10) {
			Skill skill = SkillTable.FrequentSkill.LUCKY.getSkill();
			skill = removeSkill(skill);
			
			if (Config.DEBUG && skill != null) {
				log.debug("removed skill 'Lucky' from " + getName());
			}
		}
		
		// Calculate the current higher Expertise of the Player
		for (int i = 0; i < EXPERTISE_LEVELS.length; i++) {
			if (lvl >= EXPERTISE_LEVELS[i]) {
				setExpertiseIndex(i);
			}
		}
		
		// Add the Expertise skill corresponding to its Expertise level
		if (getExpertiseIndex() > 0) {
			Skill skill = SkillTable.getInstance().getInfo(239, getExpertiseIndex());
			addSkill(skill, true);
			
			if (Config.DEBUG) {
				log.debug("awarded " + getName() + " with new expertise.");
			}
		} else {
			removeSkill(239);
			if (Config.DEBUG) {
				log.debug("No skills awarded at lvl: " + lvl);
			}
		}
		
		//Active skill dwarven craft
		if (getSkillLevelHash(172) < 10 && getClassId() == 156) // Tyrr Maestro
		{
			Skill skill = SkillTable.FrequentSkill.MAESTRO_CREATE_ITEM.getSkill();
			addSkill(skill, true);
		}
		
		//Active skill dwarven craft
		if (getSkillLevelHash(1321) < 1 && (getRace() == Race.Dwarf && getCurrentClass().getLevel() < 85 || getCurrentClass().getId() == 156)) {
			Skill skill = SkillTable.FrequentSkill.DWARVEN_CRAFT.getSkill();
			addSkill(skill, true);
		}
		
		//Active skill common craft
		if (!Config.IS_CLASSIC) {
			if (getSkillLevelHash(1322) < 1) {
				Skill skill = SkillTable.FrequentSkill.COMMON_CRAFT.getSkill();
				addSkill(skill, true);
			}
			
			for (int i = 0; i < COMMON_CRAFT_LEVELS.length; i++) {
				if (lvl >= COMMON_CRAFT_LEVELS[i] && getSkillLevelHash(1320) < i + 1) {
					Skill skill = SkillTable.getInstance().getInfo(1320, i + 1);
					addSkill(skill, true);
				}
			}
		}
		
		// Autoget skills
		for (L2SkillLearn s : SkillTreeTable.getInstance().getAvailableClassSkills(this)) {
			if (!s.isAutoGetSkill() || s.getMinLevel() > getLevel()) {
				continue;
			}
			
			addSkill(SkillTable.getInstance().getInfo(s.getId(), s.getLevel()), true);
		}
		
		// Auto-Learn skills if activated
		if (Config.AUTO_LEARN_SKILLS) {
			giveAvailableSkills(false);
		}
		
		checkItemRestriction();
		sendSkillList();
	}
	
	/**
	 * Regive all skills which aren't saved to database, like Noble, Hero, Clan Skills<BR><BR>
	 */
	public void regiveTemporarySkills() {
		// Do not call this on enterworld or char load
		
		// Add noble skills if noble
		if (isNoble()) {
			setNoble(true);
		}
		
		// Add Hero skills if hero
		if (isHero()) {
			setHero(true);
		}
		
		// Add Mentor skills if player is mentor
		if (isMentor()) {
			giveMentorSkills();
		}
		
		// Add Mentee skill if player is mentee
		if (!canBeMentor() && isMentee()) {
			giveMenteeSkills();
		}
		
		// Add clan skills
		if (getClan() != null) {
			L2Clan clan = getClan();
			clan.addSkillEffects(this);
			
			if (clan.getLevel() >= CastleSiegeManager.getInstance().getSiegeClanMinLevel() && isClanLeader()) {
				CastleSiegeManager.getInstance().addSiegeSkills(this);
			}
			if (getClan().getHasCastle() > 0) {
				CastleManager.getInstance().getCastleByOwner(getClan()).giveResidentialSkills(this);
			}
			if (getClan().getHasFort() > 0) {
				FortManager.getInstance().getFortByOwner(getClan()).giveResidentialSkills(this);
			}
		}
		
		// Reload passive skills from armors / jewels / weapons
		getInventory().reloadEquippedItems();
	}
	
	/**
	 * Give all available skills to the player.<br><br>
	 */
	public int giveAvailableSkills(boolean forceAll) {
		//if (getRace() == Race.Ertheia && !forceAll)
		//	return 0;
		
		int unLearnable = 0;
		int skillCounter = 0;
		
		// Get available skills
		L2SkillLearn[] skills = SkillTreeTable.getInstance().getAvailableClassSkills(this);
		while (skills.length > unLearnable) {
			unLearnable = 0;
			for (L2SkillLearn s : skills) {
				if (s.getMinLevel() > getLevel() || !s.getCostSkills().isEmpty() && !forceAll || s.isRemember()) {
					unLearnable++;
					continue;
				}
				
				Skill sk = SkillTable.getInstance().getInfo(s.getId(), s.getLevel());
				if (sk == null || (sk.getId() == Skill.SKILL_DIVINE_INSPIRATION || sk.getId() == Skill.SKILL_DIVINE_EXPANSION) &&
						!Config.AUTO_LEARN_DIVINE_INSPIRATION && !isGM()) {
					unLearnable++;
					continue;
				}
				
				if (getSkillLevelHash(sk.getId()) == -1) {
					skillCounter++;
				}
				
				// fix when learning toggle skills
				if (sk.isToggle()) {
					Abnormal toggleEffect = getFirstEffect(sk.getId());
					if (toggleEffect != null) {
						// stop old toggle skill effect, and give new toggle skill effect back
						toggleEffect.exit();
						sk.getEffects(this, this);
					}
				}
				
				List<Integer> reqSkillIds = s.getCostSkills();
				if (reqSkillIds != null && !reqSkillIds.isEmpty()) {
					for (Skill sk2 : getAllSkills()) {
						for (int reqSkillId : reqSkillIds) {
							if (sk2.getId() == reqSkillId) {
								removeSkill(sk2);
							}
						}
					}
				}
				
				addSkill(sk, true);
			}
			
			// Get new available skills
			skills = SkillTreeTable.getInstance().getAvailableClassSkills(this);
		}
		
		if (skillCounter > 0) {
			sendMessage("You have learned " + skillCounter + " new skills.");
		}
		
		return skillCounter;
	}
	
	@SuppressWarnings("unused")
	public final void giveSkills(boolean learnAvailable) {
		// Get the Level of the Player
		int lvl = getLevel();

		/*
        // Remove beginner Lucky skill
		if (lvl == 10)
		{
			Skill skill = SkillTable.getInstance().getInfo(194, 1);

			skill = removeSkill(skill);
		}

		// Calculate the current higher Expertise of the Player
		for (int i = 0; i < EXPERTISE_LEVELS.length; i++)
		{
			if (lvl >= EXPERTISE_LEVELS[i])
				setExpertiseIndex(i);
		}

		// Add the Expertise skill corresponding to its Expertise level
		if (getExpertiseIndex() > 0)
		{
			Skill skill = SkillTable.getInstance().getInfo(239, getExpertiseIndex());

			addSkill(skill, true);
		}

		//Active skill dwarven craft
		if (getSkillLevel(1321) < 1 && getRace() == Race.Dwarf)
		{
			Skill skill = SkillTable.getInstance().getInfo(1321, 1);

			addSkill(skill, true);
		}

		//Active skill common craft
		if (getSkillLevel(1322) < 1)
		{
			Skill skill = SkillTable.getInstance().getInfo(1322, 1);

			addSkill(skill, true);
		}

		for (int i = 0; i < COMMON_CRAFT_LEVELS.length; i++)
		{
			if (lvl >= COMMON_CRAFT_LEVELS[i] && getSkillLevel(1320) < (i + 1))
			{
				Skill skill = SkillTable.getInstance().getInfo(1320, (i + 1));

				addSkill(skill, true);
			}
		}*/
		
		if (learnAvailable) {
			int unLearnable = 0;
			int skillCounter = 0;
			boolean forceAll = true;
			
			// Get available skills
			L2SkillLearn[] skills = SkillTreeTable.getInstance().getAvailableSkillsForPlayer(this, true, true);
			int newlyLearnedSkills = 0;
			for (L2SkillLearn skill : skills) {
				Skill sk = SkillTable.getInstance().getInfo(skill.getId(), skill.getLevel());
				if (sk == null || sk.getId() == Skill.SKILL_DIVINE_INSPIRATION && !Config.AUTO_LEARN_DIVINE_INSPIRATION ||
						sk.getId() >= 23500 && sk.getId() <= 23511) {
					continue;
				}
				
				if (getSkillLevelHash(sk.getId()) == -1) {
					newlyLearnedSkills++;
				}
				
				// fix when learning toggle skills
				if (sk.isToggle()) {
					Abnormal toggleEffect = getFirstEffect(sk.getId());
					if (toggleEffect != null) {
						// stop old toggle skill effect, and give new toggle skill effect back
						toggleEffect.exit();
						sk.getEffects(this, this);
					}
				}
				
				List<Integer> reqSkillIds = skill.getCostSkills();
				if (reqSkillIds != null && !reqSkillIds.isEmpty()) {
					for (Skill sk2 : getAllSkills()) {
						for (int reqSkillId : reqSkillIds) {
							if (sk2.getId() == reqSkillId) {
								removeSkill(sk2);
							}
						}
					}
				}
				
				addSkill(sk, true);
			}
			
			sendMessage("You have learned " + newlyLearnedSkills + " new skills.");
		}
		
		checkItemRestriction();
		sendSkillList();
		
		// This function gets called on login, so not such a bad place to check weight
		refreshOverloaded(); // Update the overloaded status of the Player
		refreshExpertisePenalty(); // Update the expertise status of the Player
	}
	
	/**
	 * Set the Experience value of the Player.
	 */
	public void setExp(long exp) {
		if (exp < 0) {
			exp = 0;
		}
		
		getStat().setExp(exp);
	}
	
	/**
	 * Return the Race object of the Player.<BR><BR>
	 */
	public Race getRace() {
		return getTemplate().race;
	}
	
	public L2Radar getRadar() {
		return radar;
	}
	
	/**
	 * Return the SP amount of the Player.
	 */
	public long getSp() {
		return getStat().getSp();
	}
	
	/**
	 * Set the SP amount of the Player.
	 */
	public void setSp(long sp) {
		if (sp < 0) {
			sp = 0;
		}
		
		super.getStat().setSp(sp);
	}
	
	/**
	 * Return true if this Player is a clan leader in
	 * ownership of the passed castle
	 */
	public boolean isCastleLord(int castleId) {
		L2Clan clan = getClan();
		
		// player has clan and is the clan leader, check the castle info
		if (clan != null && clan.getLeader().getPlayerInstance() == this) {
			// if the clan has a castle and it is actually the queried castle, return true
			Castle castle = CastleManager.getInstance().getCastleByOwner(clan);
			if (castle != null && castle == CastleManager.getInstance().getCastleById(castleId)) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Return the Clan Identifier of the Player.<BR><BR>
	 */
	public int getClanId() {
		return clanId;
	}
	
	/**
	 * Return the Clan Crest Identifier of the Player or 0.<BR><BR>
	 */
	public int getClanCrestId() {
		if (clan != null) {
			return clan.getCrestId();
		}
		
		return 0;
	}
	
	/**
	 * @return The Clan CrestLarge Identifier or 0
	 */
	public int getClanCrestLargeId() {
		if (clan != null) {
			return clan.getLargeCrestId();
		}
		
		return 0;
	}
	
	public long getClanJoinExpiryTime() {
		return clanJoinExpiryTime;
	}
	
	public void setClanJoinExpiryTime(long time) {
		clanJoinExpiryTime = time;
	}
	
	public long getClanCreateExpiryTime() {
		return clanCreateExpiryTime;
	}
	
	public void setClanCreateExpiryTime(long time) {
		clanCreateExpiryTime = time;
	}
	
	public void setOnlineTime(long time) {
		onlineTime = time;
		onlineBeginTime = System.currentTimeMillis();
	}
	
	public long getZoneRestartLimitTime() {
		return zoneRestartLimitTime;
	}
	
	public void setZoneRestartLimitTime(long time) {
		zoneRestartLimitTime = time;
	}
	
	public void storeZoneRestartLimitTime() {
		if (isInsideZone(Creature.ZONE_NORESTART)) {
			NoRestartZone zone = null;
			for (ZoneType tmpzone : ZoneManager.getInstance().getZones(this)) {
				if (tmpzone instanceof NoRestartZone) {
					zone = (NoRestartZone) tmpzone;
					break;
				}
			}
			if (zone != null) {
				Connection con = null;
				try {
					con = L2DatabaseFactory.getInstance().getConnection();
					final PreparedStatement statement = con.prepareStatement(UPDATE_ZONE_RESTART_LIMIT);
					statement.setInt(1, getObjectId());
					statement.setLong(2, System.currentTimeMillis() + zone.getRestartAllowedTime() * 1000);
					statement.execute();
					statement.close();
				} catch (SQLException e) {
					log.warn("Cannot store zone norestart limit for character " + getObjectId(), e);
				} finally {
					L2DatabaseFactory.close(con);
				}
			}
		}
	}
	
	private void restoreZoneRestartLimitTime() {
		Connection con = null;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(LOAD_ZONE_RESTART_LIMIT);
			statement.setInt(1, getObjectId());
			final ResultSet rset = statement.executeQuery();
			
			if (rset.next()) {
				setZoneRestartLimitTime(rset.getLong("time_limit"));
				statement.close();
				statement = con.prepareStatement(DELETE_ZONE_RESTART_LIMIT);
				statement.setInt(1, getObjectId());
				statement.executeUpdate();
			}
			rset.close();
			statement.close();
		} catch (Exception e) {
			log.warn("Could not restore " + this + " zone restart time: " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * Return the PcInventory Inventory of the Player contained in inventory.<BR><BR>
	 */
	@Override
	public PcInventory getInventory() {
		return inventory;
	}
	
	public PcAuction getAuctionInventory() {
		return auctionInventory;
	}
	
	/**
	 * Delete a ShortCut of the Player shortCuts.<BR><BR>
	 */
	public void removeItemFromShortCut(int objectId) {
		shortCuts.deleteShortCutByObjectId(objectId);
	}
	
	/**
	 * Return True if the Player is sitting.<BR><BR>
	 */
	public boolean isSitting() {
		return waitTypeSitting;
	}
	
	/**
	 * Set waitTypeSitting to given value
	 */
	public void setIsSitting(boolean state) {
		waitTypeSitting = state;
	}
	
	/**
	 * Sit down the Player, set the AI Intention to AI_INTENTION_REST and send a Server->Client ChangeWaitType packet (broadcast)<BR><BR>
	 */
	public void sitDown() {
		sitDown(true);
	}
	
	public void sitDown(boolean checkCast) {
		if (checkCast && isCastingNow()) {
			sendMessage("Cannot sit while casting");
			return;
		}
		
		if (!waitTypeSitting && !isAttackingDisabled() && !isOutOfControl() && !isImmobilized()) {
			breakAttack();
			setIsSitting(true);
			broadcastPacket(new ChangeWaitType(this, ChangeWaitType.WT_SITTING));
			// Schedule a sit down task to wait for the animation to finish
			ThreadPoolManager.getInstance().scheduleGeneral(new SitDownTask(), 2500);
			setIsParalyzed(true);
		}
	}
	
	/**
	 * Sit down Task
	 */
	private class SitDownTask implements Runnable {
		@Override
		public void run() {
			setIsParalyzed(false);
			getAI().setIntention(CtrlIntention.AI_INTENTION_REST);
		}
	}
	
	/**
	 * Stand up Task
	 */
	private class StandUpTask implements Runnable {
		@Override
		public void run() {
			setIsSitting(false);
			getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		}
	}
	
	/**
	 * Stand up the Player, set the AI Intention to AI_INTENTION_IDLE and send a Server->Client ChangeWaitType packet (broadcast)<BR><BR>
	 */
	public void standUp() {
		if (waitTypeSitting && !isInStoreMode() && !isAlikeDead()) {
			if (effects.isAffected(EffectType.RELAXING.getMask())) {
				stopEffects(EffectType.RELAXING);
			}
			
			broadcastPacket(new ChangeWaitType(this, ChangeWaitType.WT_STANDING));
			// Schedule a stand up task to wait for the animation to finish
			ThreadPoolManager.getInstance().scheduleGeneral(new StandUpTask(), 2500);
		}
	}
	
	/**
	 * Return the PcWarehouse object of the Player.<BR><BR>
	 */
	public PcWarehouse getWarehouse() {
		if (warehouse == null) {
			warehouse = new PcWarehouse(this);
			warehouse.restore();
		}
		if (Config.WAREHOUSE_CACHE) {
			WarehouseCacheManager.getInstance().addCacheTask(this);
		}
		return warehouse;
	}
	
	/**
	 * Free memory used by Warehouse
	 */
	public void clearWarehouse() {
		if (warehouse != null) {
			warehouse.deleteMe();
		}
		warehouse = null;
	}
	
	/**
	 * Returns true if refund list is not empty
	 */
	public boolean hasRefund() {
		return refund != null && refund.getSize() > 0 && Config.ALLOW_REFUND;
	}
	
	/**
	 * Returns refund object or create new if not exist
	 */
	public PcRefund getRefund() {
		if (refund == null) {
			refund = new PcRefund(this);
		}
		return refund;
	}
	
	/**
	 * Clear refund
	 */
	public void clearRefund() {
		if (refund != null) {
			refund.deleteMe();
		}
		refund = null;
	}
	
	/**
	 * Return the Identifier of the Player.<BR><BR>
	 */
	@Deprecated
	public int getCharId() {
		return charId;
	}
	
	/**
	 * Set the Identifier of the Player.<BR><BR>
	 */
	public void setCharId(int charId) {
		this.charId = charId;
	}
	
	/**
	 * Return the Adena amount of the Player.<BR><BR>
	 */
	public long getAdena() {
		return inventory.getAdena();
	}
	
	/**
	 * Return the Ancient Adena amount of the Player.<BR><BR>
	 */
	public long getAncientAdena() {
		return inventory.getAncientAdena();
	}
	
	/**
	 * Add adena to Inventory of the Player and send a Server->Client InventoryUpdate packet to the Player.
	 *
	 * @param process     : String Identifier of process triggering this action
	 * @param count       : int Quantity of adena to be added
	 * @param reference   : WorldObject Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 */
	public void addAdena(String process, long count, WorldObject reference, boolean sendMessage) {
		if (sendMessage) {
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.EARNED_S1_ADENA);
			sm.addItemNumber(count);
			sendPacket(sm);
		}
		
		if (count > 0) {
			inventory.addAdena(process, count, this, reference);
			
			// Send update packet
			if (!Config.FORCE_INVENTORY_UPDATE) {
				InventoryUpdate iu = new InventoryUpdate();
				iu.addItem(inventory.getAdenaInstance());
				sendPacket(iu);
			} else {
				sendPacket(new ItemList(this, false));
			}
			
			sendPacket(new ExAdenaInvenCount(getAdena(), getInventory().getSize(false)));
		}
	}
	
	/**
	 * Reduce adena in Inventory of the Player and send a Server->Client InventoryUpdate packet to the Player.
	 *
	 * @param process     : String Identifier of process triggering this action
	 * @param count       : long Quantity of adena to be reduced
	 * @param reference   : WorldObject Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 * @return boolean informing if the action was successfull
	 */
	public boolean reduceAdena(String process, long count, WorldObject reference, boolean sendMessage) {
		if (count > getAdena()) {
			if (sendMessage) {
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_NOT_ENOUGH_ADENA));
			}
			return false;
		}
		
		if (count > 0) {
			Item adenaItem = inventory.getAdenaInstance();
			if (!inventory.reduceAdena(process, count, this, reference)) {
				return false;
			}
			
			// Send update packet
			if (!Config.FORCE_INVENTORY_UPDATE) {
				InventoryUpdate iu = new InventoryUpdate();
				iu.addItem(adenaItem);
				sendPacket(iu);
			} else {
				sendPacket(new ItemList(this, false));
			}
			sendPacket(new ExAdenaInvenCount(getAdena(), getInventory().getSize(false)));
			
			if (sendMessage) {
				SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S1_DISAPPEARED_ADENA);
				sm.addItemNumber(count);
				sendPacket(sm);
			}
		}
		
		return true;
	}
	
	/**
	 * Add ancient adena to Inventory of the Player and send a Server->Client InventoryUpdate packet to the Player.
	 *
	 * @param process     : String Identifier of process triggering this action
	 * @param count       : int Quantity of ancient adena to be added
	 * @param reference   : WorldObject Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 */
	public void addAncientAdena(String process, long count, WorldObject reference, boolean sendMessage) {
		if (sendMessage) {
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.EARNED_S2_S1_S);
			sm.addItemName(PcInventory.ANCIENT_ADENA_ID);
			sm.addItemNumber(count);
			sendPacket(sm);
		}
		
		if (count > 0) {
			inventory.addAncientAdena(process, count, this, reference);
			
			if (!Config.FORCE_INVENTORY_UPDATE) {
				InventoryUpdate iu = new InventoryUpdate();
				iu.addItem(inventory.getAncientAdenaInstance());
				sendPacket(iu);
			} else {
				sendPacket(new ItemList(this, false));
			}
		}
	}
	
	/**
	 * Reduce ancient adena in Inventory of the Player and send a Server->Client InventoryUpdate packet to the Player.
	 *
	 * @param process     : String Identifier of process triggering this action
	 * @param count       : long Quantity of ancient adena to be reduced
	 * @param reference   : WorldObject Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 * @return boolean informing if the action was successfull
	 */
	public boolean reduceAncientAdena(String process, long count, WorldObject reference, boolean sendMessage) {
		if (count > getAncientAdena()) {
			if (sendMessage) {
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_NOT_ENOUGH_ADENA));
			}
			
			return false;
		}
		
		if (count > 0) {
			Item ancientAdenaItem = inventory.getAncientAdenaInstance();
			if (!inventory.reduceAncientAdena(process, count, this, reference)) {
				return false;
			}
			
			if (!Config.FORCE_INVENTORY_UPDATE) {
				InventoryUpdate iu = new InventoryUpdate();
				iu.addItem(ancientAdenaItem);
				sendPacket(iu);
			} else {
				sendPacket(new ItemList(this, false));
			}
			
			if (sendMessage) {
				if (count > 1) {
					SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
					sm.addItemName(PcInventory.ANCIENT_ADENA_ID);
					sm.addItemNumber(count);
					sendPacket(sm);
				} else {
					SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S1_DISAPPEARED);
					sm.addItemName(PcInventory.ANCIENT_ADENA_ID);
					sendPacket(sm);
				}
			}
		}
		
		return true;
	}
	
	/**
	 * Adds item to inventory and send a Server->Client InventoryUpdate packet to the Player.
	 *
	 * @param process     : String Identifier of process triggering this action
	 * @param item        : Item to be added
	 * @param reference   : WorldObject Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 */
	public void addItem(String process, Item item, WorldObject reference, boolean sendMessage) {
		if (item.getCount() > 0) {
			// Sends message to client if requested
			if (sendMessage) {
				if (item.getCount() > 1) {
					SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.YOU_PICKED_UP_S1_S2);
					sm.addItemName(item);
					sm.addItemNumber(item.getCount());
					sendPacket(sm);
				} else if (item.getEnchantLevel() > 0) {
					SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.YOU_PICKED_UP_A_S1_S2);
					sm.addNumber(item.getEnchantLevel());
					sm.addItemName(item);
					sendPacket(sm);
				} else {
					SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.YOU_PICKED_UP_S1);
					sm.addItemName(item);
					sendPacket(sm);
				}
			}
			
			// Add the item to inventory
			Item newitem = inventory.addItem(process, item, this, reference);
			
			// Send inventory update packet
			if (!Config.FORCE_INVENTORY_UPDATE) {
				InventoryUpdate playerIU = new InventoryUpdate();
				playerIU.addItem(newitem);
				sendPacket(playerIU);
			} else {
				sendPacket(new ItemList(this, false));
			}
			
			if (item.getItemId() == 57) {
				sendPacket(new ExAdenaInvenCount(getAdena(), getInventory().getSize(false)));
			}
			
			// Update current load as well
			StatusUpdate su = new StatusUpdate(this);
			su.addAttribute(StatusUpdate.CUR_LOAD, getCurrentLoad());
			sendPacket(su);
			
			// If over capacity, drop the item
			if (!isGM() && !inventory.validateCapacity(0, item.isQuestItem()) && newitem.isDropable() && !item.isEquipable() &&
					(!newitem.isStackable() || newitem.getLastChange() != Item.MODIFIED)) {
				dropItem("InvDrop", newitem, null, true);
			}
			
			// Cursed Weapon
			else if (CursedWeaponsManager.getInstance().isCursed(newitem.getItemId())) {
				CursedWeaponsManager.getInstance().activate(this, newitem);
			}
			
			// Combat Flag
			else if (FortSiegeManager.getInstance().isCombat(item.getItemId())) {
				if (FortSiegeManager.getInstance().activateCombatFlag(this, item)) {
					Fort fort = FortManager.getInstance().getFort(this);
					fort.getSiege().announceToPlayer(SystemMessage.getSystemMessage(SystemMessageId.C1_ACQUIRED_THE_FLAG), getName());
				}
			}
		}
	}
	
	/**
	 * Adds item to Inventory and send a Server->Client InventoryUpdate packet to the Player.
	 *
	 * @param process     : String Identifier of process triggering this action
	 * @param itemId      : int Item Identifier of the item to be added
	 * @param count       : long Quantity of items to be added
	 * @param reference   : WorldObject Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 */
	public Item addItem(String process, int itemId, long count, WorldObject reference, boolean sendMessage) {
		if (count > 0) {
			Item item = null;
			if (ItemTable.getInstance().getTemplate(itemId) != null) {
				item = ItemTable.getInstance().createDummyItem(itemId);
			} else {
				log.error("Item doesn't exist so cannot be added. Item ID: " + itemId);
				return null;
			}
			// Sends message to client if requested
			if (sendMessage && (!isCastingNow() && item.getItemType() == EtcItemType.HERB || item.getItemType() != EtcItemType.HERB)) {
				if (count > 1) {
					if (process.equalsIgnoreCase("sweep") || process.equalsIgnoreCase("Quest")) {
						SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.EARNED_S2_S1_S);
						sm.addItemName(itemId);
						sm.addItemNumber(count);
						sendPacket(sm);
					} else {
						SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.YOU_PICKED_UP_S1_S2);
						sm.addItemName(itemId);
						sm.addItemNumber(count);
						sendPacket(sm);
					}
				} else {
					if (process.equalsIgnoreCase("sweep") || process.equalsIgnoreCase("Quest")) {
						SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.EARNED_ITEM_S1);
						sm.addItemName(itemId);
						sendPacket(sm);
					} else {
						SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.YOU_PICKED_UP_S1);
						sm.addItemName(itemId);
						sendPacket(sm);
					}
				}
			}
			//Auto use herbs - autoloot
			if (item.getItemType() == EtcItemType.HERB) //If item is herb dont add it to iv :]
			{
				if (!isCastingNow()) {
					Item herb = new Item(charId, itemId);
					IItemHandler handler = ItemHandler.getInstance().getItemHandler(herb.getEtcItem());
					if (handler == null) {
						log.warn("No item handler registered for Herb - item ID " + herb.getItemId() + ".");
					} else {
						handler.useItem(this, herb, false);
						if (herbstask >= 100) {
							herbstask -= 100;
						}
					}
				} else {
					herbstask += 100;
					ThreadPoolManager.getInstance().scheduleAi(new HerbTask(process, itemId, count, reference, sendMessage), herbstask);
				}
			} else {
				// Add the item to inventory
				Item createdItem = inventory.addItem(process, itemId, count, this, reference);
				
				// If over capacity, drop the item
				if (!isGM() && !inventory.validateCapacity(0, item.isQuestItem()) && createdItem.isDropable() && !item.isEquipable() &&
						(!createdItem.isStackable() || createdItem.getLastChange() != Item.MODIFIED)) {
					dropItem("InvDrop", createdItem, null, true);
				}
				
				// Cursed Weapon
				else if (CursedWeaponsManager.getInstance().isCursed(createdItem.getItemId())) {
					CursedWeaponsManager.getInstance().activate(this, createdItem);
				}
				
				// Combat Flag
				else if (FortSiegeManager.getInstance().isCombat(createdItem.getItemId())) {
					if (FortSiegeManager.getInstance().activateCombatFlag(this, item)) {
						Fort fort = FortManager.getInstance().getFort(this);
						fort.getSiege().announceToPlayer(SystemMessage.getSystemMessage(SystemMessageId.C1_ACQUIRED_THE_FLAG), getName());
					}
				}
				
				return createdItem;
			}
		}
		return null;
	}
	
	/**
	 * Destroy item from inventory and send a Server->Client InventoryUpdate packet to the Player.
	 *
	 * @param process     : String Identifier of process triggering this action
	 * @param item        : Item to be destroyed
	 * @param reference   : WorldObject Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 * @return boolean informing if the action was successfull
	 */
	public boolean destroyItem(String process, Item item, WorldObject reference, boolean sendMessage) {
		return this.destroyItem(process, item, item.getCount(), reference, sendMessage);
	}
	
	/**
	 * Destroy item from inventory and send a Server->Client InventoryUpdate packet to the Player.
	 *
	 * @param process     : String Identifier of process triggering this action
	 * @param item        : Item to be destroyed
	 * @param reference   : WorldObject Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 * @return boolean informing if the action was successfull
	 */
	public boolean destroyItem(String process, Item item, long count, WorldObject reference, boolean sendMessage) {
		item = inventory.destroyItem(process, item, count, this, reference);
		
		if (item == null) {
			if (sendMessage) {
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NOT_ENOUGH_ITEMS));
			}
			return false;
		}
		
		// Send inventory update packet
		if (!Config.FORCE_INVENTORY_UPDATE) {
			InventoryUpdate playerIU = new InventoryUpdate();
			playerIU.addItem(item);
			sendPacket(playerIU);
		} else {
			sendPacket(new ItemList(this, false));
		}
		
		if (item.getItemId() == 57) {
			sendPacket(new ExAdenaInvenCount(getAdena(), getInventory().getSize(false)));
		}
		
		// Update current load as well
		StatusUpdate su = new StatusUpdate(this);
		su.addAttribute(StatusUpdate.CUR_LOAD, getCurrentLoad());
		sendPacket(su);
		
		// Sends message to client if requested
		if (sendMessage) {
			if (count > 1) {
				SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
				sm.addItemName(item);
				sm.addItemNumber(count);
				sendPacket(sm);
			} else {
				SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S1_DISAPPEARED);
				sm.addItemName(item);
				sendPacket(sm);
			}
		}
		
		return true;
	}
	
	/**
	 * Destroys item from inventory and send a Server->Client InventoryUpdate packet to the Player.
	 *
	 * @param process     : String Identifier of process triggering this action
	 * @param objectId    : int Item Instance identifier of the item to be destroyed
	 * @param count       : int Quantity of items to be destroyed
	 * @param reference   : WorldObject Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 * @return boolean informing if the action was successfull
	 */
	@Override
	public boolean destroyItem(String process, int objectId, long count, WorldObject reference, boolean sendMessage) {
		Item item = inventory.getItemByObjectId(objectId);
		if (item == null) {
			if (sendMessage) {
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NOT_ENOUGH_ITEMS));
			}
			
			return false;
		}
		return this.destroyItem(process, item, count, reference, sendMessage);
	}
	
	/**
	 * Destroys shots from inventory without logging and only occasional saving to database.
	 * Sends a Server->Client InventoryUpdate packet to the Player.
	 *
	 * @param process     : String Identifier of process triggering this action
	 * @param objectId    : int Item Instance identifier of the item to be destroyed
	 * @param count       : int Quantity of items to be destroyed
	 * @param reference   : WorldObject Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 * @return boolean informing if the action was successfull
	 */
	public boolean destroyItemWithoutTrace(String process, int objectId, long count, WorldObject reference, boolean sendMessage) {
		Item item = inventory.getItemByObjectId(objectId);
		
		if (item == null || item.getCount() < count) {
			if (sendMessage) {
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NOT_ENOUGH_ITEMS));
			}
			
			return false;
		}
		
		return destroyItem(process, item, count, reference, sendMessage);
	}
	
	/**
	 * Destroy item from inventory by using its <B>itemId</B> and send a Server->Client InventoryUpdate packet to the Player.
	 *
	 * @param process     : String Identifier of process triggering this action
	 * @param itemId      : int Item identifier of the item to be destroyed
	 * @param count       : int Quantity of items to be destroyed
	 * @param reference   : WorldObject Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 * @return boolean informing if the action was successfull
	 */
	@Override
	public boolean destroyItemByItemId(String process, int itemId, long count, WorldObject reference, boolean sendMessage) {
		if (itemId == 57) {
			return reduceAdena(process, count, reference, sendMessage);
		}
		
		Item item = inventory.getItemByItemId(itemId);
		
		if (item == null || item.getCount() < count || inventory.destroyItemByItemId(process, itemId, count, this, reference) == null) {
			if (sendMessage) {
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NOT_ENOUGH_ITEMS));
			}
			
			return false;
		}
		
		// Send inventory update packet
		if (!Config.FORCE_INVENTORY_UPDATE) {
			InventoryUpdate playerIU = new InventoryUpdate();
			playerIU.addItem(item);
			sendPacket(playerIU);
		} else {
			sendPacket(new ItemList(this, false));
		}
		
		if (item.getItemId() == 57) {
			sendPacket(new ExAdenaInvenCount(getAdena(), getInventory().getSize(false)));
		}
		
		// Update current load as well
		StatusUpdate su = new StatusUpdate(this);
		su.addAttribute(StatusUpdate.CUR_LOAD, getCurrentLoad());
		sendPacket(su);
		
		// Sends message to client if requested
		if (sendMessage) {
			if (count > 1) {
				SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
				sm.addItemName(itemId);
				sm.addItemNumber(count);
				sendPacket(sm);
			} else {
				SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S1_DISAPPEARED);
				sm.addItemName(itemId);
				sendPacket(sm);
			}
		}
		
		return true;
	}
	
	/**
	 * Transfers item to another ItemContainer and send a Server->Client InventoryUpdate packet to the Player.
	 *
	 * @param process   : String Identifier of process triggering this action
	 * @param objectId  : int Item Identifier of the item to be transfered
	 * @param count     : long Quantity of items to be transfered
	 * @param reference : WorldObject Object referencing current action like NPC selling item or previous item in transformation
	 * @return Item corresponding to the new item or the updated item in inventory
	 */
	public Item transferItem(String process, int objectId, long count, Inventory target, WorldObject reference) {
		Item oldItem = checkItemManipulation(objectId, count, "transfer");
		if (oldItem == null) {
			return null;
		}
		Item newItem = getInventory().transferItem(process, objectId, count, target, this, reference);
		if (newItem == null) {
			return null;
		}
		
		// Send inventory update packet
		if (!Config.FORCE_INVENTORY_UPDATE) {
			InventoryUpdate playerIU = new InventoryUpdate();
			
			if (oldItem.getCount() > 0 && oldItem != newItem) {
				playerIU.addModifiedItem(oldItem);
			} else {
				playerIU.addRemovedItem(oldItem);
			}
			
			sendPacket(playerIU);
		} else {
			sendPacket(new ItemList(this, false));
		}
		
		if (newItem.getItemId() == 57) {
			sendPacket(new ExAdenaInvenCount(getAdena(), getInventory().getSize(false)));
		}
		
		// Update current load as well
		StatusUpdate playerSU = new StatusUpdate(this);
		playerSU.addAttribute(StatusUpdate.CUR_LOAD, getCurrentLoad());
		sendPacket(playerSU);
		
		// Send target update packet
		if (target instanceof PcInventory) {
			Player targetPlayer = ((PcInventory) target).getOwner();
			
			if (!Config.FORCE_INVENTORY_UPDATE) {
				InventoryUpdate playerIU = new InventoryUpdate();
				
				if (newItem.getCount() > count) {
					playerIU.addModifiedItem(newItem);
				} else {
					playerIU.addNewItem(newItem);
				}
				
				targetPlayer.sendPacket(playerIU);
			} else {
				targetPlayer.sendPacket(new ItemList(targetPlayer, false));
			}
			
			// Update current load as well
			playerSU = new StatusUpdate(targetPlayer);
			playerSU.addAttribute(StatusUpdate.CUR_LOAD, targetPlayer.getCurrentLoad());
			targetPlayer.sendPacket(playerSU);
		} else if (target instanceof PetInventory) {
			PetInventoryUpdate petIU = new PetInventoryUpdate();
			
			if (newItem.getCount() > count) {
				petIU.addModifiedItem(newItem);
			} else {
				petIU.addNewItem(newItem);
			}
			
			((PetInventory) target).getOwner().getOwner().sendPacket(petIU);
		}
		return newItem;
	}
	
	/**
	 * Use instead of calling {@link #addItem(String, Item, WorldObject, boolean)} and {@link #destroyItemByItemId(String, int, long, WorldObject, boolean)}<br>
	 * This method validates slots and weight limit, for stackable and non-stackable items.
	 *
	 * @param process     a generic string representing the process that is exchanging this items
	 * @param reference   the (probably NPC) reference, could be null
	 * @param coinId      the item Id of the item given on the exchange
	 * @param cost        the amount of items given on the exchange
	 * @param rewardId    the item received on the exchange
	 * @param count       the amount of items received on the exchange
	 * @param sendMessage if {@code true} it will send messages to the acting player
	 * @return {@code true} if the player successfully exchanged the items, {@code false} otherwise
	 */
	public boolean exchangeItemsById(String process, WorldObject reference, int coinId, long cost, int rewardId, long count, boolean sendMessage) {
		final PcInventory inv = getInventory();
		if (!inv.validateCapacityByItemId(rewardId, count)) {
			if (sendMessage) {
				sendPacket(SystemMessageId.SLOTS_FULL);
			}
			return false;
		}
		
		if (!inv.validateWeightByItemId(rewardId, count)) {
			if (sendMessage) {
				sendPacket(SystemMessageId.WEIGHT_LIMIT_EXCEEDED);
			}
			return false;
		}
		
		if (destroyItemByItemId(process, coinId, cost, reference, sendMessage)) {
			addItem(process, rewardId, count, reference, sendMessage);
			return true;
		}
		return false;
	}
	
	/**
	 * Drop item from inventory and send a Server->Client InventoryUpdate packet to the Player.
	 *
	 * @param process     : String Identifier of process triggering this action
	 * @param item        : Item to be dropped
	 * @param reference   : WorldObject Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 * @return boolean informing if the action was successfull
	 */
	public boolean dropItem(String process, Item item, WorldObject reference, boolean sendMessage) {
		item = inventory.dropItem(process, item, this, reference);
		
		if (item == null) {
			if (sendMessage) {
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NOT_ENOUGH_ITEMS));
			}
			
			return false;
		}
		
		item.dropMe(this, getX() + Rnd.get(50) - 25, getY() + Rnd.get(50) - 25, getZ() + 20);
		
		if (Config.AUTODESTROY_ITEM_AFTER * 1000 > 0 && Config.DESTROY_DROPPED_PLAYER_ITEM &&
				!Config.LIST_PROTECTED_ITEMS.contains(item.getItemId())) {
			if (!item.isEquipable() || Config.DESTROY_EQUIPABLE_PLAYER_ITEM) {
				ItemsAutoDestroy.getInstance().addItem(item);
			}
		}
		if (Config.DESTROY_DROPPED_PLAYER_ITEM) {
			if (!item.isEquipable() || item.isEquipable() && Config.DESTROY_EQUIPABLE_PLAYER_ITEM) {
				item.setProtected(false);
			} else {
				item.setProtected(true);
			}
		} else {
			item.setProtected(true);
		}
		
		// Send inventory update packet
		if (!Config.FORCE_INVENTORY_UPDATE) {
			InventoryUpdate playerIU = new InventoryUpdate();
			playerIU.addItem(item);
			sendPacket(playerIU);
		} else {
			sendPacket(new ItemList(this, false));
		}
		
		if (item.getItemId() == 57) {
			sendPacket(new ExAdenaInvenCount(getAdena(), getInventory().getSize(false)));
		}
		
		// Update current load as well
		StatusUpdate su = new StatusUpdate(this);
		su.addAttribute(StatusUpdate.CUR_LOAD, getCurrentLoad());
		sendPacket(su);
		
		// Sends message to client if requested
		if (sendMessage) {
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.YOU_DROPPED_S1);
			sm.addItemName(item);
			sendPacket(sm);
		}
		
		return true;
	}
	
	/**
	 * Drop item from inventory by using its <B>objectID</B> and send a Server->Client InventoryUpdate packet to the Player.
	 *
	 * @param process     : String Identifier of process triggering this action
	 * @param objectId    : int Item Instance identifier of the item to be dropped
	 * @param count       : long Quantity of items to be dropped
	 * @param x           : int coordinate for drop X
	 * @param y           : int coordinate for drop Y
	 * @param z           : int coordinate for drop Z
	 * @param reference   : WorldObject Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 * @return Item corresponding to the new item or the updated item in inventory
	 */
	public Item dropItem(String process, int objectId, long count, int x, int y, int z, WorldObject reference, boolean sendMessage) {
		Item invitem = inventory.getItemByObjectId(objectId);
		Item item = inventory.dropItem(process, objectId, count, this, reference);
		
		if (item == null) {
			if (sendMessage) {
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NOT_ENOUGH_ITEMS));
			}
			
			return null;
		}
		
		item.dropMe(this, x, y, z);
		
		if (Config.AUTODESTROY_ITEM_AFTER * 1000 > 0 && Config.DESTROY_DROPPED_PLAYER_ITEM &&
				!Config.LIST_PROTECTED_ITEMS.contains(item.getItemId())) {
			if (!item.isEquipable() || Config.DESTROY_EQUIPABLE_PLAYER_ITEM) {
				ItemsAutoDestroy.getInstance().addItem(item);
			}
		}
		if (Config.DESTROY_DROPPED_PLAYER_ITEM) {
			if (!item.isEquipable() || item.isEquipable() && Config.DESTROY_EQUIPABLE_PLAYER_ITEM) {
				item.setProtected(false);
			} else {
				item.setProtected(true);
			}
		} else {
			item.setProtected(true);
		}
		
		// Send inventory update packet
		if (!Config.FORCE_INVENTORY_UPDATE) {
			InventoryUpdate playerIU = new InventoryUpdate();
			playerIU.addItem(invitem);
			sendPacket(playerIU);
		} else {
			sendPacket(new ItemList(this, false));
		}
		
		if (item.getItemId() == 57) {
			sendPacket(new ExAdenaInvenCount(getAdena(), getInventory().getSize(false)));
		}
		
		// Update current load as well
		StatusUpdate su = new StatusUpdate(this);
		su.addAttribute(StatusUpdate.CUR_LOAD, getCurrentLoad());
		sendPacket(su);
		
		// Sends message to client if requested
		if (sendMessage) {
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.YOU_DROPPED_S1);
			sm.addItemName(item);
			sendPacket(sm);
		}
		
		return item;
	}
	
	public Item checkItemManipulation(int objectId, long count, String action) {
		//TODO: if we remove objects that are not visisble from the World, we'll have to remove this check
		if (World.getInstance().findObject(objectId) == null) {
			log.trace(getObjectId() + ": player tried to " + action + " item not available in World");
			return null;
		}
		
		Item item = getInventory().getItemByObjectId(objectId);
		
		if (item == null || item.getOwnerId() != getObjectId()) {
			log.trace(getObjectId() + ": player tried to " + action + " item he is not owner of");
			return null;
		}
		
		if (count < 0 || count > 1 && !item.isStackable()) {
			log.trace(getObjectId() + ": player tried to " + action + " item with invalid count: " + count);
			return null;
		}
		
		if (count > item.getCount()) {
			log.trace(getObjectId() + ": player tried to " + action + " more items than he owns");
			return null;
		}
		
		// Pet is summoned and not the item that summoned the pet AND not the buggle from strider you're mounting
		if (getPet() != null && getPet().getControlObjectId() == objectId || getMountObjectID() == objectId) {
			if (Config.DEBUG) {
				log.trace(getObjectId() + ": player tried to " + action + " item controling pet");
			}
			
			return null;
		}
		
		if (getActiveEnchantItem() != null && getActiveEnchantItem().getObjectId() == objectId) {
			if (Config.DEBUG) {
				log.trace(getObjectId() + ":player tried to " + action + " an enchant scroll he was using");
			}
			
			return null;
		}
		
		// We cannot put a Weapon with Augmention in WH while casting (Possible Exploit)
		if (item.isAugmented() && (isCastingNow() || isCastingSimultaneouslyNow())) {
			return null;
		}
		
		return item;
	}
	
	/**
	 * Set protectEndTime according settings.
	 */
	public void setProtection(boolean protect) {
		if (Config.DEVELOPER && (protect || protectEndTime > 0)) {
			log.warn(getName() + ": Protection " +
					(protect ? "ON " + (TimeController.getGameTicks() + Config.PLAYER_SPAWN_PROTECTION * TimeController.TICKS_PER_SECOND) : "OFF") +
					" (currently " + TimeController.getGameTicks() + ")");
		}
		
		protectEndTime = protect ? TimeController.getGameTicks() + Config.PLAYER_SPAWN_PROTECTION * TimeController.TICKS_PER_SECOND : 0;
	}
	
	public void setTeleportProtection(boolean protect) {
		if (Config.DEVELOPER && (protect || teleportProtectEndTime > 0)) {
			log.warn(getName() + ": Tele Protection " +
					(protect ? "ON " + (TimeController.getGameTicks() + Config.PLAYER_TELEPORT_PROTECTION * TimeController.TICKS_PER_SECOND) :
							"OFF") + " (currently " + TimeController.getGameTicks() + ")");
		}
		
		teleportProtectEndTime = protect ? TimeController.getGameTicks() + Config.PLAYER_TELEPORT_PROTECTION * TimeController.TICKS_PER_SECOND : 0;
	}
	
	/**
	 * Set protection from agro mobs when getting up from fake death, according settings.
	 */
	public void setRecentFakeDeath(boolean protect) {
		recentFakeDeathEndTime =
				protect ? TimeController.getGameTicks() + Config.PLAYER_FAKEDEATH_UP_PROTECTION * TimeController.TICKS_PER_SECOND : 0;
	}
	
	public boolean isRecentFakeDeath() {
		return recentFakeDeathEndTime > TimeController.getGameTicks();
	}
	
	public final boolean isFakeDeath() {
		return isFakeDeath;
	}
	
	public final void setIsFakeDeath(boolean value) {
		isFakeDeath = value;
	}
	
	@Override
	public final boolean isAlikeDead() {
		if (super.isAlikeDead()) {
			return true;
		}
		
		return isFakeDeath();
	}
	
	/**
	 * Get the client owner of this char.<BR><BR>
	 */
	public L2GameClient getClient() {
		return client;
	}
	
	public void setClient(L2GameClient client) {
		this.client = client;
	}
	
	private void closeNetConnection(boolean closeClient) {
		closeNetConnection(closeClient, false, null);
	}
	
	/**
	 * Close the active connection with the client.<BR><BR>
	 */
	public void closeNetConnection(boolean closeClient, final boolean blockDisconnectTask, final L2GameServerPacket packet) {
		L2GameClient client = this.client;
		if (client != null) {
			if (client.isDetached()) {
				client.cleanMe(true);
			} else {
				if (!client.getConnection().isClosed()) {
					if (packet != null) {
						client.close(packet, blockDisconnectTask);
					} else if (closeClient) {
						client.close(LeaveWorld.STATIC_PACKET);
					} else {
						client.close(ServerClose.STATIC_PACKET);
					}
				}
			}
		}
	}
	
	/**
	 * @see Creature#enableSkill(Skill)
	 */
	@Override
	public void enableSkill(Skill skill) {
		super.enableSkill(skill);
		reuseTimeStamps.remove(skill.getReuseHashCode());
	}
	
	/**
	 * @see Creature#checkDoCastConditions(Skill)
	 */
	@Override
	protected boolean checkDoCastConditions(Skill skill) {
		if (!super.checkDoCastConditions(skill)) {
			return false;
		}
		
		switch (skill.getSkillType()) {
			case SUMMON_TRAP: {
				if (isInsideZone(ZONE_PEACE)) {
					sendPacket(SystemMessage.getSystemMessage(SystemMessageId.A_MALICIOUS_SKILL_CANNOT_BE_USED_IN_PEACE_ZONE));
					return false;
				}
				if (getTrap() != null && getTrap().getSkill().getId() == ((SkillTrap) skill).getTriggerSkillId()) {
					SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S1_CANNOT_BE_USED);
					sm.addSkillName(skill);
					sendPacket(sm);
					return false;
				}
				break;
			}
			case SUMMON: {
				boolean canSummon = true;
				if (!getSummons().isEmpty() &&
						(getMaxSummonPoints() == 0 || getSpentSummonPoints() + ((SkillSummon) skill).getSummonPoints() > getMaxSummonPoints())) {
					canSummon = false;
				}
				if (!((SkillSummon) skill).isCubic() && !canSummon) {
					if (Config.DEBUG) {
						log.debug("player has a pet already. ignore summon skill");
					}
					
					sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_ALREADY_HAVE_A_PET));
					return false;
				}
				
				if (isPlayingEvent() && event.isType(EventType.LuckyChests)) {
					sendMessage("During a Lucky Chests event you are not allowed to summon a servitor.");
					return false;
				}
			}
		}
		
		// TODO: Should possibly be checked only in Player's useMagic
		// Can't use Hero and resurrect skills during Olympiad
		if (isInOlympiadMode() && (skill.isHeroSkill() || skill.getSkillType() == SkillType.RESURRECT)) {
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.THIS_SKILL_IS_NOT_AVAILABLE_FOR_THE_OLYMPIAD_EVENT);
			sendPacket(sm);
			return false;
		}
		
		final int charges = getCharges();
		// Check if the spell using charges or not in AirShip
		if (skill.getMaxCharges() == 0 && charges < skill.getNumCharges() || isInAirShip() && skill.getSkillType() != SkillType.REFUEL) {
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S1_CANNOT_BE_USED);
			sm.addSkillName(skill);
			sendPacket(sm);
			return false;
		}
		
		return true;
	}
	
	/**
	 * Returns true if cp update should be done, false if not
	 *
	 * @return boolean
	 */
	private boolean needCpUpdate(int barPixels) {
		double currentCp = getCurrentCp();
		
		if (currentCp <= 1.0 || getMaxCp() < barPixels) {
			return true;
		}
		
		if (currentCp <= cpUpdateDecCheck || currentCp >= cpUpdateIncCheck) {
			if (currentCp == getMaxCp()) {
				cpUpdateIncCheck = currentCp + 1;
				cpUpdateDecCheck = currentCp - cpUpdateInterval;
			} else {
				double doubleMulti = currentCp / cpUpdateInterval;
				int intMulti = (int) doubleMulti;
				
				cpUpdateDecCheck = cpUpdateInterval * (doubleMulti < intMulti ? intMulti-- : intMulti);
				cpUpdateIncCheck = cpUpdateDecCheck + cpUpdateInterval;
			}
			
			return true;
		}
		
		return false;
	}
	
	/**
	 * Returns true if mp update should be done, false if not
	 *
	 * @return boolean
	 */
	private boolean needMpUpdate(int barPixels) {
		double currentMp = getCurrentMp();
		
		if (currentMp <= 1.0 || getMaxMp() < barPixels) {
			return true;
		}
		
		if (currentMp <= mpUpdateDecCheck || currentMp >= mpUpdateIncCheck) {
			if (currentMp == getMaxMp()) {
				mpUpdateIncCheck = currentMp + 1;
				mpUpdateDecCheck = currentMp - mpUpdateInterval;
			} else {
				double doubleMulti = currentMp / mpUpdateInterval;
				int intMulti = (int) doubleMulti;
				
				mpUpdateDecCheck = mpUpdateInterval * (doubleMulti < intMulti ? intMulti-- : intMulti);
				mpUpdateIncCheck = mpUpdateDecCheck + mpUpdateInterval;
			}
			
			return true;
		}
		
		return false;
	}
	
	/**
	 * Send packet StatusUpdate with current HP,MP and CP to the Player and only current HP, MP and Level to all other Player of the Party.<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Send the Server->Client packet StatusUpdate with current HP, MP and CP to this Player </li><BR>
	 * <li>Send the Server->Client packet PartySmallWindowUpdate with current HP, MP and Level to all other Player of the Party </li><BR><BR>
	 * <p>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T SEND current HP and MP to all Player of the statusListener</B></FONT><BR><BR>
	 */
	@Override
	public void broadcastStatusUpdate(Creature causer, StatusUpdateDisplay display) {
		// Send the Server->Client packet StatusUpdate with current HP and MP to all Player that must be informed of HP/MP updates of this Player
		//super.broadcastStatusUpdate(causer, display);
		
		// Send the Server->Client packet StatusUpdate with current HP, MP and CP to this Player
		StatusUpdate su = new StatusUpdate(this, causer, display);
		su.addAttribute(StatusUpdate.CUR_HP, (int) getCurrentHp());
		su.addAttribute(StatusUpdate.CUR_MP, (int) getCurrentMp());
		su.addAttribute(StatusUpdate.CUR_CP, (int) getCurrentCp());
		su.addAttribute(StatusUpdate.MAX_CP, getMaxCp());
		sendPacket(su);
		
		final boolean needCpUpdate = needCpUpdate(352);
		final boolean needHpUpdate = needHpUpdate(352);
		
		// Go through the StatusListener
		// Send the Server->Client packet StatusUpdate with current HP and MP
		if (needHpUpdate) {
			//for (Creature temp : getStatus().getStatusListener())
			for (Player temp : getKnownList().getKnownPlayersInRadius(600)) {
				if (temp != null) {
					temp.sendPacket(su);
				}
			}
			
			for (Creature temp : getStatus().getStatusListener()) {
				if (temp != null && !temp.isInsideRadius(this, 600, false, false)) {
					temp.sendPacket(su);
				}
			}
		}
		
		// Check if a party is in progress and party window update is usefull
		L2Party party = this.party;
		if (party != null && (needCpUpdate || needHpUpdate || needMpUpdate(352))) {
			if (Config.DEBUG) {
				log.debug("Send status for party window of " + getObjectId() + " (" + getName() + ") to his party. CP: " + getCurrentCp() + " HP: " +
						getCurrentHp() + " MP: " + getCurrentMp());
			}
			// Send the Server->Client packet PartySmallWindowUpdate with current HP, MP and Level to all other Player of the Party
			PartySmallWindowUpdate update = new PartySmallWindowUpdate(this);
			party.broadcastToPartyMembers(this, update);
			party.broadcastToPartyMembers(this, su);
		}
		
		if (isInOlympiadMode() && isOlympiadStart() && (needCpUpdate || needHpUpdate)) {
			final OlympiadGameTask game = OlympiadGameManager.getInstance().getOlympiadTask(getOlympiadGameId());
			if (game != null && game.isBattleStarted()) {
				game.getZone().broadcastStatusUpdate(this);
			}
		}
		
		// In duel MP updated only with CP or HP
		if (isInDuel() && (needCpUpdate || needHpUpdate)) {
			ExDuelUpdateUserInfo update = new ExDuelUpdateUserInfo(this);
			DuelManager.getInstance().broadcastToOppositTeam(this, update);
			DuelManager.getInstance().broadcastToOppositTeam(this, su);
		}
	}
	
	/**
	 * Send a Server->Client packet UserInfo to this Player and CharInfo to all Player in its KnownPlayers.<BR><BR>
	 * <p>
	 * <B><U> Concept</U> :</B><BR><BR>
	 * Others Player in the detection area of the Player are identified in <B>knownPlayers</B>.
	 * In order to inform other players of this Player state modifications, server just need to go through knownPlayers to send Server->Client Packet<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Send a Server->Client packet UserInfo to this Player (Public and Private Data)</li>
	 * <li>Send a Server->Client packet CharInfo to all Player in KnownPlayers of the Player (Public data only)</li><BR><BR>
	 * <p>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : DON'T SEND UserInfo packet to other players instead of CharInfo packet.
	 * Indeed, UserInfo packet contains PRIVATE DATA as MaxHP, STR, DEX...</B></FONT><BR><BR>
	 */
	public final void broadcastUserInfo() {
		// Send a Server->Client packet UserInfo to this Player
		sendPacket(new UserInfo(this));
		sendPacket(new ExUserPaperdoll(this));
		sendPacket(new ExUserCubics(this));
		sendPacket(new ExUserLoad(this));
		sendPacket(new ExUserEffects(this));
		// Send also SubjobInfo
		//sendPacket(new ExSubjobInfo(this));
		sendPacket(new ExVitalityEffectInfo(getVitalityPoints(), 200));
		
		// Send a Server->Client packet CharInfo to all Player in KnownPlayers of the Player
		if (Config.DEBUG) {
			log.debug("players to notify:" + getKnownList().getKnownPlayers().size() + " packet: [S] 03 CharInfo");
		}
		
		broadcastPacket(new CharInfo(this));
		
		//broadcastAbnormalStatusUpdate();
	}
	
	public final void broadcastTitleInfo() {
		// Send a Server->Client packet UserInfo to this Player
		sendPacket(new UserInfo(this));
		
		// Send a Server->Client packet TitleUpdate to all Player in KnownPlayers of the Player
		if (Config.DEBUG) {
			log.debug("players to notify:" + getKnownList().getKnownPlayers().size() + " packet: [S] cc TitleUpdate");
		}
		
		broadcastPacket(new NicknameChanged(this));
	}
	
	@Override
	public final void broadcastPacket(L2GameServerPacket mov) {
		if (!(mov instanceof CharInfo)) {
			sendPacket(mov);
		}
		
		mov.setInvisibleCharacter(getAppearance().getInvisible() ? getObjectId() : 0);
		
		Collection<Player> plrs = getKnownList().getKnownPlayers().values();
		//synchronized (getKnownList().getKnownPlayers())
		{
			for (Player player : plrs) {
				if (player == null) {
					continue;
				}
				
				player.sendPacket(mov);
				if (mov instanceof CharInfo) {
					int relation = getRelation(player);
					Integer oldrelation = getKnownList().getKnownRelations().get(player.getObjectId());
					if (oldrelation != null && oldrelation != relation) {
						player.sendPacket(new RelationChanged(this, relation, isAutoAttackable(player)));
						if (getPet() != null) {
							player.sendPacket(new RelationChanged(getPet(), relation, isAutoAttackable(player)));
						}
						for (SummonInstance summon : getSummons()) {
							player.sendPacket(new RelationChanged(summon, relation, isAutoAttackable(player)));
						}
					}
				}
			}
		}
	}
	
	@Override
	public void broadcastPacket(L2GameServerPacket mov, int radiusInKnownlist) {
		if (!(mov instanceof CharInfo)) {
			sendPacket(mov);
		}
		
		mov.setInvisibleCharacter(getAppearance().getInvisible() ? getObjectId() : 0);
		
		boolean isInvisible = getAppearance().getInvisible();
		
		Collection<Player> plrs = getKnownList().getKnownPlayers().values();
		//synchronized (getKnownList().getKnownPlayers())
		{
			for (Player player : plrs) {
				if (player == null) {
					continue;
				} else if (!player.isGM() && isInvisible && !isInSameParty(player)) {
					continue;
				}
				
				if (isInsideRadius(player, radiusInKnownlist, false, false)) {
					player.sendPacket(mov);
					if (mov instanceof CharInfo) {
						int relation = getRelation(player);
						Integer oldrelation = getKnownList().getKnownRelations().get(player.getObjectId());
						if (oldrelation != null && oldrelation != relation) {
							player.sendPacket(new RelationChanged(this, relation, isAutoAttackable(player)));
							if (getPet() != null) {
								player.sendPacket(new RelationChanged(getPet(), relation, isAutoAttackable(player)));
							}
							for (SummonInstance summon : getSummons()) {
								player.sendPacket(new RelationChanged(summon, relation, isAutoAttackable(player)));
							}
						}
					}
				}
			}
		}
	}
	
	/**
	 * Return the Alliance Identifier of the Player.<BR><BR>
	 */
	public int getAllyId() {
		if (clan == null) {
			return 0;
		} else {
			return clan.getAllyId();
		}
	}
	
	public int getAllyCrestId() {
		if (getClanId() == 0) {
			return 0;
		}
		if (getClan().getAllyId() == 0) {
			return 0;
		}
		return getClan().getAllyCrestId();
	}
	
	public void queryGameGuard() {
		getClient().setGameGuardOk(false);
		this.sendPacket(new GameGuardQuery());
		if (Config.GAMEGUARD_ENFORCE) {
			ThreadPoolManager.getInstance().scheduleGeneral(new GameGuardCheck(), 30 * 1000);
		}
	}
	
	private class GameGuardCheck implements Runnable {
		/**
		 * @see java.lang.Runnable#run()
		 */
		@Override
		public void run() {
			L2GameClient client = getClient();
			if (client != null && !client.isAuthedGG() && isOnline()) {
				GmListTable.broadcastMessageToGMs("Client " + client + " failed to reply GameGuard query and is being kicked!");
				log.info("Client " + client + " failed to reply GameGuard query and is being kicked!");
				client.close(LeaveWorld.STATIC_PACKET);
			}
		}
	}
	
	/**
	 * Send a Server->Client packet StatusUpdate to the Player.<BR><BR>
	 */
	@Override
	public void sendPacket(L2GameServerPacket packet) {
		if (client != null) {
			client.sendPacket(packet);
		}
	}
	
	/**
	 * Send SystemMessage packet.<BR><BR>
	 *
	 * @param id: SystemMessageId
	 */
	public void sendPacket(SystemMessageId id) {
		sendPacket(SystemMessage.getSystemMessage(id));
	}
	
	/**
	 * Manage Interact Task with another Player.<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>If the private store is a STORE_PRIVATE_SELL, send a Server->Client PrivateBuyListSell packet to the Player</li>
	 * <li>If the private store is a STORE_PRIVATE_BUY, send a Server->Client PrivateBuyListBuy packet to the Player</li>
	 * <li>If the private store is a STORE_PRIVATE_MANUFACTURE, send a Server->Client RecipeShopSellList packet to the Player</li><BR><BR>
	 *
	 * @param target The Creature targeted
	 */
	public void doInteract(Creature target) {
		if (target instanceof Player) {
			Player temp = (Player) target;
			sendPacket(ActionFailed.STATIC_PACKET);
			
			if (temp.getPrivateStoreType() == STORE_PRIVATE_SELL || temp.getPrivateStoreType() == STORE_PRIVATE_PACKAGE_SELL) {
				sendPacket(new PrivateStoreListSell(this, temp));
			} else if (temp.getPrivateStoreType() == STORE_PRIVATE_BUY) {
				sendPacket(new PrivateStoreListBuy(this, temp));
			} else if (temp.getPrivateStoreType() == STORE_PRIVATE_MANUFACTURE) {
				sendPacket(new RecipeShopSellList(this, temp));
			} else if (temp.getPrivateStoreType() == STORE_PRIVATE_CUSTOM_SELL) {
				sendPacket(new PlayerMultiSellList(temp));
			}
		} else {
			// interactTarget=null should never happen but one never knows ^^;
			if (target != null) {
				target.onAction(this);
			}
		}
	}
	
	/**
	 * Manage AutoLoot Task.<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Send a System Message to the Player : YOU_PICKED_UP_S1_ADENA or YOU_PICKED_UP_S1_S2</li>
	 * <li>Add the Item to the Player inventory</li>
	 * <li>Send a Server->Client packet InventoryUpdate to this Player with NewItem (use a new slot) or ModifiedItem (increase amount)</li>
	 * <li>Send a Server->Client packet StatusUpdate to this Player with current weight</li><BR><BR>
	 * <p>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : If a Party is in progress, distribute Items between party members</B></FONT><BR><BR>
	 *
	 * @param target The Item dropped
	 */
	public void doAutoLoot(Attackable target, Attackable.RewardItem item) {
		ItemTemplate itemTemplate = ItemTable.getInstance().getTemplate(item.getItemId());
		if (isInParty() && itemTemplate.getItemType() != EtcItemType.HERB) {
			getParty().distributeItem(this, item, false, target);
		} else if (item.getItemId() == 57) {
			addAdena("Loot", item.getCount(), target, true);
		} else {
			boolean canLootToWarehouse = itemTemplate.isStackable();
			
			switch (item.getItemId()) {
				case 5572: // Wind Mantra
				case 5570: // Water Mantra
				case 5574: // Fire Mantra
				case 50007: // Raid Soul
				case 50008: // Raid Feather
				case 50009: // Raid Heart
					canLootToWarehouse = false;
					break;
			}
			
			if (canLootToWarehouse && getConfigValue("autoLootStackableToWH") && getWarehouse().validateCapacity(5)) {
				getWarehouse().addItem("Loot", item.getItemId(), item.getCount(), this, target);
				
				SystemMessage s = SystemMessage.getSystemMessage(SystemMessageId.LIGHT_BLUE_CHATBOX_S1);
				
				s.addString(item.getCount() + " " + ItemTable.getInstance().getTemplate(item.getItemId()).getName() + " " +
						(item.getCount() == 1 ? "was" : "were") + " added to your Warehouse.");
				sendPacket(s);
			} else {
				/*
				final int itemId = item.getItemId();
				if (CompoundTable.getInstance().isCombinable(item.getItemId()))
				{
					Item sameItem = getInventory().getItemByItemId(itemId);
					if (sameItem != null)
					{
						Combination combination = CompoundTable.getInstance().getCombination(item.getItemId(), sameItem.getItemId());

						int rnd = Rnd.get(100);
						if (rnd >= combination.getChance())
						{
							sendSysMessage(sameItem.getName() + " failed to level up.");
						}
						else
						{
							int newItemId = combination.getResult();

							destroyItem("Compound", sameItem, this, true);

							addItem("Compound", newItemId, 1, this, true);

							sendSysMessage(sameItem.getName() + " turned into a " + ItemTable.getInstance().getTemplate(newItemId).getName());
						}
					}
					else
						addItem("Loot", item.getItemId(), item.getCount(), target, true);

					for (Item i : getInventory().getItemsByItemId(item.getItemId() + 1))
					{
						sameItem = getInventory().getItemByItemId(i.getItemId());

						if (sameItem.getObjectId() == i.getObjectId())
							continue;

						Combination combination = CompoundTable.getInstance().getCombination(sameItem.getItemId(), sameItem.getItemId());

						int rnd = Rnd.get(100);
						if (rnd >= combination.getChance())
						{
							sendSysMessage(sameItem.getName() + " failed to level up.");
							destroyItem("Compound", sameItem, this, true);
							continue;
						}

						int newItemId = combination.getResult();

						destroyItem("Compound", i, this, true);
						destroyItem("Compound", sameItem, this, true);

						addItem("Compound", newItemId, 1, this, true);

						sendSysMessage(sameItem.getName() + " turned into a " + ItemTable.getInstance().getTemplate(newItemId).getName());
					}
				}
				else*/
				addItem("Loot", item.getItemId(), item.getCount(), target, true);
			}
		}
	}
	
	/**
	 * Manage Pickup Task.<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Send a Server->Client packet StopMove to this Player </li>
	 * <li>Remove the Item from the world and send server->client GetItem packets </li>
	 * <li>Send a System Message to the Player : YOU_PICKED_UP_S1_ADENA or YOU_PICKED_UP_S1_S2</li>
	 * <li>Add the Item to the Player inventory</li>
	 * <li>Send a Server->Client packet InventoryUpdate to this Player with NewItem (use a new slot) or ModifiedItem (increase amount)</li>
	 * <li>Send a Server->Client packet StatusUpdate to this Player with current weight</li><BR><BR>
	 * <p>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : If a Party is in progress, distribute Items between party members</B></FONT><BR><BR>
	 *
	 * @param object The Item to pick up
	 */
	public void doPickupItem(WorldObject object) {
		if (isAlikeDead() || isFakeDeath()) {
			return;
		}
		
		// Set the AI Intention to AI_INTENTION_IDLE
		getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		
		// Check if the WorldObject to pick up is a Item
		if (!(object instanceof Item)) {
			// dont try to pickup anything that is not an item :)
			log.warn(this + " trying to pickup wrong target." + getTarget());
			return;
		}
		
		Item target = (Item) object;
		
		// Send a Server->Client packet ActionFailed to this Player
		sendPacket(ActionFailed.STATIC_PACKET);
		
		// Send a Server->Client packet StopMove to this Player
		StopMove sm = new StopMove(this);
		if (Config.DEBUG) {
			log.debug("pickup pos: " + target.getX() + " " + target.getY() + " " + target.getZ());
		}
		sendPacket(sm);
		
		synchronized (target) {
			// Check if the target to pick up is visible
			if (!target.isVisible()) {
				// Send a Server->Client packet ActionFailed to this Player
				sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			
			if ((!isInParty() || getParty().getLootDistribution() == L2Party.ITEM_LOOTER) && !inventory.validateCapacity(target)) {
				sendPacket(ActionFailed.STATIC_PACKET);
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.SLOTS_FULL));
				return;
			}
			
			if (target.getOwnerId() != 0 && target.getOwnerId() != getObjectId() && !isInLooterParty(target.getOwnerId())) {
				sendPacket(ActionFailed.STATIC_PACKET);
				
				if (target.getItemId() == 57) {
					SystemMessage smsg = SystemMessage.getSystemMessage(SystemMessageId.FAILED_TO_PICKUP_S1_ADENA);
					smsg.addItemNumber(target.getCount());
					sendPacket(smsg);
				} else if (target.getCount() > 1) {
					SystemMessage smsg = SystemMessage.getSystemMessage(SystemMessageId.FAILED_TO_PICKUP_S2_S1_S);
					smsg.addItemName(target);
					smsg.addItemNumber(target.getCount());
					sendPacket(smsg);
				} else {
					SystemMessage smsg = SystemMessage.getSystemMessage(SystemMessageId.FAILED_TO_PICKUP_S1);
					smsg.addItemName(target);
					sendPacket(smsg);
				}
				
				return;
			}
			
			// You can pickup only 1 combat flag
			if (FortSiegeManager.getInstance().isCombat(target.getItemId())) {
				if (!FortSiegeManager.getInstance().checkIfCanPickup(this)) {
					return;
				}
			}
			
			if (target.getItemLootShedule() != null && (target.getOwnerId() == getObjectId() || isInLooterParty(target.getOwnerId()))) {
				target.resetOwnerTimer();
			}
			
			// Remove the Item from the world and send server->client GetItem packets
			target.pickupMe(this);
			if (Config.SAVE_DROPPED_ITEM) // item must be removed from ItemsOnGroundManager if is active
			{
				ItemsOnGroundManager.getInstance().removeObject(target);
			}
		}
		
		//Auto use herbs - pick up
		if (target.getItemType() == EtcItemType.HERB) {
			IItemHandler handler = ItemHandler.getInstance().getItemHandler(target.getEtcItem());
			if (handler == null) {
				log.debug("No item handler registered for item ID " + target.getItemId() + ".");
			} else {
				handler.useItem(this, target, false);
			}
			ItemTable.getInstance().destroyItem("Consume", target, this, null);
		}
		// Cursed Weapons are not distributed
		else if (CursedWeaponsManager.getInstance().isCursed(target.getItemId())) {
			addItem("Pickup", target, null, true);
		} else if (FortSiegeManager.getInstance().isCombat(target.getItemId())) {
			addItem("Pickup", target, null, true);
		} else {
			// if item is instance of ArmorType or WeaponType broadcast an "Attention" system message
			if (target.getItemType() instanceof ArmorType || target.getItemType() instanceof WeaponType) {
				if (target.getEnchantLevel() > 0) {
					SystemMessage msg = SystemMessage.getSystemMessage(SystemMessageId.ANNOUNCEMENT_C1_PICKED_UP_S2_S3);
					msg.addPcName(this);
					msg.addNumber(target.getEnchantLevel());
					msg.addItemName(target.getItemId());
					broadcastPacket(msg, 1400);
				} else {
					SystemMessage msg = SystemMessage.getSystemMessage(SystemMessageId.ANNOUNCEMENT_C1_PICKED_UP_S2);
					msg.addPcName(this);
					msg.addItemName(target.getItemId());
					broadcastPacket(msg, 1400);
				}
			}
			
			// Check if a Party is in progress
			if (isInParty()) {
				getParty().distributeItem(this, target);
			}
			// Target is adena
			else if (target.getItemId() == 57 && getInventory().getAdenaInstance() != null) {
				addAdena("Pickup", target.getCount(), null, true);
				ItemTable.getInstance().destroyItem("Pickup", target, this, null);
			}
			// Target is regular item
			else {
				addItem("Pickup", target, null, true);
			}
		}
	}
	
	public boolean canOpenPrivateStore() {
		return !isAlikeDead() && !isInOlympiadMode() && !isMounted() && !isInsideZone(ZONE_NOSTORE) && !isCastingNow();
	}
	
	public void tryOpenPrivateBuyStore() {
		// Player shouldn't be able to set stores if he/she is alike dead (dead or fake death)
		if (canOpenPrivateStore()) {
			if (getPrivateStoreType() == Player.STORE_PRIVATE_BUY || getPrivateStoreType() == Player.STORE_PRIVATE_BUY + 1) {
				setPrivateStoreType(Player.STORE_PRIVATE_NONE);
			}
			if (getPrivateStoreType() == Player.STORE_PRIVATE_NONE) {
				if (isSitting()) {
					standUp();
				}
				setPrivateStoreType(Player.STORE_PRIVATE_BUY + 1);
				sendPacket(new PrivateStoreManageListBuy(this));
			}
		} else {
			if (isInsideZone(ZONE_NOSTORE)) {
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NO_PRIVATE_STORE_HERE));
			}
			sendPacket(ActionFailed.STATIC_PACKET);
		}
	}
	
	public void tryOpenPrivateSellStore(boolean isPackageSale) {
		// Player shouldn't be able to set stores if he/she is alike dead (dead or fake death)
		if (canOpenPrivateStore()) {
			if (getPrivateStoreType() == Player.STORE_PRIVATE_SELL || getPrivateStoreType() == Player.STORE_PRIVATE_SELL + 1 ||
					getPrivateStoreType() == Player.STORE_PRIVATE_PACKAGE_SELL) {
				setPrivateStoreType(Player.STORE_PRIVATE_NONE);
			}
			
			if (getPrivateStoreType() == Player.STORE_PRIVATE_NONE) {
				if (isSitting()) {
					standUp();
				}
				setPrivateStoreType(Player.STORE_PRIVATE_SELL + 1);
				sendPacket(new PrivateStoreManageListSell(this, isPackageSale));
			}
		} else {
			if (isInsideZone(ZONE_NOSTORE)) {
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NO_PRIVATE_STORE_HERE));
			}
			sendPacket(ActionFailed.STATIC_PACKET);
		}
	}
	
	public final PreparedListContainer getMultiSell() {
		return currentMultiSell;
	}
	
	public final void setMultiSell(PreparedListContainer list) {
		currentMultiSell = list;
	}
	
	@Override
	public boolean isTransformed() {
		return transformation != null && !transformation.isStance();
	}
	
	public boolean isInStance() {
		return transformation != null && transformation.isStance();
	}
	
	public void transform(L2Transformation transformation) {
		if (transformation != null) {
			// You already polymorphed and cannot polymorph again.
			SystemMessage msg = SystemMessage.getSystemMessage(SystemMessageId.YOU_ALREADY_POLYMORPHED_AND_CANNOT_POLYMORPH_AGAIN);
			sendPacket(msg);
			return;
		}
		setQueuedSkill(null, false, false);
		if (isMounted()) {
			// Get off the strider or something else if character is mounted
			dismount();
		}
		
		this.transformation = transformation;
		sendSysMessage("Transformation = " + transformation);
		//stopAllToggles(); Looks like Toggles aren't removed any more at GoD
		transformation.onTransform();
		sendSkillList();
		sendPacket(new SkillCoolTime(this));
		sendPacket(ExBasicActionList.getStaticPacket(this));
		
		// To avoid falling underground
		setXYZ(getX(), getY(), GeoData.getInstance().getHeight(getX(), getY(), getZ()) + 20);
		broadcastUserInfo();
	}
	
	@Override
	public void unTransform(boolean removeEffects) {
		if (transformation != null) {
			setTransformAllowedSkills(new int[]{});
			transformation.onUntransform();
			transformation = null;
			if (removeEffects) {
				stopEffects(AbnormalType.MUTATE);
			}
			sendSkillList();
			sendPacket(new SkillCoolTime(this));
			sendPacket(ExBasicActionList.getStaticPacket(this));
			
			// To avoid falling underground
			//if (!isGM())
			setXYZ(getX(), getY(), GeoData.getInstance().getHeight(getX(), getY(), getZ()) + 20);
			broadcastUserInfo();
		}
	}
	
	public L2Transformation getTransformation() {
		return transformation;
	}
	
	/**
	 * This returns the transformation Id of the current transformation.
	 * For example, if a player is transformed as a Buffalo, and then picks up the Zariche,
	 * the transform Id returned will be that of the Zariche, and NOT the Buffalo.
	 *
	 * @return Transformation Id
	 */
	public int getTransformationId() {
		return transformation == null ? 0 : transformation.getId();
	}
	
	/**
	 * This returns the transformation Id stored inside the character table, selected by the method: transformSelectInfo()
	 * For example, if a player is transformed as a Buffalo, and then picks up the Zariche,
	 * the transform Id returned will be that of the Buffalo, and NOT the Zariche.
	 *
	 * @return Transformation Id
	 */
	public int transformId() {
		return transformationId;
	}
	
	/**
	 * This is a simple query that inserts the transform Id into the character table for future reference.
	 */
	public void transformInsertInfo() {
		transformationId = getTransformationId();
		
		if (transformationId == L2Transformation.TRANSFORM_AKAMANAH || transformationId == L2Transformation.TRANSFORM_ZARICHE) {
			return;
		}
		
		Connection con = null;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement = con.prepareStatement(UPDATE_CHAR_TRANSFORM);
			
			statement.setInt(1, transformationId);
			statement.setInt(2, getObjectId());
			
			statement.execute();
			statement.close();
		} catch (Exception e) {
			log.error("Transformation insert info: ", e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * This selects the current
	 *
	 * @return transformation Id
	 */
	public int transformSelectInfo() {
		Connection con = null;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(SELECT_CHAR_TRANSFORM);
			
			statement.setInt(1, getObjectId());
			ResultSet rset = statement.executeQuery();
			rset.next();
			transformationId = rset.getInt("transform_id");
			
			rset.close();
			statement.close();
		} catch (Exception e) {
			log.error("Transformation select info: ", e);
		} finally {
			L2DatabaseFactory.close(con);
		}
		return transformationId;
	}
	
	/**
	 * Set a target.<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Remove the Player from the statusListener of the old target if it was a Creature </li>
	 * <li>Add the Player to the statusListener of the new target if it's a Creature </li>
	 * <li>Target the new WorldObject (add the target to the Player target, knownObject and Player to KnownObject of the WorldObject)</li><BR><BR>
	 *
	 * @param newTarget The WorldObject to target
	 */
	@Override
	public void setTarget(WorldObject newTarget) {
		if (newTarget != null) {
			boolean isParty = newTarget instanceof Player && isInParty() && getParty().getPartyMembers().contains(newTarget);
			
			// Check if the new target is visible
			if (!isParty && !newTarget.isVisible()) {
				newTarget = null;
			}
			
			// Prevents /target exploiting
			if (newTarget != null && !isParty && Math.abs(newTarget.getZ() - getZ()) > 1000) {
				newTarget = null;
			}
		}
		if (!isGM()) {
			// vehicles cant be targeted
			if (newTarget instanceof Vehicle) {
				newTarget = null;
			}
		}
		
		// Get the current target
		WorldObject oldTarget = getTarget();
		
		if (oldTarget != null) {
			if (oldTarget.equals(newTarget)) {
				return; // no target change
			}
			
			// Remove the Player from the statusListener of the old target if it was a Creature
			if (oldTarget instanceof Creature) {
				((Creature) oldTarget).removeStatusListener(this);
			}
		}
		
		// Add the Player to the statusListener of the new target if it's a Creature
		if (newTarget instanceof Creature) {
			((Creature) newTarget).addStatusListener(this);
			MyTargetSelected my = new MyTargetSelected(newTarget.getObjectId(), 0);
			sendPacket(my);
			TargetSelected my2 = new TargetSelected(getObjectId(), newTarget.getObjectId(), getX(), getY(), getZ());
			broadcastPacket(my2);
			sendPacket(new AbnormalStatusUpdateFromTarget((Creature) newTarget));
		}
		if (newTarget == null && getTarget() != null) {
			broadcastPacket(new TargetUnselected(this));
		}
		
		// Target the new WorldObject (add the target to the Player target, knownObject and Player to KnownObject of the WorldObject)
		super.setTarget(newTarget);
	}
	
	/**
	 * Return the active weapon instance (always equiped in the right hand).<BR><BR>
	 */
	@Override
	public Item getActiveWeaponInstance() {
		return getInventory().getPaperdollItem(Inventory.PAPERDOLL_RHAND);
	}
	
	/**
	 * Return the active weapon item (always equiped in the right hand).<BR><BR>
	 */
	@Override
	public WeaponTemplate getActiveWeaponItem() {
		Item weapon = getActiveWeaponInstance();
		
		if (weapon == null) {
			return getFistsWeaponItem();
		}
		
		if (!(weapon.getItem() instanceof WeaponTemplate)) {
			log.warn(getName() + " is using " + weapon.getName() + " as Weapon but it isn't one.");
			return null;
		}
		
		return (WeaponTemplate) weapon.getItem();
	}
	
	public Item getChestArmorInstance() {
		return getInventory().getPaperdollItem(Inventory.PAPERDOLL_CHEST);
	}
	
	public Item getLegsArmorInstance() {
		return getInventory().getPaperdollItem(Inventory.PAPERDOLL_LEGS);
	}
	
	public ArmorTemplate getActiveChestArmorItem() {
		Item armor = getChestArmorInstance();
		
		if (armor == null) {
			return null;
		}
		
		return (ArmorTemplate) armor.getItem();
	}
	
	public ArmorTemplate getActiveLegsArmorItem() {
		Item legs = getLegsArmorInstance();
		
		if (legs == null) {
			return null;
		}
		
		return (ArmorTemplate) legs.getItem();
	}
	
	public boolean isWearingHeavyArmor() {
		Item legs = getLegsArmorInstance();
		Item armor = getChestArmorInstance();
		
		if (armor != null && legs != null) {
			if (legs.getItemType() == ArmorType.HEAVY && armor.getItemType() == ArmorType.HEAVY) {
				return true;
			}
		}
		if (armor != null) {
			if (getInventory().getPaperdollItem(Inventory.PAPERDOLL_CHEST).getItem().getBodyPart() == ItemTemplate.SLOT_FULL_ARMOR &&
					armor.getItemType() == ArmorType.HEAVY) {
				return true;
			}
		}
		return false;
	}
	
	public boolean isWearingLightArmor() {
		Item legs = getLegsArmorInstance();
		Item armor = getChestArmorInstance();
		
		if (armor != null && legs != null) {
			if (legs.getItemType() == ArmorType.LIGHT && armor.getItemType() == ArmorType.LIGHT) {
				return true;
			}
		}
		if (armor != null) {
			if (getInventory().getPaperdollItem(Inventory.PAPERDOLL_CHEST).getItem().getBodyPart() == ItemTemplate.SLOT_FULL_ARMOR &&
					armor.getItemType() == ArmorType.LIGHT) {
				return true;
			}
		}
		return false;
	}
	
	public boolean isWearingMagicArmor() {
		Item legs = getLegsArmorInstance();
		Item armor = getChestArmorInstance();
		
		if (armor != null && legs != null) {
			if (legs.getItemType() == ArmorType.MAGIC && armor.getItemType() == ArmorType.MAGIC) {
				return true;
			}
		}
		if (armor != null) {
			if (getInventory().getPaperdollItem(Inventory.PAPERDOLL_CHEST).getItem().getBodyPart() == ItemTemplate.SLOT_FULL_ARMOR &&
					armor.getItemType() == ArmorType.MAGIC) {
				return true;
			}
		}
		return false;
	}
	
	public boolean isMarried() {
		return married;
	}
	
	public void setMarried(boolean state) {
		married = state;
	}
	
	public boolean isEngageRequest() {
		return engagerequest;
	}
	
	public void setEngageRequest(boolean state, int playerid) {
		engagerequest = state;
		engageid = playerid;
	}
	
	public void setMarryRequest(boolean state) {
		marryrequest = state;
	}
	
	public boolean isMarryRequest() {
		return marryrequest;
	}
	
	public void setMarryAccepted(boolean state) {
		marryaccepted = state;
	}
	
	public boolean isMarryAccepted() {
		return marryaccepted;
	}
	
	public int getEngageId() {
		return engageid;
	}
	
	public int getPartnerId() {
		return partnerId;
	}
	
	public void setPartnerId(int partnerid) {
		this.partnerId = partnerid;
	}
	
	public int getCoupleId() {
		return coupleId;
	}
	
	public void setCoupleId(int coupleId) {
		this.coupleId = coupleId;
	}
	
	public void engageAnswer(int answer) {
		if (!engagerequest) {
		} else if (engageid == 0) {
		} else {
			Player ptarget = World.getInstance().getPlayer(engageid);
			setEngageRequest(false, 0);
			if (ptarget != null) {
				if (answer == 1) {
					CoupleManager.getInstance().createCouple(ptarget, Player.this);
					ptarget.sendMessage("Request to Engage has been >ACCEPTED<");
				} else {
					ptarget.sendMessage("Request to Engage has been >DENIED<!");
				}
			}
		}
	}
	
	/**
	 * Return the secondary weapon instance (always equiped in the left hand).<BR><BR>
	 */
	@Override
	public Item getSecondaryWeaponInstance() {
		return getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND);
	}
	
	/**
	 * Return the secondary ItemTemplate item (always equiped in the left hand).<BR>
	 * Arrows, Shield..<BR>
	 */
	@Override
	public ItemTemplate getSecondaryWeaponItem() {
		Item item = getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND);
		if (item != null) {
			return item.getItem();
		}
		return null;
	}
	
	/**
	 * Kill the Creature, Apply Death Penalty, Manage gain/loss Reputation and Item Drop.<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Reduce the Experience of the Player in function of the calculated Death Penalty </li>
	 * <li>If necessary, unsummon the Pet of the killed Player </li>
	 * <li>Manage Reputation gain for attacker and Karam loss for the killed Player </li>
	 * <li>If the killed Player has negative Reputation, manage Drop Item</li>
	 * <li>Kill the Player </li><BR><BR>
	 *
	 * @see Playable#doDie(Creature)
	 */
	@Override
	public boolean doDie(Creature killer) {
		// Kill the Player
		if (!super.doDie(killer)) {
			return false;
		}
		
		if (isMounted()) {
			stopFeed();
		}
		
		synchronized (this) {
			if (isFakeDeath()) {
				stopFakeDeath(true);
			}
		}
		
		if (killer != null) {
			Player pk = killer.getActingPlayer();
			if (getEvent() != null) {
				getEvent().onKill(killer, this);
			}
			
			//if (pk != null && Config.isServer(Config.TENKAI))
			//{
			//	OpenWorldOlympiadsManager.getInstance().onKill(pk, this);
			//}
			
			if (getIsInsideGMEvent() && pk.getIsInsideGMEvent()) {
				GMEventManager.getInstance().onKill(killer, this);
			}
			
			if (ArenaManager.getInstance().isInFight(this)) {
				Fight fight = ArenaManager.getInstance().getFight(this);
				Fighter winner = ArenaManager.getInstance().getFighter((Player) killer);
				if (fight == null || winner == null) {
					return false;
				}
				Fighter loser = ArenaManager.getInstance().getFighter(this);
				winner.onKill(this);
				loser.onDie((Player) killer);
			}
			
			//if (pk != null && getEvent() == null && !isInOlympiadMode())
			//	GmListTable.broadcastMessageToGMs(getName() + " was killed by " + pk.getName());
			
			//announce pvp/pk
			if (Config.ANNOUNCE_PK_PVP && pk != null && !pk.isGM()) {
				String msg = "";
				if (getPvpFlag() == 0) {
					msg = Config.ANNOUNCE_PK_MSG.replace("$killer", pk.getName()).replace("$target", getName());
					if (Config.ANNOUNCE_PK_PVP_NORMAL_MESSAGE) {
						SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S1);
						sm.addString(msg);
						Announcements.getInstance().announceToAll(sm);
					} else {
						Announcements.getInstance().announceToAll(msg);
					}
				} else if (getPvpFlag() != 0) {
					msg = Config.ANNOUNCE_PVP_MSG.replace("$killer", pk.getName()).replace("$target", getName());
					if (Config.ANNOUNCE_PK_PVP_NORMAL_MESSAGE) {
						SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S1);
						sm.addString(msg);
						Announcements.getInstance().announceToAll(sm);
					} else {
						Announcements.getInstance().announceToAll(msg);
					}
				}
			}
			
			// LasTravel
			if (Config.ENABLE_CUSTOM_KILL_INFO && pk != null && getEvent() == null && !isInOlympiadMode() && !getIsInsideGMEvent()) {
				RankingKillInfo.getInstance().updateSpecificKillInfo(pk, this);
			}
			
			if (pk != null && pk.getClan() != null && getClan() != null && getClan() != pk.getClan() && !isAcademyMember() && !pk.isAcademyMember() &&
					(clan.isAtWarWith(pk.getClanId()) && pk.getClan().isAtWarWith(clan.getClanId()) || isInSiege() && pk.isInSiege()) &&
					!pk.getIsInsideGMEvent() && !getIsInsideGMEvent() && !pk.isPlayingEvent() && !isPlayingEvent() &&
					AntiFeedManager.getInstance().check(killer, this)) {
				// 	when your reputation score is 0 or below, the other clan cannot acquire any reputation points
				if (getClan().getReputationScore() > 0) {
					pk.getClan().addReputationScore(Config.REPUTATION_SCORE_PER_KILL, false);
					SystemMessage sm =
							SystemMessage.getSystemMessage(SystemMessageId.BECAUSE_A_CLAN_MEMBER_OF_S1_WAS_KILLED_BY_C2_CLAN_REPUTATION_INCREASED_BY_1);
					sm.addString(getClan().getName());
					sm.addCharName(pk);
					pk.getClan().broadcastToOnlineMembers(sm);
				}
				// 	when the opposing sides reputation score is 0 or below, your clans reputation score does not decrease
				if (pk.getClan().getReputationScore() > 0) {
					clan.takeReputationScore(Config.REPUTATION_SCORE_PER_KILL, false);
					SystemMessage sm =
							SystemMessage.getSystemMessage(SystemMessageId.BECAUSE_C1_WAS_KILLED_BY_A_CLAN_MEMBER_OF_S2_CLAN_REPUTATION_DECREASED_BY_1);
					sm.addCharName(this);
					sm.addString(pk.getClan().getName());
					getClan().broadcastToOnlineMembers(sm);
				}
				
				for (ClanWar w : getClan().getWars()) {
					if (w.getClan1() == pk.getClan() && w.getClan2() == getClan()) {
						w.increaseClan1Score();
						w.decreaseClan2Score();
					} else if (w.getClan1() == getClan() && w.getClan2() == pk.getClan()) {
						w.increaseClan2Score();
						w.decreaseClan1Score();
					}
				}
			}
			
			broadcastStatusUpdate();
			// Clear resurrect xp calculation
			setExpBeforeDeath(0);
			
			// Issues drop of Cursed Weapon.
			if (isCursedWeaponEquipped()) {
				CursedWeaponsManager.getInstance().drop(cursedWeaponEquippedId, killer);
			} else if (isCombatFlagEquipped()) {
				Fort fort = FortManager.getInstance().getFort(this);
				if (fort != null) {
					FortSiegeManager.getInstance().dropCombatFlag(this, fort.getFortId());
				} else {
					int slot = getInventory().getSlotFromItem(getInventory().getItemByItemId(9819));
					getInventory().unEquipItemInBodySlot(slot);
					destroyItem("CombatFlag", getInventory().getItemByItemId(9819), null, true);
				}
			} else {
				if (pk == null || !pk.isCursedWeaponEquipped()) {
					onDieDropItem(killer); // Check if any item should be dropped
					
					// Reduce the Experience of the Player in function of the calculated Death Penalty
					// NOTE: deathPenalty +- Exp will update reputation
					// Penalty is lower if the player is at war with the pk (war has to be declared)
					if (getSkillLevelHash(Skill.SKILL_LUCKY) < 0 || getStat().getLevel() > 9) {
						boolean siege_npc = false;
						if (killer instanceof DefenderInstance || killer instanceof FortCommanderInstance) {
							siege_npc = true;
						}
						
						boolean pvp = pk != null;
						boolean atWar = pvp && getClan() != null && getClan().isAtWarWith(pk.getClanId());
						boolean isWarDeclarator = atWar && getObjectId() == getClan().getWarDeclarator(pk.getClan());
						deathPenalty(atWar, pvp, siege_npc, isWarDeclarator);
					}
				}
			}
		}
		
		setPvpFlag(0); // Clear the pvp flag
		
		// Unsummon Cubics
		if (!cubics.isEmpty()) {
			for (CubicInstance cubic : cubics.values()) {
				cubic.stopAction();
				cubic.cancelDisappear();
			}
			
			cubics.clear();
		}
		
		if (fusionSkill != null || continuousDebuffTargets != null) {
			abortCast();
		}
		
		for (Creature character : getKnownList().getKnownCharacters()) {
			if (character.getFusionSkill() != null && character.getFusionSkill().getTarget() == this ||
					character.getTarget() == this && character.getLastSkillCast() != null &&
							(character.getLastSkillCast().getSkillType() == SkillType.CONTINUOUS_DEBUFF ||
									character.getLastSkillCast().getSkillType() == SkillType.CONTINUOUS_DRAIN)) {
				character.abortCast();
			}
		}
		
		if (getAgathionId() != 0) {
			setAgathionId(0);
		}
		
		calculateBreathOfShilenDebuff(killer);
		
		stopRentPet();
		stopWaterTask();
		
		AntiFeedManager.getInstance().setLastDeathTime(getObjectId());
		
		if (!isPlayingEvent()) {
			if (isPhoenixBlessed()) {
				reviveRequest(this, null, false);
			} else if (isAffected(EffectType.CHARMOFCOURAGE.getMask()) && isInSiege()) {
				reviveRequest(this, null, false);
			}
		}
		
		for (Summon summon : getSummons()) {
			summon.setTarget(null);
			summon.abortAttack();
			summon.abortCast();
			summon.getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		}
		
		return true;
	}
	
	private void onDieDropItem(Creature killer) {
		if (killer == null) {
			return;
		}
		
		Player pk = killer.getActingPlayer();
		if (getReputation() >= 0 && pk != null && pk.getClan() != null && getClan() != null && pk.getClan().isAtWarWith(getClanId())) {
			return;
		}
		
		if ((!isInsideZone(ZONE_PVP) || pk == null) && (!isGM() || Config.REPUTATION_DROP_GM)) {
			boolean isKillerNpc = killer instanceof Npc;
			int pkLimit = Config.REPUTATION_PK_LIMIT;
			
			int dropEquip = 0;
			int dropEquipWeapon = 0;
			int dropItem = 0;
			int dropLimit = 0;
			int dropPercent = 0;
			
			if (getReputation() < 0 && getPkKills() >= pkLimit) {
				//isKarmaDrop = true;
				dropPercent = Config.REPUTATION_KARMA_RATE_DROP;
				dropEquip = Config.REPUTATION_KARMA_RATE_DROP_EQUIP;
				dropEquipWeapon = Config.REPUTATION_KARMA_RATE_DROP_EQUIP_WEAPON;
				dropItem = Config.REPUTATION_KARMA_RATE_DROP_ITEM;
				dropLimit = Config.REPUTATION_KARMA_DROP_LIMIT;
			} else if (isKillerNpc && getLevel() > 4) {
				dropPercent = Config.PLAYER_RATE_DROP;
				dropEquip = Config.PLAYER_RATE_DROP_EQUIP;
				dropEquipWeapon = Config.PLAYER_RATE_DROP_EQUIP_WEAPON;
				dropItem = Config.PLAYER_RATE_DROP_ITEM;
				dropLimit = Config.PLAYER_DROP_LIMIT;
			}
			
			if (dropPercent > 0 && Rnd.get(100) < dropPercent) {
				int dropCount = 0;
				int itemDropPercent = 0;
				for (Item itemDrop : getInventory().getItems()) {
					// Don't drop
					if (itemDrop.isShadowItem() || // Dont drop Shadow Items
							itemDrop.isTimeLimitedItem() || // Dont drop Time Limited Items
							!itemDrop.getItem().isDropable() || itemDrop.getItemId() == 57 || // Adena
							itemDrop.getItem().getType2() == ItemTemplate.TYPE2_QUEST || // Quest Items
							getPet() != null && getPet().getControlObjectId() == itemDrop.getItemId() ||
							// Control Item of active pet
							Arrays.binarySearch(Config.REPUTATION_NONDROPPABLE_ITEMS, itemDrop.getItemId()) >= 0 ||
							// Item listed in the non droppable item listsd
							Arrays.binarySearch(Config.REPUTATION_NONDROPPABLE_PET_ITEMS, itemDrop.getItemId()) >= 0 ||
							// Item listed in the non droppable pet item list
							itemDrop.isAugmented() && getReputation() < 0) {
						continue;
					}
					
					if (itemDrop.isEquipped()) {
						// Set proper chance according to Item type of equipped Item
						itemDropPercent = itemDrop.getItem().getType2() == ItemTemplate.TYPE2_WEAPON ? dropEquipWeapon : dropEquip;
					} else {
						itemDropPercent = dropItem; // Item in inventory
					}
					
					// NOTE: Each time an item is dropped, the chance of another item being dropped gets lesser (dropCount * 2)
					if (Rnd.get(100) < itemDropPercent) {
						if (itemDrop.isEquipped()) {
							getInventory().unEquipItemInSlot(itemDrop.getLocationSlot());
						}
						
						itemDrop.removeAugmentation();
						itemDrop.updateDatabase();
						dropItem("DieDrop", itemDrop, killer, true);
						
						if (++dropCount >= dropLimit) {
							break;
						}
					}
				}
			}
		}
	}
	
	public void onKillUpdatePvPReputation(Creature target) {
		if (target == null || !(target instanceof Playable)) {
			return;
		}
		
		Player targetPlayer = target.getActingPlayer();
		if (targetPlayer == null || targetPlayer == this) {
			return;
		}
		
		//if (!CustomAntiFeedManager.getInstance().isValidPoint(this, targetPlayer, false))
		//return;
		
		boolean wasSummon = target instanceof SummonInstance;
		if (isPlayingEvent()) {
			return;
		}
		
		if (isCursedWeaponEquipped()) {
			CursedWeaponsManager.getInstance().increaseKills(cursedWeaponEquippedId);
			// Custom message for time left
			// CursedWeapon cw = CursedWeaponsManager.getInstance().getCursedWeapon(cursedWeaponEquipedId);
			// SystemMessage msg = SystemMessage.getSystemMessage(SystemMessageId.THERE_IS_S1_HOUR_AND_S2_MINUTE_LEFT_OF_THE_FIXED_USAGE_TIME);
			// int timeLeftInHours = (int)(((cw.getTimeLeft()/60000)/60));
			// msg.addItemName(cursedWeaponEquipedId);
			// msg.addNumber(timeLeftInHours);
			// sendPacket(msg);
			return;
		}
		
		// If in duel and you kill (only can kill l2summon), do nothing
		if (isInDuel() && targetPlayer.isInDuel()) {
			return;
		}
		
		// If in Arena, do nothing
		if (Curfew.getInstance().getOnlyPeaceTown() == -1 && (isInsideZone(ZONE_PVP) || targetPlayer.isInsideZone(ZONE_PVP))) {
			return;
		}
		
		// Check if it's pvp
		if (!wasSummon && (checkIfPvP(target) && targetPlayer.getPvpFlag() != 0 || isInsideZone(ZONE_PVP) && targetPlayer.isInsideZone(ZONE_PVP) ||
				targetPlayer.getClan() != null && targetPlayer.getClan().getHasCastle() != 0 &&
						CastleManager.getInstance().getCastleByOwner(targetPlayer.getClan()).getTendency() ==
								Castle.TENDENCY_DARKNESS)) //Castle condition should be moved to checkIfPvP ?
		{
			increasePvpKills(target);
			if (Config.isServer(Config.TENKAI) && !targetPlayer.isGM()) {
				float expReward = targetPlayer.getDeathPenalty(targetPlayer.getClan() != null && targetPlayer.getClan().isAtWarWith(getClanId()));
				if (getLevel() < targetPlayer.getLevel()) {
					float winnerLevelExp = Experience.getLevelExp(getLevel());
					float loserLevelExp = Experience.getLevelExp(targetPlayer.getLevel());
					expReward *= winnerLevelExp / loserLevelExp;
				}
				if (expReward > 0) {
					addExpAndSp(Math.round(expReward / 2), 0);
					long adena = Math.round(targetPlayer.getAdena() * 0.05);
					if (isInParty()) {
						int memberCount = getParty().getMemberCount();
						for (Player partyMember : getParty().getPartyMembers()) {
							partyMember.addAdena("PvP", adena / memberCount, targetPlayer, true);
						}
					} else {
						addAdena("PvP", adena, targetPlayer, true);
					}
					targetPlayer.reduceAdena("PvP", adena, this, true);
				}
			}
			
			//PVP
			if (!isInsideZone(ZONE_PVP) && !targetPlayer.isInsideZone(ZONE_PVP)) {
				// Clan stuff check
				if (targetPlayer.getClan() != null) {
					boolean atWar =
							getClan() != null && getClan().isAtWarWith(targetPlayer.getClan()) && targetPlayer.getClan().isAtWarWith(getClan()) &&
									targetPlayer.getPledgeType() != L2Clan.SUBUNIT_ACADEMY && getPledgeType() != L2Clan.SUBUNIT_ACADEMY;

					/*if (!war)
					{
						int castleId = targetPlayer.getClan().getHasCastle();
						Castle castle = CastleManager.getInstance().getCastleById(castleId);
						war = castle != null && castle.getTendency() == Castle.TENDENCY_DARKNESS;
					}*/
					
					if (atWar) {
						// 'Both way war' -> 'PvP Kill'
						//increasePvpKills(target);
						return;
					}
					
					if (getClan() != null && !getClan().isAtWarWith(targetPlayer.getClanId()) && !targetPlayer.getClan().isAtWarWith(getClanId()) &&
							targetPlayer.getPledgeType() != L2Clan.SUBUNIT_ACADEMY && getPledgeType() != L2Clan.SUBUNIT_ACADEMY) {
						for (ClanWar war : targetPlayer.getClan().getWars()) {
							if (war.getClan1() == targetPlayer.getClan() && war.getClan2() == getClan() && war.getState() == WarState.DECLARED) {
								war.increaseClan1DeathsForClanWar();
								if (war.getClan1DeathsForClanWar() >= 5) {
									war.setWarDeclarator(getObjectId());
									war.start();
								}
							}
						}
					}
				}
			}
		} else
		// Target player doesn't have pvp flag set
		{
			if (targetPlayer instanceof ApInstance) {
				return;
			}
			
			// Clan stuff check
			if (targetPlayer.getClan() != null) {
				//@SuppressWarnings("unused")
				boolean war = getClan() != null && getClan().isAtWarWith(targetPlayer.getClan()) && targetPlayer.getClan().isAtWarWith(getClan()) &&
						targetPlayer.getPledgeType() != L2Clan.SUBUNIT_ACADEMY && getPledgeType() != L2Clan.SUBUNIT_ACADEMY;
				
				if (war) {
					return;
				}
			}
			
			// 'No war' or 'One way war' -> 'Normal PK'
			if (targetPlayer.getReputation() < 0) // Target player has karma
			{
				if (Config.REPUTATION_AWARD_PK_KILL) {
					increasePvpKills(target);
					if (Config.isServer(Config.TENKAI)) {
						float expReward =
								targetPlayer.getDeathPenalty(targetPlayer.getClan() != null && targetPlayer.getClan().isAtWarWith(getClanId()));
						if (getLevel() < targetPlayer.getLevel()) {
							float winnerLevelExp = Experience.getLevelExp(getLevel());
							float loserLevelExp = Experience.getLevelExp(targetPlayer.getLevel());
							expReward *= winnerLevelExp / loserLevelExp;
						}
						if (expReward > 0) {
							addExpAndSp(Math.round(expReward * 0.65), 0);
							addAdena("PvPK", (int) Math.round(targetPlayer.getAdena() * 0.05), targetPlayer, true);
							targetPlayer.reduceAdena("PvPK", (int) Math.round(targetPlayer.getAdena() * 0.05), this, true);
						}
					}
				}
				
				updateReputationForKillChaoticPlayer(targetPlayer);
			} else if (targetPlayer.getPvpFlag() == 0) // PK
			{
				if (!wasSummon) {
					increasePkKillsAndDecreaseReputation(target);
					//Unequip adventurer items
					checkItemRestriction();
				}
			}
		}
	}
	
	/**
	 * Increase the pvp kills count and send the info to the player
	 */
	public void increasePvpKills(Creature target) {
		if (target instanceof Player
			/*&& AntiFeedManager.getInstance().check(this, target)*/) {
			Player targetPlayer = (Player) target;
			if (targetPlayer.getClient() == null || targetPlayer.getClient().getConnection() == null ||
					targetPlayer.getClient().getConnection().getInetAddress() == null || getClient() == null || getClient().getConnection() == null ||
					getClient().getConnection().getInetAddress() == null ||
					getClient().getConnection().getInetAddress() == targetPlayer.getClient().getConnection().getInetAddress()) {
				return;
			}
			
			// Add karma to attacker and increase its PK counter
			setPvpKills(getPvpKills() + 1);
			
			// Send a Server->Client UserInfo packet to attacker with its Karma and PK Counter
			sendPacket(new UserInfo(this));
			
			if (Config.isServer(Config.TENKAI)) {
				if (getPvpKills() % 100 == 0) {
					Announcements.getInstance().announceToAll(getName() + " has just accumulated " + getPvpKills() + " victorious PvPs!");
				}
			}
		}
	}
	
	/**
	 * Increase pk count, karma and send the info to the player
	 */
	public void increasePkKillsAndDecreaseReputation(Creature target) {
		if (isPlayingEvent()) {
			return;
		}
		
		// Never let players get karma in events
		if (target instanceof Player && ((Player) target).isPlayingEvent()) {
			return;
		}
		
		int minReputationToReduce = Config.REPUTATION_MIN_KARMA;
		int reputationToReduce = 0;
		int maxReputationToReduce = Config.REPUTATION_MAX_KARMA;
		
		int killsCount = getPkKills();
		
		if (reputation <= 0 || killsCount >= 31) // Is not a lawful character or pk kills equals or higher than 31
		{
			reputationToReduce = (int) (Math.random() * (maxReputationToReduce - minReputationToReduce)) + minReputationToReduce;
		}
		if (reputation > 0) {
			reputationToReduce += reputation;
		}
		
		// Add karma to attacker and increase its PK counter
		setReputation(reputation - reputationToReduce);
		if (target instanceof Player) {
			setPkKills(getPkKills() + 1);
		}
		
		// Send a Server->Client UserInfo packet to attacker with its Karma and PK Counter
		sendPacket(new UserInfo(this));
	}
	
	public int calculateReputationGain(long exp) {
		// KARMA LOSS
		// When a PKer gets killed by another player or a MonsterInstance, it loses a certain amount of Karma based on their level.
		// this (with defaults) results in a level 1 losing about ~2 karma per death, and a lvl 70 loses about 11760 karma per death...
		// You lose karma as long as you were not in a pvp zone and you did not kill urself.
		// NOTE: exp for death (if delevel is allowed) is based on the players level
		
		if (getReputation() < 0) {
			long expGained = Math.abs(exp);
			
			expGained /= Config.REPUTATION_XP_DIVIDER * getLevel() * getLevel();
			
			// FIXME Micht : Maybe this code should be fixed and karma set to a long value
			int reputationGain = 0;
			if (expGained > Integer.MAX_VALUE) {
				reputationGain = Integer.MAX_VALUE;
			} else {
				reputationGain = (int) expGained;
			}
			
			if (reputationGain < Config.REPUTATION_LOST_BASE) {
				reputationGain = Config.REPUTATION_LOST_BASE;
			}
			if (reputationGain > -getReputation()) {
				reputationGain = -getReputation();
			}
			
			return reputationGain;
		}
		
		return 0;
	}
	
	public void updatePvPStatus() {
		if (isInsideZone(ZONE_PVP) || isPlayingEvent()) {
			return;
		}
		
		setPvpFlagLasts(System.currentTimeMillis() + Config.PVP_NORMAL_TIME);
		
		if (getPvpFlag() == 0) {
			startPvPFlag();
		}
	}
	
	public void updatePvPStatus(Creature target) {
		if (isPlayingEvent()) {
			return;
		}
		
		Player playerTarget = target.getActingPlayer();
		
		if (playerTarget == null) {
			return;
		}
		
		if (isInDuel() && playerTarget.getDuelId() == getDuelId()) {
			return;
		}
		
		if ((!isInsideZone(ZONE_PVP) || !playerTarget.isInsideZone(ZONE_PVP)) && playerTarget.getReputation() >= 0) {
			if (checkIfPvP(playerTarget)) {
				setPvpFlagLasts(System.currentTimeMillis() + Config.PVP_PVP_TIME);
			} else {
				setPvpFlagLasts(System.currentTimeMillis() + Config.PVP_NORMAL_TIME);
			}
			if (getPvpFlag() == 0) {
				startPvPFlag();
			}
		}
		
		PlayerAssistsManager.getInstance().updateAttackTimer(this, playerTarget);
	}
	
	/**
	 * Restore the specified % of experience this Player has
	 * lost and sends a Server->Client StatusUpdate packet.<BR><BR>
	 */
	public void restoreExp(double restorePercent) {
		if (getExpBeforeDeath() > 0) {
			// Restore the specified % of lost experience.
			getStat().addExp(Math.round((getExpBeforeDeath() - getExp()) * restorePercent / 100));
			setExpBeforeDeath(0);
		}
	}
	
	/**
	 * Reduce the Experience (and level if necessary) of the Player in function of the calculated Death Penalty.<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Calculate the Experience loss </li>
	 * <li>Set the value of expBeforeDeath </li>
	 * <li>Set the new Experience value of the Player and Decrease its level if necessary </li>
	 * <li>Send a Server->Client StatusUpdate packet with its new Experience </li><BR><BR>
	 */
	public void deathPenalty(boolean atwar, boolean killed_by_pc, boolean killed_by_siege_npc, boolean isWarDeclarator) {
		// TODO Need Correct Penalty
		// Get the level of the Player
		final int lvl = getLevel();
		
		int clan_luck = getSkillLevelHash(Skill.SKILL_CLAN_LUCK);
		
		double clan_luck_modificator = 1.0;
		
		if (!killed_by_pc) {
			switch (clan_luck) {
				case 3:
					clan_luck_modificator = 0.8;
					break;
				case 2:
					clan_luck_modificator = 0.8;
					break;
				case 1:
					clan_luck_modificator = 0.88;
					break;
				default:
					clan_luck_modificator = 1.0;
					break;
			}
		} else {
			switch (clan_luck) {
				case 3:
					clan_luck_modificator = 0.5;
					break;
				case 2:
					clan_luck_modificator = 0.5;
					break;
				case 1:
					clan_luck_modificator = 0.5;
					break;
				default:
					clan_luck_modificator = 1.0;
					break;
			}
		}
		
		//The death steal you some Exp
		double percentLost = Config.PLAYER_XP_PERCENT_LOST[getLevel()] * clan_luck_modificator;
		
		if (getReputation() < 0) {
			percentLost *= Config.RATE_REPUTATION_EXP_LOST;
		}
		
		if (atwar && !isWarDeclarator) {
			percentLost /= 4.0;
		}
		
		// Calculate the Experience loss
		long lostExp = 0;
		if (lvl < Config.MAX_LEVEL) {
			lostExp = Math.round((getStat().getExpForLevel(lvl + 1) - getStat().getExpForLevel(lvl)) * percentLost / 100);
		} else {
			lostExp = Math.round((getStat().getExpForLevel(Config.MAX_LEVEL + 1) - getStat().getExpForLevel(Config.MAX_LEVEL)) * percentLost / 100);
		}
		
		// Get the Experience before applying penalty
		setExpBeforeDeath(getExp());
		
		// No xp loss inside pvp zone unless
		// - it's a siege zone and you're NOT participating
		// - you're killed by a non-pc whose not belong to the siege
		if (isInsideZone(ZONE_PVP)) {
			// No xp loss for siege participants inside siege zone
			if (isInsideZone(ZONE_SIEGE)) {
				if (isInSiege() && (killed_by_pc || killed_by_siege_npc)) {
					lostExp = 0;
				}
			} else if (killed_by_pc) {
				lostExp = 0;
			}
		}
		
		if (!Config.ALT_GAME_DELEVEL && getExp() - lostExp < Experience.getAbsoluteExp(lvl)) {
			lostExp = getExp() - Experience.getAbsoluteExp(lvl);
		}
		
		if (Config.DEBUG) {
			log.debug(getName() + " died and lost " + lostExp + " experience.");
		}
		
		// Set the new Experience value of the Player
		getStat().addExp(-lostExp);
	}
	
	public boolean isPartyWaiting() {
		return PartyMatchWaitingList.getInstance().getPlayers().contains(this);
	}
	
	public void setPartyRoom(int id) {
		partyroom = id;
	}
	
	public int getPartyRoom() {
		return partyroom;
	}
	
	public boolean isInPartyMatchRoom() {
		return partyroom > 0;
	}
	
	/**
	 * Manage the increase level task of a Player (Max MP, Max MP, Recommandation, Expertise and beginner skills...).<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Send a Server->Client System Message to the Player : YOU_INCREASED_YOUR_LEVEL </li>
	 * <li>Send a Server->Client packet StatusUpdate to the Player with new LEVEL, MAX_HP and MAX_MP </li>
	 * <li>Set the current HP and MP of the Player, Launch/Stop a HP/MP/CP Regeneration Task and send StatusUpdate packet to all other Player to inform (exclusive broadcast)</li>
	 * <li>Recalculate the party level</li>
	 * <li>Recalculate the number of Recommandation that the Player can give</li>
	 * <li>Give Expertise skill of this level and remove beginner Lucky skill</li><BR><BR>
	 */
	public void increaseLevel() {
		// Set the current HP and MP of the Creature, Launch/Stop a HP/MP/CP Regeneration Task and send StatusUpdate packet to all other Player to inform (exclusive broadcast)
		setCurrentHpMp(getMaxHp(), getMaxMp());
		setCurrentCp(getMaxCp());
	}
	
	/**
	 * Stop the HP/MP/CP Regeneration task.<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Set the RegenActive flag to False </li>
	 * <li>Stop the HP/MP/CP Regeneration task </li><BR><BR>
	 */
	public void stopAllTimers() {
		stopHpMpRegeneration();
		stopWarnUserTakeBreak();
		stopWaterTask();
		stopFeed();
		clearPetData();
		storePetFood(mountNpcId);
		stopRentPet();
		stopPvpRegTask();
		stopPunishTask(true);
		stopSoulTask();
		stopChargeTask();
		stopFameTask();
		stopRecoBonusTask();
		stopRecoGiveTask();
	}
	
	public List<SummonInstance> getSummons() {
		return summons;
	}
	
	public SummonInstance getSummon(int summon) {
		if (summons.size() <= summon) {
			return null;
		}
		return summons.get(summon);
	}
	
	/**
	 * Return the Summon of the Player or null.<BR><BR>
	 */
	public PetInstance getPet() {
		return pet;
	}
	
	public SummonInstance getActiveSummon() {
		return activeSummon;
	}
	
	/**
	 * Return the DecoyInstance of the Player or null.<BR><BR>
	 */
	public DecoyInstance getDecoy() {
		return decoy;
	}
	
	/**
	 * Return the Trap of the Player or null.<BR><BR>
	 */
	public Trap getTrap() {
		return trap;
	}
	
	public void addSummon(SummonInstance summon) {
		summons.add(summon);
		setActiveSummon(summon);
	}
	
	public void removeSummon(SummonInstance summon) {
		summons.remove(summon);
		// update attack element value display
		if (getCurrentClass().isSummoner() && getAttackElement() != Elementals.NONE) {
			sendPacket(new UserInfo(this));
		}
		if (getActiveSummon() == summon) {
			setActiveSummon(null);
		}
	}
	
	/**
	 * Set the L2Pet of the Player.<BR><BR>
	 */
	public void setPet(PetInstance pet) {
		this.pet = pet;
	}
	
	public void setActiveSummon(SummonInstance summon) {
		activeSummon = summon;
	}
	
	/**
	 * Set the DecoyInstance of the Player.<BR><BR>
	 */
	public void setDecoy(DecoyInstance decoy) {
		this.decoy = decoy;
	}
	
	/**
	 * Set the Trap of this Player<BR><BR>
	 *
	 */
	public void setTrap(Trap trap) {
		this.trap = trap;
	}
	
	/**
	 * Return the Summon of the Player or null.<BR><BR>
	 */
	public List<TamedBeastInstance> getTrainedBeasts() {
		return tamedBeast;
	}
	
	/**
	 * Set the Summon of the Player.<BR><BR>
	 */
	public void addTrainedBeast(TamedBeastInstance tamedBeast) {
		if (this.tamedBeast == null) {
			this.tamedBeast = new ArrayList<>();
		}
		this.tamedBeast.add(tamedBeast);
	}
	
	/**
	 * Return the Player requester of a transaction (ex : FriendInvite, JoinAlly, JoinParty...).<BR><BR>
	 */
	public L2Request getRequest() {
		return request;
	}
	
	/**
	 * Set the Player requester of a transaction (ex : FriendInvite, JoinAlly, JoinParty...).<BR><BR>
	 */
	public void setActiveRequester(Player requester) {
		activeRequester = requester;
	}
	
	/**
	 * Return the Player requester of a transaction (ex : FriendInvite, JoinAlly, JoinParty...).<BR><BR>
	 */
	public Player getActiveRequester() {
		Player requester = activeRequester;
		if (requester != null) {
			if (requester.isRequestExpired() && activeTradeList == null) {
				activeRequester = null;
			}
		}
		return activeRequester;
	}
	
	/**
	 * Return True if a transaction is in progress.<BR><BR>
	 */
	public boolean isProcessingRequest() {
		return getActiveRequester() != null || requestExpireTime > TimeController.getGameTicks();
	}
	
	/**
	 * Return True if a transaction is in progress.<BR><BR>
	 */
	public boolean isProcessingTransaction() {
		return getActiveRequester() != null || activeTradeList != null || requestExpireTime > TimeController.getGameTicks();
	}
	
	/**
	 * Select the Warehouse to be used in next activity.<BR><BR>
	 */
	public void onTransactionRequest(Player partner) {
		requestExpireTime = TimeController.getGameTicks() + REQUEST_TIMEOUT * TimeController.TICKS_PER_SECOND;
		partner.setActiveRequester(this);
	}
	
	/**
	 * Return true if last request is expired.
	 *
	 */
	public boolean isRequestExpired() {
		return !(requestExpireTime > TimeController.getGameTicks());
	}
	
	/**
	 * Select the Warehouse to be used in next activity.<BR><BR>
	 */
	public void onTransactionResponse() {
		requestExpireTime = 0;
	}
	
	/**
	 * Select the Warehouse to be used in next activity.<BR><BR>
	 */
	public void setActiveWarehouse(ItemContainer warehouse) {
		activeWarehouse = warehouse;
	}
	
	/**
	 * Return active Warehouse.<BR><BR>
	 */
	public ItemContainer getActiveWarehouse() {
		return activeWarehouse;
	}
	
	/**
	 * Select the TradeList to be used in next activity.<BR><BR>
	 */
	public void setActiveTradeList(TradeList tradeList) {
		activeTradeList = tradeList;
	}
	
	/**
	 * Return active TradeList.<BR><BR>
	 */
	public TradeList getActiveTradeList() {
		return activeTradeList;
	}
	
	public void onTradeStart(Player partner) {
		activeTradeList = new TradeList(this);
		activeTradeList.setPartner(partner);
		
		SystemMessage msg = SystemMessage.getSystemMessage(SystemMessageId.BEGIN_TRADE_WITH_C1);
		msg.addPcName(partner);
		sendPacket(msg);
		sendPacket(new TradeStart(this));
	}
	
	public void onTradeConfirm(Player partner) {
		SystemMessage msg = SystemMessage.getSystemMessage(SystemMessageId.C1_CONFIRMED_TRADE);
		msg.addPcName(partner);
		sendPacket(msg);
		sendPacket(new TradeOtherDone());
	}
	
	public void onTradeCancel(Player partner) {
		if (activeTradeList == null) {
			return;
		}
		
		activeTradeList.lock();
		activeTradeList = null;
		
		sendPacket(new TradeDone(0));
		SystemMessage msg = SystemMessage.getSystemMessage(SystemMessageId.C1_CANCELED_TRADE);
		msg.addPcName(partner);
		sendPacket(msg);
	}
	
	public void onTradeFinish(boolean successfull) {
		activeTradeList = null;
		sendPacket(new TradeDone(1));
		if (successfull) {
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.TRADE_SUCCESSFUL));
		}
	}
	
	public void startTrade(Player partner) {
		onTradeStart(partner);
		partner.onTradeStart(this);
	}
	
	public void cancelActiveTrade() {
		if (activeTradeList == null) {
			return;
		}
		
		Player partner = activeTradeList.getPartner();
		if (partner != null) {
			partner.onTradeCancel(this);
		}
		onTradeCancel(this);
	}
	
	/**
	 * Return the createList object of the Player.<BR><BR>
	 */
	public L2ManufactureList getCreateList() {
		return createList;
	}
	
	/**
	 * Set the createList object of the Player.<BR><BR>
	 */
	public void setCreateList(L2ManufactureList x) {
		createList = x;
	}
	
	/**
	 * Return the buyList object of the Player.<BR><BR>
	 */
	public TradeList getSellList() {
		if (sellList == null) {
			sellList = new TradeList(this);
		}
		return sellList;
	}
	
	/**
	 * Return the buyList object of the Player.<BR><BR>
	 */
	public TradeList getBuyList() {
		if (buyList == null) {
			buyList = new TradeList(this);
		}
		return buyList;
	}
	
	/**
	 * Set the Private Store type of the Player.<BR><BR>
	 * <p>
	 * <B><U> Values </U> :</B><BR><BR>
	 * <li>0 : STORE_PRIVATE_NONE</li>
	 * <li>1 : STORE_PRIVATE_SELL</li>
	 * <li>2 : sellmanage</li><BR>
	 * <li>3 : STORE_PRIVATE_BUY</li><BR>
	 * <li>4 : buymanage</li><BR>
	 * <li>5 : STORE_PRIVATE_MANUFACTURE</li><BR>
	 */
	public void setPrivateStoreType(int type) {
		privatestore = type;
		
		if (Config.OFFLINE_DISCONNECT_FINISHED && privatestore == STORE_PRIVATE_NONE && (getClient() == null || getClient().isDetached())) {
			deleteMe();
		}
	}
	
	/**
	 * Return the Private Store type of the Player.<BR><BR>
	 * <p>
	 * <B><U> Values </U> :</B><BR><BR>
	 * <li>0 : STORE_PRIVATE_NONE</li>
	 * <li>1 : STORE_PRIVATE_SELL</li>
	 * <li>2 : sellmanage</li><BR>
	 * <li>3 : STORE_PRIVATE_BUY</li><BR>
	 * <li>4 : buymanage</li><BR>
	 * <li>5 : STORE_PRIVATE_MANUFACTURE</li><BR>
	 */
	public int getPrivateStoreType() {
		return privatestore;
	}
	
	/**
	 * Set the clan object, clanId, clanLeader Flag and title of the Player.<BR><BR>
	 */
	public void setClan(L2Clan clan) {
		this.clan = clan;
		setTitle("");
		
		if (clan == null) {
			clanId = 0;
			clanPrivileges = 0;
			pledgeType = 0;
			powerGrade = 0;
			lvlJoinedAcademy = 0;
			apprentice = 0;
			sponsor = 0;
			activeWarehouse = null;
			return;
		}
		
		if (!clan.isMember(getObjectId())) {
			// char has been kicked from clan
			setClan(null);
			return;
		}
		
		clanId = clan.getClanId();
	}
	
	/**
	 * Return the clan object of the Player.<BR><BR>
	 */
	public L2Clan getClan() {
		return clan;
	}
	
	/**
	 * Return True if the Player is the leader of its clan.<BR><BR>
	 */
	public boolean isClanLeader() {
		if (getClan() == null) {
			return false;
		} else {
			return getObjectId() == getClan().getLeaderId();
		}
	}
	
	/**
	 * Reduce the number of arrows/bolts owned by the Player and send it Server->Client Packet InventoryUpdate or ItemList (to unequip if the last arrow was consummed).<BR><BR>
	 */
	@Override
	protected void reduceArrowCount(boolean bolts) {
		Item arrows = getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND);
		
		if (arrows == null) {
			getInventory().unEquipItemInSlot(Inventory.PAPERDOLL_LHAND);
			if (bolts) {
				boltItem = null;
			} else {
				arrowItem = null;
			}
			sendPacket(new ItemList(this, false));
			return;
		}
		
		if (arrows.getName().contains("Infinite")) {
			return;
		}
		
		// Adjust item quantity
		if (arrows.getCount() > 1) {
			synchronized (arrows) {
				arrows.changeCountWithoutTrace(-1, this, null);
				arrows.setLastChange(Item.MODIFIED);
				
				// could do also without saving, but let's save approx 1 of 10
				//if (TimeController.getGameTicks() % 10 == 0)
				//	arrows.updateDatabase();
				inventory.refreshWeight();
			}
		} else {
			// Destroy entire item and save to database
			inventory.destroyItem("Consume", arrows, this, null);
			
			getInventory().unEquipItemInSlot(Inventory.PAPERDOLL_LHAND);
			if (bolts) {
				boltItem = null;
			} else {
				arrowItem = null;
			}
			
			if (Config.DEBUG) {
				log.debug("removed arrows count");
			}
			sendPacket(new ItemList(this, false));
			return;
		}
		
		if (!Config.FORCE_INVENTORY_UPDATE) {
			InventoryUpdate iu = new InventoryUpdate();
			iu.addModifiedItem(arrows);
			sendPacket(iu);
		} else {
			sendPacket(new ItemList(this, false));
		}
	}
	
	/**
	 * Equip arrows needed in left hand and send a Server->Client packet ItemList to the L2PcINstance then return True.<BR><BR>
	 */
	@Override
	protected boolean checkAndEquipArrows() {
		// Check if nothing is equiped in left hand
		if (getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND) == null) {
			// Get the Item of the arrows needed for this bow
			arrowItem = getInventory().findArrowForBow(getActiveWeaponItem());
			
			if (arrowItem != null) {
				// Equip arrows needed in left hand
				getInventory().setPaperdollItem(Inventory.PAPERDOLL_LHAND, arrowItem);
				
				// Send a Server->Client packet ItemList to this L2PcINstance to update left hand equipement
				ItemList il = new ItemList(this, false);
				sendPacket(il);
			}
		} else {
			// Get the Item of arrows equiped in left hand
			arrowItem = getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND);
		}
		
		return arrowItem != null;
	}
	
	/**
	 * Equip bolts needed in left hand and send a Server->Client packet ItemList to the L2PcINstance then return True.<BR><BR>
	 */
	@Override
	protected boolean checkAndEquipBolts() {
		// Check if nothing is equiped in left hand
		if (getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND) == null) {
			// Get the Item of the arrows needed for this bow
			boltItem = getInventory().findBoltForCrossBow(getActiveWeaponItem());
			
			if (boltItem != null) {
				// Equip arrows needed in left hand
				getInventory().setPaperdollItem(Inventory.PAPERDOLL_LHAND, boltItem);
				
				// Send a Server->Client packet ItemList to this L2PcINstance to update left hand equipement
				ItemList il = new ItemList(this, false);
				sendPacket(il);
			}
		} else {
			// Get the Item of arrows equiped in left hand
			boltItem = getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND);
		}
		
		return boltItem != null;
	}
	
	/**
	 * Disarm the player's weapon.<BR><BR>
	 */
	public boolean disarmWeapons() {
		// Don't allow disarming a cursed weapon
		if (isCursedWeaponEquipped()) {
			return false;
		}
		
		// Don't allow disarming a Combat Flag or Territory Ward
		if (isCombatFlagEquipped()) {
			return false;
		}
		
		// Unequip the weapon
		Item wpn = getInventory().getPaperdollItem(Inventory.PAPERDOLL_RHAND);
		if (wpn != null) {
			Item[] unequiped = getInventory().unEquipItemInBodySlotAndRecord(wpn.getItem().getBodyPart());
			InventoryUpdate iu = new InventoryUpdate();
			for (Item itm : unequiped) {
				iu.addModifiedItem(itm);
			}
			sendPacket(iu);
			
			abortAttack();
			broadcastUserInfo();
			
			// this can be 0 if the user pressed the right mousebutton twice very fast
			if (unequiped.length > 0) {
				SystemMessage sm = null;
				if (unequiped[0].getEnchantLevel() > 0) {
					sm = SystemMessage.getSystemMessage(SystemMessageId.EQUIPMENT_S1_S2_REMOVED);
					sm.addNumber(unequiped[0].getEnchantLevel());
					sm.addItemName(unequiped[0]);
				} else {
					sm = SystemMessage.getSystemMessage(SystemMessageId.S1_DISARMED);
					sm.addItemName(unequiped[0]);
				}
				sendPacket(sm);
			}
		}
		return true;
	}
	
	/**
	 * Disarm the player's shield.<BR><BR>
	 */
	public boolean disarmShield() {
		Item sld = getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND);
		if (sld != null) {
			Item[] unequiped = getInventory().unEquipItemInBodySlotAndRecord(sld.getItem().getBodyPart());
			InventoryUpdate iu = new InventoryUpdate();
			for (Item itm : unequiped) {
				iu.addModifiedItem(itm);
			}
			sendPacket(iu);
			
			abortAttack();
			broadcastUserInfo();
			
			// this can be 0 if the user pressed the right mousebutton twice very fast
			if (unequiped.length > 0) {
				SystemMessage sm = null;
				if (unequiped[0].getEnchantLevel() > 0) {
					sm = SystemMessage.getSystemMessage(SystemMessageId.EQUIPMENT_S1_S2_REMOVED);
					sm.addNumber(unequiped[0].getEnchantLevel());
					sm.addItemName(unequiped[0]);
				} else {
					sm = SystemMessage.getSystemMessage(SystemMessageId.S1_DISARMED);
					sm.addItemName(unequiped[0]);
				}
				sendPacket(sm);
			}
		}
		return true;
	}
	
	public boolean mount(Summon pet) {
		if (!disarmWeapons()) {
			return false;
		}
		if (!disarmShield()) {
			return false;
		}
		if (isTransformed()) {
			return false;
		}
		
		stopAllToggles();
		Ride mount = new Ride(this, true, pet.getTemplate().NpcId);
		setMount(pet.getNpcId(), pet.getLevel(), mount.getMountType());
		setMountObjectID(pet.getControlObjectId());
		clearPetData();
		startFeed(pet.getNpcId());
		broadcastPacket(mount);
		
		// Notify self and others about speed change
		broadcastUserInfo();
		
		pet.unSummon(this);
		
		return true;
	}
	
	public boolean mount(int npcId, int controlItemObjId, boolean useFood) {
		if (!disarmWeapons()) {
			return false;
		}
		if (!disarmShield()) {
			return false;
		}
		if (isTransformed()) {
			return false;
		}
		
		stopAllToggles();
		Ride mount = new Ride(this, true, npcId);
		if (setMount(npcId, getLevel(), mount.getMountType())) {
			clearPetData();
			setMountObjectID(controlItemObjId);
			broadcastPacket(mount);
			
			// Notify self and others about speed change
			broadcastUserInfo();
			if (useFood) {
				startFeed(npcId);
			}
			return true;
		}
		return false;
	}
	
	public boolean mountPlayer(Summon pet) {
		if (pet != null && pet.isMountable() && !isMounted() && !isBetrayed()) {
			if (isDead()) {
				//A strider cannot be ridden when dead
				sendPacket(ActionFailed.STATIC_PACKET);
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.STRIDER_CANT_BE_RIDDEN_WHILE_DEAD));
				return false;
			} else if (pet.isDead()) {
				//A dead strider cannot be ridden.
				sendPacket(ActionFailed.STATIC_PACKET);
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.DEAD_STRIDER_CANT_BE_RIDDEN));
				return false;
			} else if (pet.isInCombat() || pet.isRooted()) {
				//A strider in battle cannot be ridden
				sendPacket(ActionFailed.STATIC_PACKET);
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.STRIDER_IN_BATLLE_CANT_BE_RIDDEN));
				return false;
			} else if (isInCombat()) {
				//A strider cannot be ridden while in battle
				sendPacket(ActionFailed.STATIC_PACKET);
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.STRIDER_CANT_BE_RIDDEN_WHILE_IN_BATTLE));
				return false;
			} else if (isSitting()) {
				//A strider can be ridden only when standing
				sendPacket(ActionFailed.STATIC_PACKET);
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.STRIDER_CAN_BE_RIDDEN_ONLY_WHILE_STANDING));
				return false;
			} else if (isFishing()) {
				//You can't mount, dismount, break and drop items while fishing
				sendPacket(ActionFailed.STATIC_PACKET);
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CANNOT_DO_WHILE_FISHING_2));
				return false;
			} else if (isTransformed() || isCursedWeaponEquipped()) {
				// no message needed, player while transformed doesn't have mount action
				sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			} else if (getInventory().getItemByItemId(9819) != null) {
				sendPacket(ActionFailed.STATIC_PACKET);
				//FIXME: Wrong Message
				sendMessage("You cannot mount a steed while holding a flag.");
				return false;
			} else if (pet.isHungry()) {
				sendPacket(ActionFailed.STATIC_PACKET);
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.HUNGRY_STRIDER_NOT_MOUNT));
				return false;
			} else if (!Util.checkIfInRange(200, this, pet, true)) {
				sendPacket(ActionFailed.STATIC_PACKET);
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.TOO_FAR_AWAY_FROM_FENRIR_TO_MOUNT));
				return false;
			} else if (!pet.isDead() && !isMounted()) {
				mount(pet);
			}
		} else if (isRentedPet()) {
			stopRentPet();
		} else if (isMounted()) {
			if (getMountType() == 2 && isInsideZone(Creature.ZONE_NOLANDING)) {
				sendPacket(ActionFailed.STATIC_PACKET);
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NO_DISMOUNT_HERE));
				return false;
			} else if (isHungry()) {
				sendPacket(ActionFailed.STATIC_PACKET);
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.HUNGRY_STRIDER_NOT_MOUNT));
				return false;
			} else {
				dismount();
			}
		}
		return true;
	}
	
	public boolean dismount() {
		boolean wasFlying = isFlying();
		
		sendPacket(new SetupGauge(3, 0, 0));
		int petId = mountNpcId;
		if (setMount(0, 0, 0)) {
			stopFeed();
			clearPetData();
			if (wasFlying) {
				removeSkill(SkillTable.FrequentSkill.WYVERN_BREATH.getSkill());
			}
			Ride dismount = new Ride(this, false, 0);
			broadcastPacket(dismount);
			setMountObjectID(0);
			storePetFood(petId);
			// Notify self and others about speed change
			broadcastUserInfo();
			return true;
		}
		return false;
	}
	
	public PetInventory getSummonInv(int summon) {
		return summons.get(summon).getInventory();
	}
	
	public PetInventory getPetInv() {
		return petInv;
	}
	
	/**
	 * Return True if the Player use a dual weapon.<BR><BR>
	 */
	@Override
	public boolean isUsingDualWeapon() {
		WeaponTemplate weaponItem = getActiveWeaponItem();
		if (weaponItem == null) {
			return false;
		}
		
		if (weaponItem.getItemType() == WeaponType.DUAL) {
			return true;
		} else if (weaponItem.getItemType() == WeaponType.DUALFIST) {
			return true;
		} else if (weaponItem.getItemType() == WeaponType.DUALDAGGER) {
            return true;
        } else {
            return weaponItem.getItemType() == WeaponType.DUALBLUNT;
        }
	}
	
	public void setUptime(long time) {
		uptime = time;
	}
	
	public long getUptime() {
		return System.currentTimeMillis() - uptime;
	}
	
	/**
	 * Return True if the Player is invulnerable.<BR><BR>
	 */
	@Override
	public boolean isInvul() {
		return super.isInvul() || isSpawnProtected() || teleportProtectEndTime > TimeController.getGameTicks();
	}
	
	/**
	 * Return True if the Player has a Party in progress.<BR><BR>
	 */
	@Override
	public boolean isInParty() {
		return party != null;
	}
	
	/**
	 * Set the party object of the Player (without joining it).<BR><BR>
	 */
	public void setParty(L2Party party) {
		this.party = party;
	}
	
	/**
	 * Set the party object of the Player AND join it.<BR><BR>
	 */
	public void joinParty(L2Party party) {
		if (party != null) {
			// First set the party otherwise this wouldn't be considered
			// as in a party into the Creature.updateEffectIcons() call.
			this.party = party;
			party.addPartyMember(this);
		}
		//onJoinParty();
	}
	
	/**
	 * Manage the Leave Party task of the Player.<BR><BR>
	 */
	public void leaveParty() {
		if (isInParty()) {
			party.removePartyMember(this, messageType.Disconnected);
			party = null;
		}
		//onLeaveParty();
	}
	
	/**
	 * Return the party object of the Player.<BR><BR>
	 */
	@Override
	public L2Party getParty() {
		return party;
	}
	
	/**
	 * Return True if the Player is a GM.<BR><BR>
	 */
	@Override
	public boolean isGM() {
		return getAccessLevel().isGm();
	}
	
	/**
	 * Set the accessLevel of the Player.<BR><BR>
	 */
	public void setAccessLevel(int level) {
		if (level == AccessLevels.masterAccessLevelNum) {
			if (!Config.isServer(Config.TENKAI)) {
				log.warn("Master access level set for character " + getName() + "! Just a warning to be careful ;)");
			}
			accessLevel = AccessLevels.masterAccessLevel;
		} else if (level == AccessLevels.userAccessLevelNum) {
			accessLevel = AccessLevels.userAccessLevel;
		} else {
			L2AccessLevel accessLevel = AccessLevels.getInstance().getAccessLevel(level);
			
			if (accessLevel == null) {
				if (level < 0) {
					AccessLevels.getInstance().addBanAccessLevel(level);
					this.accessLevel = AccessLevels.getInstance().getAccessLevel(level);
				} else {
					log.warn("Tryed to set unregistered access level " + level + " to character " + getName() +
							". Setting access level without privileges!");
					this.accessLevel = AccessLevels.userAccessLevel;
				}
			} else {
				this.accessLevel = accessLevel;
			}
		}
		
		getAppearance().setNameColor(accessLevel.getNameColor());
		getAppearance().setTitleColor(accessLevel.getTitleColor());
		broadcastUserInfo();
		
		CharNameTable.getInstance().addName(this);
	}
	
	public void setAccountAccesslevel(int level) {
		LoginServerThread.getInstance().sendAccessLevel(getAccountName(), level);
	}
	
	/**
	 * Return the accessLevel of the Player.<BR><BR>
	 */
	public L2AccessLevel getAccessLevel() {
		if (Config.EVERYBODY_HAS_ADMIN_RIGHTS) {
			return AccessLevels.masterAccessLevel;
		} else if (accessLevel == null) /* This is here because inventory etc. is loaded before access level on login, so it is not null */ {
			setAccessLevel(AccessLevels.userAccessLevelNum);
		}
		
		return accessLevel;
	}
	
	/**
	 * Update Stats of the Player client side by sending Server->Client packet UserInfo/StatusUpdate to this Player and CharInfo/StatusUpdate to all Player in its KnownPlayers (broadcast).<BR><BR>
	 */
	public void updateAndBroadcastStatus(int broadcastType) {
		if (!hasLoaded) {
			return;
		}
		
		refreshOverloaded();
		refreshExpertisePenalty();
		// Send a Server->Client packet UserInfo to this Player and CharInfo to all Player in its KnownPlayers (broadcast)
		if (broadcastType == 1) {
			sendPacket(new UserInfo(this));
		}
		if (broadcastType == 2) {
			broadcastUserInfo();
		}
	}
	
	/**
	 * Send a Server->Client StatusUpdate packet with Reputation and PvP Flag to the Player and all Player to inform (broadcast).<BR><BR>
	 */
	public void setReputationFlag(int flag) {
		sendPacket(new UserInfo(this));
		Collection<Player> plrs = getKnownList().getKnownPlayers().values();
		//synchronized (getKnownList().getKnownPlayers())
		{
			for (Player player : plrs) {
				if (player == null) {
					continue;
				}
				
				player.sendPacket(new RelationChanged(this, getRelation(player), isAutoAttackable(player)));
				if (getPet() != null) {
					player.sendPacket(new RelationChanged(getPet(), getRelation(player), isAutoAttackable(player)));
				}
				for (SummonInstance summon : getSummons()) {
					player.sendPacket(new RelationChanged(summon, getRelation(player), isAutoAttackable(player)));
				}
			}
		}
	}
	
	/**
	 * Send a Server->Client StatusUpdate packet with Reputation to the Player and all Player to inform (broadcast).<BR><BR>
	 */
	public void broadcastReputation() {
		StatusUpdate su = new StatusUpdate(this);
		su.addAttribute(StatusUpdate.REPUTATION, getReputation());
		sendPacket(su);
		Collection<Player> plrs = getKnownList().getKnownPlayers().values();
		//synchronized (getKnownList().getKnownPlayers())
		{
			for (Player player : plrs) {
				player.sendPacket(new RelationChanged(this, getRelation(player), isAutoAttackable(player)));
				if (getPet() != null) {
					player.sendPacket(new RelationChanged(getPet(), getRelation(player), isAutoAttackable(player)));
				}
				for (SummonInstance summon : getSummons()) {
					player.sendPacket(new RelationChanged(summon, getRelation(player), isAutoAttackable(player)));
				}
			}
		}
	}
	
	/**
	 * Set the online Flag to True or False and update the characters table of the database with online status and lastAccess (called when login and logout).<BR><BR>
	 */
	public void setOnlineStatus(boolean isOnline, boolean updateInDb) {
		if (isOnline != isOnline) {
			this.isOnline = isOnline;
		}
		
		// Update the characters table of the database with online status and lastAccess (called when login and logout)
		if (updateInDb) {
			updateOnlineStatus();
		}
	}
	
	/**
	 * Update the characters table of the database with online status and lastAccess of this Player (called when login and logout).<BR><BR>
	 */
	public void updateOnlineStatus() {
		Connection con = null;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement = con.prepareStatement("UPDATE characters SET online=?, lastAccess=? WHERE charId=?");
			statement.setInt(1, isOnlineInt());
			statement.setLong(2, System.currentTimeMillis());
			statement.setInt(3, getObjectId());
			statement.execute();
			statement.close();
		} catch (Exception e) {
			log.error("Failed updating character online status.", e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * Create a new player in the characters table of the database.<BR><BR>
	 */
	private boolean createDb() {
		Connection con = null;
		
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(INSERT_CHARACTER);
			
			statement.setString(1, accountName);
			statement.setInt(2, getObjectId());
			statement.setString(3, getName());
			statement.setInt(4, getLevel());
			statement.setInt(5, getMaxHp());
			statement.setDouble(6, getCurrentHp());
			statement.setInt(7, getMaxCp());
			statement.setDouble(8, getCurrentCp());
			statement.setInt(9, getMaxMp());
			statement.setDouble(10, getCurrentMp());
			statement.setInt(11, getAppearance().getFace());
			statement.setInt(12, getAppearance().getHairStyle());
			statement.setInt(13, getAppearance().getHairColor());
			statement.setInt(14, getAppearance().getSex() ? 1 : 0);
			statement.setLong(15, getExp());
			statement.setLong(16, getSp());
			statement.setInt(17, getReputation());
			statement.setInt(18, getFame());
			statement.setInt(19, getPvpKills());
			statement.setInt(20, getPkKills());
			statement.setInt(21, getClanId());
			statement.setInt(22, templateId);
			statement.setInt(23, getCurrentClass().getId());
			statement.setLong(24, getDeleteTimer());
			statement.setInt(25, hasDwarvenCraft() ? 1 : 0);
			statement.setString(26, getTitle());
			statement.setInt(27, getAppearance().getTitleColor());
			statement.setInt(28, getAccessLevel().getLevel());
			statement.setInt(29, isOnlineInt());
			statement.setInt(30, getClanPrivileges());
			statement.setInt(31, getWantsPeace());
			statement.setInt(32, getBaseClass());
			statement.setInt(33, getNewbie());
			statement.setInt(34, isNoble() ? 1 : 0);
			statement.setLong(35, 0);
			statement.setLong(36, getCreateTime());
			
			statement.executeUpdate();
			statement.close();
		} catch (Exception e) {
			log.error("Could not insert char data: " + e.getMessage(), e);
			return false;
		} finally {
			L2DatabaseFactory.close(con);
		}
		return true;
	}
	
	/**
	 * Retrieve a Player from the characters table of the database and add it in allObjects of the L2world.<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Retrieve the Player from the characters table of the database </li>
	 * <li>Add the Player object in allObjects </li>
	 * <li>Set the x,y,z position of the Player and make it invisible</li>
	 * <li>Update the overloaded status of the Player</li><BR><BR>
	 *
	 * @param objectId Identifier of the object to initialized
	 * @return The Player loaded from the database
	 */
	private static Player restore(int objectId) {
		Player player = null;
		Connection con = null;
		
		try {
			// Retrieve the Player from the characters table of the database
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement = con.prepareStatement(RESTORE_CHARACTER);
			statement.setInt(1, objectId);
			ResultSet rset = statement.executeQuery();
			
			double currentCp = 0;
			double currentHp = 0;
			double currentMp = 0;
			
			byte playerTemporaryLevel = 0;
			if (rset.next()) {
				final int activeClassId = rset.getInt("classid");
				final boolean female = rset.getInt("sex") != 0;
				int raceId = rset.getInt("templateId") / 2;
				boolean isMage = PlayerClassTable.getInstance().getClassById(activeClassId).isMage();
				final PcTemplate template = CharTemplateTable.getInstance().getTemplate(raceId * 2 + (isMage ? 1 : 0));
				PcAppearance app = new PcAppearance(rset.getInt("face"), rset.getInt("hairColor"), rset.getInt("hairStyle"), female);
				
				if (rset.getString("account_name").startsWith("!")) {
					player = new ApInstance(objectId, template, rset.getString("account_name"), app);
				} else {
					player = new Player(objectId, template, rset.getString("account_name"), app);
				}
				player.setName(rset.getString("char_name"));
				player.lastAccess = rset.getLong("lastAccess");
				
				player.getStat().setExp(rset.getLong("exp"));
				player.setExpBeforeDeath(rset.getLong("expBeforeDeath"));
				player.getStat().setLevel(rset.getByte("level"));
				
				playerTemporaryLevel = rset.getByte("temporaryLevel");
				
				player.getStat().setSp(rset.getLong("sp"));
				
				player.setWantsPeace(rset.getInt("wantspeace"));
				
				player.setHeading(rset.getInt("heading"));
				
				player.setReputation(rset.getInt("reputation"));
				player.setFame(rset.getInt("fame"));
				player.setPvpKills(rset.getInt("pvpkills"));
				player.setPkKills(rset.getInt("pkkills"));
				player.setOnlineTime(rset.getLong("onlinetime"));
				player.setNewbie(rset.getInt("newbie"));
				
				player.setClanJoinExpiryTime(rset.getLong("clan_join_expiry_time"));
				if (player.getClanJoinExpiryTime() < System.currentTimeMillis()) {
					player.setClanJoinExpiryTime(0);
				}
				player.setClanCreateExpiryTime(rset.getLong("clan_create_expiry_time"));
				if (player.getClanCreateExpiryTime() < System.currentTimeMillis()) {
					player.setClanCreateExpiryTime(0);
				}
				
				int clanId = rset.getInt("clanid");
				player.setPowerGrade((int) rset.getLong("power_grade"));
				player.setPledgeType(rset.getInt("subpledge"));
				//player.setApprentice(rset.getInt("apprentice"));
				
				if (clanId > 0) {
					player.setClan(ClanTable.getInstance().getClan(clanId));
				}
				
				if (player.getClan() != null) {
					if (player.getClan().getLeaderId() != player.getObjectId()) {
						if (player.getPowerGrade() == 0) {
							player.setPowerGrade(5);
						}
						player.setClanPrivileges(player.getClan().getRankPrivs(player.getPowerGrade()));
					} else {
						player.setClanPrivileges(L2Clan.CP_ALL);
						player.setPowerGrade(1);
					}
					int pledgeClass = 0;
					
					pledgeClass = player.getClan().getClanMember(objectId).calculatePledgeClass(player);
					
					if (player.isNoble() && pledgeClass < 5) {
						pledgeClass = 5;
					}
					
					if (player.isHero() && pledgeClass < 8) {
						pledgeClass = 8;
					}
					
					player.setPledgeClass(pledgeClass);
				} else {
					player.setClanPrivileges(L2Clan.CP_NOTHING);
				}
				
				player.setDeleteTimer(rset.getLong("deletetime"));
				
				player.setTitle(rset.getString("title"));
				player.setTitleColor(rset.getInt("title_color"));
				player.setAccessLevel(rset.getInt("accesslevel"));
				player.setFistsWeaponItem(player.findFistsWeaponItem(activeClassId));
				player.setUptime(System.currentTimeMillis());
				
				currentHp = rset.getDouble("curHp");
				currentCp = rset.getDouble("curCp");
				currentMp = rset.getDouble("curMp");
				
				player.classIndex = 0;
				try {
					player.setBaseClass(rset.getInt("base_class"));
				} catch (Exception e) {
					player.setBaseClass(activeClassId);
				}
				
				player.templateId = rset.getInt("templateId");
				
				// Restore Subclass Data (cannot be done earlier in function)
				if (restoreSubClassData(player)) {
					if (activeClassId != player.getBaseClass()) {
						for (SubClass subClass : player.getSubClasses().values()) {
							if (subClass.getClassId() == activeClassId) {
								player.classIndex = subClass.getClassIndex();
							}
						}
					}
				}
				if (player.getClassIndex() == 0 && activeClassId != player.getBaseClass() && playerTemporaryLevel == 0) {
					// Subclass in use but doesn't exist in DB -
					// a possible restart-while-modifysubclass cheat has been attempted.
					// Switching to use base class
					player.setClassId(player.getBaseClass());
					log.warn("Player " + player.getName() + " reverted to base class. Possibly has tried a relogin exploit while subclassing.");
				} else {
					player.activeClass = activeClassId;
				}
				player.currentClass = PlayerClassTable.getInstance().getClassById(player.activeClass);
				
				player.setNoble(rset.getBoolean("nobless"));
				
				player.setApprentice(rset.getInt("apprentice"));
				player.setSponsor(rset.getInt("sponsor"));
				player.setLvlJoinedAcademy(rset.getInt("lvl_joined_academy"));
				player.setPunishLevel(rset.getInt("punish_level"));
				if (player.getPunishLevel() != PunishLevel.NONE) {
					player.setPunishTimer(rset.getLong("punish_timer"));
				} else {
					player.setPunishTimer(0);
				}
				
				CursedWeaponsManager.getInstance().checkPlayer(player);
				
				player.setAllianceWithVarkaKetra(rset.getInt("varka_ketra_ally"));
				
				// Set Teleport Bookmark Slot
				player.setBookMarkSlot(rset.getInt("BookmarkSlot"));
				
				//character creation Time
				player.setCreateTime(rset.getLong("createTime"));
				
				// Showing hat or not?
				player.setShowHat(rset.getBoolean("show_hat"));
				
				// Race appearance
				player.setRaceAppearance(rset.getInt("race_app"));
				
				// Add the Player object in allObjects
				//World.getInstance().storeObject(player);
				
				// Set the x,y,z position of the Player and make it invisible
				int x = rset.getInt("x");
				int y = rset.getInt("y");
				int z = rset.getInt("z");
				MainTownInfo mainTown = MainTownManager.getInstance().getCurrentMainTown();
				if (z > 100000 && mainTown != null) {
					z -= 1000000;
					if (TownManager.getTown(x, y, z) != TownManager.getTown(mainTown.getTownId())) {
						int[] coords = mainTown.getRandomCoords();
						x = coords[0];
						y = coords[1];
						z = coords[2];
					}
				}
				// Set the x,y,z position of the Player and make it invisible
				player.setXYZInvisible(x, y, z);
				
				// Retrieve the name and ID of the other characters assigned to this account.
				PreparedStatement stmt = con.prepareStatement("SELECT charId, char_name FROM characters WHERE account_name=? AND charId<>?");
				stmt.setString(1, player.accountName);
				stmt.setInt(2, objectId);
				ResultSet chars = stmt.executeQuery();
				
				while (chars.next()) {
					Integer charId = chars.getInt("charId");
					String charName = chars.getString("char_name");
					player.chars.put(charId, charName);
				}
				
				chars.close();
				stmt.close();
			}
			
			rset.close();
			statement.close();
			
			statement = con.prepareStatement(RESTORE_ACCOUNT_GSDATA);
			statement.setString(1, player.getAccountName());
			statement.setString(2, "vitality");
			rset = statement.executeQuery();
			if (rset.next()) {
				player.setVitalityPoints(Integer.parseInt(rset.getString("value")), true, true);
			} else {
				statement.close();
				statement = con.prepareStatement("INSERT INTO account_gsdata(account_name,var,value) VALUES(?,?,?);");
				statement.setString(1, player.getAccountName());
				statement.setString(2, "vitality");
				statement.setString(3, String.valueOf(PcStat.MAX_VITALITY_POINTS));
				statement.execute();
				player.setVitalityPoints(PcStat.MAX_VITALITY_POINTS, true, true);
			}
			rset.close();
			statement.close();
			
			// Set Hero status if it applies
			if (HeroesManager.getInstance().getHeroes() != null && HeroesManager.getInstance().getHeroes().containsKey(objectId)) {
				player.setHero(true);
			}
			
			// Retrieve from the database all skills of this Player and add them to skills
			// Retrieve from the database all items of this Player and add them to inventory
			player.getInventory().restore();
			if (!Config.WAREHOUSE_CACHE) {
				player.getWarehouse();
			}
			
			// Retrieve from the database all secondary data of this Player
			// and reward expertise/lucky skills if necessary.
			// Note that Clan, Noblesse and Hero skills are given separately and not here.
			player.restoreCharData();
			
			player.giveSkills(false);
			player.rewardSkills();
			
			if (playerTemporaryLevel != 0) {
				player.setTemporaryLevelToApply(playerTemporaryLevel);
			}
			
			// buff and status icons
			if (Config.STORE_SKILL_COOLTIME) {
				player.restoreEffects();
			}
			
			// Restore current Cp, HP and MP values
			player.setCurrentCp(currentCp);
			player.setCurrentHp(currentHp);
			player.setCurrentMp(currentMp);
			
			if (currentHp < 0.5) {
				player.setIsDead(true);
				player.stopHpMpRegeneration();
			}
			
			// Restore pet if exists in the world
			player.setPet(World.getInstance().getPet(player.getObjectId()));
			if (player.getPet() != null) {
				player.getPet().setOwner(player);
			}
			
			// Update the overloaded status of the Player
			player.refreshOverloaded();
			// Update the expertise status of the Player
			player.refreshExpertisePenalty();
			
			player.restoreFriendList();
			
			player.restoreMenteeList();
			player.restoreMentorInfo();
			
			player.restoreBlockList();
			if (player.isMentor()) {
				player.giveMentorSkills();
			}
			if (!player.canBeMentor() && player.isMentee()) {
				player.giveMenteeSkills();
			}
			
			if (Config.STORE_UI_SETTINGS) {
				player.restoreUISettings();
			}
			
			player.restoreLastSummons();
			
			player.restoreZoneRestartLimitTime();
			player.restoreGearPresets();
			//OpenWorldOlympiadsManager.getInstance().onLogin(player);
		} catch (Exception e) {
			log.error("Failed loading character.", e);
			e.printStackTrace();
		} finally {
			L2DatabaseFactory.close(con);
		}
		
		return player;
	}
	
	public Forum getMail() {
		if (forumMail == null) {
			setMail(ForumsBBSManager.getInstance().getForumByName("MailRoot").getChildByName(getName()));
			
			if (forumMail == null) {
				ForumsBBSManager.getInstance()
						.createNewForum(getName(),
								ForumsBBSManager.getInstance().getForumByName("MailRoot"),
								Forum.MAIL,
								Forum.OWNERONLY,
								getObjectId());
				setMail(ForumsBBSManager.getInstance().getForumByName("MailRoot").getChildByName(getName()));
			}
		}
		
		return forumMail;
	}
	
	public void setMail(Forum forum) {
		forumMail = forum;
	}
	
	public Forum getMemo() {
		if (forumMemo == null) {
			setMemo(ForumsBBSManager.getInstance().getForumByName("MemoRoot").getChildByName(accountName));
			
			if (forumMemo == null) {
				ForumsBBSManager.getInstance()
						.createNewForum(accountName,
								ForumsBBSManager.getInstance().getForumByName("MemoRoot"),
								Forum.MEMO,
								Forum.OWNERONLY,
								getObjectId());
				setMemo(ForumsBBSManager.getInstance().getForumByName("MemoRoot").getChildByName(accountName));
			}
		}
		
		return forumMemo;
	}
	
	public void setMemo(Forum forum) {
		forumMemo = forum;
	}
	
	/**
	 * Restores sub-class data for the Player, used to check the current
	 * class index for the character.
	 */
	private static boolean restoreSubClassData(Player player) {
		Connection con = null;
		
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(RESTORE_CHAR_SUBCLASSES);
			statement.setInt(1, player.getObjectId());
			
			ResultSet rset = statement.executeQuery();
			
			while (rset.next()) {
				SubClass subClass = new SubClass();
				subClass.setClassId(rset.getInt("class_id"));
				subClass.setIsDual(rset.getBoolean("is_dual"));
				subClass.setLevel(rset.getByte("level"));
				subClass.setExp(rset.getLong("exp"));
				subClass.setSp(rset.getLong("sp"));
				subClass.setClassIndex(rset.getInt("class_index"));
				subClass.setCertificates(rset.getInt("certificates"));
				
				// Enforce the correct indexing of subClasses against their class indexes.
				player.getSubClasses().put(subClass.getClassIndex(), subClass);
			}
			
			statement.close();
		} catch (Exception e) {
			log.warn("Could not restore classes for " + player.getName() + ": " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
		
		return true;
	}
	
	/**
	 * Restores secondary data for the Player, based on the current class index.
	 */
	private void restoreCharData() {
		// Retrieve from the database all skills of this Player and add them to skills.
		restoreSkills();
		
		// Retrieve from the database all macroses of this Player and add them to macroses.
		macroses.restore();
		
		// Retrieve from the database all shortCuts of this Player and add them to shortCuts.
		shortCuts.restore();
		
		// Retrieve from the database all henna of this Player and add them to henna.
		restoreHenna();
		
		// Retrieve from the database all teleport bookmark of this Player and add them to tpbookmark.
		restoreTeleportBookmark();
		
		// Retrieve from the database the recipe book of this Player.
		restoreRecipeBook(true);
		
		// Restore Recipe Shop list
		if (Config.STORE_RECIPE_SHOPLIST) {
			restoreRecipeShopList();
		}
		
		// Load Premium Item List
		loadPremiumItemList();
		
		// Check for items in pet inventory
		checkPetInvItems();
		
		restoreLastSummons();
		
		restoreAbilities();
		
		restoreConfigs();
	}
	
	/**
	 * Restore recipe book data for this Player.
	 */
	private void restoreRecipeBook(boolean loadCommon) {
		Connection con = null;
		
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			String sql = loadCommon ? "SELECT id, type, classIndex FROM character_recipebook WHERE charId=?" :
					"SELECT id FROM character_recipebook WHERE charId=? AND classIndex=? AND type = 1";
			PreparedStatement statement = con.prepareStatement(sql);
			statement.setInt(1, getObjectId());
			if (!loadCommon) {
				statement.setInt(2, classIndex);
			}
			ResultSet rset = statement.executeQuery();
			
			dwarvenRecipeBook.clear();
			
			L2RecipeList recipe;
			while (rset.next()) {
				recipe = RecipeController.getInstance().getRecipeList(rset.getInt("id"));
				
				if (loadCommon) {
					if (rset.getInt(2) == 1) {
						if (rset.getInt(3) == classIndex) {
							registerDwarvenRecipeList(recipe, false);
						}
					} else {
						registerCommonRecipeList(recipe, false);
					}
				} else {
					registerDwarvenRecipeList(recipe, false);
				}
			}
			
			rset.close();
			statement.close();
		} catch (Exception e) {
			log.error("Could not restore recipe book data:" + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	public Map<Integer, L2PremiumItem> getPremiumItemList() {
		return premiumItems;
	}
	
	private void loadPremiumItemList() {
		Connection con = null;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			String sql = "SELECT itemNum, itemId, itemCount, itemSender FROM character_premium_items WHERE charId=?";
			PreparedStatement statement = con.prepareStatement(sql);
			statement.setInt(1, getObjectId());
			ResultSet rset = statement.executeQuery();
			while (rset.next()) {
				int itemNum = rset.getInt("itemNum");
				int itemId = rset.getInt("itemId");
				long itemCount = rset.getLong("itemCount");
				String itemSender = rset.getString("itemSender");
				premiumItems.put(itemNum, new L2PremiumItem(itemId, itemCount, itemSender));
			}
			rset.close();
			statement.close();
		} catch (Exception e) {
			log.error("Could not restore premium items: " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	public void updatePremiumItem(int itemNum, long newcount) {
		Connection con = null;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("UPDATE character_premium_items SET itemCount=? WHERE charId=? AND itemNum=? ");
			statement.setLong(1, newcount);
			statement.setInt(2, getObjectId());
			statement.setInt(3, itemNum);
			statement.execute();
			statement.close();
		} catch (Exception e) {
			log.error("Could not update premium items: " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	public void deletePremiumItem(int itemNum) {
		Connection con = null;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("DELETE FROM character_premium_items WHERE charId=? AND itemNum=? ");
			statement.setInt(1, getObjectId());
			statement.setInt(2, itemNum);
			statement.execute();
			statement.close();
		} catch (Exception e) {
			log.error("Could not delete premium item: " + e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * Update Player stats in the characters table of the database.<BR><BR>
	 */
	public synchronized void store(boolean storeActiveEffects) {
		//update client coords, if these look like true
		// if (isInsideRadius(getClientX(), getClientY(), 1000, true))
		//	setXYZ(getClientX(), getClientY(), getClientZ());
		
		storeCharBase();
		storeCharSub();
		storeEffect(storeActiveEffects);
		transformInsertInfo();
		if (Config.STORE_RECIPE_SHOPLIST) {
			storeRecipeShopList();
		}
		if (Config.STORE_UI_SETTINGS) {
			storeUISettings();
		}
		storeLastSummons();
		storeCharFriendMemos();
	}
	
	public void store() {
		store(true);
	}
	
	private void storeCharBase() {
		Connection con = null;
		
		try {
			// Get the exp, level, and sp of base class to store in base table
			long exp = getStat().getBaseClassExp();
			int level = getStat().getBaseClassLevel();
			long sp = getStat().getBaseClassSp();
			
			int x = lastX != 0 ? lastX : eventSavedPosition != null && isPlayingEvent() ? eventSavedPosition.getX() : getX();
			int y = lastY != 0 ? lastY : eventSavedPosition != null && isPlayingEvent() ? eventSavedPosition.getY() : getY();
			int z = lastZ != 0 ? lastZ : eventSavedPosition != null && isPlayingEvent() ? eventSavedPosition.getZ() : getZ();
			
			MainTownInfo mainTown = MainTownManager.getInstance().getCurrentMainTown();
			if (mainTown != null) {
				TownZone currentTown = TownManager.getTown(x, y, z);
				if (currentTown != null && currentTown.getTownId() == mainTown.getTownId()) {
					z += 1000000;
				}
			}
			
			con = L2DatabaseFactory.getInstance().getConnection();
			
			// Update base class
			PreparedStatement statement = con.prepareStatement(UPDATE_CHARACTER);
			
			statement.setInt(1, level);
			statement.setInt(2, temporaryLevel);
			statement.setDouble(3, getMaxHp());
			statement.setDouble(4, getCurrentHp());
			statement.setDouble(5, getMaxCp());
			statement.setDouble(6, getCurrentCp());
			statement.setDouble(7, getMaxMp());
			statement.setDouble(8, getCurrentMp());
			statement.setInt(9, getAppearance().getFace());
			statement.setInt(10, getAppearance().getHairStyle());
			statement.setInt(11, getAppearance().getHairColor());
			statement.setInt(12, getAppearance().getSex() ? 1 : 0);
			statement.setInt(13, getHeading());
			statement.setInt(14, x);
			statement.setInt(15, y);
			statement.setInt(16, z);
			statement.setLong(17, exp);
			statement.setLong(18, getExpBeforeDeath());
			statement.setLong(19, sp);
			statement.setInt(20, getReputation());
			statement.setInt(21, getFame());
			statement.setInt(22, getPvpKills());
			statement.setInt(23, getPkKills());
			statement.setInt(24, getClanId());
			statement.setInt(25, templateId);
			statement.setInt(26, currentClass.getId());
			statement.setLong(27, getDeleteTimer());
			statement.setString(28, getTitle());
			statement.setInt(29, getTitleColor());
			statement.setInt(30, getAccessLevel().getLevel());
			statement.setInt(31, isOnlineInt());
			statement.setInt(32, getClanPrivileges());
			statement.setInt(33, getWantsPeace());
			statement.setInt(34, getBaseClass());
			
			long totalOnlineTime = onlineTime;
			
			if (onlineBeginTime > 0) {
				totalOnlineTime += (System.currentTimeMillis() - onlineBeginTime) / 1000;
			}
			
			statement.setLong(35, totalOnlineTime);
			statement.setInt(36, getPunishLevel().value());
			statement.setLong(37, getPunishTimer());
			statement.setInt(38, getNewbie());
			statement.setInt(39, isNoble() ? 1 : 0);
			statement.setLong(40, getPowerGrade());
			statement.setInt(41, getPledgeType());
			statement.setInt(42, getLvlJoinedAcademy());
			statement.setLong(43, getApprentice());
			statement.setLong(44, getSponsor());
			statement.setInt(45, getAllianceWithVarkaKetra());
			statement.setLong(46, getClanJoinExpiryTime());
			statement.setLong(47, getClanCreateExpiryTime());
			statement.setString(48, getName());
			statement.setInt(49, getBookMarkSlot());
			statement.setInt(50, isShowingHat() ? 1 : 0);
			statement.setInt(51, getRaceAppearance());
			statement.setInt(52, getObjectId());
			
			statement.execute();
			statement.close();
			
			if (getLevel() > 1) {
				statement = con.prepareStatement(UPDATE_ACCOUNT_GSDATA);
				statement.setString(1, String.valueOf(getVitalityPoints()));
				statement.setString(2, getAccountName());
				statement.setString(3, "vitality");
				statement.execute();
				statement.close();
			}
		} catch (Exception e)
		
		{
			log.warn("Could not store char base data: " + this + " - " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	private void storeCharSub() {
		Connection con = null;
		
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement = con.prepareStatement(UPDATE_CHAR_SUBCLASS);
			
			if (getTotalSubClasses() > 0) {
				for (SubClass subClass : getSubClasses().values()) {
					statement.setLong(1, subClass.getExp());
					statement.setLong(2, subClass.getSp());
					statement.setInt(3, subClass.getLevel());
					statement.setInt(4, subClass.getClassId());
					statement.setBoolean(5, subClass.isDual());
					statement.setInt(6, subClass.getCertificates());
					statement.setInt(7, getObjectId());
					statement.setInt(8, subClass.getClassIndex());
					
					statement.execute();
				}
			}
			statement.close();
		} catch (Exception e) {
			log.warn("Could not store sub class data for " + getName() + ": " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	private void storeEffect(boolean storeEffects) {
		if (!Config.STORE_SKILL_COOLTIME || isPlayingEvent()) {
			return;
		}
		
		Connection con = null;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			
			// Delete all current stored effects for char to avoid dupe
			PreparedStatement statement = con.prepareStatement(DELETE_SKILL_SAVE);
			
			statement.setInt(1, getObjectId());
			statement.setInt(2, getClassIndex());
			statement.execute();
			statement.close();
			
			int buff_index = 0;
			
			final List<Integer> storedSkills = new ArrayList<>();
			
			// Store all effect data along with calulated remaining
			// reuse delays for matching skills. 'restore_type'= 0.
			statement = con.prepareStatement(ADD_SKILL_SAVE);
			
			if (storeEffects) {
				for (Abnormal effect : getAllEffects()) {
					if (effect == null) {
						continue;
					}
					
					switch (effect.getType()) {
						case HEAL_OVER_TIME:
							// TODO: Fix me.
						case HIDE:
						case MUTATE:
							continue;
					}
					
					Skill skill = effect.getSkill();
					
					if (storedSkills.contains(skill.getReuseHashCode())) {
						continue;
					}
					
					if (skill.getPartyChangeSkill() != -1) {
						continue;
					}
					
					storedSkills.add(skill.getReuseHashCode());
					
					if (!effect.isHerbEffect() && effect.getInUse() && (!skill.isToggle() || skill.getId() >= 11007 && skill.getId() <= 11010)) {
						statement.setInt(1, getObjectId());
						statement.setInt(2, skill.getId());
						statement.setInt(3, skill.getLevelHash());
						statement.setInt(4, effect.getCount());
						statement.setInt(5, effect.getTime());
						
						if (reuseTimeStamps.containsKey(skill.getReuseHashCode())) {
							TimeStamp t = reuseTimeStamps.get(skill.getReuseHashCode());
							statement.setLong(6, t.hasNotPassed() ? t.getReuse() : 0);
							statement.setDouble(7, t.hasNotPassed() ? t.getStamp() : 0);
						} else {
							statement.setLong(6, 0);
							statement.setDouble(7, 0);
						}
						
						statement.setInt(8, 0);
						statement.setInt(9, getClassIndex());
						statement.setInt(10, ++buff_index);
						statement.execute();
					}
				}
			}
			
			// Store the reuse delays of remaining skills which
			// lost effect but still under reuse delay. 'restore_type' 1.
			for (int hash : reuseTimeStamps.keySet()) {
				if (storedSkills.contains(hash)) {
					continue;
				}
				
				TimeStamp t = reuseTimeStamps.get(hash);
				if (t != null && t.hasNotPassed()) {
					storedSkills.add(hash);
					
					statement.setInt(1, getObjectId());
					statement.setInt(2, t.getSkillId());
					statement.setInt(3, t.getSkillLvl());
					statement.setInt(4, -1);
					statement.setInt(5, -1);
					statement.setLong(6, t.getReuse());
					statement.setDouble(7, t.getStamp());
					statement.setInt(8, 1);
					statement.setInt(9, getClassIndex());
					statement.setInt(10, ++buff_index);
					statement.execute();
				}
			}
			statement.close();
		} catch (Exception e) {
			log.warn("Could not store char effect data: ", e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * Return True if the Player is on line.<BR>
	 * <BR>
	 */
	public boolean isOnline() {
		return isOnline;
	}
	
	public int isOnlineInt() {
		if (isOnline) {
			return getClient() == null || getClient().isDetached() ? 2 : 1;
		} else {
			return 0;
		}
	}
	
	/**
	 * Add a skill to the Player skills and its Func objects to the calculator set of the Player and save update in the character_skills table of the database.<BR><BR>
	 * <p>
	 * <B><U> Concept</U> :</B><BR><BR>
	 * All skills own by a Player are identified in <B>skills</B><BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Replace oldSkill by newSkill or Add the newSkill </li>
	 * <li>If an old skill has been replaced, remove all its Func objects of Creature calculator set</li>
	 * <li>Add Func objects of newSkill to the calculator set of the Creature </li><BR><BR>
	 *
	 * @param newSkill The Skill to add to the Creature
	 * @return The Skill replaced or null if just added a new Skill
	 */
	public Skill addSkill(Skill newSkill, boolean store) {
		// Add a skill to the Player skills and its Func objects to the calculator set of the Player
		Skill oldSkill = super.addSkill(newSkill);
		
		if (temporaryLevel != 0) {
			return oldSkill;
		}
		
		// Add or update a Player skill in the character_skills table of the database
		if (store) {
			storeSkill(newSkill, oldSkill, -1);
		}
		
		return oldSkill;
	}
	
	@Override
	public Skill removeSkill(Skill skill, boolean store) {
		if (store) {
			return removeSkill(skill);
		} else {
			return super.removeSkill(skill, true);
		}
	}
	
	public Skill removeSkill(Skill skill, boolean store, boolean cancelEffect) {
		if (store) {
			return removeSkill(skill);
		} else {
			return super.removeSkill(skill, cancelEffect);
		}
	}
	
	/**
	 * Remove a skill from the Creature and its Func objects from calculator set of the Creature and save update in the character_skills table of the database.<BR><BR>
	 * <p>
	 * <B><U> Concept</U> :</B><BR><BR>
	 * All skills own by a Creature are identified in <B>skills</B><BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Remove the skill from the Creature skills </li>
	 * <li>Remove all its Func objects from the Creature calculator set</li><BR><BR>
	 * <p>
	 * <B><U> Overridden in </U> :</B><BR><BR>
	 * <li> Player : Save update in the character_skills table of the database</li><BR><BR>
	 *
	 * @param skill The Skill to remove from the Creature
	 * @return The Skill removed
	 */
	@Override
	public Skill removeSkill(Skill skill) {
		// Remove all the cubics if the user forgot a cubic skill
		if (skill instanceof SkillSummon && ((SkillSummon) skill).isCubic() && !cubics.isEmpty()) {
			for (CubicInstance cubic : cubics.values()) {
				cubic.stopAction();
				cubic.cancelDisappear();
			}
			
			cubics.clear();
			broadcastUserInfo();
		}
		
		// Remove a skill from the Creature and its Func objects from calculator set of the Creature
		Skill oldSkill = super.removeSkill(skill);
		
		Connection con = null;
		
		try {
			// Remove or update a Player skill from the character_skills table of the database
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement = con.prepareStatement(DELETE_SKILL_FROM_CHAR);
			
			if (oldSkill != null) {
				statement.setInt(1, oldSkill.getId());
				statement.setInt(2, getObjectId());
				statement.setInt(3, getClassIndex());
				statement.execute();
			}
			statement.close();
		} catch (Exception e) {
			log.warn("Error could not delete skill: " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
		
		if (transformId() > 0 || isCursedWeaponEquipped()) {
			return oldSkill;
		}
		
		L2ShortCut[] allShortCuts = getAllShortCuts();
		
		for (L2ShortCut sc : allShortCuts) {
			if (sc != null && skill != null && sc.getId() == skill.getId() && sc.getType() == L2ShortCut.TYPE_SKILL) {
				deleteShortCut(sc.getSlot(), sc.getPage());
			}
		}
		
		return oldSkill;
	}
	
	public void removeSkill(Skill skill, int classIndex) {
		if (skill == null) {
			return;
		}
		
		Connection con = null;
		try {
			// Remove or update a Player skill from the character_skills table of the database
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement = con.prepareStatement(DELETE_SKILL_FROM_CHAR);
			
			statement.setInt(1, skill.getId());
			statement.setInt(2, getObjectId());
			statement.setInt(3, classIndex);
			statement.execute();
			
			statement.close();
		} catch (Exception e) {
			log.warn("Error could not delete skill: " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * Add or update a Player skill in the character_skills table of the database.
	 * <BR><BR>
	 * If newClassIndex > -1, the skill will be stored with that class index, not the current one.
	 */
	public void storeSkill(Skill newSkill, Skill oldSkill, int newClassIndex) {
		int classIndex = this.classIndex;
		
		if (newClassIndex > -1) {
			classIndex = newClassIndex;
		}
		
		Connection con = null;
		
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement;
			
			if (oldSkill != null && newSkill != null) {
				statement = con.prepareStatement(UPDATE_CHARACTER_SKILL_LEVEL);
				statement.setInt(1, newSkill.getLevelHash());
				statement.setInt(2, oldSkill.getId());
				statement.setInt(3, getObjectId());
				statement.setInt(4, classIndex);
				statement.execute();
				statement.close();
			} else if (newSkill != null) {
				statement = con.prepareStatement(ADD_NEW_SKILL);
				statement.setInt(1, getObjectId());
				statement.setInt(2, newSkill.getId());
				statement.setInt(3, newSkill.getLevelHash());
				statement.setInt(4, classIndex);
				statement.execute();
				statement.close();
			} else {
				log.warn("could not store new skill. its NULL");
			}
		} catch (Exception e) {
			log.warn("Error could not store char skills: " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * Retrieve from the database all skills of this Player and add them to skills.<BR><BR>
	 */
	private void restoreSkills() {
		Connection con = null;
		
		if (getClassIndex() != 0) {
			try {
				// Retrieve all skills of this Player from the database
				con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement statement = con.prepareStatement(
						"SELECT skill_id,skill_level FROM character_skills WHERE charId=? AND class_index=? AND skill_id > ? AND skill_id < ?");
				
				statement.setInt(1, getObjectId());
				statement.setInt(2, 0);
				statement.setInt(3, 1955); // Certificate Skills, Lowest ID
				statement.setInt(4, 1987); // Certificate Skills, Highest ID
				ResultSet rset = statement.executeQuery();
				
				// Go though the recordset of this SQL query
				while (rset.next()) {
					int id = rset.getInt("skill_id");
					int level = rset.getInt("skill_level");
					
					// Create a Skill object for each record
					Skill skill = SkillTable.getInstance().getInfo(id, level);
					
					if (id > 1955 && id < 1987) {
						certificationSkills.put(id, skill);
					}
					
					// Add the Skill object to the Creature skills and its Func objects to the calculator set of the Creature
					super.addSkill(skill);
				}
				
				rset.close();
				statement.close();
			} catch (Exception e) {
				log.warn("Could not restore character " + this + " certificate skills: " + e.getMessage(), e);
			} finally {
				L2DatabaseFactory.close(con);
			}
		}
		
		try {
			// Retrieve all skills of this Player from the database
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(RESTORE_SKILLS_FOR_CHAR);
			
			statement.setInt(1, getObjectId());
			statement.setInt(2, getClassIndex());
			ResultSet rset = statement.executeQuery();
			
			// Go though the recordset of this SQL query
			while (rset.next()) {
				int id = rset.getInt("skill_id");
				int level = rset.getInt("skill_level");
				
				//System.out.println("ID = " + id + " LEVEL = " + level);
				// Create a Skill object for each record
				Skill skill = SkillTable.getInstance().getInfo(id, level);
				
				if (id > 1955 && id < 1987) {
					certificationSkills.put(id, skill);
				}
				
				//System.out.println("LEVEL = " + skill.getLevel());
				
				boolean store = false;
				
				// Add the Skill object to the Creature skills and its Func objects to the calculator set of the Creature
				addSkill(skill, store);
				
				if (Config.SKILL_CHECK_ENABLE && (!isGM() || Config.SKILL_CHECK_GM)) {
					if (!SkillTreeTable.getInstance().isSkillAllowed(this, skill)) {
						//Util.handleIllegalPlayerAction(this, "Player " + getName() + " has invalid skill " + skill.getName() + " ("+skill.getId() + "/" + skill.getLevel() + "), class:" + getCurrentClass().getName(), 1);
						if (Config.SKILL_CHECK_REMOVE) {
							removeSkill(skill);
						}
					}
				}
			}
			
			rset.close();
			statement.close();
		} catch (Exception e) {
			log.warn("Could not restore character " + this + " skills: " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
		
		//if (Config.SKILL_CHECK_ENABLE && (!isGM() || Config.SKILL_CHECK_GM))
		//	CertificateSkillTable.getInstance().checkPlayer(this);
	}
	
	/**
	 * Retrieve from the database all skill effects of this Player and add them to the player.<BR><BR>
	 */
	public void restoreEffects() {
		Connection con = null;
		
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement;
			ResultSet rset;
			
			statement = con.prepareStatement(RESTORE_SKILL_SAVE);
			statement.setInt(1, getObjectId());
			statement.setInt(2, getClassIndex());
			rset = statement.executeQuery();
			
			while (rset.next()) {
				int effectCount = rset.getInt("effect_count");
				int effectCurTime = rset.getInt("effect_cur_time");
				long reuseDelay = rset.getLong("reuse_delay");
				long systime = rset.getLong("systime");
				int restoreType = rset.getInt("restore_type");
				
				final Skill skill = SkillTable.getInstance().getInfo(rset.getInt("skill_id"), rset.getInt("skill_level"));
				if (skill == null) {
					continue;
				}
				
				final long remainingTime = systime - System.currentTimeMillis();
				if (remainingTime > 10) {
					disableSkill(skill, remainingTime);
					addTimeStamp(skill, reuseDelay, systime);
				}
                /*
                   Restore Type 1
                   The remaning skills lost effect upon logout but
                   were still under a high reuse delay.
                 */
				if (restoreType > 0) {
					continue;
				}

                /*
                   Restore Type 0
                   These skill were still in effect on the character
                   upon logout. Some of which were self casted and
                   might still have had a long reuse delay which also
                   is restored.

                 */
				if (skill.hasEffects()) {
					Env env = new Env();
					env.player = this;
					env.target = this;
					env.skill = skill;
					Abnormal ef;
					for (AbnormalTemplate et : skill.getEffectTemplates()) {
						ef = et.getEffect(env);
						if (ef != null) {
							ef.setCount(effectCount);
							ef.setFirstTime(effectCurTime);
							ef.scheduleEffect();
						}
					}
				}
			}
			
			rset.close();
			statement.close();
			
			statement = con.prepareStatement(DELETE_SKILL_SAVE);
			statement.setInt(1, getObjectId());
			statement.setInt(2, getClassIndex());
			statement.executeUpdate();
			statement.close();
		} catch (Exception e) {
			log.warn("Could not restore " + this + " active effect data: " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * Retrieve from the database all Henna of this Player, add them to henna and calculate stats of the Player.<BR><BR>
	 */
	private void restoreHenna() {
		Connection con = null;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(RESTORE_CHAR_HENNAS);
			statement.setInt(1, getObjectId());
			statement.setInt(2, getClassIndex());
			ResultSet rset = statement.executeQuery();
			
			for (int i = 0; i < 4; i++) {
				henna[i] = null;
			}
			
			while (rset.next()) {
				int slot = rset.getInt("slot");
				if (slot < 1 || slot > 4) {
					continue;
				}
				
				int symbol_id = rset.getInt("symbol_id");
				if (symbol_id != 0) {
					HennaTemplate henna = HennaTable.getInstance().getTemplate(symbol_id);
					if (henna != null) {
						//Check the dye time?
						long expiryTime = rset.getLong("expiry_time");
						if (henna.isFourthSlot()) {
							if (expiryTime < System.currentTimeMillis()) {
								//In order to delete the dye from the db we should first assing it
								this.henna[slot - 1] = henna;
								removeHenna(4);
								continue;
							}
							addHennaSkills(henna);
						}
						
						this.henna[slot - 1] = henna;
						if (henna.isFourthSlot()) {
							if (expiryTime > 0) {
								this.henna[slot - 1].setExpiryTime(expiryTime);
							}
							addHennaSkills(henna);
						}
					}
				}
			}
			
			rset.close();
			statement.close();
		} catch (Exception e) {
			log.error("Failed restoing character " + this + " hennas.", e);
		} finally {
			L2DatabaseFactory.close(con);
		}
		
		// Calculate Henna modifiers of this Player
		recalcHennaStats();
	}
	
	/**
	 * Return the number of Henna empty slot of the Player.<BR><BR>
	 */
	public int getHennaEmptySlots() {
		int totalSlots = 0;
		if (getCurrentClass().level() == 1) {
			totalSlots = 2;
		} else {
			totalSlots = 4;
		}
		
		for (int i = 0; i < 4; i++) {
			if (henna[i] != null) {
				totalSlots--;
			}
		}
		
		if (totalSlots <= 0) {
			return 0;
		}
		
		return totalSlots;
	}
	
	/**
	 * Remove a Henna of the Player, save update in the character_hennas table of the database and send Server->Client HennaInfo/UserInfo packet to this Player.<BR><BR>
	 */
	public boolean removeHenna(int slot) {
		if (slot < 1 || slot > 4) {
			return false;
		}
		
		slot--;
		
		if (henna[slot] == null) {
			return false;
		}
		
		HennaTemplate henna = this.henna[slot];
		this.henna[slot] = null;
		
		if (henna.isFourthSlot()) {
			removeHennaSkills(henna);
		}
		
		Connection con = null;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(DELETE_CHAR_HENNA);
			
			statement.setInt(1, getObjectId());
			statement.setInt(2, slot + 1);
			statement.setInt(3, getClassIndex());
			
			statement.execute();
			statement.close();
		} catch (Exception e) {
			log.error("Failed remocing character henna.", e);
		} finally {
			L2DatabaseFactory.close(con);
		}
		
		// Calculate Henna modifiers of this Player
		recalcHennaStats();
		
		// Send Server->Client HennaInfo packet to this Player
		sendPacket(new HennaInfo(this));
		
		// Send Server->Client UserInfo packet to this Player
		sendPacket(new UserInfo(this));
		// Add the recovered dyes to the player's inventory and notify them.
		getInventory().addItem("Henna", henna.getDyeId(), henna.getAmountDyeRequire() / 2, this, null);
		
		reduceAdena("Henna", henna.getPrice() / 5, this, false);
		
		SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.EARNED_S2_S1_S);
		sm.addItemName(henna.getDyeId());
		sm.addItemNumber(henna.getAmountDyeRequire() / 2);
		sendPacket(sm);
		
		sendPacket(SystemMessage.getSystemMessage(SystemMessageId.SYMBOL_DELETED));
		
		return true;
	}
	
	public void addHennaSkills(HennaTemplate henna) {
		//Add the 4rt slot dye skills
		HennaTemplate pHenna = getHenna(4);
		if (pHenna != null) {
			for (SkillHolder skill : henna.getSkills()) {
				if (skill == null) {
					continue;
				}
				addSkill(skill.getSkill());
			}
			sendSkillList();
		}
	}
	
	public void removeHennaSkills(HennaTemplate henna) {
		//Add the 4rt slot dye skills
		for (SkillHolder skill : henna.getSkills()) {
			if (skill == null) {
				continue;
			}
			removeSkill(skill.getSkill());
		}
		sendSkillList();
	}
	
	/**
	 * Add a Henna to the Player, save update in the character_hennas table of the database and send Server->Client HennaInfo/UserInfo packet to this Player.<BR><BR>
	 */
	public boolean addHenna(HennaTemplate henna) {
		for (int i = 0; i < 4; i++) {
			if (this.henna[i] == null) {
				if (henna.isFourthSlot()) {
					this.henna[3] = henna;
				} else {
					this.henna[i] = henna;
				}
				
				// Calculate Henna modifiers of this Player
				recalcHennaStats();
				
				Connection con = null;
				try {
					con = L2DatabaseFactory.getInstance().getConnection();
					PreparedStatement statement = con.prepareStatement(ADD_CHAR_HENNA);
					
					statement.setInt(1, getObjectId());
					statement.setInt(2, henna.getSymbolId());
					statement.setInt(3, henna.isFourthSlot() ? 4 : i + 1);
					statement.setInt(4, getClassIndex());
					statement.setLong(5, henna.isFourthSlot() ? System.currentTimeMillis() + henna.getMaxTime() : 0);
					statement.execute();
					statement.close();
				} catch (Exception e) {
					log.error("Failed saving character henna.", e);
				} finally {
					L2DatabaseFactory.close(con);
				}
				
				if (henna.isFourthSlot()) {
					henna.setExpiryTime(System.currentTimeMillis() + henna.getMaxTime());
				}
				
				// Send Server->Client HennaInfo packet to this Player
				sendPacket(new HennaInfo(this));
				
				// Send Server->Client UserInfo packet to this Player
				sendPacket(new UserInfo(this));
				
				if (henna.isFourthSlot()) {
					addHennaSkills(henna);
				}
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Calculate Henna modifiers of this Player.<BR><BR>
	 */
	private void recalcHennaStats() {
		hennaINT = 0;
		hennaSTR = 0;
		hennaCON = 0;
		hennaMEN = 0;
		hennaWIT = 0;
		hennaDEX = 0;
		hennaLUC = 0;
		hennaCHA = 0;
		
		for (byte i = 0; i < 6; i++) {
			hennaElem[i] = 0;
		}
		
		for (int i = 0; i < 3; i++) {
			if (henna[i] == null) {
				continue;
			}
			
			hennaINT += henna[i].getStatINT();
			hennaSTR += henna[i].getStatSTR();
			hennaMEN += henna[i].getStatMEM();
			hennaCON += henna[i].getStatCON();
			hennaWIT += henna[i].getStatWIT();
			hennaDEX += henna[i].getStatDEX();
			hennaLUC += henna[i].getStatLUC();
			hennaCHA += henna[i].getStatCHA();
			
			hennaElem[this.henna[i].getStatElemId()] = henna[i].getStatElemVal();
		}
	}
	
	/**
	 * Return the Henna of this Player corresponding to the selected slot.<BR><BR>
	 */
	public HennaTemplate getHenna(int slot) {
		if (slot < 1 || slot > 4) {
			return null;
		}
		
		return henna[slot - 1];
	}
	
	/**
	 * Return the INT Henna modifier of this Player.<BR><BR>
	 */
	public int getHennaStatINT() {
		return hennaINT;
	}
	
	/**
	 * Return the STR Henna modifier of this Player.<BR><BR>
	 */
	public int getHennaStatSTR() {
		return hennaSTR;
	}
	
	/**
	 * Return the CON Henna modifier of this Player.<BR><BR>
	 */
	public int getHennaStatCON() {
		return hennaCON;
	}
	
	/**
	 * Return the MEN Henna modifier of this Player.<BR><BR>
	 */
	public int getHennaStatMEN() {
		return hennaMEN;
	}
	
	/**
	 * Return the WIT Henna modifier of this Player.<BR><BR>
	 */
	public int getHennaStatWIT() {
		return hennaWIT;
	}
	
	/**
	 * Return the LUC Henna modifier of this Player.<BR><BR>
	 */
	public int getHennaStatLUC() {
		return hennaLUC;
	}
	
	/**
	 * Return the CHA Henna modifier of this Player.<BR><BR>
	 */
	public int getHennaStatCHA() {
		return hennaCHA;
	}
	
	/**
	 * Return the DEX Henna modifier of this Player.<BR><BR>
	 */
	public int getHennaStatDEX() {
		return hennaDEX;
	}
	
	public int getHennaStatElem(byte elemId) {
		if (elemId < 0) {
			return 0;
		}
		
		return hennaElem[elemId];
	}
	
	/**
	 * Return True if the Player is autoAttackable.<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Check if the attacker isn't the Player Pet </li>
	 * <li>Check if the attacker is MonsterInstance</li>
	 * <li>If the attacker is a Player, check if it is not in the same party </li>
	 * <li>Check if the Player has negative Reputation </li>
	 * <li>If the attacker is a Player, check if it is not in the same siege clan (Attacker, Defender) </li><BR><BR>
	 */
	@Override
	public boolean isAutoAttackable(Creature attacker) {
		// Check invulnerability
		//if (isInvul())
		//return false;
		
		if (getIsInsideGMEvent()) {
			return GMEventManager.getInstance().canAttack(this, attacker);
		}
		
		// Check if the attacker isn't the Player Pet
		if (attacker == this || attacker == getPet() || getSummons().contains(attacker)) {
			return false;
		}
		
		// TODO: check for friendly mobs
		// Check if the attacker is a MonsterInstance
		if (attacker instanceof MonsterInstance) {
			return true;
		}
		
		// Check if the attacker is in olympia and olympia start
		if (attacker instanceof Player && ((Player) attacker).isInOlympiadMode()) {
			return isInOlympiadMode() && isOlympiadStart() && ((Player) attacker).getOlympiadGameId() == getOlympiadGameId();
		}
		
		// Check if the attacker is not in the same clan
		boolean sameClan = getClan() != null && getClan().isMember(attacker.getObjectId());
		boolean sameParty = getParty() != null && getParty().getPartyMembers() != null && getParty().getPartyMembers().contains(attacker);
		
		// Check if the Player has negative Reputation or is engaged in a pvp
		if ((getReputation() < 0 || getPvpFlag() > 0) && !sameClan && !sameParty) {
			return true;
		}
		
		// Check if the attacker is a Playable
		if (attacker instanceof Playable) {
			if (isInsideZone(ZONE_PEACE)) {
				return false;
			}
			
			// Get Player
			Player cha = attacker.getActingPlayer();
			
			// Check if the attacker is in event and event is started
			if (isPlayingEvent() && EventsManager.getInstance().isPlayerParticipant(cha.getObjectId())) {
				EventInstance attackerEvent = cha.getEvent();
				return event.getConfig().isAllVsAll() ||
						event == attackerEvent && event.getParticipantTeamId(getObjectId()) != event.getParticipantTeamId(cha.getObjectId());
			}
			
			if (isInDuel() && attacker instanceof Player && ((Player) attacker).isInDuel()) {
				return true;
			}
			
			// is AutoAttackable if both players are in the same duel and the duel is still going on
			if (getDuelState() == Duel.DUELSTATE_DUELLING && getDuelId() == cha.getDuelId()) {
				return true;
			}
			
			// Check if the attacker is not in the same party
			if (sameParty) {
				return false;
			}
			
			// Check if the attacker is not in the same clan
			if (sameClan) {
				return false;
			}
			
			if (getClan() != null) {
				Siege siege = CastleSiegeManager.getInstance().getSiege(getX(), getY(), getZ());
				if (siege != null) {
					// Check if a siege is in progress and if attacker and the Player aren't in the Defender clan
					if (siege.checkIsDefender(cha.getClan()) && siege.checkIsDefender(getClan())) {
						return false;
					}
					
					// Check if a siege is in progress and if attacker and the Player aren't in the Attacker clan
					if (siege.checkIsAttacker(cha.getClan()) && siege.checkIsAttacker(getClan())) {
						return false;
					}
				}
				
				// Check if clan is at war
				if (getClan() != null && cha.getClan() != null && getClan().isAtWarWith(cha.getClanId()) && cha.getClan().isAtWarWith(getClanId()) &&
						getWantsPeace() == 0 && cha.getWantsPeace() == 0 && !isAcademyMember()) {
					return true;
				}
			}
			
			// Check if the Player is in an arena or a siege area
			if (isInsideZone(ZONE_PVP) && cha.isInsideZone(ZONE_PVP)) {
				return true;
			}
		} else if (attacker instanceof DefenderInstance) {
			if (getClan() != null) {
				Siege siege = CastleSiegeManager.getInstance().getSiege(this);
				return siege != null && siege.checkIsAttacker(getClan());
			}
		}
		
		return false;
	}
	
	/**
	 * Check if the active Skill can be casted.<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Check if the skill isn't toggle and is offensive </li>
	 * <li>Check if the target is in the skill cast range </li>
	 * <li>Check if the skill is Spoil type and if the target isn't already spoiled </li>
	 * <li>Check if the caster owns enought consummed Item, enough HP and MP to cast the skill </li>
	 * <li>Check if the caster isn't sitting </li>
	 * <li>Check if all skills are enabled and this skill is enabled </li><BR><BR>
	 * <li>Check if the caster own the weapon needed </li><BR><BR>
	 * <li>Check if the skill is active </li><BR><BR>
	 * <li>Check if all casting conditions are completed</li><BR><BR>
	 * <li>Notify the AI with AI_INTENTION_CAST and target</li><BR><BR>
	 *
	 * @param skill    The Skill to use
	 * @param forceUse used to force ATTACK on players
	 * @param dontMove used to prevent movement, if not in range
	 */
	@Override
	public boolean useMagic(Skill skill, boolean forceUse, boolean dontMove) {
		if (getFirstEffect(30517) != null) // Heavy Hand
		{
			switch (skill.getId()) {
				case 10529: // Shadow Flash
				case 10267: // Hurricane Rush
				case 11057: // Magical Evasion
				case 11094: // Magical Charge
				case 10805: // Quick Charge
				case 10774: // Quick Evasion
				case 11508: // Assault Rush
				{
					// These skills canno't be used while Heavy Hand is active.
					sendPacket(ActionFailed.STATIC_PACKET);
					return false;
				}
			}
		}
		if (isGM()) {
			sendSysMessage("");
			sendSysMessage("");
			sendSysMessage("");
			sendSysMessage("");
			sendSysMessage("");
			sendSysMessage("=== Skill [" + skill.getName() + ":" + skill.getId() + "-" + skill.getLevel() + "] Data ===");
			sendSysMessage("Target > " + skill.getTargetType());
			sendSysMessage("TargetDirection > " + skill.getTargetDirection());
			sendSysMessage("BehaviorType > " + skill.getSkillBehavior());
			sendSysMessage("Type > " + skill.getSkillType());
			sendSysMessage("Reuse > " + skill.getReuseDelay() + " [" + (skill.isStaticReuse() ? "STATIC" : "NOT STATIC") + "]");
			sendSysMessage("CastTime > " + skill.getHitTime() + " [" + (skill.isStaticHitTime() ? "STATIC" : "NOT STATIC") + "]");
			sendSysMessage("CastRange > " + skill.getCastRange());
			sendSysMessage("EffectRange > " + skill.getEffectRange());
			sendSysMessage("Power > " + skill.getPower());
			sendSysMessage("OverHit > " + skill.isOverhit());
			sendSysMessage("Crit Rate > " + skill.getBaseCritRate());
			
			if (skill.getEffectTemplates() != null) {
				int abnormalId = 0;
				for (AbnormalTemplate abnormalTemplate : skill.getEffectTemplates()) {
					abnormalId++;
					
					Env env = new Env();
					
					env.player = this;
					
					if (getTarget() instanceof Creature) {
						env.target = (Creature) getTarget();
					}
					
					env.skill = skill;
					
					Abnormal abnormal = abnormalTemplate.getEffect(env);
					
					sendSysMessage("=== Abnormal[" + abnormalId + "] === ");
					sendSysMessage("- Type = " + abnormal.getType());
					sendSysMessage("- Level = " + abnormal.getLevel());
					sendSysMessage("- Land Rate = " + abnormal.getLandRate());
					sendSysMessage("- Count = " + abnormal.getCount());
					sendSysMessage("- Duration = " + abnormal.getDuration());
					sendSysMessage("StackTypes: ");
					
					for (String stackType : abnormal.getStackType()) {
						sendSysMessage("- " + stackType);
					}
					
					sendSysMessage("=== EFFECTS ===");
					
					for (Func func : abnormal.getStatFuncs()) {
						Env fEnv = new Env();
						fEnv.value = 0;
						fEnv.player = this;
						boolean isPercent = func instanceof FuncAddPercent || func instanceof FuncSubPercent || func instanceof FuncAddPercentBase ||
								func instanceof FuncSubPercentBase; // todo more cases
						if (isPercent) {
							fEnv.value = 100;
						}
						/*
						else if (func instanceof FuncMul || func instanceof FuncMulBase || func instanceof FuncSet ||
								func instanceof
								func instanceof FuncBaseAdd ||
								func instanceof FuncBaseSub ||
								func instanceof FuncAdd) // todo more cases
							fEnv.value = 0;*/
						
						fEnv.baseValue = fEnv.value;
						func.calc(fEnv);
						double val = fEnv.value;
						if (isPercent) {
							val -= 100;
						}
						
						sendSysMessage(
								func.getClass().getSimpleName().substring(4).toUpperCase() + " > " + func.stat.getValue().toUpperCase() + " > " +
										val);
					}
				}
			}
		}
		
		// Check if the skill is active
		if (skill.isPassive()) {
			// just ignore the passive skill request. why does the client send it anyway ??
			// Send a Server->Client packet ActionFailed to the Player
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		//************************************* Check Casting in Progress *******************************************
		
		// If a skill is currently being used, queue this one if this is not the same
		if (!canCastNow(skill)) {
			//log.info(getName() + " cant cast now..");
			SkillDat currentSkill = getCurrentSkill();
			// Check if new skill different from current skill in progress
			if (currentSkill != null &&
					(skill.getId() == currentSkill.getSkillId() || currentSkill.getSkill().getSkillType() == SkillType.CLASS_CHANGE)) {
				sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			}
			
			if (Config.DEBUG && getQueuedSkill() != null) {
				log.info(getQueuedSkill().getSkill().getName() + " is already queued for " + getName() + ".");
			}
			
			// Create a new SkillDat object and queue it in the player queuedSkill
			setQueuedSkill(skill, forceUse, dontMove);
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		if (canDoubleCast() && isCastingNow1()) {
			setIsCastingNow2(true);
		} else {
			setIsCastingNow(true);
		}
		// Create a new SkillDat object and set the player currentSkill
		// This is used mainly to save & queue the button presses, since Creature has
		// lastSkillCast which could otherwise replace it
		setCurrentSkill(skill, forceUse, dontMove);
		
		if (getQueuedSkill() != null) // wiping out previous values, after casting has been aborted
		{
			setQueuedSkill(null, false, false);
		}
		
		if (!checkUseMagicConditions(skill, forceUse, dontMove)) {
			if (wasLastCast1()) {
				setIsCastingNow(false);
			} else {
				setIsCastingNow2(false);
			}
			return false;
		}
		
		// Check if the target is correct and Notify the AI with AI_INTENTION_CAST and target
		WorldObject target = null;
		
		switch (skill.getTargetType()) {
			case TARGET_AURA: // AURA, SELF should be cast even if no target has been found
			case TARGET_FRONT_AURA:
			case TARGET_BEHIND_AURA:
			case TARGET_GROUND:
			case TARGET_GROUND_AREA:
			case TARGET_SELF:
			case TARGET_AURA_CORPSE_MOB:
				target = this;
				break;
			default:
				// Get the first target of the list
				if (skill.isUseableWithoutTarget()) {
					target = this;
				} else if (skill.getTargetDirection() == SkillTargetDirection.CHAIN_HEAL) {
					target = getTarget();
				} else {
					target = skill.getFirstOfTargetList(this);
				}
				break;
		}
		
		// Notify the AI with AI_INTENTION_CAST and target
		getAI().setIntention(CtrlIntention.AI_INTENTION_CAST, skill, target);
		
		if (skill.getId() == 30001) {
			setQueuedSkill(skill, forceUse, dontMove);
		}
		
		return true;
	}
	
	@Override
	public boolean canDoubleCast() {
		return elementalStance >= 10 && isAffected(EffectType.DOUBLE_CASTING.getMask());
	}
	
	private boolean checkUseMagicConditions(Skill skill, boolean forceUse, boolean dontMove) {
		SkillType sklType = skill.getSkillType();
		
		//************************************* Check Player State *******************************************
		
		// Abnormal effects(ex : Stun, Sleep...) are checked in Creature useMagic()
		boolean canCastWhileStun = false;
		switch (skill.getId()) {
			case 30008: // Wind Blend
			case 19227: // Wind Blend Trigger
			case 30009: // Deceptive Blink
			{
				canCastWhileStun = true;
				break;
			}
			default: {
				break;
			}
		}
		
		if ((isOutOfControl() || isParalyzed() || isStunned() && !canCastWhileStun || isSleeping()) && !skill.canBeUsedWhenDisabled()) {
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		// Check if the player is dead
		if (isDead()) {
			// Send a Server->Client packet ActionFailed to the Player
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		if (isFishing() && sklType != SkillType.PUMPING && sklType != SkillType.REELING && sklType != SkillType.FISHING) {
			//Only fishing skills are available
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.ONLY_FISHING_SKILLS_NOW));
			return false;
		}
		
		if (inObserverMode()) {
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.OBSERVERS_CANNOT_PARTICIPATE));
			abortCast();
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		// Check if the caster is sitting
		if (isSitting()) {
			// Send a System Message to the caster
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CANT_MOVE_SITTING));
			
			// Send a Server->Client packet ActionFailed to the Player
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		// Check if the skill type is TOGGLE
		if (skill.isToggle()) {
			// Get effects of the skill
			Abnormal effect = getFirstEffect(skill.getId());
			
			if (effect != null) {
				effect.exit();
				
				// Send a Server->Client packet ActionFailed to the Player
				sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			}
		}
		
		// Check if the player uses "Fake Death" skill
		// Note: do not check this before TOGGLE reset
		if (isFakeDeath()) {
			// Send a Server->Client packet ActionFailed to the Player
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		//************************************* Check Target *******************************************
		// Create and set a WorldObject containing the target of the skill
		WorldObject target = null;
		SkillTargetType sklTargetType = skill.getTargetType();
		Point3D worldPosition = getSkillCastPosition();
		
		if ((sklTargetType == SkillTargetType.TARGET_GROUND || sklTargetType == SkillTargetType.TARGET_GROUND_AREA) && worldPosition == null) {
			log.info("WorldPosition is null for skill: " + skill.getName() + ", player: " + getName() + ".");
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		switch (sklTargetType) {
			// Target the player if skill type is AURA, PARTY, CLAN or SELF
			case TARGET_AURA:
			case TARGET_FRONT_AURA:
			case TARGET_BEHIND_AURA:
			case TARGET_PARTY:
			case TARGET_ALLY:
			case TARGET_CLAN:
			case TARGET_PARTY_CLAN:
			case TARGET_GROUND:
			case TARGET_GROUND_AREA:
			case TARGET_SELF:
			case TARGET_SUMMON:
			case TARGET_AURA_CORPSE_MOB:
			case TARGET_AREA_SUMMON:
				target = this;
				break;
			default:
				if (skill.isUseableWithoutTarget()) {
					target = this;
				} else {
					target = getTarget();
				}
				break;
		}
		
		// Check the validity of the target
		if (target == null) {
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		// skills can be used on Walls and Doors only during siege
		if (target instanceof DoorInstance) {
			boolean isCastle = ((DoorInstance) target).getCastle() != null && ((DoorInstance) target).getCastle().getCastleId() > 0 &&
					((DoorInstance) target).getCastle().getSiege().getIsInProgress() && ((DoorInstance) target).getIsShowHp();
			boolean isFort = ((DoorInstance) target).getFort() != null && ((DoorInstance) target).getFort().getFortId() > 0 &&
					((DoorInstance) target).getFort().getSiege().getIsInProgress() && !((DoorInstance) target).getIsShowHp();
			if (!isCastle && !isFort && ((DoorInstance) target).isOpenableBySkill() && skill.getSkillType() != SkillType.UNLOCK) {
				return false;
			}
		}
		
		if (target != this && target instanceof Playable && ((Playable) target).isAffected(EffectType.UNTARGETABLE.getMask())) {
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		// Are the target and the player in the same duel?
		if (isInDuel()) {
			// Get Player
			if (target instanceof Playable) {
				// Get Player
				Player cha = target.getActingPlayer();
				if (cha.getDuelId() != getDuelId()) {
					sendMessage("You cannot do this while duelling.");
					sendPacket(ActionFailed.STATIC_PACKET);
					return false;
				}
			}
		}
		
		//************************************* Check skill availability *******************************************
		
		// Check if it's ok to summon
		//Siege Golems
		if (sklType == SkillType.SUMMON) {
			int npcId = ((SkillSummon) skill).getNpcId();
			NpcTemplate summonTemplate = NpcTable.getInstance().getTemplate(npcId);
			if (summonTemplate != null) {
				if (summonTemplate.Type.equalsIgnoreCase("L2SiegeSummon") && !CastleSiegeManager.getInstance().checkIfOkToSummon(this, false) &&
						!FortSiegeManager.getInstance().checkIfOkToSummon(this, false)) {
					return false;
				}
			}
		}
		
		// Check if this skill is enabled (ex : reuse time)
		if (isSkillDisabled(skill)) {
			SystemMessage sm = null;
			
			if (reuseTimeStamps.containsKey(skill.getReuseHashCode())) {
				int remainingTime = (int) (reuseTimeStamps.get(skill.getReuseHashCode()).getRemaining() / 1000);
				int hours = remainingTime / 3600;
				int minutes = remainingTime % 3600 / 60;
				int seconds = remainingTime % 60;
				if (hours > 0) {
					sm = SystemMessage.getSystemMessage(SystemMessageId.S2_HOURS_S3_MINUTES_S4_SECONDS_REMAINING_FOR_REUSE_S1);
					sm.addSkillName(skill);
					sm.addNumber(hours);
					sm.addNumber(minutes);
				} else if (minutes > 0) {
					sm = SystemMessage.getSystemMessage(SystemMessageId.S2_MINUTES_S3_SECONDS_REMAINING_FOR_REUSE_S1);
					sm.addSkillName(skill);
					sm.addNumber(minutes);
				} else {
					sm = SystemMessage.getSystemMessage(SystemMessageId.S2_SECONDS_REMAINING_FOR_REUSE_S1);
					sm.addSkillName(skill);
				}
				
				sm.addNumber(seconds);
			} else {
				sm = SystemMessage.getSystemMessage(SystemMessageId.S1_PREPARED_FOR_REUSE);
				sm.addSkillName(skill);
			}
			
			sendPacket(sm);
			return false;
		}
		
		//************************************* Check Consumables *******************************************
		
		// Check if spell consumes a Soul
		if (skill.getSoulConsumeCount() > 0) {
			if (getSouls() < skill.getSoulConsumeCount()) {
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.THERE_IS_NOT_ENOUGH_SOUL));
				sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			}
		}
		//************************************* Check casting conditions *******************************************
		
		// Check if all casting conditions are completed
		if (!skill.checkCondition(this, target, false)) {
			// Send a Server->Client packet ActionFailed to the Player
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		//************************************* Check Skill Type *******************************************
		
		// Check if this is offensive magic skill
		if (skill.isOffensive()) {
			if (!isInDuel() && isInsidePeaceZone(this, target) && !getAccessLevel().allowPeaceAttack()) {
				// If Creature or target is in a peace zone, send a system message TARGET_IN_PEACEZONE a Server->Client packet ActionFailed
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.TARGET_IN_PEACEZONE));
				sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			}
			
			if (isInOlympiadMode() && !isOlympiadStart()) {
				// if Player is in Olympia and the match isn't already start, send a Server->Client packet ActionFailed
				sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			}
			
			if (target.getActingPlayer() != null && getSiegeState() > 0 && isInsideZone(Creature.ZONE_SIEGE) &&
					target.getActingPlayer().getSiegeState() == getSiegeState() && target.getActingPlayer() != this &&
					target.getActingPlayer().getSiegeSide() == getSiegeSide() && !Config.isServer(Config.TENKAI)) {
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.FORCED_ATTACK_IS_IMPOSSIBLE_AGAINST_SIEGE_SIDE_TEMPORARY_ALLIED_MEMBERS));
				sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			}
			
			// Check if the target is attackable
			if (target != this && !(target instanceof NpcInstance) && !isInDuel() && target instanceof Player &&
					!((Player) target).isInDuel() && !target.isAttackable() && !getAccessLevel().allowPeaceAttack()) {
				// If target is not attackable, send a Server->Client packet ActionFailed
				sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			}
			
			// Check if a Forced ATTACK is in progress on non-attackable target
			if (!target.isAutoAttackable(this) && !forceUse) {
				switch (sklTargetType) {
					case TARGET_AURA:
					case TARGET_FRONT_AURA:
					case TARGET_BEHIND_AURA:
					case TARGET_CLAN:
					case TARGET_PARTY_CLAN:
					case TARGET_ALLY:
					case TARGET_PARTY:
					case TARGET_SELF:
					case TARGET_GROUND:
					case TARGET_GROUND_AREA:
					case TARGET_AURA_CORPSE_MOB:
					case TARGET_AREA_SUMMON:
					case TARGET_AROUND_CASTER:
					case TARGET_FRIENDS:
					case TARGET_AROUND_TARGET:
						break;
					default: // Send a Server->Client packet ActionFailed to the Player
						sendPacket(ActionFailed.STATIC_PACKET);
						return false;
				}
			}
			
			// Check if the target is in the skill cast range
			if (dontMove) {
				// Calculate the distance between the Player and the target
				if (sklTargetType == SkillTargetType.TARGET_GROUND || sklTargetType == SkillTargetType.TARGET_GROUND_AREA) {
					if (!isInsideRadius(worldPosition.getX(),
							worldPosition.getY(),
							worldPosition.getZ(),
							skill.getCastRange() + getTemplate().collisionRadius,
							false,
							false)) {
						// Send a System Message to the caster
						sendPacket(SystemMessage.getSystemMessage(SystemMessageId.TARGET_TOO_FAR));
						
						// Send a Server->Client packet ActionFailed to the Player
						sendPacket(ActionFailed.STATIC_PACKET);
						return false;
					}
				} else if (skill.getCastRange() > 0 && !isInsideRadius(target, skill.getCastRange() + getTemplate().collisionRadius, false, false)) {
					// Send a System Message to the caster
					sendPacket(SystemMessage.getSystemMessage(SystemMessageId.TARGET_TOO_FAR));
					
					// Send a Server->Client packet ActionFailed to the Player
					sendPacket(ActionFailed.STATIC_PACKET);
					return false;
				}
			}
		}
		
		if (skill.getSkillType() == SkillType.INSTANT_JUMP) {
			if (skill.getId() == 10529) // TODO this should be done in the skill xml...
			{
				SkillTable.getInstance().getInfo(10530, 1).getEffects(this, this);
			}
			
			// You cannot jump while movement disabled
			if (isMovementDisabled() && !isRooted()) {
				// Sends message that skill cannot be used...
				SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S1_CANNOT_BE_USED);
				sm.addSkillName(skill.getId());
				sendPacket(sm);
				
				// Send a Server->Client packet ActionFailed to the Player
				sendPacket(ActionFailed.STATIC_PACKET);
				
				return false;
			}
			
			// And this skill cannot be used in peace zone, not even on NPCs!
			if (isInsideZone(Creature.ZONE_PEACE)) {
				//Sends a sys msg to client
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.TARGET_IN_PEACEZONE));
				
				// Send a Server->Client packet ActionFailed to the Player
				sendPacket(ActionFailed.STATIC_PACKET);
				
				return false;
			}
		}
		// Check if the skill is defensive
		if (skill.getSkillBehavior() != SkillBehaviorType.ATTACK && !skill.isOffensive() && target instanceof MonsterInstance && !forceUse &&
				!skill.isNeutral()) {
			// check if the target is a monster and if force attack is set.. if not then we don't want to cast.
			switch (sklTargetType) {
				case TARGET_SUMMON:
				case TARGET_AURA:
				case TARGET_FRONT_AURA:
				case TARGET_BEHIND_AURA:
				case TARGET_CLAN:
				case TARGET_PARTY_CLAN:
				case TARGET_SELF:
				case TARGET_PARTY:
				case TARGET_ALLY:
				case TARGET_CORPSE_MOB:
				case TARGET_AURA_CORPSE_MOB:
				case TARGET_AREA_CORPSE_MOB:
				case TARGET_GROUND:
				case TARGET_GROUND_AREA:
					break;
				default: {
					switch (sklType) {
						case BEAST_FEED:
						case DELUXE_KEY_UNLOCK:
						case UNLOCK:
							break;
						default:
							sendPacket(ActionFailed.STATIC_PACKET);
							return false;
					}
					break;
				}
			}
		}
		
		// Check if the skill is Spoil type and if the target isn't already spoiled
		if (sklType == SkillType.SPOIL) {
			if (!(target instanceof MonsterInstance)) {
				// Send a System Message to the Player
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.INCORRECT_TARGET));
				
				// Send a Server->Client packet ActionFailed to the Player
				sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			}
		}
		
		// Check if the skill is Sweep type and if conditions not apply
		if (sklType == SkillType.SWEEP && target instanceof Attackable) {
			int spoilerId = ((Attackable) target).getIsSpoiledBy();
			
			if (((Attackable) target).isDead()) {
				if (!((Attackable) target).isSpoil()) {
					// Send a System Message to the Player
					sendPacket(SystemMessage.getSystemMessage(SystemMessageId.SWEEPER_FAILED_TARGET_NOT_SPOILED));
					
					// Send a Server->Client packet ActionFailed to the Player
					sendPacket(ActionFailed.STATIC_PACKET);
					return false;
				}
				
				if (getObjectId() != spoilerId && !isInLooterParty(spoilerId)) {
					// Send a System Message to the Player
					sendPacket(SystemMessage.getSystemMessage(SystemMessageId.SWEEP_NOT_ALLOWED));
					
					// Send a Server->Client packet ActionFailed to the Player
					sendPacket(ActionFailed.STATIC_PACKET);
					return false;
				}
			}
		}
		
		// Check if the skill is Drain Soul (Soul Crystals) and if the target is a MOB
		if (sklType == SkillType.DRAIN_SOUL) {
			if (!(target instanceof MonsterInstance)) {
				// Send a System Message to the Player
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.INCORRECT_TARGET));
				
				// Send a Server->Client packet ActionFailed to the Player
				sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			}
		}
		
		// Check if this is a Pvp skill and target isn't a non-flagged/non-karma player
		switch (sklTargetType) {
			case TARGET_PARTY:
			case TARGET_ALLY: // For such skills, checkPvpSkill() is called from Skill.getTargetList()
			case TARGET_CLAN: // For such skills, checkPvpSkill() is called from Skill.getTargetList()
			case TARGET_PARTY_CLAN: // For such skills, checkPvpSkill() is called from Skill.getTargetList()
			case TARGET_AURA:
			case TARGET_FRONT_AURA:
			case TARGET_BEHIND_AURA:
			case TARGET_GROUND:
			case TARGET_GROUND_AREA:
			case TARGET_SELF:
			case TARGET_AURA_CORPSE_MOB:
				break;
			default:
				if (!checkPvpSkill(target, skill) && !getAccessLevel().allowPeaceAttack()) {
					// Send a System Message to the Player
					sendPacket(SystemMessage.getSystemMessage(SystemMessageId.TARGET_IS_INCORRECT));
					
					// Send a Server->Client packet ActionFailed to the Player
					sendPacket(ActionFailed.STATIC_PACKET);
					return false;
				}
		}
		
		// TODO: Unhardcode skillId 844 which is the outpost construct skill
		if (sklTargetType == SkillTargetType.TARGET_HOLY && !checkIfOkToCastSealOfRule(CastleManager.getInstance().getCastle(this), false, skill) ||
				sklTargetType == SkillTargetType.TARGET_FLAGPOLE &&
						!checkIfOkToCastFlagDisplay(FortManager.getInstance().getFort(this), false, skill) ||
				sklType == SkillType.SIEGEFLAG && !SkillSiegeFlag.checkIfOkToPlaceFlag(this, false, skill.getId() == 844) ||
				sklType == SkillType.STRSIEGEASSAULT && !checkIfOkToUseStriderSiegeAssault() ||
				sklType == SkillType.SUMMON_FRIEND && !(checkSummonerStatus(this) && checkSummonTargetStatus(target, this))) {
			sendPacket(ActionFailed.STATIC_PACKET);
			abortCast();
			return false;
		}
		
		// GeoData Los Check here
		if (skill.getCastRange() > 0) {
			if (sklTargetType == SkillTargetType.TARGET_GROUND || sklTargetType == SkillTargetType.TARGET_GROUND_AREA) {
				if (!GeoData.getInstance().canSeeTarget(this, worldPosition)) {
					sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CANT_SEE_TARGET));
					sendPacket(ActionFailed.STATIC_PACKET);
					return false;
				}
			} else if (!GeoData.getInstance().canSeeTarget(this, target)) {
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CANT_SEE_TARGET));
				sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			}
		}
		// finally, after passing all conditions
		return true;
	}
	
	public boolean checkIfOkToUseStriderSiegeAssault() {
		Castle castle = CastleManager.getInstance().getCastle(this);
		Fort fort = FortManager.getInstance().getFort(this);
		
		if (castle == null && fort == null) {
			return false;
		}
		
		if (castle != null) {
			return checkIfOkToUseStriderSiegeAssault(castle);
		} else {
			return checkIfOkToUseStriderSiegeAssault(fort);
		}
	}
	
	public boolean checkIfOkToUseStriderSiegeAssault(Castle castle) {
		String text = "";
		
		if (castle == null || castle.getCastleId() <= 0) {
			text = "You must be on castle ground to use strider siege assault";
		} else if (!castle.getSiege().getIsInProgress()) {
			text = "You can only use strider siege assault during a siege.";
		} else if (!(getTarget() instanceof DoorInstance)) {
			text = "You can only use strider siege assault on doors and walls.";
		} else if (!isRidingStrider()) {
			text = "You can only use strider siege assault when on strider.";
		} else {
			return true;
		}
		
		sendMessage(text);
		
		return false;
	}
	
	public boolean checkIfOkToUseStriderSiegeAssault(Fort fort) {
		String text = "";
		
		if (fort == null || fort.getFortId() <= 0) {
			text = "You must be on fort ground to use strider siege assault";
		} else if (!fort.getSiege().getIsInProgress()) {
			text = "You can only use strider siege assault during a siege.";
		} else if (!(getTarget() instanceof DoorInstance)) {
			text = "You can only use strider siege assault on doors and walls.";
		} else if (!isRidingStrider()) {
			text = "You can only use strider siege assault when on strider.";
		} else {
			return true;
		}
		
		sendMessage(text);
		
		return false;
	}
	
	public boolean checkIfOkToCastSealOfRule(Castle castle, boolean isCheckOnly, Skill skill) {
		SystemMessage sm;
		
		if (castle == null || castle.getCastleId() <= 0) {
			sm = SystemMessage.getSystemMessage(SystemMessageId.S1_CANNOT_BE_USED);
			sm.addSkillName(skill);
		} else if (!castle.getArtefacts().contains(getTarget())) {
			sm = SystemMessage.getSystemMessage(SystemMessageId.INCORRECT_TARGET);
		} else if (!castle.getSiege().getIsInProgress()) {
			sm = SystemMessage.getSystemMessage(SystemMessageId.S1_CANNOT_BE_USED);
			sm.addSkillName(skill);
		} else if (!Util.checkIfInRange(85, this, getTarget(), true)) {
			sm = SystemMessage.getSystemMessage(SystemMessageId.DIST_TOO_FAR_CASTING_STOPPED);
		} else if (castle.getSiege().getAttackerClan(getClan()) == null) {
			sm = SystemMessage.getSystemMessage(SystemMessageId.S1_CANNOT_BE_USED);
			sm.addSkillName(skill);
		} else {
			if (!isCheckOnly) {
				sm = SystemMessage.getSystemMessage(SystemMessageId.OPPONENT_STARTED_ENGRAVING);
				castle.getSiege().announceToPlayer(sm, false);
			}
			return true;
		}
		
		sendPacket(sm);
		return false;
	}
	
	public boolean checkIfOkToCastFlagDisplay(Fort fort, boolean isCheckOnly, Skill skill) {
		SystemMessage sm;
		
		if (fort == null || fort.getFortId() <= 0) {
			sm = SystemMessage.getSystemMessage(SystemMessageId.S1_CANNOT_BE_USED);
			sm.addSkillName(skill);
		} else if (fort.getFlagPole() != getTarget()) {
			sm = SystemMessage.getSystemMessage(SystemMessageId.INCORRECT_TARGET);
		} else if (!fort.getSiege().getIsInProgress()) {
			sm = SystemMessage.getSystemMessage(SystemMessageId.S1_CANNOT_BE_USED);
			sm.addSkillName(skill);
		} else if (!Util.checkIfInRange(85, this, getTarget(), true)) {
			sm = SystemMessage.getSystemMessage(SystemMessageId.DIST_TOO_FAR_CASTING_STOPPED);
		} else if (fort.getSiege().getAttackerClan(getClan()) == null) {
			sm = SystemMessage.getSystemMessage(SystemMessageId.S1_CANNOT_BE_USED);
			sm.addSkillName(skill);
		} else {
			if (!isCheckOnly) {
				fort.getSiege().announceToPlayer(SystemMessage.getSystemMessage(SystemMessageId.S1_TRYING_RAISE_FLAG), getClan().getName());
			}
			return true;
		}
		
		sendPacket(sm);
		return false;
	}
	
	public boolean isInLooterParty(int LooterId) {
		Player looter = World.getInstance().getPlayer(LooterId);
		
		// if Player is in a CommandChannel
		if (isInParty() && getParty().isInCommandChannel() && looter != null) {
			return getParty().getCommandChannel().getMembers().contains(looter);
		}
		
		if (isInParty() && looter != null) {
			return getParty().getPartyMembers().contains(looter);
		}
		
		return false;
	}
	
	/**
	 * Check if the requested casting is a Pc->Pc skill cast and if it's a valid pvp condition
	 *
	 * @param target WorldObject instance containing the target
	 * @param skill  Skill instance with the skill being casted
	 * @return False if the skill is a pvpSkill and target is not a valid pvp target
	 */
	public boolean checkPvpSkill(WorldObject target, Skill skill) {
		return checkPvpSkill(target, skill, false);
	}
	
	/**
	 * Check if the requested casting is a Pc->Pc skill cast and if it's a valid pvp condition
	 *
	 * @param target      WorldObject instance containing the target
	 * @param skill       Skill instance with the skill being casted
	 * @param srcIsSummon is Summon - caster?
	 * @return False if the skill is a pvpSkill and target is not a valid pvp target
	 */
	public boolean checkPvpSkill(WorldObject target, Skill skill, boolean srcIsSummon) {
		if (isPlayingEvent()) {
			return true;
		}
		
		if (!Config.isServer(Config.TENKAI) && skill.getSkillBehavior() == SkillBehaviorType.ATTACK) {
			return true;
		}
		
		// check for PC->PC Pvp status
		if (target instanceof Summon) {
			target = target.getActingPlayer();
		}
		if (target != null && // target not null and
				target != this && // target is not self and
				target instanceof Player && // target is Player and
				!(isInDuel() && ((Player) target).getDuelId() == getDuelId()) &&
				// self is not in a duel and attacking opponent
				!isInsideZone(ZONE_PVP) && // Pc is not in PvP zone
				!((Player) target).isInsideZone(ZONE_PVP) // target is not in PvP zone
				) {
			if (skill.isOffensive() && ((Player) target).isInsidePeaceZone(this)) {
				return false;
			}
			
			SkillDat skilldat = getCurrentSkill();
			SkillDat skilldatpet = getCurrentPetSkill();
			if (skill.isPvpSkill()) // pvp skill
			{
				if (getClan() != null && ((Player) target).getClan() != null) {
					if (getClan().isAtWarWith(((Player) target).getClanId()) && ((Player) target).getClan().isAtWarWith(getClanId())) {
						return true; // in clan war player can attack whites even with sleep etc.
					}
				}
				if (((Player) target).getPvpFlag() == 0 && //   target's pvp flag is not set and
						((Player) target).getReputation() >= 0) {
					return false;
				}
			} else if (skilldat != null && !skilldat.isCtrlPressed() && skill.isOffensive() && !srcIsSummon ||
					skilldatpet != null && !skilldatpet.isCtrlPressed() && skill.isOffensive() && srcIsSummon) {
				if (getClan() != null && ((Player) target).getClan() != null) {
					if (getClan().isAtWarWith(((Player) target).getClanId()) && ((Player) target).getClan().isAtWarWith(getClanId())) {
						return true; // in clan war player can attack whites even without ctrl
					}
				}
				if (!Config.isServer(Config.TENKAI) && ((Player) target).getPvpFlag() == 0 &&
						//   target's pvp flag is not set and
						((Player) target).getReputation() >= 0) {
					return false;
				}
				
				if (Config.isServer(Config.TENKAI) && getPvpFlag() == 0 && ((Player) target).getReputation() >= 0) {
					return false;
				}
			}
		}
		
		return true;
	}
	
	/**
	 * Return True if the Player is a Mage.<BR><BR>
	 */
	public boolean isMageClass() {
		return getCurrentClass().isMage();
	}
	
	public boolean isMounted() {
		return mountType > 0;
	}
	
	/**
	 * Set the type of Pet mounted (0 : none, 1 : Stridder, 2 : Wyvern) and send a Server->Client packet InventoryUpdate to the Player.<BR><BR>
	 */
	public boolean checkLandingState() {
		// Check if char is in a no landing zone
		if (isInsideZone(ZONE_NOLANDING)) {
			return true;
		} else
			// if this is a castle that is currently being sieged, and the rider is NOT a castle owner
			// he cannot land.
			// castle owner is the leader of the clan that owns the castle where the pc is
			if (isInsideZone(ZONE_SIEGE) &&
					!(getClan() != null && CastleManager.getInstance().getCastle(this) == CastleManager.getInstance().getCastleByOwner(getClan()) &&
							this == getClan().getLeader().getPlayerInstance())) {
				return true;
			}
		
		return false;
	}
	
	// returns false if the change of mount type fails.
	public boolean setMount(int npcId, int npcLevel, int mountType) {
		switch (mountType) {
			case 0:
				setIsFlying(false);
				setIsRidingStrider(false);
				break; //Dismounted
			case 1:
				setIsRidingStrider(true);
				if (isNoble()) {
					Skill striderAssaultSkill = SkillTable.FrequentSkill.STRIDER_SIEGE_ASSAULT.getSkill();
					addSkill(striderAssaultSkill, false); // not saved to DB
				}
				break;
			case 2:
				setIsFlying(true);
				break; //Flying Wyvern
			case 3:
				break;
		}
		
		this.mountType = mountType;
		mountNpcId = npcId;
		mountLevel = npcLevel;
		
		return true;
	}
	
	/**
	 * Return the type of Pet mounted (0 : none, 1 : Strider, 2 : Wyvern, 3: Wolf).<BR><BR>
	 */
	public int getMountType() {
		return mountType;
	}
	
	@Override
	public final void stopAllEffects() {
		super.stopAllEffects();
		updateAndBroadcastStatus(2);
	}
	
	@Override
	public final void stopAllEffectsExceptThoseThatLastThroughDeath() {
		super.stopAllEffectsExceptThoseThatLastThroughDeath();
		
		if (!getSummons().isEmpty()) {
			for (SummonInstance summon : getSummons()) {
				if (summon == null) {
					continue;
				}
				summon.stopAllEffectsExceptThoseThatLastThroughDeath();
			}
		}
		
		if (getPet() != null) {
			getPet().stopAllEffectsExceptThoseThatLastThroughDeath();
		}
		
		updateAndBroadcastStatus(2);
	}
	
	/**
	 * Stop all toggle-type effects
	 */
	public final void stopAllToggles() {
		effects.stopAllToggles();
	}
	
	public final void stopCubics() {
		if (getCubics() != null) {
			boolean removed = false;
			for (CubicInstance cubic : getCubics().values()) {
				cubic.stopAction();
				delCubic(cubic.getId());
				removed = true;
			}
			if (removed) {
				broadcastUserInfo();
			}
		}
	}
	
	public final void stopCubicsByOthers() {
		if (getCubics() != null) {
			boolean removed = false;
			for (CubicInstance cubic : getCubics().values()) {
				if (cubic.givenByOther()) {
					cubic.stopAction();
					delCubic(cubic.getId());
					removed = true;
				}
			}
			if (removed) {
				broadcastUserInfo();
			}
		}
	}
	
	/**
	 * Send a Server->Client packet UserInfo to this Player and CharInfo to all Player in its KnownPlayers.<BR><BR>
	 * <p>
	 * <B><U> Concept</U> :</B><BR><BR>
	 * Others Player in the detection area of the Player are identified in <B>knownPlayers</B>.
	 * In order to inform other players of this Player state modifications, server just need to go through knownPlayers to send Server->Client Packet<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Send a Server->Client packet UserInfo to this Player (Public and Private Data)</li>
	 * <li>Send a Server->Client packet CharInfo to all Player in KnownPlayers of the Player (Public data only)</li><BR><BR>
	 * <p>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : DON'T SEND UserInfo packet to other players instead of CharInfo packet.
	 * Indeed, UserInfo packet contains PRIVATE DATA as MaxHP, STR, DEX...</B></FONT><BR><BR>
	 */
	@Override
	public void updateAbnormalEffect() {
		broadcastUserInfo();
		for (SummonInstance summon : summons) {
			summon.updateAbnormalEffect();
		}
	}
	
	/**
	 * Disable the Inventory and create a new task to enable it after 1.5s.<BR><BR>
	 */
	public void tempInventoryDisable() {
		inventoryDisable = true;
		
		ThreadPoolManager.getInstance().scheduleGeneral(new InventoryEnable(), 1500);
	}
	
	/**
	 * Return True if the Inventory is disabled.<BR><BR>
	 */
	public boolean isInventoryDisabled() {
		return inventoryDisable;
	}
	
	private class InventoryEnable implements Runnable {
		@Override
		public void run() {
			inventoryDisable = false;
		}
	}
	
	public Map<Integer, CubicInstance> getCubics() {
		return cubics;
	}
	
	/**
	 * Add a CubicInstance to the Player cubics.<BR><BR>
	 */
	public void addCubic(int id,
	                     int level,
	                     double matk,
	                     int activationtime,
	                     int activationchance,
	                     int maxcount,
	                     int totalLifetime,
	                     boolean givenByOther) {
		if (Config.DEBUG) {
			log.info("Player(" + getName() + "): addCubic(" + id + "|" + level + "|" + matk + ")");
		}
		CubicInstance cubic =
				new CubicInstance(this, id, level, (int) matk, activationtime, activationchance, maxcount, totalLifetime, givenByOther);
		
		cubics.put(id, cubic);
	}
	
	/**
	 * Remove a CubicInstance from the Player cubics.<BR><BR>
	 */
	public void delCubic(int id) {
		cubics.remove(id);
	}
	
	/**
	 * Return the CubicInstance corresponding to the Identifier of the Player cubics.<BR><BR>
	 */
	public CubicInstance getCubic(int id) {
		return cubics.get(id);
	}
	
	/**
	 * Return the modifier corresponding to the Enchant Effect of the Active Weapon (Min : 127).<BR><BR>
	 */
	public int getEnchantEffect() {
		if (getIsWeaponGlowDisabled()) {
			return 0;
		}
		
		Item wpn = getActiveWeaponInstance();
		if (wpn == null) {
			return 0;
		}

		/*if (Config.isServer(Config.TENKAI))
		{
			int effect = Math.min(12, wpn.getEnchantLevel());
			int[] effectArray = {0, 1, 2, 3, 4, 6, 9, 11, 13, 14, 15, 16, 17};
			return effectArray[effect];
		}*/
		
		sendSysMessage("Glow = " + Math.min(127, wpn.getEnchantLevel()));
		return Math.min(127, wpn.getEnchantLevel());
	}
	
	/**
	 * Set the lastFolkNpc of the Player corresponding to the last Folk wich one the player talked.<BR><BR>
	 */
	public void setLastFolkNPC(Npc folkNpc) {
		lastFolkNpc = folkNpc;
	}
	
	/**
	 * Return the lastFolkNpc of the Player corresponding to the last Folk wich one the player talked.<BR><BR>
	 */
	public Npc getLastFolkNPC() {
		return lastFolkNpc;
	}
	
	public void addAutoSoulShot(Item item) {
		int shotIndex = item.getItem().getShotTypeIndex();
		activeSoulShots[shotIndex] = item;
		disabledShoulShots[shotIndex] = false;
	}
	
	public boolean removeAutoSoulShot(Item item) {
		int shotIndex = item.getItem().getShotTypeIndex();
		if (activeSoulShots[shotIndex] == item) {
			activeSoulShots[shotIndex] = null;
			return true;
		}
		
		return false;
	}
	
	public Item getAutoSoulShot(int slot) {
		return activeSoulShots[slot];
	}
	
	public boolean hasAutoSoulShot(Item item) {
		return activeSoulShots[item.getItem().getShotTypeIndex()] == item;
	}
	
	public void rechargeAutoSoulShot(boolean physical, boolean magic, boolean summon) {
		try {
			for (Item item : activeSoulShots) {
				if (item == null || item.getItem().getShotTypeIndex() < 0) {
					continue;
				}
				
				if (item.getCount() > 0) {
					IItemHandler handler = ItemHandler.getInstance().getItemHandler(item.getEtcItem());
					if (handler != null) {
						handler.useItem(this, item, false);
					}
				} else {
					removeAutoSoulShot(item);
				}
			}
		} catch (NullPointerException npe) {
			log.warn(toString(), npe);
		}
	}
	
	/**
	 * Cancel autoshot use for shot itemId
	 *
	 * @param slot int id to disable
	 * @return true if canceled.
	 */
	public boolean disableAutoShot(int slot) {
		if (activeSoulShots[slot] != null) {
			return disableAutoShot(activeSoulShots[slot]);
		}
		
		return false;
	}
	
	/**
	 * Cancel autoshot use for shot itemId
	 *
	 * @param item item to disable
	 * @return true if canceled.
	 */
	public boolean disableAutoShot(Item item) {
		if (hasAutoSoulShot(item)) {
			int shotIndex = item.getItem().getShotTypeIndex();
			removeAutoSoulShot(item);
			sendPacket(new ExAutoSoulShot(item.getItemId(), 0, shotIndex));
			
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.AUTO_USE_OF_S1_CANCELLED);
			sm.addString(item.getItem().getName());
			sendPacket(sm);
			
			disabledShoulShots[shotIndex] = true;
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * Cancel all autoshots for player
	 */
	public void disableAutoShotsAll() {
		for (int i = 0; i < 4; i++) {
			disableAutoShot(i);
			disabledShoulShots[i] = true;
		}
	}
	
	public void checkAutoShots() {
		if (activeSoulShots[0] != null &&
				(getActiveWeaponItem() == null || getActiveWeaponItem().getItemGradePlain() != activeSoulShots[0].getItem().getItemGradePlain())) {
			sendPacket(new ExAutoSoulShot(activeSoulShots[0].getItemId(), 0, 0));
			activeSoulShots[0] = null;
		}
		
		if (activeSoulShots[1] != null &&
				(getActiveWeaponItem() == null || getActiveWeaponItem().getItemGradePlain() != activeSoulShots[1].getItem().getItemGradePlain())) {
			sendPacket(new ExAutoSoulShot(activeSoulShots[1].getItemId(), 0, 1));
			activeSoulShots[1] = null;
		}
		
		for (Item item : getInventory().getItems()) {
			int shotIndex = item.getItem().getShotTypeIndex();
			if (shotIndex < 0 || disabledShoulShots[shotIndex]) {
				continue;
			}
			
			if (shotIndex < 2 && (getActiveWeaponItem() == null || getActiveWeaponItem().getItemGradePlain() != item.getItem().getItemGradePlain())) {
				//if (shotIndex == 1)
				//	sendPacket(SystemMessage.getSystemMessage(SystemMessageId.SPIRITSHOTS_GRADE_MISMATCH));
				//else
				//	sendPacket(SystemMessage.getSystemMessage(SystemMessageId.SOULSHOTS_GRADE_MISMATCH));
				continue;
			}
			
			// Check if there are summons for the beast ss
			if (shotIndex >= 2 && getPet() == null && getSummons().isEmpty()) {
				//sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NO_SERVITOR_CANNOT_AUTOMATE_USE));
				continue;
			}
			
			addAutoSoulShot(item);
			sendPacket(new ExAutoSoulShot(item.getItemId(), 1, shotIndex));
			
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.USE_OF_S1_WILL_BE_AUTO);
			sm.addItemName(item);// Update Message by rocknow
			sendPacket(sm);
			
			rechargeAutoSoulShot(true, true, true);
		}
	}
	
	private ScheduledFuture<?> taskWarnUserTakeBreak;
	
	private class WarnUserTakeBreak implements Runnable {
		@Override
		public void run() {
			if (isOnline()) {
				SystemMessage msg = SystemMessage.getSystemMessage(SystemMessageId.PLAYING_FOR_LONG_TIME);
				Player.this.sendPacket(msg);
			} else {
				stopWarnUserTakeBreak();
			}
		}
	}
	
	private class RentPetTask implements Runnable {
		@Override
		public void run() {
			stopRentPet();
		}
	}
	
	private class WaterTask implements Runnable {
		@Override
		public void run() {
			double reduceHp = getMaxHp() / 100.0;
			
			if (reduceHp < 1) {
				reduceHp = 1;
			}
			
			reduceCurrentHp(reduceHp, Player.this, false, false, null);
			//reduced hp, becouse not rest
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.DROWN_DAMAGE_S1);
			sm.addNumber((int) reduceHp);
			sendPacket(sm);
		}
	}
	
	private class LookingForFishTask implements Runnable {
		boolean isNoob, isUpperGrade;
		int fishType, fishGutsCheck;
		long endTaskTime;
		
		protected LookingForFishTask(int fishWaitTime, int fishGutsCheck, int fishType, boolean isNoob, boolean isUpperGrade) {
			this.fishGutsCheck = fishGutsCheck;
			endTaskTime = System.currentTimeMillis() + fishWaitTime + 10000;
			this.fishType = fishType;
			this.isNoob = isNoob;
			this.isUpperGrade = isUpperGrade;
		}
		
		@Override
		public void run() {
			if (System.currentTimeMillis() >= endTaskTime) {
				endFishing(false);
				return;
			}
			if (fishType == -1) {
				return;
			}
			int check = Rnd.get(1000);
			if (fishGutsCheck > check) {
				stopLookingForFishTask();
				startFishCombat(isNoob, isUpperGrade);
			}
		}
	}
	
	public int getClanPrivileges() {
		return clanPrivileges;
	}
	
	public void setClanPrivileges(int n) {
		clanPrivileges = n;
	}
	
	// baron etc
	public void setPledgeClass(int classId) {
		pledgeClass = classId;
		checkItemRestriction();
	}
	
	public int getPledgeClass() {
		return pledgeClass;
	}
	
	public void setPledgeType(int typeId) {
		pledgeType = typeId;
	}
	
	public int getPledgeType() {
		return pledgeType;
	}
	
	public int getApprentice() {
		return apprentice;
	}
	
	public void setApprentice(int apprentice_id) {
		apprentice = apprentice_id;
	}
	
	public int getSponsor() {
		return sponsor;
	}
	
	public void setSponsor(int sponsor_id) {
		sponsor = sponsor_id;
	}
	
	public int getBookMarkSlot() {
		return bookmarkslot;
	}
	
	public void setBookMarkSlot(int slot) {
		bookmarkslot = slot;
		sendPacket(new ExGetBookMarkInfoPacket(this));
	}
	
	public L2ContactList getContactList() {
		return contactList;
	}
	
	@Override
	public void sendMessage(String message) {
		sendPacket(SystemMessage.sendString(message));
	}
	
	public final void sendSysMessage(final String message) {
		if (!isGM()) {
			return;
		}
		
		sendPacket(new CreatureSay(0, Say2.CRITICAL_ANNOUNCE, "", message));
	}
	
	public void enterObserverMode(int x, int y, int z) {
		lastX = getX();
		lastY = getY();
		lastZ = getZ();
		
		observerMode = true;
		setTarget(null);
		setIsParalyzed(true);
		startParalyze();
		setIsInvul(true);
		getAppearance().setInvisible();
		//sendPacket(new GMHide(1));
		sendPacket(new ObservationMode(x, y, z));
		getKnownList().removeAllKnownObjects(); // reinit knownlist
		setXYZ(x, y, z);
		broadcastUserInfo();
	}
	
	public void setLastCords(int x, int y, int z) {
		lastX = getX();
		lastY = getY();
		lastZ = getZ();
	}
	
	public void enterOlympiadObserverMode(Location loc, int id) {
		if (getPet() != null) {
			getPet().unSummon(this);
		}
		for (SummonInstance summon : getSummons()) {
			summon.unSummon(this);
		}
		
		if (!getCubics().isEmpty()) {
			for (CubicInstance cubic : getCubics().values()) {
				cubic.stopAction();
				cubic.cancelDisappear();
			}
			
			getCubics().clear();
		}
		
		if (getParty() != null) {
			getParty().removePartyMember(this, messageType.Expelled);
		}
		
		olympiadGameId = id;
		if (isSitting()) {
			standUp();
		}
		if (!observerMode) {
			lastX = getX();
			lastY = getY();
			lastZ = getZ();
		}
		
		observerMode = true;
		setTarget(null);
		setIsInvul(true);
		getAppearance().setInvisible();
		setInstanceId(id + Olympiad.BASE_INSTANCE_ID);
		//sendPacket(new GMHide(1));
		teleToLocation(loc, false);
		sendPacket(new ExOlympiadMode(3));
		
		broadcastUserInfo();
	}
	
	public void leaveObserverMode() {
		setTarget(null);
		getKnownList().removeAllKnownObjects(); // reinit knownlist
		setXYZ(lastX, lastY, lastZ);
		setIsParalyzed(false);
		stopParalyze(false);
		//sendPacket(new GMHide(0));
		if (!AdminCommandAccessRights.getInstance().hasAccess("admin_invis", getAccessLevel())) {
			getAppearance().setVisible();
		}
		if (!AdminCommandAccessRights.getInstance().hasAccess("admin_invul", getAccessLevel())) {
			setIsInvul(false);
		}
		if (getAI() != null) {
			getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		}
		
		setFalling(); // prevent receive falling damage
		observerMode = false;
		setLastCords(0, 0, 0);
		sendPacket(new ObservationReturn(this));
		broadcastUserInfo();
	}
	
	public void leaveOlympiadObserverMode() {
		if (olympiadGameId == -1) {
			return;
		}
		olympiadGameId = -1;
		observerMode = false;
		setTarget(null);
		sendPacket(new ExOlympiadMode(0));
		setInstanceId(0);
		teleToLocation(lastX, lastY, lastZ, true);
		//sendPacket(new GMHide(0));
		if (!AdminCommandAccessRights.getInstance().hasAccess("admin_invis", getAccessLevel())) {
			getAppearance().setVisible();
		}
		if (!AdminCommandAccessRights.getInstance().hasAccess("admin_invul", getAccessLevel())) {
			setIsInvul(false);
		}
		if (getAI() != null) {
			getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		}
		setLastCords(0, 0, 0);
		broadcastUserInfo();
	}
	
	public void setOlympiadSide(int i) {
		olympiadSide = i;
	}
	
	public int getOlympiadSide() {
		return olympiadSide;
	}
	
	public void setOlympiadGameId(int id) {
		olympiadGameId = id;
	}
	
	public int getOlympiadGameId() {
		return olympiadGameId;
	}
	
	public int getLastX() {
		return lastX;
	}
	
	public int getLastY() {
		return lastY;
	}
	
	public int getLastZ() {
		return lastZ;
	}
	
	public boolean inObserverMode() {
		return observerMode;
	}
	
	public int getTeleMode() {
		return telemode;
	}
	
	public void setTeleMode(int mode) {
		telemode = mode;
	}
	
	public void setLoto(int i, int val) {
		loto[i] = val;
	}
	
	public int getLoto(int i) {
		return loto[i];
	}
	
	public void setRace(int i, int val) {
		race[i] = val;
	}
	
	public int getRace(int i) {
		return race[i];
	}
	
	public boolean getMessageRefusal() {
		return messageRefusal;
	}
	
	public void setMessageRefusal(boolean mode) {
		messageRefusal = mode;
		sendPacket(new EtcStatusUpdate(this));
	}
	
	public void setDietMode(boolean mode) {
		dietMode = mode;
	}
	
	public boolean getDietMode() {
		return dietMode;
	}
	
	public void setTradeRefusal(boolean mode) {
		tradeRefusal = mode;
	}
	
	public boolean getTradeRefusal() {
		return tradeRefusal;
	}
	
	public void setExchangeRefusal(boolean mode) {
		exchangeRefusal = mode;
	}
	
	public boolean getExchangeRefusal() {
		return exchangeRefusal;
	}
	
	public BlockList getBlockList() {
		return blockList;
	}
	
	public void setHero(boolean hero) {
		if (hero && baseClass == activeClass) {
			broadcastPacket(new SocialAction(getObjectId(), 20016));
			for (Skill s : HeroSkillTable.getHeroSkills()) {
				addSkill(s, false); //Dont Save Hero skills to database
			}
		} else {
			for (Skill s : HeroSkillTable.getHeroSkills()) {
				super.removeSkill(s); //Just Remove skills from nonHero characters
			}
		}
		this.hero = hero;
		
		sendSkillList();
	}
	
	public void setIsInOlympiadMode(boolean b) {
		inOlympiadMode = b;
	}
	
	public void setIsOlympiadStart(boolean b) {
		OlympiadStart = b;
	}
	
	public boolean isOlympiadStart() {
		return OlympiadStart;
	}
	
	public boolean isHero() {
		return hero;
	}
	
	public void setHasCoCAura(boolean b) {
		isCoCWinner = b;
	}
	
	public boolean hasCoCAura() {
		return isCoCWinner;
	}
	
	public boolean hasHeroAura() {
		if (isPlayingEvent()) {
			return event.isType(EventType.CaptureTheFlag) && ctfFlag != null ||
					event.isType(EventType.VIP) && event.getParticipantTeam(getObjectId()) != null &&
							event.getParticipantTeam(getObjectId()).getVIP() != null &&
							event.getParticipantTeam(getObjectId()).getVIP().getObjectId() == getObjectId();
		}
		
		return isHero() || isGM() && Config.GM_HERO_AURA;
	}
	
	public boolean isInOlympiadMode() {
		return inOlympiadMode;
	}
	
	public boolean isInDuel() {
		return isInDuel;
	}
	
	public int getDuelId() {
		return duelId;
	}
	
	public void setDuelState(int mode) {
		duelState = mode;
	}
	
	public int getDuelState() {
		return duelState;
	}
	
	/**
	 * Sets up the duel state using a non 0 duelId.
	 *
	 * @param duelId 0=not in a duel
	 */
	public void setIsInDuel(int duelId) {
		if (duelId > 0) {
			isInDuel = true;
			duelState = Duel.DUELSTATE_DUELLING;
			this.duelId = duelId;
		} else {
			if (duelState == Duel.DUELSTATE_DEAD) {
				enableAllSkills();
				getStatus().startHpMpRegeneration();
			}
			isInDuel = false;
			duelState = Duel.DUELSTATE_NODUEL;
			duelId = 0;
		}
	}
	
	/**
	 * This returns a SystemMessage stating why
	 * the player is not available for duelling.
	 *
	 * @return S1_CANNOT_DUEL... message
	 */
	public SystemMessage getNoDuelReason() {
		SystemMessage sm = SystemMessage.getSystemMessage(noDuelReason);
		sm.addPcName(this);
		noDuelReason = SystemMessageId.THERE_IS_NO_OPPONENT_TO_RECEIVE_YOUR_CHALLENGE_FOR_A_DUEL;
		return sm;
	}
	
	/**
	 * Checks if this player might join / start a duel.
	 * To get the reason use getNoDuelReason() after calling this function.
	 *
	 * @return true if the player might join/start a duel.
	 */
	public boolean canDuel() {
		if (isInCombat() || getPunishLevel() == PunishLevel.JAIL) {
			noDuelReason = SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_IS_CURRENTLY_ENGAGED_IN_BATTLE;
			return false;
		}
		if (isDead() || isAlikeDead() || getCurrentHp() < getMaxHp() / 2 || getCurrentMp() < getMaxMp() / 2) {
			noDuelReason = SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_HP_OR_MP_IS_BELOW_50_PERCENT;
			return false;
		}
		if (isInDuel()) {
			noDuelReason = SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_IS_ALREADY_ENGAGED_IN_A_DUEL;
			return false;
		}
		if (isInOlympiadMode()) {
			noDuelReason = SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_IS_PARTICIPATING_IN_THE_OLYMPIAD;
			return false;
		}
		if (isCursedWeaponEquipped()) {
			noDuelReason = SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_IS_IN_A_CHAOTIC_STATE;
			return false;
		}
		if (getPrivateStoreType() != STORE_PRIVATE_NONE) {
			noDuelReason = SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_IS_CURRENTLY_ENGAGED_IN_A_PRIVATE_STORE_OR_MANUFACTURE;
			return false;
		}
		if (isMounted() || isInBoat()) {
			noDuelReason = SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_IS_CURRENTLY_RIDING_A_BOAT_WYVERN_OR_STRIDER;
			return false;
		}
		if (isFishing()) {
			noDuelReason = SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_IS_CURRENTLY_FISHING;
			return false;
		}
		if (isInsideZone(ZONE_PVP) || isInsideZone(ZONE_PEACE) || isInsideZone(ZONE_SIEGE)) {
			noDuelReason = SystemMessageId.C1_CANNOT_MAKE_A_CHALLANGE_TO_A_DUEL_BECAUSE_C1_IS_CURRENTLY_IN_A_DUEL_PROHIBITED_AREA;
			return false;
		}
		return true;
	}
	
	public boolean isNoble() {
		return noble;
	}
	
	public void setNoble(boolean val) {
		if (Config.IS_CLASSIC) {
			return;
		}
		
		// On Khadia people are noble from the very beginning
		if (Config.isServer(Config.TENKAI)) {
			val = true;
		}
		if (val) {
			for (Skill s : NobleSkillTable.getInstance().getNobleSkills()) {
				addSkill(s, false); //Dont Save Noble skills to Sql
			}
		} else {
			for (Skill s : NobleSkillTable.getInstance().getNobleSkills()) {
				super.removeSkill(s); //Just Remove skills without deleting from Sql
			}
		}
		noble = val;
		
		sendSkillList();
	}
	
	public void setLvlJoinedAcademy(int lvl) {
		lvlJoinedAcademy = lvl;
	}
	
	public int getLvlJoinedAcademy() {
		return lvlJoinedAcademy;
	}
	
	public boolean isAcademyMember() {
		return lvlJoinedAcademy > 0;
	}
	
	public void setTeam(int team) {
		this.team = team;
		if (getPet() != null) {
			getPet().broadcastStatusUpdate();
		}
		for (SummonInstance summon : getSummons()) {
			summon.broadcastStatusUpdate();
		}
	}
	
	public int getTeam() {
		return team;
	}
	
	public void setWantsPeace(int wantsPeace) {
		this.wantsPeace = wantsPeace;
	}
	
	public int getWantsPeace() {
		return wantsPeace;
	}
	
	public boolean isFishing() {
		return fishing;
	}
	
	public void setFishing(boolean fishing) {
		this.fishing = fishing;
	}
	
	public void setAllianceWithVarkaKetra(int sideAndLvlOfAlliance) {
		// [-5,-1] varka, 0 neutral, [1,5] ketra
		alliedVarkaKetra = sideAndLvlOfAlliance;
	}
	
	public int getAllianceWithVarkaKetra() {
		return alliedVarkaKetra;
	}
	
	public boolean isAlliedWithVarka() {
		return alliedVarkaKetra < 0;
	}
	
	public boolean isAlliedWithKetra() {
		return alliedVarkaKetra > 0;
	}
	
	public void sendSkillList() {
		sendSkillList(this);
	}
	
	public void sendSkillList(Player player) {
		boolean isDisabled = false;
		SkillList sl = new SkillList();
		if (player != null) {
			for (Skill s : player.getAllSkills()) {
				if (s == null) {
					continue;
				}
				if (s.getId() > 9000 && s.getId() < 9007) {
					continue; // Fake skills to change base stats
				}
				if (transformation != null && !containsAllowedTransformSkill(s.getId()) && !s.allowOnTransform()) {
					int[] specialTransformationSkillIds = {11543, 11540, 11541, 11537, 11580};
					
					for (int skillId : specialTransformationSkillIds) {
						if (getFirstEffect(skillId) == null) {
							continue;
						}
						
						isDisabled = true;
						break;
					}
					
					if (!isDisabled) {
						continue;
					}
				} else if (player.getClan() != null) {
					isDisabled = s.isClanSkill() && player.getClan().getReputationScore() < 0;
				}
				
				boolean isEnchantable = SkillTable.getInstance().isEnchantable(s.getId());
				if (isEnchantable) {
					L2EnchantSkillLearn esl = EnchantCostsTable.getInstance().getSkillEnchantmentBySkillId(s.getId());
					if (esl != null) {
						//if player dont have min level to enchant
						if (s.getLevelHash() < esl.getBaseLevel()) {
							isEnchantable = false;
						}
					}
					// if no enchant data
					else {
						isEnchantable = false;
					}
				}
				
				//sendSysMessage(s.getName() + " -- " + s.getLevel() + " -- " + s.getEnchantRouteId() + " -- " + s.getEnchantLevel());
				sl.addSkill(s.getId(), s.getLevelHash(), s.getReuseHashCode(), s.isPassive(), isDisabled, isEnchantable);
			}
		}
		
		sendPacket(sl);
		
		sendPacket(new AcquireSkillList(this));
	}
	
	/**
	 * 1. Add the specified class ID as a subclass (up to the maximum number of <b>three</b>)
	 * for this character.<BR>
	 * 2. This method no longer changes the active classIndex of the player. This is only
	 * done by the calling of setActiveClass() method as that should be the only way to do so.
	 *
	 * @return boolean subclassAdded
	 */
	public boolean addSubClass(int classId, int classIndex, int certsCount) {
		if (!subclassLock.tryLock()) {
			return false;
		}
		
		try {
			int maxSubs = Config.MAX_SUBCLASS;
			if (getRace() == Race.Ertheia) {
				maxSubs = 1;
			}
			
			if (getTotalSubClasses() == maxSubs || classIndex == 0) {
				return false;
			}
			
			if (getSubClasses().containsKey(classIndex)) {
				return false;
			}
			
			// Note: Never change classIndex in any method other than setActiveClass().
			
			SubClass newClass = new SubClass();
			newClass.setClassId(classId);
			newClass.setClassIndex(classIndex);
			newClass.setCertificates(certsCount);
			if (getRace() == Race.Ertheia) {
				newClass.setIsDual(true);
				byte level = 85;
				if (Config.STARTING_LEVEL > 85) {
					level = Config.STARTING_LEVEL;
				}
				newClass.setLevel(level);
				newClass.setExp(Experience.getAbsoluteExp(level));
			}
			
			Connection con = null;
			try {
				// Store the basic info about this new sub-class.
				con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement statement = con.prepareStatement(ADD_CHAR_SUBCLASS);
				
				statement.setInt(1, getObjectId());
				statement.setInt(2, newClass.getClassId());
				statement.setLong(3, newClass.getExp());
				statement.setLong(4, newClass.getSp());
				statement.setInt(5, newClass.getLevel());
				statement.setInt(6, newClass.getClassIndex());
				statement.setInt(7, newClass.getCertificates());
				
				statement.execute();
				statement.close();
			} catch (Exception e) {
				log.warn("WARNING: Could not add character sub class for " + getName() + ": " + e.getMessage(), e);
				return false;
			} finally {
				L2DatabaseFactory.close(con);
			}
			
			// Commit after database INSERT incase exception is thrown.
			getSubClasses().put(newClass.getClassIndex(), newClass);
			
			if (Config.DEBUG) {
				log.info(getName() + " added class ID " + classId + " as a sub class at index " + classIndex + ".");
			}
			
			Collection<L2SkillLearn> skillTree = PlayerClassTable.getInstance().getClassById(classId).getSkills().values();
			if (skillTree == null) {
				return true;
			}
			
			Map<Integer, Skill> prevSkillList = new HashMap<>();
			for (L2SkillLearn skillInfo : skillTree) {
				if (skillInfo.getMinLevel() <= 40) {
					Skill prevSkill = prevSkillList.get(skillInfo.getId());
					Skill newSkill = SkillTable.getInstance().getInfo(skillInfo.getId(), skillInfo.getLevel());
					
					if (prevSkill != null && prevSkill.getLevelHash() > newSkill.getLevelHash()) {
						continue;
					}
					
					prevSkillList.put(newSkill.getId(), newSkill);
					storeSkill(newSkill, prevSkill, classIndex);
				}
			}
			
			if (Config.DEBUG) {
				log.info(getName() + " was given " + getAllSkills().length + " skills for their new sub class.");
			}
			
			return true;
		} finally {
			subclassLock.unlock();
		}
	}
	
	/**
	 * 1. Completely erase all existance of the subClass linked to the classIndex.<BR>
	 * 2. Send over the newClassId to addSubClass()to create a new instance on this classIndex.<BR>
	 * 3. Upon Exception, revert the player to their BaseClass to avoid further problems.<BR>
	 *
	 * @return boolean subclassAdded
	 */
	public boolean modifySubClass(int classIndex, int newClassId) {
		if (!subclassLock.tryLock()) {
			return false;
		}
		
		int certsCount = 0;
		try {
			SubClass oldSub = getSubClasses().get(classIndex);
			int oldClassId = oldSub.getClassId();
			
			certsCount = oldSub.getCertificates();
			
			if (Config.DEBUG) {
				log.info(getName() + " has requested to modify sub class index " + classIndex + " from class ID " + oldClassId + " to " + newClassId +
						".");
			}
			
			Connection con = null;
			PreparedStatement statement = null;
			
			try {
				con = L2DatabaseFactory.getInstance().getConnection();
				
				// Remove all henna info stored for this sub-class.
				statement = con.prepareStatement(DELETE_CHAR_HENNAS);
				statement.setInt(1, getObjectId());
				statement.setInt(2, classIndex);
				statement.execute();
				statement.close();
				
				// Remove all shortcuts info stored for this sub-class.
				statement = con.prepareStatement(DELETE_CHAR_SHORTCUTS);
				statement.setInt(1, getObjectId());
				statement.setInt(2, classIndex);
				statement.execute();
				statement.close();
				
				// Remove all effects info stored for this sub-class.
				statement = con.prepareStatement(DELETE_SKILL_SAVE);
				statement.setInt(1, getObjectId());
				statement.setInt(2, classIndex);
				statement.execute();
				statement.close();
				
				// Remove all skill info stored for this sub-class.
				statement = con.prepareStatement(DELETE_CHAR_SKILLS);
				statement.setInt(1, getObjectId());
				statement.setInt(2, classIndex);
				statement.execute();
				statement.close();
				
				// Remove all basic info stored about this sub-class.
				statement = con.prepareStatement(DELETE_CHAR_SUBCLASS);
				statement.setInt(1, getObjectId());
				statement.setInt(2, classIndex);
				statement.execute();
				statement.close();
			} catch (Exception e) {
				log.warn("Could not modify sub class for " + getName() + " to class index " + classIndex + ": " + e.getMessage(), e);
				
				// This must be done in order to maintain data consistency.
				getSubClasses().remove(classIndex);
				return false;
			} finally {
				L2DatabaseFactory.close(con);
			}
			
			getSubClasses().remove(classIndex);
		} finally {
			subclassLock.unlock();
		}
		
		return addSubClass(newClassId, classIndex, certsCount);
	}
	
	public boolean isSubClassActive() {
		return classIndex > 0;
	}
	
	public Map<Integer, SubClass> getSubClasses() {
		if (subClasses == null) {
			subClasses = new HashMap<>();
		}
		
		return subClasses;
	}
	
	public int getTotalSubClasses() {
		return getSubClasses().size();
	}
	
	public int getBaseClass() {
		return baseClass;
	}
	
	public int getBaseClassLevel() {
		return getStat().getBaseClassLevel();
	}
	
	public int getActiveClass() {
		if (temporaryClassId != 0) {
			return temporaryClassId;
		}
		
		return activeClass;
	}
	
	public int getClassIndex() {
		return classIndex;
	}
	
	public void setClassTemplate(int classId) {
		activeClass = classId;
		
		PlayerClass cl = PlayerClassTable.getInstance().getClassById(classId);
		
		if (cl == null) {
			log.error("Missing template for classId: " + classId);
			throw new Error();
		}
		currentClass = cl;
		
		if (classIndex == 0 && cl.getRace() != null) {
			templateId = cl.getRace().ordinal() * 2 + (cl.isMage() ? 1 : 0);
		}
		
		// Set the template of the Player
		setTemplate(CharTemplateTable.getInstance().getTemplate(templateId / 2 * 2 + (cl.isMage() ? 1 : 0)));
	}
	
	public PlayerClass getCurrentClass() {
		if (temporaryPlayerClass != null) {
			return temporaryPlayerClass;
		}
		
		return currentClass;
	}
	
	/**
	 * Changes the character's class based on the given class index.
	 * <BR><BR>
	 * An index of zero specifies the character's original (base) class,
	 * while indexes 1-3 specifies the character's sub-classes respectively.
	 * <br><br>
	 * <font color="00FF00">WARNING: Use only on subclase change</font>
	 *
	 */
	public boolean setActiveClass(int classIndex) {
		if (!subclassLock.tryLock()) {
			return false;
		}
		
		try {
			// Cannot switch or change subclasses while transformed
			if (transformation != null) {
				return false;
			}
			
			// Remove active item skills before saving char to database
			// because next time when choosing this class, weared items can
			// be different
			for (Item item : getInventory().getAugmentedItems()) {
				if (item != null && item.isEquipped()) {
					item.getAugmentation().removeBonus(this);
				}
			}
			
			// abort any kind of cast.
			abortCast();
			
			// Stop casting for any player that may be casting a force buff on this l2pcinstance.
			for (Creature character : getKnownList().getKnownCharacters()) {
				if (character.getFusionSkill() != null && character.getFusionSkill().getTarget() == this) {
					character.abortCast();
				}
			}
			
			/*
			 * 1. Call store() before modifying classIndex to avoid skill effects rollover.
			 * 2. Register the correct classId against applied 'classIndex'.
			 */
			store(Config.SUBCLASS_STORE_SKILL_COOLTIME);
			reuseTimeStamps.clear();
			
			// clear charges
			charges.set(0);
			stopChargeTask();
			
			if (classIndex != 0 && getSubClasses().get(classIndex) == null) {
				log.warn("Could not switch " + getName() + "'s sub class to class index " + classIndex + ": ");
				return false;
			}
			
			this.classIndex = classIndex;
			if (classIndex == 0) {
				setClassTemplate(getBaseClass());
			} else {
				setClassTemplate(getSubClasses().get(classIndex).getClassId());
			}
			
			if (isInParty()) {
				getParty().recalculatePartyLevel();
			}
			
			/*
			 * Update the character's change in class status.
			 *
			 * 1. Remove any active cubics from the player.
			 * 2. Renovate the characters table in the database with the new class info, storing also buff/effect data.
			 * 3. Remove all existing skills.
			 * 4. Restore all the learned skills for the current class from the database.
			 * 5. Restore effect/buff data for the new class.
			 * 6. Restore henna data for the class, applying the new stat modifiers while removing existing ones.
			 * 7. Reset HP/MP/CP stats and send Server->Client character status packet to reflect changes.
			 * 8. Restore shortcut data related to this class.
			 * 9. Resend a class change animation effect to broadcast to all nearby players.
			 * 10.Unsummon any active servitor from the player.
			 */
			
			for (SummonInstance summon : getSummons()) {
				summon.unSummon(this);
			}
			
			for (Skill oldSkill : getAllSkills()) {
				super.removeSkill(oldSkill);
			}
			
			stopAllEffectsExceptThoseThatLastThroughDeath();
			stopCubics();
			
			restoreRecipeBook(false);
			
			restoreSkills();
			rewardSkills();
			regiveTemporarySkills();
			
			// Prevents some issues when changing between subclases that shares skills
			if (disabledSkills != null && !disabledSkills.isEmpty()) {
				disabledSkills.clear();
			}
			
			restoreEffects();
			updateEffectIcons();
			sendPacket(new EtcStatusUpdate(this));
			
			// if player has quest 422: Repent Your Sins, remove it
			QuestState st = getQuestState("422_RepentYourSins");
			if (st != null) {
				st.exitQuest(true);
			}
			
			for (int i = 0; i < 3; i++) {
				henna[i] = null;
			}
			
			restoreHenna();
			sendPacket(new HennaInfo(this));
			
			restoreAbilities();
			
			if (getCurrentHp() > getMaxHp()) {
				setCurrentHp(getMaxHp());
			}
			if (getCurrentMp() > getMaxMp()) {
				setCurrentMp(getMaxMp());
			}
			if (getCurrentCp() > getMaxCp()) {
				setCurrentCp(getMaxCp());
			}
			
			refreshOverloaded();
			refreshExpertisePenalty();
			broadcastUserInfo();
			
			// Clear resurrect xp calculation
			setExpBeforeDeath(0);
			
			shortCuts.restore();
			sendPacket(new ShortCutInit(this));
			
			broadcastPacket(new SocialAction(getObjectId(), SocialAction.LEVEL_UP));
			sendPacket(new SkillCoolTime(this));
			sendPacket(new ExStorageMaxCount(this));
			
			SkillTable.getInstance().getInfo(1570, 1).getEffects(this, this); // Identity crisis buff Id -> 1570
			
			setHasIdentityCrisis(true);
			
			if (classIndex != 0 && certificationSkills.size() != 0) {
				for (Skill skill : certificationSkills.values()) {
					addSkill(skill, false);
				}
			}
			
			if (getClan() != null) {
				getClan().broadcastClanStatus();
			}
			
			return true;
		} finally {
			subclassLock.unlock();
		}
	}
	
	public boolean isLocked() {
		return subclassLock.isLocked();
	}
	
	public void stopWarnUserTakeBreak() {
		if (taskWarnUserTakeBreak != null) {
			taskWarnUserTakeBreak.cancel(true);
			//ThreadPoolManager.getInstance().removeGeneral((Runnable)taskWarnUserTakeBreak);
			taskWarnUserTakeBreak = null;
		}
	}
	
	public void startWarnUserTakeBreak() {
		if (taskWarnUserTakeBreak == null) {
			taskWarnUserTakeBreak = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new WarnUserTakeBreak(), 7200000, 7200000);
		}
	}
	
	public void stopRentPet() {
		if (taskRentPet != null) {
			// if the rent of a wyvern expires while over a flying zone, tp to down before unmounting
			if (checkLandingState() && getMountType() == 2) {
				teleToLocation(MapRegionTable.TeleportWhereType.Town);
			}
			
			if (dismount()) // this should always be true now, since we teleported already
			{
				taskRentPet.cancel(true);
				taskRentPet = null;
			}
		}
	}
	
	public void startRentPet(int seconds) {
		if (taskRentPet == null) {
			taskRentPet = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new RentPetTask(), seconds * 1000L, seconds * 1000L);
		}
	}
	
	public boolean isRentedPet() {
		return taskRentPet != null;
	}
	
	public void stopWaterTask() {
		if (taskWater != null) {
			taskWater.cancel(false);
			
			taskWater = null;
			sendPacket(new SetupGauge(2, 0));
		}
	}
	
	public void startWaterTask() {
		if (!isDead() && taskWater == null) {
			int timeinwater = (int) calcStat(Stats.BREATH, 60000, this, null);
			
			sendPacket(new SetupGauge(2, timeinwater));
			taskWater = ThreadPoolManager.getInstance().scheduleEffectAtFixedRate(new WaterTask(), timeinwater, 1000);
		}
	}
	
	public boolean isInWater() {
		return taskWater != null;
	}
	
	public void checkWaterState() {
		if (isInsideZone(ZONE_WATER)) {
			startWaterTask();
		} else {
			stopWaterTask();
		}
	}
	
	public void onPlayerEnter() {
		startWarnUserTakeBreak();
		
		// jail task
		updatePunishState();
		
		if (isGM()) {
			if (isInvul()) {
				sendMessage("Entering world in Invulnerable mode.");
			}
			if (getAppearance().getInvisible()) {
				sendMessage("Entering world in Invisible mode.");
			}
			if (isSilenceMode()) {
				sendMessage("Entering world in Silence mode.");
			}
		}
		
		revalidateZone(true);
		
		notifyFriends();
		if (!isGM() && Config.DECREASE_SKILL_LEVEL) {
			checkPlayerSkills();
		}
	}
	
	public long getLastAccess() {
		return lastAccess;
	}
	
	@Override
	public void doRevive() {
		super.doRevive();
		stopEffects(EffectType.CHARMOFCOURAGE);
		updateEffectIcons();
		sendPacket(new EtcStatusUpdate(this));
		reviveRequested = false;
		revivePower = 0;
		
		if (isMounted()) {
			startFeed(mountNpcId);
		}
	}
	
	@Override
	public void doRevive(double revivePower) {
		// Restore the player's lost experience,
		// depending on the % return of the skill used (based on its power).
		restoreExp(revivePower);
		doRevive();
	}
	
	public void reviveRequest(Player reviver, Skill skill, boolean Pet) {
		if (isResurrectionBlocked()) {
			return;
		}
		
		if (reviveRequested) {
			if (revivePet == Pet) {
				reviver.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.RES_HAS_ALREADY_BEEN_PROPOSED)); // Resurrection is already been proposed.
			} else {
				if (Pet) {
					reviver.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CANNOT_RES_PET2)); // A pet cannot be resurrected while it's owner is in the process of resurrecting.
				} else {
					reviver.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.MASTER_CANNOT_RES)); // While a pet is attempting to resurrect, it cannot help in resurrecting its master.
				}
			}
			return;
		}
		if (Pet && getPet() != null && getPet().isDead() || !Pet && isDead()) {
			reviveRequested = true;
			int restoreExp = 0;
			if (isPhoenixBlessed()) {
				revivePower = 100;
			} else if (isAffected(EffectType.CHARMOFCOURAGE.getMask())) {
				revivePower = 0;
			} else {
				revivePower = Formulas.calculateSkillResurrectRestorePercent(skill.getPower(), reviver);
			}
			
			restoreExp = (int) Math.round((getExpBeforeDeath() - getExp()) * revivePower / 100);
			
			revivePet = Pet;
			
			if (isAffected(EffectType.CHARMOFCOURAGE.getMask())) {
				ConfirmDlg dlg = new ConfirmDlg(SystemMessageId.RESURRECT_USING_CHARM_OF_COURAGE.getId());
				dlg.addTime(60000);
				sendPacket(dlg);
				return;
			}
			ConfirmDlg dlg = new ConfirmDlg(SystemMessageId.RESSURECTION_REQUEST_BY_C1_FOR_S2_XP.getId());
			dlg.addPcName(reviver);
			dlg.addString(String.valueOf(restoreExp));
			sendPacket(dlg);
		}
		
		if (this instanceof ApInstance) {
			reviveAnswer(1);
		}
	}
	
	public void reviveAnswer(int answer) {
		if (!reviveRequested || !isDead() && !revivePet || revivePet && getPet() != null && !getPet().isDead()) {
			return;
		}
		//If character refuses a PhoenixBless autoress, cancel all buffs he had
		if (answer == 0 && isPhoenixBlessed()) {
			stopPhoenixBlessing(null);
			stopAllEffectsExceptThoseThatLastThroughDeath();
		}
		if (answer == 1) {
			if (!revivePet) {
				if (revivePower != 0) {
					doRevive(revivePower);
				} else {
					doRevive();
				}
			} else if (getPet() != null) {
				if (revivePower != 0) {
					getPet().doRevive(revivePower);
				} else {
					getPet().doRevive();
				}
			}
		}
		reviveRequested = false;
		revivePower = 0;
	}
	
	public boolean isReviveRequested() {
		return reviveRequested;
	}
	
	public boolean isRevivingPet() {
		return revivePet;
	}
	
	public void removeReviving() {
		reviveRequested = false;
		revivePower = 0;
	}
	
	public void onActionRequest() {
		if (isSpawnProtected()) {
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_ARE_NO_LONGER_PROTECTED_FROM_AGGRESSIVE_MONSTERS));
		}
		if (isTeleportProtected()) {
			sendMessage("Teleport spawn protection ended.");
		}
		setProtection(false);
		setTeleportProtection(false);
		if (!lastSummons.isEmpty()) {
			summonLastSummons();
		}
		hasMoved = true;
	}
	
	/**
	 * @param expertiseIndex The expertiseIndex to set.
	 */
	public void setExpertiseIndex(int expertiseIndex) {
		this.expertiseIndex = expertiseIndex;
	}
	
	/**
	 * @return Returns the expertiseIndex.
	 */
	public int getExpertiseIndex() {
		return expertiseIndex;
	}
	
	@Override
	public void teleToLocation(int x, int y, int z, int heading, boolean allowRandomOffset) {
		if (getVehicle() != null && !getVehicle().isTeleporting()) {
			setVehicle(null);
		}
		
		if (isFlyingMounted() && z < -1005) {
			z = -1005;
		}
		
		super.teleToLocation(x, y, z, heading, allowRandomOffset);
	}
	
	@Override
	public final void onTeleported() {
		super.onTeleported();
		
		if (isInAirShip()) {
			getAirShip().sendInfo(this);
		}
		
		// Force a revalidation
		revalidateZone(true);
		
		// Prevent stuck-in-bird-transformation bug
		if (isInsideZone(ZONE_PEACE) && isTransformed() && isFlyingMounted() &&
				MapRegionTable.getInstance().getClosestTownNumber(this) < 32) // Not in Gracia
		{
			unTransform(true);
		}
		
		checkItemRestriction();
		
		if (Config.PLAYER_TELEPORT_PROTECTION > 0 && !isInOlympiadMode()) {
			setTeleportProtection(true);
		}
		
		// Trained beast is after teleport lost
		if (getTrainedBeasts() != null) {
			for (TamedBeastInstance tamedBeast : getTrainedBeasts()) {
				tamedBeast.deleteMe();
			}
			getTrainedBeasts().clear();
		}
		
		// Modify the position of the pet if necessary
		Summon pet = getPet();
		if (pet != null) {
			pet.setFollowStatus(false);
			pet.teleToLocation(getPosition().getX(), getPosition().getY(), getPosition().getZ(), false);
			((SummonAI) pet.getAI()).setStartFollowController(true);
			pet.setFollowStatus(true);
			pet.updateAndBroadcastStatus(0);
		}
		for (SummonInstance summon : getSummons()) {
			summon.setFollowStatus(false);
			summon.teleToLocation(getPosition().getX(), getPosition().getY(), getPosition().getZ(), false);
			((SummonAI) summon.getAI()).setStartFollowController(true);
			summon.setFollowStatus(true);
		}
		for (SummonInstance summon : getSummons()) {
			summon.updateAndBroadcastStatus(0);
		}
		
		if (isPerformingFlyMove()) {
			setFlyMove(null);
		}
		
		onEventTeleported();
	}
	
	@Override
	public void setIsTeleporting(boolean teleport) {
		setIsTeleporting(teleport, true);
	}
	
	public void setIsTeleporting(boolean teleport, boolean useWatchDog) {
		super.setIsTeleporting(teleport);
		if (!useWatchDog) {
			return;
		}
		if (teleport) {
			if (teleportWatchdog == null && Config.TELEPORT_WATCHDOG_TIMEOUT > 0) {
				teleportWatchdog = ThreadPoolManager.getInstance().scheduleGeneral(new TeleportWatchdog(), Config.TELEPORT_WATCHDOG_TIMEOUT * 1000);
			}
		} else {
			if (teleportWatchdog != null) {
				teleportWatchdog.cancel(false);
				teleportWatchdog = null;
			}
		}
	}
	
	private class TeleportWatchdog implements Runnable {
		private final Player player;
		
		TeleportWatchdog() {
			player = Player.this;
		}
		
		@Override
		public void run() {
			if (player == null || !player.isTeleporting()) {
				return;
			}
			
			if (Config.DEBUG) {
				log.warn("Player " + player.getName() + " teleport timeout expired");
			}
			player.onTeleported();
		}
	}
	
	public void setLastServerPosition(int x, int y, int z) {
		lastServerPosition.setXYZ(x, y, z);
	}
	
	public Point3D getLastServerPosition() {
		return lastServerPosition;
	}
	
	public boolean checkLastServerPosition(int x, int y, int z) {
		return lastServerPosition.equals(x, y, z);
	}
	
	public int getLastServerDistance(int x, int y, int z) {
		double dx = x - lastServerPosition.getX();
		double dy = y - lastServerPosition.getY();
		double dz = z - lastServerPosition.getZ();
		
		return (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
	
	@Override
	public void addExpAndSp(long addToExp, long addToSp) {
		getStat().addExpAndSp(addToExp, addToSp, false);
	}
	
	public void addExpAndSp(long addToExp, long addToSp, boolean useVitality) {
		getStat().addExpAndSp(addToExp, addToSp, useVitality);
	}
	
	public void removeExpAndSp(long removeExp, long removeSp) {
		getStat().removeExpAndSp(removeExp, removeSp, true);
	}
	
	public void removeExpAndSp(long removeExp, long removeSp, boolean sendMessage) {
		getStat().removeExpAndSp(removeExp, removeSp, sendMessage);
	}
	
	@Override
	public void reduceCurrentHp(double value, Creature attacker, boolean awake, boolean isDOT, Skill skill) {
		if (skill != null) {
			getStatus().reduceHp(value, attacker, awake, isDOT, skill.isToggle(), skill.getDmgDirectlyToHP(), skill.ignoreImmunity());
		} else {
			getStatus().reduceHp(value, attacker, awake, isDOT, false, false, false);
		}
		
		// notify the tamed beast of attacks
		if (getTrainedBeasts() != null) {
			for (TamedBeastInstance tamedBeast : getTrainedBeasts()) {
				tamedBeast.onOwnerGotAttacked(attacker);
			}
		}
	}
	
	public void broadcastSnoop(int type, String name, String text) {
		if (!snoopListener.isEmpty()) {
			Snoop sn = new Snoop(getObjectId(), getName(), type, name, text);
			
			for (Player pci : snoopListener) {
				if (pci != null) {
					pci.sendPacket(sn);
				}
			}
		}
	}
	
	public void addSnooper(Player pci) {
		if (!snoopListener.contains(pci)) {
			snoopListener.add(pci);
		}
	}
	
	public void removeSnooper(Player pci) {
		snoopListener.remove(pci);
	}
	
	public void addSnooped(Player pci) {
		if (!snoopedPlayer.contains(pci)) {
			snoopedPlayer.add(pci);
		}
	}
	
	public void removeSnooped(Player pci) {
		snoopedPlayer.remove(pci);
	}
	
	/**
	 * Tenkai custom - store instance reference when GM observes land rates of this instance
	 */
	private void addLandrateObserver(Player pci) {
		// Only GM can observe land rates
		if (!pci.isGM()) {
			return;
		}
		
		if (!landrateObserver.contains(pci)) {
			landrateObserver.add(pci);
			landrateObservationActive = true;
		}
	}
	
	/**
	 * Tenkai custom - remove instance reference when GM stops observing land rates of this instance
	 */
	private void removeLandrateObserver(Player pci) {
		// Only GM can observe land rates
		if (!pci.isGM()) {
			return;
		}
		
		if (landrateObserver.contains(pci)) {
			landrateObserver.remove(pci);
			landrateObservationActive = false;
		}
	}
	
	/**
	 * Tenkai custom - store instance reference of player whose land rates are under observation
	 */
	private void addPlayerUnderLandrateObservation(Player pci) {
		if (!playersUnderLandrateObservation.contains(pci)) {
			playersUnderLandrateObservation.add(pci);
			landrateObservationActive = true;
		}
	}
	
	/**
	 * Tenkai custom - remove instance reference of player whose land rates were under observation
	 */
	private void removePlayerUnderLandrateObservation(Player pci) {
		if (playersUnderLandrateObservation.contains(pci)) {
			playersUnderLandrateObservation.remove(pci);
			landrateObservationActive = false;
		}
	}
	
	/**
	 * Tenkai custom - convenience method for observing land rates of this player instance
	 */
	public void registerLandratesObserver(Player observingGM) {
		if (!observingGM.isGM()) {
			return;
		}
		
		addLandrateObserver(observingGM);
		observingGM.addPlayerUnderLandrateObservation(this);
	}
	
	/**
	 * Tenkai custom - convenience method for ending observation of land rates of this player instance
	 */
	public void stopLandrateObservation(Player observingGM) {
		if (!observingGM.isGM()) {
			return;
		}
		
		removeLandrateObserver(observingGM);
		observingGM.removePlayerUnderLandrateObservation(this);
	}
	
	/**
	 * Tenkai custom - returns the collection of land rate observing GMs for this player instance
	 */
	public ArrayList<Player> getLandrateObservers() {
		return landrateObserver;
	}
	
	/**
	 * Tenkai custom - returns the collection of players under land rate observation by this GM
	 */
	public ArrayList<Player> getPlayersUnderLandrateObservation() {
		return playersUnderLandrateObservation;
	}
	
	/**
	 * Tenkai custom - returns true when this instance is either observing someone's land rates or is under observation
	 */
	public boolean isLandrateObservationActive() {
		return landrateObservationActive;
	}
	
	/**
	 * Tenkai custom - returns true when this GM is observing the land rates of the player instance given as argument
	 */
	public boolean isLandrateObservationActive(Player player) {
		for (Player p : playersUnderLandrateObservation) {
			if (p.getName().equals(player.getName())) {
				return true;
			}
		}
		
		return false;
	}
	
	public void addBypass(String bypass) {
		if (bypass == null) {
			return;
		}
		
		synchronized (validBypass) {
			validBypass.add(bypass);
		}
	}
	
	public void addBypass2(String bypass) {
		if (bypass == null) {
			return;
		}
		
		synchronized (validBypass2) {
			validBypass2.add(bypass);
		}
	}
	
	public boolean validateBypass(String cmd) {
		if (!Config.BYPASS_VALIDATION) {
			return true;
		}
		
		synchronized (validBypass) {
			for (String bp : validBypass) {
				if (bp == null) {
					continue;
				}
				
				if (bp.equals(cmd)) {
					return true;
				}
			}
		}
		
		synchronized (validBypass2) {
			for (String bp : validBypass2) {
				if (bp == null) {
					continue;
				}
				
				if (cmd.startsWith(bp)) {
					return true;
				}
			}
		}
		
		log.warn("[Player] player [" + getName() + "] sent invalid bypass '" + cmd + "'.");
		//Util.handleIllegalPlayerAction(this, "Player " + getName() + " sent invalid bypass '"+cmd+"'", Config.DEFAULT_PUNISH);
		return false;
	}
	
	/**
	 * Performs following tests:<br>
	 * <li> Inventory contains item
	 * <li> Item owner id == this.owner id
	 * <li> It isnt pet control item while mounting pet or pet summoned
	 * <li> It isnt active enchant item
	 * <li> It isnt cursed weapon/item
	 * <li> It isnt wear item
	 * <br>
	 *
	 * @param objectId: item object id
	 * @param action:   just for login porpouse
	 */
	public boolean validateItemManipulation(int objectId, String action) {
		Item item = getInventory().getItemByObjectId(objectId);
		
		if (item == null || item.getOwnerId() != getObjectId()) {
			log.trace(getObjectId() + ": player tried to " + action + " item he is not owner of");
			return false;
		}
		
		// Pet is summoned and not the item that summoned the pet AND not the buggle from strider you're mounting
		if (getPet() != null && getPet().getControlObjectId() == objectId || getMountObjectID() == objectId) {
			if (Config.DEBUG) {
				log.trace(getObjectId() + ": player tried to " + action + " item controling pet");
			}
			
			return false;
		}
		
		if (getActiveEnchantItem() != null && getActiveEnchantItem().getObjectId() == objectId) {
			if (Config.DEBUG) {
				log.trace(getObjectId() + ":player tried to " + action + " an enchant scroll he was using");
			}
			
			return false;
		}
		
		return !CursedWeaponsManager.getInstance().isCursed(item.getItemId());
	}
	
	public void clearBypass() {
		synchronized (validBypass) {
			validBypass.clear();
		}
		synchronized (validBypass2) {
			validBypass2.clear();
		}
	}
	
	/**
	 * @return Returns the inBoat.
	 */
	public boolean isInBoat() {
		return vehicle != null && vehicle.isBoat();
	}
	
	public BoatInstance getBoat() {
		return (BoatInstance) vehicle;
	}
	
	/**
	 * @return Returns the inAirShip.
	 */
	public boolean isInAirShip() {
		return vehicle != null && vehicle.isAirShip();
	}
	
	public AirShipInstance getAirShip() {
		return (AirShipInstance) vehicle;
	}
	
	public boolean isInShuttle() {
		return vehicle != null && vehicle.isShuttle();
	}
	
	private long lastGotOnOffShuttle = 0;
	
	public void gotOnOffShuttle() {
		lastGotOnOffShuttle = System.currentTimeMillis();
	}
	
	public boolean canGetOnOffShuttle() {
		return System.currentTimeMillis() - lastGotOnOffShuttle > 1000L;
	}
	
	public ShuttleInstance getShuttle() {
		return (ShuttleInstance) vehicle;
	}
	
	public Vehicle getVehicle() {
		return vehicle;
	}
	
	public void setVehicle(Vehicle v) {
		if (v == null && vehicle != null) {
			vehicle.removePassenger(this);
		}
		
		vehicle = v;
	}
	
	public void setInCrystallize(boolean inCrystallize) {
		this.inCrystallize = inCrystallize;
	}
	
	public boolean isInCrystallize() {
		return inCrystallize;
	}
	
	public Point3D getInVehiclePosition() {
		return inVehiclePosition;
	}
	
	public void setInVehiclePosition(Point3D pt) {
		inVehiclePosition = pt;
	}
	
	/**
	 * Manage the delete task of a Player (Leave Party, Unsummon pet, Save its inventory in the database, Remove it from the world...).<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>If the Player is in observer mode, set its position to its position before entering in observer mode </li>
	 * <li>Set the online Flag to True or False and update the characters table of the database with online status and lastAccess </li>
	 * <li>Stop the HP/MP/CP Regeneration task </li>
	 * <li>Cancel Crafting, Attak or Cast </li>
	 * <li>Remove the Player from the world </li>
	 * <li>Stop Party and Unsummon Pet </li>
	 * <li>Update database with items in its inventory and remove them from the world </li>
	 * <li>Remove all WorldObject from knownObjects and knownPlayer of the Creature then cancel Attak or Cast and notify AI </li>
	 * <li>Close the connection with the client </li><BR><BR>
	 */
	@Override
	public void deleteMe() {
		cleanup();
		store();
		super.deleteMe();
	}
	
	private synchronized void cleanup() {
		// Set the online Flag to True or False and update the characters table of the database with online status and lastAccess (called when login and logout)
		try {
			if (!isOnline()) {
				log.error("deleteMe() called on offline character " + this, new RuntimeException());
			}
			setOnlineStatus(false, true);
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		
		// Remove the player form the events
		try {
			EventsManager.getInstance().onLogout(this);
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		
		try {
			if (getFriendList().size() > 0) {
				for (int i : getFriendList()) {
					Player friend = World.getInstance().getPlayer(i);
					if (friend != null) {
						friend.sendPacket(new FriendPacket(true, getObjectId(), friend));
						friend.sendPacket(new FriendList(friend));
					}
				}
			}
			
			SystemMessage sm;
			if (isMentee() && !canBeMentor()) {
				for (Abnormal e : getAllEffects()) {
					if (e.getSkill().getId() >= 9227 && e.getSkill().getId() <= 9233) {
						e.exit();
					}
				}
				
				Player mentor = World.getInstance().getPlayer(getMentorId());
				if (mentor != null && mentor.isOnline()) {
					mentor.sendPacket(new ExMentorList(mentor));
					sm = SystemMessage.getSystemMessage(SystemMessageId.YOUR_MENTEE_S1_HAS_DISCONNECTED);
					sm.addCharName(this);
					mentor.sendPacket(sm);
					mentor.giveMentorBuff();
				}
			} else if (isMentor()) {
				for (int objId : getMenteeList()) {
					Player mentee = World.getInstance().getPlayer(objId);
					if (mentee != null && mentee.isOnline()) {
						sm = SystemMessage.getSystemMessage(SystemMessageId.YOUR_MENTOR_S1_HAS_DISCONNECTED);
						sm.addCharName(this);
						mentee.sendPacket(sm);
						mentee.sendPacket(new ExMentorList(mentee));
						for (Abnormal e : mentee.getAllEffects()) {
							if (e.getSkill().getId() >= 9227 && e.getSkill().getId() <= 9233) {
								e.exit();
							}
						}
					}
				}
				
				for (Abnormal e : getAllEffects()) {
					if (e.getSkill().getId() == 9256) {
						e.exit();
					}
				}
			}
			PartySearchManager psm = PartySearchManager.getInstance();
			if (psm.getWannaToChangeThisPlayer(getLevel(), getClassId()) != null) {
				psm.removeChangeThisPlayer(this);
			}
			if (psm.getLookingForParty(getLevel(), getClassId()) != null) {
				psm.removeLookingForParty(this);
			}
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		try {
			if (Config.ENABLE_BLOCK_CHECKER_EVENT && getBlockCheckerArena() != -1) {
				HandysBlockCheckerManager.getInstance().onDisconnect(this);
			}
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		
		try {
			isOnline = false;
			abortAttack();
			abortCast();
			stopMove(null);
			setDebug(null);
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		
		//remove combat flag
		try {
			if (getInventory().getItemByItemId(9819) != null) {
				Fort fort = FortManager.getInstance().getFort(this);
				if (fort != null) {
					FortSiegeManager.getInstance().dropCombatFlag(this, fort.getFortId());
				} else {
					int slot = getInventory().getSlotFromItem(getInventory().getItemByItemId(9819));
					getInventory().unEquipItemInBodySlot(slot);
					destroyItem("CombatFlag", getInventory().getItemByItemId(9819), null, true);
				}
			}
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		
		try {
			if (getPet() != null) {
				getPet().storeEffects();
			}
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		
		try {
			// Block Checker Event is enabled and player is still playing a match
			if (handysBlockCheckerEventArena != -1) {
				int team = HandysBlockCheckerManager.getInstance().getHolder(handysBlockCheckerEventArena).getPlayerTeam(this);
				HandysBlockCheckerManager.getInstance().removePlayer(this, handysBlockCheckerEventArena, team);
				if (getTeam() > 0) {
					// Remove transformation
					Abnormal transform;
					if ((transform = getFirstEffect(6035)) != null) {
						transform.exit();
					} else if ((transform = getFirstEffect(6036)) != null) {
						transform.exit();
					}
					// Remove team aura
					setTeam(0);
					
					// Remove the event items
					PcInventory inv = getInventory();
					
					if (inv.getItemByItemId(13787) != null) {
						long count = inv.getInventoryItemCount(13787, 0);
						inv.destroyItemByItemId("Handys Block Checker", 13787, count, this, this);
					}
					if (inv.getItemByItemId(13788) != null) {
						long count = inv.getInventoryItemCount(13788, 0);
						inv.destroyItemByItemId("Handys Block Checker", 13788, count, this, this);
					}
					setInsideZone(Creature.ZONE_PVP, false);
					// Teleport Back
					teleToLocation(-57478, -60367, -2370);
				}
			}
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		
		try {
			PartyMatchWaitingList.getInstance().removePlayer(this);
			if (partyroom != 0) {
				PartyMatchRoom room = PartyMatchRoomList.getInstance().getRoom(partyroom);
				if (room != null) {
					room.deleteMember(this);
				}
			}
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		
		try {
			if (isFlying()) {
				removeSkill(SkillTable.getInstance().getInfo(4289, 1));
			}
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		
		// Recommendations must be saved before task (timer) is canceled
		try {
			storeRecommendations();
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		
		// Stop the HP/MP/CP Regeneration task (scheduled tasks)
		try {
			stopAllTimers();
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		
		try {
			setIsTeleporting(false);
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		
		// Stop crafting, if in progress
		try {
			RecipeController.getInstance().requestMakeItemAbort(this);
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		
		// Cancel Attack or Cast
		try {
			setTarget(null);
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		
		try {
			if (fusionSkill != null || continuousDebuffTargets != null) {
				abortCast();
			}
			
			for (Creature character : getKnownList().getKnownCharacters()) {
				if (character.getFusionSkill() != null && character.getFusionSkill().getTarget() == this) {
					character.abortCast();
				}
			}
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		
		try {
			for (Abnormal effect : getAllEffects()) {
				if (effect.getSkill().isToggle() && !(effect.getSkill().getId() >= 11007 && effect.getSkill().getId() <= 11010)) {
					effect.exit();
					continue;
				}
				
				switch (effect.getType()) {
					case SIGNET_GROUND:
					case SIGNET_EFFECT:
						effect.exit();
						break;
				}
			}
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		
		// Remove from world regions zones
		WorldRegion oldRegion = getWorldRegion();
		
		// Remove the Player from the world
		try {
			decayMe();
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		
		if (oldRegion != null) {
			oldRegion.removeFromZones(this);
		}
		
		// If a Party is in progress, leave it (and festival party)
		if (isInParty()) {
			try {
				leaveParty();
			} catch (Exception e) {
				log.error("deleteMe()", e);
			}
		}
		
		if (OlympiadManager.getInstance().isRegistered(this) || getOlympiadGameId() != -1) // handle removal from olympiad game
		{
			OlympiadManager.getInstance().removeDisconnectedCompetitor(this);
		}
		
		// If the Player has Pet, unsummon it
		PetInstance pet = getPet();
		if (pet != null) {
			try {
				Item controlPetItem = pet.getControlItem();
				if (controlPetItem != null) {
					lastSummons.add(controlPetItem.getItemId());
				}
				
				pet.unSummon(this);
				pet.broadcastNpcInfo(0);
			} catch (Exception e) {
				log.error("deleteMe()", e);
			}// returns pet to control item
		}
		
		for (SummonInstance summon : getSummons()) {
			if (summon == null) {
				continue;
			}
			try {
				lastSummons.add(summon.getSummonSkillId());
				summon.unSummon(this);
			} catch (Exception e) {
				log.error("deleteMe()", e);
			}// returns pet to control item
		}
		
		if (getClan() != null) {
			// set the status for pledge member list to OFFLINE
			try {
				L2ClanMember clanMember = getClan().getClanMember(getObjectId());
				if (clanMember != null) {
					clanMember.setPlayerInstance(null);
				}
				
				if (isClanLeader()) {
					for (L2ClanMember member : getClan().getMembers()) {
						if (member.getPlayerInstance() == null) {
							continue;
						}
						
						Abnormal eff = member.getPlayerInstance().getFirstEffect(19009);
						if (eff != null) {
							eff.exit();
						}
					}
				}
			} catch (Exception e) {
				log.error("deleteMe()", e);
			}
		}
		
		if (getActiveRequester() != null) {
			// deals with sudden exit in the middle of transaction
			setActiveRequester(null);
			cancelActiveTrade();
		}
		
		// Stop possible land rate observations
		if (isLandrateObservationActive()) {
			if (!isGM()) {
				for (Player gm : landrateObserver) {
					gm.sendMessage(getName() + " logged out. This ends your land rate observation.");
					stopLandrateObservation(gm);
				}
			} else {
				for (Player obsP : playersUnderLandrateObservation) {
					obsP.stopLandrateObservation(this);
				}
			}
		}
		
		// If the Player is a GM, remove it from the GM List
		if (isGM()) {
			try {
				GmListTable.getInstance().deleteGm(this);
			} catch (Exception e) {
				log.error("deleteMe()", e);
			}
		}
		
		try {
			// Check if the Player is in observer mode to set its position to its position
			// before entering in observer mode
			if (inObserverMode()) {
				setXYZInvisible(lastX, lastY, lastZ);
			}
			
			if (getVehicle() != null) {
				getVehicle().oustPlayer(this);
			}
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		
		// remove player from instance and set spawn location if any
		try {
			final int instanceId = getInstanceId();
			if (instanceId != 0 && !Config.RESTORE_PLAYER_INSTANCE) {
				final Instance inst = InstanceManager.getInstance().getInstance(instanceId);
				if (inst != null) {
					inst.removePlayer(getObjectId());
					final int[] spawn = inst.getSpawnLoc();
					if (spawn[0] != 0 && spawn[1] != 0 && spawn[2] != 0) {
						final int x = spawn[0] + Rnd.get(-30, 30);
						final int y = spawn[1] + Rnd.get(-30, 30);
						setXYZInvisible(x, y, spawn[2]);
						if (getPet() != null) // dead pet
						{
							getPet().teleToLocation(x, y, spawn[2]);
							getPet().setInstanceId(0);
						}
					}
				}
			}
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		
		// Update database with items in its inventory and remove them from the world
		try {
			getInventory().deleteMe();
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		try {
			if (getPetInv() != null) {
				getPetInv().deleteMe();
			}
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		
		// Update database with items in its warehouse and remove them from the world
		try {
			clearWarehouse();
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		if (Config.WAREHOUSE_CACHE) {
			WarehouseCacheManager.getInstance().remCacheTask(this);
		}
		
		try {
			clearRefund();
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		
		if (isCursedWeaponEquipped()) {
			try {
				CursedWeaponsManager.getInstance().getCursedWeapon(cursedWeaponEquippedId).setPlayer(null);
			} catch (Exception e) {
				log.error("deleteMe()", e);
			}
		}
		
		// Remove all WorldObject from knownObjects and knownPlayer of the Creature then cancel Attak or Cast and notify AI
		try {
			getKnownList().removeAllKnownObjects();
		} catch (Exception e) {
			log.error("deleteMe()", e);
		}
		
		if (getClanId() > 0) {
			getClan().broadcastToOtherOnlineMembers(new PledgeShowMemberListUpdate(this), this);
			getClan().broadcastToOnlineMembers(new ExPledgeCount(getClan().getOnlineMembersCount()));
		}
		//ClanTable.getInstance().getClan(getClanId()).broadcastToOnlineMembers(new PledgeShowMemberListAdd(this));
		
		for (Player player : snoopedPlayer) {
			player.removeSnooper(this);
		}
		
		for (Player player : snoopListener) {
			player.removeSnooped(this);
		}
		
		// Remove WorldObject object from allObjects of World
		World.getInstance().removeObject(this);
		World.getInstance().removeFromAllPlayers(this); // force remove in case of crash during teleport
		
		InstanceManager.getInstance().destroyInstance(getObjectId());
		
		try {
			notifyFriends();
			getBlockList().playerLogout();
		} catch (Exception e) {
			log.warn("Exception on deleteMe() notifyFriends: " + e.getMessage(), e);
		}
	}
	
	private FishData fish;
	
	/*  startFishing() was stripped of any pre-fishing related checks, namely the fishing zone check.
	 * Also worthy of note is the fact the code to find the hook landing position was also striped. The
	 * stripped code was moved into fishing.java. In my opinion it makes more sense for it to be there
	 * since all other skill related checks were also there. Last but not least, moving the zone check
	 * there, fixed a bug where baits would always be consumed no matter if fishing actualy took place.
	 * startFishing() now takes up 3 arguments, wich are acurately described as being the hook landing
	 * coordinates.
	 */
	public void startFishing(int x, int y, int z) {
		stopMove(null);
		setIsImmobilized(true);
		fishing = true;
		fishx = x;
		fishy = y;
		fishz = z;
		//broadcastUserInfo();
		//Starts fishing
		int lvl = GetRandomFishLvl();
		int group = GetRandomGroup();
		int type = GetRandomFishType(group);
		List<FishData> fishs = FishTable.getInstance().getfish(lvl, type, group);
		if (fishs == null || fishs.isEmpty()) {
			sendMessage("Error - Fishes are not definied");
			endFishing(false);
			return;
		}
		int check = Rnd.get(fishs.size());
		// Use a copy constructor else the fish data may be over-written below
		fish = new FishData(fishs.get(check));
		fishs.clear();
		fishs = null;
		sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CAST_LINE_AND_START_FISHING));
		if (!TimeController.getInstance().isNowNight() && lure.isNightLure()) {
			fish.setType(-1);
		}
		//sendMessage("Hook x,y: " + x + "," + y + " - Water Z, Player Z:" + z + ", " + getZ()); //debug line, uncoment to show coordinates used in fishing.
		broadcastPacket(new ExFishingStart(this, fish.getType(), x, y, z, lure.isNightLure()));
		sendPacket(new PlaySound(1, "SF_P_01", 0, 0, 0, 0, 0));
		startLookingForFishTask();
	}
	
	public void stopLookingForFishTask() {
		if (taskforfish != null) {
			taskforfish.cancel(false);
			taskforfish = null;
		}
	}
	
	public void startLookingForFishTask() {
		if (!isDead() && taskforfish == null) {
			int checkDelay = 0;
			boolean isNoob = false;
			boolean isUpperGrade = false;
			
			if (lure != null) {
				int lureid = lure.getItemId();
				isNoob = fish.getGroup() == 0;
				isUpperGrade = fish.getGroup() == 2;
				if (lureid == 6519 || lureid == 6522 || lureid == 6525 || lureid == 8505 || lureid == 8508 || lureid == 8511) //low grade
				{
					checkDelay = Math.round((float) (fish.getGutsCheckTime() * 1.33));
				} else if (lureid == 6520 || lureid == 6523 || lureid == 6526 || lureid >= 8505 && lureid <= 8513 ||
						lureid >= 7610 && lureid <= 7613 || lureid >= 7807 && lureid <= 7809 ||
						lureid >= 8484 && lureid <= 8486) //medium grade, beginner, prize-winning & quest special bait
				{
					checkDelay = Math.round((float) (fish.getGutsCheckTime() * 1.00));
				} else if (lureid == 6521 || lureid == 6524 || lureid == 6527 || lureid == 8507 || lureid == 8510 || lureid == 8513) //high grade
				{
					checkDelay = Math.round((float) (fish.getGutsCheckTime() * 0.66));
				}
			}
			taskforfish = ThreadPoolManager.getInstance()
					.scheduleEffectAtFixedRate(new LookingForFishTask(fish.getWaitTime(), fish.getFishGuts(), fish.getType(), isNoob, isUpperGrade),
							10000,
							checkDelay);
		}
	}
	
	private int GetRandomGroup() {
		switch (lure.getItemId()) {
			case 7807: //green for beginners
			case 7808: //purple for beginners
			case 7809: //yellow for beginners
			case 8486: //prize-winning for beginners
				return 0;
			case 8485: //prize-winning luminous
			case 8506: //green luminous
			case 8509: //purple luminous
			case 8512: //yellow luminous
				return 2;
			default:
				return 1;
		}
	}
	
	private int GetRandomFishType(int group) {
		int check = Rnd.get(100);
		int type = 1;
		switch (group) {
			case 0: //fish for novices
				switch (lure.getItemId()) {
					case 7807: //green lure, preferred by fast-moving (nimble) fish (type 5)
						if (check <= 54) {
							type = 5;
						} else if (check <= 77) {
							type = 4;
						} else {
							type = 6;
						}
						break;
					case 7808: //purple lure, preferred by fat fish (type 4)
						if (check <= 54) {
							type = 4;
						} else if (check <= 77) {
							type = 6;
						} else {
							type = 5;
						}
						break;
					case 7809: //yellow lure, preferred by ugly fish (type 6)
						if (check <= 54) {
							type = 6;
						} else if (check <= 77) {
							type = 5;
						} else {
							type = 4;
						}
						break;
					case 8486: //prize-winning fishing lure for beginners
						if (check <= 33) {
							type = 4;
						} else if (check <= 66) {
							type = 5;
						} else {
							type = 6;
						}
						break;
				}
				break;
			case 1: //normal fish
				switch (lure.getItemId()) {
					case 7610:
					case 7611:
					case 7612:
					case 7613:
						type = 3;
						break;
					case 6519: //all theese lures (green) are prefered by fast-moving (nimble) fish (type 1)
					case 8505:
					case 6520:
					case 6521:
					case 8507:
						if (check <= 54) {
							type = 1;
						} else if (check <= 74) {
							type = 0;
						} else if (check <= 94) {
							type = 2;
						} else {
							type = 3;
						}
						break;
					case 6522: //all theese lures (purple) are prefered by fat fish (type 0)
					case 8508:
					case 6523:
					case 6524:
					case 8510:
						if (check <= 54) {
							type = 0;
						} else if (check <= 74) {
							type = 1;
						} else if (check <= 94) {
							type = 2;
						} else {
							type = 3;
						}
						break;
					case 6525: //all theese lures (yellow) are prefered by ugly fish (type 2)
					case 8511:
					case 6526:
					case 6527:
					case 8513:
						if (check <= 55) {
							type = 2;
						} else if (check <= 74) {
							type = 1;
						} else if (check <= 94) {
							type = 0;
						} else {
							type = 3;
						}
						break;
					case 8484: //prize-winning fishing lure
						if (check <= 33) {
							type = 0;
						} else if (check <= 66) {
							type = 1;
						} else {
							type = 2;
						}
						break;
				}
				break;
			case 2: //upper grade fish, luminous lure
				switch (lure.getItemId()) {
					case 8506: //green lure, preferred by fast-moving (nimble) fish (type 8)
						if (check <= 54) {
							type = 8;
						} else if (check <= 77) {
							type = 7;
						} else {
							type = 9;
						}
						break;
					case 8509: //purple lure, preferred by fat fish (type 7)
						if (check <= 54) {
							type = 7;
						} else if (check <= 77) {
							type = 9;
						} else {
							type = 8;
						}
						break;
					case 8512: //yellow lure, preferred by ugly fish (type 9)
						if (check <= 54) {
							type = 9;
						} else if (check <= 77) {
							type = 8;
						} else {
							type = 7;
						}
						break;
					case 8485: //prize-winning fishing lure
						if (check <= 33) {
							type = 7;
						} else if (check <= 66) {
							type = 8;
						} else {
							type = 9;
						}
						break;
				}
		}
		return type;
	}
	
	private int GetRandomFishLvl() {
		int skilllvl = getSkillLevelHash(1315);
		final Abnormal e = getFirstEffect(2274);
		if (e != null) {
			skilllvl = (int) e.getSkill().getPower();
		}
		if (skilllvl <= 0) {
			return 1;
		}
		int randomlvl;
		int check = Rnd.get(100);
		
		if (check <= 50) {
			randomlvl = skilllvl;
		} else if (check <= 85) {
			randomlvl = skilllvl - 1;
			if (randomlvl <= 0) {
				randomlvl = 1;
			}
		} else {
			randomlvl = skilllvl + 1;
			if (randomlvl > 27) {
				randomlvl = 27;
			}
		}
		
		return randomlvl;
	}
	
	public void startFishCombat(boolean isNoob, boolean isUpperGrade) {
		fishCombat = new L2Fishing(this, fish, isNoob, isUpperGrade);
	}
	
	public void endFishing(boolean win) {
		fishing = false;
		fishx = 0;
		fishy = 0;
		fishz = 0;
		//broadcastUserInfo();
		if (fishCombat == null) {
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.BAIT_LOST_FISH_GOT_AWAY));
		}
		fishCombat = null;
		lure = null;
		//Ends fishing
		broadcastPacket(new ExFishingEnd(win, this));
		sendPacket(SystemMessage.getSystemMessage(SystemMessageId.REEL_LINE_AND_STOP_FISHING));
		setIsImmobilized(false);
		stopLookingForFishTask();
	}
	
	public L2Fishing getFishCombat() {
		return fishCombat;
	}
	
	public int getFishx() {
		return fishx;
	}
	
	public int getFishy() {
		return fishy;
	}
	
	public int getFishz() {
		return fishz;
	}
	
	public void setLure(Item lure) {
		this.lure = lure;
	}
	
	public Item getLure() {
		return lure;
	}
	
	public int getInventoryLimit() {
		int ivlim;
		
		if (isGM()) {
			ivlim = Config.INVENTORY_MAXIMUM_GM;
		} else if (getRace() == Race.Dwarf) {
			ivlim = Config.INVENTORY_MAXIMUM_DWARF;
		} else {
			ivlim = Config.INVENTORY_MAXIMUM_NO_DWARF;
		}
		
		ivlim += (int) getStat().calcStat(Stats.INVENTORY_LIMIT, 0, null, null);
		return ivlim;
	}
	
	public int getWareHouseLimit() {
		int whlim;
		if (getRace() == Race.Dwarf) {
			whlim = Config.WAREHOUSE_SLOTS_DWARF;
		} else {
			whlim = Config.WAREHOUSE_SLOTS_NO_DWARF;
		}
		
		whlim += (int) getStat().calcStat(Stats.WAREHOUSE_LIMIT, 0, null, null);
		
		return whlim;
	}
	
	public int getPrivateSellStoreLimit() {
		int pslim;
		
		if (getRace() == Race.Dwarf) {
			pslim = Config.MAX_PVTSTORESELL_SLOTS_DWARF;
		} else {
			pslim = Config.MAX_PVTSTORESELL_SLOTS_OTHER;
		}
		
		pslim += (int) getStat().calcStat(Stats.P_SELL_LIMIT, 0, null, null);
		
		return pslim;
	}
	
	public int getPrivateBuyStoreLimit() {
		int pblim;
		
		if (getRace() == Race.Dwarf) {
			pblim = Config.MAX_PVTSTOREBUY_SLOTS_DWARF;
		} else {
			pblim = Config.MAX_PVTSTOREBUY_SLOTS_OTHER;
		}
		pblim += (int) getStat().calcStat(Stats.P_BUY_LIMIT, 0, null, null);
		
		return pblim;
	}
	
	public int getDwarfRecipeLimit() {
		int recdlim = Config.DWARF_RECIPE_LIMIT;
		recdlim += (int) getStat().calcStat(Stats.REC_D_LIMIT, 0, null, null);
		return recdlim;
	}
	
	public int getCommonRecipeLimit() {
		int recclim = Config.COMMON_RECIPE_LIMIT;
		recclim += (int) getStat().calcStat(Stats.REC_C_LIMIT, 0, null, null);
		return recclim;
	}
	
	/**
	 * @return Returns the mountNpcId.
	 */
	public int getMountNpcId() {
		return mountNpcId;
	}
	
	/**
	 * @return Returns the mountLevel.
	 */
	public int getMountLevel() {
		return mountLevel;
	}
	
	public void setMountObjectID(int newID) {
		mountObjectID = newID;
	}
	
	public int getMountObjectID() {
		return mountObjectID;
	}
	
	private Item lure = null;
	public int shortBuffTaskSkillId = 0;
	
	/**
	 * Get the current skill in use or return null.<BR><BR>
	 */
	public SkillDat getCurrentSkill() {
		return currentSkill;
	}
	
	/**
	 * Create a new SkillDat object and set the player currentSkill.<BR><BR>
	 */
	public void setCurrentSkill(Skill currentSkill, boolean ctrlPressed, boolean shiftPressed) {
		if (currentSkill == null) {
			if (Config.DEBUG) {
				log.info("Setting current skill: NULL for " + getName() + ".");
			}
			
			currentSkill = null;
			return;
		}
		
		if (Config.DEBUG) {
			log.info("Setting current skill: " + currentSkill.getName() + " (ID: " + currentSkill.getId() + ") for " + getName() + ".");
		}
		
		this.currentSkill = new SkillDat(currentSkill, ctrlPressed, shiftPressed);
	}
	
	/**
	 * Get the current pet skill in use or return null.<br><br>
	 */
	public SkillDat getCurrentPetSkill() {
		return currentPetSkill;
	}
	
	/**
	 * Create a new SkillDat object and set the player currentPetSkill.<br><br>
	 */
	public void setCurrentPetSkill(Skill currentSkill, boolean ctrlPressed, boolean shiftPressed) {
		if (currentSkill == null) {
			if (Config.DEBUG) {
				log.info("Setting current pet skill: NULL for " + getName() + ".");
			}
			
			currentPetSkill = null;
			return;
		}
		
		if (Config.DEBUG) {
			log.info("Setting current Pet skill: " + currentSkill.getName() + " (ID: " + currentSkill.getId() + ") for " + getName() + ".");
		}
		
		currentPetSkill = new SkillDat(currentSkill, ctrlPressed, shiftPressed);
	}
	
	public SkillDat getQueuedSkill() {
		return queuedSkill;
	}
	
	/**
	 * Create a new SkillDat object and queue it in the player queuedSkill.<BR><BR>
	 */
	public void setQueuedSkill(Skill queuedSkill, boolean ctrlPressed, boolean shiftPressed) {
		if (queuedSkill == null) {
			if (Config.DEBUG) {
				log.info("Setting queued skill: NULL for " + getName() + ".");
			}
			
			queuedSkill = null;
			return;
		}
		
		if (Config.DEBUG) {
			log.info("Setting queued skill: " + queuedSkill.getName() + " (ID: " + queuedSkill.getId() + ") for " + getName() + ".");
		}
		
		this.queuedSkill = new SkillDat(queuedSkill, ctrlPressed, shiftPressed);
	}
	
	/**
	 * returns punishment level of player
	 *
	 */
	public PunishLevel getPunishLevel() {
		return punishLevel;
	}
	
	/**
	 * @return True if player is jailed
	 */
	public boolean isInJail() {
		return punishLevel == PunishLevel.JAIL;
	}
	
	/**
	 * @return True if player is chat banned
	 */
	public boolean isChatBanned() {
		return punishLevel == PunishLevel.CHAT;
	}
	
	public void setPunishLevel(int state) {
		switch (state) {
			case 0: {
				punishLevel = PunishLevel.NONE;
				break;
			}
			case 1: {
				punishLevel = PunishLevel.CHAT;
				break;
			}
			case 2: {
				punishLevel = PunishLevel.JAIL;
				break;
			}
			case 3: {
				punishLevel = PunishLevel.CHAR;
				break;
			}
			case 4: {
				punishLevel = PunishLevel.ACC;
				break;
			}
		}
	}
	
	/**
	 * Sets punish level for player based on delay
	 *
	 * @param delayInMinutes 0 - Indefinite
	 */
	public void setPunishLevel(PunishLevel state, int delayInMinutes) {
		long delayInMilliseconds = delayInMinutes * 60000L;
		switch (state) {
			case NONE: // Remove Punishments
			{
				switch (punishLevel) {
					case CHAT: {
						punishLevel = state;
						stopPunishTask(true);
						sendPacket(new EtcStatusUpdate(this));
						sendMessage("Your Chat ban has been lifted");
						break;
					}
					case JAIL: {
						punishLevel = state;
						// Open a Html message to inform the player
						NpcHtmlMessage htmlMsg = new NpcHtmlMessage(0);
						String jailInfos = HtmCache.getInstance().getHtm(getHtmlPrefix(), "jail_out.htm");
						if (jailInfos != null) {
							htmlMsg.setHtml(jailInfos);
						} else {
							htmlMsg.setHtml("<html><body>You are free for now, respect server Rule!</body></html>");
						}
						sendPacket(htmlMsg);
						stopPunishTask(true);
						teleToLocation(17836, 170178, -3507, true); // Floran
						break;
					}
				}
				break;
			}
			case CHAT: // Chat Ban
			{
				// not allow player to escape jail using chat ban
				if (punishLevel == PunishLevel.JAIL) {
					break;
				}
				punishLevel = state;
				punishTimer = 0;
				sendPacket(new EtcStatusUpdate(this));
				// Remove the task if any
				stopPunishTask(false);
				
				if (delayInMinutes > 0) {
					punishTimer = delayInMilliseconds;
					
					// start the countdown
					punishTask = ThreadPoolManager.getInstance().scheduleGeneral(new PunishTask(), punishTimer);
					sendMessage("You are chat banned for " + delayInMinutes + " minutes.");
				} else {
					sendMessage("You have been chat banned");
				}
				break;
			}
			case JAIL: // Jail Player
			{
				punishLevel = state;
				punishTimer = 0;
				// Remove the task if any
				stopPunishTask(false);
				
				if (delayInMinutes > 0) {
					punishTimer = delayInMilliseconds;
					
					// start the countdown
					punishTask = ThreadPoolManager.getInstance().scheduleGeneral(new PunishTask(), punishTimer);
					sendMessage("You are in jail for " + delayInMinutes + " minutes.");
				}
				
				if (event != null) {
					event.removeParticipant(getObjectId());
				}
				if (EventsManager.getInstance().isPlayerParticipant(getObjectId())) {
					EventsManager.getInstance().removeParticipant(getObjectId());
				}
				if (OlympiadManager.getInstance().isRegisteredInComp(this)) {
					OlympiadManager.getInstance().removeDisconnectedCompetitor(this);
				}
				
				// Open a Html message to inform the player
				NpcHtmlMessage htmlMsg = new NpcHtmlMessage(0);
				String jailInfos = HtmCache.getInstance().getHtm(getHtmlPrefix(), "jail_in.htm");
				if (jailInfos != null) {
					htmlMsg.setHtml(jailInfos);
				} else {
					htmlMsg.setHtml("<html><body>You have been put in jail by an admin.</body></html>");
				}
				sendPacket(htmlMsg);
				setInstanceId(0);
				
				teleToLocation(-114356, -249645, -2984, false); // Jail
				break;
			}
			case CHAR: // Ban Character
			{
				setAccessLevel(-100);
				logout();
				break;
			}
			case ACC: // Ban Account
			{
				setAccountAccesslevel(-100);
				logout();
				break;
			}
			default: {
				punishLevel = state;
				break;
			}
		}
		
		// store in database
		storeCharBase();
	}
	
	public long getPunishTimer() {
		return punishTimer;
	}
	
	public void setPunishTimer(long time) {
		punishTimer = time;
	}
	
	private void updatePunishState() {
		if (getPunishLevel() != PunishLevel.NONE) {
			// If punish timer exists, restart punishtask.
			if (punishTimer > 0) {
				punishTask = ThreadPoolManager.getInstance().scheduleGeneral(new PunishTask(), punishTimer);
				sendMessage("You are still " + getPunishLevel().string() + " for " + Math.round(punishTimer / 60000f) + " minutes.");
			}
			if (getPunishLevel() == PunishLevel.JAIL) {
				// If player escaped, put him back in jail
				if (!isInsideZone(ZONE_JAIL)) {
					teleToLocation(-114356, -249645, -2984, true);
				}
			}
		}
	}
	
	public void stopPunishTask(boolean save) {
		if (punishTask != null) {
			if (save) {
				long delay = punishTask.getDelay(TimeUnit.MILLISECONDS);
				if (delay < 0) {
					delay = 0;
				}
				setPunishTimer(delay);
			}
			punishTask.cancel(false);
			//ThreadPoolManager.getInstance().removeGeneral((Runnable)punishTask);
			punishTask = null;
		}
	}
	
	private class PunishTask implements Runnable {
		@Override
		public void run() {
			Player.this.setPunishLevel(PunishLevel.NONE, 0);
		}
	}
	
	public void startFameTask(long delay, int fameFixRate) {
		if (getLevel() < 40 || getCurrentClass().level() > 0 && getCurrentClass().level() < 2) {
			return;
		}
		if (fameTask == null) {
			fameTask = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new FameTask(fameFixRate), delay, delay);
		}
	}
	
	public void stopFameTask() {
		if (fameTask != null) {
			fameTask.cancel(false);
			//ThreadPoolManager.getInstance().removeGeneral((Runnable)fameTask);
			fameTask = null;
		}
	}
	
	private class FameTask implements Runnable {
		private final Player player;
		private final int value;
		
		protected FameTask(int value) {
			player = Player.this;
			this.value = value;
		}
		
		@Override
		public void run() {
			if (player == null || player.isDead() && !Config.FAME_FOR_DEAD_PLAYERS) {
				return;
			}
			if ((player.getClient() == null || player.getClient().isDetached()) && !Config.OFFLINE_FAME) {
				return;
			}
			player.setFame(player.getFame() + value);
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.ACQUIRED_S1_REPUTATION_SCORE);
			sm.addNumber(value);
			player.sendPacket(sm);
			player.sendPacket(new UserInfo(player));
		}
	}
	
	public int getPowerGrade() {
		return powerGrade;
	}
	
	public void setPowerGrade(int power) {
		powerGrade = power;
	}
	
	public boolean isCursedWeaponEquipped() {
		return cursedWeaponEquippedId != 0;
	}
	
	public void setCursedWeaponEquippedId(int value) {
		cursedWeaponEquippedId = value;
	}
	
	public int getCursedWeaponEquippedId() {
		return cursedWeaponEquippedId;
	}
	
	@Override
	public boolean isAttackingDisabled() {
		return super.isAttackingDisabled() || combatFlagEquippedId;
	}
	
	public boolean isCombatFlagEquipped() {
		return combatFlagEquippedId;
	}
	
	public void setCombatFlagEquipped(boolean value) {
		combatFlagEquippedId = value;
	}
	
	public final void setIsRidingStrider(boolean mode) {
		isRidingStrider = mode;
	}
	
	public final boolean isRidingStrider() {
		return isRidingStrider;
	}
	
	/**
	 * Returns the Number of Souls this Player got.
	 *
	 */
	public int getSouls() {
		return souls;
	}
	
	/**
	 * Absorbs a Soul from a Npc.
	 *
	 * @param skill The used skill
	 * @param npc   The target
	 */
	public void absorbSoul(Skill skill, Npc npc) {
		if (souls >= skill.getNumSouls()) {
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.SOUL_CANNOT_BE_INCREASED_ANYMORE);
			sendPacket(sm);
			return;
		}
		
		increaseSouls(1);
		
		if (npc != null) {
			broadcastPacket(new ExSpawnEmitter(this, npc), 500);
		}
	}
	
	/**
	 * Increase Souls
	 *
	 */
	public void increaseSouls(int count) {
		if (count < 0 || count > 45) {
			return;
		}
		
		souls += count;
		
		if (getSouls() > 45) {
			souls = 45;
		}
		
		SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.YOUR_SOUL_HAS_INCREASED_BY_S1_SO_IT_IS_NOW_AT_S2);
		sm.addNumber(count);
		sm.addNumber(souls);
		sendPacket(sm);
		
		restartSoulTask();
		
		sendPacket(new EtcStatusUpdate(this));
	}
	
	/**
	 * Decreases existing Souls.
	 *
	 */
	public boolean decreaseSouls(int count, Skill skill) {
		if (getSouls() <= 0 && skill.getSoulConsumeCount() > 0) {
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.THERE_IS_NOT_ENOUGH_SOUL));
			return false;
		}
		
		souls -= count;
		
		if (getSouls() < 0) {
			souls = 0;
		}
		
		if (getSouls() == 0) {
			stopSoulTask();
		} else {
			restartSoulTask();
		}
		
		sendPacket(new EtcStatusUpdate(this));
		return true;
	}
	
	/**
	 * Clear out all Souls from this Player
	 */
	public void clearSouls() {
		souls = 0;
		stopSoulTask();
		sendPacket(new EtcStatusUpdate(this));
	}
	
	/**
	 * Starts/Restarts the SoulTask to Clear Souls after 10 Mins.
	 */
	private void restartSoulTask() {
		synchronized (this) {
			if (soulTask != null) {
				soulTask.cancel(false);
				soulTask = null;
			}
			soulTask = ThreadPoolManager.getInstance().scheduleGeneral(new SoulTask(), 600000);
		}
	}
	
	/**
	 * Stops the Clearing Task.
	 */
	public void stopSoulTask() {
		if (soulTask != null) {
			soulTask.cancel(false);
			//ThreadPoolManager.getInstance().removeGeneral((Runnable)soulTask);
			soulTask = null;
		}
	}
	
	private class SoulTask implements Runnable {
		@Override
		public void run() {
			clearSouls();
		}
	}
	
	public void shortBuffStatusUpdate(int magicId, int level, int time) {
		if (shortBuffTask != null) {
			shortBuffTask.cancel(false);
			shortBuffTask = null;
		}
		shortBuffTask = ThreadPoolManager.getInstance().scheduleGeneral(new ShortBuffTask(), time * 1000);
		setShortBuffTaskSkillId(magicId);
		
		sendPacket(new ShortBuffStatusUpdate(magicId, level, time));
	}
	
	public void setShortBuffTaskSkillId(int id) {
		shortBuffTaskSkillId = id;
	}
	
	public void calculateBreathOfShilenDebuff(Creature killer) {
		if (killer instanceof MonsterInstance) {
			switch (((MonsterInstance) killer).getNpcId()) {
				//Needs those NPC IDs (Spezion, Teredor, Veridan, Michaelo, Fortuna, Felicia, Isadora, Octavis, Istina, Balok, Barler)
				case 25532: // Kechi
				case 29068: // Antharas (Needs to be instanced version, don't know which is it)
					raiseBreathOfShilenDebuffLevel();
					break;
			}
		}
	}
	
	private void raiseBreathOfShilenDebuffLevel() {
		if (breathOfShilenDebuffLevel > 5) {
			return;
		}
		
		breathOfShilenDebuffLevel++;
		
		for (Abnormal effect : getAllEffects()) {
			if (effect.getSkill().getId() == 14571) {
				effect.exit();
			}
		}
		
		if (breathOfShilenDebuffLevel != 0) {
			SkillTable.getInstance().getInfo(14571, breathOfShilenDebuffLevel).getEffects(this, this);
		}
	}
	
	public void decreaseBreathOfShilenDebuffLevel() {
		if (breathOfShilenDebuffLevel >= 1) {
			if (!isDead()) {
				breathOfShilenDebuffLevel--;
				if (breathOfShilenDebuffLevel != 0) {
					SkillTable.getInstance().getInfo(14571, breathOfShilenDebuffLevel).getEffects(this, this);
				}
			}
		}
	}
	
	private Map<Integer, TimeStamp> reuseTimeStamps = new ConcurrentHashMap<>();
	private boolean canFeed;
	private boolean isInSiege;
	
	public Collection<TimeStamp> getReuseTimeStamps() {
		return reuseTimeStamps.values();
	}
	
	public Map<Integer, TimeStamp> getReuseTimeStamp() {
		return reuseTimeStamps;
	}
	
	/**
	 * Simple class containing all neccessary information to maintain
	 * valid timestamps and reuse for skills upon relog. Filter this
	 * carefully as it becomes redundant to store reuse for small delays.
	 *
	 * @author Yesod
	 */
	public static class TimeStamp {
		private final int skillId;
		private final int skillLvl;
		private final long reuse;
		private final long stamp;
		
		public TimeStamp(Skill skill, long reuse) {
			skillId = skill.getId();
			skillLvl = skill.getLevelHash();
			this.reuse = reuse;
			stamp = System.currentTimeMillis() + reuse;
		}
		
		public TimeStamp(Skill skill, long reuse, long systime) {
			skillId = skill.getId();
			skillLvl = skill.getLevelHash();
			this.reuse = reuse;
			stamp = systime;
		}
		
		public long getStamp() {
			return stamp;
		}
		
		public int getSkillId() {
			return skillId;
		}
		
		public int getSkillLvl() {
			return skillLvl;
		}
		
		public long getReuse() {
			return reuse;
		}
		
		public long getRemaining() {
			return Math.max(stamp - System.currentTimeMillis(), 0);
		}
		
		/* Check if the reuse delay has passed and
		 * if it has not then update the stored reuse time
		 * according to what is currently remaining on
		 * the delay. */
		public boolean hasNotPassed() {
			return System.currentTimeMillis() < stamp;
		}
	}
	
	/**
	 * Index according to skill id the current
	 * timestamp of use.
	 *
	 * @param reuse delay
	 */
	@Override
	public void addTimeStamp(Skill skill, long reuse) {
		reuseTimeStamps.put(skill.getReuseHashCode(), new TimeStamp(skill, reuse));
	}
	
	/**
	 * Index according to skill this TimeStamp
	 * instance for restoration purposes only.
	 */
	public void addTimeStamp(Skill skill, long reuse, long systime) {
		reuseTimeStamps.put(skill.getReuseHashCode(), new TimeStamp(skill, reuse, systime));
	}
	
	@Override
	public Player getActingPlayer() {
		return this;
	}
	
	@Override
	public final void sendDamageMessage(Creature target, int damage, boolean mcrit, boolean pcrit, boolean miss) {
		// Check if hit is missed
		if (miss) {
			if (target instanceof Player) {
				SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.C1_EVADED_C2_ATTACK);
				sm.addPcName((Player) target);
				sm.addCharName(this);
				target.sendPacket(sm);
			}
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.C1_ATTACK_WENT_ASTRAY).addPcName(this));
			return;
		}
		
		// Check if hit is critical
		if (pcrit) {
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.C1_HAD_CRITICAL_HIT).addPcName(this));
			if (target instanceof Npc && getSkillLevelHash(467) > 0) {
				Skill skill = SkillTable.getInstance().getInfo(467, getSkillLevelHash(467));
				if (Rnd.get(100) < skill.getCritChance()) {
					absorbSoul(skill, (Npc) target);
				}
			}
		}
		if (mcrit) {
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CRITICAL_HIT_MAGIC));
		}
		
		if (isInOlympiadMode() && target instanceof Player && ((Player) target).isInOlympiadMode() &&
				((Player) target).getOlympiadGameId() == getOlympiadGameId()) {
			OlympiadGameManager.getInstance().notifyCompetitorDamage(this, damage);
		}
		
		final SystemMessage sm;
		
		int dmgCap = (int) target.getStat().calcStat(Stats.DAMAGE_CAP, 0, null, null);
		if (dmgCap > 0 && damage > dmgCap) {
			damage = dmgCap;
		}
		
		if (damage == -1 || target.isInvul(this) && !(target instanceof Npc) ||
				target.getFaceoffTarget() != null && target.getFaceoffTarget() != this) {
			sm = SystemMessage.getSystemMessage(SystemMessageId.ATTACK_WAS_BLOCKED);
		} else {
			sm = SystemMessage.getSystemMessage(SystemMessageId.YOU_DID_S1_DMG);
			sm.addNumber(damage);
			sm.addHpChange(target.getObjectId(), getObjectId(), -damage);
		}
		sendPacket(sm);
	}
	
	public void setAgathionId(int npcId) {
		agathionId = npcId;
	}
	
	public int getAgathionId() {
		return agathionId;
	}
	
	public int getVitalityPoints() {
		return getStat().getVitalityPoints();
	}
	
	public void setVitalityPoints(int points, boolean quiet) {
		getStat().setVitalityPoints(points, quiet, false);
	}
	
	public void setVitalityPoints(int points, boolean quiet, boolean allowGM) {
		getStat().setVitalityPoints(points, quiet, allowGM);
	}
	
	public void updateVitalityPoints(float points, boolean useRates, boolean quiet) {
		getStat().updateVitalityPoints(points, useRates, quiet);
	}
	
	/*
	 * Function for skill summon friend or Gate Chant.
	 */
	
	/**
	 * Request Teleport
	 **/
	public boolean teleportRequest(Player requester, Skill skill) {
		if (summonRequest.getTarget() != null && requester != null) {
			return false;
		}
		summonRequest.setTarget(requester, skill);
		return true;
	}
	
	/**
	 * Action teleport
	 **/
	public void teleportAnswer(int answer, int requesterId) {
		if (summonRequest.getTarget() == null) {
			return;
		}
		if (answer == 1 && summonRequest.getTarget().getObjectId() == requesterId) {
			teleToTarget(this, summonRequest.getTarget(), summonRequest.getSkill());
		}
		summonRequest.setTarget(null, null);
	}
	
	public static void teleToTarget(Player targetChar, Player summonerChar, Skill summonSkill) {
		if (targetChar == null || summonerChar == null || summonSkill == null) {
			return;
		}
		
		if (!checkSummonerStatus(summonerChar)) {
			return;
		}
		if (!checkSummonTargetStatus(targetChar, summonerChar)) {
			return;
		}
		
		int itemConsumeId = summonSkill.getTargetConsumeId();
		int itemConsumeCount = summonSkill.getTargetConsume();
		if (itemConsumeId > 0 && itemConsumeCount > 0) {
			//Delete by rocknow
			if (targetChar.getInventory().getInventoryItemCount(itemConsumeId, 0) < itemConsumeCount) {
				SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S1_REQUIRED_FOR_SUMMONING);
				sm.addItemName(summonSkill.getTargetConsumeId());
				targetChar.sendPacket(sm);
				return;
			}
			targetChar.getInventory().destroyItemByItemId("Consume", itemConsumeId, itemConsumeCount, summonerChar, targetChar);
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S1_DISAPPEARED);
			sm.addItemName(summonSkill.getTargetConsumeId());
			targetChar.sendPacket(sm);
		}
		targetChar.teleToLocation(summonerChar.getX(), summonerChar.getY(), summonerChar.getZ(), true);
	}
	
	public static boolean checkSummonerStatus(Player summonerChar) {
		if (summonerChar == null) {
			return false;
		}
		
		if (summonerChar.isInOlympiadMode()) {
			summonerChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.THIS_ITEM_IS_NOT_AVAILABLE_FOR_THE_OLYMPIAD_EVENT));
			return false;
		}
		
		if (summonerChar.getIsInsideGMEvent()) {
			return false;
		}
		
		if (summonerChar.inObserverMode()) {
			return false;
		}
		
		if (summonerChar.getEvent() != null && !summonerChar.getEvent().onEscapeUse(summonerChar.getObjectId())) {
			summonerChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOUR_TARGET_IS_IN_AN_AREA_WHICH_BLOCKS_SUMMONING));
			return false;
		}
		
		if (summonerChar.isInsideZone(Creature.ZONE_NOSUMMONFRIEND) || summonerChar.isFlyingMounted()) {
			summonerChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOUR_TARGET_IS_IN_AN_AREA_WHICH_BLOCKS_SUMMONING));
			return false;
		}
		return true;
	}
	
	public static boolean checkSummonTargetStatus(WorldObject target, Player summonerChar) {
		if (target == null || !(target instanceof Player)) {
			return false;
		}
		
		Player targetChar = (Player) target;
		if (targetChar.isAlikeDead()) {
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.C1_IS_DEAD_AT_THE_MOMENT_AND_CANNOT_BE_SUMMONED);
			sm.addPcName(targetChar);
			summonerChar.sendPacket(sm);
			return false;
		}
		
		if (targetChar.isInStoreMode()) {
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.C1_CURRENTLY_TRADING_OR_OPERATING_PRIVATE_STORE_AND_CANNOT_BE_SUMMONED);
			sm.addPcName(targetChar);
			summonerChar.sendPacket(sm);
			return false;
		}
		
		if (targetChar.isRooted() || targetChar.isInCombat()) {
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.C1_IS_ENGAGED_IN_COMBAT_AND_CANNOT_BE_SUMMONED);
			sm.addPcName(targetChar);
			summonerChar.sendPacket(sm);
			return false;
		}
		
		if (targetChar.isInOlympiadMode()) {
			summonerChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_CANNOT_SUMMON_PLAYERS_WHO_ARE_IN_OLYMPIAD));
			return false;
		}
		
		if (targetChar.getIsInsideGMEvent()) {
			return false;
		}
		
		if (targetChar.isFlyingMounted()) {
			summonerChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOUR_TARGET_IS_IN_AN_AREA_WHICH_BLOCKS_SUMMONING));
			return false;
		}
		
		if (targetChar.inObserverMode()) {
			summonerChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.C1_STATE_FORBIDS_SUMMONING).addCharName(targetChar));
			return false;
		}
		
		if (!targetChar.canEscape() || targetChar.isCombatFlagEquipped()) {
			summonerChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOUR_TARGET_IS_IN_AN_AREA_WHICH_BLOCKS_SUMMONING));
			return false;
		}
		
		if (targetChar.getEvent() != null && !targetChar.getEvent().onEscapeUse(targetChar.getObjectId())) {
			summonerChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOUR_TARGET_IS_IN_AN_AREA_WHICH_BLOCKS_SUMMONING));
			return false;
		}
		
		if (targetChar.isInsideZone(Creature.ZONE_NOSUMMONFRIEND)) {
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.C1_IN_SUMMON_BLOCKING_AREA);
			sm.addString(targetChar.getName());
			summonerChar.sendPacket(sm);
			return false;
		}
		
		if (summonerChar.getInstanceId() > 0) {
			Instance summonerInstance = InstanceManager.getInstance().getInstance(summonerChar.getInstanceId());
			if (!Config.ALLOW_SUMMON_TO_INSTANCE || !summonerInstance.isSummonAllowed()) {
				summonerChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_MAY_NOT_SUMMON_FROM_YOUR_CURRENT_LOCATION));
				return false;
			}
		}
		
		return true;
	}
	
	public void gatesRequest(DoorInstance door) {
		gatesRequest.setTarget(door);
	}
	
	public void gatesAnswer(int answer, int type) {
		if (gatesRequest.getDoor() == null) {
			return;
		}
		
		if (answer == 1 && getTarget() == gatesRequest.getDoor() && type == 1) {
			gatesRequest.getDoor().openMe();
		} else if (answer == 1 && getTarget() == gatesRequest.getDoor() && type == 0) {
			gatesRequest.getDoor().closeMe();
		}
		
		gatesRequest.setTarget(null);
	}
	
	public void checkItemRestriction() {
		for (int i = 0; i < Inventory.PAPERDOLL_TOTALSLOTS; i++) {
			Item equippedItem = getInventory().getPaperdollItem(i);
			if (equippedItem != null &&
					(!equippedItem.getItem().checkCondition(this, this, false) || isInOlympiadMode() && equippedItem.getItem().isOlyRestricted())) {
				getInventory().unEquipItemInSlot(i);
				
				InventoryUpdate iu = new InventoryUpdate();
				iu.addModifiedItem(equippedItem);
				sendPacket(iu);
				
				SystemMessage sm = null;
				if (equippedItem.getItem().getBodyPart() == ItemTemplate.SLOT_BACK) {
					sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CLOAK_REMOVED_BECAUSE_ARMOR_SET_REMOVED));
					return;
				}
				
				if (equippedItem.getEnchantLevel() > 0) {
					sm = SystemMessage.getSystemMessage(SystemMessageId.EQUIPMENT_S1_S2_REMOVED);
					sm.addNumber(equippedItem.getEnchantLevel());
					sm.addItemName(equippedItem);
				} else {
					sm = SystemMessage.getSystemMessage(SystemMessageId.S1_DISARMED);
					sm.addItemName(equippedItem);
				}
				sendPacket(sm);
			}
		}
	}
	
	public void setTransformAllowedSkills(int[] ids) {
		transformAllowedSkills = ids;
	}
	
	public boolean containsAllowedTransformSkill(int id) {
		for (int transformAllowedSkill : transformAllowedSkills) {
			if (transformAllowedSkill == id) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Section for mounted pets
	 */
	private class FeedTask implements Runnable {
		@Override
		public void run() {
			try {
				if (!isMounted() || getMountNpcId() == 0) {
					stopFeed();
					return;
				}
				
				if (getCurrentFeed() > getFeedConsume()) {
					// eat
					setCurrentFeed(getCurrentFeed() - getFeedConsume());
				} else {
					// go back to pet control item, or simply said, unsummon it
					setCurrentFeed(0);
					stopFeed();
					dismount();
					sendPacket(SystemMessage.getSystemMessage(SystemMessageId.OUT_OF_FEED_MOUNT_CANCELED));
				}
				
				L2PetData petData = getPetData(getMountNpcId());
				if (petData == null) {
					return;
				}
				
				int[] foodIds = petData.getFood();
				if (foodIds.length == 0) {
					return;
				}
				Item food = null;
				for (int id : foodIds) {
					if (getPetInv() != null) {
						food = getPetInv().getItemByItemId(id);
					} else {
						food = getInventory().getItemByItemId(id);
					}
					if (food != null) {
						break;
					}
				}
				
				if (food != null && isHungry()) {
					IItemHandler handler = ItemHandler.getInstance().getItemHandler(food.getEtcItem());
					if (handler != null) {
						handler.useItem(Player.this, food, false);
						SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.PET_TOOK_S1_BECAUSE_HE_WAS_HUNGRY);
						sm.addItemName(food.getItemId());
						sendPacket(sm);
					}
				}
			} catch (Exception e) {
				log.error("Mounted Pet [NpcId: " + getMountNpcId() + "] a feed task error has occurred", e);
			}
		}
	}
	
	protected synchronized void startFeed(int npcId) {
		canFeed = npcId > 0;
		if (!isMounted()) {
			return;
		}
		if (getPet() != null) {
			setCurrentFeed(getPet().getCurrentFed());
			controlItemId = getPet().getControlObjectId();
			petInv = getPet().getInventory();
			sendPacket(new SetupGauge(3, getCurrentFeed() * 10000 / getFeedConsume(), getMaxFeed() * 10000 / getFeedConsume()));
			if (!isDead()) {
				mountFeedTask = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new FeedTask(), 10000, 10000);
			}
		} else if (canFeed && getFeedConsume() > 0) {
			setCurrentFeed(getMaxFeed());
			SetupGauge sg = new SetupGauge(3, getCurrentFeed() * 10000 / getFeedConsume(), getMaxFeed() * 10000 / getFeedConsume());
			sendPacket(sg);
			if (!isDead()) {
				mountFeedTask = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new FeedTask(), 10000, 10000);
			}
		}
	}
	
	protected synchronized void stopFeed() {
		if (mountFeedTask != null) {
			mountFeedTask.cancel(false);
			//ThreadPoolManager.getInstance().removeGeneral((Runnable)mountFeedTask);
			mountFeedTask = null;
			if (Config.DEBUG) {
				log.debug("Pet [#" + mountNpcId + "] feed task stop");
			}
		}
	}
	
	private void clearPetData() {
		data = null;
	}
	
	private L2PetData getPetData(int npcId) {
		if (data == null) {
			data = PetDataTable.getInstance().getPetData(npcId);
		}
		return data;
	}
	
	private L2PetLevelData getPetLevelData(int npcId) {
		if (leveldata == null) {
			leveldata = PetDataTable.getInstance().getPetData(npcId).getPetLevelData(getMountLevel());
		}
		return leveldata;
	}
	
	public int getCurrentFeed() {
		return curFeed;
	}
	
	private int getFeedConsume() {
		if (getPetLevelData(mountNpcId) == null) {
			return 0;
		}
		// if pet is attacking
		if (isAttackingNow()) {
			return getPetLevelData(mountNpcId).getPetFeedBattle();
		} else {
			return getPetLevelData(mountNpcId).getPetFeedNormal();
		}
	}
	
	public void setCurrentFeed(int num) {
		if (getFeedConsume() == 0) {
			return;
		}
		curFeed = num > getMaxFeed() ? getMaxFeed() : num;
		SetupGauge sg = new SetupGauge(3, getCurrentFeed() * 10000 / getFeedConsume(), getMaxFeed() * 10000 / getFeedConsume());
		sendPacket(sg);
	}
	
	private int getMaxFeed() {
		if (getPetLevelData(mountNpcId) == null) {
			return 1;
		}
		return getPetLevelData(mountNpcId).getPetMaxFeed();
	}
	
	private boolean isHungry() {
		if (canFeed && getPetData(getMountNpcId()) != null && getPetLevelData(getMountNpcId()) != null) {
			return getCurrentFeed() < getPetData(getMountNpcId()).getHungry_limit() / 100f * getPetLevelData(getMountNpcId()).getPetMaxFeed();
		}
		return false;
	}
	
	private class Dismount implements Runnable {
		@Override
		public void run() {
			try {
				dismount();
			} catch (Exception e) {
				log.warn("Exception on dismount(): " + e.getMessage(), e);
			}
		}
	}
	
	public void enteredNoLanding(int delay) {
		dismountTask = ThreadPoolManager.getInstance().scheduleGeneral(new Player.Dismount(), delay * 1000);
	}
	
	public void exitedNoLanding() {
		if (dismountTask != null) {
			dismountTask.cancel(true);
			dismountTask = null;
		}
	}
	
	public void storePetFood(int petId) {
		if (controlItemId != 0 && petId != 0) {
			String req;
			req = "UPDATE pets SET fed=? WHERE item_obj_id = ?";
			Connection con = null;
			try {
				con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement statement = con.prepareStatement(req);
				statement.setInt(1, getCurrentFeed());
				statement.setInt(2, controlItemId);
				statement.executeUpdate();
				statement.close();
				controlItemId = 0;
			} catch (Exception e) {
				log.error("Failed to store Pet [NpcId: " + petId + "] data", e);
			} finally {
				L2DatabaseFactory.close(con);
			}
		}
	}
	
	/**
	 * End of section for mounted pets
	 */
	
	@Override
	public int getAttackElementValue(byte attribute) {
		
		// 20% if summon exist
		//if (!getSummons().isEmpty() && getCurrentClass().isSummoner())
		//	return value / 5;
		
		return super.getAttackElementValue(attribute);
	}
	
	public void setIsInSiege(boolean b) {
		isInSiege = b;
	}
	
	public boolean isInSiege() {
		return isInSiege;
	}
	
	public FloodProtectors getFloodProtectors() {
		return getClient().getFloodProtectors();
	}
	
	public boolean isFlyingMounted() {
		return isFlyingMounted;
	}
	
	public void setIsFlyingMounted(boolean val) {
		isFlyingMounted = val;
		setIsFlying(val);
	}
	
	/**
	 * Returns the Number of Charges this Player got.
	 *
	 */
	public int getCharges() {
		return charges.get();
	}
	
	public void increaseCharges(int count, int max) {
		if (charges.get() >= max) {
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.FORCE_MAXLEVEL_REACHED));
			return;
		} else {
			// if no charges - start clear task
			if (charges.get() == 0) {
				restartChargeTask();
			}
		}
		
		if (charges.addAndGet(count) >= max) {
			charges.set(max);
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.FORCE_MAXLEVEL_REACHED));
		} else {
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.FORCE_INCREASED_TO_S1);
			sm.addNumber(charges.get());
			sendPacket(sm);
		}
		
		sendPacket(new EtcStatusUpdate(this));
	}
	
	public boolean decreaseCharges(int count) {
		if (charges.get() < count) {
			return false;
		}
		
		if (charges.addAndGet(-count) == 0) {
			stopChargeTask();
		}
		
		sendPacket(new EtcStatusUpdate(this));
		return true;
	}
	
	public void clearCharges() {
		charges.set(0);
		sendPacket(new EtcStatusUpdate(this));
	}
	
	/**
	 * Starts/Restarts the ChargeTask to Clear Charges after 10 Mins.
	 */
	private void restartChargeTask() {
		if (chargeTask != null) {
			chargeTask.cancel(false);
			chargeTask = null;
		}
		chargeTask = ThreadPoolManager.getInstance().scheduleGeneral(new ChargeTask(), 600000);
	}
	
	/**
	 * Stops the Charges Clearing Task.
	 */
	public void stopChargeTask() {
		if (chargeTask != null) {
			chargeTask.cancel(false);
			//ThreadPoolManager.getInstance().removeGeneral((Runnable)chargeTask);
			chargeTask = null;
		}
	}
	
	private class ChargeTask implements Runnable {
		
		@Override
		public void run() {
			clearCharges();
		}
	}
	
	public static class TeleportBookmark {
		public int id, x, y, z, icon;
		public String name, tag;
		
		TeleportBookmark(int id, int x, int y, int z, int icon, String tag, String name) {
			this.id = id;
			this.x = x;
			this.y = y;
			this.z = z;
			this.icon = icon;
			this.name = name;
			this.tag = tag;
		}
	}
	
	public void teleportBookmarkModify(int Id, int icon, String tag, String name) {
		int count = 0;
		int size = tpbookmark.size();
		while (size > count) {
			if (tpbookmark.get(count).id == Id) {
				tpbookmark.get(count).icon = icon;
				tpbookmark.get(count).tag = tag;
				tpbookmark.get(count).name = name;
				
				Connection con = null;
				
				try {
					
					con = L2DatabaseFactory.getInstance().getConnection();
					PreparedStatement statement = con.prepareStatement(UPDATE_TP_BOOKMARK);
					
					statement.setInt(1, icon);
					statement.setString(2, tag);
					statement.setString(3, name);
					statement.setInt(4, getObjectId());
					statement.setInt(5, Id);
					
					statement.execute();
					statement.close();
				} catch (Exception e) {
					log.warn("Could not update character teleport bookmark data: " + e.getMessage(), e);
				} finally {
					L2DatabaseFactory.close(con);
				}
			}
			count++;
		}
		
		sendPacket(new ExGetBookMarkInfoPacket(this));
	}
	
	public void teleportBookmarkDelete(int Id) {
		Connection con = null;
		
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(DELETE_TP_BOOKMARK);
			
			statement.setInt(1, getObjectId());
			statement.setInt(2, Id);
			
			statement.execute();
			statement.close();
		} catch (Exception e) {
			log.warn("Could not delete character teleport bookmark data: " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
		
		int count = 0;
		int size = tpbookmark.size();
		
		while (size > count) {
			if (tpbookmark.get(count).id == Id) {
				tpbookmark.remove(count);
				break;
			}
			count++;
		}
		
		sendPacket(new ExGetBookMarkInfoPacket(this));
	}
	
	public void teleportBookmarkGo(int Id) {
		if (!teleportBookmarkCondition(0)) {
			return;
		}
		if (getInventory().getInventoryItemCount(13016, 0) == 0) {
			sendPacket(SystemMessage.getSystemMessage(2359));
			return;
		}
		SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S1_DISAPPEARED);
		sm.addItemName(13016);
		sendPacket(sm);
		int count = 0;
		int size = tpbookmark.size();
		while (size > count) {
			if (tpbookmark.get(count).id == Id) {
				destroyItem("Consume", getInventory().getItemByItemId(13016).getObjectId(), 1, null, false);
				this.teleToLocation(tpbookmark.get(count).x, tpbookmark.get(count).y, tpbookmark.get(count).z);
				break;
			}
			count++;
		}
		sendPacket(new ExGetBookMarkInfoPacket(this));
	}
	
	public boolean teleportBookmarkCondition(int type) {
		if (isInCombat()) {
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_DURING_A_BATTLE));
			return false;
		} else if (isInSiege() || getSiegeState() != 0) {
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_WHILE_PARTICIPATING));
			return false;
		} else if (isInDuel()) {
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_DURING_A_DUEL));
			return false;
		} else if (isFlying()) {
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_WHILE_FLYING));
			return false;
		} else if (isInOlympiadMode()) {
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_WHILE_PARTICIPATING_IN_AN_OLYMPIAD_MATCH));
			return false;
		} else if (isParalyzed()) {
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_WHILE_YOU_ARE_PARALYZED));
			return false;
		} else if (isDead()) {
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_WHILE_YOU_ARE_DEAD));
			return false;
		} else if (isInBoat() || isInAirShip() || isInJail() || isInsideZone(ZONE_NOSUMMONFRIEND)) {
			if (type == 0) {
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_IN_THIS_AREA));
			} else if (type == 1) {
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_TO_REACH_THIS_AREA));
			}
			return false;
		} else if (isInWater()) {
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_UNDERWATER));
			return false;
		} else if (type == 1 && (isInsideZone(ZONE_SIEGE) || isInsideZone(ZONE_CLANHALL) || isInsideZone(ZONE_JAIL) || isInsideZone(ZONE_CASTLE) ||
				isInsideZone(ZONE_NOSUMMONFRIEND) || isInsideZone(ZONE_FORT))) {
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_TO_REACH_THIS_AREA));
			return false;
		} else if (isInsideZone(ZONE_NOBOOKMARK)) {
			if (type == 0) {
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_IN_THIS_AREA));
			} else if (type == 1) {
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_CANNOT_USE_MY_TELEPORTS_TO_REACH_THIS_AREA));
			}
			return false;
		}
		/* TODO: Instant Zone still not implement
		else if (this.isInsideZone(ZONE_INSTANT))
		{
			sendPacket(SystemMessage.getSystemMessage(2357));
			return;
		}
		 */
		else {
			return true;
		}
	}
	
	public void teleportBookmarkAdd(int x, int y, int z, int icon, String tag, String name) {
		if (!teleportBookmarkCondition(1)) {
			return;
		}
		
		if (tpbookmark.size() >= bookmarkslot) {
			sendPacket(SystemMessage.getSystemMessage(2358));
			return;
		}
		
		if (getInventory().getInventoryItemCount(20033, 0) == 0) {
			sendPacket(SystemMessage.getSystemMessage(6501));
			return;
		}
		
		int count = 0;
		int id = 1;
		ArrayList<Integer> idlist = new ArrayList<>();
		
		int size = tpbookmark.size();
		
		while (size > count) {
			idlist.add(tpbookmark.get(count).id);
			count++;
		}
		
		for (int i = 1; i < 10; i++) {
			if (!idlist.contains(i)) {
				id = i;
				break;
			}
		}
		
		TeleportBookmark tpadd = new TeleportBookmark(id, x, y, z, icon, tag, name);
		if (tpbookmark == null) {
			tpbookmark = new ArrayList<>();
		}
		
		tpbookmark.add(tpadd);
		
		destroyItem("Consume", getInventory().getItemByItemId(20033).getObjectId(), 1, null, false);
		
		SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S1_DISAPPEARED);
		sm.addItemName(20033);
		sendPacket(sm);
		
		Connection con = null;
		
		try {
			
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(INSERT_TP_BOOKMARK);
			
			statement.setInt(1, getObjectId());
			statement.setInt(2, id);
			statement.setInt(3, x);
			statement.setInt(4, y);
			statement.setInt(5, z);
			statement.setInt(6, icon);
			statement.setString(7, tag);
			statement.setString(8, name);
			
			statement.execute();
			statement.close();
		} catch (Exception e) {
			log.warn("Could not insert character teleport bookmark data: " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
		
		sendPacket(new ExGetBookMarkInfoPacket(this));
	}
	
	public void restoreTeleportBookmark() {
		if (tpbookmark == null) {
			tpbookmark = new ArrayList<>();
		}
		Connection con = null;
		
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(RESTORE_TP_BOOKMARK);
			statement.setInt(1, getObjectId());
			ResultSet rset = statement.executeQuery();
			
			while (rset.next()) {
				tpbookmark.add(new TeleportBookmark(rset.getInt("Id"),
						rset.getInt("x"),
						rset.getInt("y"),
						rset.getInt("z"),
						rset.getInt("icon"),
						rset.getString("tag"),
						rset.getString("name")));
			}
			
			rset.close();
			statement.close();
		} catch (Exception e) {
			log.error("Failed restoing character teleport bookmark.", e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	@Override
	public void sendInfo(Player activeChar) {
		int relation1 = getRelation(activeChar);
		int relation2 = activeChar.getRelation(this);
		
		if (getAppearance().getInvisible() && !activeChar.isGM() && (relation1 & RelationChanged.RELATION_HAS_PARTY) == 0) {
			return;
		}
		
		if (isInBoat()) {
			getPosition().setWorldPosition(getBoat().getPosition().getWorldPosition());
			
			activeChar.sendPacket(new CharInfo(this));
			Integer oldrelation = getKnownList().getKnownRelations().get(activeChar.getObjectId());
			if (oldrelation != null && oldrelation != relation1) {
				activeChar.sendPacket(new RelationChanged(this, relation1, isAutoAttackable(activeChar)));
				if (getPet() != null) {
					activeChar.sendPacket(new RelationChanged(getPet(), relation1, isAutoAttackable(activeChar)));
				}
				for (SummonInstance summon : getSummons()) {
					activeChar.sendPacket(new RelationChanged(summon, relation1, isAutoAttackable(activeChar)));
				}
			}
			oldrelation = activeChar.getKnownList().getKnownRelations().get(getObjectId());
			if (oldrelation != null && oldrelation != relation2) {
				sendPacket(new RelationChanged(activeChar, relation2, activeChar.isAutoAttackable(this)));
				if (activeChar.getPet() != null) {
					sendPacket(new RelationChanged(activeChar.getPet(), relation2, activeChar.isAutoAttackable(this)));
				}
				for (SummonInstance summon : getSummons()) {
					activeChar.sendPacket(new RelationChanged(summon, relation2, activeChar.isAutoAttackable(this)));
				}
			}
			activeChar.sendPacket(new GetOnVehicle(getObjectId(), getBoat().getObjectId(), getInVehiclePosition()));
		} else if (isInAirShip()) {
			getPosition().setWorldPosition(getAirShip().getPosition().getWorldPosition());
			
			activeChar.sendPacket(new CharInfo(this));
			Integer oldrelation = getKnownList().getKnownRelations().get(activeChar.getObjectId());
			if (oldrelation != null && oldrelation != relation1) {
				activeChar.sendPacket(new RelationChanged(this, relation1, isAutoAttackable(activeChar)));
				if (getPet() != null) {
					activeChar.sendPacket(new RelationChanged(getPet(), relation1, isAutoAttackable(activeChar)));
				}
				for (SummonInstance summon : getSummons()) {
					activeChar.sendPacket(new RelationChanged(summon, relation1, isAutoAttackable(activeChar)));
				}
			}
			oldrelation = activeChar.getKnownList().getKnownRelations().get(getObjectId());
			if (oldrelation != null && oldrelation != relation2) {
				sendPacket(new RelationChanged(activeChar, relation2, activeChar.isAutoAttackable(this)));
				if (activeChar.getPet() != null) {
					sendPacket(new RelationChanged(activeChar.getPet(), relation2, activeChar.isAutoAttackable(this)));
				}
				for (SummonInstance summon : getSummons()) {
					activeChar.sendPacket(new RelationChanged(summon, relation2, activeChar.isAutoAttackable(this)));
				}
			}
			activeChar.sendPacket(new ExGetOnAirShip(this, getAirShip()));
		} else {
			activeChar.sendPacket(new CharInfo(this));
			Integer oldrelation = getKnownList().getKnownRelations().get(activeChar.getObjectId());
			if (oldrelation != null && oldrelation != relation1) {
				activeChar.sendPacket(new RelationChanged(this, relation1, isAutoAttackable(activeChar)));
				if (getPet() != null) {
					activeChar.sendPacket(new RelationChanged(getPet(), relation1, isAutoAttackable(activeChar)));
				}
				for (SummonInstance summon : getSummons()) {
					activeChar.sendPacket(new RelationChanged(summon, relation1, isAutoAttackable(activeChar)));
				}
			}
			oldrelation = activeChar.getKnownList().getKnownRelations().get(getObjectId());
			if (oldrelation != null && oldrelation != relation2) {
				sendPacket(new RelationChanged(activeChar, relation2, activeChar.isAutoAttackable(this)));
				if (activeChar.getPet() != null) {
					sendPacket(new RelationChanged(activeChar.getPet(), relation2, activeChar.isAutoAttackable(this)));
				}
				for (SummonInstance summon : getSummons()) {
					activeChar.sendPacket(new RelationChanged(summon, relation2, activeChar.isAutoAttackable(this)));
				}
			}
		}
		if (getMountType() == 4) {
			// TODO: Remove when horse mounts fixed
			//activeChar.sendPacket(new Ride(this, false, 0));
			activeChar.sendPacket(new Ride(this, true, getMountNpcId()));
		}
		
		switch (getPrivateStoreType()) {
			case Player.STORE_PRIVATE_SELL:
			case Player.STORE_PRIVATE_CUSTOM_SELL:
				activeChar.sendPacket(new PrivateStoreMsgSell(this));
				break;
			case Player.STORE_PRIVATE_PACKAGE_SELL:
				activeChar.sendPacket(new ExPrivateStoreSetWholeMsg(this));
				break;
			case Player.STORE_PRIVATE_BUY:
				activeChar.sendPacket(new PrivateStoreMsgBuy(this));
				break;
			case Player.STORE_PRIVATE_MANUFACTURE:
				activeChar.sendPacket(new RecipeShopMsg(this));
				break;
		}
		
		if (activeChar.getParty() != null) {
			int tag = activeChar.getParty().getTag(getObjectId());
			if (tag > 0) {
				activeChar.sendPacket(new ExTacticalSign(getObjectId(), tag));
			}
		}
	}
	
	public void showQuestMovie(int id) {
		if (movieId > 0) //already in movie
		{
			return;
		}
		abortAttack();
		abortCast();
		stopMove(null);
		movieId = id;
		sendPacket(new ExStartScenePlayer(id));
	}
	
	public boolean isAllowedToEnchantSkills() {
		if (isLocked()) {
			return false;
		}
		if (isTransformed()) {
			return false;
		}
		if (AttackStanceTaskManager.getInstance().getAttackStanceTask(this)) {
			return false;
		}
		if (isCastingNow() || isCastingSimultaneouslyNow()) {
			return false;
		}
		return !(isInBoat() || isInAirShip());
	}
	
	/**
	 * Set the creationTime of the Player.<BR><BR>
	 */
	public void setCreateTime(long creationTime) {
		this.creationTime = creationTime;
	}
	
	/**
	 * Return the creationTime of the Player.<BR><BR>
	 */
	public long getCreateTime() {
		return creationTime;
	}
	
	/**
	 * @return number of days to char birthday.<BR><BR>
	 */
	public int checkBirthDay() {
		QuestState state = getQuestState("CharacterBirthday");
		Calendar now = Calendar.getInstance();
		Calendar birth = Calendar.getInstance();
		now.setTimeInMillis(System.currentTimeMillis());
		birth.setTimeInMillis(creationTime);
		
		if (state != null && state.getInt("Birthday") > now.get(Calendar.YEAR)) {
			return -1;
		}
		
		// "Characters with a February 29 creation date will receive a gift on February 28."
		if (birth.get(Calendar.DAY_OF_MONTH) == 29 && birth.get(Calendar.MONTH) == 1) {
			birth.add(Calendar.HOUR_OF_DAY, -24);
		}
		
		if (now.get(Calendar.MONTH) == birth.get(Calendar.MONTH) && now.get(Calendar.DAY_OF_MONTH) == birth.get(Calendar.DAY_OF_MONTH) &&
				now.get(Calendar.YEAR) != birth.get(Calendar.YEAR)) {
			return 0;
		} else {
			int i;
			for (i = 1; i < 6; i++) {
				now.add(Calendar.HOUR_OF_DAY, 24);
				if (now.get(Calendar.MONTH) == birth.get(Calendar.MONTH) && now.get(Calendar.DAY_OF_MONTH) == birth.get(Calendar.DAY_OF_MONTH) &&
						now.get(Calendar.YEAR) != birth.get(Calendar.YEAR)) {
					return i;
				}
			}
		}
		return -1;
	}
	
	/**
	 * list of character friends
	 */
	private List<Integer> friendList = new ArrayList<>();
	
	public List<Integer> getFriendList() {
		return friendList;
	}
	
	public void restoreFriendList() {
		friendList.clear();
		
		Connection con = null;
		
		try {
			String sqlQuery = "SELECT friendId, memo FROM character_friends WHERE charId=? AND relation=0";
			
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(sqlQuery);
			statement.setInt(1, getObjectId());
			ResultSet rset = statement.executeQuery();
			
			int friendId;
			while (rset.next()) {
				friendId = rset.getInt("friendId");
				if (friendId == getObjectId()) {
					continue;
				}
				friendList.add(friendId);
				if (rset.getString("memo") != null) {
					friendMemo.put(friendId, rset.getString("memo"));
				}
			}
			
			rset.close();
			statement.close();
		} catch (Exception e) {
			log.warn("Error found in " + getName() + "'s FriendList: " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	public void restoreBlockList() {
		
		Connection con = null;
		
		try {
			String sqlQuery = "SELECT friendId, memo FROM character_friends WHERE charId=? AND relation=1";
			
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(sqlQuery);
			statement.setInt(1, getObjectId());
			ResultSet rset = statement.executeQuery();
			
			int friendId;
			while (rset.next()) {
				friendId = rset.getInt("friendId");
				if (friendId == getObjectId()) {
					continue;
				}
				if (rset.getString("memo") != null) {
					blockMemo.put(friendId, rset.getString("memo"));
				}
			}
			
			rset.close();
			statement.close();
		} catch (Exception e) {
			log.warn("Error found in " + getName() + "'s BlockList: " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 *
	 */
	private void notifyFriends() {
		FriendStatusPacket pkt = new FriendStatusPacket(getObjectId());
		for (int id : friendList) {
			Player friend = World.getInstance().getPlayer(id);
			if (friend != null) {
				friend.sendPacket(pkt);
			}
		}
	}
	
	/**
	 * @return the silenceMode
	 */
	public boolean isSilenceMode() {
		return silenceMode;
	}
	
	/**
	 * @param mode the silenceMode to set
	 */
	public void setSilenceMode(boolean mode) {
		silenceMode = mode;
		sendPacket(new EtcStatusUpdate(this));
	}
	
	private void storeRecipeShopList() {
		Connection con = null;
		
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement;
			L2ManufactureList list = getCreateList();
			
			if (list != null && list.size() > 0) {
				int position = 1;
				statement = con.prepareStatement("DELETE FROM character_recipeshoplist WHERE charId=? ");
				statement.setInt(1, getObjectId());
				statement.execute();
				statement.close();
				
				PreparedStatement statement2 =
						con.prepareStatement("INSERT INTO character_recipeshoplist (charId, Recipeid, Price, Pos) VALUES (?, ?, ?, ?)");
				for (L2ManufactureItem item : list.getList()) {
					statement2.setInt(1, getObjectId());
					statement2.setInt(2, item.getRecipeId());
					statement2.setLong(3, item.getCost());
					statement2.setInt(4, position);
					statement2.execute();
					statement2.clearParameters();
					position++;
				}
				statement2.close();
			}
		} catch (Exception e) {
			log.error("Could not store recipe shop for playerID " + getObjectId() + ": ", e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	private void restoreRecipeShopList() {
		Connection con = null;
		
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("SELECT Recipeid,Price FROM character_recipeshoplist WHERE charId=? ORDER BY Pos ASC");
			statement.setInt(1, getObjectId());
			ResultSet rset = statement.executeQuery();
			
			L2ManufactureList createList = new L2ManufactureList();
			while (rset.next()) {
				createList.add(new L2ManufactureItem(rset.getInt("Recipeid"), rset.getLong("Price")));
			}
			setCreateList(createList);
			rset.close();
			statement.close();
		} catch (Exception e) {
			log.error("Could not restore recipe shop list data for playerId: " + getObjectId(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	public double getCollisionRadius() {
		if (getAppearance().getSex()) {
			return getVisibleTemplate().fCollisionRadiusFemale;
		} else {
			return getVisibleTemplate().fCollisionRadius;
		}
	}
	
	public double getCollisionHeight() {
		if (getAppearance().getSex()) {
			return getVisibleTemplate().fCollisionHeightFemale;
		} else {
			return getVisibleTemplate().fCollisionHeight;
		}
	}
	
	public final int getClientX() {
		return clientX;
	}
	
	public final int getClientY() {
		return clientY;
	}
	
	public final int getClientZ() {
		return clientZ;
	}
	
	public final int getClientHeading() {
		return clientHeading;
	}
	
	public final void setClientX(int val) {
		clientX = val;
	}
	
	public final void setClientY(int val) {
		clientY = val;
	}
	
	public final void setClientZ(int val) {
		clientZ = val;
	}
	
	public final void setClientHeading(int val) {
		clientHeading = val;
	}
	
	/**
	 * Return true if character falling now
	 * On the start of fall return false for correct coord sync !
	 */
	public final boolean isFalling(int z) {
		if (isDead() || isFlying() || isFlyingMounted() || isInsideZone(ZONE_WATER)) {
			return false;
		}
		
		if (System.currentTimeMillis() < fallingTimestamp) {
			return true;
		}
		
		final int deltaZ = getZ() - z;
		if (deltaZ <= getBaseTemplate().getFallHeight()) {
			return false;
		}
		
		final int damage = (int) Formulas.calcFallDam(this, deltaZ);
		if (damage > 0) {
			reduceCurrentHp(Math.min(damage, getCurrentHp() - 1), null, false, true, null);
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.FALL_DAMAGE_S1).addNumber(damage));
		}
		
		setFalling();
		
		return false;
	}
	
	/**
	 * Set falling timestamp
	 */
	public final void setFalling() {
		fallingTimestamp = System.currentTimeMillis() + FALLING_VALIDATION_DELAY;
	}
	
	/**
	 * @return the movieId
	 */
	public int getMovieId() {
		return movieId;
	}
	
	public void setMovieId(int id) {
		movieId = id;
	}
	
	/**
	 * Update last item auction request timestamp to current
	 */
	public void updateLastItemAuctionRequest() {
		lastItemAuctionInfoRequest = System.currentTimeMillis();
	}
	
	/**
	 * Returns true if receiving item auction requests
	 * (last request was in 2 seconds before)
	 */
	public boolean isItemAuctionPolling() {
		return System.currentTimeMillis() - lastItemAuctionInfoRequest < 2000;
	}
	
	/* (non-Javadoc)
	 * @see l2server.gameserver.model.actor.Creature#isMovementDisabled()
	 */
	@Override
	public boolean isMovementDisabled() {
		return super.isMovementDisabled() || movieId > 0;
	}
	
	private void restoreUISettings() {
		uiKeySettings = new L2UIKeysSettings(this);
	}
	
	private void storeUISettings() {
		if (uiKeySettings == null) {
			return;
		}
		
		if (!uiKeySettings.isSaved()) {
			uiKeySettings.saveInDB();
		}
	}
	
	public L2UIKeysSettings getUISettings() {
		return uiKeySettings;
	}
	
	public String getHtmlPrefix() {
		return null;
	}
	
	public long getOfflineStartTime() {
		return offlineShopStart;
	}
	
	public void setOfflineStartTime(long time) {
		offlineShopStart = time;
	}
	
	/**
	 * Remove player from BossZones (used on char logout/exit)
	 */
	public void removeFromBossZone() {
		try {
			for (BossZone zone : GrandBossManager.getInstance().getZones()) {
				if (zone == null) {
					continue;
				}
				zone.removePlayer(this);
			}
		} catch (Exception e) {
			log.warn("Exception on removeFromBossZone(): " + e.getMessage(), e);
		}
	}
	
	/**
	 * Check all player skills for skill level. If player level is lower than skill learn level - 9, skill level is decreased to next possible level.
	 */
	public void checkPlayerSkills() {
		for (int id : skills.keySet()) {
			int level = getSkillLevelHash(id);
			if (level >= 100) // enchanted skill
			{
				level = SkillTable.getInstance().getMaxLevel(id);
			}
			L2SkillLearn learn = SkillTreeTable.getInstance().getSkillLearnBySkillIdLevel(getCurrentClass(), id, level);
			// not found - not a learn skill?
			if (learn == null) {
			} else {
				// player level is too low for such skill level
				if (getLevel() < learn.getMinLevel() - 9) {
					deacreaseSkillLevel(id);
				}
			}
		}
	}
	
	private void deacreaseSkillLevel(int id) {
		int nextLevel = -1;
		for (L2SkillLearn sl : PlayerClassTable.getInstance().getClassById(getCurrentClass().getId()).getSkills().values()) {
			if (sl.getId() == id && nextLevel < sl.getLevel() && getLevel() >= sl.getMinLevel() - 9) {
				// next possible skill level
				nextLevel = sl.getLevel();
			}
		}
		
		if (nextLevel == -1) // there is no lower skill
		{
			if (!Config.isServer(Config.TENKAI)) {
				log.info("Removing skill id " + id + " level " + getSkillLevelHash(id) + " from player " + this);
			}
			removeSkill(skills.get(id), true);
		} else
		// replace with lower one
		{
			if (!Config.isServer(Config.TENKAI)) {
				log.info("Decreasing skill id " + id + " from " + getSkillLevelHash(id) + " to " + nextLevel + " for " + this);
			}
			addSkill(SkillTable.getInstance().getInfo(id, nextLevel), true);
		}
	}
	
	public boolean canMakeSocialAction() {
		//&& getAI().getIntention() == CtrlIntention.AI_INTENTION_IDLE
		//&& !AttackStanceTaskManager.getInstance().getAttackStanceTask(this)
		//&& !isInOlympiadMode())
		return getPrivateStoreType() == 0 && getActiveRequester() == null && !isAlikeDead() && (!isAllSkillsDisabled() || isInDuel()) &&
				!isCastingNow() && !isCastingSimultaneouslyNow();
	}
	
	public void setMultiSocialAction(int id, int targetId) {
		multiSociaAction = id;
		multiSocialTarget = targetId;
	}
	
	public int getMultiSociaAction() {
		return multiSociaAction;
	}
	
	public int getMultiSocialTarget() {
		return multiSocialTarget;
	}
	
	public List<TeleportBookmark> getTpbookmark() {
		return tpbookmark;
	}
	
	public int getBookmarkslot() {
		return bookmarkslot;
	}
	
	public int getQuestInventoryLimit() {
		return Config.INVENTORY_MAXIMUM_QUEST_ITEMS;
	}
	
	public boolean canAttackCharacter(Creature cha) {
		if (cha instanceof Attackable) {
			return true;
		} else if (cha instanceof Playable) {
			if (cha.isInsideZone(Creature.ZONE_PVP) && !cha.isInsideZone(Creature.ZONE_SIEGE)) {
				return true;
			}
			
			Player target;
			if (cha instanceof Summon) {
				target = ((Summon) cha).getOwner();
			} else {
				target = (Player) cha;
			}
			
			if (isInDuel() && target.isInDuel() && target.getDuelId() == getDuelId()) {
				return true;
			} else if (isInParty() && target.isInParty()) {
				if (getParty() == target.getParty()) {
					return false;
				}
				if ((getParty().getCommandChannel() != null || target.getParty().getCommandChannel() != null) &&
						getParty().getCommandChannel() == target.getParty().getCommandChannel()) {
					return false;
				}
			} else if (getClan() != null && target.getClan() != null) {
				if (getClan() == target.getClan()) {
					return false;
				}
				if ((getAllyId() > 0 || target.getAllyId() > 0) && getAllyId() == target.getAllyId()) {
					return false;
				}
				if (getClan().isAtWarWith(target.getClan()) && target.getClan().isAtWarWith(getClan())) {
					return true;
				}
			} else if (getClan() == null || target.getClan() == null) {
				if (target.getPvpFlag() == 0 && target.getReputation() == 0) {
					return false;
				}
			}
		}
		return true;
	}
	
	/**
	 * Test if player inventory is under 90% capacity
	 *
	 * @param includeQuestInv check also quest inventory
	 */
	public boolean isInventoryUnder90(boolean includeQuestInv) {
		if (getInventory().getSize(false) <= getInventoryLimit() * 0.9) {
			if (includeQuestInv) {
				if (getInventory().getSize(true) <= getQuestInventoryLimit() * 0.9) {
					return true;
				}
			} else {
				return true;
			}
		}
		return false;
	}
	
	public boolean havePetInvItems() {
		return petItems;
	}
	
	public void setPetInvItems(boolean haveit) {
		petItems = haveit;
	}
	
	private void checkPetInvItems() {
		Connection con = null;
		
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement =
					con.prepareStatement("SELECT object_id FROM `items` WHERE `owner_id`=? AND (`loc`='PET' OR `loc`='PET_EQUIP') LIMIT 1;");
			statement.setInt(1, getObjectId());
			ResultSet rset = statement.executeQuery();
			if (rset.next() && rset.getInt("object_id") > 0) {
				setPetInvItems(true);
			} else {
				setPetInvItems(false);
			}
			rset.close();
			statement.close();
		} catch (Exception e) {
			log.error("Could not check Items in Pet Inventory for playerId: " + getObjectId(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	public String getAdminConfirmCmd() {
		return adminConfirmCmd;
	}
	
	public void setAdminConfirmCmd(String adminConfirmCmd) {
		this.adminConfirmCmd = adminConfirmCmd;
	}
	
	public void setBlockCheckerArena(byte arena) {
		handysBlockCheckerEventArena = arena;
	}
	
	public int getBlockCheckerArena() {
		return handysBlockCheckerEventArena;
	}
	
	/**
	 * Load Player Recommendations data.<BR><BR>
	 */
	private long loadRecommendations() {
		long _time_left = 0;
		Connection con = null;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("SELECT rec_have,rec_left,time_left FROM character_reco_bonus WHERE charId=? LIMIT 1");
			statement.setInt(1, getObjectId());
			ResultSet rset = statement.executeQuery();
			
			if (rset.next()) {
				setRecomHave(rset.getInt("rec_have"));
				setRecomLeft(rset.getInt("rec_left"));
				_time_left = rset.getLong("time_left");
			} else {
				_time_left = 3600000;
			}
			
			rset.close();
			statement.close();
		} catch (Exception e) {
			log.error("Could not restore Recommendations for player: " + getObjectId(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
		return _time_left;
	}
	
	/**
	 * Update Player Recommendations data.<BR><BR>
	 */
	public void storeRecommendations() {
		long recoTaskEnd = 0;
		if (recoBonusTask != null) {
			recoTaskEnd = Math.max(0, recoBonusTask.getDelay(TimeUnit.MILLISECONDS));
		}
		
		Connection con = null;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement = con.prepareStatement(
					"INSERT INTO character_reco_bonus (charId,rec_have,rec_left,time_left) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE rec_have=?, rec_left=?, time_left=?");
			statement.setInt(1, getObjectId());
			statement.setInt(2, getRecomHave());
			statement.setInt(3, getRecomLeft());
			statement.setLong(4, recoTaskEnd);
			// Update part
			statement.setInt(5, getRecomHave());
			statement.setInt(6, getRecomLeft());
			statement.setLong(7, recoTaskEnd);
			statement.execute();
			statement.close();
		} catch (Exception e) {
			log.error("Could not update Recommendations for player: " + getObjectId(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	public void checkRecoBonusTask() {
		// Load data
		long _task_time = loadRecommendations();
		
		if (_task_time > 0) {
			// Add 20 recos on first login
			if (_task_time == 3600000) {
				setRecomLeft(getRecomLeft() + 20);
			}
			// If player have some timeleft, start bonus task
			recoBonusTask = ThreadPoolManager.getInstance().scheduleGeneral(new RecoBonusTaskEnd(), _task_time);
		}
		// Create task to give new recommendations
		recoGiveTask = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new RecoGiveTask(), 7200000, 3600000);
		// Store new data
		storeRecommendations();
	}
	
	public void stopRecoBonusTask() {
		if (recoBonusTask != null) {
			recoBonusTask.cancel(false);
			recoBonusTask = null;
		}
	}
	
	public void stopRecoGiveTask() {
		if (recoGiveTask != null) {
			recoGiveTask.cancel(false);
			recoGiveTask = null;
		}
	}
	
	private class RecoGiveTask implements Runnable {
		@Override
		public void run() {
			int reco_to_give;
			// 10 recommendations to give out after 2 hours of being logged in
			// 1 more recommendation to give out every hour after that.
			if (recoTwoHoursGiven) {
				reco_to_give = 1;
			} else {
				reco_to_give = 10;
			}
			
			recoTwoHoursGiven = true;
			
			setRecomLeft(getRecomLeft() + reco_to_give);
			
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.YOU_OBTAINED_S1_RECOMMENDATIONS);
			sm.addNumber(reco_to_give);
			Player.this.sendPacket(sm);
			Player.this.sendPacket(new UserInfo(Player.this));
		}
	}
	
	private class RecoBonusTaskEnd implements Runnable {
		@Override
		public void run() {
			Player.this.sendPacket(new ExVoteSystemInfo(Player.this));
		}
	}
	
	public int getRecomBonusTime() {
		if (recoBonusTask != null) {
			return (int) Math.max(0, recoBonusTask.getDelay(TimeUnit.SECONDS));
		}
		
		return 0;
	}
	
	public int getRecomBonusType() {
		// Maintain = 1
		//return 0;
		return getRecomBonusTime() == 0 ? 0 : 1;
	}
	
	// Summons that this character summoned before logging out
	private List<Integer> lastSummons = new ArrayList<>();
	
	private void storeLastSummons() {
		Connection con = null;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			
			// Delete all current stored effects for char to avoid dupe
			PreparedStatement statement = con.prepareStatement("DELETE FROM character_last_summons WHERE charId = ?");
			
			statement.setInt(1, getObjectId());
			statement.execute();
			statement.close();
			
			// Store all effect data along with calulated remaining
			// reuse delays for matching skills. 'restore_type'= 0.
			statement = con.prepareStatement("INSERT INTO character_last_summons (charId, summonIndex, npcId) VALUES (?, ?, ?)");
			
			int i = 0;
			for (int summonId : lastSummons) {
				statement.setInt(1, getObjectId());
				statement.setInt(2, i);
				statement.setInt(3, summonId);
				statement.execute();
				i++;
			}
			statement.close();
		} catch (Exception e) {
			log.warn("Could not store last summons data: ", e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	public void restoreLastSummons() {
		lastSummons.clear();
		
		Connection con = null;
		
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("SELECT npcId FROM character_last_summons WHERE charId=?");
			statement.setInt(1, getObjectId());
			ResultSet rset = statement.executeQuery();
			
			while (rset.next()) {
				int npcId = rset.getInt("npcId");
				lastSummons.add(npcId);
			}
			
			rset.close();
			statement.close();
		} catch (Exception e) {
			log.warn("Error found in " + getName() + "'s last summons' list: " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	public void summonLastSummons() {
		for (int petId : lastSummons) {
			int skillLevel = getSkillLevelHash(petId);
			if (skillLevel == -1) {
				continue;
			}
			
			Skill skill = SkillTable.getInstance().getInfo(petId, skillLevel);
			if (skill instanceof SkillSummon) {
				boolean canSummon = true;
				if (((SkillSummon) skill).getSummonPoints() > 0 &&
						getSpentSummonPoints() + ((SkillSummon) skill).getSummonPoints() > getMaxSummonPoints()) {
					canSummon = false;
				}
				
				if (getSpentSummonPoints() == 0 && !getSummons().isEmpty()) {
					canSummon = false;
				}
				
				if (canSummon) {
					NpcTemplate npcTemplate = NpcTable.getInstance().getTemplate(((SkillSummon) skill).getNpcId());
					SummonInstance summon = new SummonInstance(IdFactory.getInstance().getNextId(), npcTemplate, this, skill);
					
					summon.setName(npcTemplate.Name);
					summon.setTitle(getName());
					summon.setExpPenalty(0);
					if (summon.getLevel() > Config.MAX_LEVEL) {
						summon.getStat().setExp(Experience.getAbsoluteExp(Config.MAX_LEVEL));
						log.warn("Summon (" + summon.getName() + ") NpcID: " + summon.getNpcId() + " has a level above " + Config.MAX_LEVEL +
								". Please rectify.");
					} else {
						summon.getStat().setExp(Experience.getAbsoluteExp(summon.getLevel()));
					}
					summon.setCurrentHp(summon.getMaxHp());
					summon.setCurrentMp(summon.getMaxMp());
					summon.setHeading(getHeading());
					summon.setRunning();
					addSummon(summon);
					
					summon.spawnMe(getX() + 50, getY() + 100, getZ());
					summon.restoreEffects();
				}
			}
		}
		
		lastSummons.clear();
	}
	
	public void setOlyGivenDmg(int olyGivenDmg) {
		this.olyGivenDmg = olyGivenDmg;
	}
	
	public int getOlyGivenDmg() {
		return olyGivenDmg;
	}
	
	public boolean isAtWarWithCastle(int castleId) {
		if (getClan() == null) {
			return false;
		}
		int clanId = 0;
		String consultaCastell = "SELECT clan_id FROM clan_data WHERE hasCastle=?";
		Connection con = null;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement = con.prepareStatement(consultaCastell);
			statement.setInt(1, castleId);
			ResultSet rset = statement.executeQuery();
			
			while (rset.next()) {
				clanId = rset.getInt("clan_id");
			}
			
			rset.close();
			statement.close();
		} catch (Exception e) {
			log.error("No s'ha pogut agafar el clan del castell " + castleId + ": " + e);
		} finally {
			L2DatabaseFactory.close(con);
		}
		return getClan().getWarList().contains(clanId);
	}
	
	public void setIsItemId(boolean isItemId) {
		this.isItemId = isItemId;
	}
	
	public boolean isItemId() {
		return isItemId;
	}
	
	public EventTeam getCtfFlag() {
		return ctfFlag;
	}
	
	public void setCtfFlag(EventTeam team) {
		ctfFlag = team;
	}
	
	public int getEventPoints() {
		return eventPoints;
	}
	
	public void setEventPoints(int eventPoints) {
		/*if (eventPoints < -1) eventPoints += 5000;
		if (eventPoints >= 0)
		{
			if (!titleModified)
			{
				originalTitle = getTitle();
			  titleModified = true;
			}
			int titleId = 40153;
			if (TenkaiEvent.tipus == 1 || TenkaiEvent.tipus == 5)
				titleId = 40154;
			else if (TenkaiEvent.tipus == 6)
				titleId = 40155;
			CoreMessage cm = new CoreMessage(titleId);
			cm.addNumber(eventPoints);
			setTitle(cm.renderMsg(getLang()));
			broadcastTitleInfo();
		}
		else if (titleModified)
		{
			setTitle(originalTitle);
			broadcastTitleInfo();
			titleModified = false;
		}*/
		this.eventPoints = eventPoints;
	}
	
	public void addEventPoints(int eventPoints) {
		eventPoints += eventPoints;
	}
	
	// Has moved?
	private boolean hasMoved;
	
	public boolean hasMoved() {
		return hasMoved;
	}
	
	public void setHasMoved(boolean hasMoved) {
		this.hasMoved = hasMoved;
	}
	
	class HasMovedTask implements Runnable {
		@Override
		public void run() {
			Player player = Player.this;
			EventInstance event = player.getEvent();
			if (isPlayingEvent()) {
				if (!hasMoved && !isDead() && !event.isType(EventType.VIP) && !event.isType(EventType.KingOfTheHill) &&
						!event.isType(EventType.SimonSays) && !player.isSleeping()) {
					player.sendPacket(new CreatureSay(0, Say2.TELL, "Instanced Events", "We don't like idle participants!"));
					event.removeParticipant(player.getObjectId());
					new EventTeleporter(player, new Point3D(0, 0, 0), true, true);
				} else {
					hasMoved = false;
					ThreadPoolManager.getInstance().scheduleGeneral(this, 120000L);
				}
			}
		}
	}
	
	public void startHasMovedTask() {
		hasMoved = false;
		ThreadPoolManager.getInstance().scheduleGeneral(new HasMovedTask(), 50000L);
	}
	
	public void eventSaveData() {
		int i = 0;
		for (Abnormal effect : getAllEffects()) {
			if (i >= EVENT_SAVED_EFFECTS_SIZE) {
				break;
			}
			eventSavedEffects[i] = effect;
			i++;
		}
		if (getPet() != null) {
			i = 0;
			for (Abnormal effect : getPet().getAllEffects()) {
				if (i >= EVENT_SAVED_EFFECTS_SIZE) {
					break;
				}
				eventSavedSummonEffects[i] = effect;
				i++;
			}
		}
		eventSavedPosition = new Point3D(getPosition().getX(), getPosition().getY(), getPosition().getZ());
		eventSavedTime = TimeController.getGameTicks();

		/*for (Item temp : getInventory().getAugmentedItems())
		{
			if (temp != null && temp.isEquipped())
				removeSkill(temp.getAugmentation().getSkill());
		}*/
	}
	
	public void eventRestoreBuffs() {
		//restoreEffects();
		WorldObject[] targets = new Creature[]{this};
		for (int i = 0; i < EVENT_SAVED_EFFECTS_SIZE; i++) {
			if (eventSavedEffects[i] != null) {
				restoreBuff(eventSavedEffects[i], targets);
			}
		}
		if (getPet() != null) {
			targets = new Creature[]{getPet()};
			for (int i = 0; i < EVENT_SAVED_EFFECTS_SIZE; i++) {
				if (eventSavedSummonEffects[i] != null) {
					restoreBuff(eventSavedSummonEffects[i], targets);
				}
			}
		}
		setCurrentHp(getMaxHp());
	}
	
	private void restoreBuff(Abnormal buff, WorldObject[] targets) {
		int skillId = buff.getSkill().getId();
		int skillLvl = buff.getLevelHash();
		int effectCount = buff.getCount();
		int effectCurTime = buff.getTime() - (TimeController.getGameTicks() - eventSavedTime) / TimeController.TICKS_PER_SECOND;
		
		if (skillId == -1 || effectCount == -1 || effectCurTime < 30 || effectCurTime >= buff.getDuration()) {
			return;
		}
		
		Skill skill = SkillTable.getInstance().getInfo(skillId, skillLvl);
		ISkillHandler IHand = SkillHandler.getInstance().getSkillHandler(skill.getSkillType());
		if (IHand != null) {
			IHand.useSkill(this, skill, targets);
		} else {
			skill.useSkill(this, targets);
		}
		
		for (Abnormal effect : getAllEffects()) {
			if (effect != null && effect.getSkill() != null && effect.getSkill().getId() == skillId) {
				effect.setCount(effectCount);
				effect.setFirstTime(effectCurTime);
			}
		}
	}
	
	public long getDeathPenalty(boolean atwar) {
		// Get the level of the Player
		final int lvl = getLevel();
		
		if (lvl == 85) {
			return 0;
		}
		
		byte level = (byte) getLevel();
		
		int clan_luck = getSkillLevelHash(Skill.SKILL_CLAN_LUCK);
		
		double clan_luck_modificator = 1.0;
		switch (clan_luck) {
			case 3:
				clan_luck_modificator = 0.5;
				break;
			case 2:
				clan_luck_modificator = 0.5;
				break;
			case 1:
				clan_luck_modificator = 0.5;
				break;
			default:
				clan_luck_modificator = 1.0;
				break;
		}
		
		//The death steal you some Exp
		double percentLost = 1.0 * clan_luck_modificator;
		
		switch (level) {
			case 78:
				percentLost = 1.5 * clan_luck_modificator;
				break;
			case 77:
				percentLost = 2.0 * clan_luck_modificator;
				break;
			case 76:
				percentLost = 2.5 * clan_luck_modificator;
				break;
			default:
				if (level < 40) {
					percentLost = 7.0 * clan_luck_modificator;
				} else if (level >= 40 && level <= 75) {
					percentLost = 4.0 * clan_luck_modificator;
				}
				
				break;
		}
		
		if (getReputation() < 0) {
			percentLost *= Config.RATE_REPUTATION_EXP_LOST;
		}
		
		if (atwar || isInsideZone(ZONE_SIEGE)) {
			percentLost /= 4.0;
		}
		
		// Calculate the Experience loss
		long lostExp = 0;
		
		if (lvl < Config.MAX_LEVEL) {
			lostExp = Math.round((getStat().getExpForLevel(lvl + 1) - getStat().getExpForLevel(lvl)) * percentLost / 100);
		} else {
			lostExp = Math.round((getStat().getExpForLevel(Config.MAX_LEVEL + 1) - getStat().getExpForLevel(Config.MAX_LEVEL)) * percentLost / 100);
		}
		
		// No xp loss inside pvp zone unless
		// - it's a siege zone and you're NOT participating
		// - you're killed by a non-pc
		if (isInsideZone(ZONE_PVP)) {
			// No xp loss for siege participants inside siege zone
			if (isInsideZone(ZONE_SIEGE)) {
				if (getSiegeState() > 0) {
					lostExp = 0;
				}
			} else {
				lostExp = 0;
			}
		}
		
		// Set the new Experience value of the Player
		return lostExp;
	}
	
	public Point3D getEventSavedPosition() {
		return eventSavedPosition;
	}
	
	public void setTitleColor(String color) {
		if (color.length() > 0) {
			titleColor = color;
		}
		if (titleColor != null && titleColor.length() > 0) {
			getAppearance().setTitleColor(Integer.decode("0x" + titleColor));
		} else {
			getAppearance().setTitleColor(Integer.decode("0xFFFF77"));
		}
	}
	
	public void setTitleColor(int color) {
		setTitleColor(Integer.toHexString(color));
	}
	
	public int getTitleColor() {
		if (titleColor != null && titleColor.length() > 0) {
			return Integer.decode("0x" + titleColor);
		} else {
			return Integer.decode("0xFFFF77");
		}
	}
	
	public boolean isMobSummonRequest() {
		return mobSummonRequest;
	}
	
	public void setMobSummonRequest(boolean state, Item item) {
		mobSummonRequest = state;
		mobSummonItem = item;
	}
	
	public void mobSummonAnswer(int answer) {
		if (!mobSummonRequest) {
		} else {
			if (answer == 1) {
				confirmUseMobSummonItem(mobSummonItem);
			}
			setMobSummonRequest(false, null);
		}
	}
	
	public boolean isMobSummonExchangeRequest() {
		return mobSummonExchangeRequest;
	}
	
	public void setMobSummonExchangeRequest(boolean state, MobSummonInstance mob) {
		mobSummonExchangeRequest = state;
		//mobSummonExchange = mob;
	}
	
	public void mobSummonExchangeAnswer(int answer) {
		if (!mobSummonExchangeRequest) {
		} else {
			/*if (answer == 1 && getPet() instanceof MobSummonInstance
					&& mobSummonExchange == mobSummonExchange.getOwner().getPet()
					&& isMobSummonExchangeRequest())
			{
				((MobSummonInstance)getSummons().get(0)).exchange(mobSummonExchange);
			}
			setMobSummonExchangeRequest(false, null);*///TODO
		}
	}
	
	public void confirmUseMobSummonItem(Item item) {
		if (isSitting()) {
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CANT_MOVE_SITTING));
			return;
		}
		
		if (inObserverMode() || event != null) {
			return;
		}
		
		if (isInOlympiadMode()) {
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.THIS_ITEM_IS_NOT_AVAILABLE_FOR_THE_OLYMPIAD_EVENT));
			return;
		}
		
		if (isAllSkillsDisabled() || isCastingNow()) {
			return;
		}
		
		if (!getSummons().isEmpty()) {
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_ALREADY_HAVE_A_PET));
			return;
		}
		
		if (isAttackingNow() || isInCombat() || getPvpFlag() != 0) {
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_CANNOT_SUMMON_IN_COMBAT));
			return;
		}
		
		int mobId = item.getMobId();
		
		if (mobId == 0) {
			WorldObject target = getTarget();
			if (target instanceof MonsterInstance && !(target instanceof ArmyMonsterInstance) && !(target instanceof ChessPieceInstance) &&
					!(target instanceof EventGolemInstance) && !((MonsterInstance) target).isRaid() &&
					((MonsterInstance) target).getCollisionHeight() < 49 && ((MonsterInstance) target).getCollisionRadius() < 29) {
				MonsterInstance mob = (MonsterInstance) target;
				stopMove(null, false);
				
				WorldObject oldtarget = getTarget();
				setTarget(this);
				setHeading(Util.calculateHeadingFrom(this, mob));
				setTarget(oldtarget);
				broadcastPacket(new MagicSkillUse(this, 1050, 1, 20000, 0));
				sendPacket(new SetupGauge(0, 20000));
				sendMessage("Preparing the catch item...");
				
				MobCatchFinalizer mcf = new MobCatchFinalizer(mob, item);
				setSkillCast(ThreadPoolManager.getInstance().scheduleEffect(mcf, 20000));
				forceIsCasting(TimeController.getGameTicks() + 20000 / TimeController.MILLIS_IN_TICK);
			} else if (target instanceof MonsterInstance &&
					(((MonsterInstance) target).getCollisionHeight() >= 50 || ((MonsterInstance) target).getCollisionRadius() >= 30)) {
				sendMessage("This monster is too big!");
			} else {
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.INCORRECT_TARGET));
			}
			return;
		}
		
		NpcTemplate npcTemplate = NpcTable.getInstance().getTemplate(mobId);
		
		if (npcTemplate == null) {
			return;
		}
		
		stopMove(null, false);
		
		WorldObject oldtarget = getTarget();
		setTarget(this);
		Broadcast.toSelfAndKnownPlayers(this, new MagicSkillUse(this, 2046, 1, 5000, 0));
		setTarget(oldtarget);
		sendPacket(new SetupGauge(0, 5000));
		sendPacket(SystemMessage.getSystemMessage(SystemMessageId.SUMMON_A_PET));
		setIsCastingNow(true);
		
		ThreadPoolManager.getInstance().scheduleGeneral(new MobPetSummonFinalizer(npcTemplate, item), 5000);
	}
	
	class MobPetSummonFinalizer implements Runnable {
		private Item item;
		private NpcTemplate npcTemplate;
		
		MobPetSummonFinalizer(NpcTemplate npcTemplate, Item item) {
			this.npcTemplate = npcTemplate;
			this.item = item;
		}
		
		@Override
		public void run() {
			sendPacket(new MagicSkillLaunched(Player.this, 2046, 1));
			setIsCastingNow(false);
			MobSummonInstance summon = new MobSummonInstance(IdFactory.getInstance().getNextId(), npcTemplate, Player.this, item);
			
			summon.setName(npcTemplate.Name);
			summon.setTitle(getName());
			summon.setExpPenalty(0);
			if (summon.getLevel() > Config.MAX_LEVEL) {
				summon.getStat().setExp(Experience.getAbsoluteExp(Config.MAX_LEVEL));
				log.warn("Summon (" + summon.getName() + ") NpcID: " + summon.getNpcId() + " has a level above " + Config.MAX_LEVEL +
						". Please rectify.");
			} else {
				summon.getStat().setExp(Experience.getAbsoluteExp(summon.getLevel()));
			}
			summon.setCurrentHp(summon.getMaxHp());
			summon.setCurrentMp(summon.getMaxMp());
			summon.setHeading(getHeading());
			summon.setRunning();
			addSummon(summon);
			
			summon.spawnMe(getX() + 50, getY() + 100, getZ());
			
			if (isPlayingEvent()) {
				summon.setInsideZone(Creature.ZONE_PVP, true);
				summon.setInsideZone(Creature.ZONE_PVP, true);
			}
		}
	}
	
	class MobCatchFinalizer implements Runnable {
		private Item item;
		private MonsterInstance mob;
		
		MobCatchFinalizer(MonsterInstance mob, Item item) {
			this.item = item;
			this.mob = mob;
		}
		
		@Override
		public void run() {
			sendPacket(new MagicSkillLaunched(Player.this, 2046, 1));
			setIsCastingNow(false);
			
			if (mob.isDead()) {
				sendMessage("This monster is already dead!");
			} else {
				item.setMobId(mob.getNpcId());
				mob.onDecay();
				sendMessage("You have caught " + mob.getName() + "!!!");
			}
		}
	}
	
	public EventInstance getEvent() {
		return event;
	}
	
	public void setEvent(EventInstance event) {
		this.event = event;
	}
	
	public boolean isNoExp() {
		return noExp;
	}
	
	public void setNoExp(boolean noExp) {
		this.noExp = noExp;
	}
	
	public boolean isLandRates() {
		return landRates;
	}
	
	public void setLandRates(boolean landRates) {
		debugger = landRates ? this : null;
		this.landRates = landRates;
	}
	
	public boolean isShowingStabs() {
		return stabs;
	}
	
	public void setShowStabs(boolean stabs) {
		this.stabs = stabs;
	}
	
	private L2Spawn getNpcServitor(int id) {
		if (npcServitors[id] != null) {
			return npcServitors[id];
		}
		L2Spawn spawn = null;
		try {
			NpcTemplate tmpl;
			switch (id) {
				case 0:
					tmpl = NpcTable.getInstance().getTemplate(40001);
					break;
				case 1:
					tmpl = NpcTable.getInstance().getTemplate(40002);
					break;
				case 2:
					tmpl = NpcTable.getInstance().getTemplate(40003);
					break;
				default:
					tmpl = NpcTable.getInstance().getTemplate(40005);
			}
			spawn = new L2Spawn(tmpl);
		} catch (Exception e) {
			e.printStackTrace();
		}
		npcServitors[id] = spawn;
		return npcServitors[id];
	}
	
	public void spawnServitors() {
		InstanceManager.getInstance().createInstance(getObjectId());
		L2Spawn servitor;
		float angle = Rnd.get(1000);
		int sCount = 4;
		if (Config.isServer(Config.TENKAI_LEGACY)) {
			MainTownInfo currentTown = MainTownManager.getInstance().getCurrentMainTown();
			TownZone townZone = TownManager.getTown(currentTown.getTownId());
			if (!townZone.isCharacterInZone(this)) {
				sCount = 2;
			}
		}
		for (int i = 0; i < sCount; i++) {
			servitor = getNpcServitor(i);
			if (servitor != null) {
				servitor.setInstanceId(getObjectId());
				servitor.setX(Math.round(getX() + (float) Math.cos(angle / 1000 * 2 * Math.PI) * 30));
				servitor.setY(Math.round(getY() + (float) Math.sin(angle / 1000 * 2 * Math.PI) * 30));
				servitor.setZ(getZ() + 75);
				int heading = (int) Math.round(Math.atan2(getY() - servitor.getY(), getX() - servitor.getX()) / Math.PI * 32768);
				if (heading < 0) {
					heading = 65535 + heading;
				}
				servitor.setHeading(heading);
				
				if (InstanceManager.getInstance().getInstance(getObjectId()) != null) {
					servitor.doSpawn();
				}
			}
			angle += 1000 / sCount;
		}
	}
	
	private void onEventTeleported() {
		if (isPlayingEvent()) {
			if (event.isType(EventType.VIP) && event.getParticipantTeam(getObjectId()) != null &&
					event.getParticipantTeam(getObjectId()).getVIP() != null &&
					event.getParticipantTeam(getObjectId()).getVIP().getObjectId() == getObjectId()) {
				event.setImportant(this, true);
			} else {
				event.setImportant(this, false);
			}
			
			NpcBufferInstance.buff(this);
			setCurrentCp(getMaxCp());
			setCurrentHp(getMaxHp());
			setCurrentMp(getMaxMp());
			
			for (SummonInstance summon : summons) {
				NpcBufferInstance.buff(summon);
				summon.setCurrentCp(summon.getMaxCp());
				summon.setCurrentHp(summon.getMaxHp());
				summon.setCurrentMp(summon.getMaxMp());
				
				if (summon instanceof MobSummonInstance) {
					summon.unSummon(this);
				}
			}
			
			setProtection(!event.isType(EventType.StalkedSalkers));
			
			broadcastStatusUpdate();
			broadcastUserInfo();
			
			if (dwEquipped) {
				getInventory().unEquipItemInBodySlot(ItemTemplate.SLOT_LR_HAND);
			}
		} else if (wasInEvent) {
			setTeam(0);
			getAppearance().setNameColor(Integer.decode("0xFFFFFF"));
			setTitleColor("");
			setIsEventDisarmed(false);
			
			int i = 0;
			while (isInsideZone(Creature.ZONE_PVP) && i < 100) {
				setInsideZone(Creature.ZONE_PVP, false);
				i++;
			}
			eventRestoreBuffs();
			
			removeSkill(9940);
			stopVisualEffect(VisualEffect.S_AIR_STUN);
			
			broadcastStatusUpdate();
			broadcastUserInfo();
			
			wasInEvent = false;
		}
		setIsCastingNow(false);
	}
	
	public void returnedFromEvent() {
		wasInEvent = true;
	}
	
	public void enterEventObserverMode(int x, int y, int z) {
		if (getPet() != null) {
			getPet().unSummon(this);
		}
		for (SummonInstance summon : getSummons()) {
			summon.unSummon(this);
		}
		
		if (!getCubics().isEmpty()) {
			for (CubicInstance cubic : getCubics().values()) {
				cubic.stopAction();
				cubic.cancelDisappear();
			}
			
			getCubics().clear();
		}
		
		if (isSitting()) {
			standUp();
		}
		
		lastX = getX();
		lastY = getY();
		lastZ = getZ();
		
		observerMode = true;
		setTarget(null);
		setIsInvul(true);
		getAppearance().setInvisible();
		teleToLocation(x, y, z, false);
		sendPacket(new ExOlympiadMode(3));
		
		broadcastUserInfo();
	}
	
	public void leaveEventObserverMode() {
		setTarget(null);
		sendPacket(new ExOlympiadMode(0));
		setInstanceId(0);
		teleToLocation(lastX, lastY, lastZ, true);
		if (!AdminCommandAccessRights.getInstance().hasAccess("admin_invis", getAccessLevel())) {
			getAppearance().setVisible();
		}
		if (!AdminCommandAccessRights.getInstance().hasAccess("admin_invul", getAccessLevel())) {
			setIsInvul(false);
		}
		if (getAI() != null) {
			getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		}
		
		observerMode = false;
		broadcastUserInfo();
	}
	
	public boolean hadStoreActivity() {
		return hadStoreActivity;
	}
	
	public void hasBeenStoreActive() {
		if (!hadStoreActivity) {
			hadStoreActivity = true;
		}
	}
	
	class StalkerHintsTask implements Runnable {
		boolean stop = false;
		int level = 0;
		
		@Override
		public void run() {
			if (!stop && level < 4 && isPlayingEvent() && getEvent() instanceof StalkedStalkers) {
				level++;
				((StalkedStalkers) getEvent()).stalkerMessage(Player.this, level);
				int nextHintTimer = 0;
				switch (level) {
					case 1:
						nextHintTimer = 15000;
						break;
					case 2:
						nextHintTimer = 30000;
						break;
					case 3:
						nextHintTimer = 45000;
						break;
					case 4:
						nextHintTimer = 60000;
						break;
				}
				
				if (nextHintTimer > 0) {
					ThreadPoolManager.getInstance().scheduleGeneral(this, nextHintTimer);
				}
			}
		}
		
		public void stop() {
			stop = true;
		}
	}
	
	public void startStalkerHintsTask() {
		if (stalkerHintsTask != null) {
			stalkerHintsTask.stop();
		}
		
		stalkerHintsTask = new StalkerHintsTask();
		ThreadPoolManager.getInstance().scheduleGeneral(stalkerHintsTask, 5000);
	}
	
	public boolean isChessChallengeRequest() {
		return chessChallengeRequest;
	}
	
	public void setChessChallengeRequest(boolean state, Player challenger) {
		chessChallengeRequest = state;
		chessChallenger = challenger;
	}
	
	public void chessChallengeAnswer(int answer) {
		if (!chessChallengeRequest) {
		} else {
			if (answer == 1 && isChessChallengeRequest() && !ChessEvent.isState(ChessState.STARTED)) {
				ChessEvent.startFight(chessChallenger, this);
			}
			setChessChallengeRequest(false, null);
		}
	}
	
	public boolean isCastingProtected() {
		return isCastingNow() && getLastSkillCast() != null && (getLastSkillCast().getTargetType() == SkillTargetType.TARGET_HOLY ||
				getLastSkillCast().getTargetType() == SkillTargetType.TARGET_FLAGPOLE || getLastSkillCast().getId() == 1050);
	}
	
	public boolean hasReceivedImage(int id) {
		return receivedImages.contains(id);
	}
	
	public void setReceivedImage(int id) {
		receivedImages.add(id);
	}
	
	public void scheduleEffectRecovery(Abnormal effect, int seconds, boolean targetWasInOlys) {
		//if (Config.isServer(Config.PVP) || Config.isServer(Config.CRAFT))
		//	ThreadPoolManager.getInstance().scheduleGeneral(new EffectRecoveryTask(effect, targetWasInOlys), seconds * 1000L);
	}
	
	@SuppressWarnings("unused")
	private class EffectRecoveryTask implements Runnable {
		private Abnormal effect;
		private int effectSavedTime;
		boolean targetWasInOlys;
		
		public EffectRecoveryTask(Abnormal effect, boolean targetWasInOlys) {
			this.effect = effect;
			effectSavedTime = TimeController.getGameTicks();
			this.targetWasInOlys = targetWasInOlys;
		}
		
		@Override
		public void run() {
			if (!targetWasInOlys && isInOlympiadMode()) {
				return;
			}
			
			WorldObject[] targets = new Creature[]{Player.this};
			if (effect != null) {
				int skillId = effect.getSkill().getId();
				int skillLvl = effect.getLevel();
				int effectCount = effect.getCount();
				int effectCurTime = effect.getTime() - (TimeController.getGameTicks() - effectSavedTime) / TimeController.TICKS_PER_SECOND;
				
				if (skillId == -1 || effectCount == -1 || effectCurTime < 30 || effectCurTime >= effect.getDuration()) {
					return;
				}
				
				Skill skill = SkillTable.getInstance().getInfo(skillId, skillLvl);
				ISkillHandler IHand = SkillHandler.getInstance().getSkillHandler(skill.getSkillType());
				if (IHand != null) {
					IHand.useSkill(Player.this, skill, targets);
				} else {
					skill.useSkill(Player.this, targets);
				}
				
				for (Abnormal effect : getAllEffects()) {
					if (effect != null && effect.getSkill() != null && effect.getSkill().getId() == skillId) {
						effect.setCount(effectCount);
						effect.setFirstTime(effectCurTime);
					}
				}
			}
		}
	}
	
	public int getStrenghtPoints(boolean randomize) {
		final int levelI = 600;
		final int weaponI = 200;
		final int chestI = 50;
		final int miscI = 14;
		final int jewelI = 16;
		
		int strenghtPoints = getExpertiseIndex() * levelI + (getLevel() - EXPERTISE_LEVELS[getExpertiseIndex()]) * levelI /
				((getExpertiseIndex() < 7 ? EXPERTISE_LEVELS[getExpertiseIndex() + 1] : 86) - EXPERTISE_LEVELS[getExpertiseIndex()]);
		
		int[] bestItems = new int[14];
		for (Item item : getInventory().getItems()) {
			if (item == null) {
				continue;
			}
			int influence, index;
			switch (item.getItem().getBodyPart()) {
				case ItemTemplate.SLOT_L_HAND:
				case ItemTemplate.SLOT_R_HAND:
				case ItemTemplate.SLOT_LR_HAND:
					influence = weaponI;
					index = 0;
					break;
				case ItemTemplate.SLOT_CHEST:
				case ItemTemplate.SLOT_FULL_ARMOR:
					influence = chestI;
					index = 1;
					break;
				case ItemTemplate.SLOT_LEGS:
					influence = miscI;
					index = 2;
					break;
				case ItemTemplate.SLOT_HEAD:
					influence = miscI;
					index = 3;
					break;
				case ItemTemplate.SLOT_GLOVES:
					influence = miscI;
					index = 4;
					break;
				case ItemTemplate.SLOT_FEET:
					influence = miscI;
					index = 5;
					break;
				case ItemTemplate.SLOT_UNDERWEAR:
					influence = miscI;
					index = 6;
					break;
				case ItemTemplate.SLOT_BACK:
					influence = miscI;
					index = 7;
					break;
				case ItemTemplate.SLOT_BELT:
					influence = miscI;
					index = 8;
					break;
				case ItemTemplate.SLOT_NECK:
					influence = jewelI;
					index = 9;
					break;
				case ItemTemplate.SLOT_R_EAR:
					influence = jewelI;
					index = 10;
					break;
				case ItemTemplate.SLOT_L_EAR:
					influence = jewelI;
					index = 11;
					break;
				case ItemTemplate.SLOT_R_FINGER:
					influence = jewelI;
					index = 12;
					break;
				case ItemTemplate.SLOT_L_FINGER:
					influence = jewelI;
					index = 13;
					break;
				default:
					continue;
			}
			
			// Check if the item has higher grade than user and it is not equipped. If so, don't count it.
			if (item.getItem().getCrystalType() > getExpertiseIndex() && !item.isEquipped()) {
				continue;
			}
			
			int str = Math.min(item.getItem().getCrystalType() + 1, getExpertiseIndex() + 1) * influence + item.getEnchantLevel() * influence / 10;
			if (str > bestItems[index]) {
				bestItems[index] = str;
			}
		}
		
		for (int str : bestItems) {
			strenghtPoints += str;
		}
		
		for (Skill skill : getAllSkills()) {
			if (skill.getEnchantRouteId() > 0) {
				strenghtPoints += skill.getEnchantLevel() * skill.getEnchantLevel() / 10;
			}
		}
		
		int rand = randomize ? Rnd.get(3) : 0;
		
		return strenghtPoints * (10 + rand);
	}
	
	/**
	 * Call this when a summon dies to store its buffs if it had noblesse
	 */
	public void storeSummonBuffs(Abnormal[] effects) {
		// Store buffs
		summonBuffs = effects;
		
		if (pet != null) {
			// Only for the same summon buffs shall be restored
			lastSummonId = pet.getNpcId();
		}
	}
	
	/**
	 * Resturns collection of buffs which are possibly stored from last summon
	 */
	public Abnormal[] restoreSummonBuffs() {
		return summonBuffs;
	}
	
	/**
	 * Returns npc id of last summon
	 */
	public int getLastSummonId() {
		return lastSummonId;
	}
	
	public void setIsEventDisarmed(boolean disarmed) {
		eventDisarmed = disarmed;
	}
	
	public boolean isEventDisarmed() {
		return eventDisarmed;
	}
	
	@Override
	public boolean isDisarmed() {
		return super.isDisarmed() || eventDisarmed;
	}
	
	private int lastCheckedAwakeningClassId = 0;
	
	public int getLastCheckedAwakeningClassId() {
		return lastCheckedAwakeningClassId;
	}
	
	public void setLastCheckedAwakeningClassId(int classId) {
		lastCheckedAwakeningClassId = classId;
	}
	
	private L2FlyMove flyMove = null;
	private L2FlyMoveChoose flyMoveChoose = null;
	private int flyMoveEndAt = -1;
	private int flyMoveLast = -1;
	
	public void startFlyMove() {
		if (flyMove == null) {
			return;
		}
		
		flyMoveChoose = null;
		flyMoveEndAt = -1;
		flyMoveLast = -1;
		
		disableAllSkills();
		abortAttack();
		abortCast();
		getAI().clientStopAutoAttack();
		getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		
		flyMoveStep(0);
	}
	
	public void flyMoveStep(int stepId) {
		if (flyMove == null) {
			return;
		}
		
		if (stepId == -1) {
			setFlyMove(null);
			return;
		}
		
		L2FlyMoveChoose choose = flyMove.getChoose(stepId);
		ExFlyMove fm;
		ExFlyMoveBroadcast fmb;
		if (choose != null && choose.getOptions().size() > 0 && choose.getAt() == stepId) {
			flyMoveChoose = choose;
			Map<Integer, Point3D> options = new HashMap<>();
			for (L2FlyMoveOption o : flyMoveChoose.getOptions()) {
				options.put(o.getStart(), flyMove.getStep(o.getStart()));
			}
			fm = new ExFlyMove(this, flyMove.getId(), options);
			fmb = new ExFlyMoveBroadcast(this, options.keySet().contains(-1));
			flyMoveEndAt = -1;
			flyMoveLast = -1;
		} else {
			if (flyMoveEndAt == -1) {
				if (flyMoveChoose != null) {
					for (L2FlyMoveOption o : flyMoveChoose.getOptions()) {
						if (stepId == o.getStart()) {
							flyMoveEndAt = o.getEnd();
							flyMoveLast = o.getLast();
						}
					}
				} else {
					flyMoveEndAt = 1000;
				}
			}
			
			if (!(stepId == flyMoveEndAt && stepId == flyMoveLast)) {
				stepId++;
			}
			
			if (stepId > flyMoveEndAt) {
				if (flyMoveLast == -1 || stepId > flyMoveLast) {
					flyMove = null;
					flyMoveChoose = null;
					flyMoveEndAt = -1;
					enableAllSkills();
					return;
				}
				stepId = flyMoveLast;
			}
			
			Point3D step = flyMove.getStep(stepId);
			
			if (step == null) {
				return;
			}
			
			boolean last = stepId == flyMoveEndAt && flyMoveLast == -1 || stepId == flyMoveLast;
			if (last) {
				stepId = -1;
			}
			
			fm = new ExFlyMove(this, flyMove.getId(), stepId, step.getX(), step.getY(), step.getZ());
			fmb = new ExFlyMoveBroadcast(this, step.getX(), step.getY(), step.getZ());
			
			if (last) {
				flyMove = null;
				flyMoveChoose = null;
				enableAllSkills();
			}
		}
		
		sendPacket(fm);
		fmb.setInvisibleCharacter(getAppearance().getInvisible() ? getObjectId() : 0);
		for (Player player : getKnownList().getKnownPlayers().values()) {
			if (player == null) {
				continue;
			}
			
			player.sendPacket(fmb);
		}
	}
	
	public void setFlyMove(L2FlyMove move) {
		if (move == null) {
			enableAllSkills();
			flyMoveChoose = null;
		}
		
		flyMove = move;
	}
	
	public boolean isPerformingFlyMove() {
		return flyMove != null;
	}
	
	public boolean isChoosingFlyMove() {
		return flyMoveChoose != null;
	}
	
	private Map<GlobalQuest, Integer> mainQuestsState = new HashMap<>();
	
	private void setGlobalQuestState(GlobalQuest quest, int state) {
		mainQuestsState.put(quest, state);
		
		Connection con = null;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement;
			statement = con.prepareStatement("REPLACE INTO character_quest_global_data (charId,var,value) VALUES (?,?,?)");
			statement.setInt(1, getObjectId());
			statement.setString(2, "GlobalQuest" + quest.ordinal());
			statement.setInt(3, state);
			statement.executeUpdate();
			statement.close();
		} catch (Exception e) {
			log.warn("Could not insert player's global quest variable: " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
		
		sendPacket(new QuestList());
	}
	
	public int getGlobalQuestState(GlobalQuest quest) {
		if (mainQuestsState.containsKey(quest)) {
			return mainQuestsState.get(quest);
		}
		
		Connection con = null;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement;
			statement = con.prepareStatement("SELECT value FROM character_quest_global_data WHERE charId = ? AND var = ?");
			statement.setInt(1, getObjectId());
			statement.setString(2, "GlobalQuest" + quest.ordinal());
			ResultSet rs = statement.executeQuery();
			if (rs.first()) {
				mainQuestsState.put(quest, rs.getInt(1));
			} else {
				mainQuestsState.put(quest, 0);
			}
			rs.close();
			statement.close();
		} catch (Exception e) {
			log.warn("Could not load player's global quest variable: " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
		
		return mainQuestsState.get(quest);
	}
	
	public void setGlobalQuestFlag(GlobalQuest quest, int order, boolean value) {
		if (order < 1) {
			return;
		}
		
		int state = getGlobalQuestState(quest);
		int flag = 1 << order - 1;
		
		if (value) {
			state |= flag;
		} else {
			state &= ~flag;
		}
		
		setGlobalQuestState(quest, state);
	}
	
	public void setGlobalQuestFlag(GlobalQuest quest, int order) {
		setGlobalQuestFlag(quest, order, true);
	}
	
	public boolean getGlobalQuestFlag(GlobalQuest quest, int order) {
		if (order < 1) {
			return false;
		}
		
		int state = getGlobalQuestState(quest);
		int flag = 1 << order - 1;
		
		return (state & flag) == flag;
	}
	
	private int vitalityItemsUsed = -1;
	
	public int getVitalityItemsUsed() {
		if (vitalityItemsUsed == -1) {
			Connection con = null;
			try {
				con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement statement = con.prepareStatement("SELECT value FROM account_gsdata WHERE account_name=? AND var=?");
				statement.setString(1, getAccountName());
				statement.setString(2, "vit_items_used");
				ResultSet rs = statement.executeQuery();
				if (rs.next()) {
					vitalityItemsUsed = Integer.parseInt(rs.getString("value"));
				} else {
					statement.close();
					statement = con.prepareStatement("INSERT INTO account_gsdata(account_name,var,value) VALUES(?,?,?)");
					statement.setString(1, getAccountName());
					statement.setString(2, "vit_items_used");
					statement.setString(3, String.valueOf(0));
					statement.execute();
				}
				rs.close();
				statement.close();
			} catch (Exception e) {
				log.warn("Could not load player vitality items used count: " + e.getMessage(), e);
			} finally {
				L2DatabaseFactory.close(con);
			}
		}
		return vitalityItemsUsed;
	}
	
	public void increaseVitalityItemsUsed() {
		Connection con = null;
		if (vitalityItemsUsed > 5) {
			vitalityItemsUsed = 5;
		}
		vitalityItemsUsed++;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("UPDATE account_gsdata SET value=? WHERE account_name=? AND var=?");
			statement.setString(1, String.valueOf(vitalityItemsUsed));
			statement.setString(2, getAccountName());
			statement.setString(3, "vit_items_used");
			statement.execute();
			statement.close();
		} catch (Exception e) {
			log.info("Could not store player vitality items used count: " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	private int elementalStance = 0;
	
	public int getElementalStance() {
		return elementalStance;
	}
	
	public void setElementalStance(int stanceId) {
		elementalStance = stanceId;
	}
	
	public int getMaxSummonPoints() {
		return (int) getStat().calcStat(Stats.SUMMON_POINTS, 0, null, null);
	}
	
	public int getSpentSummonPoints() {
		int spentSummonPoints = 0;
		for (SummonInstance summon : getSummons()) {
			spentSummonPoints += summon.getSummonPoints();
		}
		
		return spentSummonPoints;
	}
	
	private Item currentUnbindScroll = null;
	
	public void setCurrentUnbindScroll(Item scroll) {
		currentUnbindScroll = scroll;
	}
	
	public Item getCurrentUnbindScroll() {
		return currentUnbindScroll;
	}
	
	private Item currentBlessingScroll = null;
	
	public void setCurrentBlessingScroll(Item scroll) {
		currentBlessingScroll = scroll;
	}
	
	public Item getCurrentBlessingScroll() {
		return currentBlessingScroll;
	}
	
	private boolean canEscape = true;
	
	public void setCanEscape(boolean canEscape) {
		this.canEscape = canEscape;
	}
	
	public boolean canEscape() {
		if (calcStat(Stats.BLOCK_ESCAPE, 0, this, null) > 0) {
			return false;
		}
		
		return canEscape;
	}
	
	private boolean hasLoaded = false;
	
	public void hasLoaded() {
		hasLoaded = true;
	}
	
	public boolean isAlly(Creature target) {
		return target instanceof Player &&
				(getParty() != null && target.getParty() == getParty() || getClanId() != 0 && ((Player) target).getClanId() == getClanId() ||
						getAllyId() != 0 && ((Player) target).getAllyId() == getAllyId()
						/*|| target instanceof ApInstance*/);
	}
	
	public boolean isEnemy(Creature target) {
		return /*(target instanceof MonsterInstance && target.isInCombat())
				||*/target instanceof Player && target.isAutoAttackable(this) &&
				(getAllyId() == 0 || ((Player) target).getAllyId() != getAllyId()) && !target.isInsideZone(Creature.ZONE_SIEGE) &&
				!((Player) target).getAppearance().getInvisible()
				/*&& !(target instanceof ApInstance)*/
				/*|| (target instanceof ApInstance && !target.isInsidePeaceZone(this))*/;
	}
	
	private Item appearanceStone;
	
	public void setActiveAppearanceStone(Item stone) {
		appearanceStone = stone;
	}
	
	public Item getActiveAppearanceStone() {
		return appearanceStone;
	}
	
	boolean isSearchingForParty = false;
	
	public boolean isSearchingForParty() {
		return isSearchingForParty;
	}
	
	Player playerForChange;
	
	public Player getPlayerForChange() {
		return playerForChange;
	}
	
	public void setPlayerForChange(Player playerForChange) {
		this.playerForChange = playerForChange;
	}
	
	public void showWaitingSubstitute() {
		sendPacket(SystemMessageId.YOU_ARE_REGISTERED_ON_THE_WAITING_LIST);
		PartySearchManager.getInstance().addLookingForParty(this);
		isSearchingForParty = true;
		sendPacket(new ExWaitWaitingSubStituteInfo(true));
	}
	
	public void closeWaitingSubstitute() {
		sendPacket(SystemMessageId.STOPPED_SEARCHING_THE_PARTY);
		PartySearchManager.getInstance().removeLookingForParty(this);
		isSearchingForParty = false;
		sendPacket(new ExWaitWaitingSubStituteInfo(false));
	}
	
	public ArrayList<Integer> menteeList = new ArrayList<>();
	
	public List<Integer> getMenteeList() {
		return menteeList;
	}
	
	public void restoreMenteeList() {
		menteeList.clear();
		
		Connection con = null;
		
		try {
			String sqlQuery = "SELECT menteeId FROM character_mentees WHERE charId=?";
			
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(sqlQuery);
			statement.setInt(1, getObjectId());
			ResultSet rset = statement.executeQuery();
			
			int menteeId;
			while (rset.next()) {
				menteeId = rset.getInt("menteeId");
				if (menteeId == getObjectId()) {
					continue;
				}
				menteeList.add(menteeId);
			}
			
			rset.close();
			statement.close();
		} catch (Exception e) {
			log.warn("Error found in " + getName() + "'s MenteeList: " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	public boolean isMentee() {
		return getMentorId() != 0;
	}
	
	public boolean canBeMentor() {
		return (getBaseClassLevel() >= 86 || getBaseClassLevel() >= 85 && !isMentee()) && getBaseClass() >= 139;
	}
	
	public boolean isMentor() {
		return !menteeList.isEmpty();
	}
	
	int mentorId;
	
	public int getMentorId() {
		return mentorId;
	}
	
	public void setMentorId(int mentorId) {
		this.mentorId = mentorId;
	}
	
	public boolean restoreMentorInfo() {
		int charId = 0;
		Connection con = null;
		
		try {
			String sqlQuery = "SELECT charId FROM character_mentees WHERE menteeId=?";
			
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(sqlQuery);
			statement.setInt(1, getObjectId());
			ResultSet rset = statement.executeQuery();
			
			while (rset.next()) {
				charId = rset.getInt("charId");
				if (charId == getObjectId()) {
				}
			}
			
			rset.close();
			statement.close();
		} catch (Exception e) {
			log.warn("Error found in " + getName() + "'s MenteeList: " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
		if (charId != 0) {
			setMentorId(charId);
			return true;
		}
		setMentorId(0);
		return false;
	}
	
	public void removeMentor() {
		SystemMessage sm;
		Connection con = null;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement;
			statement = con.prepareStatement("DELETE FROM character_mentees WHERE (charId=? AND menteeId=?)");
			statement.setInt(1, getMentorId());
			statement.setInt(2, getObjectId());
			statement.execute();
			statement.close();
			
			// Mentee cancelled mentoring with mentor
			sm =
					SystemMessage.getSystemMessage(SystemMessageId.YOU_REACHED_LEVEL_86_SO_THE_MENTORING_RELATIONSHIP_WITH_YOUR_MENTOR_S1_CAME_TO_AN_END);
			sm.addString(CharNameTable.getInstance().getNameById(getMentorId()));
			sendPacket(sm);
			
			if (World.getInstance().getPlayer(getMentorId()) != null) {
				Player player = World.getInstance().getPlayer(getMentorId());
				sm = SystemMessage.getSystemMessage(SystemMessageId.THE_MENTEE_S1_REACHED_LEVEL_86_SO_THE_MENTORING_RELATIONSHIP_WAS_ENDED);
				sm.addString(getName());
				player.sendPacket(sm);
				player.sendPacket(new ExMentorList(player));
			}
			sendPacket(new ExMentorList(this));
		} catch (Exception e) {
			log.warn("could not delete mentees mentor: ", e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	public void giveMenteeBuffs() {
		ArrayList<Integer> alreadyAddedBuffs = new ArrayList<>();
		for (Abnormal e : getAllEffects()) {
			if (e.getSkill().getId() >= 9227 && e.getSkill().getId() <= 9233) {
				alreadyAddedBuffs.add(e.getSkill().getId());
			}
		}
		for (int i = 9227; i < 9234; i++) {
			if (!alreadyAddedBuffs.contains(i)) {
				SkillTable.getInstance().getInfo(i, 1).getEffects(this, this);
			}
		}
	}
	
	public void giveMentorBuff() {
		if (!isOnline() || getCurrentHp() < 1) {
			return;
		}
		int menteeOnline = 0;
		for (int objectId : getMenteeList()) {
			if (World.getInstance().getPlayer(objectId) != null && World.getInstance().getPlayer(objectId).isOnline()) {
				World.getInstance().getPlayer(objectId).giveMenteeBuffs();
				menteeOnline++;
			}
		}
		
		for (Abnormal e : getAllEffects()) {
			if (e.getSkill().getId() == 9256) {
				if (menteeOnline > 0) {
					return;
				} else {
					e.exit();
				}
			}
		}
		if (menteeOnline > 0 && menteeOnline < 4 && getBaseClass() == getClassId()) {
			SkillTable.getInstance().getInfo(9256, 1).getEffects(this, this);
		}
	}
	
	public void giveMentorSkills() {
		for (int i = 9376; i < 9379; i++) {
			Skill s = SkillTable.getInstance().getInfo(i, 1);
			addSkill(s, false); //Dont Save Mentor skills to database
		}
	}
	
	public void giveMenteeSkills() {
		Skill s = SkillTable.getInstance().getInfo(9379, 1);
		addSkill(s, false); //Dont Save Mentee skill to database
	}
	
	int activeForcesCount = 0;
	
	public int getActiveForcesCount() {
		return activeForcesCount;
	}
	
	public void setActiveForcesCount(int activeForcesCount) {
		this.activeForcesCount = activeForcesCount;
	}
	
	public String getExternalIP() {
		if (publicIP == null) {
			if (getClient() != null && getClient().getConnection() != null) {
				publicIP = getClient().getConnection().getInetAddress().getHostAddress();
			} else {
				return "";
			}
		}
		return publicIP;
	}
	
	public String getInternalIP() {
		if (internalIP == null) {
			if (getClient() != null && getClient().getTrace() != null) {
				internalIP = getClient().getTrace()[0][0] + "." + getClient().getTrace()[0][1] + "." + getClient().getTrace()[0][2] + "." +
						getClient().getTrace()[0][3];
			} else {
				return "";
			}
		}
		return internalIP;
	}
	
	public String getHWID() {
		if (getClient() == null || getClient().getHWId() == null) {
			return "";
		}
		
		return getClient().getHWId();
	}
	
	public long getOnlineTime() {
		return onlineTime;
	}
	
	public void storeCharFriendMemos() {
		Connection con = null;
		for (Map.Entry<Integer, String> friendMemo : friendMemo.entrySet()) {
			try {
				con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement statement =
						con.prepareStatement("UPDATE character_friends SET memo=? WHERE charId=? AND friendId=? AND relation=0");
				statement.setString(1, friendMemo.getValue());
				statement.setInt(2, getObjectId());
				statement.setInt(3, friendMemo.getKey());
				statement.execute();
				
				statement.close();
			} catch (Exception e) {
				log.warn(
						"Could not update character(" + getObjectId() + ") friend(" + friendMemo.getKey() + ") memo: " + e.getMessage(),
						e);
			} finally {
				L2DatabaseFactory.close(con);
			}
		}
		for (Map.Entry<Integer, String> blockMemo : blockMemo.entrySet()) {
			try {
				con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement statement =
						con.prepareStatement("UPDATE character_friends SET memo=? WHERE charId=? AND friendId=? AND relation=1");
				statement.setString(1, blockMemo.getValue());
				statement.setInt(2, getObjectId());
				statement.setInt(3, blockMemo.getKey());
				statement.execute();
				
				statement.close();
			} catch (Exception e) {
				log.warn(
						"Could not update character(" + getObjectId() + ") block(" + blockMemo.getKey() + ") memo: " + e.getMessage(),
						e);
			} finally {
				L2DatabaseFactory.close(con);
			}
		}
	}
	
	private ScheduledFuture<?>[] mezResistTasks = new ScheduledFuture<?>[2];
	private int[] mezResistLevels = new int[2];
	
	@Override
	public float getMezMod(final int type) {
		if (type == -1) {
			return 1.0f;
		}
		
		float result;
		switch (mezResistLevels[type]) {
			case 0:
				result = 1.0f;
				break;
			case 1:
				result = 0.6f;
				break;
			case 2:
				result = 3.0f;
				break;
			default:
				result = 0.0f;
		}
		
		return result;
	}
	
	@Override
	public void increaseMezResist(final int type) {
		if (type == -1) {
			return;
		}
		
		if (mezResistLevels[type] < 3) {
			if (mezResistTasks[type] != null) {
				mezResistTasks[type].cancel(false);
			}
			
			mezResistTasks[type] = ThreadPoolManager.getInstance().scheduleEffect(() -> {
				mezResistLevels[type] = 0;
				mezResistTasks[type] = null;
			}, 15000L);
		}
		
		mezResistLevels[type]++;
	}
	
	@Override
	public int getMezType(AbnormalType type) {
		if (type == null || getClassId() < 139) {
			return -1;
		}
		
		int mezType = -1;
		switch (type) {
			case STUN:
			case PARALYZE:
			case KNOCK_BACK:
			case KNOCK_DOWN:
			case HOLD:
			case DISARM:
				mezType = 0;
				break;
			case SLEEP:
			case MUTATE:
			case FEAR:
			case LOVE:
			case AERIAL_YOKE:
			case SILENCE:
				mezType = 1;
				break;
		}
		
		return mezType;
	}
	
	public boolean getIsSummonsInDefendingMode() {
		return summonsInDefendingMode;
	}
	
	public void setIsSummonsInDefendingMode(boolean a) {
		summonsInDefendingMode = a;
	}
	
	public TradeList getCustomSellList() {
		if (customSellList == null) {
			customSellList = new TradeList(this);
		}
		
		return customSellList;
	}
	
	public void setIsAddSellItem(boolean isSellItem) {
		isAddSellItem = isSellItem;
	}
	
	public boolean isAddSellItem() {
		return isAddSellItem;
	}
	
	public void setAddSellPrice(int addSellPrice) {
		this.addSellPrice = addSellPrice;
	}
	
	public int getAddSellPrice() {
		return addSellPrice;
	}
	
	public int getCubicMastery() {
		int cubicMastery = getSkillLevelHash(143); //Cubic Mastery
		
		if (cubicMastery < 0) {
			cubicMastery = getSkillLevelHash(10075); //Superior Cubic Mastery
			
			if (cubicMastery > 0) {
				cubicMastery = 2;
			}
		}
		
		if (cubicMastery < 0) {
			cubicMastery = 0;
		}
		
		return cubicMastery;
	}
	
	public boolean getIsRefusalKillInfo() {
		return getConfigValue("isRefusalKillInfo");
	}
	
	public void setIsRefusalKillInfo(boolean b) {
		setConfigValue("isRefusalKillInfo", b);
	}
	
	public boolean getIsInsideGMEvent() {
		return getConfigValue("isInsideGMEvent");
	}
	
	public void setIsInsideGMEvent(boolean b) {
		setConfigValue("isInsideGMEvent", b);
	}
	
	public void setIsOfflineBuffer(boolean b) {
		setConfigValue("isOfflineBuffer", b);
	}
	
	public boolean getIsOfflineBuffer() {
		return getConfigValue("isOfflineBuffer");
	}
	
	public boolean getIsWeaponGlowDisabled() {
		return getConfigValue("isWeaponGlowDisabled");
	}
	
	public void setIsWeaponGlowDisabled(boolean b) {
		setConfigValue("isWeaponGlowDisabled", b);
		broadcastUserInfo();
	}
	
	public boolean getIsArmorGlowDisabled() {
		return getConfigValue("isArmorGlowDisabled");
	}
	
	public void setIsArmorGlowDisabled(boolean b) {
		setConfigValue("isArmorGlowDisabled", b);
		broadcastUserInfo();
	}
	
	public boolean getIsRefusingRequests() {
		return getConfigValue("isNoRequests");
	}
	
	public void setIsRefusingRequests(boolean b) {
		setConfigValue("isNoRequests", b);
	}
	
	public boolean isNickNameWingsDisabled() {
		return getConfigValue("isNickNameWingsDisabled");
	}
	
	public void setNickNameWingsDisabled(boolean b) {
		setConfigValue("isNickNameWingsDisabled", b);
		broadcastUserInfo();
	}
	
	public boolean isCloakHidden() {
		return getConfigValue("isCloakHidden");
	}
	
	public void setCloakHidden(boolean b) {
		setConfigValue("isCloakHidden", b);
		broadcastUserInfo();
	}
	
	private void restoreConfigs() {
		playerConfigs.clear();
		Connection con = null;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement;
			statement = con.prepareStatement("SELECT var, value FROM character_quest_global_data WHERE charId = ? AND var LIKE 'Config_%'");
			statement.setInt(1, getObjectId());
			ResultSet rs = statement.executeQuery();
			while (rs.next()) {
				String var = rs.getString("var").substring(7);
				boolean val = rs.getBoolean("value");
				playerConfigs.put(var, val);
			}
			
			rs.close();
			statement.close();
		} catch (Exception e) {
			log.warn("Could not load player's global quest variable: " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	public void setConfigValue(String config, boolean b) {
		playerConfigs.put(config, b);
		
		Connection con = null;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement;
			statement = con.prepareStatement("REPLACE INTO character_quest_global_data (charId,var,value) VALUES (?,?,?)");
			statement.setInt(1, getObjectId());
			statement.setString(2, "Config_" + config);
			statement.setBoolean(3, b);
			statement.executeUpdate();
			statement.close();
		} catch (Exception e) {
			log.warn("Could not insert player's global quest variable: " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	public boolean getConfigValue(String config) {
		if (playerConfigs.containsKey(config)) {
			return playerConfigs.get(config);
		}
		
		return false;
	}
	
	public String getShopMessage() {
		if (!isInStoreMode()) {
			return "No Message";
		}
		
		switch (getPrivateStoreType()) {
			case 1:
				return getSellList().getTitle();
			case 5:
				return getCreateList().getStoreName();
			case 10:
				return getCustomSellList().getTitle();
			case 3:
				return getBuyList().getTitle();
		}
		
		return "";
	}
	
	public String getShopNameType() {
		switch (getPrivateStoreType()) {
			case 1:
				return "<font color=E4A1EE>Sell</font>";
			case 3:
				return "<font color=FDFEA5>Buy</font>";
			case 5:
				return "<font color=FCB932>Manufacture</font>";
			case 10:
				return "<font color=E4A1EE>Custom Sell</font>";
		}
		
		return "Unknown";
	}
	
	public void increaseBotLevel() {
		botLevel++;
	}
	
	public int getBotLevel() {
		return botLevel;
	}
	
	public void setBotLevel(int level) {
		botLevel = level;
	}
	
	public void setCaptcha(String captcha) {
		this.captcha = captcha;
	}
	
	public String getCaptcha() {
		return captcha;
	}
	
	public void captcha(String message) {
		if (isGM()) {
			return;
		}
		
		NpcHtmlMessage captchaMsg = null;
		int imgId = Rnd.get(1000000) + 1000000;
		String html =
				"<html><body><center>" + message + "<img src=\"Crest.pledge_crest_" + Config.SERVER_ID + "_" + imgId + "\" width=256 height=64><br>" +
						"Enter the above characters:" + "<edit var=text width=130 height=11 length=16><br>" +
						"<button value=\"Done\" action=\"bypass Captcha $text\" back=\"l2ui_ct1.button_df\" width=65 height=20 fore=\"l2ui_ct1.button_df\">" +
						"</center></body></html>";
		setCaptcha(generateRandomString(4));
		try {
			File captcha = new File(Config.DATA_FOLDER + "captcha/" + Rnd.get(100) + ".png");
			captcha.mkdirs();
			ImageIO.write(generateCaptcha(getCaptcha()), "png", captcha);
			PledgeCrest packet = new PledgeCrest(imgId, DDSConverter.convertToDDS(captcha).array());
			sendPacket(packet);
			captchaMsg = new NpcHtmlMessage(0, html);
			sendPacket(captchaMsg);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static String generateRandomString(int length) {
		String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ123456789";
		String rndString = "";
		for (int i = 0; i < length; i++) {
			rndString += chars.charAt(Rnd.get(chars.length()));
		}
		return rndString;
	}
	
	private static BufferedImage generateCaptcha(String randomString) {
		char[] charString = randomString.toCharArray();
		final int width = 256;
		final int height = 64;
		final BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		final Graphics2D g2d = bufferedImage.createGraphics();
		final Font font = new Font("verdana", Font.BOLD, 36);
		RenderingHints renderingHints = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		renderingHints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2d.setRenderingHints(renderingHints);
		g2d.setFont(font);
		g2d.setColor(new Color(255, 255, 0));
		final GradientPaint gradientPaint = new GradientPaint(0, 0, Color.black, 0, height / 2, Color.black, true);
		g2d.setPaint(gradientPaint);
		g2d.fillRect(0, 0, width, height);
		g2d.setColor(new Color(255, 153, 0));
		int xCordinate = 0;
		int yCordinate = 0;
		for (int i = 0; i < charString.length; i++) {
			xCordinate = 55 * i + Rnd.get(25) + 10;
			if (xCordinate >= width - 5) {
				xCordinate = 0;
			}
			yCordinate = 30 + Rnd.get(34);
			g2d.drawChars(charString, i, 1, xCordinate, yCordinate);
		}
		g2d.dispose();
		return bufferedImage;
	}
	
	private long lastActionCaptcha = 0L;
	
	public boolean onActionCaptcha(boolean increase) {
		if (botLevel < 10) {
			long curTime = System.currentTimeMillis();
			if (curTime < lastActionCaptcha + 10000L) {
				return true;
			}
			
			lastActionCaptcha = curTime;
		}
		
		if (botLevel < 100) {
			if (increase || botLevel >= 10) {
				botLevel++;
			}
			
			if (botLevel < 4) {
				return true;
			} else if (botLevel < 10) {
				captcha("Reply at the captcha as soon as possible, before your ability to target characters is disabled!<br>");
				return true;
			} else {
				captcha("Now you cannot target other characters without replying at the captcha.<br> And don't be persistent, you will be considered as a bot and banned if you don't reply!<br>");
			}
		} else {
			sendPacket(new CreatureSay(0,
					Say2.TELL,
					"AntiBots",
					"Next time try to play without a bot or looking at what is happening on your screen."));
			Util.handleIllegalPlayerAction(this,
					"Player " + getName() +
							" didn't reply to the Captcha but tried to target a lot of times! Character being kicked and account locked.",
					IllegalPlayerAction.PUNISH_KICK);
			
			Connection con = null;
			try {
				con = L2DatabaseFactory.getInstance().getConnection();
				
				PreparedStatement statement = con.prepareStatement("REPLACE INTO ban_timers (identity, timer, author, reason) VALUES (?, ?, ?, ?);");
				
				statement.setString(1, getAccountName());
				statement.setLong(2, System.currentTimeMillis() / 1000 + 200 * 3600);
				statement.setString(3, "AntiBots");
				statement.setString(4, "using bot");
				statement.execute();
				statement.close();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				L2DatabaseFactory.close(con);
			}
		}
		return false;
	}
	
	public class CaptchaTask implements Runnable {
		//int times = 0;
		@Override
		public void run() {
			if (/*times == 0 ||*/getTarget() instanceof Attackable || isFishing()) {
				//times++;
				setBotLevel(0);
				captcha("Please, understand that this is to avoid cheaters on this server.<br>" +
						"Don't worry if you late to reply, your character will not be kicked ;)");
				int time;

				/*if (times == 1)
				 	time = (Rnd.get(3 * 3600) + 1800) * 1000;
				 else*/
				time = (Rnd.get(3 * 3600) + 7200) * 1000;
				
				ThreadPoolManager.getInstance().scheduleGeneral(this, time);
			} else {
				ThreadPoolManager.getInstance().scheduleGeneral(this, 300 * 1000);
			}
		}
	}
	
	public void startCaptchaTask() {
		if (!isGM() && Config.isServer(Config.TENKAI)) {
			//ThreadPoolManager.getInstance().scheduleGeneral(new CaptchaTask(), 30 * 1000);
			//ThreadPoolManager.getInstance().scheduleGeneral(new CaptchaTask(), (Rnd.get(3 * 3600) + 7200) * 1000);
		}
	}
	
	private boolean showHat = true;
	
	public boolean isShowingHat() {
		return showHat;
	}
	
	public void setShowHat(boolean showHat) {
		this.showHat = showHat;
	}
	
	public int getArmorEnchant() {
		if (getInventory() == null || getIsArmorGlowDisabled()) {
			return 0;
		}
		
		Item chest = getInventory().getPaperdollItem(Inventory.PAPERDOLL_CHEST);
		if (chest == null || chest.getArmorItem() == null || chest.getArmorItem().getArmorSet() == null) {
			return 0;
		}
		
		int enchant = 0;
		for (int itemId : chest.getArmorItem().getArmorSet()) {
			L2ArmorSet set = ArmorSetsTable.getInstance().getSet(itemId);
			if (set == null) {
				continue;
			}
			
			int setEnchant = set.getEnchantLevel(this);
			if (setEnchant > enchant) {
				enchant = setEnchant;
			}
		}
		
		return enchant;
	}
	
	public int abilityPoints = 0;
	public int spentAbilityPoints = 0;
	public TIntIntHashMap abilities = new TIntIntHashMap();
	
	public int getAbilityPoints() {
		return abilityPoints;
	}
	
	public int getSpentAbilityPoints() {
		return spentAbilityPoints;
	}
	
	public TIntIntHashMap getAbilities() {
		return abilities;
	}
	
	public void restoreAbilities() {
		abilityPoints = 0;
		spentAbilityPoints = 0;
		for (int skillId : abilities.keys()) {
			removeSkill(skillId);
		}
		
		abilities.clear();
		
		Connection con = null;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("SELECT points FROM character_ability_points WHERE charId = ? AND classIndex = ?");
			statement.setInt(1, getObjectId());
			statement.setInt(2, getClassIndex());
			ResultSet rset = statement.executeQuery();
			
			if (rset.next()) {
				abilityPoints = rset.getInt("points");
			}
			
			rset.close();
			statement.close();
			
			statement = con.prepareStatement("SELECT skillId, level FROM character_abilities WHERE charId = ? AND classIndex = ?");
			statement.setInt(1, getObjectId());
			statement.setInt(2, getClassIndex());
			rset = statement.executeQuery();
			
			while (rset.next()) {
				int skillId = rset.getInt("skillId");
				int level = rset.getInt("level");
				
				spentAbilityPoints += level;
				abilities.put(skillId, level);
				addSkill(SkillTable.getInstance().getInfo(skillId, level));
			}
			
			rset.close();
			statement.close();
		} catch (Exception e) {
			log.warn("Error found in " + getName() + "'s abilities: " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
		
		if (getLevel() < 99 && spentAbilityPoints > 0) {
			setAbilities(new TIntIntHashMap());
		}
	}
	
	public void setAbilityPoints(int points) {
		abilityPoints = points;
		Connection con = null;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("REPLACE INTO character_ability_points (charId, classIndex, points) VALUES (?, ?, ?)");
			statement.setInt(1, getObjectId());
			statement.setInt(2, getClassIndex());
			statement.setInt(3, abilityPoints);
			statement.execute();
			statement.close();
		} catch (Exception e) {
			log.warn("Error found while storing " + getName() + "'s ability points: " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	public void setAbilities(TIntIntHashMap abilities) {
		spentAbilityPoints = 0;
		for (int skillId : abilities.keys()) {
			AbilityTable.getInstance().manageHiddenSkill(this, skillId, getSkillLevelHash(skillId), false);
			removeSkill(skillId);
		}
		this.abilities = abilities;
		
		Connection con = null;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("DELETE FROM character_abilities WHERE charId = ? AND classIndex = ?");
			statement.setInt(1, getObjectId());
			statement.setInt(2, getClassIndex());
			statement.execute();
			statement.close();
			
			statement = con.prepareStatement("INSERT INTO character_abilities (charId, classIndex, skillId, level) VALUES (?, ?, ?, ?)");
			for (int skillId : abilities.keys()) {
				int level = abilities.get(skillId);
				statement.setInt(1, getObjectId());
				statement.setInt(2, getClassIndex());
				statement.setInt(3, skillId);
				statement.setInt(4, level);
				statement.execute();
				
				spentAbilityPoints += level;
				addSkill(SkillTable.getInstance().getInfo(skillId, level));
				AbilityTable.getInstance().manageHiddenSkill(this, skillId, level, true);
			}
			statement.close();
		} catch (Exception e) {
			log.warn("Error found while storing " + getName() + "'s abilities: " + e.getMessage(), e);
		} finally {
			L2DatabaseFactory.close(con);
		}
	}
	
	public void deleteAllItemsById(int itemId) {
		long playerItemCount = getInventory().getInventoryItemCount(itemId, 0);
		
		if (playerItemCount > 0) {
			destroyItemByItemId("", itemId, playerItemCount, this, true);
		}
		
		if (getPet() != null) {
			long petItemCount = getPet().getInventory().getInventoryItemCount(itemId, 0);
			
			if (petItemCount > 0) {
				getPet().destroyItemByItemId("", itemId, petItemCount, this, true);
			}
		}
	}
	
	private int premiumItemId;
	
	public final void setPremiumItemId(int premiumItemId) {
		this.premiumItemId = premiumItemId;
	}
	
	public final int getPremiumItemId() {
		return premiumItemId;
	}
	
	private boolean rememberSkills;
	
	public final void setRememberSkills(boolean remember) {
		rememberSkills = remember;
	}
	
	public final boolean isRememberSkills() {
		return rememberSkills && isInsideZone(Creature.ZONE_TOWN);
	}
	
	public boolean isMage() {
		int playerClasse = getClassId();
		return playerClasse == 143 || playerClasse >= 166 && playerClasse <= 170 || playerClasse == 146 ||
				playerClasse >= 179 && playerClasse <= 181 || playerClasse >= 94 && playerClasse <= 98 ||
				playerClasse >= 103 && playerClasse <= 105 || playerClasse >= 110 && playerClasse <= 112 ||
				playerClasse >= 115 && playerClasse <= 116 || playerClasse >= 10 && playerClasse <= 17 || playerClasse >= 25 && playerClasse <= 30 ||
				playerClasse >= 38 && playerClasse <= 43 || playerClasse >= 49 && playerClasse <= 52;
	}
	
	public boolean isFighter() {
		int playerClasse = getClassId();
		return playerClasse >= 88 && playerClasse <= 93 || playerClasse >= 99 && playerClasse <= 102 || playerClasse >= 106 && playerClasse <= 109 ||
				playerClasse >= 113 && playerClasse <= 114 || playerClasse >= 117 && playerClasse <= 136 || playerClasse >= 0 && playerClasse <= 9 ||
				playerClasse >= 18 && playerClasse <= 24 || playerClasse >= 31 && playerClasse <= 37 || playerClasse >= 44 && playerClasse <= 48 ||
				playerClasse >= 53 && playerClasse <= 57 || playerClasse == 140 || playerClasse >= 152 && playerClasse <= 157 ||
				playerClasse == 141 || playerClasse >= 158 && playerClasse <= 161 || playerClasse == 142 ||
				playerClasse >= 162 && playerClasse <= 165 || playerClasse == 139 || playerClasse >= 148 && playerClasse <= 151;
	}
	
	public boolean isHybrid() {
		int playerClass = getClassId();
		return playerClass == 132 || playerClass == 133 || playerClass == 144 || playerClass >= 171 && playerClass <= 175;
	}
	
	private int[] movementTrace = new int[3];
	private int[] previousMovementTrace = new int[3];
	
	public final void setMovementTrace(final int[] movementTrace) {
		if (movementTrace != null) {
			previousMovementTrace = movementTrace;
		}
		
		this.movementTrace = movementTrace;
	}
	
	public final int[] getMovementTrace() {
		return movementTrace;
	}
	
	public final int[] getPreviousMovementTrace() {
		return previousMovementTrace;
	}
	
	public final boolean isPlayingEvent() {
		return event != null && event.isState(EventState.STARTED);
	}
	
	public final int getTeamId() {
		if (getEvent() == null) {
			return -1;
		}
		
		return getEvent().getParticipantTeamId(getObjectId());
	}
	
	public void removeSkillReuse(boolean update) {
		getReuseTimeStamp().clear();
		if (getDisabledSkills() != null) {
			getDisabledSkills().clear();
		}
		
		if (update) {
			sendPacket(new SkillCoolTime(this));
		}
	}
	
	public void heal() {
		setCurrentHp(getMaxHp());
		setCurrentMp(getMaxMp());
		setCurrentCp(getMaxCp());
		
		for (SummonInstance summon : getSummons()) {
			if (summon == null) {
				continue;
			}
			
			summon.setCurrentHp(summon.getMaxHp());
			summon.setCurrentMp(summon.getMaxMp());
			summon.setCurrentCp(summon.getMaxCp());
		}
		
		if (getPet() != null) {
			getPet().setCurrentHpMp(getPet().getMaxHp(), getPet().getMaxMp());
		}
	}
	
	boolean inWatcherMode = false;
	
	public void startWatcherMode() {
		inWatcherMode = true;
	}
	
	public void stopWatcherMode() {
		inWatcherMode = false;
	}
	
	public boolean isInWatcherMode() {
		return inWatcherMode;
	}
	
	public ScheduledFuture<?> respawnTask;
	
	public final ScheduledFuture<?> getRespawnTask() {
		return respawnTask;
	}
	
	public ScheduledFuture<?> comboTask;
	
	public final ScheduledFuture<?> getComboTask() {
		return comboTask;
	}
	
	public final void setComboTask(ScheduledFuture<?> task) {
		if (task == null && comboTask != null) {
			comboTask.cancel(false);
		}
		
		comboTask = task;
	}
	
	private Item compoundItem1 = null;
	private Item compoundItem2 = null;
	
	public Item getCompoundItem1() {
		return compoundItem1;
	}
	
	public void setCompoundItem1(Item compoundItem) {
		compoundItem1 = compoundItem;
	}
	
	public Item getCompoundItem2() {
		return compoundItem2;
	}
	
	public void setCompoundItem2(Item compoundItem) {
		compoundItem2 = compoundItem;
	}
	
	protected final Map<Integer, Skill> temporarySkills;
	
	private byte temporaryLevel;
	private byte temporaryLevelToApply;
	
	public byte getTemporaryLevelToApply() {
		return temporaryLevelToApply;
	}
	
	public void setTemporaryLevelToApply(final byte level) {
		temporaryLevelToApply = level;
	}
	
	@SuppressWarnings("unused")
	private int classIdBeforeDelevel;
	
	private int temporaryClassId;
	private PlayerClass temporaryPlayerClass;
	private int temporaryTemplateId;
	private PcTemplate temporaryTemplate;
	
	public synchronized final void setTemporaryLevel(final byte level) {
		isUpdateLocked = true;
		
		int oldLevel = getOriginalLevel();
		
		// We remove the active skill effects whenever switching levels...
		for (Skill skill : getAllSkills()) {
			final Abnormal activeEffect = getFirstEffect(skill.getId());
			
			boolean removeActiveEffect = activeEffect == null || !(activeEffect.getEffector() instanceof NpcInstance);
			
			removeSkillEffect(skill.getId(), removeActiveEffect);
		}
		
		final Abnormal elixir = getFirstEffect(9982); // Divine Protection Elixir
		if (level < 85 && elixir != null) {
			elixir.exit();
		}
		
		// When getting on a temporary level,
		// We remove all equipped items skills.
		if (level != 0) {
			for (int i = Inventory.PAPERDOLL_TOTALSLOTS; i-- > Inventory.PAPERDOLL_UNDER; ) {
				Item equippedItem = getInventory().getPaperdollItem(i);
				
				if (equippedItem == null) {
					continue;
				}
				
				ArmorTemplate armor = equippedItem.getArmorItem();
				
				if (armor == null) {
					continue;
				}
				
				final SkillHolder[] itemSkills = armor.getSkills();
				
				if (itemSkills == null) {
					continue;
				}
				
				for (SkillHolder skillInfo : itemSkills) {
					if (skillInfo == null) {
						continue;
					}
					
					Skill skill = skillInfo.getSkill();
					
					if (skill == null) {
						continue;
					}
					
					removeSkill(skill, false, skill.isPassive());
				}
			}
		}
		
		temporaryLevel = level;
		
		if (temporarySkills.size() != 0) {
			temporarySkills.clear();
			
			// When switching back to the original level, we re-add the skills effects.
			for (Skill skill : getAllSkills()) {
				addSkillEffect(skill);
			}
		}
		
		if (temporaryLevel == 0) {
			temporaryClassId = 0;
			temporaryPlayerClass = null;
			temporaryTemplateId = 0;
			temporaryTemplate = null;
			
			giveSkills(false);
			
			// When getting back to the original level,
			// We re-add item skills.
			for (int i = Inventory.PAPERDOLL_TOTALSLOTS; i-- > Inventory.PAPERDOLL_UNDER; ) {
				Item equippedItem = getInventory().getPaperdollItem(i);
				
				if (equippedItem == null) {
					continue;
				}
				
				ArmorTemplate armor = equippedItem.getArmorItem();
				
				if (armor == null) {
					continue;
				}
				
				final SkillHolder[] itemSkills = armor.getSkills();
				
				if (itemSkills == null) {
					continue;
				}
				
				for (SkillHolder skillInfo : itemSkills) {
					if (skillInfo == null) {
						continue;
					}
					
					Skill skill = skillInfo.getSkill();
					
					if (skill == null) {
						continue;
					}
					
					addSkill(skill, false);
					
					if (skill.isActive()) {
						if (getReuseTimeStamp().isEmpty() || !getReuseTimeStamp().containsKey(skill.getReuseHashCode())) {
							int equipDelay = skill.getEquipDelay();
							
							if (equipDelay > 0) {
								addTimeStamp(skill, equipDelay);
								disableSkill(skill, equipDelay);
							}
						}
						//updateTimeStamp = true;
					}
					//update = true;
				}
			}
		} else {
			// When deleveling, we unsummon the active summon if any...
			if (getPet() != null) {
				getPet().unSummon(this);
			}
			
			if (getSummons().size() != 0) {
				for (SummonInstance summon : getSummons()) {
					summon.unSummon(this);
				}
			}
			
			if (getCubics().size() != 0) {
				for (CubicInstance cubic : getCubics().values()) {
					delCubic(cubic.getId());
				}
			}
			
			int newLevel = temporaryLevel;
			
			int[] levels = null;
			
			if (getRace() != Race.Ertheia) {
				levels = new int[]{20, 40, 76, 86};
			} else {
				levels = new int[]{40, 76, 86};
			}
			
			int classesChanges = 0;
			
			for (int level2 : levels) {
				if (level2 > oldLevel) // + 1 for ertheia
				{
					break;
				} else if (level2 < newLevel + 1) {
					continue;
				}
				
				classesChanges++;
			}
			
			if (classesChanges != 0) {
				PlayerClass newClass = null;
				
				for (int i = 0; i < classesChanges; i++) {
					if (newClass == null) {
						newClass = getCurrentClass().getParent();
					} else {
						newClass = newClass.getParent();
					}
					
					if (newClass != null) {
						// When a Female Soulhound becomes a Feoh Soulhound...
						// Parent Class becomes Male Soulhound...
						switch (newClass.getId()) {
							case 132: // Male Soulhound
							{
								if (getAppearance().getSex()) {
									newClass = PlayerClassTable.getInstance().getClassById(133);
								}
								
								break;
							}
							default:
								break;
						}
					}
				}
				
				if (newClass == null) {
					setTemporaryLevel((byte) 0);
					return;
				}
				
				sendMessage("Your class is now " + newClass.getName() + ".");
				
				temporaryClassId = newClass.getId();
				temporaryPlayerClass = newClass;
				
				temporaryTemplateId = temporaryPlayerClass.getRace().ordinal() * 2 + (temporaryPlayerClass.isMage() ? 1 : 0);
				
				temporaryTemplate = CharTemplateTable.getInstance().getTemplate(temporaryTemplateId);
			}
			
			// No Brooches for non awakened characters...
			if (getLevel() <= 85) {
				ArrayList<Item> modifiedItems = new ArrayList<>();
				
				for (int i = Inventory.PAPERDOLL_TOTALSLOTS; i-- > Inventory.PAPERDOLL_BROOCH; ) {
					Item equippedItem = getInventory().getPaperdollItem(i);
					
					if (equippedItem == null) {
						continue;
					}
					
					getInventory().unEquipItemInSlotAndRecord(i);
					
					SystemMessage sm = null;
					
					if (equippedItem.getEnchantLevel() > 0) {
						sm = SystemMessage.getSystemMessage(SystemMessageId.EQUIPMENT_S1_S2_REMOVED);
						sm.addNumber(equippedItem.getEnchantLevel());
						sm.addItemName(equippedItem);
					} else {
						sm = SystemMessage.getSystemMessage(SystemMessageId.S1_DISARMED);
						sm.addItemName(equippedItem);
					}
					
					sendPacket(sm);
					
					modifiedItems.add(equippedItem);
				}
				
				if (modifiedItems.size() != 0) {
					InventoryUpdate iu = new InventoryUpdate();
					iu.addItems(modifiedItems);
					
					sendPacket(iu);
				}
			}
			
			giveSkills(true);
		}
		
		regiveTemporarySkills();
		rewardSkills();
		
		//initCharStat();
		
		StatusUpdate su = new StatusUpdate(this);
		su.addAttribute(StatusUpdate.LEVEL, getLevel());
		su.addAttribute(StatusUpdate.MAX_CP, getMaxCp());
		su.addAttribute(StatusUpdate.MAX_HP, getMaxHp());
		su.addAttribute(StatusUpdate.MAX_MP, getMaxMp());
		
		sendPacket(su);
		
		// Update the overloaded status of the Player
		refreshOverloaded();
		// Update the expertise status of the Player
		refreshExpertisePenalty();
		// Send a Server->Client packet UserInfo to the Player
		sendPacket(new UserInfo(this));
		
		broadcastStatusUpdate();
		
		if (getClan() != null) {
			getClan().broadcastClanStatus();
		}
		
		int playerLevel = getLevel();
		int maxAllowedGrade = 0;
		
		if (playerLevel >= 99) {
			maxAllowedGrade = ItemTemplate.CRYSTAL_R99;
		} else if (playerLevel >= 95) {
			maxAllowedGrade = ItemTemplate.CRYSTAL_R95;
		} else if (playerLevel >= 85) {
			maxAllowedGrade = ItemTemplate.CRYSTAL_R;
		} else if (playerLevel >= 80) {
			maxAllowedGrade = ItemTemplate.CRYSTAL_S80;
		} else if (playerLevel >= 76) {
			maxAllowedGrade = ItemTemplate.CRYSTAL_S;
		} else if (playerLevel >= 61) {
			maxAllowedGrade = ItemTemplate.CRYSTAL_A;
		} else if (playerLevel >= 52) {
			maxAllowedGrade = ItemTemplate.CRYSTAL_B;
		} else if (playerLevel >= 40) {
			maxAllowedGrade = ItemTemplate.CRYSTAL_C;
		} else if (playerLevel >= 20) {
			maxAllowedGrade = ItemTemplate.CRYSTAL_D;
		} else {
			maxAllowedGrade = ItemTemplate.CRYSTAL_NONE;
		}
		
		ArrayList<Item> modifiedItems = new ArrayList<>();
		
		// Remove over enchanted items and hero weapons
		for (int i = 0; i < Inventory.PAPERDOLL_TOTALSLOTS; i++) {
			Item equippedItem = getInventory().getPaperdollItem(i);
			
			if (equippedItem == null) {
				continue;
			}
			
			if (equippedItem.getItem().getCrystalType() > maxAllowedGrade) {
				getInventory().unEquipItemInSlotAndRecord(i);
				
				SystemMessage sm = null;
				
				if (equippedItem.getEnchantLevel() > 0) {
					sm = SystemMessage.getSystemMessage(SystemMessageId.EQUIPMENT_S1_S2_REMOVED);
					sm.addNumber(equippedItem.getEnchantLevel());
					sm.addItemName(equippedItem);
				} else {
					sm = SystemMessage.getSystemMessage(SystemMessageId.S1_DISARMED);
					sm.addItemName(equippedItem);
				}
				
				sendPacket(sm);
				
				modifiedItems.add(equippedItem);
			}
		}
		
		if (modifiedItems.size() != 0) {
			InventoryUpdate iu = new InventoryUpdate();
			iu.addItems(modifiedItems);
			
			sendPacket(iu);
		}
		
		int gearGrade = getGearGradeForCurrentLevel();
		
		int[][] gearPreset = getGearPreset(getClassId(), gearGrade);
		
		if (gearPreset != null) {
			equipGearPreset(gearPreset);
		}
		
		isUpdateLocked = false;
		
		sendPacket(new ItemList(this, false));
		
		refreshExpertisePenalty();
		
		broadcastUserInfo();
		
		sendSkillList();
		
		shortCuts.restore();
		sendPacket(new ShortCutInit(this));
		
		sendPacket(new ItemList(this, false));
		
		sendMessage("Your level has been adjusted.");
		sendMessage("You are now a " + getCurrentClass().getName() + ".");
		
		setCurrentHp(getMaxHp());
		setCurrentMp(getMaxMp());
		setCurrentCp(getMaxCp());
	}
	
	private boolean isUpdateLocked;
	
	public final boolean isUpdateLocked() {
		return isUpdateLocked;
	}

	/*
	private PlayerClass getDelevelClass(final int oldLevel, final int newLevel, final PlayerClass oldClass)
	{
		int[] levels = new int[]{ 20, 40, 76, 85 };

		int classesChanges = 0;
		for (int i = 0; i < levels.length; i++)
		{
			if (levels[i] > oldLevel)
				break;

			classesChanges++;
		}

		PlayerClass newClass = null;

		for (int i = 0; i < classesChanges; i++)
		{
			newClass = newClass == null ? oldClass.getParent() : newClass.getParent();
		}

		return newClass;
	}*/
	
	public final byte getTemporaryLevel() {
		return temporaryLevel;
	}
	
	public final byte getOriginalLevel() {
		return getStat().getLevel();
	}
	
	@Override
	public Skill[] getAllSkills() {
		if (temporaryLevel != 0) {
			if (temporarySkills == null) {
				return new Skill[0];
			}
			
			try {
				synchronized (temporarySkills) {
					return temporarySkills.values().toArray(new Skill[temporarySkills.size()]);
				}
			} catch (Exception e) {
				e.printStackTrace();
				return new Skill[0];
			}
		}
		
		return super.getAllSkills();
	}
	
	@Override
	public final Skill getKnownSkill(int skillId) {
		if (temporaryLevel != 0) {
			if (temporarySkills == null) {
				return null;
			}
			
			return temporarySkills.get(skillId);
		}
		
		return super.getKnownSkill(skillId);
	}
	
	@Override
	public int getSkillLevelHash(int skillId) {
		if (temporaryLevel != 0) {
			if (temporarySkills == null) {
				return -1;
			}
			
			Skill skill = temporarySkills.get(skillId);
			
			if (skill == null) {
				return -1;
			}
			
			return skill.getLevelHash();
		}
		
		return super.getSkillLevelHash(skillId);
	}
	
	@Override
	public Map<Integer, Skill> getSkills() {
		if (temporaryLevel != 0) {
			return temporarySkills;
		}
		
		return super.getSkills();
	}
	
	public final boolean hasAwakaned() {
		if (getCurrentClass() == null) {
			return false;
		}
		
		if (getRace() == Race.Ertheia) {
			switch (getCurrentClass().getId()) {
				case 182: // Ertheia Fighter
				case 183: // Ertheia Wizard
				case 184: // Marauder
				case 185: // Cloud Breaker
				case 186: // Ripper
				case 187: // Stratomancer
					return false;
				default:
					break;
			}
		}
		
		return getCurrentClass().getId() >= 139;
	}
	
	public int windowId = 0;
	
	public int getWindowId() {
		return windowId;
	}
	
	public void setWindowId(int id) {
		windowId = id;
	}
	
	public PlayerClass getOriginalClass() {
		return currentClass;
	}
	
	protected final HashMap<Integer, Skill> certificationSkills = new HashMap<>();
	
	public final void addCertificationSkill(final Skill skill) {
		certificationSkills.put(skill.getId(), skill);
	}
	
	public final void resetCertificationSkills() {
		certificationSkills.clear();
	}
	
	/**
	 * Combat Parameters & Checks...
	 */
	public final boolean isAvailableForCombat() {
		return getPvpFlag() > 0 || getReputation() < 0;
	}
	
	public final boolean isInSameChannel(final Player target) {
		final L2Party activeCharP = getParty();
		final L2Party targetP = target.getParty();
		if (activeCharP != null && targetP != null) {
			final L2CommandChannel chan = activeCharP.getCommandChannel();
			if (chan != null && chan.isInChannel(targetP)) {
				return true;
			}
		}
		
		return false;
	}
	
	public final boolean isInSameParty(final Creature target) {
		return getParty() != null && target.getParty() != null && getParty().getLeader() == target.getParty().getLeader();
	}
	
	public final boolean isInSameClan(final Player target) {
		return getClanId() != 0 && getClanId() == target.getClanId();
	}
	
	public final boolean isInSameAlly(final Player target) {
		return getAllyId() != 0 && getAllyId() == target.getAllyId();
	}
	
	public final boolean isInSameDuel(final Player target) {
		return getDuelId() != 0 && getDuelId() == target.getDuelId();
	}
	
	public final boolean isInSameOlympiadGame(final Player target) {
		return getOlympiadGameId() != 0 && getOlympiadGameId() == target.getOlympiadGameId();
	}
	
	public final boolean isInSameClanWar(final Player target) {
		final L2Clan aClan = getClan();
		final L2Clan tClan = target.getClan();
		
		if (aClan != null && tClan != null && aClan != tClan) {
			if (aClan.isAtWarWith(tClan.getClanId()) && tClan.isAtWarWith(aClan.getClanId())) {
				return true;
			}
		}
		return false;
	}
	
	public final boolean isInSameSiegeSide(final Player target) {
		if (getSiegeState() == 0 || target.getSiegeState() == 0) {
			return false;
		}
		
		final Siege s = CastleSiegeManager.getInstance().getSiege(getX(), getY(), getZ());
		
		if (s != null) {
			if (s.checkIsDefender(getClan()) && s.checkIsDefender(target.getClan())) {
				return true;
			}
			if (s.checkIsAttacker(getClan()) && s.checkIsAttacker(target.getClan())) {
				return true;
			}
		}
		
		return false;
	}
	
	/***
	 *
	 */
	public final boolean isInsidePvpZone() {
		return isInsideZone(Creature.ZONE_PVP) || isInsideZone(Creature.ZONE_SIEGE);
	}
	
	private int lastPhysicalDamages;
	
	public final void setLastPhysicalDamages(int lastPhysicalDamages) {
		this.lastPhysicalDamages = lastPhysicalDamages;
	}
	
	public final int getLastPhysicalDamages() {
		return lastPhysicalDamages;
	}
	
	private int hatersAmount;
	
	public final void setHatersAmount(final int hatersAmount) {
		this.hatersAmount = hatersAmount;
	}
	
	public final int getHatersAmount() {
		return hatersAmount;
	}
	
	// ClassId, LevelRange, GearPreset
	public final Map<Integer, Map<Integer, int[][]>> gearPresets = new HashMap<>();
	
	public final void addGearPreset(final int classId, final int levelRange, final int[][] gearPreset) {
		addGearPreset(classId, levelRange, gearPreset, false);
	}
	
	public final void addGearPreset(final int classId, final int levelRange, final int[][] gearPreset, final boolean store) {
		if (!gearPresets.containsKey(classId)) {
			gearPresets.put(classId, new HashMap<>());
		}
		
		gearPresets.get(classId).put(levelRange, gearPreset);
		
		if (store) {
			String presetData = "";
			
			for (int[] element : gearPreset) {
				if (element[1] == 0) {
					continue;
				}
				
				presetData += element[0] + ","; // SlotId
				presetData += element[1] + ","; // ItemId
				presetData += element[2] + ";"; // ItemObjectId
			}
			
			String playerClassName = PlayerClassTable.getInstance().getClassNameById(classId);
			Connection con = null;
			try {
				con = L2DatabaseFactory.getInstance().getConnection();
				
				PreparedStatement statement = con.prepareStatement(
						"INSERT INTO character_gears_presets (playerId, classId, gearGrade, presetData) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE presetData=?");
				statement.setInt(1, getObjectId());
				statement.setInt(2, classId);
				statement.setInt(3, levelRange);
				statement.setString(4, presetData);
				statement.setString(5, presetData);
				
				statement.execute();
				statement.close();
			} catch (Exception e) {
				log.error("L2BufferInstance: Could not store gears preset for player : " + getObjectId(), e);
			} finally {
				try {
					con.close();
				} catch (Exception ignored) {
				
				}
			}
			
			sendMessage("Saved current equipment for future usage on your " + playerClassName + " Level " + getLevel() + ".");
		}
	}
	
	public final int[][] getGearPreset(final int classId, final int levelRange) {
		if (!gearPresets.containsKey(classId) || !gearPresets.get(classId).containsKey(levelRange)) {
			return null;
		}
		
		return gearPresets.get(classId).get(levelRange);
	}
	
	public final Map<Integer, Map<Integer, int[][]>> getGearPresets() {
		return gearPresets;
	}
	
	public final boolean equipGearPreset(final int classId, final int levelRange) {
		int[][] gearPreset = getGearPreset(classId, levelRange);
		
		if (gearPreset == null) {
			return false;
		}
		
		equipGearPreset(gearPreset);
		return true;
	}
	
	public final void equipGearPreset(final int[][] gearPreset) {
		if (getPrivateStoreType() != 0) {
			sendMessage("Gears couldn't be automatically swapped because you are in a private store mode.");
			return;
		} else if (isEnchanting() || getActiveEnchantAttrItem() != null || getActiveEnchantItem() != null || getActiveEnchantSupportItem() != null) {
			sendMessage("Gears couldn't be automatically swapped because you have an active enchantment going on.");
			return;
		} else if (getActiveTradeList() != null) {
			sendMessage("Gears couldn't be automatically swapped because you have an active trade going on.");
			return;
		}
		
		for (int i = 0; i < gearPreset.length; i++) {
			getInventory().unEquipItemInSlot(i);
		}
		
		for (int[] element : gearPreset) {
			int itemId = element[1];
			int itemObjectId = element[2];
			
			if (itemId == 0 || itemObjectId == 0) {
				continue;
			}
			
			Item item = getInventory().getItemByObjectId(itemObjectId);
			
			if (item == null) {
				item = getInventory().getItemByItemId(itemId);
				
				if (item == null) {
					sendMessage("Couldn't find " + ItemTable.getInstance().getTemplate(itemId).getName() + " in your inventory.");
					continue;
				}
			}
			//getInventory().unEquipItemInSlot(i);
			/*
			getInventory().unEquipItemInSlot(i);
			getInventory().unEquipItemInSlot(i);
			// If it's equipped, let's un-equip it first?
			if (item.isEquipped())
				useEquippableItem(item, true);
			 */
			useEquippableItem(item, true);
		}
	}
	
	public final int getGearGradeForCurrentLevel() {
		return getGearGradeForCurrentLevel(0);
	}
	
	public final int getGearGradeForCurrentLevel(int level) {
		final int playerLevel = level == 0 ? getLevel() : level;
		
		int levelRange = 0;
		
		if (playerLevel >= 99) {
			levelRange = ItemTemplate.CRYSTAL_R99;
		} else if (playerLevel >= 95) {
			levelRange = ItemTemplate.CRYSTAL_R95;
		} else if (playerLevel >= 85) {
			levelRange = ItemTemplate.CRYSTAL_R;
		} else if (playerLevel >= 80) {
			levelRange = ItemTemplate.CRYSTAL_S80;
		} else if (playerLevel >= 76) {
			levelRange = ItemTemplate.CRYSTAL_S;
		} else if (playerLevel >= 61) {
			levelRange = ItemTemplate.CRYSTAL_A;
		} else if (playerLevel >= 52) {
			levelRange = ItemTemplate.CRYSTAL_B;
		} else if (playerLevel >= 40) {
			levelRange = ItemTemplate.CRYSTAL_C;
		} else if (playerLevel >= 20) {
			levelRange = ItemTemplate.CRYSTAL_D;
		} else {
			levelRange = ItemTemplate.CRYSTAL_NONE;
		}
		
		return levelRange;
	}
	
	public final int[] getMinMaxLevelForGearGrade(final int gearGrade) {
		switch (gearGrade) {
			case ItemTemplate.CRYSTAL_NONE:
				return new int[]{1, 19};
			case ItemTemplate.CRYSTAL_D:
				return new int[]{20, 39};
			case ItemTemplate.CRYSTAL_C:
				return new int[]{40, 51};
			case ItemTemplate.CRYSTAL_B:
				return new int[]{52, 60};
			case ItemTemplate.CRYSTAL_A:
				return new int[]{61, 75};
			case ItemTemplate.CRYSTAL_S:
				return new int[]{76, 79};
			case ItemTemplate.CRYSTAL_S80:
				return new int[]{80, 84};
			case ItemTemplate.CRYSTAL_R:
				return new int[]{85, 94};
			case ItemTemplate.CRYSTAL_R95:
				return new int[]{95, 98};
			case ItemTemplate.CRYSTAL_R99:
				return new int[]{99, 99};
		}
		
		return null;
	}
	
	public final void restoreGearPresets() {
		Connection con = null;
		try {
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement = con.prepareStatement("SELECT classId, gearGrade, presetData FROM character_gears_presets WHERE playerId=?");
			statement.setInt(1, getObjectId());
			
			final ResultSet rset = statement.executeQuery();
			
			while (rset.next()) {
				int classId = rset.getInt("classId");
				int gearGrade = rset.getInt("gearGrade");
				final String presetData = rset.getString("presetData");
				
				final String[] presetDataArgs = presetData.split(";");
				
				int[][] gearPreset = new int[Inventory.PAPERDOLL_TOTALSLOTS][3];
				
				for (String presetDataArg : presetDataArgs) {
					String[] itemData = presetDataArg.split(",");
					
					int slotId = Integer.parseInt(itemData[0]);
					int itemId = Integer.parseInt(itemData[1]);
					int itemObjectId = Integer.parseInt(itemData[2]);
					
					gearPreset[slotId][0] = slotId;
					gearPreset[slotId][1] = itemId;
					gearPreset[slotId][2] = itemObjectId;
				}
				
				addGearPreset(classId, gearGrade, gearPreset, false);
			}
			
			statement.close();
		} catch (final SQLException e) {
			log.warn("Could not restore gear presets for Player(" + getName() + ")...", e);
		} finally {
			try {
				con.close();
			} catch (final Exception ignored) {
			
			}
		}
	}
	
	private Map<Integer, Integer> tryingOn = new HashMap<>();
	
	public int getTryingOn(int slot) {
		if (!tryingOn.containsKey(slot)) {
			return 0;
		}
		
		return tryingOn.get(slot);
	}
	
	public void tryOn(int itemId) {
		final ItemTemplate item = ItemTable.getInstance().getTemplate(itemId);
		int slot = item.getBodyPart();
		if (slot == ItemTemplate.SLOT_FULL_ARMOR || slot == ItemTemplate.SLOT_ALLDRESS) {
			slot = ItemTemplate.SLOT_CHEST;
		}
		
		final int invSlot = Inventory.getPaperdollIndex(slot);
		tryingOn.put(invSlot, itemId);
		
		broadcastUserInfo();
		
		if (Config.isServer(Config.TENKAI) && !Config.isServer(Config.TENKAI_LEGACY)) {
			return;
		}
		
		sendMessage("You have one minute to see how the " + item.getName() + " appearance looks on your char.");
		ThreadPoolManager.getInstance().scheduleGeneral(() -> {
			if (!tryingOn.containsKey(invSlot) || tryingOn.get(invSlot) != item.getItemId()) {
				return;
			}
			
			tryingOn.remove(invSlot);
			
			broadcastUserInfo();
			sendMessage("Your minute to see the " + item.getName() + " appearance has expired.");
		}, 60000L);
	}
	
	private int luckyEnchantStoneId;
	
	public final void setLuckyEnchantStoneId(final int stoneId) {
		luckyEnchantStoneId = stoneId;
	}
	
	public final int getLuckyEnchantStoneId() {
		return luckyEnchantStoneId;
	}
	
	private boolean dwEquipped = false;
	private Future<?> dragonBloodConsumeTask = null;
	
	public void onDWEquip() {
		dwEquipped = true;
		//if (AttackStanceTaskManager.getInstance().getAttackStanceTask(this))
		//	startDragonBloodConsumeTask();
		ThreadPoolManager.getInstance().scheduleGeneral(() -> {
			if (isPlayingEvent() || isInOlympiadMode()) {
				getInventory().unEquipItemInBodySlot(ItemTemplate.SLOT_LR_HAND);
				broadcastUserInfo();
				sendPacket(new ItemList(Player.this, false));
			}
			//sendPacket(new ExShowScreenMessage("Dragonclaw weapons are disabled! Let's test some R99 PvP for now ;)", 5000));
			//getInventory().unEquipItemInBodySlot(ItemTemplate.SLOT_LR_HAND);
			//broadcastUserInfo();
			//sendPacket(new ItemList(Player.this, false));
		}, 10L);
	}
	
	public void onDWUnequip() {
		dwEquipped = false;
		if (dragonBloodConsumeTask != null) {
			dragonBloodConsumeTask.cancel(false);
			dragonBloodConsumeTask = null;
		}
	}
	
	public void onCombatStanceStart() {
		//if (dcEquipped)
		//	startDragonBloodConsumeTask();
	}
	
	public void onCombatStanceEnd() {
		if (dragonBloodConsumeTask != null) {
			dragonBloodConsumeTask.cancel(false);
			dragonBloodConsumeTask = null;
		}
	}
	
	@SuppressWarnings("unused")
	private void startDragonBloodConsumeTask() {
		if (dragonBloodConsumeTask != null) {
			return;
		}
		
		dragonBloodConsumeTask = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(() -> {
			if (!isInsideZone(ZONE_PEACE) && !destroyItemByItemId("dcConsume", 36415, 1, Player.this, true)) {
				sendPacket(new ExShowScreenMessage("You don't have Dragon Blood in your inventory!", 5000));
				getInventory().unEquipItemInBodySlot(ItemTemplate.SLOT_LR_HAND);
				broadcastUserInfo();
				sendPacket(new ItemList(Player.this, false));
			} else {
				Item dw = getActiveWeaponInstance();
				sendPacket(new ExShowScreenMessage("Your " + dw.getName() + " has just consumed 1 Dragon Blood", 5000));
			}
		}, 10L, 60000L);
	}
}
