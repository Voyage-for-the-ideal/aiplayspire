package battleaimod;

import basemod.ModLabel;
import basemod.ModLabeledToggleButton;
import basemod.ModPanel;
import battleaimod.networking.BattleClientController;
import battleaimod.search.SearchProfile;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.screens.options.DropdownMenu;
import com.megacrit.cardcrawl.screens.options.DropdownMenuListener;

import java.util.Arrays;

public class BattleAiModOptionsPanel extends ModPanel implements DropdownMenuListener {
    private static final float LABEL_X_POS = Settings.WIDTH / 5.0F;
    private static final float LABEL_Y_POS = Settings.HEIGHT * 2.0F / 3.0F;
    public static DropdownMenu controllerMode;
    public static DropdownMenu searchProfile;
    private ModLabeledToggleButton autoStartToggle;

    public BattleAiModOptionsPanel() {
        ModLabel controllerModeLabel = new ModLabel(
                "", LABEL_X_POS / Settings.scale, LABEL_Y_POS / Settings.scale, Settings.CREAM_COLOR, FontHelper.charDescFont,
                this, modLabel -> {
            modLabel.text = "Client Controller Mode";
        });
        this.addUIElement(controllerModeLabel);

        String[] values = Arrays.stream(BattleClientController.ControllerMode.values())
                                .map(BattleClientController.ControllerMode::toString)
                                .toArray(String[]::new);
        controllerMode = new DropdownMenu(this, values, FontHelper.tipBodyFont, Settings.CREAM_COLOR);
        controllerMode.setSelectedIndex(BattleAiMod.battleClientControllerMode.ordinal());

        float toggleY = LABEL_Y_POS / Settings.scale - 50.0F;
        autoStartToggle = new ModLabeledToggleButton(
                "Auto-Start AI on Battle",
                LABEL_X_POS / Settings.scale, toggleY,
                Settings.CREAM_COLOR, FontHelper.charDescFont,
                BattleAiMod.autoStartAi,
                this,
                modLabel -> {},
                modToggle -> {}
        );
        this.addUIElement(autoStartToggle);

        ModLabel searchProfileLabel = new ModLabel(
                "Search Profile", LABEL_X_POS / Settings.scale,
                LABEL_Y_POS / Settings.scale - 110.0F, Settings.CREAM_COLOR,
                FontHelper.charDescFont, this, modLabel -> {});
        this.addUIElement(searchProfileLabel);

        String[] profileValues = Arrays.stream(SearchProfile.values())
                                       .map(SearchProfile::toString)
                                       .toArray(String[]::new);
        searchProfile = new DropdownMenu(this, profileValues, FontHelper.tipBodyFont,
                Settings.CREAM_COLOR);
        searchProfile.setSelectedIndex(BattleAiMod.searchProfile.ordinal());
    }

    @Override
    public void update() {
        super.update();
        controllerMode.update();
        searchProfile.update();

        if (autoStartToggle.toggle.enabled != BattleAiMod.autoStartAi) {
            BattleAiMod.autoStartAi = autoStartToggle.toggle.enabled;
            BattleClientController.saveAutoStartAi(BattleAiMod.autoStartAi);
        }
    }

    @Override
    public void render(SpriteBatch sb) {
        super.render(sb);

        controllerMode.render(sb, LABEL_X_POS + 350 * Settings.scale, LABEL_Y_POS + 22 * Settings.scale);
        searchProfile.render(sb, LABEL_X_POS + 350 * Settings.scale,
                LABEL_Y_POS - 88 * Settings.scale);
    }

    @Override
    public void changedSelectionTo(DropdownMenu dropdownMenu, int i, String s) {
        if (dropdownMenu == controllerMode) {
            BattleAiMod.battleClientControllerMode = BattleClientController.ControllerMode
                    .valueOf(s);
            BattleClientController.saveMode(BattleAiMod.battleClientControllerMode);
        } else if (dropdownMenu == searchProfile) {
            BattleAiMod.searchProfile = SearchProfile.fromString(s);
            BattleClientController.saveSearchProfile(BattleAiMod.searchProfile);
        }
    }
}
