import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import java.lang.reflect.Field;
import java.util.*;

/** Headless input binding to the original matching logic; hidden faces stay hidden. */
public final class HeadlessMatchGame {
    private static final Map<AbstractEvent, Map<UUID,Integer>> SLOTS = new WeakHashMap<>();
    private static final Map<AbstractEvent, Map<String,Object>> LAST = new WeakHashMap<>();
    static Object field(AbstractEvent e, String name) {
        try { Field f=e.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(e); }
        catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
    }
    static String screen(AbstractEvent e) { return String.valueOf(field(e,"screen")); }
    static boolean ready(AbstractEvent e) { return screen(e).equals("PLAY") && ((Number)field(e,"waitTimer")).floatValue()==0 && !(Boolean)field(e,"gameDone"); }
    static Map<UUID,Integer> slots(AbstractEvent e) {
        Map<UUID,Integer> slots=SLOTS.computeIfAbsent(e, ignored->new LinkedHashMap<>());
        for(AbstractCard c:((CardGroup)field(e,"cards")).group) slots.computeIfAbsent(c.uuid, ignored->slots.size());
        return slots;
    }
    public static Map<String,Object> capture(AbstractEvent e) {
        Map<UUID,Integer> ids=slots(e); List<Object> rows=new ArrayList<>();
        for(AbstractCard c:((CardGroup)field(e,"cards")).group)
            rows.add(HeadlessInteractionExport.object("slot",ids.get(c.uuid),"face_up",!c.isFlipped,"card_id",c.isFlipped?null:c.cardID));
        return HeadlessInteractionExport.object("screen",screen(e),"attempts",field(e,"attemptCount"),"matched",field(e,"cardsMatched"),"cards",rows,"last_flip",LAST.get(e));
    }
    public static void flip(AbstractEvent e,int slot) {
        if(!ready(e)) throw new IllegalStateException("match_not_ready");
        Map<UUID,Integer> ids=slots(e); List<AbstractCard> cards=((CardGroup)field(e,"cards")).group;
        AbstractCard selected=cards.stream().filter(c->ids.get(c.uuid)==slot && c.isFlipped).findFirst().orElseThrow(()->new IllegalArgumentException("bad_match_slot"));
        int mx=InputHelper.mX,my=InputHelper.mY; boolean click=InputHelper.justClickedLeft;
        List<float[]> positions=new ArrayList<>();
        try {
            // Only input hitboxes move, never card identities, ordering, RNG or gameplay fields.
            for(AbstractCard c:cards) { positions.add(new float[]{c.hb.cX,c.hb.cY}); c.hb.move(c==selected?100:-10000,c==selected?100:-10000); c.hb.unhover(); }
            InputHelper.mX=100;InputHelper.mY=100;InputHelper.justClickedLeft=true;
            var method=e.getClass().getDeclaredMethod("updateMatchGameLogic");method.setAccessible(true);method.invoke(e);
            if(selected.isFlipped) throw new IllegalStateException("match_click_not_applied");
            LAST.put(e,HeadlessInteractionExport.object("slot",slot,"card_id",selected.cardID));
        } catch(ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
        finally {
            for(int i=0;i<cards.size();i++) { cards.get(i).hb.move(positions.get(i)[0],positions.get(i)[1]);cards.get(i).hb.unhover(); }
            InputHelper.mX=mx;InputHelper.mY=my;InputHelper.justClickedLeft=click;
        }
    }
}
