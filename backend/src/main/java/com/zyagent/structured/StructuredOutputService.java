package com.zyagent.structured;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StructuredOutputService {
    private final ObjectMapper objectMapper;

    public StructuredOutputService() {
        this(new ObjectMapper());
    }

    public StructuredOutputService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public StudyPlanOutput parseStudyPlan(String raw) {
        try {
            return objectMapper.readValue(raw, StudyPlanOutput.class);
        } catch (Exception ex) {
            return new StudyPlanOutput("学习计划", List.of(raw == null || raw.isBlank() ? "暂无可解析内容" : raw), "模型输出不是标准 JSON，已保留原文。");
        }
    }

    public JobAnalysisOutput parseJobAnalysis(String raw) {
        try {
            return objectMapper.readValue(raw, JobAnalysisOutput.class);
        } catch (Exception ex) {
            return new JobAnalysisOutput("岗位分析", "", List.of(), List.of(raw == null ? "" : raw));
        }
    }

    public InterviewScoreOutput parseInterviewScore(String raw) {
        try {
            return objectMapper.readValue(raw, InterviewScoreOutput.class);
        } catch (Exception ex) {
            return new InterviewScoreOutput(0, List.of(), List.of(raw == null ? "" : raw));
        }
    }
}
