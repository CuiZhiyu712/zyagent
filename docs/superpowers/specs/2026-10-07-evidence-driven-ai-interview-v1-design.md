# Evidence-Driven AI Interview v1 Design

**Date:** 2026-10-07

**Status:** Approved for implementation by the user's request to turn the preceding optimization analysis into a spec and begin subagent execution.

## 1. Problem

The current interview workflow has a sound persisted session/turn state machine, idempotent answer submission, timeout handling, and unusable-evaluation fallback. Its production `InterviewAgent`, however, is `RuleBasedInterviewAgent`: questions come from a fixed pool and scores are derived from answer length and keyword matches. This makes the workflow demonstrable but does not materially improve interview quality.

The application also does not tell the user whether an interview is using a real model, the deterministic rule demo, or an unavailable model with local fallback. A valid JSON payload from the rule implementation is currently treated as a usable evaluation, which can be mistaken for an AI assessment.

## 2. Goal

Deliver the first narrow, independently testable quality improvement:

- generate interview questions with the configured DeepSeek-backed Spring AI client;
- ground question generation in the selected JD snapshot and prior interview turns;
- evaluate answers with strict structured output and answer-derived evidence quotes;
- fail honestly when the model is unavailable or produces invalid/fabricated evidence;
- keep the deterministic rule implementation only as an explicitly selected demo provider;
- expose the active provider and availability to the frontend.

This is the first subproject of the broader quality-first roadmap. Real end-to-end RAG evaluation, frontend route decomposition, Maven wrapper/CI, resume analysis, and voice interview remain separate specs.

## 3. Non-goals

- No voice interview.
- No new model-provider management UI.
- No Redis Stream or asynchronous evaluation pipeline.
- No database schema change.
- No rewrite of the interview session state machine.
- No resume parsing or automatic resume-context attachment in this iteration.
- No claim that the rule provider is a model evaluation.

## 4. Considered Approaches

### A. Reuse `AiChatService`

This is the smallest change, but `AiChatService.complete()` converts missing credentials and model exceptions into ordinary text. The interview parser would only see malformed JSON and could not distinguish configuration failure from a bad model response. Rejected because it preserves ambiguous fallback behavior.

### B. Add an explicit conditional `LlmInterviewAgent` and keep the rule provider opt-in

The LLM adapter calls the low-level `AiChatClient` directly, owns interview-specific prompts, reports its provider/availability, and lets `InterviewAgentService` retain timeout and validation responsibilities. `RuleBasedInterviewAgent` is created only when `zyagent.interview.provider=rule`.

This is the selected approach. It is small, testable, and makes runtime behavior truthful.

### C. Build a generic multi-provider interview engine now

This would introduce provider registries, runtime switching, model metadata persistence, and configuration UI. Rejected for v1 because it adds breadth before the quality path is proven.

## 5. Architecture

```text
InterviewController
    |-- GET /api/interviews/capabilities
    v
InterviewService
    v
InterviewAgentService  -- timeout, JSON parsing, validation, honest fallback
    v
InterviewAgent (port)
    |-- LlmInterviewAgent       [provider=llm, default]
    |       |-- question prompt resource
    |       |-- evaluation prompt resource
    |       `-- AiChatClient -> Spring AI DeepSeek
    `-- RuleBasedInterviewAgent [provider=rule, explicit demo]
```

`InterviewAgentService` remains the only component allowed to convert raw model output into domain types. The LLM adapter only assembles prompts and returns raw text. This keeps output validation independent of the model implementation.

## 6. Configuration and Provider Semantics

Add `zyagent.interview.provider`, backed by `ZYAGENT_INTERVIEW_PROVIDER`:

- `llm` (default): create `LlmInterviewAgent`. If `DEEPSEEK_API_KEY` is blank, capabilities report unavailable and calls produce the existing unusable fallback behavior.
- `rule`: create `RuleBasedInterviewAgent`. Capabilities identify it as `rule_demo`; the UI must not label it as AI scoring.
- any other value: application startup fails through missing `InterviewAgent`, rather than silently choosing a provider.

The default remains `llm` to make the production intent explicit. Local deterministic demonstrations must set `ZYAGENT_INTERVIEW_PROVIDER=rule`.

## 7. Prompt Contracts

Prompts live under `backend/src/main/resources/prompts/` and are versionable resources rather than Java strings.

### Question generation input

- interview type and difficulty;
- JD snapshot, or an explicit “not provided” marker;
- compact history containing prior question, answer, and usable evaluation summary;
- instruction not to repeat previous questions;
- strict JSON output: `{"question":"...","focus":"..."}`.

The question should test one topic at a time and, when history exists, target an unresolved weakness rather than generically asking the next fixed question.

### Answer evaluation input

- interview type, difficulty, and JD snapshot;
- current question and complete answer;
- whether a follow-up is allowed;
- four integer scores from 0 through 5;
- concise explanations;
- `evidence` containing short exact quotes copied from the candidate's answer;
- optional follow-up question that references a concrete omission in the answer.

The prompt must treat JD text and candidate answers as untrusted data, not instructions.

## 8. Validation and Error Handling

`InterviewAgentService` must reject an evaluation as unusable when:

- any of the four score fields is missing, non-integral, or outside 0–5;
- `explanations` or `evidence` is not an array of strings;
- any nonblank evidence item is not an exact substring of the submitted answer;
- the payload is not valid JSON after optionally removing one outer Markdown JSON fence;
- follow-up is requested but no nonblank follow-up question is supplied.

Rejected results use `InterviewEvaluation.fallback(...)`, remain excluded from averages/profile suggestions, and include a diagnostic note without exposing prompt contents or credentials.

Question generation remains usable only when a nonblank `question` string is present. Timeout, missing credentials, exceptions, or invalid output use the existing local fallback question with `usable=false`.

## 9. Capability Contract and UI

Add `GET /api/interviews/capabilities` returning:

```json
{
  "provider": "llm",
  "available": true,
  "label": "DeepSeek AI 面试官"
}
```

For the rule provider:

```json
{
  "provider": "rule_demo",
  "available": true,
  "label": "规则演示模式"
}
```

For LLM without a key, `available=false`. The interview page displays this status beside the session state. It does not infer configuration from a hard-coded badge.

When creating an interview, the frontend includes the selected job's `rawText` as `jdSnapshot`. Missing JD remains supported for general interviews.

## 10. Testing

Implementation follows red-green-refactor.

- Unit-test prompt composition with JD and history, prompt-injection boundaries, and exact JSON instructions.
- Unit-test missing-key behavior without invoking `AiChatClient`.
- Unit-test strict score validation, fenced JSON, fabricated evidence rejection, and follow-up validation.
- Controller-test the capabilities contract for both provider modes at the service boundary.
- Node-test construction of the interview creation payload so selected JD text is preserved.
- Run the focused tests, then the complete backend suite when a Maven executable/wrapper is available, all frontend Node tests, and the frontend production build.

## 11. Acceptance Criteria

1. With `ZYAGENT_INTERVIEW_PROVIDER=llm` and a configured key, both question generation and answer evaluation call `AiChatClient` with resource-backed prompts containing the JD and relevant history.
2. With LLM selected and no key, no model call occurs; capability is unavailable and evaluations are stored as unusable fallbacks.
3. With `provider=rule`, the existing deterministic flow still works and the frontend visibly labels it “规则演示模式”.
4. A fabricated evidence quote or invalid score makes the evaluation unusable and prevents it from affecting averages/profile suggestions.
5. The frontend sends the selected JD raw text as `jdSnapshot` and renders provider availability.
6. Existing interview state, idempotency, persistence, and compatibility endpoints remain unchanged.

## 12. Follow-up Specs

After this slice is verified:

1. real-corpus Hybrid RAG evaluation and production embedding/reranker defaults;
2. frontend router/page decomposition and bundle splitting;
3. Maven Wrapper, CI, database migrations, and end-to-end golden-path tests;
4. resume/JD/profile evidence fusion for adaptive interview selection.
