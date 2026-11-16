# C# (.NET) standalone worker

A long-running .NET 10 process for a VM, an on-prem server or a container. It receives H2R extensionFunction requests
from the source queue with peek-lock, runs your business logic, and replies on the destination queue with the same
CorrelationId. See the [top-level README](../../README.md) for the message contract, the Service Bus rights and the
end-to-end test.

## Files
| File | Purpose |
| --- | --- |
| `BusinessLogic.cs` | **Your logic goes here.** The only file you need to edit |
| `Program.cs` | Entry point: reads the settings, starts listening, runs until stopped |
| `config/settings.json` | Connection and queue details |
| `tests/` | Unit tests (xUnit), run with `dotnet test tests` |

You don't need to change anything in these folders:

| Folder | Purpose |
| --- | --- |
| `Core` | Runs your logic and builds the success/failure reply H2R expects (`ExtensionRequestService`); defines `IExtensionLogic` and `BusinessLogicException`. No Azure code |
| `ServiceBus` | Receives requests with peek-lock and sends each reply with the CorrelationId (`ServiceBusRequestListener`) |
| `Configuration` | Loads and checks `config/settings.json` (`FunctionConfig`) |

## Prerequisites
- .NET 10 SDK

## Configure
Fill in `config/settings.json`. It uses the same keys as the PowerShell, Java and Node boilerplates:

```json
{
  "namespace": "<Service Bus Namespace, without .servicebus.windows.net>",
  "keyName": "<Shared Access Policy name>",
  "key": "<Shared Access Policy primary key>",
  "sourceQueue": "<Queue H2R sends requests to>",
  "destinationQueue": "<Queue H2R reads replies from>"
}
```

The file holds a secret. Restrict it to the account that runs the worker, and don't commit it with a real key.
The worker stops at startup and names any value that is still empty or a `<placeholder>`.

## Write your logic
```csharp
public class BusinessLogic : IExtensionLogic
{
    private readonly MyApiClient _client;                       // fields are kept between requests

    public BusinessLogic()
    {
        _client = new MyApiClient();                            // one-time setup, done once at startup
    }

    public async Task<JsonObject?> ProcessAsync(JsonObject request, string? correlationId)
    {
        var email = request["email"]?.GetValue<string>();       // read a value from the request
        if (string.IsNullOrEmpty(email))
        {
            throw new BusinessLogicException("Email is missing", "400");   // report a failure with your own code
        }
        var accountId = await _client.CreateAccountAsync(email);
        return new JsonObject { ["accountId"] = accountId };    // optional: data sent back to H2R; or return null
    }
}
```
Any other exception is reported to H2R as a failure with error code `500`.

**Make `ProcessAsync` safe to run twice for the same request.** If the reply can't be sent, Service Bus delivers the
request again, even though your logic already ran. For example, check whether the account already exists before creating it.

One `BusinessLogic` object is created at startup and reused for every request, so values you keep in its fields
(clients, tokens, caches) carry over between requests. Requests are processed one at a time.

## Build, test and run
Step by step, including deployment and troubleshooting: [RUN_GUIDE.md](RUN_GUIDE.md).

```sh
dotnet test tests            # unit tests
dotnet run                   # reads config/settings.json
dotnet run -- /path/settings.json   # or pass a path
```

## Deploy
`dotnet publish -c Release -o out`, copy `out/` to the server (it includes `config/settings.json`), and run
`dotnet ExtensionWorker.dll` as a service (systemd, a Windows service wrapper, or a container).
It stops cleanly on SIGTERM / Ctrl+C. A request in flight is released and redelivered.

How it behaves:
- One request at a time. You can raise `MaxConcurrentCalls` in `ServiceBus/ServiceBusRequestListener.cs`, but then
  `BusinessLogic` must be thread-safe, because several requests will use it at once.
- State in `BusinessLogic` lives in memory only. It is lost when the worker restarts.
- The SDK retries connection errors on its own and renews message locks for up to 5 minutes, so slow logic does not lose the request.
- If the reply cannot be sent, the request is abandoned and redelivered. It is dead-lettered after the queue's max delivery count.
