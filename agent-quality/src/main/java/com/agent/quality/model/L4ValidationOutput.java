package com.agent.quality.model;

import com.agent.quality.enums.L4ValidationResult;

/**
 * L4 三级校验输出 (doc 11-detail-flow F9.D2/D3/D4).
 *
 * <p>passed=true 时 result=PASS；passed=false 时 result 为具体失败类型.
 * cosineSim 为 F9.D3 事实一致性相似度，overallScore 为 F9.D4 强模型四维度评分.</p>
 */
public class L4ValidationOutput {

    private boolean passed;
    private L4ValidationResult result;
    private String violationDetail;
    private double cosineSim;
    private double overallScore;

    public L4ValidationOutput() {
    }

    public L4ValidationOutput(boolean passed, L4ValidationResult result) {
        this.passed = passed;
        this.result = result;
    }

    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }

    public L4ValidationResult getResult() { return result; }
    public void setResult(L4ValidationResult result) { this.result = result; }

    public String getViolationDetail() { return violationDetail; }
    public void setViolationDetail(String violationDetail) { this.violationDetail = violationDetail; }

    public double getCosineSim() { return cosineSim; }
    public void setCosineSim(double cosineSim) { this.cosineSim = cosineSim; }

    public double getOverallScore() { return overallScore; }
    public void setOverallScore(double overallScore) { this.overallScore = overallScore; }
}
