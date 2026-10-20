package thorization.ui;

import thorization.ai.AiConfig;
import thorization.ai.OpenAiClient;
import thorization.core.EngineController;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

/**
 * Settings panel for configuring OpenAI-compatible AI Semantic Analysis endpoints,
 * provider presets, authentication, timeouts, temperature, and background testing.
 */
public class AiSettingsPanel extends JPanel {

    private final EngineController controller;

    private JComboBox<String> presetCombo;
    private JTextField endpointUrlField;
    private JPasswordField apiKeyField;
    private JCheckBox showApiKeyCheck;
    private JTextField modelNameField;
    private JTextArea customHeadersArea;
    private JSpinner timeoutSpinner;
    private JSpinner temperatureSpinner;
    private JCheckBox autoAnalyzeCheckBox;
    private JLabel cacheStatsLabel;
    private JButton clearCacheBtn;
    private JButton testConnectionBtn;
    private JButton saveBtn;
    private JLabel testStatusLabel;

    public AiSettingsPanel(EngineController controller) {
        this.controller = controller;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        initComponents();
        loadConfig();
    }

    private void initComponents() {
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        // 1. Header Card
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        JLabel titleLabel = new JLabel("AI Semantic Analysis Configuration");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        JLabel subtitleLabel = new JLabel("Configure OpenAI-compatible LLM endpoints (Ollama, DeepSeek, OpenAI, Qwen, Gemini) for access control validation.");
        subtitleLabel.setForeground(Color.GRAY);
        headerPanel.add(titleLabel, BorderLayout.NORTH);
        headerPanel.add(subtitleLabel, BorderLayout.SOUTH);
        contentPanel.add(headerPanel);
        contentPanel.add(Box.createVerticalStrut(16));

        // 2. Main Form Panel with GridBagLayout
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("Endpoint & Model Parameters"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;

        // Provider Preset Dropdown
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Provider Preset:"), gbc);
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1.0;
        presetCombo = new JComboBox<>(new String[]{
                "Local Ollama (http://localhost:11434/v1/chat/completions)",
                "OpenAI API (https://api.openai.com/v1/chat/completions)",
                "DeepSeek API (https://api.deepseek.com/chat/completions)",
                "Google Gemini (https://generativelanguage.googleapis.com/v1beta/openai/chat/completions)",
                "Alibaba Qwen / DashScope (https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions)",
                "OpenCode Go (https://opencode.ai/zen/go/v1/chat/completions)",
                "Custom OpenAI-Compatible Endpoint"
        });
        presetCombo.addActionListener(e -> onPresetChanged());
        formPanel.add(presetCombo, gbc);
        row++;

        // Endpoint URL
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Endpoint URL:"), gbc);
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1.0;
        endpointUrlField = new JTextField(AiConfig.DEFAULT_OLLAMA_ENDPOINT, 40);
        formPanel.add(endpointUrlField, gbc);
        row++;

        // API Key with Show/Hide checkbox
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("API Key (Bearer Token):"), gbc);
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1.0;
        JPanel keyPanel = new JPanel(new BorderLayout(8, 0));
        apiKeyField = new JPasswordField(30);
        showApiKeyCheck = new JCheckBox("Show");
        final char defaultEchoChar = apiKeyField.getEchoChar();
        showApiKeyCheck.addActionListener(e -> {
            apiKeyField.setEchoChar(showApiKeyCheck.isSelected() ? (char) 0 : defaultEchoChar);
        });
        keyPanel.add(apiKeyField, BorderLayout.CENTER);
        keyPanel.add(showApiKeyCheck, BorderLayout.EAST);
        formPanel.add(keyPanel, gbc);
        row++;

        // Model Name
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Model Name:"), gbc);
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1.0;
        modelNameField = new JTextField(AiConfig.DEFAULT_MODEL, 30);
        formPanel.add(modelNameField, gbc);
        row++;

        // Custom HTTP Headers (e.g. x-opencode-session, Anthropic-Version, etc.)
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        formPanel.add(new JLabel("Custom Headers:"), gbc);
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        customHeadersArea = new JTextArea(3, 40);
        customHeadersArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        customHeadersArea.setToolTipText("Enter custom HTTP headers, one per line (e.g. x-opencode-session: session_id, Anthropic-Version: 2023-06-01)");
        JScrollPane headersScroll = new JScrollPane(customHeadersArea);
        headersScroll.setPreferredSize(new Dimension(450, 70));
        JPanel headersContainer = new JPanel(new BorderLayout(0, 4));
        headersContainer.add(headersScroll, BorderLayout.CENTER);
        JLabel headersHint = new JLabel("Optional. One header per line (e.g. 'x-opencode-session: my-session-id' for OpenCode Go, or custom auth/routing headers).");
        headersHint.setFont(headersHint.getFont().deriveFont(Font.PLAIN, 11f));
        headersHint.setForeground(Color.GRAY);
        headersContainer.add(headersHint, BorderLayout.SOUTH);
        formPanel.add(headersContainer, gbc);
        row++;

        // Timeout (seconds)
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Timeout (seconds):"), gbc);
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1.0;
        timeoutSpinner = new JSpinner(new SpinnerNumberModel(AiConfig.DEFAULT_TIMEOUT_SECONDS, 1, 600, 1));
        JPanel timeoutPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        timeoutPanel.add(timeoutSpinner);
        formPanel.add(timeoutPanel, gbc);
        row++;

        // Temperature
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Temperature:"), gbc);
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1.0;
        temperatureSpinner = new JSpinner(new SpinnerNumberModel(AiConfig.DEFAULT_TEMPERATURE, 0.0, 2.0, 0.05));
        JPanel tempPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tempPanel.add(temperatureSpinner);
        formPanel.add(tempPanel, gbc);
        row++;

        // Auto-analyze toggle
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Automation:"), gbc);
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1.0;
        autoAnalyzeCheckBox = new JCheckBox("Automatically trigger AI Semantic Analysis on intercepted requests");
        formPanel.add(autoAnalyzeCheckBox, gbc);
        row++;

        contentPanel.add(formPanel);
        contentPanel.add(Box.createVerticalStrut(14));

        // 3. Cache Management Panel
        JPanel cachePanel = new JPanel(new BorderLayout(10, 0));
        cachePanel.setBorder(BorderFactory.createTitledBorder("SHA-256 Evaluation Cache"));
        cacheStatsLabel = new JLabel("Cached Evaluations: 0 entries");
        clearCacheBtn = new JButton("Clear Cache");
        clearCacheBtn.addActionListener(e -> {
            if (controller != null && controller.getAiSemanticEngine() != null) {
                controller.getAiSemanticEngine().getCache().clear();
                updateCacheStats();
            }
        });
        JPanel cacheLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        cacheLeft.add(cacheStatsLabel);
        JPanel cacheRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        cacheRight.add(clearCacheBtn);
        cachePanel.add(cacheLeft, BorderLayout.WEST);
        cachePanel.add(cacheRight, BorderLayout.EAST);
        contentPanel.add(cachePanel);
        contentPanel.add(Box.createVerticalStrut(14));

        // 4. Action Bar (Test Connection, Save, Status)
        JPanel actionPanel = new JPanel(new BorderLayout(10, 0));
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        testConnectionBtn = new JButton("Test Connection");
        testConnectionBtn.addActionListener(e -> onTestConnection());
        saveBtn = new JButton("Save Settings");
        saveBtn.addActionListener(e -> saveConfig());
        btnPanel.add(testConnectionBtn);
        btnPanel.add(saveBtn);

        testStatusLabel = new JLabel("Ready.");
        testStatusLabel.setFont(testStatusLabel.getFont().deriveFont(Font.PLAIN, 12f));

        actionPanel.add(btnPanel, BorderLayout.WEST);
        actionPanel.add(testStatusLabel, BorderLayout.CENTER);
        contentPanel.add(actionPanel);

        add(new JScrollPane(contentPanel), BorderLayout.CENTER);
    }

    private void onPresetChanged() {
        int idx = presetCombo.getSelectedIndex();
        switch (idx) {
            case 0 -> { // Ollama
                endpointUrlField.setText(AiConfig.DEFAULT_OLLAMA_ENDPOINT);
                modelNameField.setText("llama3");
                timeoutSpinner.setValue(60);
            }
            case 1 -> { // OpenAI
                endpointUrlField.setText(AiConfig.DEFAULT_OPENAI_ENDPOINT);
                modelNameField.setText("gpt-4o-mini");
                timeoutSpinner.setValue(30);
            }
            case 2 -> { // DeepSeek
                endpointUrlField.setText(AiConfig.DEFAULT_DEEPSEEK_ENDPOINT);
                modelNameField.setText("deepseek-chat");
                timeoutSpinner.setValue(45);
            }
            case 3 -> { // Gemini
                endpointUrlField.setText(AiConfig.DEFAULT_GEMINI_ENDPOINT);
                modelNameField.setText("gemini-1.5-flash");
                timeoutSpinner.setValue(30);
            }
            case 4 -> { // Qwen
                endpointUrlField.setText(AiConfig.DEFAULT_QWEN_ENDPOINT);
                modelNameField.setText("qwen-plus");
                timeoutSpinner.setValue(45);
            }
            case 5 -> { // OpenCode Go
                endpointUrlField.setText(AiConfig.DEFAULT_OPENCODE_ENDPOINT);
                modelNameField.setText(AiConfig.DEFAULT_OPENCODE_MODEL);
                timeoutSpinner.setValue(45);
                customHeadersArea.setText("x-opencode-session: thorization-" + java.util.UUID.randomUUID().toString().substring(0, 8));
            }
            default -> {} // Custom
        }
    }

    private void onTestConnection() {
        testConnectionBtn.setEnabled(false);
        testStatusLabel.setText("● Testing connection to endpoint...");
        testStatusLabel.setForeground(new Color(0, 123, 255));

        AiConfig testConfig = buildConfigFromUi();

        Thread.startVirtualThread(() -> {
            try {
                OpenAiClient client = controller != null && controller.getAiSemanticEngine() != null
                        ? controller.getAiSemanticEngine().getClient()
                        : new OpenAiClient();

                OpenAiClient.ConnectionTestResult res = client.testConnectionDetailed(testConfig);
                SwingUtilities.invokeLater(() -> {
                    testConnectionBtn.setEnabled(true);
                    if (res.success()) {
                        testStatusLabel.setText(String.format("✓ Success (%d ms): %s", res.latencyMs(), res.message()));
                        testStatusLabel.setForeground(new Color(40, 167, 69));
                    } else {
                        testStatusLabel.setText(String.format("✗ Failed (%d ms): %s", res.latencyMs(), res.message()));
                        testStatusLabel.setForeground(new Color(220, 53, 69));
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    testConnectionBtn.setEnabled(true);
                    testStatusLabel.setText("✗ Error: " + ex.getMessage());
                    testStatusLabel.setForeground(new Color(220, 53, 69));
                });
            }
        });
    }

    public void saveConfig() {
        AiConfig newConfig = buildConfigFromUi();
        List<String> errors = newConfig.validate();
        if (!errors.isEmpty()) {
            testStatusLabel.setText("✗ Validation Error: " + String.join("; ", errors));
            testStatusLabel.setForeground(new Color(220, 53, 69));
            return;
        }

        if (controller != null) {
            controller.setAiConfig(newConfig);
        }
        testStatusLabel.setText("✓ Settings saved successfully.");
        testStatusLabel.setForeground(new Color(40, 167, 69));
        updateCacheStats();
    }

    public void loadConfig() {
        AiConfig cfg = controller != null ? controller.getAiConfig() : new AiConfig();
        if (cfg == null) {
            cfg = new AiConfig();
        }

        endpointUrlField.setText(cfg.getEndpointUrl());
        apiKeyField.setText(cfg.getApiKey());
        modelNameField.setText(cfg.getModelName());
        customHeadersArea.setText(cfg.getRawHeaders() != null ? cfg.getRawHeaders() : "");
        timeoutSpinner.setValue(cfg.getTimeoutSeconds());
        temperatureSpinner.setValue(cfg.getTemperature());
        autoAnalyzeCheckBox.setSelected(cfg.isAutoAnalyzeEnabled());

        updateCacheStats();
    }

    private AiConfig buildConfigFromUi() {
        AiConfig cfg = new AiConfig();
        cfg.setEndpointUrl(endpointUrlField.getText() != null ? endpointUrlField.getText().trim() : "");
        cfg.setApiKey(apiKeyField.getPassword() != null ? new String(apiKeyField.getPassword()).trim() : "");
        cfg.setModelName(modelNameField.getText() != null ? modelNameField.getText().trim() : "");
        cfg.setRawHeaders(customHeadersArea.getText() != null ? customHeadersArea.getText().trim() : "");
        cfg.setTimeoutSeconds(((Number) timeoutSpinner.getValue()).intValue());
        cfg.setTemperature(((Number) temperatureSpinner.getValue()).doubleValue());
        cfg.setAutoAnalyzeEnabled(autoAnalyzeCheckBox.isSelected());
        return cfg;
    }

    public void updateCacheStats() {
        if (controller != null && controller.getAiSemanticEngine() != null && controller.getAiSemanticEngine().getCache() != null) {
            long size = controller.getAiSemanticEngine().getCache().size();
            cacheStatsLabel.setText(String.format("Cached Evaluations: %d entries", size));
        } else {
            cacheStatsLabel.setText("Cached Evaluations: 0 entries");
        }
    }

    // Getters for Headless Testing
    public JTextField getEndpointUrlField() { return endpointUrlField; }
    public JPasswordField getApiKeyField() { return apiKeyField; }
    public JTextField getModelNameField() { return modelNameField; }
    public JTextArea getCustomHeadersArea() { return customHeadersArea; }
    public JSpinner getTimeoutSpinner() { return timeoutSpinner; }
    public JSpinner getTemperatureSpinner() { return temperatureSpinner; }
    public JCheckBox getAutoAnalyzeCheckBox() { return autoAnalyzeCheckBox; }
    public JButton getTestConnectionBtn() { return testConnectionBtn; }
    public JButton getSaveBtn() { return saveBtn; }
    public JButton getClearCacheBtn() { return clearCacheBtn; }
    public JLabel getTestStatusLabel() { return testStatusLabel; }
    public JLabel getCacheStatsLabel() { return cacheStatsLabel; }
    public JComboBox<String> getPresetCombo() { return presetCombo; }
}
