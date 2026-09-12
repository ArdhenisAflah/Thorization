package thorization.core;

import thorization.model.AuditRecord;
import thorization.model.FilterConfig;
import thorization.model.UserRole;
import thorization.model.pipeline.Pipeline;

import java.util.List;

/**
 * Observer interface for listening to engine state changes, filter updates, pipeline workflows, and audit events.
 */
public interface EngineListener {
    void onAuditRecordAdded(AuditRecord record);
    void onAuditRecordUpdated(AuditRecord record);
    void onAuditRecordsRemoved(List<AuditRecord> removedRecords);
    void onAuditRecordsCleared();
    void onRolesChanged(List<UserRole> roles);
    void onEngineStateChanged(boolean enabled);
    void onFilterConfigChanged(FilterConfig config);
    void onPipelinesChanged(List<Pipeline> pipelines);
}
