package de.piu.camunda.dmn.simulator.dto;

import java.util.List;

public class DecisionInfo {
    public String id;
    public String name;
    public List<InputDefinition> inputs;
    public List<OutputDefinition> outputs;
    public List<RuleDefinition> rules;
}
