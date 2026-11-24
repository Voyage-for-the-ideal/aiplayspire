import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.actions.common.DiscardAction;
import com.megacrit.cardcrawl.actions.unique.RetainCardsAction;
import com.megacrit.cardcrawl.actions.utility.ScryAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.headless.GameKernel;
import com.megacrit.cardcrawl.screens.GameOverScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Isolated future driver: recognize the settled victory screen as a decision. */
public class HeadlessFlowKernel extends GameKernel {
    private com.megacrit.cardcrawl.rooms.AbstractRoom declinedBossRoom;
    private ShopRewardParent cardRewardParent;
    private com.megacrit.cardcrawl.rewards.RewardItem nestedCardReward;
    private DrawInsertion pendingInsertion;
    private Map<String,Object> observedInsertion;
    private SelectionCommit pendingSelection;
    private Map<String,Object> observedSelection;

    private record DrawInsertion(com.megacrit.cardcrawl.actions.unique.CodexAction source,
            com.megacrit.cardcrawl.cards.AbstractCard offer, int index) {}

    private enum SelectionKind { SCRY, RETAIN, DISCARD }

    private record SelectionCommit(AbstractGameAction source,SelectionKind kind,List<AbstractCard> selected) {}

    private record SelectionStep(SelectionCommit choice,float duration,int turn,int discarded,
            List<AbstractCard> draw,List<AbstractCard> hand,List<AbstractCard> discard) {}

    /** A real observed SHOP owner, never inferred from relic ownership alone. */
    private static final class ShopRewardParent {
        final com.megacrit.cardcrawl.characters.AbstractPlayer player=AbstractDungeon.player;
        final com.megacrit.cardcrawl.map.MapRoomNode node=AbstractDungeon.currMapNode;
        final com.megacrit.cardcrawl.rooms.AbstractRoom room=AbstractDungeon.getCurrRoom();
        final com.megacrit.cardcrawl.shop.ShopScreen shop=AbstractDungeon.shopScreen;
        final com.megacrit.cardcrawl.screens.CombatRewardScreen rewards=AbstractDungeon.combatRewardScreen;
        final com.megacrit.cardcrawl.screens.CardRewardScreen cards=AbstractDungeon.cardRewardScreen;
        final int floor=AbstractDungeon.floorNum,act=AbstractDungeon.actNum;
        boolean matches() {
            return player==AbstractDungeon.player && node==AbstractDungeon.currMapNode
                && node!=null && room==AbstractDungeon.getCurrRoom() && shop==AbstractDungeon.shopScreen
                && rewards==AbstractDungeon.combatRewardScreen && cards==AbstractDungeon.cardRewardScreen
                && floor==AbstractDungeon.floorNum && act==AbstractDungeon.actNum;
        }
    }

    private void clearCardRewardParent() { cardRewardParent=null;nestedCardReward=null; }

    @Override
    public void newRun(long seed,com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass character,int ascension) {
        clearCardRewardParent();declinedBossRoom=null;
        super.newRun(seed,character,ascension);
    }

    private static boolean inShop() {
        return AbstractDungeon.player!=null && AbstractDungeon.currMapNode!=null
            && AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.ShopRoom
            && AbstractDungeon.screen==AbstractDungeon.CurrentScreen.SHOP;
    }

    private static void restoreShopRewardParent(ShopRewardParent parent,Phase result) {
        if(parent==null || !parent.matches() || result!=Phase.REWARD
                || AbstractDungeon.screen!=AbstractDungeon.CurrentScreen.COMBAT_REWARD || !cancelVisible())return;
        var previous=AbstractDungeon.previousScreen;
        if(previous!=null && previous!=AbstractDungeon.CurrentScreen.COMBAT_REWARD && previous!=AbstractDungeon.CurrentScreen.SHOP)
            throw new IllegalStateException("Unexpected shop reward parent screen");
        // Orrery.open() omits this parent; RewardItem.claimReward() replaces
        // it with COMBAT_REWARD for the nested picker. Restore only the owner
        // captured before the real source action, without touching gameplay.
        AbstractDungeon.previousScreen=AbstractDungeon.CurrentScreen.SHOP;
    }

    @Override
    public Phase buyRelic(int index) {
        ShopRewardParent parent=inShop()?new ShopRewardParent():null;
        clearCardRewardParent();
        Phase result=super.buyRelic(index);
        restoreShopRewardParent(parent,result);
        return result;
    }

    @Override
    public Phase openCardReward(int index) {
        ShopRewardParent parent=canReturnToShop()?new ShopRewardParent():null;
        var rewards=AbstractDungeon.combatRewardScreen.rewards;
        var requested=index>=0 && index<rewards.size()?rewards.get(index):null;
        clearCardRewardParent();
        Phase result=super.openCardReward(index);
        if(parent!=null && parent.matches() && result==Phase.CARD_PICK
                && AbstractDungeon.screen==AbstractDungeon.CurrentScreen.CARD_REWARD
                && requested!=null && AbstractDungeon.cardRewardScreen.rItem==requested) {
            cardRewardParent=parent;nestedCardReward=requested;
        }
        return result;
    }

    private Phase finishCardReward(java.util.function.Supplier<Phase> action) {
        ShopRewardParent parent=cardRewardParent;
        boolean bound=parent!=null && parent.matches()
            && AbstractDungeon.screen==AbstractDungeon.CurrentScreen.CARD_REWARD
            && AbstractDungeon.previousScreen==AbstractDungeon.CurrentScreen.COMBAT_REWARD
            && AbstractDungeon.cardRewardScreen.rItem==nestedCardReward;
        clearCardRewardParent(); // Exceptions and foreign screens also consume the pending binding.
        Phase result=action.get();
        if(bound)restoreShopRewardParent(parent,result);
        return result;
    }

    @Override public Phase chooseRewardCard(int index) {
        observedInsertion=null;
        if(phase()!=Phase.CARD_PICK || !(AbstractDungeon.actionManager.currentAction
                instanceof com.megacrit.cardcrawl.actions.unique.CodexAction source))
            return finishCardReward(()->super.chooseRewardCard(index));
        var screen=AbstractDungeon.cardRewardScreen;
        var offers=(java.util.List<?>)field(screen,"rewardGroup");
        var offer=(com.megacrit.cardcrawl.cards.AbstractCard)offers.get(index);
        pendingInsertion=new DrawInsertion(source,offer,index);
        try { return finishCardReward(()->super.chooseRewardCard(index)); }
        finally { pendingInsertion=null; }
    }

    Map<String,Object> takeObservedInsertion() {
        var result=observedInsertion;
        observedInsertion=null;
        return result;
    }

    Map<String,Object> takeObservedSelection() {
        var result=observedSelection;
        observedSelection=null;
        return result;
    }

    @Override public Phase confirmGrid() {
        if(AbstractDungeon.screen!=AbstractDungeon.CurrentScreen.GRID || phase()!=Phase.CARD_SELECT
                || !(AbstractDungeon.actionManager.currentAction instanceof ScryAction source))return super.confirmGrid();
        return watchSelection(new SelectionCommit(source,SelectionKind.SCRY,
            List.copyOf(AbstractDungeon.gridSelectScreen.selectedCards)),super::confirmGrid);
    }

    @Override public Phase confirmHandSelect() {
        if(AbstractDungeon.screen!=AbstractDungeon.CurrentScreen.HAND_SELECT || phase()!=Phase.CARD_SELECT
                || AbstractDungeon.handCardSelectScreen.wereCardsRetrieved)return super.confirmHandSelect();
        var source=AbstractDungeon.actionManager.currentAction;
        var selected=List.copyOf(AbstractDungeon.handCardSelectScreen.selectedCards.group);
        if(source instanceof RetainCardsAction)
            return watchSelection(new SelectionCommit(source,SelectionKind.RETAIN,selected),super::confirmHandSelect);
        if(source instanceof DiscardAction && !selected.isEmpty() && !(Boolean)field(source,"isRandom") && !(Boolean)field(source,"endTurn"))
            return watchSelection(new SelectionCommit(source,SelectionKind.DISCARD,selected),super::confirmHandSelect);
        return super.confirmHandSelect();
    }

    private Phase watchSelection(SelectionCommit selection,java.util.function.Supplier<Phase> command) {
        observedSelection=null;pendingSelection=selection;
        try { return command.get(); }
        finally { pendingSelection=null; }
    }

    private SelectionStep observeSelectionStart() {
        var selection=pendingSelection;
        if(selection==null || observedSelection!=null || AbstractDungeon.isScreenUp
                || AbstractDungeon.actionManager.currentAction!=selection.source() || selection.source().isDone)return null;
        var player=AbstractDungeon.player;
        return new SelectionStep(selection,(Float)field(selection.source(),"duration"),GameActionManager.turn,
            GameActionManager.totalDiscardedThisTurn,List.copyOf(player.drawPile.group),List.copyOf(player.hand.group),
            List.copyOf(player.discardPile.group));
    }

    private void observeSelectionEnd(SelectionStep before) {
        if(before==null || before.turn()!=GameActionManager.turn
                || (Float)field(before.choice().source(),"duration")>=before.duration())return;
        var choice=before.choice();var player=AbstractDungeon.player;
        var drawn=new ArrayList<>(before.draw());var hand=new ArrayList<>(before.hand());var discard=new ArrayList<>(before.discard());
        String destination;
        switch(choice.kind()) {
            case SCRY -> {
                if(!AbstractDungeon.gridSelectScreen.selectedCards.isEmpty() || !drawn.containsAll(choice.selected()))return;
                drawn.removeAll(choice.selected());discard.addAll(choice.selected());destination="discard_pile";
            }
            case RETAIN -> {
                if(!AbstractDungeon.handCardSelectScreen.wereCardsRetrieved
                        || choice.selected().stream().anyMatch(c->!c.isEthereal && !c.retain))return;
                hand.addAll(choice.selected());destination="retained_hand";
            }
            case DISCARD -> {
                if(!AbstractDungeon.handCardSelectScreen.wereCardsRetrieved
                        || GameActionManager.totalDiscardedThisTurn!=before.discarded()+choice.selected().size())return;
                discard.addAll(choice.selected());destination="discard_pile";
            }
            default -> throw new AssertionError(choice.kind());
        }
        if(!drawn.equals(player.drawPile.group) || !hand.equals(player.hand.group) || !discard.equals(player.discardPile.group))return;
        observedSelection=HeadlessInteractionExport.object("source_action",choice.source().getClass().getSimpleName(),
            "selected_uuids",choice.selected().stream().map(c->c.uuid.toString()).toList(),"destination",destination,"turn",before.turn());
    }
    @Override public Phase skipRewardCard() { return finishCardReward(()->super.skipRewardCard()); }
    @Override public Phase chooseSingingBowl() { return finishCardReward(()->super.chooseSingingBowl()); }

    private static Object field(Object target,String name) {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass()) {
            try { var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target); }
            catch(NoSuchFieldException error) { /* Check the source superclass. */ }
            catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
        throw new IllegalStateException("Missing source field "+name);
    }

    public static boolean cancelVisible() {
        return AbstractDungeon.overlayMenu!=null && AbstractDungeon.overlayMenu.cancelButton!=null
            && !((Boolean)field(AbstractDungeon.overlayMenu.cancelButton,"isHidden"));
    }

    /** Read-only evidence for Orrery's reward overlay over the current shop. */
    public static boolean canReturnToShop() {
        return AbstractDungeon.player!=null && AbstractDungeon.currMapNode!=null
            && AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.ShopRoom
            && AbstractDungeon.screen==AbstractDungeon.CurrentScreen.COMBAT_REWARD
            && AbstractDungeon.previousScreen==AbstractDungeon.CurrentScreen.SHOP && cancelVisible();
    }

    // Tiny House opens a nested combat reward from the chosen boss chest. The
    // original kernel routes proceed in that room directly to startNextAct().
    public static boolean canLeaveTinyHouseReward() {
        if (AbstractDungeon.player == null || AbstractDungeon.currMapNode == null
                || !(AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.TreasureRoomBoss boss)
                || !boss.choseRelic || boss.chest == null || !boss.chest.isOpen
                || AbstractDungeon.screen != AbstractDungeon.CurrentScreen.COMBAT_REWARD
                || !cancelVisible() || !AbstractDungeon.player.hasRelic("Tiny House")) return false;
        var history = com.megacrit.cardcrawl.core.CardCrawlGame.metricData.boss_relics;
        return history.size() == AbstractDungeon.actNum
                && "Tiny House".equals(history.get(history.size()-1).get("picked"));
    }

    @Override
    public Phase proceed() {
        if(canReturnToShop()) {
            // CancelButton.update hides first, clears screenSwap, then uses
            // the original screen stack. ShopScreen.open re-shows its own
            // cancel control; hiding after close would hide the new button.
            AbstractDungeon.overlayMenu.cancelButton.hide();
            AbstractDungeon.screenSwap=false;
            AbstractDungeon.closeCurrentScreen();
            return drainUntilDecision();
        }
        return super.proceed();
    }

    @Override
    public Phase chooseDialogOption(int index) {
        var room=AbstractDungeon.getCurrRoom();
        var before=AbstractDungeon.deathScreen;
        Phase result=super.chooseDialogOption(index);
        if (result==Phase.GAME_OVER && room==AbstractDungeon.getCurrRoom()
                && room instanceof com.megacrit.cardcrawl.rooms.EventRoom
                && AbstractDungeon.player.isDead && AbstractDungeon.player.currentHealth==0
                && !GameOverScreen.isVictory && AbstractDungeon.deathScreen!=null
                && AbstractDungeon.deathScreen!=before && AbstractDungeon.screen==AbstractDungeon.CurrentScreen.COMBAT_REWARD)
            AbstractDungeon.screen=AbstractDungeon.CurrentScreen.DEATH;
        return result;
    }

    @Override
    public Phase chooseGridCard(int cardIndex) {
        if(phase()!=Phase.CARD_SELECT || AbstractDungeon.screen!=AbstractDungeon.CurrentScreen.GRID)
            throw new IllegalStateException("not in grid select phase");
        var grid=AbstractDungeon.gridSelectScreen;
        var player=AbstractDungeon.player;
        if(isNeowAstrolabeGrid() && grid.selectedCards.size()==2
                && cardIndex>=0 && cardIndex<grid.targetGroup.size()
                && !grid.selectedCards.contains(grid.targetGroup.group.get(cardIndex))) {
            var astrolabe=player.getRelic("Astrolabe");
            // NeowReward.update unconditionally clears selectedCards, also
            // for the BOSS_RELIC reward that opened Astrolabe's picker.
            grid.selectCardHeadless(cardIndex);
            if(grid.selectedCards.size()!=3 || AbstractDungeon.screen==AbstractDungeon.CurrentScreen.GRID)
                throw new IllegalStateException("Astrolabe choice did not close its grid");
            astrolabe.update();
            if(!((Boolean)field(astrolabe,"cardsSelected")) || !grid.selectedCards.isEmpty())
                throw new IllegalStateException("Astrolabe did not consume its source selection");
            return drainUntilDecision(); // The base detector waits for card-obtain effects.
        }
        var room=AbstractDungeon.getCurrRoom();
        var cage=player.getRelic("Empty Cage");
        if(room instanceof com.megacrit.cardcrawl.neow.NeowRoom
                && room.event instanceof com.megacrit.cardcrawl.neow.NeowEvent
                && AbstractDungeon.floorNum==0 && AbstractDungeon.actNum==1
                && ((Integer)field(room.event,"screenNum"))==99
                && "BOSS_RELIC".equals(com.megacrit.cardcrawl.core.CardCrawlGame.metricData.neowBonus)
                && room.phase==com.megacrit.cardcrawl.rooms.AbstractRoom.RoomPhase.INCOMPLETE
                && cage instanceof com.megacrit.cardcrawl.relics.EmptyCage
                && !((Boolean)field(cage,"cardsSelected")) && grid.forPurge
                && ((Integer)field(grid,"numCards"))==2 && grid.selectedCards.size()==1
                && !grid.anyNumber && !grid.confirmScreenUp && grid.targetGroup!=null
                && grid.targetGroup.group.stream().allMatch(player.masterDeck.group::contains)
                && cardIndex>=0 && cardIndex<grid.targetGroup.size()
                && !grid.selectedCards.contains(grid.targetGroup.group.get(cardIndex))) {
            // NeowReward.update clears a final grid selection before the relic
            // update can consume it. Let its actual owner perform both removals.
            grid.selectCardHeadless(cardIndex);
            if(grid.selectedCards.size()!=2 || AbstractDungeon.screen==AbstractDungeon.CurrentScreen.GRID)
                throw new IllegalStateException("Empty Cage choice did not close its grid");
            cage.update();
            if(!((Boolean)field(cage,"cardsSelected")) || !grid.selectedCards.isEmpty())
                throw new IllegalStateException("Empty Cage did not consume its source selection");
            // EmptyCage normally completes a boss chest. At floor zero, the
            // original Neow leave control still owns the next room transition.
            room.phase=com.megacrit.cardcrawl.rooms.AbstractRoom.RoomPhase.EVENT;
            return drainUntilDecision();
        }
        var mirror=player.getRelic("DollysMirror");
        boolean singleShopChoice=room instanceof com.megacrit.cardcrawl.rooms.ShopRoom
            && !grid.forUpgrade && !grid.forTransform && !grid.forPurge && !grid.forClarity
            && !grid.anyNumber && !grid.isJustForConfirming && !grid.confirmScreenUp
            && ((Integer)field(grid,"numCards"))==1 && grid.selectedCards.isEmpty();
        if (singleShopChoice) {
            var bottleTypes=java.util.Map.of(
                "Bottled Flame",com.megacrit.cardcrawl.cards.AbstractCard.CardType.ATTACK,
                "Bottled Lightning",com.megacrit.cardcrawl.cards.AbstractCard.CardType.SKILL,
                "Bottled Tornado",com.megacrit.cardcrawl.cards.AbstractCard.CardType.POWER);
            var bottles=player.relics.stream().filter(r->bottleTypes.containsKey(r.relicId)
                    && !((Boolean)field(r,"cardSelected"))).toList();
            if (!bottles.isEmpty()) {
                if (bottles.size()!=1 || grid.targetGroup==null || cardIndex<0 || cardIndex>=grid.targetGroup.size())
                    throw new IllegalStateException("Invalid shop bottle source or grid index");
                var bottle=bottles.get(0);
                var candidates=player.masterDeck.getPurgeableCards();
                var expected=switch (bottleTypes.get(bottle.relicId)) {
                    case ATTACK -> candidates.getAttacks();
                    case SKILL -> candidates.getSkills();
                    case POWER -> candidates.getPowers();
                    default -> throw new IllegalStateException("Invalid shop bottle type");
                };
                if (!grid.targetGroup.group.equals(expected.group))
                    throw new IllegalStateException("Bottle options differ from the source deck");
                var chosen=grid.targetGroup.group.get(cardIndex);
                grid.selectCardHeadless(cardIndex);
                if (grid.selectedCards.size()!=1 || grid.selectedCards.get(0)!=chosen)
                    throw new IllegalStateException("Bottle choice did not settle");
                bottle.update();
                if (!((Boolean)field(bottle,"cardSelected")) || field(bottle,"card")!=chosen
                        || !grid.selectedCards.isEmpty() || !player.masterDeck.contains(chosen))
                    throw new IllegalStateException("Bottle did not consume its source selection");
                return drainUntilDecision();
            }
        }
        boolean mirrorSelection=singleShopChoice && mirror instanceof com.megacrit.cardcrawl.relics.DollysMirror
            && !((Boolean)field(mirror,"cardSelected")) && grid.targetGroup==player.masterDeck
            && grid.targetGroup!=null;
        if(!mirrorSelection)return super.chooseGridCard(cardIndex);
        // The direct headless click closes GRID to SHOP before a frame update.
        // ShopScreen.updatePurge then consumes every selectedCards reference,
        // even though this is not a purge. Let the real owning relic consume
        // its choice first, exactly once, before the normal frame is drained.
        grid.selectCardHeadless(cardIndex);
        if(grid.selectedCards.size()!=1)throw new IllegalStateException("Mirror choice did not settle");
        mirror.update();
        if(!((Boolean)field(mirror,"cardSelected")) || !grid.selectedCards.isEmpty())
            throw new IllegalStateException("Mirror did not consume its source selection");
        return drainUntilDecision();
    }

    public static boolean isNeowAstrolabeGrid() {
        if(AbstractDungeon.player==null || AbstractDungeon.floorNum!=0 || AbstractDungeon.actNum!=1
                || AbstractDungeon.currMapNode==null || AbstractDungeon.screen!=AbstractDungeon.CurrentScreen.GRID
                || !(AbstractDungeon.getCurrRoom().event instanceof com.megacrit.cardcrawl.neow.NeowEvent))
            return false;
        var player=AbstractDungeon.player;
        var astrolabe=player.getRelic("Astrolabe");
        var g=AbstractDungeon.gridSelectScreen;
        return astrolabe instanceof com.megacrit.cardcrawl.relics.Astrolabe && !((Boolean)field(astrolabe,"cardsSelected"))
            && ((Integer)field(g,"numCards"))==3 && g.targetGroup!=null && !g.targetGroup.group.isEmpty()
            && g.targetGroup.group.stream().allMatch(player.masterDeck.group::contains)
            && !g.forUpgrade && !g.forTransform && !g.forPurge && !g.forClarity
            && !g.anyNumber && !g.isJustForConfirming && !g.confirmScreenUp;
    }
    @Override
    public Phase skipBossRelic() {
        if(phase()!=Phase.BOSS_RELIC)throw new IllegalStateException("not in boss relic phase");
        // Original cancel closes the chest; the definitive skip intent then
        // waits for the real ProceedButton, rather than reopening its offers.
        declinedBossRoom=AbstractDungeon.getCurrRoom();
        AbstractDungeon.bossRelicScreen.skipHeadless();
        return drainUntilDecision();
    }
    @Override
    public void advanceOneFrame() {
        var selectionStep=observeSelectionStart();
        var insertion=pendingInsertion;
        boolean watching=insertion!=null && observedInsertion==null
            && AbstractDungeon.actionManager.currentAction==insertion.source()
            && AbstractDungeon.cardRewardScreen.discoveryCard==insertion.offer();
        var drawBefore=watching?java.util.Set.copyOf(AbstractDungeon.player.drawPile.group):null;
        super.advanceOneFrame();
        observeSelectionEnd(selectionStep);
        if(watching && AbstractDungeon.cardRewardScreen.discoveryCard==null) {
            var added=AbstractDungeon.player.drawPile.group.stream().filter(c->!drawBefore.contains(c)).toList();
            if(added.size()==1) {
                var card=added.get(0);
                var offer=insertion.offer();
                boolean fromEffect=AbstractDungeon.effectList.stream().anyMatch(effect->
                    effect instanceof com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndAddToDrawPileEffect
                    && field(effect,"card")==card);
                if(fromEffect && card!=offer && card.cardID.equals(offer.cardID))
                    observedInsertion=HeadlessInteractionExport.object("source_action","CodexAction",
                        "index",insertion.index(),"offered_uuid",offer.uuid.toString(),"created_uuid",card.uuid.toString(),
                        "card_id",card.cardID,"upgrades",card.timesUpgraded,"destination","draw_pile");
            }
        }
        if(AbstractDungeon.currMapNode!=null && AbstractDungeon.waitingOnFadeOut
                && AbstractDungeon.getCurrRoom().event instanceof com.megacrit.cardcrawl.events.beyond.SecretPortal
                && AbstractDungeon.getCurrRoom().phase==com.megacrit.cardcrawl.rooms.AbstractRoom.RoomPhase.COMPLETE
                && AbstractDungeon.nextRoom!=null && AbstractDungeon.nextRoom.room instanceof com.megacrit.cardcrawl.rooms.MonsterRoomBoss) {
            // The headless frame loop has no dungeon fade callback. Take
            // the portal's already queued room exactly as a normal map click.
            AbstractDungeon.waitingOnFadeOut=false;
            com.megacrit.cardcrawl.core.CardCrawlGame.dungeon.nextRoomTransition();
        }
    }

    @Override
    public Phase phase() {
        if (AbstractDungeon.player != null && AbstractDungeon.currMapNode != null) {
            // Soul completion calls clearPowers()/resetAttributes(), including
            // temporary costs. A queued card animation is not a stable decision.
            if(com.megacrit.cardcrawl.cards.SoulGroup.isActive())return null;
            for(var effects:java.util.List.of(AbstractDungeon.effectList,AbstractDungeon.effectsQueue,
                    AbstractDungeon.topLevelEffects,AbstractDungeon.topLevelEffectsQueue)) {
                for(var effect:effects) {
                    if(!effect.isDone && effect.getClass().getSimpleName().equals("ExhaustCardEffect")) {
                        var card=(com.megacrit.cardcrawl.cards.AbstractCard)field(effect,"c");
                        if(card.costForTurn!=card.cost && AbstractDungeon.player.exhaustPile.contains(card))return null;
                    }
                }
            }
            var event=AbstractDungeon.getCurrRoom().event;
            if(event != null && event.getClass().getSimpleName().equals("GremlinMatchGame")) {
                String screen=HeadlessMatchGame.screen(event);
                if(screen.equals("PLAY") || screen.equals("CLEAN_UP")) {
                    for(var list:java.util.List.of(AbstractDungeon.effectList,AbstractDungeon.effectsQueue,AbstractDungeon.topLevelEffects,AbstractDungeon.topLevelEffectsQueue))
                        for(var effect:list) if(effect instanceof com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect || effect instanceof com.megacrit.cardcrawl.vfx.FastCardObtainEffect) return null;
                    return HeadlessMatchGame.ready(event)?Phase.EVENT:null;
                }
            }
            // CampfireRecall marks COMPLETE before its ObtainKeyEffect settles.
            for (var effect : AbstractDungeon.topLevelEffects)
                if (effect instanceof com.megacrit.cardcrawl.vfx.ObtainKeyEffect) return null;
            for (var effect : AbstractDungeon.topLevelEffectsQueue)
                if (effect instanceof com.megacrit.cardcrawl.vfx.ObtainKeyEffect) return null;
            var result = HeadlessFlowExport.terminal(AbstractDungeon.player.isDead,
                    AbstractDungeon.getCurrRoom().getClass().getSimpleName(), AbstractDungeon.screen.name(),
                    GameOverScreen.isVictory, AbstractDungeon.deathScreen != null,
                    AbstractDungeon.victoryScreen != null, AbstractDungeon.actNum);
            if (result != null) return result.get("outcome").equals("victory") ? Phase.RUN_COMPLETE : Phase.GAME_OVER;
        }
        Phase phase=super.phase();
        // Colosseum and Mind Bloom leave their image dialog's wait flag set when
        // enterCombatFromImage hides it. The phase detector would report
        // a second [Fight] during the opening queue and every later turn.
        if(phase==Phase.EVENT && AbstractDungeon.currMapNode!=null) {
            var room=AbstractDungeon.getCurrRoom();
            if(room.phase==com.megacrit.cardcrawl.rooms.AbstractRoom.RoomPhase.COMBAT
                    && (room.event instanceof com.megacrit.cardcrawl.events.city.Colosseum
                        || room.event instanceof com.megacrit.cardcrawl.events.beyond.MindBloom) && room.event.combatTime) {
                var manager=AbstractDungeon.actionManager;
                if(com.megacrit.cardcrawl.rooms.AbstractRoom.waitTimer>0.0f || room.isBattleOver
                        || room.monsters==null || room.monsters.areMonstersBasicallyDead()
                        || manager.phase!=com.megacrit.cardcrawl.actions.GameActionManager.Phase.WAITING_ON_USER
                        || !manager.actions.isEmpty() || !manager.preTurnActions.isEmpty()
                        || !manager.cardQueue.isEmpty() || !manager.monsterQueue.isEmpty() || manager.currentAction!=null)
                    return null;
                return manager.turnHasEnded ? Phase.MONSTER_TURN : Phase.PLAYER_TURN;
            }
        }
        if(phase==Phase.BOSS_CHEST && AbstractDungeon.getCurrRoom()==declinedBossRoom)phase=Phase.PROCEED;
        // COMPLETE precedes AbstractRoom's delayed ProceedButton.show().
        // Let that room update settle before exposing a user decision.
        if(phase==Phase.PROCEED) {
            try {
                var field=AbstractDungeon.overlayMenu.proceedButton.getClass().getDeclaredField("isHidden");field.setAccessible(true);
                if(field.getBoolean(AbstractDungeon.overlayMenu.proceedButton))return null;
            } catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
        return phase;
    }
    public Phase chooseMatchCard(int slot) {
        var event=AbstractDungeon.getCurrRoom().event;
        if(event==null || !event.getClass().getSimpleName().equals("GremlinMatchGame")) throw new IllegalStateException("not_match_game");
        HeadlessMatchGame.flip(event,slot);
        return drainUntilDecision();
    }
    @Override
    public Phase openChest() {
        Phase phase=phase();
        if(phase!=Phase.CHEST && phase!=Phase.BOSS_CHEST)throw new IllegalStateException("no_closed_chest");
        var room=AbstractDungeon.getCurrRoom();
        var chest=room instanceof com.megacrit.cardcrawl.rooms.TreasureRoomBoss?
            ((com.megacrit.cardcrawl.rooms.TreasureRoomBoss)room).chest:((com.megacrit.cardcrawl.rooms.TreasureRoom)room).chest;
        if(chest.isOpen)throw new IllegalStateException("chest_already_open");
        // Original UI calls keyRequirement() before open(); upstream's direct
        // binding omits that bookkeeping and permits duplicate chest rewards.
        try {
            var method=com.megacrit.cardcrawl.rewards.chests.AbstractChest.class.getDeclaredMethod("keyRequirement");method.setAccessible(true);
            if(!((Boolean)method.invoke(chest)))throw new IllegalStateException("chest_locked");
        } catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
        chest.open(phase==Phase.BOSS_CHEST);
        return drainUntilDecision();
    }
}

