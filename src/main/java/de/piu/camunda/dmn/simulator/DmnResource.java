package de.piu.camunda.dmn.simulator;

import de.piu.camunda.dmn.simulator.dto.*;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Collections;

@Path("/api/dmn")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DmnResource {

    @Inject
    DmnService dmnService;

    @POST
    @Path("/parse")
    public ParseResponse parse(ParseRequest req) {
        ParseResponse res = new ParseResponse();
        res.decisions = dmnService.parseDecisions(req.dmnXml);
        return res;
    }

    @POST
    @Path("/evaluate")
    public EvaluateResponse evaluate(EvaluateRequest req) {
        EvaluateResponse res = new EvaluateResponse();
        if (req.variables == null) {
            req.variables = Collections.emptyMap();
        }
        EvaluationResult eval = dmnService.evaluate(req.dmnXml, req.decisionId, req.variables);
        res.result = eval.result;
        res.matchedRuleIndexes = eval.matchedRuleIndexes;
        return res;
    }
}
