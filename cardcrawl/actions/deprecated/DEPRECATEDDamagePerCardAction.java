package com.megacrit.cardcrawl.actions.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DEPRECATEDDamagePerCardAction
        extends AbstractGameAction {
    private static final Logger logger = LogManager.getLogger(DEPRECATEDDamagePerCardAction.class.getName());

    private DamageInfo info;

    private String cardName;

    public DEPRECATEDDamagePerCardAction(AbstractCreature target, DamageInfo info, String cardName,
            AttackEffect effect) {
        this.info = info;
        this.cardName = cardName;
        this.attackEffect = effect;

        setValues(target, info);
        this.actionType = ActionType.DAMAGE;
    }

    public void update() {
        if (!this.isDone) {
            this.isDone = true;
            for (AbstractCard c : AbstractDungeon.player.hand.group) {
                if (c.originalName.equals(this.cardName)) {
                    logger.info("QUEUED DAMAGE...");
                    AbstractDungeon.actionManager
                            .addToTop((AbstractGameAction) new DamageAction(this.target, this.info, this.attackEffect));
                }
            }
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\deprecated
 * \DEPRECATEDDamagePerCardAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */



