package thorization.ui;

import burp.api.montoya.MontoyaApi;
import thorization.core.EngineController;
import thorization.core.EngineListener;
import thorization.model.AuditRecord;
import thorization.model.FilterConfig;
import thorization.model.UserRole;
import thorization.model.pipeline.ConditionBranch;
import thorization.model.pipeline.Pipeline;
import thorization.model.pipeline.PipelineStep;
import thorization.model.pipeline.RequestStep;
import thorization.ui.components.pipeline.BranchBlockCard;
import thorization.ui.components.pipeline.PipelineConsolePanel;
import thorization.ui.components.pipeline.RequestBlockCard;

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

        // --- Top Control Bar ---
        JPanel topToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        topToolbar.setBackground(new Color(245, 247, 250));
        topToolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)
        ));

        topToolbar.add(new JLabel("Active Pipeline:"));
        pipelineSelector = new JComboBox<>();
        pipelineSelector.setPreferredSize(new Dimension(220, 26));
        pipelineSelector.addActionListener(e -> {
            Pipeline selected = (Pipeline) pipelineSelector.getSelectedItem();
            if (selected != null) {
                loadPipeline(selected);
            }
        });
        topToolbar.add(pipelineSelector);

        JButton newPipelineBtn = new JButton("+ New Scenario");
        JButton savePipelineBtn = new JButton("💾 Save");
        JButton deletePipelineBtn = new JButton("🗑️ Delete");
        JButton runPipelineBtn = new JButton("▶ Run Pipeline");
        runPipelineBtn.setBackground(new Color(40, 167, 69));
        runPipelineBtn.setForeground(Color.WHITE);
        runPipelineBtn.setFont(runPipelineBtn.getFont().deriveFont(Font.BOLD, 12f));

        topToolbar.add(newPipelineBtn);
        topToolbar.add(savePipelineBtn);
        topToolbar.add(deletePipelineBtn);
        topToolbar.add(Box.createHorizontalStrut(10));
        topToolbar.add(runPipelineBtn);

        topToolbar.add(Box.createHorizontalStrut(15));
        topToolbar.add(new JSeparator(JSeparator.VERTICAL));
        topToolbar.add(Box.createHorizontalStrut(10));

        JButton addReqBtn = new JButton("🟦 + Add Request Step");
        JButton addBranchBtn = new JButton("🟨 + Add IF/ELSE Branch");

        topToolbar.add(addReqBtn);
        topToolbar.add(addBranchBtn);

        add(topToolbar, BorderLayout.NORTH);

        // --- Center: Scrollable Cards Container ---
        cardsContainer = new JPanel();
        cardsContainer.setLayout(new BoxLayout(cardsContainer, BoxLayout.Y_AXIS));
        cardsContainer.setBackground(Color.WHITE);

        JScrollPane cardsScroll = new JScrollPane(cardsContainer);
        cardsScroll.getVerticalScrollBar().setUnitIncrement(16);
        cardsScroll.setBorder(BorderFactory.createTitledBorder("Visual Step-by-Step Block Pipeline"));

        // --- Bottom: Pipeline Console Panel ---
        consolePanel = new PipelineConsolePanel();
        consolePanel.setPreferredSize(new Dimension(800, 220));

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, cardsScroll, consolePanel);
        mainSplit.setResizeWeight(0.65);
        add(mainSplit, BorderLayout.CENTER);

        // --- Actions ---
        newPipelineBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Enter New Pipeline Name:", "New Pipeline Scenario", JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                Pipeline p = new Pipeline(name.trim(), "Stateful test scenario");
                // Add default sample steps
                RequestStep step1 = new RequestStep("Step 1: Setup Instance (User A)", "POST", "https://example.com/api/resource", "");
                step1.setBodyTemplate("{\"name\": \"TestObject\", \"code\": \"{{UPK}}\"}");
                RequestStep step2 = new RequestStep("Step 2: Access Check (User B)", "GET", "https://example.com/api/resource/{{VAR_ID}}", "");
                p.addStep(step1);
                p.addStep(step2);

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
                RequestStep step = new RequestStep("New Request Step", "GET", "https://example.com/api/data", "");
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
        RequestStep s1 = new RequestStep("Step 1: Create Record (Doctor A)", "POST", "https://selaras.kemkes.go.id/selaras_api/mobile/save_individu", "");
        s1.setBodyTemplate("[\n  {\n    \"id_unique\": \"ID20260824125737\",\n    \"kode_fasyankes\": \"P3674020202\",\n    \"nik\": \"ardaeded\"\n  }\n]");

        ConditionBranch branch = new ConditionBranch(
                "Step 2: Check Creation Success",
                ConditionBranch.ConditionType.STATUS_CODE_EQUALS,
                "200",
                ConditionBranch.BranchAction.CONTINUE,
                ConditionBranch.BranchAction.STOP_PIPELINE
        );

        RequestStep s2 = new RequestStep("Step 3: Query Data (Attacker B)", "GET", "https://selaras.kemkes.go.id/selaras_api/mobile/list_biodata?upk={{UPK_CODE}}", "");

        sample.addStep(s1);
        sample.addStep(branch);
        sample.addStep(s2);

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
            if (r.getOriginalRequest() != null && r.getOriginalRequest().body() != null) {
                step.setBodyTemplate(r.getOriginalRequest().bodyToString());
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
                    cardsContainer.add(card);
                    cardsContainer.add(Box.createVerticalStrut(8));
                    stepCards.add(card);
                } else if (step instanceof ConditionBranch) {
                    BranchBlockCard card = new BranchBlockCard((ConditionBranch) step,
                            c -> moveStepUp(index),
                            c -> moveStepDown(index),
                            c -> removeStep(index)
                    );
                    cardsContainer.add(card);
                    cardsContainer.add(Box.createVerticalStrut(8));
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

