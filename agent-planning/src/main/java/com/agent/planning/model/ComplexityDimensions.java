package com.agent.planning.model;

/**
 * Complexity scoring dimensions (doc 03-task-engine §8.2.1 AssessRequest → 6 dimensions).
 *
 * <p>6 dimensions scored 1-5 each (total 6-30):
 * goal (目标清晰度) / execution (执行难度) / domain (领域复杂度) /
 * knowledge (知识依赖度) / risk (风险等级) / context (上下文依赖度).</p>
 *
 * <p>Scoring rules (ref doc §6.2 assessor):
 * total ≤8 → L1 / 9-14 → L2 / >14 → L3; risk ≥4 forces L3 upgrade.</p>
 */
public class ComplexityDimensions {

    private int goal;           // 1-5: 目标清晰度 (1=清晰, 5=模糊)
    private int execution;      // 1-5: 执行难度 (1=单步, 5=多步复杂)
    private int domain;         // 1-5: 领域复杂度 (1=通用, 5=专业)
    private int knowledge;      // 1-5: 知识依赖度 (1=无, 5=深度依赖)
    private int risk;           // 1-5: 风险等级 (1=低, 5=高)
    private int context;        // 1-5: 上下文依赖度 (1=无状态, 5=强依赖)

    public ComplexityDimensions() {
    }

    public ComplexityDimensions(int goal, int execution, int domain, int knowledge, int risk, int context) {
        this.goal = goal;
        this.execution = execution;
        this.domain = domain;
        this.knowledge = knowledge;
        this.risk = risk;
        this.context = context;
    }

    public int getGoal() { return goal; }
    public void setGoal(int goal) { this.goal = goal; }

    public int getExecution() { return execution; }
    public void setExecution(int execution) { this.execution = execution; }

    public int getDomain() { return domain; }
    public void setDomain(int domain) { this.domain = domain; }

    public int getKnowledge() { return knowledge; }
    public void setKnowledge(int knowledge) { this.knowledge = knowledge; }

    public int getRisk() { return risk; }
    public void setRisk(int risk) { this.risk = risk; }

    public int getContext() { return context; }
    public void setContext(int context) { this.context = context; }

    /** Sum of all 6 dimensions. */
    public int total() {
        return goal + execution + domain + knowledge + risk + context;
    }
}
