package thorization.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpHeader;
import thorization.core.EngineController;
import thorization.core.EngineListener;
import thorization.model.AuditRecord;
import thorization.model.FilterConfig;
import thorization.model.UserRole;
import thorization.model.pipeline.ConditionBranch;
import thorization.model.pipeline.ExtractionRule;
import thorization.model.pipeline.ExtractionSource;
import thorization.model.pipeline.ExtractionType;
import thorization.model.pipeline.Pipeline;
import thorization.model.pipeline.PipelineStep;
import thorization.model.pipeline.RequestStep;
import thorization.ui.components.pipeline.BranchBlockCard;
import thorization.ui.components.pipeline.PipelineConsolePanel;
import thorization.ui.components.pipeline.RequestBlockCard;
import thorization.util.UiUtils;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Tab 3: Visual Block-Based Pipeline Studio for designing, configuring,
 * and executing stateful multi-step BOLA authorization tests with variable chaining.
 */
public class PipelineStudioPanel extends JPanel implements EngineListener {

    private final MontoyaApi api;
    private final EngineController engineController;

    private final JComboBox<Pipeline> pipelineSelector;
    private final JPanel cardsContainer;
    private final PipelineConsolePanel consolePanel;
    private final List<Component> stepCards = new ArrayList<>();

    private Pipeline activePipeline;
    private List<UserRole> currentRoles = new ArrayList<>();

    public PipelineStudioPanel(MontoyaApi api, EngineController engineController) {
        this.api = api;
        this.engineController = engineController;
        this.currentRoles = new ArrayList<>(engineController.getRoles());

        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        // --- Top Container (Notice Banner + Toolbar) ---
        JPanel northContainer = new JPanel(new BorderLayout(0, 6));
        northContainer.setOpaque(false);

        // 1. Notice Banner: WIP / Future Development
        JPanel bannerPanel = new JPanel(new BorderLayout());
        Color bannerBg = UiUtils.isDarkMode() ? new Color(52, 42, 22) : new Color(255, 248, 225);
        Color bannerBorder = new Color(230, 160, 0);
        bannerPanel.setBackground(bannerBg);
        bannerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bannerBorder, 1, true),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));

        String bannerMsg = "[WIP / Future Development] Pipeline Studio automated execution is currently paused. " +
                "Development is focused on Core Interception, Safe Drop Replays, and AI MCP integration.";
        JLabel bannerText = new JLabel(bannerMsg);
        bannerText.setFont(bannerText.getFont().deriveFont(Font.PLAIN, 12f));
        Color bannerFg = UiUtils.isDarkMode() ? new Color(240, 200, 120) : new Color(133, 77, 0);
        bannerText.setForeground(bannerFg);
        bannerPanel.setToolTipText(bannerMsg);
        bannerText.setToolTipText(bannerMsg);

        bannerPanel.add(bannerText, BorderLayout.CENTER);
        northContainer.add(bannerPanel, BorderLayout.NORTH);

        // 2. Toolbar
        JPanel topToolbar = new JPanel(new GridBagLayout());
        topToolbar.setOpaque(false);
        topToolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground") != null ? UIManager.getColor("Separator.foreground") : new Color(60, 63, 65)),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));

        // Left Controls: Scenario management
        JPanel leftGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        leftGroup.setOpaque(false);

        leftGroup.add(new JLabel("Scenario:"));
        pipelineSelector = new JComboBox<>();
        pipelineSelector.setPreferredSize(new Dimension(170, 26));
        pipelineSelector.addActionListener(e -> {
            Pipeline selected = (Pipeline) pipelineSelector.getSelectedItem();
            if (selected != null) {
                loadPipeline(selected);
            }
        });
        leftGroup.add(pipelineSelector);

        JButton newPipelineBtn = new JButton("+ New");
        newPipelineBtn.setToolTipText("Create a new BOLA test scenario");
        JButton savePipelineBtn = new JButton("Save");
        savePipelineBtn.setToolTipText("Save current scenario changes");
        JButton deletePipelineBtn = new JButton("Delete");
        deletePipelineBtn.setToolTipText("Delete selected scenario");
        JButton runPipelineBtn = new JButton("▶ Run (Paused - WIP)");
        runPipelineBtn.setToolTipText("Pipeline Studio execution is currently paused for future development.");
        runPipelineBtn.setEnabled(false);
        runPipelineBtn.setBackground(new Color(108, 117, 125));
        runPipelineBtn.setForeground(new Color(220, 220, 220));
        runPipelineBtn.setFont(runPipelineBtn.getFont().deriveFont(Font.BOLD, 12f));

        leftGroup.add(newPipelineBtn);
        leftGroup.add(savePipelineBtn);
        leftGroup.add(deletePipelineBtn);
        leftGroup.add(runPipelineBtn);

        // Right Controls: Step additions
        JPanel rightGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightGroup.setOpaque(false);

        JButton addReqBtn = new JButton("+ HTTP Step");
        addReqBtn.setToolTipText("Add an HTTP request step with role credential binding");
        JButton addBranchBtn = new JButton("+ Condition");
        addBranchBtn.setToolTipText("Add an IF/ELSE validation branch or assertion rule");

        rightGroup.add(addReqBtn);
        rightGroup.add(addBranchBtn);

        GridBagConstraints tgbc = new GridBagConstraints();
        tgbc.gridx = 0; tgbc.gridy = 0; tgbc.weightx = 1.0;
        tgbc.anchor = GridBagConstraints.WEST;
        tgbc.fill = GridBagConstraints.NONE;
        topToolbar.add(leftGroup, tgbc);

        tgbc.gridx = 1; tgbc.weightx = 0.0;
        tgbc.anchor = GridBagConstraints.EAST;
        tgbc.fill = GridBagConstraints.NONE;
        topToolbar.add(rightGroup, tgbc);

        northContainer.add(topToolbar, BorderLayout.CENTER);
        add(northContainer, BorderLayout.NORTH);

        // --- Center: Scrollable Cards Container ---
        cardsContainer = new JPanel();
        cardsContainer.setLayout(new BoxLayout(cardsContainer, BoxLayout.Y_AXIS));
        cardsContainer.setOpaque(false);
        cardsContainer.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JScrollPane cardsScroll = new JScrollPane(cardsContainer);
        cardsScroll.getViewport().setOpaque(false);
        cardsScroll.setOpaque(false);
        cardsScroll.getVerticalScrollBar().setUnitIncrement(16);
        cardsScroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Step-by-Step Scenario Pipeline"),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));

        // --- Bottom: Pipeline Console Panel ---
        consolePanel = new PipelineConsolePanel();
        consolePanel.setPreferredSize(new Dimension(800, 200));

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, cardsScroll, consolePanel);
        mainSplit.setResizeWeight(0.65);
        add(mainSplit, BorderLayout.CENTER);

        // --- Actions ---
        newPipelineBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Enter New Pipeline Name:", "New Pipeline Scenario", JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                Pipeline p = new Pipeline(name.trim(), "Stateful test scenario");
                String role1 = !currentRoles.isEmpty() ? currentRoles.get(0).getId() : "UNAUTHENTICATED";
                String role2 = currentRoles.size() > 1 ? currentRoles.get(1).getId() : "UNAUTHENTICATED";

                // Step 1: Create/Setup Resource as Victim
                RequestStep step1 = new RequestStep("Step 1: Setup Resource (Victim)", "POST", "https://api.example.local/resource", role1);
                step1.setBodyTemplate("{\n  \"name\": \"TargetData\",\n  \"status\": \"private\"\n}");
                ExtractionRule ext = new ExtractionRule("RESOURCE_ID", ExtractionSource.RESPONSE_BODY, ExtractionType.JSON_PATH, "$.id");
                ext.setDefaultValue("REC-1001");
                step1.addExtraction(ext);

                // Step 2: Verify Setup Succeeded
                ConditionBranch branch1 = new ConditionBranch(
                        "Step 2: Check Setup Succeeded",
                        ConditionBranch.ConditionType.STATUS_CODE_EQUALS,
                        "200",
                        ConditionBranch.BranchAction.CONTINUE,
                        ConditionBranch.BranchAction.STOP_PIPELINE
                );
                branch1.setFailureNote("Resource setup step failed - pipeline halted");

                // Step 3: Access Resource as Attacker
                RequestStep step2 = new RequestStep("Step 3: Access Resource (Attacker)", "GET", "https://api.example.local/resource/{{RESOURCE_ID}}", role2);

                // Step 4: Assert BOLA Vulnerability
                ConditionBranch branch2 = new ConditionBranch(
                        "Step 4: Assert BOLA Vulnerability",
                        ConditionBranch.ConditionType.STATUS_CODE_EQUALS,
                        "200",
                        ConditionBranch.BranchAction.MARK_VERDICT_BYPASS,
                        ConditionBranch.BranchAction.MARK_VERDICT_ENFORCED
                );
                branch2.setFailureNote("Attacker was denied access (HTTP 401/403) - authorization enforced");

                p.addStep(step1);
                p.addStep(branch1);
                p.addStep(step2);
                p.addStep(branch2);

                engineController.addPipeline(p);
                loadPipeline(p);
            }
        });

        savePipelineBtn.addActionListener(e -> {
            saveCurrentPipelineFromUI();
            JOptionPane.showMessageDialog(this, "Pipeline \"" + activePipeline.getName() + "\" saved successfully!", "Saved", JOptionPane.INFORMATION_MESSAGE);
        });

        deletePipelineBtn.addActionListener(e -> {
            if (activePipeline != null) {
                int choice = JOptionPane.showConfirmDialog(this, "Delete pipeline \"" + activePipeline.getName() + "\"?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
                if (choice == JOptionPane.YES_OPTION) {
                    engineController.removePipeline(activePipeline.getId());
                }
            }
        });

        runPipelineBtn.addActionListener(e -> {
            saveCurrentPipelineFromUI();
            if (activePipeline != null) {
                consolePanel.setRunning(activePipeline.getName());
                engineController.executePipeline(activePipeline, consolePanel::displayResult);
            }
        });

        addReqBtn.addActionListener(e -> {
            if (activePipeline != null) {
                RequestStep step = new RequestStep("New Request Step", "GET", "", "");
                activePipeline.addStep(step);
                renderStepCards();
            }
        });

        addBranchBtn.addActionListener(e -> {
            if (activePipeline != null) {
                ConditionBranch branch = new ConditionBranch();
                activePipeline.addStep(branch);
                renderStepCards();
            }
        });

        // Initialize default pipeline if none exists
        if (engineController.getPipelines().isEmpty()) {
            createDefaultSamplePipeline();
        } else {
            refreshPipelinesCombo(engineController.getPipelines());
        }

        engineController.addListener(this);
    }

    private void createDefaultSamplePipeline() {
        Pipeline sample = new Pipeline("BOLA Cross-Role Data Verification", "Tests if User 2 can read records created by User 1");
        String role1 = !currentRoles.isEmpty() ? currentRoles.get(0).getId() : "UNAUTHENTICATED";
        String role2 = currentRoles.size() > 1 ? currentRoles.get(1).getId() : "UNAUTHENTICATED";

        RequestStep s1 = new RequestStep("Step 1: Setup Resource", "POST", "https://api.target.local/resource", role1);
        s1.setBodyTemplate("{\n  \"item_id\": \"ITEM_001\"\n}");
        ExtractionRule ext = new ExtractionRule("RESOURCE_ID", ExtractionSource.RESPONSE_BODY, ExtractionType.JSON_PATH, "$.item_id");
        ext.setDefaultValue("ITEM_001");
        s1.addExtraction(ext);

        ConditionBranch branch1 = new ConditionBranch(
                "Step 2: Verify Setup Response",
                ConditionBranch.ConditionType.STATUS_CODE_EQUALS,
                "200",
                ConditionBranch.BranchAction.CONTINUE,
                ConditionBranch.BranchAction.STOP_PIPELINE
        );
        branch1.setFailureNote("Resource setup step failed - aborting pipeline");

        RequestStep s2 = new RequestStep("Step 3: Replay with Attacker Context", "GET", "https://api.target.local/resource/{{RESOURCE_ID}}", role2);

        ConditionBranch branch2 = new ConditionBranch(
                "Step 4: Assert BOLA Vulnerability",
                ConditionBranch.ConditionType.STATUS_CODE_EQUALS,
                "200",
                ConditionBranch.BranchAction.MARK_VERDICT_BYPASS,
                ConditionBranch.BranchAction.MARK_VERDICT_ENFORCED
        );
        branch2.setFailureNote("Attacker query was blocked or returned non-200 (Protected)");

        sample.addStep(s1);
        sample.addStep(branch1);
        sample.addStep(s2);
        sample.addStep(branch2);

        engineController.addPipeline(sample);
        loadPipeline(sample);
    }

    public void loadPipeline(Pipeline p) {
        this.activePipeline = p;
        renderStepCards();
    }

    public void createPipelineFromRequests(List<AuditRecord> records) {
        if (records == null || records.isEmpty()) return;

        String name = "Pipeline from " + records.size() + " Requests";
        Pipeline pipeline = new Pipeline(name, "Auto-generated from analysis table");

        for (int i = 0; i < records.size(); i++) {
            AuditRecord r = records.get(i);
            RequestStep step = new RequestStep("Step " + (i + 1) + ": " + r.getMethod() + " " + r.getPath(),
                    r.getMethod(), r.getUrl(), "");
            if (r.getOriginalRequest() != null) {
                if (r.getOriginalRequest().body() != null && !r.getOriginalRequest().bodyToString().isEmpty()) {
                    step.setBodyTemplate(r.getOriginalRequest().bodyToString());
                }
                if (r.getOriginalRequest().headers() != null) {
                    StringBuilder hdrBuilder = new StringBuilder();
                    for (HttpHeader h : r.getOriginalRequest().headers()) {
                        if (!"Host".equalsIgnoreCase(h.name()) && !"Content-Length".equalsIgnoreCase(h.name())) {
                            hdrBuilder.append(h.name()).append(": ").append(h.value()).append("\n");
                        }
                    }
                    if (hdrBuilder.length() > 0) {
                        step.setHeadersTemplate(hdrBuilder.toString().trim());
                    }
                }
            }
            pipeline.addStep(step);
        }

        engineController.addPipeline(pipeline);
        loadPipeline(pipeline);
    }

    private void renderStepCards() {
        cardsContainer.removeAll();
        stepCards.clear();

        if (activePipeline != null) {
            List<PipelineStep> steps = activePipeline.getSteps();
            for (int i = 0; i < steps.size(); i++) {
                PipelineStep step = steps.get(i);
                final int index = i;

                if (step instanceof RequestStep) {
                    RequestBlockCard card = new RequestBlockCard((RequestStep) step, currentRoles,
                            c -> moveStepUp(index),
                            c -> moveStepDown(index),
                            c -> removeStep(index)
                    );
                    card.setAlignmentX(Component.CENTER_ALIGNMENT);
                    cardsContainer.add(card);
                    cardsContainer.add(Box.createVerticalStrut(10));
                    stepCards.add(card);
                } else if (step instanceof ConditionBranch) {
                    BranchBlockCard card = new BranchBlockCard((ConditionBranch) step,
                            c -> moveStepUp(index),
                            c -> moveStepDown(index),
                            c -> removeStep(index)
                    );
                    card.setAlignmentX(Component.CENTER_ALIGNMENT);
                    cardsContainer.add(card);
                    cardsContainer.add(Box.createVerticalStrut(10));
                    stepCards.add(card);
                }
            }
        }

        cardsContainer.revalidate();
        cardsContainer.repaint();
    }

    private void saveCurrentPipelineFromUI() {
        if (activePipeline == null) return;
        for (Component c : stepCards) {
            if (c instanceof RequestBlockCard) {
                ((RequestBlockCard) c).getStepData();
            } else if (c instanceof BranchBlockCard) {
                ((BranchBlockCard) c).getBranchData();
            }
        }
        engineController.updatePipeline(activePipeline);
    }

    private void moveStepUp(int index) {
        saveCurrentPipelineFromUI();
        activePipeline.moveStepUp(index);
        renderStepCards();
    }

    private void moveStepDown(int index) {
        saveCurrentPipelineFromUI();
        activePipeline.moveStepDown(index);
        renderStepCards();
    }

    private void removeStep(int index) {
        saveCurrentPipelineFromUI();
        activePipeline.removeStep(index);
        renderStepCards();
    }

    private void refreshPipelinesCombo(List<Pipeline> pipelines) {
        pipelineSelector.removeAllItems();
        if (pipelines != null) {
            for (Pipeline p : pipelines) {
                pipelineSelector.addItem(p);
            }
        }
        if (activePipeline != null) {
            pipelineSelector.setSelectedItem(activePipeline);
        }
    }

    // --- EngineListener Callbacks ---

    @Override
    public void onRolesChanged(List<UserRole> roles) {
        this.currentRoles = new ArrayList<>(roles);
        for (Component c : stepCards) {
            if (c instanceof RequestBlockCard) {
                ((RequestBlockCard) c).updateRolesList(roles);
            }
        }
    }

    @Override
    public void onPipelinesChanged(List<Pipeline> pipelines) {
        refreshPipelinesCombo(pipelines);
    }

    @Override public void onAuditRecordAdded(AuditRecord record) {}
    @Override public void onAuditRecordUpdated(AuditRecord record) {}
    @Override public void onAuditRecordsRemoved(List<AuditRecord> removedRecords) {}
    @Override public void onAuditRecordsCleared() {}
    @Override public void onEngineStateChanged(boolean enabled) {}
    @Override public void onFilterConfigChanged(FilterConfig config) {}
}

