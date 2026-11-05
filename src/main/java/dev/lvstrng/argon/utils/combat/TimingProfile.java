package dev.lvstrng.argon.utils.combat;

public final class TimingProfile {

    public static final double MAX_DELAY_MS = 40.0;
    public static final long SHIELD_STUN_FIRST_GAP_MS = 19L;
    public static final long SHIELD_STUN_FINAL_GAP_MS = 30L;

    public static final String BLATANT = "Blatant";
    public static final String STRICT = "Strict";
    public static final String LEGIT = "Legit";
    public static final String ULTRA_LEGIT = "Ultra Legit";

    public static final String DEFAULT = LEGIT;

    private static volatile double minMs = 26.0;
    private static volatile double maxMs = 34.0;

    private TimingProfile() {
    }

    public static void set(String mode) {
        if (BLATANT.equalsIgnoreCase(mode)) {
            apply(1.0, 3.0);
        } else if (STRICT.equalsIgnoreCase(mode)) {
            apply(10.0, 15.0);
        } else if (ULTRA_LEGIT.equalsIgnoreCase(mode)) {
            apply(36.0, 40.0);
        } else {
            apply(26.0, 34.0);
        }
    }

    private static void apply(double lo, double hi) {
        minMs = lo;
        maxMs = Math.min(hi, MAX_DELAY_MS);
    }

    public static double minDelayMs() {
        return minMs;
    }

    public static double maxDelayMs() {
        return maxMs;
    }

    public static long delayMs() {
        return Math.round(minMs + (maxMs - minMs) * 0.4);
    }

    public static double delayMsExact() {
        double lo = minMs;
        double hi = maxMs;
        if (hi - lo < 1.0e-9) {
            return lo;
        }
        return Math.max(lo, Math.min(hi, lo + (hi - lo) * 0.4));
    }
}
```[cite: 1]

5. Sabse neeche jaakar **"Commit changes..."** par click kar de. 

Bas itna hi karna hai! Isse `combat` folder bhi ban jayega aur file bhi uske andar save ho jayegi. Ise karke bata phir aage chalte hain!
