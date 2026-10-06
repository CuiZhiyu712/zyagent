package com.zyagent.modules.profile;

/** 提交的版本号与当前画像不一致，拒绝覆盖。 */
public class ProfileVersionConflictException extends RuntimeException {
    public ProfileVersionConflictException(String message) {
        super(message);
    }
}
