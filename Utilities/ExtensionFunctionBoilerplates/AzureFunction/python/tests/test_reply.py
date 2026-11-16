import json
import logging
import unittest

from core.business_logic_error import BusinessLogicError
from core.reply import create_reply


def reply_for(process_request, body, correlation_id="cid-1"):
    return json.loads(create_reply(process_request, body, correlation_id))


def fail(message):
    raise AssertionError(message)


class CreateReplyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        logging.disable(logging.CRITICAL)  # business-logic failures are logged on purpose; keep test output readable

    @classmethod
    def tearDownClass(cls):
        logging.disable(logging.NOTSET)

    def test_none_result_is_success_without_data(self):
        reply = reply_for(lambda request, cid: None, b'{"email":"a@b.com"}')
        self.assertEqual(reply["status"], "success")
        self.assertNotIn("data", reply)
        self.assertTrue(reply["timestamp"].endswith("Z"))

    def test_request_and_correlation_id_reach_the_logic_and_data_is_returned(self):
        reply = reply_for(lambda request, cid: {"echo": f"{request['email']}/{cid}"}, '{"email":"a@b.com"}')
        self.assertEqual(reply["data"], {"echo": "a@b.com/cid-1"})

    def test_business_logic_error_keeps_its_code(self):
        def process_request(request, cid):
            raise BusinessLogicError("Email is missing", "400")

        reply = reply_for(process_request, b"{}")
        self.assertEqual((reply["status"], reply["error_code"], reply["error_description"]),
                         ("failure", "400", "Email is missing"))
        self.assertTrue(reply["timestamp"].endswith("Z"))

    def test_other_exception_is_500(self):
        def process_request(request, cid):
            raise RuntimeError("boom")

        reply = reply_for(process_request, b"{}")
        self.assertEqual((reply["error_code"], reply["error_description"]), ("500", "boom"))

    def test_exception_without_message_still_has_a_description(self):
        def process_request(request, cid):
            raise KeyError

        self.assertEqual(reply_for(process_request, b"{}")["error_description"], "KeyError")

    def test_result_that_is_not_json_serializable_is_500(self):
        reply = reply_for(lambda request, cid: {"when": object()}, b"{}")
        self.assertEqual((reply["status"], reply["error_code"]), ("failure", "500"))

    def test_invalid_json_or_non_object_body_is_500_and_logic_is_not_called(self):
        for body in (b"not json", b"[1,2]", b"null", b"42", b"", b"\xff\xfe"):
            with self.subTest(body=body):
                reply = reply_for(lambda request, cid: fail("business logic must not run"), body)
                self.assertEqual((reply["status"], reply["error_code"]), ("failure", "500"))

    def test_state_kept_outside_the_function_survives_between_requests(self):
        calls = []

        def process_request(request, cid):
            calls.append(cid)
            return {"count": len(calls)}

        create_reply(process_request, b"{}", "1")
        self.assertEqual(reply_for(process_request, b"{}", "2")["data"]["count"], 2)


if __name__ == "__main__":
    unittest.main()
