package thorization.model.pipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * Pipeline step representing an HTTP request template with dynamic {{VAR}} placeholder support,
 * role credential binding, and variable extraction rules.
 */
public class RequestStep extends PipelineStep {
    private String method;
    private String urlTemplate;
    private String headersTemplate;
    private String bodyTemplate;
    private String targetRoleId; // Role ID to execute with, or "UNAUTHENTICATED"
    private List<ExtractionRule> extractions;
    private boolean isTeardown;

    public RequestStep() {
        super("HTTP Request Step", StepType.REQUEST);
        this.method = "GET";
        this.urlTemplate = "https://example.com/api/v1/resource/{{VAR_ID}}";
        this.headersTemplate = "";
        this.bodyTemplate = "";
        this.targetRoleId = "";
        this.extractions = new ArrayList<>();
        this.isTeardown = false;
    }

    public RequestStep(String name, String method, String urlTemplate, String targetRoleId) {
        super(name, StepType.REQUEST);
        this.method = method != null ? method.toUpperCase() : "GET";
        this.urlTemplate = urlTemplate != null ? urlTemplate : "";
        this.headersTemplate = "";
        this.bodyTemplate = "";
        this.targetRoleId = targetRoleId != null ? targetRoleId : "";
        this.extractions = new ArrayList<>();
        this.isTeardown = false;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method != null ? method.toUpperCase() : "GET";
    }

    public String getUrlTemplate() {
        return urlTemplate;
    }

    public void setUrlTemplate(String urlTemplate) {
        this.urlTemplate = urlTemplate;
    }

    public String getHeadersTemplate() {
        return headersTemplate;
    }

    public void setHeadersTemplate(String headersTemplate) {
        this.headersTemplate = headersTemplate;
    }

    public String getBodyTemplate() {
        return bodyTemplate;
    }

    public void setBodyTemplate(String bodyTemplate) {
        this.bodyTemplate = bodyTemplate;
    }

    public String getTargetRoleId() {
        return targetRoleId;
    }

    public void setTargetRoleId(String targetRoleId) {
        this.targetRoleId = targetRoleId;
    }

    public List<ExtractionRule> getExtractions() {
        return extractions;
    }

    public void setExtractions(List<ExtractionRule> extractions) {
        this.extractions = extractions != null ? extractions : new ArrayList<>();
    }

    public void addExtraction(ExtractionRule rule) {
        if (rule != null) {
            this.extractions.add(rule);
        }
    }

    public boolean isTeardown() {
        return isTeardown;
    }

    public void setTeardown(boolean teardown) {
        this.isTeardown = teardown;
    }
}

