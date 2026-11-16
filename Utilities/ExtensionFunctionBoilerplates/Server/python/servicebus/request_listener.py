"""Receives requests from the source queue with peek-lock and sends each reply to the destination queue with the
request's CorrelationId (which is how H2R matches it).

A request is completed only after its reply was sent. If sending fails, the request is abandoned, so Service Bus
redelivers it, then dead-letters it after the queue's max delivery count."""

import logging
import time

from azure.servicebus import AutoLockRenewer, ServiceBusClient, ServiceBusMessage

from core.reply import create_reply

RECEIVE_WAIT_SECONDS = 5      # how often the loop checks for a stop request while the queue is empty
RECONNECT_DELAY_SECONDS = 5

log = logging.getLogger(__name__)


def run_request_listener(config, process_request, stop_requested) -> None:
    """Processes requests one at a time until stop_requested (a threading.Event) is set.

    Reconnects on its own after connection errors. Message locks are renewed while the business logic runs, so slow
    logic does not lose the request.
    """
    while not stop_requested.is_set():
        try:
            with ServiceBusClient.from_connection_string(config.connection_string) as client, \
                    AutoLockRenewer() as lock_renewer, \
                    client.get_queue_receiver(config.source_queue, auto_lock_renewer=lock_renewer) as receiver, \
                    client.get_queue_sender(config.destination_queue) as sender:
                while not stop_requested.is_set():
                    for message in receiver.receive_messages(max_message_count=1, max_wait_time=RECEIVE_WAIT_SECONDS):
                        _respond_to(message, receiver, sender, process_request)
        except Exception:
            log.exception("Service Bus connection error, reconnecting in %s seconds", RECONNECT_DELAY_SECONDS)
            stop_requested.wait(RECONNECT_DELAY_SECONDS)


def _respond_to(message, receiver, sender, process_request) -> None:
    correlation_id = message.correlation_id
    log.info("Received request %s", correlation_id)
    reply = create_reply(process_request, b"".join(message.body), correlation_id)
    try:
        sender.send_messages(ServiceBusMessage(reply, correlation_id=correlation_id, content_type="application/json"))
        receiver.complete_message(message)
        log.info("Replied to request %s", correlation_id)
    except Exception:
        log.exception("Could not reply to request %s; it goes back to the queue", correlation_id)
        try:
            receiver.abandon_message(message)
        except Exception:
            log.exception("Could not abandon request %s; it is redelivered when its lock expires", correlation_id)
