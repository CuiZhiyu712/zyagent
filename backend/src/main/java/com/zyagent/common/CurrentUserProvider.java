package com.zyagent.common;

/**
 * 当前用户身份的唯一适配点。
 *
 * <p>当前为无认证单用户模式，实现返回配置的默认 owner；接入认证后只需替换实现，
 * 让 owner 来自真实身份上下文，而不需要修改各 Repository 调用。
 */
public interface CurrentUserProvider {
    String currentUserId();
}
