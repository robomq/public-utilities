package io.robomq.extension.servicebus;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import io.robomq.extension.core.ExtensionRequestService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Receives requests from the source queue with peek-lock and sends each reply to the destination queue with the
 * request's CorrelationId (which is how H2R matches it).
 * The SDK completes a request when the reply was sent and abandons it if sending throws; abandoned requests are
 * redelivered, then dead-lettered after the queue's max delivery count.
 */
public class ServiceBusRequestListener implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(ServiceBusRequestListener.class.getName());

    private final ExtensionRequestService requestService;
    private final ServiceBusSenderClient senderClient;
    private final ServiceBusProcessorClient processorClient;

    public ServiceBusRequestListener(String connectionString, String sourceQueue, String destinationQueue,
                                     ExtensionRequestService requestService) {
        this.requestService = requestService;
        this.senderClient = new ServiceBusClientBuilder().connectionString(connectionString)
                .sender().queueName(destinationQueue).buildClient();
        this.processorClient = new ServiceBusClientBuilder().connectionString(connectionString)
                .processor().queueName(sourceQueue)
                // One request at a time, so state kept in BusinessLogic is never used by two threads at once.
                .maxConcurrentCalls(1)
                .processMessage(context -> respondTo(context.getMessage()))
                .processError(ServiceBusRequestListener::logError)
                .buildProcessorClient();
    }

    public void start() {
        processorClient.start();
    }

    /** Stops receiving first (in-flight requests finish or go back to the queue), then closes the sender. */
    @Override
    public void close() {
        processorClient.close();
        senderClient.close();
    }

    private void respondTo(ServiceBusReceivedMessage request) {
        String correlationId = request.getCorrelationId();
        LOG.info("Received request " + correlationId);
        String reply = requestService.createReply(request.getBody().toString(), correlationId);
        senderClient.sendMessage(new ServiceBusMessage(reply).setCorrelationId(correlationId).setContentType("application/json"));
        LOG.info("Replied to request " + correlationId);
    }

    // The processor recovers from connection and receive errors on its own, so logging is the only action needed.
    private static void logError(ServiceBusErrorContext context) {
        LOG.log(Level.SEVERE, "Service Bus error (" + context.getErrorSource() + ")", context.getException());
    }
}
