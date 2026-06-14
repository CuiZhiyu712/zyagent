package com.zyagent.job;

public record BossSessionStatus(
    boolean opened,
    String currentUrl,
    boolean probablyLoggedIn,
    String message
) {
}
