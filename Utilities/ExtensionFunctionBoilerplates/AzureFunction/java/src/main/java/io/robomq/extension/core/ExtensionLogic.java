package io.robomq.extension.core;

import org.json.JSONObject;

/**
 * Port for the customer's business logic. {@code BusinessLogic} implements it; you do not need to change this file.
 */
public interface ExtensionLogic {

    /**
     * Runs the business logic for one request from H2R.
     *
     * <p>Preconditions: {@code request} is the parsed JSON body of the request; {@code correlationId} identifies it
     * (it can be null if H2R did not set one).
     * <p>Postconditions: returns null for a plain success, or a JSONObject that is sent back to H2R as {@code data}.
     * <p>Side effects: whatever the business logic does (for example, calling another system).
     * <p>Idempotency: the same request can arrive more than once. Service Bus redelivers a message if the reply could
     * not be sent, even when this method already succeeded. Make repeated calls with the same request safe.
     * <p>Failure modes: throw {@link BusinessLogicException} to report a failure with your own error code;
     * any other exception is reported to H2R with error code 500.
     */
    JSONObject process(JSONObject request, String correlationId) throws Exception;
}
