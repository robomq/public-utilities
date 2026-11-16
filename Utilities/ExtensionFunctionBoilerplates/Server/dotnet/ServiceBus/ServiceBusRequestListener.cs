using Azure.Messaging.ServiceBus;
using RoboMQ.Extension.Configuration;
using RoboMQ.Extension.Core;

namespace RoboMQ.Extension.ServiceBus;

/// <summary>
/// Receives requests from the source queue with peek-lock and sends each reply to the destination queue with the
/// request's CorrelationId (which is how H2R matches it).
/// The SDK completes a request when the handler returns and abandons it when it throws (the reply could not be sent);
/// abandoned requests are redelivered, then dead-lettered after the queue's max delivery count.
/// </summary>
public sealed class ServiceBusRequestListener : IAsyncDisposable
{
    private readonly ServiceBusClient _client;
    private readonly ServiceBusSender _sender;
    private readonly ServiceBusProcessor _processor;
    private readonly ExtensionRequestService _requestService;

    public ServiceBusRequestListener(FunctionConfig config, ExtensionRequestService requestService)
    {
        _requestService = requestService;
        _client = new ServiceBusClient(config.ConnectionString);
        _sender = _client.CreateSender(config.DestinationQueue);
        // Peek-lock and auto-complete are the defaults. One request at a time, so state kept in BusinessLogic is
        // never used by two requests at once.
        _processor = _client.CreateProcessor(config.SourceQueue, new ServiceBusProcessorOptions { MaxConcurrentCalls = 1 });
        _processor.ProcessMessageAsync += RespondToAsync;
        _processor.ProcessErrorAsync += LogErrorAsync;
    }

    public Task StartAsync() => _processor.StartProcessingAsync();

    /// <summary>Stops receiving first (an in-flight request finishes or goes back to the queue), then closes the connection.</summary>
    public async ValueTask DisposeAsync()
    {
        await _processor.StopProcessingAsync();
        await _processor.DisposeAsync();
        await _sender.DisposeAsync();
        await _client.DisposeAsync();
    }

    private async Task RespondToAsync(ProcessMessageEventArgs args)
    {
        var correlationId = args.Message.CorrelationId;
        Console.WriteLine($"Received request {correlationId}");
        var reply = await _requestService.CreateReplyAsync(args.Message.Body.ToString(), correlationId);
        await _sender.SendMessageAsync(
            new ServiceBusMessage(reply) { CorrelationId = correlationId, ContentType = "application/json" },
            args.CancellationToken);
        Console.WriteLine($"Replied to request {correlationId}");
    }

    // The processor recovers from connection and receive errors on its own, so logging is the only action needed.
    private static Task LogErrorAsync(ProcessErrorEventArgs args)
    {
        Console.Error.WriteLine($"Service Bus error ({args.ErrorSource}): {args.Exception}");
        return Task.CompletedTask;
    }
}
