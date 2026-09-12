package thorization.ui.components;

import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.OriginalAction;
import thorization.model.UserRole;
import thorization.model.Verdict;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic Swing TableModel reflecting intercepted HTTP transactions, traffic source (Proxy/Repeater),
 * drop actions (USP 1), and multi-role authorization verdicts.
 */
public class AuditTableModel extends AbstractTableModel {

    private final List<AuditRecord> records = new ArrayList<>();
    private final List<UserRole> roles = new ArrayList<>();

    private static final String[] BASE_COLUMNS = {
            "#", "Time", "Source", "Action", "Method", "URL", "Orig Status", "Orig Length"
    };

    public void setRoles(List<UserRole> newRoles) {
        this.roles.clear();
        if (newRoles != null) {
            this.roles.addAll(newRoles);
        }
        fireTableStructureChanged();
    }

    public void addRecord(AuditRecord record) {
        if (record != null) {
            this.records.add(record);
            int row = records.size() - 1;
            fireTableRowsInserted(row, row);
        }
    }

    public void updateRecord(AuditRecord record) {
        int index = records.indexOf(record);
        if (index >= 0) {
            fireTableRowsUpdated(index, index);
        } else {
            // Check by ID
            for (int i = 0; i < records.size(); i++) {
                if (records.get(i).getId() == record.getId()) {
                    records.set(i, record);
                    fireTableRowsUpdated(i, i);
                    return;
                }
            }
        }
    }

    public void clear() {
        this.records.clear();
        fireTableDataChanged();
    }

    public void removeRecords(List<AuditRecord> toRemove) {
        if (toRemove != null && !toRemove.isEmpty()) {
            this.records.removeAll(toRemove);
            fireTableDataChanged();
        }
    }

    public AuditRecord getRecordAt(int row) {
        if (row >= 0 && row < records.size()) {
            return records.get(row);
        }
        return null;
    }

    @Override
    public int getRowCount() {
        return records.size();
    }

    @Override
    public int getColumnCount() {
        // Base columns (8) + 1 per Role + 1 for Unauthenticated
        return BASE_COLUMNS.length + roles.size() + 1;
    }

    @Override
    public String getColumnName(int column) {
        if (column < BASE_COLUMNS.length) {
            return BASE_COLUMNS[column];
        }
        int roleIndex = column - BASE_COLUMNS.length;
        if (roleIndex < roles.size()) {
            return roles.get(roleIndex).getName();
        }
        return "Unauthenticated";
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= records.size()) {
            return null;
        }

        AuditRecord record = records.get(rowIndex);

        switch (columnIndex) {
            case 0:
                return record.getId();
            case 1:
                return record.getTimestamp();
            case 2:
                return record.getSourceTool();
            case 3:
                return record.getOriginalAction();
            case 4:
                return record.getMethod();
            case 5:
                return record.getUrl();
            case 6:
                return record.getOriginalStatusCode();
            case 7:
                return record.getOriginalResponseLength();
            default:
                int roleIndex = columnIndex - BASE_COLUMNS.length;
                if (roleIndex < roles.size()) {
                    UserRole role = roles.get(roleIndex);
                    AuthorizationCheckResult res = record.getRoleResults().get(role.getId());
                    return res != null ? res.getVerdict() : "Pending...";
                } else {
                    AuthorizationCheckResult unauth = record.getUnauthenticatedResult();
                    return unauth != null ? unauth.getVerdict() : "Pending...";
                }
        }
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        if (columnIndex == 0) return Long.class;
        if (columnIndex == 3) return OriginalAction.class;
        if (columnIndex == 6 || columnIndex == 7) return Integer.class;
        if (columnIndex >= BASE_COLUMNS.length) return Object.class;
        return String.class;
    }
}
