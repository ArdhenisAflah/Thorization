package thorization.ui.components;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.UserRole;
import thorization.model.Verdict;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tabbed container holding native Burp Request/Response editors for:
 * 1. Original traffic
 * 2. Dedicated individual tab for EACH configured Role (N users)
 * 3. Unauthenticated replay test
 * 4. Comprehensive audit notes and diff breakdown
 */
public class RequestResponseViewerPanel extends JPanel {

    private final MontoyaApi api;
    private final JTabbedPane tabbedPane;

    // Original Editors
    private JPanel originalTabPanel;
    private HttpRequestEditor originalRequestEditor;
    private HttpResponseEditor originalResponseEditor;

    // Dynamic Role Tabs: Map<RoleId, RoleViewerTab>
    private final Map<String, RoleViewerTab> roleTabs = new LinkedHashMap<>();

    // Unauthenticated Editors
    private JPanel unauthTabPanel;
    private HttpRequestEditor unauthRequestEditor;
    private HttpResponseEditor unauthResponseEditor;
    private JLabel unauthStatusLabel;

    // Notes / Details Area
    private JPanel notesTabPanel;
    private JTextArea notesArea;

    private AuditRecord currentRecord;

    public RequestResponseViewerPanel(MontoyaApi api) {
        this.api = api;
        setLayout(new BorderLayout());

        this.tabbedPane = new JTabbedPane();
        this.tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

        // Enable mouse wheel scrolling across tabs
        this.tabbedPane.addMouseWheelListener(e -> {
            int selected = tabbedPane.getSelectedIndex();
            int count = tabbedPane.getTabCount();
            if (count > 0) {
                int rotation = e.getWheelRotation();
                int nextIndex = selected + rotation;
                if (nextIndex >= 0 && nextIndex < count) {
                    tabbedPane.setSelectedIndex(nextIndex);
                }
            }
        });

        initOriginalTab();
        initUnauthenticatedTab();
        initNotesAndDiffTab();

        rebuildTabs(new ArrayList<>());

        add(tabbedPane, BorderLayout.CENTER);
    }

    private void initOriginalTab() {
        originalTabPanel = new JPanel(new BorderLayout());
        if (api != null && api.userInterface() != null) {
            originalRequestEditor = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY);
            originalResponseEditor = api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    originalRequestEditor.uiComponent(),
                    originalResponseEditor.uiComponent());
            split.setResizeWeight(0.5);

            originalTabPanel.add(split, BorderLayout.CENTER);
        } else {
            originalTabPanel.add(new JLabel(" Original Editors available inside Burp Suite runtime."), BorderLayout.CENTER);
        }
    }

    private void initUnauthenticatedTab() {
        unauthTabPanel = new JPanel(new BorderLayout());
        unauthStatusLabel = new JLabel(" Unauthenticated authorization test result.");
        unauthStatusLabel.setFont(unauthStatusLabel.getFont().deriveFont(Font.BOLD, 12f));
        unauthStatusLabel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        unauthTabPanel.add(unauthStatusLabel, BorderLayout.NORTH);

        if (api != null && api.userInterface() != null) {
            unauthRequestEditor = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY);
            unauthResponseEditor = api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    unauthRequestEditor.uiComponent(),
                    unauthResponseEditor.uiComponent());
            split.setResizeWeight(0.5);

            unauthTabPanel.add(split, BorderLayout.CENTER);
        } else {
            unauthTabPanel.add(new JLabel(" Unauthenticated Editors available inside Burp Suite runtime."), BorderLayout.CENTER);
        }
    }

    private void initNotesAndDiffTab() {
        notesTabPanel = new JPanel(new BorderLayout());
        notesArea = new JTextArea();
        notesArea.setEditable(false);
        notesArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        notesArea.setText("Select an entry in the table above to view audit analysis details and verdict explanations.");

        JScrollPane scrollPane = new JScrollPane(notesArea);
        notesTabPanel.add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Dynamically synchronizes tabs so each configured user role gets its own dedicated tab.
     */
    public synchronized void syncRoles(List<UserRole> roles) {
        rebuildTabs(roles);
        if (currentRecord != null) {
            displayRecord(currentRecord, roles);
        } else {
            clearViewers();
        }
    }

    private void rebuildTabs(List<UserRole> roles) {
        int previousSelectedIndex = tabbedPane.getSelectedIndex();
        String selectedTitle = previousSelectedIndex >= 0 && previousSelectedIndex < tabbedPane.getTabCount()
                ? tabbedPane.getTitleAt(previousSelectedIndex) : null;

        tabbedPane.removeAll();

        // 1. Tab 0: Original Request & Response
        tabbedPane.addTab("Original Request & Response", originalTabPanel);

        // 2. Tab 1 .. N: Dedicated tab for EACH role
        if (roles != null) {
            for (UserRole role : roles) {
                RoleViewerTab viewerTab = roleTabs.computeIfAbsent(role.getId(), id -> new RoleViewerTab(api, role.getId(), role.getName()));
                viewerTab.updateRoleName(role.getName());
                tabbedPane.addTab("Role: " + role.getName(), viewerTab.getContainerPanel());
            }
        }

        // 3. Unauthenticated Replay
        tabbedPane.addTab("Unauthenticated Replay", unauthTabPanel);

        // 4. Audit Details & Notes
        tabbedPane.addTab("Audit Details & Notes", notesTabPanel);

        // Restore selected tab if possible
        if (selectedTitle != null) {
            for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                if (tabbedPane.getTitleAt(i).equals(selectedTitle)) {
                    tabbedPane.setSelectedIndex(i);
                    return;
                }
            }
        }
        if (tabbedPane.getTabCount() > 0) {
            tabbedPane.setSelectedIndex(0);
        }
    }

    /**
     * Clears all request/response content and resets labels when table is cleared or no row is selected.
     */
    public synchronized void clearViewers() {
        this.currentRecord = null;

        // 1. Clear Original
        if (originalRequestEditor != null) {
            try {
                originalRequestEditor.setRequest(HttpRequest.httpRequest(""));
            } catch (Exception ignored) {}
        }
        if (originalResponseEditor != null) {
            try {
                originalResponseEditor.setResponse(HttpResponse.httpResponse(""));
            } catch (Exception ignored) {}
        }

        // 2. Clear all Role tabs
        for (RoleViewerTab tab : roleTabs.values()) {
            tab.clear();
        }

        // 3. Clear Unauthenticated
        if (unauthRequestEditor != null) {
            try {
                unauthRequestEditor.setRequest(HttpRequest.httpRequest(""));
            } catch (Exception ignored) {}
        }
        if (unauthResponseEditor != null) {
            try {
                unauthResponseEditor.setResponse(HttpResponse.httpResponse(""));
            } catch (Exception ignored) {}
        }
        if (unauthStatusLabel != null) {
            unauthStatusLabel.setText(" Unauthenticated authorization test result.");
            unauthStatusLabel.setForeground(Color.GRAY);
        }

        // 4. Reset Notes Area
        if (notesArea != null) {
            notesArea.setText("Select an entry in the table above to view audit analysis details and verdict explanations.");
        }
    }

    /**
     * Populates all role tabs and editors with the data from the selected record.
     */
    public synchronized void displayRecord(AuditRecord record, List<UserRole> roles) {
        this.currentRecord = record;
        if (record == null) {
            clearViewers();
            return;
        }

        // 1. Display Original
        if (originalRequestEditor != null && record.getOriginalRequest() != null) {
            originalRequestEditor.setRequest(record.getOriginalRequest());
        }
        if (originalResponseEditor != null && record.getOriginalResponse() != null) {
            originalResponseEditor.setResponse(record.getOriginalResponse());
        }

        // 2. Display each Role in its dedicated tab
        if (roles != null) {
            for (UserRole role : roles) {
                RoleViewerTab tab = roleTabs.get(role.getId());
                if (tab != null) {
                    AuthorizationCheckResult res = record.getRoleResults().get(role.getId());
                    tab.displayResult(res);
                }
            }
        }

        // 3. Display Unauthenticated Replay
        AuthorizationCheckResult unauth = record.getUnauthenticatedResult();
        if (unauth != null) {
            if (unauthRequestEditor != null && unauth.getRequest() != null) {
                unauthRequestEditor.setRequest(unauth.getRequest());
            }
            if (unauthResponseEditor != null && unauth.getResponse() != null) {
                unauthResponseEditor.setResponse(unauth.getResponse());
            }
            Verdict v = unauth.getVerdict();
            unauthStatusLabel.setText(String.format(" Unauthenticated Verdict: %s | Status: %d | Len: %d bytes | %s",
                    v != null ? v.getDisplayName() : "-",
                    unauth.getStatusCode(),
                    unauth.getResponseLength(),
                    unauth.getNotes()));
            if (v != null) {
                unauthStatusLabel.setForeground(v.getForegroundColor());
            }
        } else {
            unauthStatusLabel.setText(" Unauthenticated Replay: Not tested / Disabled");
            unauthStatusLabel.setForeground(Color.GRAY);
        }

        // 4. Build comprehensive Audit Notes
        if (record.isPipelineExecution() && record.getPipelineTrace() != null && !record.getPipelineTrace().isEmpty()) {
            if (notesArea != null) {
                notesArea.setText(record.getPipelineTrace());
                notesArea.setCaretPosition(0);
            }
            return;
        }

        StringBuilder notesBuilder = new StringBuilder();
        notesBuilder.append("=================================================================\n");
        notesBuilder.append(String.format("  THORIZATION AUDIT RECORD #%d\n", record.getId()));
        notesBuilder.append("=================================================================\n");
        notesBuilder.append("Timestamp: ").append(record.getTimestamp()).append("\n");
        notesBuilder.append("Source:    ").append(record.getSourceTool() != null ? record.getSourceTool() : "Proxy").append("\n");
        notesBuilder.append("Method:    ").append(record.getMethod()).append("\n");
        notesBuilder.append("URL:       ").append(record.getUrl()).append("\n");
        notesBuilder.append("Action:    ").append(record.getOriginalAction() != null ? record.getOriginalAction().getLabel() : "PASSED").append("\n");
        notesBuilder.append("Original:  Status ").append(record.getOriginalStatusCode())
                .append(" | Length: ").append(record.getOriginalResponseLength()).append(" bytes\n\n");

        notesBuilder.append("--- ROLE VERDICT SUMMARY ---\n");
        if (roles != null) {
            for (UserRole role : roles) {
                AuthorizationCheckResult res = record.getRoleResults().get(role.getId());
                if (res != null) {
                    notesBuilder.append(String.format("• [%s] %s:\n", res.getVerdict().name(), role.getName()));
                    notesBuilder.append(String.format("    - Status: %d | Length: %d bytes (diff from orig)\n",
                            res.getStatusCode(), res.getResponseLength()));
                    notesBuilder.append(String.format("    - Body Similarity: %.2f | Elapsed: %d ms\n",
                            res.getSimilarityScore(), res.getExecutionTimeMs()));
                    notesBuilder.append(String.format("    - Evaluation Notes: %s\n", res.getNotes()));
                } else {
                    notesBuilder.append(String.format("• [PENDING/SKIPPED] %s: Not executed\n", role.getName()));
                }
            }
        }

        if (unauth != null) {
            notesBuilder.append(String.format("\n• [%s] Unauthenticated:\n", unauth.getVerdict().name()));
            notesBuilder.append(String.format("    - Status: %d | Length: %d bytes\n", unauth.getStatusCode(), unauth.getResponseLength()));
            notesBuilder.append(String.format("    - Evaluation Notes: %s\n", unauth.getNotes()));
        }

        if (notesArea != null) {
            notesArea.setText(notesBuilder.toString());
            notesArea.setCaretPosition(0);
        }
    }

    /**
     * Encapsulates the UI components for a single user role's dedicated tab.
     */
    public static class RoleViewerTab {
        private final String roleId;
        private String roleName;
        private final JPanel containerPanel;
        private final HttpRequestEditor requestEditor;
        private final HttpResponseEditor responseEditor;
        private final JLabel statusLabel;

        public RoleViewerTab(MontoyaApi api, String roleId, String roleName) {
            this.roleId = roleId;
            this.roleName = roleName;
            this.containerPanel = new JPanel(new BorderLayout());

            this.statusLabel = new JLabel(" Select an audit record to view replayed traffic for " + roleName);
            this.statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 12f));
            this.statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

            if (api != null && api.userInterface() != null) {
                this.requestEditor = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY);
                this.responseEditor = api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);

                JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                        requestEditor.uiComponent(),
                        responseEditor.uiComponent());
                split.setResizeWeight(0.5);

                containerPanel.add(statusLabel, BorderLayout.NORTH);
                containerPanel.add(split, BorderLayout.CENTER);
            } else {
                this.requestEditor = null;
                this.responseEditor = null;
                containerPanel.add(statusLabel, BorderLayout.NORTH);
                containerPanel.add(new JLabel(" Role Editors available inside Burp Suite runtime."), BorderLayout.CENTER);
            }
        }

        public void updateRoleName(String newName) {
            this.roleName = newName;
        }

        public void clear() {
            if (requestEditor != null) {
                try {
                    requestEditor.setRequest(HttpRequest.httpRequest(""));
                } catch (Exception ignored) {}
            }
            if (responseEditor != null) {
                try {
                    responseEditor.setResponse(HttpResponse.httpResponse(""));
                } catch (Exception ignored) {}
            }
            if (statusLabel != null) {
                statusLabel.setText(" Select an audit record to view replayed traffic for " + roleName);
                statusLabel.setForeground(Color.GRAY);
            }
        }

        public void displayResult(AuthorizationCheckResult result) {
            if (result != null) {
                if (requestEditor != null && result.getRequest() != null) {
                    requestEditor.setRequest(result.getRequest());
                }
                if (responseEditor != null && result.getResponse() != null) {
                    responseEditor.setResponse(result.getResponse());
                }
                Verdict v = result.getVerdict();
                statusLabel.setText(String.format(" Role: %s | Verdict: %s | Status: %d | Len: %d bytes | Sim: %.2f | Time: %d ms",
                        roleName,
                        v != null ? v.getDisplayName() : "-",
                        result.getStatusCode(),
                        result.getResponseLength(),
                        result.getSimilarityScore(),
                        result.getExecutionTimeMs()));
                if (v != null) {
                    statusLabel.setForeground(v.getForegroundColor());
                } else {
                    statusLabel.setForeground(Color.BLACK);
                }
            } else {
                statusLabel.setText(" Role: " + roleName + " | Status: Not tested / Pending");
                statusLabel.setForeground(Color.GRAY);
            }
        }

        public JPanel getContainerPanel() {
            return containerPanel;
        }
    }
}
