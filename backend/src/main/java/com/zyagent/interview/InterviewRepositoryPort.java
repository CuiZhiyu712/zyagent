package com.zyagent.interview;

import java.util.List;
import java.util.Optional;

public interface InterviewRepositoryPort {
    void saveSession(InterviewSession session);

    Optional<InterviewSession> findSession(String id);

    void saveTurn(InterviewTurn turn);

    default Optional<InterviewTurn> findTurn(String sessionId, int turnNo) {
        return Optional.empty();
    }

    /** 按 requestId 查找已处理的轮次，用于提交幂等。 */
    default Optional<InterviewTurn> findTurnByRequestId(String sessionId, String requestId) {
        return Optional.empty();
    }

    default List<InterviewTurn> findTurns(String sessionId) {
        return List.of();
    }

    /** 分页历史；{@code ownerId} 为 null 时不按归属过滤。 */
    default List<InterviewSession> findSessions(String ownerId, int offset, int limit) {
        return List.of();
    }

    default long countSessions(String ownerId) {
        return 0L;
    }
}
