package com.zyagent.config;

import com.zyagent.common.CurrentUserProvider;
import org.springframework.stereotype.Component;

/**
 * 无认证模式下的默认 owner 实现：从 {@code zyagent.task.owner-id} 读取。
 *
 * <p>这不代表已经具备多租户安全；认证接入前所有数据都属于同一个本地 owner。
 */
@Component
public class ConfiguredCurrentUserProvider implements CurrentUserProvider {
    public static final String DEFAULT_OWNER = "local-user";

    private final String ownerId;

    public ConfiguredCurrentUserProvider(ZyagentProperties properties) {
        ZyagentProperties.Task task = properties == null ? null : properties.task();
        this.ownerId = task == null || task.ownerId() == null || task.ownerId().isBlank()
            ? DEFAULT_OWNER
            : task.ownerId();
    }

    @Override
    public String currentUserId() {
        return ownerId;
    }
}
