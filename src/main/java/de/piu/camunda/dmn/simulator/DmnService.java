package de.piu.camunda.dmn.simulator;

import de.piu.camunda.dmn.simulator.dto.DecisionInfo;
import de.piu.camunda.dmn.simulator.dto.InputDefinition;
import de.piu.camunda.dmn.simulator.dto.OutputDefinition;
import de.piu.camunda.dmn.simulator.dto.RuleDefinition;
import de.piu.camunda.dmn.simulator.dto.EvaluationResult;
import jakarta.inject.Singleton;
import org.camunda.bpm.dmn.engine.DmnDecisionResult;
import org.camunda.bpm.dmn.engine.DmnEngine;
import org.camunda.bpm.dmn.engine.DmnEngineConfiguration;
import org.camunda.bpm.dmn.engine.delegate.DmnDecisionTableEvaluationEvent;
import org.camunda.bpm.dmn.engine.delegate.DmnDecisionTableEvaluationListener;
import org.camunda.bpm.dmn.engine.delegate.DmnEvaluatedDecisionRule;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Singleton
public class DmnService {

    private final DmnEngine dmnEngine;

    /**
     * Thread-local store for the last evaluated decision table's matching rule IDs.
     * This is populated by a DmnDecisionTableEvaluationListener.
     */
    private static final ThreadLocal<List<String>> LAST_MATCHED_RULE_IDS = new ThreadLocal<>();

    public DmnService() {
        DmnEngineConfiguration cfg = DmnEngineConfiguration.createDefaultDmnEngineConfiguration();

        // Register a listener that captures the IDs of the matching rules for a decision table evaluation
        cfg.getCustomPostDecisionTableEvaluationListeners().add(new DmnDecisionTableEvaluationListener() {
            @Override
            public void notify(DmnDecisionTableEvaluationEvent evaluationEvent) {
                List<DmnEvaluatedDecisionRule> matchingRules = evaluationEvent.getMatchingRules();
                List<String> ids = new ArrayList<>();
                if (matchingRules != null) {
                    for (DmnEvaluatedDecisionRule r : matchingRules) {
                        if (r != null && r.getId() != null) {
                            ids.add(r.getId());
                        }
                    }
                }
                LAST_MATCHED_RULE_IDS.set(ids);
            }
        });

        this.dmnEngine = cfg.buildEngine();
    }

    public List<DecisionInfo> parseDecisions(String dmnXml) {
        List<DecisionInfo> result = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(
                new ByteArrayInputStream(dmnXml.getBytes(StandardCharsets.UTF_8))
            );

            NodeList decisions = doc.getElementsByTagNameNS("*", "decision");
            for (int i = 0; i < decisions.getLength(); i++) {
                Element el = (Element) decisions.item(i);
                String id = el.getAttribute("id");
                String name = el.getAttribute("name");

                DecisionInfo info = new DecisionInfo();
                info.id = id;
                info.name = (name != null && !name.isEmpty()) ? name : id;
                info.inputs = extractInputsForDecision(el);
                info.outputs = extractOutputsForDecision(el);
                info.rules = extractRulesForDecision(el);
                result.add(info);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse DMN XML", e);
        }
        return result;
    }

    private List<InputDefinition> extractInputsForDecision(Element decisionEl) {
        List<InputDefinition> inputs = new ArrayList<>();

        Element table = firstChildElementByLocalName(decisionEl, "decisionTable");
        if (table == null) {
            return inputs;
        }

        NodeList inputNodes = table.getElementsByTagNameNS("*", "input");
        for (int i = 0; i < inputNodes.getLength(); i++) {
            Element inputEl = (Element) inputNodes.item(i);
            InputDefinition def = new InputDefinition();

            def.id = inputEl.getAttribute("id");
            def.label = inputEl.getAttribute("label");

            Element inputExpr = firstChildElementByLocalName(inputEl, "inputExpression");
            if (inputExpr != null) {
                String typeRef = inputExpr.getAttribute("typeRef");
                def.typeRef = (typeRef != null && !typeRef.isEmpty()) ? typeRef : null;

                Element textEl = firstChildElementByLocalName(inputExpr, "text");
                if (textEl != null) {
                    String name = textEl.getTextContent();
                    if (name != null) {
                        def.name = name.trim();
                    }
                }
            }

            if (def.label == null || def.label.isEmpty()) {
                def.label = def.name;
            }

            Element inputValues = firstChildElementByLocalName(inputEl, "inputValues");
            if (inputValues != null) {
                Element textEl = firstChildElementByLocalName(inputValues, "text");
                if (textEl != null) {
                    String raw = textEl.getTextContent();
                    if (raw != null) {
                        raw = raw.trim();
                        def.allowedValues = parseAllowedValues(raw);
                    }
                }
            }

            inputs.add(def);
        }

        return inputs;
    }

    private List<OutputDefinition> extractOutputsForDecision(Element decisionEl) {
        List<OutputDefinition> outputs = new ArrayList<>();

        Element table = firstChildElementByLocalName(decisionEl, "decisionTable");
        if (table == null) {
            return outputs;
        }

        NodeList outputNodes = table.getElementsByTagNameNS("*", "output");
        for (int i = 0; i < outputNodes.getLength(); i++) {
            Element outputEl = (Element) outputNodes.item(i);
            OutputDefinition def = new OutputDefinition();

            def.id = outputEl.getAttribute("id");
            def.name = outputEl.getAttribute("name");
            def.label = outputEl.getAttribute("label");
            def.typeRef = outputEl.getAttribute("typeRef");

            if (def.label == null || def.label.isEmpty()) {
                def.label = def.name;
            }

            outputs.add(def);
        }

        return outputs;
    }

    private List<RuleDefinition> extractRulesForDecision(Element decisionEl) {
        List<RuleDefinition> rules = new ArrayList<>();

        Element table = firstChildElementByLocalName(decisionEl, "decisionTable");
        if (table == null) {
            return rules;
        }

        NodeList ruleNodes = table.getElementsByTagNameNS("*", "rule");
        for (int i = 0; i < ruleNodes.getLength(); i++) {
            Element ruleEl = (Element) ruleNodes.item(i);
            RuleDefinition rule = new RuleDefinition();
            rule.id = ruleEl.getAttribute("id");
            rule.index = i + 1;

            NodeList inputEntries = ruleEl.getElementsByTagNameNS("*", "inputEntry");
            for (int j = 0; j < inputEntries.getLength(); j++) {
                Element inEntry = (Element) inputEntries.item(j);
                Element textEl = firstChildElementByLocalName(inEntry, "text");
                String text = textEl != null ? textEl.getTextContent() : "";
                if (text != null) {
                    text = text.trim();
                }
                rule.inputEntries.add(text);
            }

            NodeList outputEntries = ruleEl.getElementsByTagNameNS("*", "outputEntry");
            for (int j = 0; j < outputEntries.getLength(); j++) {
                Element outEntry = (Element) outputEntries.item(j);
                Element textEl = firstChildElementByLocalName(outEntry, "text");
                String text = textEl != null ? textEl.getTextContent() : "";
                if (text != null) {
                    text = text.trim();
                }
                rule.outputEntries.add(text);
            }

            rules.add(rule);
        }

        return rules;
    }

    private Element firstChildElementByLocalName(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element) {
                Element e = (Element) n;
                if (localName.equals(e.getLocalName())) {
                    return e;
                }
            }
        }
        return null;
    }

    private List<String> parseAllowedValues(String raw) {
        List<String> values = new ArrayList<>();
        if (raw == null) {
            return values;
        }
        if (raw.contains("..") || raw.contains(">") || raw.contains("<")) {
            return values;
        }

        String trimmed = raw.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }

        String[] parts = trimmed.split(",");
        for (String p : parts) {
            String v = p.trim();
            if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                v = v.substring(1, v.length() - 1);
            }
            if (!v.isEmpty()) {
                values.add(v);
            }
        }

        return values;
    }

    public EvaluationResult evaluate(String dmnXml, String decisionId, Map<String, Object> variables) {
        try {
            ByteArrayInputStream in = new ByteArrayInputStream(
                dmnXml.getBytes(StandardCharsets.UTF_8)
            );

            // clear any previous state before evaluation
            LAST_MATCHED_RULE_IDS.remove();

            DmnDecisionResult decisionResult = dmnEngine.evaluateDecision(decisionId, in, variables);

            EvaluationResult eval = new EvaluationResult();
            eval.result = extractPayload(decisionResult);

            List<String> matchedRuleIds = LAST_MATCHED_RULE_IDS.get();
            LAST_MATCHED_RULE_IDS.remove();

            eval.matchedRuleIndexes = mapRuleIdsToIndexes(dmnXml, decisionId, matchedRuleIds);
            return eval;
        } catch (Exception e) {
            throw new RuntimeException("Error evaluating DMN decision", e);
        }
    }

    private Object extractPayload(DmnDecisionResult result) {
        try {
            List<Map<String, Object>> list = result.getResultList();
            if (list != null && !list.isEmpty()) {
                return list;
            }
        } catch (UnsupportedOperationException ignored) {
        }

        try {
            Object single = result.getSingleEntry();
            if (single != null) {
                return single;
            }
        } catch (Exception ignored) {
        }

        return result;
    }

    /**
     * Map the matching rule IDs (from the evaluation listener) to 1-based rule indexes
     * of the given decision's decision table as parsed from the DMN XML.
     */
    private List<Integer> mapRuleIdsToIndexes(String dmnXml, String decisionId, List<String> matchedRuleIds) {
        List<Integer> indexes = new ArrayList<>();
        if (matchedRuleIds == null || matchedRuleIds.isEmpty()) {
            return indexes;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(
                new ByteArrayInputStream(dmnXml.getBytes(StandardCharsets.UTF_8))
            );

            NodeList decisions = doc.getElementsByTagNameNS("*", "decision");
            Element targetDecision = null;
            for (int i = 0; i < decisions.getLength(); i++) {
                Element el = (Element) decisions.item(i);
                if (decisionId.equals(el.getAttribute("id"))) {
                    targetDecision = el;
                    break;
                }
            }
            if (targetDecision == null) {
                return indexes;
            }

            Element table = firstChildElementByLocalName(targetDecision, "decisionTable");
            if (table == null) {
                return indexes;
            }

            NodeList ruleNodes = table.getElementsByTagNameNS("*", "rule");
            for (int i = 0; i < ruleNodes.getLength(); i++) {
                Element ruleEl = (Element) ruleNodes.item(i);
                String ruleId = ruleEl.getAttribute("id");
                if (matchedRuleIds.contains(ruleId)) {
                    indexes.add(i + 1);
                }
            }
        } catch (Exception e) {
            // ignore mapping errors; we'll just return what we have so far
        }
        return indexes;
    }
}
