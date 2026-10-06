package com.zyagent.modules.agent;

import com.zyagent.modules.job.TestAssertions;

import java.util.List;

public class PromptAdvisorChainTest {
    public static void run() {
        keepsTheSelectedDayScopeFromThePreviousPlan();
    }

    private static void keepsTheSelectedDayScopeFromThePreviousPlan() {
        PromptAdvisorChain advisor = new PromptAdvisorChain();
        String memory = "助手：全覆盖学习计划清单，共约365题，按14天计划组织。\n"
            + "Day1｜模块一：自我介绍与项目总览（Q1–10）\n"
            + "问题：Q1-Q10\n学习思路：90秒口述\n"
            + "Day2｜模块二：求职Agent平台架构与Agent基础（Q11–40）";

        String prompt = advisor.render("现在进行Day1的学习", memory, List.of());

        TestAssertions.isTrue(prompt.contains("Day1｜模块一：自我介绍与项目总览"), "prompt keeps day one scope");
        TestAssertions.isTrue(prompt.contains("Q1-Q10"), "prompt keeps planned question range");
        TestAssertions.isTrue(prompt.contains("不要重新发明题目"), "prompt adds continuity constraint");
        TestAssertions.isTrue(!prompt.contains("Day2｜模块二"), "prompt excludes next day scope");
    }
}
