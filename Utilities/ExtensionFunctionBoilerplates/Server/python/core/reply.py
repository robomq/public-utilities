"""Builds the reply defined by the H2R extensionFunction contract. Do not change its shape without the H2R team.
You do not need to change this file."""

import json
import logging
from datetime import datetime, timezone
from typing import Callable, Optional

from .business_logic_error import BusinessLogicError

UNEXPECTED_ERROR_CODE = "500"

log = logging.getLogger(__name__)


def create_reply(process_request: Callable[[dict, Optional[str]], Optional[dict]],
                 request_body: "bytes | str", correlation_id: Optional[str]) -> str:
    """Runs the business logic for one request and creates the reply for H2R.

    Postconditions: always returns a reply (success or failure) as JSON text; a failure inside the business logic is
    turned into a failure reply, never raised.
    Side effects: runs the business logic.
    """
    try:
        request = _parse_request(request_body)
    except ValueError as error:
        log.warning("Request %s is not a JSON object", correlation_id)
        return _failure(UNEXPECTED_ERROR_CODE, str(error))
    try:
        return _success(process_request(request, correlation_id))
    except BusinessLogicError as error:
        log.warning("Business logic reported failure %s for request %s", error.error_code, correlation_id)
        return _failure(error.error_code, str(error))
    except Exception as error:  # any failure in the business logic becomes a 500 reply, never a lost request
        log.exception("Business logic failed for request %s", correlation_id)
        return _failure(UNEXPECTED_ERROR_CODE, str(error) or type(error).__name__)


def _parse_request(request_body: "bytes | str") -> dict:
    request = json.loads(request_body)  # accepts bytes (UTF-8) or str; JSONDecodeError is a ValueError
    if not isinstance(request, dict):
        raise ValueError("Request body is not a JSON object")
    return request


def _timestamp() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def _success(data: Optional[dict]) -> str:
    reply = {"status": "success", "timestamp": _timestamp()}
    if data is not None:
        reply["data"] = data
    return json.dumps(reply)


def _failure(error_code: str, error_description: str) -> str:
    return json.dumps({
        "status": "failure",
        "timestamp": _timestamp(),
        "error_code": error_code,
        "error_description": error_description,
    })
