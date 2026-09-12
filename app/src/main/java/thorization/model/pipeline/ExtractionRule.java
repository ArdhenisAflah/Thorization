package thorization.model.pipeline;

import java.util.UUID;

/**
 * Defines a specific variable extraction rule within a pipeline request step.
 */
public class ExtractionRule {
    private String id;
    private String variableName;
    private ExtractionSource source;
    private ExtractionType type;
    private String expression;
    private String defaultValue;

    public ExtractionRule() {
        this.id = UUID.randomUUID().toString();
        this.variableName = "VAR_ID";
        this.source = ExtractionSource.RESPONSE_BODY;
        this.type = ExtractionType.JSON_PATH;
        this.expression = "$.id";
        this.defaultValue = "";
    }

    public ExtractionRule(String variableName, ExtractionSource source, ExtractionType type, String expression) {
        this.id = UUID.randomUUID().toString();
        this.variableName = variableName;
        this.source = source;
        this.type = type;
        this.expression = expression;
        this.defaultValue = "";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVariableName() {
        return variableName;
    }

    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    public ExtractionSource getSource() {
        return source;
    }

    public void setSource(ExtractionSource source) {
        this.source = source;
    }

    public ExtractionType getType() {
        return type;
    }

    public void setType(ExtractionType type) {
        this.type = type;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
}

