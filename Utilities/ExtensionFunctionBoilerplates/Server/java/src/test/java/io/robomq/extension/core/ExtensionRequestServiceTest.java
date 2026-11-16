package io.robomq.extension.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class ExtensionRequestServiceTest {

    private static JSONObject createReply(ExtensionLogic businessLogic, String requestBody) {
        return new JSONObject(new ExtensionRequestService(businessLogic).createReply(requestBody, "cid-1"));
    }

    @Test
    void nullResultIsSuccessWithoutData() {
        JSONObject reply = createReply((request, correlationId) -> null, "{\"email\":\"a@b.com\"}");
        assertEquals("success", reply.getString("status"));
        assertFalse(reply.has("data"));
        assertTrue(reply.getString("timestamp").endsWith("Z"));
    }

    @Test
    void requestAndCorrelationIdReachLogicAndDataIsReturned() {
        JSONObject reply = createReply((request, correlationId) ->
                new JSONObject().put("echo", request.getString("email") + "/" + correlationId), "{\"email\":\"a@b.com\"}");
        assertEquals("a@b.com/cid-1", reply.getJSONObject("data").getString("echo"));
    }

    @Test
    void businessLogicExceptionKeepsItsCode() {
        JSONObject reply = createReply((request, correlationId) -> {
            throw new BusinessLogicException("Email is missing", "400");
        }, "{}");
        assertEquals("failure", reply.getString("status"));
        assertEquals("400", reply.getString("error_code"));
        assertEquals("Email is missing", reply.getString("error_description"));
        assertTrue(reply.getString("timestamp").endsWith("Z"));
    }

    @Test
    void otherExceptionIs500() {
        JSONObject reply = createReply((request, correlationId) -> {
            throw new IllegalStateException("boom");
        }, "{}");
        assertEquals("500", reply.getString("error_code"));
        assertEquals("boom", reply.getString("error_description"));
    }

    @Test
    void exceptionWithoutMessageStillHasDescription() {
        JSONObject reply = createReply((request, correlationId) -> {
            throw new NullPointerException();
        }, "{}");
        assertEquals("java.lang.NullPointerException", reply.getString("error_description"));
    }

    @Test
    void invalidJsonIs500AndLogicIsNotCalled() {
        JSONObject reply = createReply((request, correlationId) -> {
            throw new AssertionError("business logic must not run");
        }, "not json");
        assertEquals("failure", reply.getString("status"));
        assertEquals("500", reply.getString("error_code"));
    }

    @Test
    void stateInBusinessLogicIsKeptBetweenRequests() {
        ExtensionRequestService service = new ExtensionRequestService(new ExtensionLogic() {
            private int count = 0;

            @Override
            public JSONObject process(JSONObject request, String correlationId) {
                count++;
                return new JSONObject().put("count", count);
            }
        });
        service.createReply("{}", "1");
        assertEquals(2, new JSONObject(service.createReply("{}", "2")).getJSONObject("data").getInt("count"));
    }
}
