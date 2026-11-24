import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.google.gson.GsonBuilder;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.OverlayMenu;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.headless.GameKernel;
import com.megacrit.cardcrawl.helpers.CardHelper;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.metrics.MetricData;
import com.megacrit.cardcrawl.relics.DollysMirror;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.ShopRoom;
import com.megacrit.cardcrawl.screens.CombatRewardScreen;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import com.megacrit.cardcrawl.shop.ShopScreen;
import com.megacrit.cardcrawl.ui.buttons.DynamicBanner;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Isolated static fixtures: no newRun/dungeon construction, original globals restored. */
public final class ShopFlowFixtureTest {
    private static final class LocalShop extends ShopScreen {
        int opens;
        @Override public void purchaseRelicHeadless(int index) {
            if(index!=0)throw new IllegalArgumentException("fixture relic index");
            // Replace purchase inventory only. The real source open/claim/close
            // screen methods below reproduce Orrery's missing parent stack.
            AbstractDungeon.combatRewardScreen.open("fixture Orrery rewards");
        }
        @Override public void open() {
            opens++;
            AbstractDungeon.screen=AbstractDungeon.CurrentScreen.SHOP;
            AbstractDungeon.isScreenUp=true;
            AbstractDungeon.overlayMenu.cancelButton.show("fixture shop");
        }
    }

    private static final class LocalTopPanel extends com.megacrit.cardcrawl.ui.panels.TopPanel {
        @Override public void unhoverHitboxes() { /* Presentation only. */ }
    }

    private static final class LocalRewardScreen extends CombatRewardScreen {
        @Override public void setupItemReward() {
            // Fixed offers avoid initializing any run RNG. Open/reopen, claim,
            // picker operations and the screen stack still use original code.
            rewards=new ArrayList<>(AbstractDungeon.getCurrRoom().rewards);
            hasTakenAll=false;positionRewards();
        }
    }

    private static final class LocalDriver extends HeadlessFlowKernel {
        int drains;
        boolean checkedObtainWait,checkedSoulWait;
        @Override public Phase phase() {
            return AbstractDungeon.screen==AbstractDungeon.CurrentScreen.GRID?Phase.CARD_SELECT
                :AbstractDungeon.screen==AbstractDungeon.CurrentScreen.CARD_REWARD?Phase.CARD_PICK
                :AbstractDungeon.screen==AbstractDungeon.CurrentScreen.SHOP?Phase.SHOP:Phase.REWARD;
        }
        @Override public Phase drainUntilDecision() {
            drains++;
            for(int i=0;i<2000;i++) {
                if(java.util.stream.Stream.of(AbstractDungeon.effectList,AbstractDungeon.effectsQueue,
                        AbstractDungeon.topLevelEffects,AbstractDungeon.topLevelEffectsQueue).flatMap(java.util.Collection::stream)
                        .anyMatch(e->e instanceof com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect
                            || e instanceof com.megacrit.cardcrawl.vfx.FastCardObtainEffect)) {
                    check(new HeadlessFlowKernel().phase()==null,"Runtime exposed a decision before card obtain");
                    checkedObtainWait=true;
                } else if(com.megacrit.cardcrawl.cards.SoulGroup.isActive()) {
                    check(new HeadlessFlowKernel().phase()==null,"Runtime exposed a decision before Soul completion");
                    checkedSoulWait=true;
                }
                // Exercise the real competing consumer on every local frame.
                AbstractDungeon.shopScreen.updatePurge();
                for(var effects:java.util.List.of(AbstractDungeon.effectList,AbstractDungeon.topLevelEffects)) {
                    for(var effect:new ArrayList<>(effects)) {
                        effect.update();
                        if(effect.isDone)effects.remove(effect);
                    }
                }
                AbstractDungeon.effectList.addAll(AbstractDungeon.effectsQueue);AbstractDungeon.effectsQueue.clear();
                AbstractDungeon.topLevelEffects.addAll(AbstractDungeon.topLevelEffectsQueue);AbstractDungeon.topLevelEffectsQueue.clear();
                AbstractDungeon.getCurrRoom().souls.update();
                if(AbstractDungeon.effectList.isEmpty() && AbstractDungeon.topLevelEffects.isEmpty()
                        && !com.megacrit.cardcrawl.cards.SoulGroup.isActive())return phase();
            }
            throw new AssertionError("Local obtain effect did not settle");
        }
    }

    private static Object allocate(Class<?> type) throws Exception {
        var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
        return ((sun.misc.Unsafe)f.get(null)).allocateInstance(type);
    }
    private static void set(Object target,String name,Object value) throws Exception {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass()) {
            try { var f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return; }
            catch(NoSuchFieldException error) { /* Source superclass. */ }
        }
        throw new AssertionError("Missing fixture field "+name);
    }
    private static Map<Field,Object> snapshot(Class<?> type) throws Exception {
        Map<Field,Object> result=new LinkedHashMap<>();
        for(var f:type.getDeclaredFields())if(Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers())) {
            f.setAccessible(true);result.put(f,f.get(null));
        }
        return result;
    }
    private static void check(boolean condition,String text) {
        if(!condition)throw new AssertionError(text);
    }

    private static void shopRewards(LocalDriver driver,AbstractCard offered) throws Exception {
        var room=AbstractDungeon.getCurrRoom();room.phase=AbstractRoom.RoomPhase.COMPLETE;room.rewards.clear();
        var item=(com.megacrit.cardcrawl.rewards.RewardItem)allocate(com.megacrit.cardcrawl.rewards.RewardItem.class);
        item.type=com.megacrit.cardcrawl.rewards.RewardItem.RewardType.CARD;
        item.cards=new ArrayList<>();item.cards.add(offered.makeCopy());item.text="fixture card reward";
        item.hb=new com.megacrit.cardcrawl.helpers.Hitbox(460f,90f);room.rewards.add(item);
        AbstractDungeon.screen=AbstractDungeon.CurrentScreen.SHOP;
        AbstractDungeon.previousScreen=null;AbstractDungeon.isScreenUp=true;
        AbstractDungeon.overlayMenu.cancelButton.show("fixture shop");
        check(driver.buyRelic(0)==GameKernel.Phase.REWARD,"Purchase did not open source reward overlay");
        check(AbstractDungeon.previousScreen==AbstractDungeon.CurrentScreen.SHOP && HeadlessFlowKernel.canReturnToShop(),
            "Purchase lost the actually observed shop parent");
    }

    private static void openNested(LocalDriver driver) {
        check(driver.openCardReward(0)==GameKernel.Phase.CARD_PICK,"Source nested card picker not opened");
        check(AbstractDungeon.previousScreen==AbstractDungeon.CurrentScreen.COMBAT_REWARD,
            "Fixture did not exercise source RewardItem.claimReward parent overwrite");
    }

    public static void main(String[] args) throws Exception {
        GameKernel.boot(args[0],args[1]);NoRunGuard.verify();
        var originalMath=MathUtils.random;
        var originalWords=(RandomXS128)originalMath;long word0=originalWords.getState(0),word1=originalWords.getState(1);
        var globals=snapshot(AbstractDungeon.class);var helpers=snapshot(CardHelper.class);
        var originalMetrics=CardCrawlGame.metricData;
        Map<String,Object> result=new LinkedHashMap<>();
        try {
            // All source cosmetic draws belong to this local fixture stream.
            MathUtils.random=new RandomXS128(918273L);
            CardHelper.obtainedCards=new HashMap<>();CardHelper.removedCards=new ArrayList<>();
            CardCrawlGame.metricData=new MetricData();
            var player=(AbstractPlayer)allocate(com.megacrit.cardcrawl.characters.Ironclad.class);
            set(player,"masterDeck",new CardGroup(CardGroup.CardGroupType.MASTER_DECK));
            set(player,"relics",new ArrayList<com.megacrit.cardcrawl.relics.AbstractRelic>());
            set(player,"powers",new ArrayList<com.megacrit.cardcrawl.powers.AbstractPower>());
            set(player,"gold",6);
            AbstractDungeon.player=player;
            set(player,"maxHealth",80);set(player,"currentHealth",60);
            set(player,"chosenClass",AbstractPlayer.PlayerClass.IRONCLAD);
            set(player,"hb",new com.megacrit.cardcrawl.helpers.Hitbox(80f,100f));
            AbstractDungeon.currMapNode=new MapRoomNode(1,1);AbstractDungeon.currMapNode.room=new ShopRoom();
            AbstractDungeon.getCurrRoom().phase=AbstractRoom.RoomPhase.INCOMPLETE;
            AbstractDungeon.overlayMenu=new OverlayMenu(player);
            AbstractDungeon.dynamicBanner=new DynamicBanner();
            AbstractDungeon.gridSelectScreen=new GridCardSelectScreen();
            AbstractDungeon.combatRewardScreen=new LocalRewardScreen();
            AbstractDungeon.cardRewardScreen=new com.megacrit.cardcrawl.screens.CardRewardScreen();
            var top=(LocalTopPanel)allocate(LocalTopPanel.class);top.twitch=java.util.Optional.empty();AbstractDungeon.topPanel=top;
            AbstractDungeon.effectList=new ArrayList<>();AbstractDungeon.effectsQueue=new ArrayList<>();
            AbstractDungeon.topLevelEffects=new ArrayList<>();AbstractDungeon.topLevelEffectsQueue=new ArrayList<>();
            var shop=(LocalShop)allocate(LocalShop.class);shop.purgeAvailable=true;AbstractDungeon.shopScreen=shop;
            var mirror=new DollysMirror();set(mirror,"cardSelected",false);player.relics.add(mirror);
            AbstractCard target=CardLibrary.getCard("Finisher").makeCopy();target.upgrade();target.inBottleFlame=true;
            player.masterDeck.group.add(target);
            var before=HeadlessStateExport.cards(player.masterDeck.group);
            var grid=AbstractDungeon.gridSelectScreen;grid.targetGroup=player.masterDeck;set(grid,"numCards",1);
            AbstractDungeon.screen=AbstractDungeon.CurrentScreen.GRID;
            AbstractDungeon.previousScreen=AbstractDungeon.CurrentScreen.SHOP;AbstractDungeon.isScreenUp=true;
            var driver=new LocalDriver();
            check(driver.chooseGridCard(0)==GameKernel.Phase.SHOP,"Mirror did not return to source shop");
            check(driver.checkedObtainWait && driver.checkedSoulWait,"Runtime pending obtain/Soul guards were not exercised");
            check(new HeadlessFlowKernel().phase()==GameKernel.Phase.SHOP,"Runtime did not settle to shop");
            check(player.gold==6 && shop.purgeAvailable,"Shop purge consumed the Mirror selection");
            check(player.masterDeck.size()==2 && player.masterDeck.group.contains(target),"Mirror removed original instead of duplicating");
            AbstractCard copy=player.masterDeck.group.stream().filter(c->c!=target).findFirst().orElseThrow();
            check(!copy.uuid.equals(target.uuid) && copy.cardID.equals(target.cardID) && copy.timesUpgraded==target.timesUpgraded
                && copy.baseDamage==target.baseDamage && copy.baseBlock==target.baseBlock && copy.baseMagicNumber==target.baseMagicNumber
                && copy.misc==target.misc && !copy.inBottleFlame && !copy.inBottleLightning && !copy.inBottleTornado,"Mirror stat-equivalent fresh copy");
            check(target.inBottleFlame && grid.selectedCards.isEmpty(),"Mirror changed original bottle or retained selection");
            int deckSize=player.masterDeck.size();mirror.update();shop.updatePurge();
            check(player.masterDeck.size()==deckSize && player.gold==6,"Mirror duplicated or purged twice");
            result.put("mirror_before",before);result.put("mirror_after",HeadlessStateExport.cards(player.masterDeck.group));
            result.put("mirror_gold",player.gold);

            // Original CancelButton action sequence restores previousScreen;
            // LocalShop overrides only presentation reopening, not the stack.
            AbstractDungeon.screen=AbstractDungeon.CurrentScreen.COMBAT_REWARD;
            AbstractDungeon.previousScreen=AbstractDungeon.CurrentScreen.SHOP;
            AbstractDungeon.overlayMenu.cancelButton.show("fixture return");
            check(HeadlessFlowKernel.canReturnToShop(),"Visible shop return was not recognized");
            AbstractDungeon.overlayMenu.cancelButton.hide();check(!HeadlessFlowKernel.canReturnToShop(),"Hidden cancel accepted");
            AbstractDungeon.overlayMenu.cancelButton.show("fixture return");
            AbstractDungeon.previousScreen=AbstractDungeon.CurrentScreen.MAP;
            check(!HeadlessFlowKernel.canReturnToShop(),"Non-shop previous screen accepted");
            AbstractDungeon.previousScreen=AbstractDungeon.CurrentScreen.SHOP;
            int opens=shop.opens;
            check(driver.proceed()==GameKernel.Phase.SHOP && shop.opens==opens+1,"Cancel did not reopen previous shop");
            check(HeadlessFlowKernel.cancelVisible(),"New shop cancel control was hidden after reopening");
            check(player.gold==6 && player.masterDeck.size()==deckSize,"Shop return changed inventory");
            result.put("shop_return","SHOP");result.put("source_updates",driver.drains);

            shopRewards(driver,target);openNested(driver);
            check(driver.skipRewardCard()==GameKernel.Phase.REWARD && HeadlessFlowKernel.canReturnToShop(),"Skip lost shop parent");
            check(AbstractDungeon.combatRewardScreen.rewards.size()==1,"Skip silently consumed card reward");
            check(player.gold==6 && player.masterDeck.size()==deckSize,"Skip changed owned inventory");
            // Repeat opening the same declined item; each nested scope restores once.
            openNested(driver);check(driver.skipRewardCard()==GameKernel.Phase.REWARD && HeadlessFlowKernel.canReturnToShop(),"Repeated skip lost parent");
            check(driver.proceed()==GameKernel.Phase.SHOP,"Nested skipped reward did not return to shop");

            shopRewards(driver,target);openNested(driver);
            check(driver.chooseRewardCard(0)==GameKernel.Phase.REWARD && HeadlessFlowKernel.canReturnToShop(),"Pick lost shop parent");
            check(player.masterDeck.size()==deckSize+1 && AbstractDungeon.combatRewardScreen.rewards.isEmpty(),"Pick did not settle source obtain");
            deckSize=player.masterDeck.size();check(driver.proceed()==GameKernel.Phase.SHOP,"Picked reward did not return to shop");

            player.relics.add(new com.megacrit.cardcrawl.relics.SingingBowl());
            shopRewards(driver,target);openNested(driver);int hp=player.maxHealth;
            check(driver.chooseSingingBowl()==GameKernel.Phase.REWARD && HeadlessFlowKernel.canReturnToShop(),"Bowl lost shop parent");
            check(player.maxHealth==hp+2 && player.masterDeck.size()==deckSize,"Bowl changed wrong source resource");
            check(driver.proceed()==GameKernel.Phase.SHOP,"Bowl reward did not return to shop");

            shopRewards(driver,target);openNested(driver);
            new LocalDriver().skipRewardCard();
            check(!HeadlessFlowKernel.canReturnToShop(),"A foreign kernel invented a shop return binding");

            shopRewards(driver,target);openNested(driver);
            var previousNode=AbstractDungeon.currMapNode;
            AbstractDungeon.currMapNode=new MapRoomNode(2,2);AbstractDungeon.currMapNode.room=new ShopRoom();
            driver.skipRewardCard();check(!HeadlessFlowKernel.canReturnToShop(),"A stale room binding was reused");
            AbstractDungeon.currMapNode=previousNode;

            shopRewards(driver,target);openNested(driver);
            try { driver.openCardReward(99);throw new AssertionError("Invalid nested open accepted"); }
            catch(IllegalStateException expected) { /* Invalid phase must discard pending ownership. */ }
            driver.skipRewardCard();check(!HeadlessFlowKernel.canReturnToShop(),"Failed action retained stale parent");
            result.put("nested_reward_skip_pick_bowl",true);result.put("stale_or_foreign_parent_rejected",true);
            result.put("new_run_calls",0);result.put("fixture_only",true);
        } finally {
            for(var entry:globals.entrySet())entry.getKey().set(null,entry.getValue());
            for(var entry:helpers.entrySet())entry.getKey().set(null,entry.getValue());
            CardCrawlGame.metricData=originalMetrics;MathUtils.random=originalMath;
        }
        NoRunGuard.verify();
        check(originalWords.getState(0)==word0 && originalWords.getState(1)==word1,"Original cosmetic RNG changed");
        result.put("original_math_rng_unchanged",true);result.put("no_run_guard_after",true);
        Files.writeString(Path.of(args[2]),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
        System.out.println("SHOP_FLOW_FIXTURE_OK mirror_source_update=true return_shop=true new_run_calls=0 original_rng_unchanged=true");
    }
}
