package dev.lvstrng.argon.utils;

public final class ExecutionMode {
    private static boolean strictMode = false;

    private ExecutionMode() {}

    public static boolean isStrictMode() {
        return strictMode;
    }

    public static void setStrictMode(boolean mode) {
        strictMode = mode;
    }
}
