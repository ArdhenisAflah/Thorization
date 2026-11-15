package thorization.ui;

import thorization.ai.AiConfig;
import thorization.ai.OpenAiClient;
import thorization.core.EngineController;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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
    private boolean updatingPreset;

    private static final String[] PROVIDER_PRESETS = {
            "Local Ollama", "OpenAI", "DeepSeek", "Google Gemini", "Alibaba Qwen", "OpenCode Go", "Custom endpoint"
    };

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
        contentPanel.setBorder(BorderFactory.createEmptyBorder(2, 4, 12, 4));

        // 1. Header Card
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        JLabel titleLabel = new JLabel("AI Settings");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        JLabel subtitleLabel = new JLabel("Choose a provider, configure access, and test the connection.");
        subtitleLabel.setForeground(Color.GRAY);
        headerPanel.add(titleLabel, BorderLayout.NORTH);
        headerPanel.add(subtitleLabel, BorderLayout.SOUTH);
        headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(headerPanel);
        contentPanel.add(Box.createVerticalStrut(16));

        // 2. Main Form Panel with GridBagLayout
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Model connection"),
                BorderFactory.createEmptyBorder(4, 8, 8, 8)));
        formPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;

        // Provider Preset Dropdown
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Provider:"), gbc);
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1.0;
        presetCombo = new JComboBox<>(PROVIDER_PRESETS);
        presetCombo.setPreferredSize(new Dimension(260, 30));
        presetCombo.setMaximumRowCount(PROVIDER_PRESETS.length);
        presetCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setBorder(new EmptyBorder(6, 9, 6, 9));
                return this;
            }
        });
        presetCombo.addActionListener(e -> onPresetChanged());
        formPanel.add(presetCombo, gbc);
        row++;

        row = addSectionHeading(formPanel, gbc, row, "Connection details");

        // Endpoint URL
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Endpoint URL:"), gbc);
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1.0;
        endpointUrlField = new JTextField(AiConfig.DEFAULT_OLLAMA_ENDPOINT, 40);
        formPanel.add(endpointUrlField, gbc);
        row++;

        // API Key with Show/Hide checkbox
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("API Key:"), gbc);
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
        formPanel.add(new JLabel("Model:"), gbc);
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1.0;
        modelNameField = new JTextField(AiConfig.DEFAULT_MODEL, 30);
        formPanel.add(modelNameField, gbc);
        row++;

        endpointUrlField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { syncPresetWithEndpoint(); }
            @Override public void removeUpdate(DocumentEvent event) { syncPresetWithEndpoint(); }
            @Override public void changedUpdate(DocumentEvent event) { syncPresetWithEndpoint(); }
        });

        row = addSectionHeading(formPanel, gbc, row, "Request options");

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
        JLabel headersHint = new JLabel("Add one provider header per line. Leave blank if none are needed.");
        headersHint.setFont(headersHint.getFont().deriveFont(Font.PLAIN, 11f));
        headersHint.setForeground(Color.GRAY);
        headersContainer.add(headersHint, BorderLayout.SOUTH);
        formPanel.add(headersContainer, gbc);
        row++;

        // Timeout (seconds)
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Timeout:"), gbc);
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1.0;
        timeoutSpinner = new JSpinner(new SpinnerNumberModel(AiConfig.DEFAULT_TIMEOUT_SECONDS, 1, 600, 1));
        JPanel timeoutPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        timeoutPanel.add(timeoutSpinner);
        JLabel secondsLabel = new JLabel(" seconds");
        secondsLabel.setForeground(Color.GRAY);
        timeoutPanel.add(secondsLabel);
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
        autoAnalyzeCheckBox = new JCheckBox("Analyze intercepted requests automatically");
        formPanel.add(autoAnalyzeCheckBox, gbc);
        row++;

        contentPanel.add(formPanel);
        formPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, formPanel.getPreferredSize().height));
        contentPanel.add(Box.createVerticalStrut(14));

        // 3. Cache Management Panel
        JPanel cachePanel = new JPanel(new BorderLayout(10, 0));
        cachePanel.setBorder(BorderFactory.createTitledBorder("Analysis Cache"));
        cachePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        cacheStatsLabel = new JLabel("Cached analyses: 0");
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
        cachePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, cachePanel.getPreferredSize().height));
        contentPanel.add(Box.createVerticalStrut(14));

        // 4. Action Bar (Test Connection, Save, Status)
        JPanel actionPanel = new JPanel();
        actionPanel.setLayout(new BoxLayout(actionPanel, BoxLayout.Y_AXIS));
        actionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btnPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        testConnectionBtn = new JButton("Test Connection");
        testConnectionBtn.addActionListener(e -> onTestConnection());
        saveBtn = new JButton("Save Settings");
        saveBtn.addActionListener(e -> saveConfig());
        btnPanel.add(testConnectionBtn);
        btnPanel.add(saveBtn);

        testStatusLabel = new JLabel("Ready.");
        testStatusLabel.setFont(testStatusLabel.getFont().deriveFont(Font.PLAIN, 12f));
        testStatusLabel.setBorder(new EmptyBorder(6, 10, 6, 10));
        testStatusLabel.setOpaque(true);
        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        statusRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusRow.add(testStatusLabel);
        actionPanel.add(btnPanel);
        actionPanel.add(statusRow);
        showStatus("Ready", StatusTone.NEUTRAL);
        contentPanel.add(actionPanel);
        actionPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, actionPanel.getPreferredSize().height));

        add(new JScrollPane(contentPanel), BorderLayout.CENTER);
    }

    private void onPresetChanged() {
        if (updatingPreset) {
            return;
        }
        int idx = presetCombo.getSelectedIndex();
        updatingPreset = true;
        try {
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
        } finally {
            updatingPreset = false;
        }
    }

    private int addSectionHeading(JPanel panel, GridBagConstraints gbc, int row, String title) {
        JLabel headingLabel = new JLabel(title);
        headingLabel.setFont(headingLabel.getFont().deriveFont(Font.BOLD, 12f));
        JPanel heading = new JPanel(new BorderLayout(0, 5));
        heading.setOpaque(false);
        heading.add(headingLabel, BorderLayout.NORTH);
        heading.add(new JSeparator(), BorderLayout.SOUTH);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(row == 0 ? 4 : 12, 8, 3, 8);
        panel.add(heading, gbc);

        gbc.gridwidth = 1;
        gbc.insets = new Insets(5, 8, 5, 8);
        return row + 1;
    }

    private void syncPresetWithEndpoint() {
        if (updatingPreset || presetCombo == null || endpointUrlField == null) {
            return;
        }
        int matchingPreset = findPreset(endpointUrlField.getText());
        if (presetCombo.getSelectedIndex() != matchingPreset) {
            updatingPreset = true;
            try {
                presetCombo.setSelectedIndex(matchingPreset);
            } finally {
                updatingPreset = false;
            }
        }
    }

    private int findPreset(String endpoint) {
        if (endpoint == null) return PROVIDER_PRESETS.length - 1;
        String value = endpoint.trim();
        if (AiConfig.DEFAULT_OLLAMA_ENDPOINT.equalsIgnoreCase(value)) return 0;
        if (AiConfig.DEFAULT_OPENAI_ENDPOINT.equalsIgnoreCase(value)) return 1;
        if (AiConfig.DEFAULT_DEEPSEEK_ENDPOINT.equalsIgnoreCase(value)) return 2;
        if (AiConfig.DEFAULT_GEMINI_ENDPOINT.equalsIgnoreCase(value)) return 3;
        if (AiConfig.DEFAULT_QWEN_ENDPOINT.equalsIgnoreCase(value)) return 4;
        if (AiConfig.DEFAULT_OPENCODE_ENDPOINT.equalsIgnoreCase(value)) return 5;
        return PROVIDER_PRESETS.length - 1;
    }

    private enum StatusTone { NEUTRAL, INFO, SUCCESS, ERROR }

    private void showStatus(String message, StatusTone tone) {
        testStatusLabel.setText("  ●  " + message + "  ");
        Color panelColor = UIManager.getColor("Panel.background");
        boolean dark = panelColor != null && (panelColor.getRed() + panelColor.getGreen() + panelColor.getBlue()) / 3 < 128;
        switch (tone) {
            case INFO -> setStatusColors(dark ? new Color(42, 67, 91) : new Color(232, 242, 252),
                    dark ? new Color(166, 207, 246) : new Color(35, 91, 145));
            case SUCCESS -> setStatusColors(dark ? new Color(38, 75, 56) : new Color(232, 246, 237),
                    dark ? new Color(166, 224, 185) : new Color(39, 112, 65));
            case ERROR -> setStatusColors(dark ? new Color(87, 43, 46) : new Color(252, 235, 235),
                    dark ? new Color(255, 177, 177) : new Color(157, 43, 43));
            default -> setStatusColors(dark ? new Color(55, 58, 62) : new Color(241, 243, 245),
                    UIManager.getColor("Label.foreground") != null ? UIManager.getColor("Label.foreground") : Color.GRAY);
        }
    }

    private void setStatusColors(Color background, Color foreground) {
        testStatusLabel.setBackground(background);
        testStatusLabel.setForeground(foreground);
    }

    private String conciseConnectionMessage(String message) {
        if (message == null || message.isBlank()) return "No details provided";
        return message.replaceFirst("^Connection test failed:\\s*", "");
    }

    private void onTestConnection() {
        testConnectionBtn.setEnabled(false);
        showStatus("Testing connection…", StatusTone.INFO);

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
                        String details = conciseConnectionMessage(res.message())
                                .replaceFirst("^Successfully connected to model ", "Model ");
                        showStatus(String.format("Success · %s · %d ms", details, res.latencyMs()), StatusTone.SUCCESS);
                    } else {
                        showStatus(String.format("Connection failed · %s · %d ms",
                                conciseConnectionMessage(res.message()), res.latencyMs()), StatusTone.ERROR);
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    testConnectionBtn.setEnabled(true);
                    String message = ex.getMessage();
                    showStatus("Connection failed · " + (message == null || message.isBlank() ? ex.getClass().getSimpleName() : message), StatusTone.ERROR);
                });
            }
        });
    }

    public void saveConfig() {
        AiConfig newConfig = buildConfigFromUi();
        List<String> errors = newConfig.validate();
        if (!errors.isEmpty()) {
            showStatus("Check settings · " + String.join("; ", errors), StatusTone.ERROR);
            return;
        }

        if (controller != null) {
            controller.setAiConfig(newConfig);
        }
        showStatus("Settings saved successfully", StatusTone.SUCCESS);
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
            cacheStatsLabel.setText(String.format("Cached analyses: %d", size));
        } else {
            cacheStatsLabel.setText("Cached analyses: 0");
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
