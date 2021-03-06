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

package l2server.gameserver.ai.aplayer;

import l2server.gameserver.ai.CtrlIntention;
import l2server.gameserver.model.Abnormal;
import l2server.gameserver.model.Skill;
import l2server.gameserver.model.actor.Creature;
import l2server.gameserver.model.actor.Playable;
import l2server.gameserver.model.actor.instance.Player;
import l2server.gameserver.templates.skills.SkillTargetType;
import l2server.gameserver.templates.skills.SkillType;

/**
 * @author Pere
 */
public class AEnchanterAI extends APlayerAI {
	private static final int[] SONATAS = {11529, 11530, 11532};
	private static final int ASSAULT_RUSH = 11508;
	
	public AEnchanterAI(Creature creature) {
		super(creature);
	}
	
	@Override
	protected int[] getRandomGear() {
		return new int[]{30267, 19698, 19699, 19700, 19701, 19702, 19464, 19463, 19458, 17623, 35570, 34860, 19462, 19454, 35846, 30316};
	}
	
	@Override
	protected boolean interactWith(Creature target) {
		if (super.interactWith(target)) {
			return true;
		}
		
		if (player.getCurrentMp() > player.getMaxMp() * 0.7 || player.getCurrentHp() < player.getMaxHp() * 0.5 ||
				player.getTarget() instanceof Playable) {
			// First, let's try to Rush
			if (target != null && 600 - player.getDistanceSq(target) > 100) {
				Skill skill = player.getKnownSkill(ASSAULT_RUSH);
				
				if (skill != null) {
					player.useMagic(skill, true, false);
				}
			}
			
			// Then, let's attack!
			for (Skill skill : player.getAllSkills()) {
				if (!skill.isOffensive() || skill.getTargetType() != SkillTargetType.TARGET_ONE) {
					continue;
				}
				
				if (player.useMagic(skill, true, false)) {
					break;
				}
			}
		}
		
		setIntention(CtrlIntention.AI_INTENTION_ATTACK, target);
		
		return true;
	}
	
	private boolean checkBuffs(Player partner) {
		if (partner.isDead()) {
			return false;
		}
		
		// Check the sonatas
		for (int sonata : SONATAS) {
			boolean hasBuff = false;
			for (Abnormal e : partner.getAllEffects()) {
				if (e.getSkill().getId() == sonata /*&& e.getTime() > 30*/) {
					hasBuff = true;
					break;
				}
			}
			
			if (!hasBuff) {
				Skill skill = player.getKnownSkill(sonata);
				if (skill != null && player.useMagic(skill, true, false)) {
					return false;
				}
			}
		}
		
		return true;
	}
	
	@Override
	protected void think() {
		super.think();
		
		if (player.getParty() == null) {
			return;
		}
		
		int memberCount = 0;
		Player mostHarmed = null;
		int leastHealth = 100;
		int totalHealth = 0;
		for (Player member : player.getParty().getPartyMembers()) {
			if (player.getDistanceSq(member) > 1000 * 1000) {
				continue;
			}
			
			checkBuffs(member);
			
			int health = (int) (member.getCurrentHp() * 100 / member.getMaxHp());
			if (health < leastHealth) {
				leastHealth = health;
				mostHarmed = member;
			}
			
			totalHealth += health;
			memberCount++;
		}
		
		int meanHealth = totalHealth / memberCount;
		
		if (meanHealth < 80 || leastHealth < 60) {
			player.setTarget(mostHarmed);
			
			for (Skill skill : player.getAllSkills()) {
				if (skill.getSkillType() != SkillType.HEAL && skill.getSkillType() != SkillType.HEAL_STATIC &&
						skill.getSkillType() != SkillType.HEAL_PERCENT && skill.getSkillType() != SkillType.CHAIN_HEAL &&
						skill.getSkillType() != SkillType.OVERHEAL || skill.getTargetType() != SkillTargetType.TARGET_ONE &&
						(skill.getTargetType() != SkillTargetType.TARGET_SELF || mostHarmed != player) &&
						(skill.getTargetType() != SkillTargetType.TARGET_PARTY_OTHER || mostHarmed == player) &&
						skill.getTargetType() != SkillTargetType.TARGET_PARTY_MEMBER) {
					continue;
				}
				
				if (player.useMagic(skill, true, false)) {
					break;
				}
			}
		}
	}
}
