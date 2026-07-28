package com.megacrit.cardcrawl.events.city;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.helpers.PotionHelper;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.vfx.RainingGoldEffect;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class KnowingSkull
        extends AbstractImageEvent {
    private static final Logger logger = LogManager.getLogger(KnowingSkull.class.getName());
    public static final String ID = "Knowing Skull";
    private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Knowing Skull");
    public static final String NAME = eventStrings.NAME;
    public static final String[] DESCRIPTIONS = eventStrings.DESCRIPTIONS;
    public static final String[] OPTIONS = eventStrings.OPTIONS;

    private static final String INTRO_MSG = DESCRIPTIONS[0];
    private static final String INTRO_2_MSG = DESCRIPTIONS[1];
    private static final String ASK_AGAIN_MSG = DESCRIPTIONS[2];
    private static final String POTION_MSG = DESCRIPTIONS[4];
    private static final String CARD_MSG = DESCRIPTIONS[5];
    private static final String GOLD_MSG = DESCRIPTIONS[6];
    private static final String LEAVE_MSG = DESCRIPTIONS[7];

    private int potionCost;
    private int cardCost;
    private CurScreen screen = CurScreen.INTRO_1;
    private int goldCost;
    private int leaveCost;
    private static final int GOLD_REWARD = 90;
    private String optionsChosen = "";

    private int damageTaken;
    private int goldEarned;
    private List<String> potions;
    private List<String> cards;
    private ArrayList<Reward> options = new ArrayList<>();

    private enum CurScreen {
        INTRO_1, ASK, COMPLETE;
    }

    private enum Reward {
        POTION, LEAVE, GOLD, CARD;
    }

    public KnowingSkull() {
        super(NAME, INTRO_MSG, "images/events/knowingSkull.jpg");
        this.imageEventText.setDialogOption(OPTIONS[0]);
        this.options.add(Reward.CARD);
        this.options.add(Reward.GOLD);
        this.options.add(Reward.POTION);
        this.options.add(Reward.LEAVE);

        this.leaveCost = 6;
        this.cardCost = this.leaveCost;
        this.potionCost = this.leaveCost;
        this.goldCost = this.leaveCost;

        this.damageTaken = 0;
        this.goldEarned = 0;
        this.cards = new ArrayList<>();
        this.potions = new ArrayList<>();
    }

    public void onEnterRoom() {
        if (Settings.AMBIANCE_ON) {
            CardCrawlGame.sound.play("EVENT_SKULL");
        }
    }

    protected void buttonEffect(int buttonPressed) {
        switch (this.screen) {
            case INTRO_1:
                this.imageEventText.updateBodyText(INTRO_2_MSG);
                this.imageEventText.clearAllDialogs();
                this.imageEventText.setDialogOption(OPTIONS[4] + this.potionCost + OPTIONS[1]);
                this.imageEventText.setDialogOption(OPTIONS[5] + 'Z' + OPTIONS[6] + this.goldCost + OPTIONS[1]);
                this.imageEventText.setDialogOption(OPTIONS[3] + this.cardCost + OPTIONS[1]);
                this.imageEventText.setDialogOption(OPTIONS[7] + this.leaveCost + OPTIONS[1]);
                this.screen = CurScreen.ASK;
                break;
            case ASK:
                CardCrawlGame.sound.play("DEBUFF_2");
                switch (buttonPressed) {
                    case 0:
                        obtainReward(0);
                        break;
                    case 1:
                        obtainReward(1);
                        break;
                    case 2:
                        obtainReward(2);
                        break;
                }
                AbstractDungeon.player.damage(new DamageInfo(null, this.leaveCost, DamageInfo.DamageType.HP_LOSS));
                this.damageTaken += this.leaveCost;
                setLeave();
                break;

            case COMPLETE:
                logMetric("Knowing Skull", this.optionsChosen, this.cards, null, null, null, null, this.potions, null,
                        this.damageTaken, 0, 0, 0, this.goldEarned, 0);

                openMap();
                break;
        }
    }
    private void obtainReward(int slot) {
        AbstractPotion p;
        AbstractCard c;
        switch (slot) {
            case 0:
                AbstractDungeon.player.damage(new DamageInfo(null, this.potionCost, DamageInfo.DamageType.HP_LOSS));
                this.damageTaken += this.potionCost;
                this.potionCost++;
                this.optionsChosen += "POTION ";
                this.imageEventText.updateBodyText(POTION_MSG + ASK_AGAIN_MSG);
                if (AbstractDungeon.player.hasRelic("Sozu")) {
                    AbstractDungeon.player.getRelic("Sozu").flash();
                    break;
                }
                p = PotionHelper.getRandomPotion();
                this.potions.add(p.ID);
                AbstractDungeon.player.obtainPotion(p);
                break;

            case 1:
                AbstractDungeon.player.damage(new DamageInfo(null, this.goldCost, DamageInfo.DamageType.HP_LOSS));
                this.damageTaken += this.goldCost;
                this.goldCost++;
                this.optionsChosen += "GOLD ";
                this.imageEventText.updateBodyText(GOLD_MSG + ASK_AGAIN_MSG);
                AbstractDungeon.effectList.add(new RainingGoldEffect(90));
                AbstractDungeon.player.gainGold(90);
                this.goldEarned += 90;
                break;
            case 2:
                AbstractDungeon.player.damage(new DamageInfo(null, this.cardCost, DamageInfo.DamageType.HP_LOSS));
                this.damageTaken += this.cardCost;
                this.cardCost++;
                this.optionsChosen += "CARD ";
                this.imageEventText.updateBodyText(CARD_MSG + ASK_AGAIN_MSG);
                c = AbstractDungeon.returnColorlessCard(AbstractCard.CardRarity.UNCOMMON).makeCopy();
                this.cards.add(c.cardID);
                AbstractDungeon.effectList
                        .add(new ShowCardAndObtainEffect(c, Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F));
                break;

            default:
                logger.info("This should never happen.");
                break;
        }
        this.imageEventText.clearAllDialogs();
        this.imageEventText.setDialogOption(OPTIONS[4] + this.potionCost + OPTIONS[1]);
        this.imageEventText.setDialogOption(OPTIONS[5] + 'Z' + OPTIONS[6] + this.goldCost + OPTIONS[1]);
        this.imageEventText.setDialogOption(OPTIONS[3] + this.cardCost + OPTIONS[1]);
        this.imageEventText.setDialogOption(OPTIONS[7] + this.leaveCost + OPTIONS[1]);
    }

    private void setLeave() {
        this.imageEventText.updateBodyText(LEAVE_MSG);
        this.imageEventText.clearAllDialogs();
        this.imageEventText.setDialogOption(OPTIONS[8]);
        this.screen = CurScreen.COMPLETE;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\events\city\
 * KnowingSkull.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

