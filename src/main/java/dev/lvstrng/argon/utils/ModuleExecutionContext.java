package dev.lvstrng.argon.utils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

public final class ModuleExecutionContext {
    private static final ThreadLocal<Deque<Object>> OWNERS = ThreadLocal.withInitial(ArrayDeque::new);

    private ModuleExecutionContext() {
    }

    public static Object currentOwner() {
        return OWNERS.get().peekLast();
    }

    public static void run(Object owner, Runnable action) {
        if (action == null) return;
        if (owner == null) {
            action.run();
            return;
        }
        Deque<Object> stack = OWNERS.get();
        stack.addLast(owner);
        try {
            action.run();
        } finally {
            stack.removeLast();
        }
    }

    public static <T> T call(Object owner, Supplier<T> action) {
        if (action == null) return null;
        if (owner == null) return action.get();
        Deque<Object> stack = OWNERS.get();
        stack.addLast(owner);
        try {
            return action.get();
        } finally {
            stack.removeLast();
        }
    }
}
