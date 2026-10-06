package com.zyagent.modules.job;

import java.util.Collection;

public final class TestAssertions {
    private TestAssertions() {
    }

    public static void equals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " expected <" + expected + "> but got <" + actual + ">");
        }
    }

    public static void containsAll(Collection<String> actual, Collection<String> expected, String label) {
        for (String item : expected) {
            if (!actual.contains(item)) {
                throw new AssertionError(label + " missing <" + item + "> in " + actual);
            }
        }
    }

    public static void isTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label + " expected true");
        }
    }
}
