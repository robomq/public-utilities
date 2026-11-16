package io.robomq.extension;

import io.robomq.extension.core.BusinessLogicException;
import io.robomq.extension.core.ExtensionLogic;
import org.json.JSONObject;

/**
 * Write your business logic in this class. This is the only file you need to change.
 *
 * <p>One BusinessLogic object is created at startup and reused for every request, so fields you set in the
 * constructor or in {@link #process} (API clients, tokens, caches) are kept between requests.
 * Requests are handled one at a time.
 *
 * <p>Example:
 * <pre>{@code
 * public class BusinessLogic implements ExtensionLogic {
 *     private final MyApiClient client;
 *
 *     public BusinessLogic() {
 *         client = new MyApiClient();                  // one-time setup
 *     }
 *
 *     @Override
 *     public JSONObject process(JSONObject request, String correlationId) throws Exception {
 *         String email = request.getString("email");    // read a value from the request
 *         if (email.isEmpty()) {
 *             throw new BusinessLogicException("Email is missing", "400");   // failure with your own code
 *         }
 *         JSONObject data = new JSONObject();          // optional: data sent back to H2R
 *         data.put("accountId", client.createAccount(email));
 *         return data;                                 // or return null if there is nothing to send back
 *     }
 * }
 * }</pre>
 */
public class BusinessLogic implements ExtensionLogic {

    public BusinessLogic() {
        // One-time setup goes here.
    }

    /** See {@link ExtensionLogic#process} for the contract: return values, errors and why it must be safe to repeat. */
    @Override
    public JSONObject process(JSONObject request, String correlationId) throws Exception {
        return null;
    }
}
