import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.headless.GameKernel;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.potions.PotionSlot;
import com.megacrit.cardcrawl.random.Random;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Decision snapshot and native legality facts, without submitting commands. */
public final class HeadlessCombatExport {
    public static final String SCHEMA = "headless-combat-v1";
    public static final String RNG_POLICY = "headless-b354a9ee-v1";
    private HeadlessCombatExport() {}
    public static String toJson(GameKernel kernel) {
        return new com.google.gson.GsonBuilder().serializeNulls().create().toJson(capture(kernel));
    }
    private static Map<String,Object> obj(Object... pairs) { return HeadlessInteractionExport.object(pairs); }
    private static Object read(Object owner,String name) {
        for (Class<?> c=owner.getClass(); c!=null; c=c.getSuperclass()) {
            try { Field f=c.getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
            catch (NoSuchFieldException e) { /* Continue in base class. */ }
            catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        throw new IllegalStateException("Missing field " + name);
    }
    public static Map<String,Object> primitiveFields(Object owner,Class<?> stop) {
        Map<String,Object> result=new TreeMap<>();
        for (Class<?> c=owner.getClass(); c!=null && c!=stop; c=c.getSuperclass()) for (Field f:c.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || !(f.getType().isPrimitive() || f.getType().isEnum())) continue;
            try { f.setAccessible(true); Object v=f.get(owner); result.put(c.getSimpleName()+"."+f.getName(),v instanceof Enum ? v.toString() : v); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        return result;
    }
    public static Map<String,Object> rng(Random r) {
        if (r==null || !(r.random instanceof RandomXS128)) throw new IllegalStateException("Missing run RNG");
        var x=(RandomXS128)r.random;
        return obj("counter",r.counter,"state0",String.format("%016x",x.getState(0)),"state1",String.format("%016x",x.getState(1)));
    }
    public static Map<String,Object> rngPayload(Map<String,Random> source) {
        Map<String,Object> streams=new TreeMap<>();
        for (var entry:source.entrySet()) streams.put(entry.getKey(),rng(entry.getValue()));
        var math=(RandomXS128)MathUtils.random;
        return obj("schema","headless-rng-v1","policy",RNG_POLICY,"streams",streams,
                "math",obj("state0",String.format("%016x",math.getState(0)),"state1",String.format("%016x",math.getState(1))));
    }
    public static Map<String,Random> streams() {
        Map<String,Random> r=new TreeMap<>();
        r.put("monster",AbstractDungeon.monsterRng);r.put("map",AbstractDungeon.mapRng);r.put("event",AbstractDungeon.eventRng);
        r.put("merchant",AbstractDungeon.merchantRng);r.put("card",AbstractDungeon.cardRng);r.put("treasure",AbstractDungeon.treasureRng);
        r.put("relic",AbstractDungeon.relicRng);r.put("potion",AbstractDungeon.potionRng);r.put("monster_hp",AbstractDungeon.monsterHpRng);
        r.put("ai",AbstractDungeon.aiRng);r.put("shuffle",AbstractDungeon.shuffleRng);r.put("card_random",AbstractDungeon.cardRandomRng);
        r.put("misc",AbstractDungeon.miscRng);r.put("headless",AbstractDungeon.headlessRng);return r;
    }
    public static Map<String,Object> card(AbstractCard c) {
        return obj("card_id",c.cardID,"uuid",c.uuid.toString(),"times_upgraded",c.timesUpgraded,"upgraded",c.upgraded,
                "cost",c.cost,"cost_for_turn",c.costForTurn,"base_damage",c.baseDamage,"base_block",c.baseBlock,
                "base_magic_number",c.baseMagicNumber,"damage",c.damage,"block",c.block,"magic_number",c.magicNumber,
                "misc",c.misc,"exhaust",c.exhaust,"is_ethereal",c.isEthereal,"self_retain",c.selfRetain,"retain",c.retain,
                "free_to_play_once",c.freeToPlayOnce,"purge_on_use",c.purgeOnUse,"shuffle_back_into_draw_pile",c.shuffleBackIntoDrawPile,
                "dont_trigger_on_use_card",c.dontTriggerOnUseCard,"target_enum_ordinal",c.target.ordinal(),"_private",primitiveFields(c,AbstractCard.class));
    }
    public static List<Object> cards(List<AbstractCard> source) {
        List<Object> out=new ArrayList<>();for (var c:source)out.add(card(c));return out;
    }
    public static Map<String,Object> orb(com.megacrit.cardcrawl.orbs.AbstractOrb orb) {
        String id=orb instanceof com.megacrit.cardcrawl.orbs.EmptyOrbSlot ? "EmptyOrbSlot" : orb.ID;
        if(id==null)throw new IllegalStateException("Nonempty orb missing identity");
        return obj("lookup_key",id,"evoke_amount",orb.evokeAmount,"passive_amount",orb.passiveAmount);
    }
    public static Map<String,Object> creature(AbstractCreature c) {
        List<Object> powers=new ArrayList<>();
        for (var p:c.powers) {
            var row=obj("power_id",p.ID,"amount",p.amount,"_private",primitiveFields(p,com.megacrit.cardcrawl.powers.AbstractPower.class));
            for (Class<?> t=p.getClass(); t!=null && t!=com.megacrit.cardcrawl.powers.AbstractPower.class; t=t.getSuperclass()) {
                for (Field f:t.getDeclaredFields()) if (f.getName().equals("justApplied")) row.put("just_applied",read(p,"justApplied"));
            }
            if (p.ID.equals("DevaForm")) row.put("energy_gain_amount",read(p,"energyGainAmount"));
            if (p.ID.equals("Ritual")) row.put("just_applied",read(p,"skipFirst"));
            powers.add(row);
        }
        return obj("id",c.id,"current_health",c.currentHealth,"max_health",c.maxHealth,"current_block",c.currentBlock,
                "gold",c.gold,"is_dead",c.isDead,"is_dying",c.isDying,"is_escaping",c.isEscaping,"half_dead",c.halfDead,
                "draw_x",c.drawX,"powers",powers);
    }
    public static Map<String,Object> monster(AbstractMonster m) {
        var result=obj("creature",creature(m),"next_move",m.nextMove,"intent_name",m.intent.name(),"move_history",new ArrayList<>(m.moveHistory),
                "escaped",m.escaped,"_private",primitiveFields(m,AbstractMonster.class),
                "move_base_damage",read(m,"intentBaseDmg"),"move_adjusted_damage",read(m,"intentDmg"),
                "move_hits",read(m,"isMultiDmg").equals(Boolean.TRUE)?read(m,"intentMultiAmt"):1);
        if(m.id.equals("Hexaghost")) { result.put("active_orbs",read(m,"orbActiveCount"));result.put("burn_upgraded",read(m,"burnUpgraded")); }
        return result;
    }
    public static Map<String,Object> relic(com.megacrit.cardcrawl.relics.AbstractRelic r) {
        var fields=primitiveFields(r,com.megacrit.cardcrawl.relics.AbstractRelic.class);
        // This gameplay latch is private static in the pinned source, so the
        // ordinary instance-only primitive scan deliberately does not see it.
        if(r.relicId.equals("Centennial Puzzle")) {
            Object used=read(r,"usedThisCombat");
            if(!(used instanceof Boolean))throw new IllegalStateException("Invalid Centennial Puzzle latch");
            fields.put("CentennialPuzzle.usedThisCombat",used);
        }
        if(r.relicId.equals("OrangePellets")) {
            // These three latches are static in this game's OrangePellets,
            // not derivable from its counter or from the last played card.
            for(String key:List.of("ATTACK","SKILL","POWER")) {
                Object bit=read(r,key);
                if(!(bit instanceof Boolean))throw new IllegalStateException("Invalid Orange Pellets latch: "+key);
                fields.put("OrangePellets."+key,bit);
            }
        }
        return obj("relic_id",r.relicId,"counter",r.counter,"_private",fields);
    }
    private static boolean gamblerSelection() {
        return AbstractDungeon.player!=null && AbstractDungeon.screen==AbstractDungeon.CurrentScreen.HAND_SELECT
                && AbstractDungeon.actionManager.currentAction!=null
                && AbstractDungeon.actionManager.currentAction.getClass().getSimpleName().equals("GamblingChipAction");
    }
    private static boolean secretFetch(String source) {
        return "Secret Weapon".equals(source) || "Secret Technique".equals(source);
    }
    static Map<String,Object> scryReplayEvidence() {
        var am=AbstractDungeon.actionManager;
        var source=AbstractDungeon.player.cardInUse;
        if(source==null || !"CutThroughFate".equals(source.cardID) ||
                am.currentAction==null || am.currentAction.getClass()!=com.megacrit.cardcrawl.actions.utility.ScryAction.class)
            return null;
        var monsters=AbstractDungeon.getMonsters().monsters;
        int target=-1;
        for(var action:am.actions) {
            if(action.getClass()==com.megacrit.cardcrawl.actions.utility.UseCardAction.class && read(action,"targetCard")==source)
                target=monsters.indexOf(((com.megacrit.cardcrawl.actions.utility.UseCardAction)action).target);
        }
        if(target<0)return null;
        Map<String,Object> result=obj("source",card(source),"target",target,"autoplay",source.isInAutoplay,
                "remaining",am.cardQueue.size(),"next",null);
        if(am.cardQueue.size()==1) {
            var next=am.cardQueue.get(0);
            if(next.card!=null && !next.isEndTurnAutoPlay && next.autoplayCard && !next.randomTarget &&
                    !next.card.dontTriggerOnUseCard && !next.card.exhaustOnUseOnce &&
                    !next.card.exhaust && "CutThroughFate".equals(next.card.cardID) && monsters.contains(next.monster))
                result.put("next",obj("card",card(next.card),"target",monsters.indexOf(next.monster),
                        "ignore_energy_total",next.ignoreEnergyTotal,"energy_on_use",next.energyOnUse));
        }
        return result;
    }
    private static String fetchGridSource() {
        var p=AbstractDungeon.player;
        if(p==null || AbstractDungeon.screen!=AbstractDungeon.CurrentScreen.GRID || p.cardInUse==null)return null;
        var a=AbstractDungeon.actionManager.currentAction;var g=AbstractDungeon.gridSelectScreen;var c=p.cardInUse;
        boolean seek=a!=null && "Seek".equals(c.cardID) && a.getClass()==com.megacrit.cardcrawl.actions.common.BetterDrawPileToHandAction.class
                && read(a,"player")==p && read(a,"optional").equals(false) && read(a,"numberOfCards").equals(c.upgraded?2:1);
        boolean omni=a!=null && "Omniscience".equals(c.cardID) && a.getClass()==com.megacrit.cardcrawl.actions.watcher.OmniscienceAction.class
                && read(a,"player")==p && read(a,"playAmt").equals(2);
        if ((seek || omni)
                && g.targetGroup!=p.drawPile && g.targetGroup.size()==p.drawPile.size()
                && g.targetGroup.group.containsAll(p.drawPile.group)) {
            // BetterDrawPileToHandAction builds a temporary group, then does
            // three stable sorts; identical cards keep source draw-pile order.
            var expected=new com.megacrit.cardcrawl.cards.CardGroup(com.megacrit.cardcrawl.cards.CardGroup.CardGroupType.UNSPECIFIED);
            for(var card:p.drawPile.group)expected.addToTop(card);
            expected.sortAlphabetically(true);expected.sortByRarityPlusStatusCardType(false);
            if(!expected.group.equals(g.targetGroup.group))throw new IllegalStateException("Seek option order changed");
            return c.cardID;
        }
        if ("Hologram".equals(c.cardID) && a instanceof com.megacrit.cardcrawl.actions.common.BetterDiscardPileToHandAction
                && read(a,"player")==p && read(a,"setCost").equals(false) && read(a,"optional").equals(false)
                && read(a,"numberOfCards").equals(1) && g.targetGroup==p.discardPile) return "Hologram";
        if (secretFetch(c.cardID) && a!=null) {
            boolean weapon="Secret Weapon".equals(c.cardID);
            if(a.getClass()!=(weapon?com.megacrit.cardcrawl.actions.unique.AttackFromDeckToHandAction.class:
                    com.megacrit.cardcrawl.actions.unique.SkillFromDeckToHandAction.class)
                    || read(a,"p")!=p || a.target!=p || a.source!=p || a.amount!=1)return null;
            var type=weapon?AbstractCard.CardType.ATTACK:AbstractCard.CardType.SKILL;
            var expected=p.drawPile.group.stream().filter(card->card.type==type).toList();
            if(expected.size()<=1 || g.targetGroup==p.drawPile || g.targetGroup.size()!=expected.size()
                    || !g.targetGroup.group.containsAll(expected))
                throw new IllegalStateException("Filtered fetch options differ from source draw pile");
            // Java's random insertion order is compared with the native prediction.
            return c.cardID;
        }
        return null;
    }
    private static List<Object> omniscienceQueue() {
        var p=AbstractDungeon.player;
        var queue=AbstractDungeon.actionManager.cardQueue;
        var source=p.cardInUse;
        if(queue.size()>12 || (!queue.isEmpty() || source.purgeOnUse) && !source.isInAutoplay)
            throw new IllegalStateException("Unmapped nested Omniscience origin");
        var seen=new java.util.HashSet<java.util.UUID>();
        seen.add(source.uuid);
        for(var pile:List.of(p.hand,p.drawPile,p.discardPile,p.exhaustPile))
            for(var c:pile.group)seen.add(c.uuid);
        List<Object> rows=new ArrayList<>();
        for(var q:queue) {
            var c=q.card;
            if(c==null || !seen.add(c.uuid) || q.monster!=null || !q.randomTarget || !q.autoplayCard ||
                    q.ignoreEnergyTotal || q.isEndTurnAutoPlay || q.energyOnUse<0 || c.exhaustOnUseOnce ||
                    c.dontTriggerOnUseCard || (!c.purgeOnUse && !c.exhaust))
                throw new IllegalStateException("Unmapped nested Omniscience queue item");
            rows.add(obj("card",card(c),"monster_index",null,"energy_on_use",q.energyOnUse,
                    "ignore_energy_total",false,"autoplay_card",true,"random_target",true));
        }
        return rows;
    }
    private static List<Object> fetchCleanup(String source) {
        var am=AbstractDungeon.actionManager;var c=AbstractDungeon.player.cardInUse;
        var continuation=new ArrayList<Object>();
        var use=skillSelectionTail(continuation,source);
        boolean autoplay="Omniscience".equals(source) && c.isInAutoplay;
        boolean targetOk=use.target==null || autoplay && AbstractDungeon.getMonsters().monsters.contains(use.target);
        if (read(use,"targetCard")!=c || !targetOk || use.returnToHand || Boolean.TRUE.equals(read(use,"reboundCard"))
                || use.exhaustCard!=c.exhaust || c.exhaust!=("Seek".equals(source) || "Omniscience".equals(source) || !c.upgraded) || c.exhaustOnUseOnce ||
                (c.purgeOnUse && !autoplay) || c.dontTriggerOnUseCard || c.returnToHand)
            throw new IllegalStateException("Unmapped fetch cleanup owner/flags");
        continuation.add(obj("state_class","savestate.actions.UseCardActionState","state",
                obj("card",card(c),"targetIndex",-2,"exhaustCard",use.exhaustCard,"returnToHand",false,"reboundCard",false)));
        return continuation;
    }
    private static void selectionDraw(List<Object> out,com.megacrit.cardcrawl.actions.AbstractGameAction action,
                                      Object source,int amount,String kind) {
        if(action.getClass()!=com.megacrit.cardcrawl.actions.common.DrawCardAction.class ||
                action.target!=AbstractDungeon.player || action.source!=source || action.amount!=amount ||
                action.isDone || read(action,"followUpAction")!=null ||
                !Boolean.FALSE.equals(read(action,"shuffleCheck")) || !Boolean.TRUE.equals(read(action,"clearDrawHistory")))
            throw new IllegalStateException("Unexpected selection draw continuation: "+kind);
        out.add(obj("state_class","savestate.actions.SelectionDrawActionState","state",obj("kind",kind,"amount",amount)));
    }
    private static int selectionJuggernaut(String source,AbstractCard played) {
        var p=AbstractDungeon.player;var power=p.getPower("Juggernaut");
        return "True Grit".equals(source) && played.block>0 && !p.hasPower("NoBlockPower") && power!=null?power.amount:0;
    }
    private static com.megacrit.cardcrawl.actions.utility.UseCardAction skillSelectionTail(List<Object> out,String source) {
        var p=AbstractDungeon.player;var actions=AbstractDungeon.actionManager.actions;var played=p.cardInUse;
        if(played==null || !source.equals(played.cardID))throw new IllegalStateException("Unmapped selection source "+source);
        var hex=p.getPower("Hex");var ink=p.getRelic("InkBottle");int i=0;
        var letter=p.getRelic("Letter Opener");
        boolean letterHit="Hologram".equals(source) && letter!=null && letter.counter==0;
        int expected=1+(letterHit?2:0)+("Burning Pact".equals(source)?1:0)+(hex!=null?1:0)+(ink!=null && ink.counter==0?2:0)
                +(selectionJuggernaut(source,played)>0?1:0);
        if(actions.size()!=expected)throw new IllegalStateException("Unmapped selection continuation: "+actions);
        if("Burning Pact".equals(source)) {
            int amount=played.upgraded?3:2;
            if(played.magicNumber!=amount)throw new IllegalStateException("Burning Pact draw amount changed");
            selectionDraw(out,actions.get(i++),p,amount,"burning_pact");
        }
        if(hex!=null) {
            var action=actions.get(i++);
            if(action.getClass()!=com.megacrit.cardcrawl.actions.common.MakeTempCardInDrawPileAction.class ||
                    action.source!=null || action.target!=null || action.amount!=hex.amount || action.amount<1 || action.isDone ||
                    !Boolean.TRUE.equals(read(action,"randomSpot")) || !Boolean.TRUE.equals(read(action,"autoPosition")) ||
                    !Boolean.FALSE.equals(read(action,"toBottom")) || !read(action,"duration").equals(read(action,"startDuration")))
                throw new IllegalStateException("Unmapped Hex continuation");
            var template=(AbstractCard)read(action,"cardToMake");
            if(template.getClass()!=com.megacrit.cardcrawl.cards.status.Dazed.class || template.timesUpgraded!=0 ||
                    template.cost!=-2 || template.costForTurn!=-2 || !template.isEthereal)
                throw new IllegalStateException("Unmapped Hex card template");
            out.add(obj("state_class","savestate.actions.SelectionHexActionState","state",obj("amount",hex.amount)));
        }
        if(ink!=null && ink.counter==0) {
            var flash=actions.get(i++);
            if(flash.getClass()!=com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction.class ||
                    flash.target!=p || flash.source!=p || flash.isDone || read(flash,"relic")!=ink ||
                    !Boolean.FALSE.equals(read(flash,"used")))throw new IllegalStateException("Unmapped Ink Bottle presentation");
            selectionDraw(out,actions.get(i++),null,1,"ink_bottle");
        }
        if(letterHit) {
            var flash=actions.get(i++);
            if(flash.getClass()!=com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction.class ||
                    flash.target!=p || flash.source!=p || flash.isDone || read(flash,"relic")!=letter ||
                    !Boolean.FALSE.equals(read(flash,"used")))throw new IllegalStateException("Unmapped Letter Opener presentation");
            var hit=actions.get(i++);
            if(hit.getClass()!=com.megacrit.cardcrawl.actions.common.DamageAllEnemiesAction.class ||
                    hit.source!=null || hit.target!=null || hit.isDone ||
                    hit.damageType!=com.megacrit.cardcrawl.cards.DamageInfo.DamageType.THORNS ||
                    !read(hit,"attackEffect").toString().equals("SLASH_HEAVY") ||
                    !Boolean.TRUE.equals(read(hit,"firstFrame")) || !Boolean.FALSE.equals(read(hit,"utilizeBaseDamage")) ||
                    !(read(hit,"damage") instanceof int[] damage) ||
                    damage.length!=AbstractDungeon.getMonsters().monsters.size() ||
                    java.util.Arrays.stream(damage).anyMatch(n->n!=5))
                throw new IllegalStateException("Unmapped Letter Opener hit");
            out.add(obj("state_class","savestate.actions.SelectionLetterOpenerActionState","state",obj("amount",5)));
        }
        if(!(actions.get(i) instanceof com.megacrit.cardcrawl.actions.utility.UseCardAction))
            throw new IllegalStateException("Selection cleanup is not a UseCardAction");
        return (com.megacrit.cardcrawl.actions.utility.UseCardAction)actions.get(i);
    }
    private static boolean liquidSelection() {
        if(AbstractDungeon.player==null || AbstractDungeon.screen!=AbstractDungeon.CurrentScreen.GRID
                || AbstractDungeon.actionManager.currentAction==null
                || !AbstractDungeon.actionManager.currentAction.getClass().getSimpleName().equals("BetterDiscardPileToHandAction"))return false;
        var action=AbstractDungeon.actionManager.currentAction;
        if(fetchGridSource()!=null)return false;
        if(read(action,"player")!=AbstractDungeon.player || !read(action,"setCost").equals(Boolean.TRUE)
                || !read(action,"newCost").equals(0) || !read(action,"optional").equals(Boolean.FALSE))
            throw new IllegalStateException("Unmapped discard-to-hand selection variant");
        return true;
    }
    private static boolean elixirSelection() {
        if(AbstractDungeon.player==null || AbstractDungeon.screen!=AbstractDungeon.CurrentScreen.HAND_SELECT)return false;
        var action=AbstractDungeon.actionManager.currentAction;
        return action!=null && action.getClass()==com.megacrit.cardcrawl.actions.common.ExhaustAction.class
                && action.amount==99 && action.source==null && action.target==null && AbstractDungeon.player.cardInUse==null
                && read(action,"p")==AbstractDungeon.player && Boolean.FALSE.equals(read(action,"isRandom"))
                && Boolean.TRUE.equals(read(action,"anyNumber")) && Boolean.TRUE.equals(read(action,"canPickZero"));
    }
    public static String singleHandSource() {
        var p=AbstractDungeon.player;
        if(p==null || AbstractDungeon.screen!=AbstractDungeon.CurrentScreen.HAND_SELECT || p.cardInUse==null)return null;
        var a=AbstractDungeon.actionManager.currentAction;var c=p.cardInUse;
        if(a==null)return null;
        if(c.cardID.equals("Forethought") && a.getClass()==com.megacrit.cardcrawl.actions.unique.ForethoughtAction.class
            && read(a,"p")==p && read(a,"chooseAny").equals(c.upgraded) && AbstractDungeon.actionManager.cardQueue.isEmpty())
            return c.cardID;
        if(c.cardID.equals("Armaments") && !c.upgraded && a instanceof com.megacrit.cardcrawl.actions.unique.ArmamentsAction
            && read(a,"p")==p && Boolean.FALSE.equals(read(a,"upgraded")))return c.cardID;
        if(c.cardID.equals("Dual Wield") && a instanceof com.megacrit.cardcrawl.actions.unique.DualWieldAction
            && read(a,"p")==p && a.source==p && a.target==p && read(a,"dupeAmount").equals(c.upgraded?2:1))return c.cardID;
        if((c.cardID.equals("True Grit") && c.upgraded || c.cardID.equals("Burning Pact")) && a instanceof com.megacrit.cardcrawl.actions.common.ExhaustAction
            && read(a,"p")==p && a.amount==1 && a.source==null && a.target==null && read(a,"isRandom").equals(false)
            && read(a,"anyNumber").equals(false) && read(a,"canPickZero").equals(false))return c.cardID;
        return null;
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> singleHandSnapshot(String source) {
        var p=AbstractDungeon.player;var am=AbstractDungeon.actionManager;var c=p.cardInUse;var h=AbstractDungeon.handCardSelectScreen;
        var continuation=new ArrayList<Object>();
        var q=skillSelectionTail(continuation,source);
        // Distilled Chaos samples a monster even for a nontargeted skill.
        // These four skills' cleanup uses the played card, not that monster.
        boolean targetOk=q.target==null || c.isInAutoplay && AbstractDungeon.getMonsters().monsters.contains(q.target);
        if(read(q,"targetCard")!=c || !targetOk || q.exhaustCard || q.returnToHand || read(q,"reboundCard").equals(true)
                || c.exhaustOnUseOnce || c.purgeOnUse || c.exhaust || c.dontTriggerOnUseCard || c.returnToHand)
            throw new IllegalStateException("Single hand cleanup flags");
        List<AbstractCard> hidden=source.equals("True Grit") || source.equals("Burning Pact") || source.equals("Forethought")?List.of():(List<AbstractCard>)read(am.currentAction,
                source.equals("Armaments")?"cannotUpgrade":"cannotDuplicate");
        continuation.add(obj("state_class","savestate.actions.UseCardActionState","state",
                    obj("card",card(c),"targetIndex",-2,"exhaustCard",false,"returnToHand",false,"reboundCard",false)));
        int juggernaut=selectionJuggernaut(source,c);
        if(juggernaut>0) {
            var action=am.actions.get(am.actions.size()-1);
            if(action.getClass()!=com.megacrit.cardcrawl.actions.common.DamageRandomEnemyAction.class || action.isDone
                    || action.source!=null || action.target!=null || !read(action,"attackEffect").toString().equals("SLASH_HORIZONTAL"))
                throw new IllegalStateException("Unmapped True Grit Juggernaut continuation");
            var info=(com.megacrit.cardcrawl.cards.DamageInfo)read(action,"info");
            if(info.owner!=p || info.type!=com.megacrit.cardcrawl.cards.DamageInfo.DamageType.THORNS
                    || info.base!=juggernaut || info.output!=juggernaut)
                throw new IllegalStateException("Unmapped True Grit Juggernaut damage");
            continuation.add(obj("state_class","savestate.actions.SelectionJuggernautActionState","state",obj("amount",juggernaut)));
        }
        return obj("num_cards_to_select",h.numCardsToSelect,"selected_cards",cards(h.selectedCards.group),"any_number",read(h,"anyNumber"),
                "up_to",h.upTo,"can_pick_zero",h.canPickZero,"were_cards_retrieved",read(h,"wereCardsRetrieved"),
                "current_action_state",obj("state_class","savestate.actions.SingleHandActionState","state",
                    obj("source",source,"owner_index",-1,"copies",source.equals("Dual Wield") && c.upgraded?2:1,"hidden",cards(hidden))),
                "action_queue",continuation,
                "card_queue_state",singleHandAutoplayQueue(c));
    }
    private static List<Object> singleHandAutoplayQueue(AbstractCard source) {
        var am=AbstractDungeon.actionManager;
        if(am.cardQueue.isEmpty())return List.of();
        if(!source.isInAutoplay || am.cardQueue.size()>5)
            throw new IllegalStateException("Unmapped single hand autoplay origin");
        List<Object> queue=new ArrayList<>();
        var ids=new java.util.HashSet<java.util.UUID>();
        ids.add(source.uuid);
        for(var q:am.cardQueue) {
            var c=q.card;
            if(c==null || !ids.add(c.uuid) || c.purgeOnUse || c.exhaustOnUseOnce || c.dontTriggerOnUseCard ||
                    !q.autoplayCard || q.ignoreEnergyTotal || q.randomTarget || q.isEndTurnAutoPlay || q.monster==null)
                throw new IllegalStateException("Unmapped single hand autoplay queue item");
            queue.add(obj("card",card(c),"monster_index",AbstractDungeon.getMonsters().monsters.indexOf(q.monster),
                    "energy_on_use",q.energyOnUse,"ignore_energy_total",false,"autoplay_card",true,"random_target",false));
        }
        return queue;
    }
    private static boolean headbuttSelection() {
        if(AbstractDungeon.player==null || AbstractDungeon.screen!=AbstractDungeon.CurrentScreen.GRID
                || AbstractDungeon.actionManager.currentAction==null
                || !AbstractDungeon.actionManager.currentAction.getClass().getSimpleName().equals("DiscardPileToTopOfDeckAction"))return false;
        if(read(AbstractDungeon.actionManager.currentAction,"p")!=AbstractDungeon.player)
            throw new IllegalStateException("Unmapped Headbutt selection owner");
        return true;
    }
    public static List<Object> headbuttContinuation(Iterable<com.megacrit.cardcrawl.actions.AbstractGameAction> actions) {
        List<Object> queue=new ArrayList<>();var rows=actions.iterator();int sourceTarget=-1;
        while(rows.hasNext()) {
            var action=rows.next();
            if(queue.isEmpty() && action.getClass()==com.megacrit.cardcrawl.actions.common.DamageAction.class) {
                var p=AbstractDungeon.player;var monsters=AbstractDungeon.getMonsters().monsters;
                var owner=monsters.isEmpty()?null:monsters.get(0);
                var power=owner==null?null:owner.getPower("Sharp Hide");
                var info=(com.megacrit.cardcrawl.cards.DamageInfo)read(action,"info");
                if(power==null || !owner.id.equals("TheGuardian") || info.owner!=owner || action.source!=owner || action.target!=p
                        || info.type!=com.megacrit.cardcrawl.cards.DamageInfo.DamageType.THORNS || power.amount<=0
                        || info.base!=power.amount || info.output!=power.amount || action.isDone ||
                        !Integer.valueOf(0).equals(read(action,"goldAmount")) ||
                        !Boolean.FALSE.equals(read(action,"skipWait")) || !rows.hasNext())
                    throw new IllegalStateException("Unmapped Headbutt Sharp Hide damage");
                queue.add(obj("state_class","savestate.actions.HeadbuttSharpHideActionState","state",
                         obj("owner_index",0,"target_index",-1,"amount",power.amount)));
                action=rows.next();
            }
            if(queue.isEmpty() && action.getClass()==com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction.class) {
                var p=AbstractDungeon.player;
                if(p==null || action.source!=p || action.target!=p || !"Double Tap".equals(read(action,"powerToRemove"))
                        || read(action,"powerInstance")!=null || !p.hasPower("Double Tap") || p.getPower("Double Tap").amount!=0 ||
                        headbuttReplayQueue().size()!=1 || !rows.hasNext())
                    throw new IllegalStateException("Unmapped Headbutt power removal");
                queue.add(obj("state_class","savestate.actions.RemoveSpecificPowerActionState","state",obj("ownerIndex",-1,"powerId","Double Tap")));
                action=rows.next();
            }
            if(sourceTarget<0 && action.getClass()==com.megacrit.cardcrawl.actions.common.GainBlockAction.class) {
                var p=AbstractDungeon.player;
                var rage=p.getPower("Rage");
                if(rage==null || rage.amount<=0 || action.source!=p || action.target!=p || action.isDone
                        || action.amount!=rage.amount || !read(action,"startDuration").equals(read(action,"duration"))
                        || !headbuttReplayQueue().isEmpty() || !rows.hasNext()
                        || queue.stream().anyMatch(row->"savestate.actions.HeadbuttRageBlockActionState".equals(((Map<?,?>)row).get("state_class"))))
                    throw new IllegalStateException("Unmapped Headbutt pending Rage block");
                queue.add(obj("state_class","savestate.actions.HeadbuttRageBlockActionState",
                              "state",obj("owner_index",-1,"amount",rage.amount)));
                action=rows.next();
            }
            while(sourceTarget<0 && action.getClass()==com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction.class) {
                var p=AbstractDungeon.player;
                var relic=(com.megacrit.cardcrawl.relics.AbstractRelic)read(action,"relic");
                boolean kunai=relic!=null && relic.relicId.equals("Kunai");
                if(relic==null || (!kunai && !relic.relicId.equals("Shuriken"))
                        || p.getRelic(relic.relicId)!=relic || relic.counter!=0
                        || action.source!=p || action.target!=p || action.isDone
                        || !Boolean.FALSE.equals(read(action,"used")) || !rows.hasNext())
                    throw new IllegalStateException("Unmapped Headbutt relic flash");
                var buff=rows.next();
                if(buff.getClass()!=com.megacrit.cardcrawl.actions.common.ApplyPowerAction.class ||
                        buff.source!=p || buff.target!=p || buff.amount!=1 || buff.isDone || !rows.hasNext())
                    throw new IllegalStateException("Unmapped Headbutt attack relic buff");
                var power=(com.megacrit.cardcrawl.powers.AbstractPower)read(buff,"powerToApply");
                if(power==null || !(kunai ? power instanceof com.megacrit.cardcrawl.powers.DexterityPower
                        : power instanceof com.megacrit.cardcrawl.powers.StrengthPower) ||
                        power.owner!=p || power.amount!=1)
                    throw new IllegalStateException("Invalid Headbutt attack relic power");
                queue.add(obj("state_class",kunai?"savestate.actions.HeadbuttKunaiActionState":"savestate.actions.HeadbuttShurikenActionState",
                              "state",obj("owner_index",-1,"amount",1)));
                action=rows.next();
            }
            if(sourceTarget>=0) {
                var m=AbstractDungeon.getMonsters().monsters.get(sourceTarget);
                if(action.getClass()==com.megacrit.cardcrawl.actions.utility.TextAboveCreatureAction.class
                        && m instanceof com.megacrit.cardcrawl.monsters.exordium.SlimeBoss
                        && action.source==m && action.target==m && !action.isDone
                        && Boolean.FALSE.equals(read(action,"used")) && m.currentHealth>0 && 2*m.currentHealth<=m.maxHealth
                        && com.megacrit.cardcrawl.core.AbstractCreature.TEXT[4].equals(read(action,"msg"))
                        && m.nextMove==3 && m.intent==com.megacrit.cardcrawl.monsters.AbstractMonster.Intent.UNKNOWN
                        && rows.hasNext()) {
                    var move=rows.next();
                    // Only omit the text and the exact no-op reapplication of the already active Split.
                    if(move.getClass()==com.megacrit.cardcrawl.actions.common.SetMoveAction.class && !move.isDone
                            && read(move,"monster")==m && Byte.valueOf((byte)3).equals(read(move,"theNextMove"))
                            && read(m,"SPLIT_NAME").equals(read(move,"theNextName"))
                            && read(move,"theNextIntent")==m.intent && Integer.valueOf(-1).equals(read(move,"theNextDamage"))
                            && Integer.valueOf(0).equals(read(move,"theMultiplier"))
                            && Boolean.FALSE.equals(read(move,"isMultiplier")) && !rows.hasNext()) continue;
                    throw new IllegalStateException("Unmapped Headbutt Slime Boss Split continuation");
                }
                String tail=null;
                if(action.getClass()==com.megacrit.cardcrawl.actions.common.ReducePowerAction.class
                        && action.source==m && action.target==m && action.amount==1 && "Plated Armor".equals(read(action,"powerID"))
                        && read(action,"powerInstance")==null && m.hasPower("Plated Armor") && m.getPower("Plated Armor").amount>0)
                    tail="plated_armor_minus_one";
                else if(action.getClass()==com.megacrit.cardcrawl.actions.common.ReducePowerAction.class
                        && action.source==m && action.target==m && action.amount==1 && "Flight".equals(read(action,"powerID"))
                        && read(action,"powerInstance")==null && m.id.equals("Byrd") && m.hasPower("Flight") && m.getPower("Flight").amount>0)
                    tail="flight_minus_one";
                else if(action.getClass()==com.megacrit.cardcrawl.actions.common.GainBlockAction.class
                        && action.source==m && action.target==m && !action.isDone && m.id.equals("SnakePlant")
                        && m.hasPower("Malleable") && m.getPower("Malleable").amount>3
                        && action.amount==m.getPower("Malleable").amount-1)
                    tail="malleable_block";
                else if(action.getClass()==com.megacrit.cardcrawl.actions.animations.VFXAction.class
                        && m.id.equals("TheGuardian") && action.target==m && action.source==m && m.hasPower("Mode Shift")
                        && m.getPower("Mode Shift").amount<=0 && Boolean.TRUE.equals(read(m,"closeUpTriggered"))
                        && Boolean.TRUE.equals(read(action,"isTopLevelEffect")) && read(action,"effect").getClass()==com.megacrit.cardcrawl.vfx.combat.IntenseZoomEffect.class
                        && rows.hasNext()) {
                    var change=rows.next();
                    if(change.getClass()==com.megacrit.cardcrawl.actions.common.ChangeStateAction.class
                            && read(change,"m")==m && "Defensive Mode".equals(read(change,"stateName")) && Boolean.FALSE.equals(read(change,"called")))
                        tail="guardian_defensive_mode";
                }
                if(tail!=null && !rows.hasNext()) {
                    queue.add(obj("state_class","savestate.actions.HeadbuttMonsterTailState","state",obj("owner_index",sourceTarget,"kind",tail)));
                    continue;
                }
            }
            if(sourceTarget>=0 || !action.getClass().getSimpleName().equals("UseCardAction"))
                throw new IllegalStateException("Unmapped Headbutt executable continuation: "+action.getClass().getName()+" at queue index "+queue.size());
            var c=(AbstractCard)read(action,"targetCard");
            Object target=read(action,"target");int index=AbstractDungeon.getMonsters().monsters.indexOf(target);
            sourceTarget=index;
            if(!AbstractDungeon.actionManager.cardQueue.isEmpty() && AbstractDungeon.actionManager.cardQueue.get(0).monster!=target)
                throw new IllegalStateException("Stale Headbutt replay target");
            queue.add(obj("state_class","savestate.actions.UseCardActionState","state",headbuttCleanupCard(c,AbstractDungeon.player.cardInUse,index,
                    (Boolean)read(action,"exhaustCard"),(Boolean)read(action,"returnToHand"),(Boolean)read(action,"reboundCard"))));
        }
        if(queue.isEmpty())throw new IllegalStateException("Headbutt cleanup missing");
        return queue;
    }
    private static List<Object> headbuttReplayQueue() {
        var p=AbstractDungeon.player;var cq=AbstractDungeon.actionManager.cardQueue;var inUse=p.cardInUse;
        if(cq.isEmpty()) {
            if(!p.limbo.isEmpty() && !(inUse!=null && inUse.isInAutoplay && inUse.purgeOnUse && p.limbo.size()==1 && p.limbo.group.get(0)==inUse))
                throw new IllegalStateException("Unmapped Headbutt limbo owner");
            return new ArrayList<>();
        }
        if(inUse==null || inUse.purgeOnUse || !"Headbutt".equals(inUse.cardID) || cq.size()!=1 || p.limbo.size()!=1)
            throw new IllegalStateException("Unmapped Headbutt queued replay");
        var q=cq.get(0);var c=q.card;
        if(c==null || c==inUse || c!=p.limbo.group.get(0) || !c.purgeOnUse || !c.uuid.equals(inUse.uuid) || !"Headbutt".equals(c.cardID)
                || c.timesUpgraded!=inUse.timesUpgraded || c.costForTurn!=inUse.costForTurn || c.baseDamage!=inUse.baseDamage
                || c.dontTriggerOnUseCard || !q.autoplayCard || !q.ignoreEnergyTotal || q.randomTarget || q.isEndTurnAutoPlay
                || q.energyOnUse!=inUse.energyOnUse || q.monster==null)
            throw new IllegalStateException("Unmapped Headbutt copy identity, target, or controls");
        return listOfHeadbuttReplay(c,AbstractDungeon.getMonsters().monsters.indexOf(q.monster),q.energyOnUse);
    }
    private static List<Object> listOfHeadbuttReplay(AbstractCard c,int target,int energy) {
        if(target<0 || energy<0)throw new IllegalStateException("Invalid Headbutt replay target/energy");
        return new ArrayList<>(List.of(obj("card",card(c),"monster_index",target,"energy_on_use",energy,
                "ignore_energy_total",true,"autoplay_card",true,"random_target",false)));
    }
    public static Map<String,Object> headbuttCleanupCard(AbstractCard card,AbstractCard inUse,int target,
            boolean exhaust,boolean returnToHand,boolean rebound) {
        if(card!=inUse || !card.cardID.equals("Headbutt") || card.exhaust || card.exhaustOnUseOnce || card.purgeOnUse && !card.isInAutoplay
                || card.dontTriggerOnUseCard || card.returnToHand || exhaust || returnToHand || rebound)
            throw new IllegalStateException("Unmapped Headbutt cleanup variant");
        if(target<0)throw new IllegalStateException("Unmapped Headbutt cleanup target");
        return obj("card",card(card),"targetIndex",target,"exhaustCard",false,"returnToHand",false,"reboundCard",false);
    }
    public static List<Object> gamblingContinuation(Iterable<com.megacrit.cardcrawl.actions.AbstractGameAction> actions) {
        List<Object> queue=new ArrayList<>();boolean refresh=false,wait=false,toy=false;
        var rows=actions.iterator();
        while(rows.hasNext()) {
            var action=rows.next();
            String name=action.getClass().getSimpleName();
            if(name.equals("HandCheckAction") && !refresh && !wait) {
                // GameKernel.usePotion enqueues a refresh of this player's
                // derived card powers/glow. Keep its identity; never execute it here.
                if(read(action,"player")!=AbstractDungeon.player)throw new IllegalStateException("Unmapped Gambler refresh owner");
                refresh=true;
                queue.add(obj("state_class","savestate.actions.HandCheckActionState","state",obj("owner_index",-1,"derived_refresh",true)));
            } else if(action.getClass()==com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction.class && refresh && !wait && !toy) {
                var p=AbstractDungeon.player;
                if(p==null || !p.hasRelic("Toy Ornithopter") || action.source!=p || action.target!=p
                        || read(action,"relic")!=p.getRelic("Toy Ornithopter") || !Boolean.FALSE.equals(read(action,"used"))
                        || !rows.hasNext())throw new IllegalStateException("Unmapped potion relic continuation");
                var heal=rows.next();
                if(heal.getClass()!=com.megacrit.cardcrawl.actions.common.HealAction.class || heal.source!=p
                        || heal.target!=p || heal.amount!=5 || heal.isDone || !read(heal,"duration").equals(read(heal,"startDuration")))
                    throw new IllegalStateException("Unmapped potion heal continuation");
                toy=true;
                queue.add(obj("state_class","savestate.actions.OrnithopterHealActionState",
                        "state",obj("source_index",-1,"target_index",-1,"amount",5)));
            } else if(name.equals("WaitAction") && !wait) {
                wait=true;
                queue.add(obj("state_class","savestate.actions.WaitActionState","state",obj("duration",read(action,"duration"))));
            } else throw new IllegalStateException("Unmapped Gambler continuation: "+name);
        }
        return queue;
    }
    public static List<Object> liquidContinuation(Iterable<com.megacrit.cardcrawl.actions.AbstractGameAction> actions) {
        List<Object> queue=gamblingContinuation(actions);
        for(Object value:queue)if(((Map<?,?>)value).get("state_class").equals("savestate.actions.WaitActionState"))
            throw new IllegalStateException("Unmapped Liquid Memories wait continuation");
        return queue;
    }
    public static List<Object> legalActions(GameKernel kernel) {
        List<Object> out=new ArrayList<>();
        String fetch=fetchGridSource();
        if(fetch!=null || liquidSelection() || headbuttSelection()) {
            var g=AbstractDungeon.gridSelectScreen;boolean omni="Omniscience".equals(fetch);
            int count=headbuttSelection() || omni || secretFetch(fetch)?1:((Number)read(AbstractDungeon.actionManager.currentAction,"numberOfCards")).intValue();
            if(((fetch==null || "Hologram".equals(fetch)) && g.targetGroup!=AbstractDungeon.player.discardPile) || g.isHeadlessSelectionLocked() || g.isHeadlessConfirmAvailable()
                    || ((Number)read(g,"numCards")).intValue()!=count || g.selectedCards.size()>=count)
                throw new IllegalStateException("Unexpected Liquid Memories grid controls");
            for(int i=0;i<g.targetGroup.size();i++)out.add(obj("type","GRID_SELECT","card_index",i,"uuid",g.targetGroup.group.get(i).uuid.toString()));
            return out;
        }
        String single=singleHandSource();
        if(single!=null){
            var h=AbstractDungeon.handCardSelectScreen;var hand=AbstractDungeon.player.hand.group;
            boolean any=single.equals("Forethought") && AbstractDungeon.player.cardInUse.upgraded;
            if(h.numCardsToSelect!=(any?99:1) || h.canPickZero!=any || h.upTo || !read(h,"anyNumber").equals(any)
                    || read(h,"wereCardsRetrieved").equals(true) || h.selectedCards.size()>(any?10:1))
                throw new IllegalStateException("Single hand controls");
            for(int i=0;i<hand.size();i++)out.add(obj("type","HAND_SELECT","card_index",i,"uuid",hand.get(i).uuid.toString()));
            if(any || h.selectedCards.size()==1)out.add(obj("type","HAND_SELECT_CONFIRM"));
            return out;
        }
        if(gamblerSelection() || elixirSelection()) {
            var h=AbstractDungeon.handCardSelectScreen;var hand=AbstractDungeon.player.hand.group;
            if(!read(h,"anyNumber").equals(Boolean.TRUE) || !h.canPickZero || h.numCardsToSelect!=99 || read(h,"wereCardsRetrieved").equals(Boolean.TRUE))
                throw new IllegalStateException("Unexpected Gambler selection controls");
            for(int i=0;i<hand.size();i++)out.add(obj("type","HAND_SELECT","card_index",i,"uuid",hand.get(i).uuid.toString()));
            out.add(obj("type","HAND_SELECT_CONFIRM"));
            return out;
        }
        if (kernel.phase()!=GameKernel.Phase.PLAYER_TURN) return out;
        var p=AbstractDungeon.player;var monsters=AbstractDungeon.getMonsters().monsters;
        for (int i=0;i<p.hand.size();i++) {
            var c=p.hand.group.get(i);
            if (c.target==AbstractCard.CardTarget.ENEMY || c.target==AbstractCard.CardTarget.SELF_AND_ENEMY) {
                for (int j=0;j<monsters.size();j++) if (!monsters.get(j).isDeadOrEscaped() && c.canUse(p,monsters.get(j)))
                    out.add(obj("type","CARD","card_index",i,"monster_index",j,"uuid",c.uuid.toString()));
            } else if (c.canUse(p,null))out.add(obj("type","CARD","card_index",i,"monster_index",-1,"uuid",c.uuid.toString()));
        }
        for (int i=0;i<p.potions.size();i++) {
            var potion=p.potions.get(i);if (potion instanceof PotionSlot || !potion.canUse())continue;
            if (potion.targetRequired) {
                for (int j=0;j<monsters.size();j++) if (!monsters.get(j).isDeadOrEscaped())out.add(obj("type","POTION","potion_index",i,"monster_index",j,"id",potion.ID));
            } else out.add(obj("type","POTION","potion_index",i,"monster_index",-1,"id",potion.ID));
        }
        if (AbstractDungeon.overlayMenu.endTurnButton.enabled) out.add(obj("type","END"));
        return out;
    }
    @SuppressWarnings("unchecked")
    public static Map<String,Object> capture(GameKernel kernel) {
        var observation=HeadlessInteractionExport.capture(kernel);
        if(AbstractDungeon.player!=null && AbstractDungeon.currMapNode!=null) {
            var room=AbstractDungeon.getCurrRoom();List<Object> history=new ArrayList<>();int active=0;
            for(var c:AbstractDungeon.actionManager.cardsPlayedThisCombat)history.add(c.uuid.toString());
            if(room.monsters!=null)for(var m:room.monsters.monsters)if(m.currentHealth>0 && !m.isDeadOrEscaped() && !m.halfDead)active++;
            observation.put("combat_receipt",obj("battle_over",room.isBattleOver,"room_class",room.getClass().getSimpleName(),
                "room_phase",room.phase.name(),"active_monsters",active,"player_dead",AbstractDungeon.player.isDead,
                "played_card_uuids",history,"turn",GameActionManager.turn,"rng",rngPayload(streams())));
        }
        String fetch=fetchGridSource();
        String single=singleHandSource();
        boolean elixir=elixirSelection(),hand=gamblerSelection() || elixir || single!=null,liquid=liquidSelection(),headbutt=headbuttSelection(),selection=hand || liquid || headbutt || fetch!=null;
        if (!"PLAYER_TURN".equals(observation.get("phase")) && !selection)return observation;
        var p=AbstractDungeon.player;var am=AbstractDungeon.actionManager;
        List<Object> headbuttReplay=headbutt?headbuttReplayQueue():new ArrayList<>();
        if ((!selection && (am.currentAction!=null || !am.actions.isEmpty())) || (!headbutt && single==null && !"Omniscience".equals(fetch) && !am.cardQueue.isEmpty()) || !am.monsterQueue.isEmpty() || !am.preTurnActions.isEmpty()
                || (p.cardInUse!=null && !headbutt && fetch==null && single==null) || (!headbutt && !p.limbo.isEmpty()) || p.isEndingTurn || p.endTurnQueued)
            throw new IllegalStateException("Not a settled normal combat decision: current="+(am.currentAction==null?"null":am.currentAction.getClass().getName())
                    +",actions="+am.actions.stream().map(a->a.getClass().getSimpleName()).collect(java.util.stream.Collectors.joining(","))
                    +",cards="+am.cardQueue.size()+",monsters="+am.monsterQueue.size()+",preTurn="+am.preTurnActions.size()
                    +",cardInUse="+(p.cardInUse==null?"null":p.cardInUse.cardID)+",limbo="+p.limbo.size()+",ending="+p.isEndingTurn+",endQueued="+p.endTurnQueued);
        List<Object> relics=new ArrayList<>(),potions=new ArrayList<>(),orbs=new ArrayList<>(),channeled=new ArrayList<>(),monsters=new ArrayList<>();
        for (var r:p.relics)relics.add(relic(r));
        for (int i=0;i<p.potions.size();i++)potions.add(obj("id",p.potions.get(i).ID,"slot",i));
        for (var orb:p.orbs)orbs.add(orb(orb));
        for (var orb:am.orbsChanneledThisCombat)channeled.add(orb(orb));
        for (var m:AbstractDungeon.getMonsters().monsters)monsters.add(monster(m)); // Keep dead slots and target indices.
        var player=obj("chosen_class_name",p.chosenClass.name(),"creature",creature(p),"game_hand_size",p.gameHandSize,
                "energy_manager_energy",p.energy.energy,"energy_panel_total_energy",EnergyPanel.totalCount,"damaged_this_combat",p.damagedThisCombat,
                "is_ending_turn",p.isEndingTurn,"card_in_use",(single!=null || fetch!=null || headbutt) && p.cardInUse!=null?card(p.cardInUse):null,"limbo",cards(p.limbo.group),"max_orbs",p.maxOrbs,"stance",p.stance.ID,
                "master_deck",cards(p.masterDeck.group),"hand",cards(p.hand.group),"draw_pile",cards(p.drawPile.group),
                "discard_pile",cards(p.discardPile.group),"exhaust_pile",cards(p.exhaustPile.group),"relics",relics,"potions",potions,
                "orbs",orbs,"orbs_channeled_this_combat",channeled);
        var rngs=streams();Map<String,Object> counters=obj("seed",Settings.seed);
        for (var entry:rngs.entrySet())counters.put(entry.getKey()+"_rng_counter",entry.getValue().counter);
        List<Object> played=new ArrayList<>();for (var c:am.cardsPlayedThisCombat)played.add(obj("card_index",-1,"card_state",card(c)));
        var snapshot=obj("_engine","headless","_headless_rng",rngPayload(rngs),"_settled_exhaust_costs",true,"floor_num",AbstractDungeon.floorNum,"act_num",AbstractDungeon.actNum,
                "turn",GameActionManager.turn,"ascension_level",AbstractDungeon.ascensionLevel,"screen_name",hand?"HAND_SELECT":liquid || headbutt || fetch!=null?"GRID":"NONE","is_screen_up",selection,
                "end_turn_queued",p.endTurnQueued,"mantra_gained",am.mantraGained,"total_discarded_this_turn",GameActionManager.totalDiscardedThisTurn,
                "cards_played_this_turn",new ArrayList<>(),"cards_played_this_turn_backup",cards(am.cardsPlayedThisTurn),"cards_played_this_combat",played,
                "player_state",player,"rng_state",counters,"cur_map_node_state",obj("phase_name","COMBAT","monster_data",monsters));
        if(single!=null)snapshot.put("hand_select_screen_state",singleHandSnapshot(single));
        else if(hand) {
            List<Object> queue=elixir?liquidContinuation(am.actions):gamblingContinuation(am.actions);
            var h=AbstractDungeon.handCardSelectScreen;
            snapshot.put("hand_select_screen_state",obj("num_cards_to_select",h.numCardsToSelect,
                    "selected_cards",cards(h.selectedCards.group),"any_number",read(h,"anyNumber"),"up_to",h.upTo,
                    "can_pick_zero",h.canPickZero,"were_cards_retrieved",read(h,"wereCardsRetrieved"),
                    "current_action_state",elixir?obj("state_class","savestate.actions.ElixirExhaustActionState","state",obj(
                            "owner_index",-1,"amount",99,"is_random",false,"any_number",true,"can_pick_zero",true)):
                            obj("state_class","savestate.actions.GamblingChipActionState","state",obj("notchip",read(am.currentAction,"notchip"))),
                    "action_queue",queue,"card_queue_state",new ArrayList<>()));
        }
        if(liquid || headbutt || fetch!=null) {
            var g=AbstractDungeon.gridSelectScreen;var action=am.currentAction;
            List<Object> queue=fetch!=null?fetchCleanup(fetch):headbutt?headbuttContinuation(am.actions):liquidContinuation(am.actions);
            snapshot.put("grid_card_select_screen_state",obj("num_cards",read(g,"numCards"),
                    "group_cards",cards(g.targetGroup.group),"selected_cards",cards(g.selectedCards),
                    "any_number",g.anyNumber,"for_clarity",g.forClarity,"for_upgrade",g.forUpgrade,
                    "for_transform",g.forTransform,"for_purge",g.forPurge,"is_just_for_confirming",g.isJustForConfirming,
                    "confirm_screen_up",g.confirmScreenUp,
                    "current_action_state",fetch!=null?obj("state_class","savestate.actions.FetchCardGridActionState","state",obj("source_card",fetch,
                        "number_of_cards","Omniscience".equals(fetch) || secretFetch(fetch)?1:read(action,"numberOfCards"),"owner_index",-1)):headbutt?obj("state_class","savestate.actions.DiscardPileToTopOfDeckActionState","state",obj("owner_index",-1)):obj("state_class","savestate.actions.BetterDiscardPileToHandActionState","state",obj(
                        "number_of_cards",read(action,"numberOfCards"),"optional",read(action,"optional"),
                        "set_cost",read(action,"setCost"),"new_cost",read(action,"newCost"),"owner_index",-1)),
                    "action_queue",queue,"card_queue_state","Omniscience".equals(fetch)?omniscienceQueue():headbuttReplay));
        }
        var pools=battleaimod.networking.BattleCardPools.current();
        var liveCommon=new com.google.gson.JsonArray();
        for(AbstractCard card:AbstractDungeon.commonCardPool.group)liveCommon.add(card.cardID);
        pools.add("live_common",liveCommon);
        snapshot.put("_combat_card_pools",pools);
        List<Object> publicMonsters=new ArrayList<>();
        var menuMonsters=("Omniscience".equals(fetch) || "Forethought".equals(single))
                ?((List<Object>)observation.get("monsters")).iterator():null;
        for (var m:AbstractDungeon.getMonsters().monsters) {
            if(menuMonsters!=null && !m.isDeadOrEscaped())publicMonsters.add(menuMonsters.next());
            else publicMonsters.add(obj("id",m.id,"name",m.name,
                    "hp",m.currentHealth,"maxHp",m.maxHealth,"block",m.currentBlock,"intent",m.intent.name()));
        }
        observation.put("monsters",publicMonsters);
        observation.put("combat",obj("schema",SCHEMA,"snapshot",snapshot,"legal_actions",legalActions(kernel)));
        return (Map<String,Object>)HeadlessInteractionExport.wireTree(observation);
    }
}
