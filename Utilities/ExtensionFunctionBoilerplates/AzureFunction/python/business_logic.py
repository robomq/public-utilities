"""Write your business logic in this file. This is the only file you need to change.

One-time setup: code at the top level of this file runs once, when the program starts. Variables defined here
(API clients, tokens, caches) are kept between requests. Requests are handled one at a time.

Example:

    client = create_my_api_client(os.environ["MyApiKey"])        # one-time setup

    def process_request(request, correlation_id):
        email = request.get("email")                               # read a value from the request
        if not email:
            raise BusinessLogicError("Email is missing", "400")    # failure with your own code
        account_id = client.create_account(email)
        return {"accountId": account_id}                           # optional: data sent back to H2R; or return None
"""

from core.business_logic_error import BusinessLogicError  # noqa: F401  (raise it to choose the error code)


def process_request(request: dict, correlation_id: "str | None") -> "dict | None":
    """Called once for every request from H2R.

    Return None for a plain success, or a dict that is sent back to H2R as "data".
    Raise BusinessLogicError(message, code) to report a failure with your own error code; any other exception is
    reported with code 500. The same request can arrive more than once (Service Bus redelivers it if the reply could
    not be sent), so make repeated calls with the same request safe.

    request        -- the request body from H2R, parsed from JSON
    correlation_id -- the ID of this request (use it in your log messages)
    """
    return None
