package de.piu.camunda.dmn.simulator.dto;

import java.util.ArrayList;
import java.util.List;

public class RuleDefinition {
    public String id;
    public int index;
    public List<String> inputEntries = new ArrayList<>();
    public List<String> outputEntries = new ArrayList<>();
}
