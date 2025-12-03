package de.piu.camunda.dmn.simulator.dto;

import java.util.Map;

public class EvaluateRequest {
    public String dmnXml;
    public String decisionId;
    public Map<String, Object> variables;
}
