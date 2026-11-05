package dev.lvstrng.argon.utils;

import dev.lvstrng.argon.mixin.MinecraftClientAccessor;

public final class ExecutionMode {
    public static final String LEGIT = "Legit";
    public static final String DIRECT = "Direct";

    private static volatile boolean direct = false;

    private ExecutionMode() {
    }

    public static boolean isDirect() {
        return direct;
    }

    public static boolean isLegit() {
        return !direct;
    }

    public static void set(String mode) {
        direct = DIRECT.equalsIgnoreCase(mode);
    }

    public static void relaxAttackCooldown(MinecraftClientAccessor accessor) {
        if (!direct && accessor != null) {
            accessor.setAttackCooldown(0);
        }
    }
}
