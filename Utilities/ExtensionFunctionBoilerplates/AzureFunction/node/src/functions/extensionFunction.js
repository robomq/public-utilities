"use strict";

const { app } = require("@azure/functions");
const { ServiceBusClient } = require("@azure/service-bus");
const { processRequest } = require("../businessLogic");
const { createReply } = require("../core/reply");

// Azure Function entry point. The Service Bus trigger receives each request from the queue in the SourceQueue setting;
// the reply is sent with the Service Bus SDK because an output binding cannot set the CorrelationId H2R needs.
// Azure completes the request when the handler returns and abandons it when the handler throws (the reply could not
// be sent); abandoned requests are retried, then dead-lettered after the queue's max delivery count.
// Settings (app settings in Azure, local.settings.json locally): ServiceBusConnection, SourceQueue, DestinationQueue.

const CONNECTION_SETTING = "ServiceBusConnection";
const DESTINATION_QUEUE_SETTING = "DestinationQueue";

// Created once when the function app starts and reused for every request.
const sender = createSender();

app.serviceBusQueue("ExtensionFunction", {
  connection: CONNECTION_SETTING,
  queueName: "%SourceQueue%",
  handler: async (message, context) => {
    const correlationId = context.triggerMetadata.correlationId;
    context.log(`Received request ${correlationId}`);
    const reply = await createReply(processRequest, message, correlationId);
    // The SDK sends an object body as JSON.
    await sender.sendMessages({ body: reply, correlationId, contentType: "application/json" });
    context.log(`Replied to request ${correlationId}`);
  },
});

function createSender() {
  const connectionString = process.env[CONNECTION_SETTING];
  const destinationQueue = process.env[DESTINATION_QUEUE_SETTING];
  if (!connectionString || !destinationQueue) {
    throw new Error(`Set the ${CONNECTION_SETTING} and ${DESTINATION_QUEUE_SETTING} app settings`);
  }
  return new ServiceBusClient(connectionString).createSender(destinationQueue);
}
