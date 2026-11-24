import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Map;

/** Pure descriptors only: no player, event construction, kernel, RNG, or game action. */
public final class WheelDescriptorFixtureTest {
    private static final Gson JSON=new Gson();
    private static JsonObject view(Map<String,Object> facts) {
        return JSON.toJsonTree(HeadlessInteractionExport.wheelEvent(facts,"localized")).getAsJsonObject();
    }
    private static Map<String,Object> facts(String phase, boolean spin, boolean pressed, int result) {
        return HeadlessInteractionExport.wheelFacts(phase,spin,false,false,pressed,200,8,true,result);
    }
    public static void main(String[] args) {
        for(int result=0;result<6;result++) {
            for(boolean spin:new boolean[]{false,true}) {
                Map<String,Object> hidden=facts("INTRO",spin,false,result);
                if(hidden.containsKey("result"))throw new AssertionError("Hidden wheel result leaked");
                JsonObject event=view(hidden);
                if(!event.get("phase").getAsString().equals(spin?"SPIN":"INTRO"))throw new AssertionError("Native INTRO spin substage");
                var outcomes=event.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonArray("outcomes");
                if(spin) {
                    if(outcomes.size()!=2)throw new AssertionError("Pre-apply distribution");
                    var gold=outcomes.get(0).getAsJsonObject().getAsJsonArray("effects").get(0).getAsJsonObject();
                    if(!gold.get("type").getAsString().equals("gain_gold") || gold.get("amount").getAsInt()!=200)
                        throw new AssertionError("Only gold is pre-awarded");
                    if(outcomes.get(1).getAsJsonObject().getAsJsonArray("effects").size()!=0)
                        throw new AssertionError("Other result effects are deferred");
                } else if(outcomes.get(0).getAsJsonObject().getAsJsonArray("effects").size()!=0)
                    throw new AssertionError("Intro must only start spin");
            }
            Map<String,Object> visible=facts("COMPLETE",false,true,result);
            if(!visible.get("result").equals(result))throw new AssertionError("Visible result missing");
            JsonObject choice=view(visible).getAsJsonArray("choices").get(0).getAsJsonObject();
            var effects=choice.getAsJsonArray("outcomes").get(0).getAsJsonObject().getAsJsonArray("effects");
            if(result==0 && effects.size()!=0)throw new AssertionError("Gold must not be awarded twice");
            if(result==1 && !choice.get("followup").getAsString().equals("COMBAT_REWARD"))throw new AssertionError("Relic remains a reward");
            if(result==4 && !choice.get("followup").getAsString().equals("GRID"))throw new AssertionError("Purge must open grid");
        }
        System.out.println("WHEEL_DESCRIPTOR_FIXTURE_OK stages=4 results=6 hidden_result_absent=true game_actions=0");
    }
}
