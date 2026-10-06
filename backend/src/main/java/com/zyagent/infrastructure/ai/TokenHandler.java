package com.zyagent.infrastructure.ai;

@FunctionalInterface
public interface TokenHandler {
    void onToken(String token);
}
