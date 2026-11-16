"use strict";

const { ServiceBusClient } = require("@azure/service-bus");
const { createReply } = require("../core/reply");

/**
 * Receives requests from the source queue with peek-lock and sends each reply to the destination queue with the
 * request's CorrelationId (which is how H2R matches it).
 * The SDK completes a request when processMessage returns and abandons it if it throws (the reply could not be sent);
 * abandoned requests are redelivered, then dead-lettered after the queue's max delivery count.
 *
 * @param {{ connectionString: string, sourceQueue: string, destinationQueue: string }} config
 * @param {(request: object, correlationId: string) => Promise<object|null|undefined>} processRequest the business logic
 * @returns {{ close: () => Promise<void> }} call close() to stop
 */
function startRequestListener(config, processRequest) {
  const client = new ServiceBusClient(config.connectionString);
  const receiver = client.createReceiver(config.sourceQueue); // peek-lock is the default
  const sender = client.createSender(config.destinationQueue);

  async function respondTo(message) {
    const correlationId = message.correlationId;
    console.log(`Received request ${correlationId}`);
    const reply = await createReply(processRequest, message.body, correlationId);
    // The SDK sends an object body as JSON.
    await sender.sendMessages({ body: reply, correlationId, contentType: "application/json" });
    console.log(`Replied to request ${correlationId}`);
  }

  const subscription = receiver.subscribe(
    {
      processMessage: respondTo,
      // The SDK recovers from connection and receive errors on its own, so logging is the only action needed.
      processError: async (args) => console.error(`Service Bus error (${args.errorSource})`, args.error),
    },
    // One request at a time, so state kept in businessLogic.js is never used by two requests at once.
    { maxConcurrentCalls: 1 }
  );

  // Stops receiving first (an in-flight request finishes or goes back to the queue), then closes the connection.
  async function close() {
    await subscription.close();
    await receiver.close();
    await sender.close();
    await client.close();
  }

  return { close };
}

module.exports = { startRequestListener };
