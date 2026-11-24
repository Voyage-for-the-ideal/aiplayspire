import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.megacrit.cardcrawl.random.Random;

/** Pure RNG/math only; no bootstrap, dungeon construction, or game actions. */
public final class RngProbe {
    private static String f(float v) { return Integer.toHexString(Float.floatToRawIntBits(v)); }
    private static String d(double v) { return Long.toHexString(Double.doubleToRawLongBits(v)); }
    private static void emit(Object v) { System.out.println("RNG\t" + v); }

    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("copy-global")) {
            MathUtils.random = new RandomXS128(101L);
            Random original = new Random(42L);
            emit("before\t" + ((RandomXS128) MathUtils.random).getState(0) + "\t" + ((RandomXS128) MathUtils.random).getState(1));
            Random copied = original.copy();
            emit("after\t" + ((RandomXS128) MathUtils.random).getState(0) + "\t" + ((RandomXS128) MathUtils.random).getState(1));
            emit("copy-value\t" + (original.randomLong() == copied.randomLong()));
            return;
        }
        long[] seeds = {0L, 1L, -1L, 42L, 123456789L, Long.MIN_VALUE, Long.MAX_VALUE, 0x123456789abcdefL};
        for (long seed : seeds) {
            RandomXS128 r = new RandomXS128(seed);
            for (int i = 0; i < 128; ++i) {
                emit("xs\t" + seed + "\t" + i + "\t" + r.nextInt() + "\t" + r.nextInt(101)
                    + "\t" + r.nextLong() + "\t" + r.nextLong(1000001L) + "\t" + f(r.nextFloat())
                    + "\t" + d(r.nextDouble()) + "\t" + r.nextBoolean()
                    + "\t" + r.getState(0) + "\t" + r.getState(1));
            }
            Random w = new Random(seed);
            for (int i = 0; i < 128; ++i) {
                emit("wrapper\t" + seed + "\t" + i + "\t" + w.random(99) + "\t" + w.random(-7, 99)
                    + "\t" + w.random(1000001L) + "\t" + w.random(-100L, 1000001L)
                    + "\t" + w.randomLong() + "\t" + w.randomBoolean() + "\t" + w.randomBoolean(0.3f)
                    + "\t" + f(w.random()) + "\t" + f(w.random(3.7f)) + "\t" + f(w.random(-3.7f, 8.1f))
                    + "\t" + w.counter + "\t" + w.random.getState(0) + "\t" + w.random.getState(1));
            }
            Random c = w.copy();
            emit("copy\t" + seed + "\t" + c.counter + "\t" + c.randomLong() + "\t" + w.randomLong());
            Random advanced = new Random(seed);
            for (int i = 0; i < 37; ++i) advanced.random(999);
            Random reconstructed = new Random(seed, 37);
            emit("reconstruct\t" + seed + "\t" + advanced.counter + "\t" + reconstructed.counter
                + "\t" + advanced.randomLong() + "\t" + reconstructed.randomLong());
        }
        for (int i = -1000; i <= 1000; ++i) {
            float x = i / 17.0f;
            emit("math\t" + i + "\t" + f(MathUtils.sin(x)) + "\t" + f(MathUtils.cos(x))
                + "\t" + MathUtils.round(x) + "\t" + MathUtils.floor(x) + "\t" + MathUtils.ceil(x)
                + "\t" + f(MathUtils.atan2(x, x + 0.1f)));
        }
    }
}
