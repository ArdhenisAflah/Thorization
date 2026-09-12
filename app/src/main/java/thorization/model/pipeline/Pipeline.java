package thorization.model.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Encapsulates a complete stateful authorization test pipeline consisting of ordered steps,
 * dynamic placeholder extractions, and conditional branches.
 */
public class Pipeline {
    private String id;
    private String name;
    private String description;
    private List<PipelineStep> steps;
    private Map<String, String> initialVariables;

    public Pipeline() {
        this.id = UUID.randomUUID().toString();
        this.name = "New Pipeline Scenario";
        this.description = "Multi-step stateful authorization check";
        this.steps = new ArrayList<>();
        this.initialVariables = new LinkedHashMap<>();
    }

    public Pipeline(String name, String description) {
        this.id = UUID.randomUUID().toString();
        this.name = name != null ? name : "Pipeline Scenario";
        this.description = description != null ? description : "";
        this.steps = new ArrayList<>();
        this.initialVariables = new LinkedHashMap<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<PipelineStep> getSteps() {
        return steps;
    }

    public void setSteps(List<PipelineStep> steps) {
        this.steps = steps != null ? steps : new ArrayList<>();
    }

    public void addStep(PipelineStep step) {
        if (step != null) {
            this.steps.add(step);
        }
    }

    public void removeStep(int index) {
        if (index >= 0 && index < steps.size()) {
            this.steps.remove(index);
        }
    }

    public void moveStepUp(int index) {
        if (index > 0 && index < steps.size()) {
            PipelineStep step = steps.remove(index);
            steps.add(index - 1, step);
        }
    }

    public void moveStepDown(int index) {
        if (index >= 0 && index < steps.size() - 1) {
            PipelineStep step = steps.remove(index);
            steps.add(index + 1, step);
        }
    }

    public Map<String, String> getInitialVariables() {
        return initialVariables;
    }

    public void setInitialVariables(Map<String, String> initialVariables) {
        this.initialVariables = initialVariables != null ? initialVariables : new LinkedHashMap<>();
    }

    @Override
    public String toString() {
        return name;
    }
}

