import com.badlogic.gdx.Gdx;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.headless.GameKernel;
import com.megacrit.cardcrawl.random.Random;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Shared invariants for the deployed BOOT-only adapter, not a run lifecycle. */
public final class NoRunGuard {
    private NoRunGuard() {}

    public static GameKernel kernel() {
        return new GameKernel() {
            @Override
            public void newRun(long seed, AbstractPlayer.PlayerClass character, int ascension) {
                throw new IllegalStateException("Run creation is disabled in the read-only adapter");
            }
        };
    }

    public static void verify() {
        for (String gate : new String[] {
                "/home/ubuntu/headless-spire-poc/ALLOW_COLLECTION",
                "/home/ubuntu/aiplayspire-runtime/ALLOW_COLLECTION"}) {
            if (Files.exists(Path.of(gate), LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("Collection gate must stay absent");
            }
        }
        if (AbstractDungeon.player != null || AbstractDungeon.currMapNode != null
                || AbstractDungeon.map != null || AbstractDungeon.floorNum != 0
                || AbstractDungeon.actNum != 0 || Settings.seed != null || Settings.seedSet) {
            throw new IllegalStateException("Read-only adapter requires an unstarted run");
        }
        for (Random rng : new Random[] {AbstractDungeon.monsterRng, AbstractDungeon.mapRng,
                AbstractDungeon.eventRng, AbstractDungeon.merchantRng, AbstractDungeon.cardRng,
                AbstractDungeon.treasureRng, AbstractDungeon.relicRng, AbstractDungeon.potionRng,
                AbstractDungeon.monsterHpRng, AbstractDungeon.aiRng, AbstractDungeon.shuffleRng,
                AbstractDungeon.cardRandomRng, AbstractDungeon.miscRng, AbstractDungeon.headlessRng}) {
            if (rng != null) throw new IllegalStateException("Unexpected initialized run RNG");
        }
        if (Gdx.graphics == null || !"Mock".equals(String.valueOf(Gdx.graphics.getType()))
                || Gdx.gl != null || Gdx.gl20 != null || Gdx.gl30 != null) {
            throw new IllegalStateException("Expected pure Java headless graphics stubs");
        }
    }
}
