package com.agent.tool.engine.api;

import com.agent.tool.engine.enums.ToolRiskLevel;
import com.agent.tool.engine.model.RiskAssessment;
import com.agent.tool.engine.model.ToolCallRequest;
import com.agent.tool.engine.model.ToolMeta;

/**
 * Risk classifier port (doc 05-tool-engine §4.2 risk grading).
 *
 * <p>Classification rule (doc 05 §4.2):</p>
 * <ul>
 *   <li>R1 (low): SideEffect ∈ {NONE, READ_ONLY}</li>
 *   <li>R2 (medium): SideEffect = WRITE_LOCAL</li>
 *   <li>R3 (high): SideEffect ∈ {WRITE_EXTERNAL, DESTRUCTIVE}</li>
 *   <li>Boost: R3 + 涉及 PII → 强制审批 (force R3 even if tool declares lower)</li>
 *   <li>Never downgrades: max(base, meta.riskLevel) wins</li>
 * </ul>
 *
 * <p>Approval policy (doc 05 §4.3):</p>
 * <ul>
 *   <li>R1 → no approval</li>
 *   <li>R2 → no approval if same tenant + same toolId + same paramsHash approved
 *       within last 1h; otherwise approval required</li>
 *   <li>R3 → always approval required</li>
 * </ul>
 */
public interface RiskClassifier {

    /**
     * Classify tool + request into a full risk assessment.
     *
     * <p>The assessment includes the resolved {@link ToolRiskLevel}, whether
     * approval is required, and a reason string for audit logging.</p>
     *
     * @param meta    tool metadata (must not be null)
     * @param request call request (carries params + tenantId for PII scan
     *                and recent-approval lookup); may be null when only
     *                base level is needed
     * @return risk assessment (never null; null meta → R3 safety fallback)
     */
    RiskAssessment classify(ToolMeta meta, ToolCallRequest request);

    /**
     * Classify tool metadata only (no PII boost, no recent-approval shortcut).
     *
     * <p>Shortcut for {@link #classify(ToolMeta, ToolCallRequest)} with
     * {@code request=null}. Used by ToolRegistry during registration to
     * compute the persisted default risk level.</p>
     */
    default ToolRiskLevel classify(ToolMeta meta) {
        RiskAssessment assessment = classify(meta, null);
        return assessment.getRiskLevel();
    }
}
