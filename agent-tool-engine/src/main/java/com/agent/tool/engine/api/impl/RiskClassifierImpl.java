package com.agent.tool.engine.api.impl;

import com.agent.tool.engine.api.ApprovalStore;
import com.agent.tool.engine.api.RiskClassifier;
import com.agent.tool.engine.enums.SideEffect;
import com.agent.tool.engine.enums.ToolRiskLevel;
import com.agent.tool.engine.model.RiskAssessment;
import com.agent.tool.engine.model.ToolCallRequest;
import com.agent.tool.engine.model.ToolMeta;
import com.agent.tool.engine.risk.PiiDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * F8 / doc 05-tool-engine §4.2 risk classifier implementation.
 *
 * <p>Classification pipeline (doc 05 §4.2):</p>
 * <ol>
 *   <li>Base level from {@link SideEffect}:
 *     <ul>
 *       <li>NONE / READ_ONLY → R1</li>
 *       <li>WRITE_LOCAL → R2</li>
 *       <li>WRITE_EXTERNAL / DESTRUCTIVE → R3</li>
 *     </ul>
 *   </li>
 *   <li>Never-downgrade: {@code max(base, meta.declaredRiskLevel)} wins
 *       (a tool declaring R3 stays R3 even if sideEffect maps to R1).</li>
 *   <li>PII boost: if request params contain phone / email / id card / api key,
 *       force R3 (doc 05 §4.2 "R3 + 涉及 PII → 强制审批").</li>
 *   <li>Approval decision (doc 05 §4.3):
 *     <ul>
 *       <li>R1 → no approval</li>
 *       <li>R2 → no approval if same tenant + toolId + paramsHash approved
 *           within last 1h; otherwise required</li>
 *       <li>R3 → always required</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>Null meta → R3 safety fallback (doc 05 §4.2 "保守").</p>
 */
@Component
public class RiskClassifierImpl implements RiskClassifier {

    private static final Logger log = LoggerFactory.getLogger(RiskClassifierImpl.class);

    /** R2 approval shortcut lookback window (doc 05 §4.3 "最近 1h"). */
    private static final Duration R2_RECENT_APPROVAL_LOOKBACK = Duration.ofHours(1);

    private final PiiDetector piiDetector;
    private final ApprovalStore approvalStore;

    public RiskClassifierImpl(PiiDetector piiDetector, ApprovalStore approvalStore) {
        this.piiDetector = piiDetector;
        this.approvalStore = approvalStore;
    }

    @Override
    public RiskAssessment classify(ToolMeta meta, ToolCallRequest request) {
        // Null meta → safety fallback R3
        if (meta == null) {
            log.warn("风险分级收到空 meta, 默认 R3 (安全兜底)");
            return new RiskAssessment(ToolRiskLevel.R3, true,
                    "null meta safety fallback");
        }

        // Step 1: base level from sideEffect
        ToolRiskLevel base = baseLevelFromSideEffect(meta.getSideEffect());
        StringBuilder reason = new StringBuilder("sideEffect=")
                .append(meta.getSideEffect())
                .append(" → base=").append(base);

        // Step 2: never-downgrade vs declared level
        ToolRiskLevel declared = meta.getRiskLevel();
        if (declared != null && declared.getLevel() > base.getLevel()) {
            base = declared;
            reason.append("; declared=").append(declared).append(" upgrades");
        }

        // Step 3: PII boost (request present → scan input)
        boolean piiHit = false;
        if (request != null) {
            String inputJson = request.getInputJson();
            if (piiDetector.containsPii(inputJson)) {
                String category = piiDetector.detectCategory(inputJson);
                if (base != ToolRiskLevel.R3) {
                    reason.append("; PII=").append(category).append(" boosts R3");
                    base = ToolRiskLevel.R3;
                } else {
                    reason.append("; PII=").append(category).append(" confirms R3");
                }
                piiHit = true;
            }
        }

        // Step 4: approval decision
        boolean requiresApproval = decideApproval(base, request, meta, reason);

        log.debug("工具 [{}] 风险分级: {} (approval={}, reason={})",
                meta.getToolId(), base, requiresApproval, reason);
        return new RiskAssessment(base, requiresApproval, reason.toString());
    }

    private ToolRiskLevel baseLevelFromSideEffect(SideEffect sideEffect) {
        if (sideEffect == null) {
            return ToolRiskLevel.R1; // default conservative-low when not specified
        }
        return switch (sideEffect) {
            case NONE, READ_ONLY -> ToolRiskLevel.R1;
            case WRITE_LOCAL -> ToolRiskLevel.R2;
            case WRITE_EXTERNAL, DESTRUCTIVE -> ToolRiskLevel.R3;
        };
    }

    private boolean decideApproval(ToolRiskLevel level, ToolCallRequest request,
                                   ToolMeta meta, StringBuilder reason) {
        if (level == ToolRiskLevel.R1) {
            return false;
        }
        if (level == ToolRiskLevel.R3) {
            return true;
        }
        // R2: check recent approval shortcut
        if (request == null) {
            // No request context → default require approval
            reason.append("; R2 default requires approval");
            return true;
        }
        String tenantId = request.getTenantId();
        String toolId = meta.getToolId();
        String paramsHash = request.getInputHash();
        if (tenantId == null || toolId == null) {
            reason.append("; R2 missing tenant/toolId → approval required");
            return true;
        }
        boolean recentApproved = approvalStore
                .findRecentApproved(tenantId, toolId, paramsHash, R2_RECENT_APPROVAL_LOOKBACK)
                .isPresent();
        if (recentApproved) {
            reason.append("; R2 recent approval within 1h → skip");
            return false;
        }
        reason.append("; R2 no recent approval → required");
        return true;
    }

    /** Helper for tests / registry: classify meta-only (no PII / no recent approval). */
    @Override
    public ToolRiskLevel classify(ToolMeta meta) {
        return RiskClassifier.super.classify(meta);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RiskClassifierImpl that)) return false;
        return Objects.equals(piiDetector, that.piiDetector)
                && Objects.equals(approvalStore, that.approvalStore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(piiDetector, approvalStore);
    }
}
