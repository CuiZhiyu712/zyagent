package com.zyagent.agent;

/**
 * 本轮路由相对上一轮任务的决策。
 *
 * <pre>
 * CONTINUE -> 沿用上一轮的 Skill（当前任务还在继续）
 * SWITCH   -> 切到另一个 Skill（新任务）
 * CLARIFY  -> 请求缺少明确对象，先向用户澄清而不是硬选一个 Skill
 * </pre>
 */
public enum RouteAction {
    CONTINUE,
    SWITCH,
    CLARIFY
}
