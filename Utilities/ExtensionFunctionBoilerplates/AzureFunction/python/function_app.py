"""Azure Function entry point (Python v2 programming model). You do not need to change this file.

The Service Bus trigger receives each request from the queue in the SourceQueue setting; the reply is sent with the
Service Bus SDK because an output binding cannot set the CorrelationId H2R needs.
Azure completes the request when the function returns and abandons it when the function raises (the reply could not
be sent); abandoned requests are retried, then dead-lettered after the queue's max delivery count.
Settings (app settings in Azure, local.settings.json locally): ServiceBusConnection, SourceQueue, DestinationQueue.
"""

import logging
import os

import azure.functions as func
from azure.servicebus import ServiceBusClient, ServiceBusMessage

from business_logic import process_request
from core.reply import create_reply

CONNECTION_SETTING = "ServiceBusConnection"
DESTINATION_QUEUE_SETTING = "DestinationQueue"

app = func.FunctionApp()


def _create_sender(destination_queue_setting):
    connection_string = os.environ.get(CONNECTION_SETTING)
    destination_queue = os.environ.get(destination_queue_setting)
    if not connection_string or not destination_queue:
        raise RuntimeError(f"Set the {CONNECTION_SETTING} and {destination_queue_setting} app settings")
    return ServiceBusClient.from_connection_string(connection_string).get_queue_sender(destination_queue)


# Created once when the function app starts and reused for every request. Requests are handled one at a time
# (maxConcurrentCalls in host.json), so the sender is never used by two requests at once.
_sender = _create_sender(DESTINATION_QUEUE_SETTING)


@app.function_name("ExtensionFunction")
@app.service_bus_queue_trigger(arg_name="message", queue_name="%SourceQueue%", connection=CONNECTION_SETTING)
def extension_function(message: func.ServiceBusMessage) -> None:
    correlation_id = message.correlation_id
    logging.info("Received request %s", correlation_id)
    reply = create_reply(process_request, message.get_body(), correlation_id)
    _sender.send_messages(ServiceBusMessage(reply, correlation_id=correlation_id, content_type="application/json"))
    logging.info("Replied to request %s", correlation_id)
