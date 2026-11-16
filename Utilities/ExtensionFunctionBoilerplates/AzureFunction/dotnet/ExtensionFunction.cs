using Azure.Messaging.ServiceBus;
using Microsoft.Azure.Functions.Worker;
using Microsoft.Extensions.Logging;
using RoboMQ.Extension.Core;

namespace RoboMQ.Extension;

/// <summary>
/// Azure Function entry point. The Service Bus trigger receives each request from the queue in the SourceQueue setting;
/// the reply is sent with the Service Bus SDK so the CorrelationId H2R needs is always set.
/// Azure completes the request when Run returns and abandons it when Run throws (the reply could not be sent);
/// abandoned requests are retried, then dead-lettered after the queue's max delivery count.
/// Settings (app settings in Azure, local.settings.json locally): ServiceBusConnection, SourceQueue, DestinationQueue.
/// </summary>
public class ExtensionFunction(ILogger<ExtensionFunction> logger)
{
    private const string ConnectionSetting = "ServiceBusConnection";
    private const string DestinationQueueSetting = "DestinationQueue";

    // Azure creates a new ExtensionFunction object for every request, so these live in static fields:
    // they are created once per Function instance, and BusinessLogic keeps its state between requests.
    private static readonly ExtensionRequestService RequestService = new(new BusinessLogic());
    private static readonly ServiceBusSender Sender = CreateSender();

    [Function("ExtensionFunction")]
    public async Task Run(
        [ServiceBusTrigger("%SourceQueue%", Connection = ConnectionSetting)] ServiceBusReceivedMessage message,
        CancellationToken cancellationToken)
    {
        var correlationId = message.CorrelationId;
        logger.LogInformation("Received request {CorrelationId}", correlationId);
        var reply = await RequestService.CreateReplyAsync(message.Body.ToString(), correlationId);
        await Sender.SendMessageAsync(
            new ServiceBusMessage(reply) { CorrelationId = correlationId, ContentType = "application/json" },
            cancellationToken);
        logger.LogInformation("Replied to request {CorrelationId}", correlationId);
    }

    private static ServiceBusSender CreateSender()
    {
        var connectionString = Environment.GetEnvironmentVariable(ConnectionSetting);
        var destinationQueue = Environment.GetEnvironmentVariable(DestinationQueueSetting);
        if (string.IsNullOrEmpty(connectionString) || string.IsNullOrEmpty(destinationQueue))
        {
            throw new InvalidOperationException($"Set the {ConnectionSetting} and {DestinationQueueSetting} app settings");
        }
        return new ServiceBusClient(connectionString).CreateSender(destinationQueue);
    }
}
