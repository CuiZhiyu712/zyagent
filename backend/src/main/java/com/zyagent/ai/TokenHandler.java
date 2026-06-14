package com.zyagent.ai;

@FunctionalInterface
public interface TokenHandler {
    void onToken(String token);
}
