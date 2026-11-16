package io.robomq.extension.core;

import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Runs the business logic for one H2R request and creates the reply defined by the H2R extensionFunction contract.
 * Do not change the reply shape without the H2R team.
 */
public class ExtensionRequestService {
    private static final Logger LOG = Logger.getLogger(ExtensionRequestService.class.getName());
    private static final String UNEXPECTED_ERROR_CODE = "500";

    private final ExtensionLogic businessLogic;

    public ExtensionRequestService(ExtensionLogic businessLogic) {
        this.businessLogic = businessLogic;
    }

    /**
     * Creates the reply for one request.
     *
     * <p>Postconditions: always returns a reply (success or failure); a failure inside the business logic is turned
     * into a failure reply, never thrown.
     * <p>Side effects: runs the business logic.
     */
    public String createReply(String requestBody, String correlationId) {
        JSONObject request;
        try {
            request = new JSONObject(requestBody);
        } catch (JSONException e) {
            LOG.warning("Request " + correlationId + " is not a JSON object");
            return failure(UNEXPECTED_ERROR_CODE, e.getMessage());
        }
        try {
            return success(businessLogic.process(request, correlationId));
        } catch (BusinessLogicException e) {
            LOG.warning("Business logic reported failure " + e.getErrorCode() + " for request " + correlationId);
            return failure(e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Business logic failed for request " + correlationId, e);
            return failure(UNEXPECTED_ERROR_CODE, e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    private static String success(JSONObject data) {
        JSONObject reply = new JSONObject();
        reply.put("status", "success");
        reply.put("timestamp", Instant.now().toString());
        if (data != null) {
            reply.put("data", data);
        }
        return reply.toString();
    }

    private static String failure(String errorCode, String errorDescription) {
        JSONObject reply = new JSONObject();
        reply.put("status", "failure");
        reply.put("timestamp", Instant.now().toString());
        reply.put("error_code", errorCode);
        reply.put("error_description", errorDescription);
        return reply.toString();
    }
}
