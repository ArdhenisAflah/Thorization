package thorization.report;

import thorization.ai.AiVerdict;
import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.UserRole;
import thorization.model.Verdict;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes statistical metrics, compliance posture score, and distribution breakdowns
 * across audit records and user roles.
 */
public record ReportMetrics(
        int totalRequests,
        int totalReplays,
        int enforcedCount,
        int bypassCount,
        int indeterminableCount,
        int aiValidatedAccessCount,
        int aiAccessDeniedCount,
        int aiInconclusiveCount,
        int aiErrorCount,
        int aiPendingCount,
        int aiUntestedCount,
        double securityPostureScore,
        Map<String, RoleStatistics> roleStats
) {

    public record RoleStatistics(
            String roleId,
            String roleName,
            int totalTested,
            int enforcedCount,
            int bypassCount,
            int indeterminableCount,
            int aiValidatedCount
    ) {}

    public static ReportMetrics compute(List<AuditRecord> records, List<UserRole> roles) {
        if (records == null || records.isEmpty()) {
            return new ReportMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 100.0, Collections.emptyMap());
        }

        int totalRequests = records.size();
        int totalReplays = 0;
        int enforced = 0;
        int bypass = 0;
        int indeterminable = 0;

        int aiValidated = 0;
        int aiDenied = 0;
        int aiInconclusive = 0;
        int aiError = 0;
        int aiPending = 0;
        int aiUntested = 0;

        Map<String, Integer> roleTested = new LinkedHashMap<>();
        Map<String, Integer> roleEnforced = new LinkedHashMap<>();
        Map<String, Integer> roleBypass = new LinkedHashMap<>();
        Map<String, Integer> roleIndeterminable = new LinkedHashMap<>();
        Map<String, Integer> roleAiVal = new LinkedHashMap<>();
        Map<String, String> roleNames = new LinkedHashMap<>();

        if (roles != null) {
            for (UserRole r : roles) {
                if (r == null || r.getId() == null) continue;
                roleNames.put(r.getId(), r.getName() != null ? r.getName() : r.getId());
                roleTested.put(r.getId(), 0);
                roleEnforced.put(r.getId(), 0);
                roleBypass.put(r.getId(), 0);
                roleIndeterminable.put(r.getId(), 0);
                roleAiVal.put(r.getId(), 0);
            }
        }
        roleNames.put("unauthenticated", "Unauthenticated");
        roleTested.put("unauthenticated", 0);
        roleEnforced.put("unauthenticated", 0);
        roleBypass.put("unauthenticated", 0);
        roleIndeterminable.put("unauthenticated", 0);
        roleAiVal.put("unauthenticated", 0);

        for (AuditRecord rec : records) {
            if (rec == null) continue;

            // 1. Role Results
            Map<String, AuthorizationCheckResult> roleMap = rec.getRoleResults();
            if (roleMap != null) {
                for (Map.Entry<String, AuthorizationCheckResult> entry : roleMap.entrySet()) {
                    String roleId = entry.getKey();
                    AuthorizationCheckResult res = entry.getValue();
                    if (res == null) continue;

                    totalReplays++;
                    String rName = res.getRoleName() != null ? res.getRoleName() : roleNames.getOrDefault(roleId, "Role " + roleId);
                    roleNames.putIfAbsent(roleId, rName);

                    Verdict v = res.getVerdict();
                    if (v == Verdict.ENFORCED) {
                        enforced++;
                        roleEnforced.merge(roleId, 1, Integer::sum);
                    } else if (v == Verdict.BYPASS) {
                        bypass++;
                        roleBypass.merge(roleId, 1, Integer::sum);
                    } else {
                        indeterminable++;
                        roleIndeterminable.merge(roleId, 1, Integer::sum);
                    }
                    roleTested.merge(roleId, 1, Integer::sum);

                    // AI Result
                    if (res.getAiResult() != null && res.getAiResult().getVerdict() != null) {
                        AiVerdict aiv = res.getAiResult().getVerdict();
                        if (aiv == AiVerdict.VALIDATED_ACCESS) {
                            aiValidated++;
                            roleAiVal.merge(roleId, 1, Integer::sum);
                        } else if (aiv == AiVerdict.ACCESS_DENIED) {
                            aiDenied++;
                        } else if (aiv == AiVerdict.INCONCLUSIVE) {
                            aiInconclusive++;
                        } else if (aiv == AiVerdict.ERROR) {
                            aiError++;
                        } else if (aiv == AiVerdict.PENDING) {
                            aiPending++;
                        }
                    } else {
                        aiUntested++;
                    }
                }
            }

            // 2. Unauthenticated Result
            AuthorizationCheckResult unauth = rec.getUnauthenticatedResult();
            if (unauth != null) {
                totalReplays++;
                Verdict v = unauth.getVerdict();
                if (v == Verdict.ENFORCED) {
                    enforced++;
                    roleEnforced.merge("unauthenticated", 1, Integer::sum);
                } else if (v == Verdict.BYPASS) {
                    bypass++;
                    roleBypass.merge("unauthenticated", 1, Integer::sum);
                } else {
                    indeterminable++;
                    roleIndeterminable.merge("unauthenticated", 1, Integer::sum);
                }
                roleTested.merge("unauthenticated", 1, Integer::sum);

                if (unauth.getAiResult() != null && unauth.getAiResult().getVerdict() != null) {
                    AiVerdict aiv = unauth.getAiResult().getVerdict();
                    if (aiv == AiVerdict.VALIDATED_ACCESS) {
                        aiValidated++;
                        roleAiVal.merge("unauthenticated", 1, Integer::sum);
                    } else if (aiv == AiVerdict.ACCESS_DENIED) {
                        aiDenied++;
                    } else if (aiv == AiVerdict.INCONCLUSIVE) {
                        aiInconclusive++;
                    } else if (aiv == AiVerdict.ERROR) {
                        aiError++;
                    } else if (aiv == AiVerdict.PENDING) {
                        aiPending++;
                    }
                } else {
                    aiUntested++;
                }
            }
        }

        double postureScore = (enforced + bypass > 0)
                ? ((double) enforced / (enforced + bypass)) * 100.0
                : 100.0;

        Map<String, RoleStatistics> statsMap = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : roleNames.entrySet()) {
            String roleId = entry.getKey();
            String roleName = entry.getValue();
            int tested = roleTested.getOrDefault(roleId, 0);
            if (tested > 0 || (roles != null && roles.stream().anyMatch(r -> r != null && roleId.equals(r.getId())))) {
                statsMap.put(roleId, new RoleStatistics(
                        roleId,
                        roleName,
                        tested,
                        roleEnforced.getOrDefault(roleId, 0),
                        roleBypass.getOrDefault(roleId, 0),
                        roleIndeterminable.getOrDefault(roleId, 0),
                        roleAiVal.getOrDefault(roleId, 0)
                ));
            }
        }

        return new ReportMetrics(
                totalRequests, totalReplays, enforced, bypass, indeterminable,
                aiValidated, aiDenied, aiInconclusive, aiError, aiPending, aiUntested,
                Math.round(postureScore * 10.0) / 10.0,
                statsMap
        );
    }
}
