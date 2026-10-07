package com.zyagent.interview;

import com.zyagent.common.CurrentUserProvider;
import com.zyagent.config.ZyagentProperties;
import com.zyagent.profile.ProfileService;
import com.zyagent.profile.SkillLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 模拟面试生命周期：创建并生成首题、幂等提交回答、追问/换题决策、结束总结与画像建议草案。
 */
@Service
public class InterviewService {
    private static final Logger log = LoggerFactory.getLogger(InterviewService.class);
    private static final String DEFAULT_OWNER = "local-user";

    private final InterviewRepositoryPort repository;
    private final InterviewAgentService agentService;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectProvider<ProfileService> profileServiceProvider;
    private final int defaultMaxTurns;
    private final int defaultMaxFollowUps;

    public InterviewService(InterviewRepositoryPort repository, InterviewAgentService agentService) {
        this(repository, agentService, null, null, null);
    }

    @Autowired
    public InterviewService(
        InterviewRepositoryPort repository,
        InterviewAgentService agentService,
        ZyagentProperties properties,
        CurrentUserProvider currentUserProvider,
        ObjectProvider<ProfileService> profileServiceProvider
    ) {
        this.repository = repository;
        this.agentService = agentService;
        this.currentUserProvider = currentUserProvider == null ? () -> DEFAULT_OWNER : currentUserProvider;
        this.profileServiceProvider = profileServiceProvider;
        ZyagentProperties.Interview interview = properties == null ? null : properties.interview();
        this.defaultMaxTurns = interview == null ? InterviewSession.DEFAULT_MAX_TURNS : Math.max(1, interview.maxTurns());
        this.defaultMaxFollowUps = interview == null ? InterviewSession.DEFAULT_MAX_FOLLOW_UPS : Math.max(0, interview.maxFollowUps());
    }

    public InterviewSession start(String jobId, String jdSnapshot, String type, String difficulty) {
        InterviewSession created = InterviewSession.create(
            currentUser(), jobId, jdSnapshot, type, difficulty, defaultMaxTurns, defaultMaxFollowUps);
        repository.saveSession(created);
        InterviewSession started = created.start();
        repository.saveSession(started);
        QuestionPlan plan = agentService.nextQuestion(started, List.of());
        repository.saveTurn(InterviewTurn.question(started.id(), 1, formatQuestion(plan)));
        return started;
    }

    public InterviewSession get(String sessionId) {
        return repository.findSession(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("面试会话不存在：" + sessionId));
    }

    public List<InterviewTurn> turns(String sessionId) {
        get(sessionId);
        return repository.findTurns(sessionId);
    }

    /**
     * 提交当前轮回答：幂等（同 requestId 重复提交直接返回已有轮次），写评价并决定追问或换题。
     */
    public InterviewTurn submitTurn(String sessionId, String answer, String requestId) {
        InterviewSession session = get(sessionId);
        if (requestId != null && !requestId.isBlank()) {
            Optional<InterviewTurn> replay = repository.findTurnByRequestId(sessionId, requestId);
            if (replay.isPresent()) {
                return replay.get();
            }
        }
        if (!session.canContinue()) {
            throw new IllegalStateException("面试会话已结束或已达到最大轮次");
        }
        int turnNo = session.currentTurn() + 1;
        InterviewTurn current = repository.findTurn(sessionId, turnNo)
            .orElseThrow(() -> new IllegalStateException("第 " + turnNo + " 轮问题不存在"));
        List<InterviewTurn> history = repository.findTurns(sessionId);
        boolean allowFollowUp = trailingFollowUps(history) < session.maxFollowUps();

        AnswerAssessment assessment = agentService.assess(session, current.question(), answer, allowFollowUp);
        InterviewTurn evaluated = current
            .answered(answer, requestId)
            .evaluated(assessment.evaluation(), assessment.followUp() ? assessment.followUpQuestion() : null);
        repository.saveTurn(evaluated);

        InterviewSession advanced = session.nextTurn();
        if (advanced.canContinue()) {
            repository.saveSession(advanced);
            String nextQuestion;
            if (evaluated.hasFollowUp()) {
                nextQuestion = evaluated.followUpQuestion();
            } else {
                List<InterviewTurn> currentHistory = repository.findTurns(sessionId);
                QuestionPlan plan = agentService.nextQuestion(advanced, currentHistory);
                nextQuestion = formatQuestion(plan);
            }
            repository.saveTurn(InterviewTurn.question(sessionId, turnNo + 1, nextQuestion));
        } else {
            repository.saveSession(advanced.complete(summarize(advanced, repository.findTurns(sessionId))));
        }
        draftProfileSuggestion(session, evaluated);
        return evaluated;
    }

    public InterviewSession complete(String sessionId) {
        InterviewSession session = get(sessionId);
        InterviewSession completed = session.complete(summarize(session, repository.findTurns(sessionId)));
        repository.saveSession(completed);
        return completed;
    }

    public InterviewSession abort(String sessionId) {
        InterviewSession aborted = get(sessionId).abort();
        repository.saveSession(aborted);
        return aborted;
    }

    public SessionPage sessions(int page, int size) {
        int safeSize = Math.max(1, Math.min(100, size));
        int safePage = Math.max(0, page);
        List<InterviewSession> items = repository.findSessions(currentUser(), safePage * safeSize, safeSize);
        long total = repository.countSessions(currentUser());
        return new SessionPage(items, safePage, safeSize, total);
    }

    private String summarize(InterviewSession session, List<InterviewTurn> turns) {
        List<InterviewTurn> scored = turns.stream()
            .filter(turn -> turn.evaluation() != null && turn.evaluation().usable())
            .toList();
        long unusable = turns.stream()
            .filter(turn -> turn.evaluation() != null && !turn.evaluation().usable())
            .count();
        if (scored.isEmpty()) {
            return "本次面试完成 " + session.currentTurn() + " 轮；评价不可用，建议重试后再复盘。";
        }
        double average = scored.stream().mapToDouble(turn -> turn.evaluation().average()).average().orElse(0.0);
        StringBuilder summary = new StringBuilder();
        summary.append("共 ").append(session.currentTurn()).append(" 轮，平均分 ")
            .append(String.format(Locale.ROOT, "%.2f", average))
            .append("；最弱维度：").append(weakestDimension(scored)).append("。");
        if (unusable > 0) {
            summary.append("另有 ").append(unusable).append(" 轮评价不可用。");
        }
        return summary.toString();
    }

    private String weakestDimension(List<InterviewTurn> scored) {
        int technical = 0;
        int completeness = 0;
        int project = 0;
        int structure = 0;
        for (InterviewTurn turn : scored) {
            technical += turn.evaluation().technicalCorrectness();
            completeness += turn.evaluation().completeness();
            project += turn.evaluation().projectEvidence();
            structure += turn.evaluation().expressionStructure();
        }
        int min = Math.min(Math.min(technical, completeness), Math.min(project, structure));
        if (min == project) {
            return "项目证据";
        }
        if (min == completeness) {
            return "完整性";
        }
        if (min == technical) {
            return "技术正确性";
        }
        return "表达结构";
    }

    private void draftProfileSuggestion(InterviewSession session, InterviewTurn turn) {
        ProfileService profileService = profileServiceProvider == null ? null : profileServiceProvider.getIfAvailable();
        if (profileService == null || turn.evaluation() == null || !turn.evaluation().usable()) {
            return;
        }
        try {
            profileService.suggest(session.ownerId(), skillKeyFor(session), levelFor(turn.evaluation()),
                "面试第 " + turn.turnNo() + " 轮：" + abbreviate(turn.question())
                    + " → 平均分 " + String.format(Locale.ROOT, "%.2f", turn.evaluation().average()),
                "INTERVIEW", session.id() + "#turn-" + turn.turnNo());
        } catch (RuntimeException ex) {
            log.warn("面试评价生成画像建议失败：{}", ex.getClass().getSimpleName());
        }
    }

    private String formatQuestion(QuestionPlan plan) {
        return plan.usable() ? plan.question() : "【本地兜底问题】" + plan.question();
    }

    private static String skillKeyFor(InterviewSession session) {
        String type = session.interviewType() == null || session.interviewType().isBlank()
            ? "综合面" : session.interviewType();
        return "面试-" + type;
    }

    private static SkillLevel levelFor(InterviewEvaluation evaluation) {
        double average = evaluation.average();
        if (average <= 2.0) {
            return SkillLevel.AWARENESS;
        }
        if (average <= 3.0) {
            return SkillLevel.BASIC;
        }
        return average <= 4.0 ? SkillLevel.WORKING : SkillLevel.PROFICIENT;
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").strip();
        return normalized.length() > 40 ? normalized.substring(0, 40) + "..." : normalized;
    }

    private static int trailingFollowUps(List<InterviewTurn> history) {
        int count = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).state() == InterviewTurnState.FOLLOW_UP_READY) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private String currentUser() {
        String owner = currentUserProvider.currentUserId();
        return owner == null || owner.isBlank() ? DEFAULT_OWNER : owner;
    }

    public record SessionPage(List<InterviewSession> items, int page, int size, long total) {
    }
}
