package io.robomq.extension;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.ServiceBusQueueTrigger;
import io.robomq.extension.core.ExtensionRequestService;
import java.util.logging.Logger;

/**
 * Azure Function entry point. The Service Bus trigger receives each request from the queue in the SourceQueue setting;
 * the reply is sent with the Service Bus SDK because an output binding cannot set the CorrelationId H2R needs.
 * Azure completes the request when run returns and abandons it when run throws (the reply could not be sent);
 * abandoned requests are retried, then dead-lettered after the queue's max delivery count.
 * Settings (app settings in Azure, local.settings.json locally): ServiceBusConnection, SourceQueue, DestinationQueue.
 */
public class ExtensionFunction {
    private static final Logger LOG = Logger.getLogger(ExtensionFunction.class.getName());
    private static final String CONNECTION_SETTING = "ServiceBusConnection";
    private static final String DESTINATION_QUEUE_SETTING = "DestinationQueue";

    // Azure creates a new ExtensionFunction object for every request, so these live in static fields:
    // they are created once per Function instance, and BusinessLogic keeps its state between requests.
    private static final ExtensionRequestService extensionRequestService = new ExtensionRequestService(new BusinessLogic());
    private static final ServiceBusSenderClient sender = createSender();

    @FunctionName("ExtensionFunction")
    public void run(
            @ServiceBusQueueTrigger(name = "message", queueName = "%SourceQueue%", connection = CONNECTION_SETTING)
            String message,
            @BindingName("CorrelationId") String correlationId) {
        LOG.info("Received request " + correlationId);
        String reply = extensionRequestService.createReply(message, correlationId);
        sender.sendMessage(new ServiceBusMessage(reply).setCorrelationId(correlationId).setContentType("application/json"));
        LOG.info("Replied to request " + correlationId);
    }

    private static ServiceBusSenderClient createSender() {
        String connectionString = System.getenv(CONNECTION_SETTING);
        String destinationQueue = System.getenv(DESTINATION_QUEUE_SETTING);
        if (connectionString == null || destinationQueue == null) {
            throw new IllegalStateException("Set the " + CONNECTION_SETTING + " and " + DESTINATION_QUEUE_SETTING + " app settings");
        }
        return new ServiceBusClientBuilder().connectionString(connectionString)
                .sender().queueName(destinationQueue).buildClient();
    }
}
