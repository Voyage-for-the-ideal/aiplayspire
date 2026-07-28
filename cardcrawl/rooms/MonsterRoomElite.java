package com.megacrit.cardcrawl.rooms;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.unique.IncreaseMaxHpAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ModHelper;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.MetallicizePower;
import com.megacrit.cardcrawl.powers.RegenerateMonsterPower;
import com.megacrit.cardcrawl.powers.StrengthPower;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rewards.RewardItem;

public class MonsterRoomElite
        extends MonsterRoom {
    public void applyEmeraldEliteBuff() {
        if (Settings.isFinalActAvailable && (AbstractDungeon.getCurrMapNode()).hasEmeraldKey) {
            switch (AbstractDungeon.mapRng.random(0, 3)) {
                case 0:
                    for (AbstractMonster m : this.monsters.monsters) {
                        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyPowerAction(
                                (AbstractCreature) m, (AbstractCreature) m,
                                (AbstractPower) new StrengthPower((AbstractCreature) m, AbstractDungeon.actNum + 1),
                                AbstractDungeon.actNum + 1));
                    }
                    break;

                case 1:
                    for (AbstractMonster m : this.monsters.monsters) {
                        AbstractDungeon.actionManager
                                .addToBottom((AbstractGameAction) new IncreaseMaxHpAction(m, 0.25F, true));
                    }
                    break;
                case 2:
                    for (AbstractMonster m : this.monsters.monsters) {
                        AbstractDungeon.actionManager
                                .addToBottom(
                                        (AbstractGameAction) new ApplyPowerAction((AbstractCreature) m,
                                                (AbstractCreature) m,
                                                (AbstractPower) new MetallicizePower((AbstractCreature) m,
                                                        AbstractDungeon.actNum * 2 + 2),
                                                AbstractDungeon.actNum * 2 + 2));
                    }
                    break;

                case 3:
                    for (AbstractMonster m : this.monsters.monsters) {
                        AbstractDungeon.actionManager.addToBottom(
                                (AbstractGameAction) new ApplyPowerAction((AbstractCreature) m, (AbstractCreature) m,
                                        (AbstractPower) new RegenerateMonsterPower(m, 1 + AbstractDungeon.actNum * 2),
                                        1 + AbstractDungeon.actNum * 2));
                    }
                    break;
            }
        }
    }

    public void onPlayerEntry() {
        playBGM(null);
        if (this.monsters == null) {
            this.monsters = CardCrawlGame.dungeon.getEliteMonsterForRoomCreation();
            this.monsters.init();
        }

        waitTimer = 0.1F;
    }

    public void dropReward() {
        AbstractRelic.RelicTier tier = returnRandomRelicTier();
        if (Settings.isEndless && AbstractDungeon.player.hasBlight("MimicInfestation")) {

            AbstractDungeon.player.getBlight("MimicInfestation").flash();
        } else {
            addRelicToRewards(tier);
            if (AbstractDungeon.player.hasRelic("Black Star")) {
                addNoncampRelicToRewards(returnRandomRelicTier());
            }

            addEmeraldKey();
        }
    }

    private void addEmeraldKey() {
        if (Settings.isFinalActAvailable && !Settings.hasEmeraldKey && !this.rewards.isEmpty() &&
                (AbstractDungeon.getCurrMapNode()).hasEmeraldKey) {
            this.rewards
                    .add(new RewardItem(this.rewards.get(this.rewards.size() - 1), RewardItem.RewardType.EMERALD_KEY));
        }
    }

    private AbstractRelic.RelicTier returnRandomRelicTier() {
        int roll = AbstractDungeon.relicRng.random(0, 99);

        if (ModHelper.isModEnabled("Elite Swarm")) {
            roll += 10;
        }

        if (roll < 50) {
            return AbstractRelic.RelicTier.COMMON;
        }
        if (roll > 82) {
            return AbstractRelic.RelicTier.RARE;
        }

        return AbstractRelic.RelicTier.UNCOMMON;
    }

    public AbstractCard.CardRarity getCardRarity(int roll) {
        if (ModHelper.isModEnabled("Elite Swarm")) {
            return AbstractCard.CardRarity.RARE;
        }

        return super.getCardRarity(roll);
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\rooms\
 * MonsterRoomElite.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

