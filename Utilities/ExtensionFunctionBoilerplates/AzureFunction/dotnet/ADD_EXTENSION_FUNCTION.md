# Adding another extension function

Each extension function in H2R has its own pair of queues: H2R sends requests to a **source queue** and waits for
replies on a **destination queue**. This template has one function, `ExtensionFunction`, for one pair of queues.

If you need a second one (for example, one to assign a license and one to create a mailbox), you can add it to the
**same Function App**. You add two files and two settings. You do not change any existing file.

The steps below use **License** as the example name. Replace it with your own name everywhere.

| What | Existing function | New function (example) |
| --- | --- | --- |
| Business logic class | `BusinessLogic.cs` | `LicenseBusinessLogic.cs` |
| Function class | `ExtensionFunction.cs` | `LicenseFunction.cs` |
| Function name in Azure | `ExtensionFunction` | `LicenseFunction` |
| Source queue setting | `SourceQueue` | `LicenseSourceQueue` |
| Destination queue setting | `DestinationQueue` | `LicenseDestinationQueue` |
| Connection setting | `ServiceBusConnection` | `ServiceBusConnection` (shared) |

## Step 1: Create the business logic class

Create `LicenseBusinessLogic.cs` next to `BusinessLogic.cs`. Write the logic for the new function in `ProcessAsync`,
the same way as in `BusinessLogic.cs` (see [RUN_GUIDE.md section 3](RUN_GUIDE.md#3-writing-your-business-logic)).

```csharp
using System.Text.Json.Nodes;
using RoboMQ.Extension.Core;

namespace RoboMQ.Extension;

public class LicenseBusinessLogic : IExtensionLogic
{
    public LicenseBusinessLogic()
    {
        // One-time setup, done once at startup. Example: create your API client here.
    }

    public Task<JsonObject?> ProcessAsync(JsonObject request, string? correlationId)
    {
        // Write the logic for this function here.
        return Task.FromResult<JsonObject?>(null);
    }
}
```

## Step 2: Create the function class

Create `LicenseFunction.cs` next to `ExtensionFunction.cs`. This is a copy of `ExtensionFunction.cs` with the five
numbered names changed. Don't change anything else.

```csharp
using Azure.Messaging.ServiceBus;
using Microsoft.Azure.Functions.Worker;
using Microsoft.Extensions.Logging;
using RoboMQ.Extension.Core;

namespace RoboMQ.Extension;

public class LicenseFunction(ILogger<LicenseFunction> logger)                                  // 1. class name
{
    private const string ConnectionSetting = "ServiceBusConnection";
    private const string DestinationQueueSetting = "LicenseDestinationQueue";                 // 2. destination queue setting

    private static readonly ExtensionRequestService RequestService = new(new LicenseBusinessLogic());  // 3. your logic class
    private static readonly ServiceBusSender Sender = CreateSender();

    [Function("LicenseFunction")]                                                            // 4. function name
    public async Task Run(
        [ServiceBusTrigger("%LicenseSourceQueue%", Connection = ConnectionSetting)] ServiceBusReceivedMessage message,  // 5. source queue setting
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
```

Keep the `%` signs around the source queue setting name. They tell Azure to read the queue name from that setting.

## Step 3: Add the settings

Add the two new queue settings next to the existing ones in `local.settings.json`:

```json
"LicenseSourceQueue": "<Queue H2R sends license requests to>",
"LicenseDestinationQueue": "<Queue H2R reads license replies from>"
```

**In Azure**, after you deploy, run **Azure Functions: Upload Local Settings** from the Command Palette and pick your
Function App. It copies the new settings along with the existing ones.

The Shared Access Policy in `ServiceBusConnection` also needs **Listen** on the new source queue and **Send** on the
new destination queue. A namespace-level policy with Listen + Send already covers them.
If the new queues are in a different Service Bus namespace, also add a new connection setting, for example
`LicenseServiceBusConnection`, and set `ConnectionSetting` in `LicenseFunction.cs` to that name.

Every function in the app must have its settings. If a function's settings are missing, that function fails to start.

## Step 4: Run it

Start Azurite and press **F5**, as in [RUN_GUIDE.md section 7](RUN_GUIDE.md#7-run-on-your-machine).
The Terminal should now list both functions:

```
Functions:
        ExtensionFunction: serviceBusTrigger
        LicenseFunction: serviceBusTrigger
```

If `LicenseFunction` is missing, check that the class and the `[Function("...")]` name were renamed.

## Step 5: Test and deploy

1. Send a test request ([RUN_GUIDE.md section 8](RUN_GUIDE.md#8-send-a-test-request)) to the **new** source queue,
   and check the reply on the **new** destination queue.
2. Deploy as usual ([RUN_GUIDE.md section 10](RUN_GUIDE.md#10-update-after-a-change)). Both functions are deployed together.
3. Run **Azure Functions: Upload Local Settings** so Azure gets the new queue settings.

## Good to know

- Each function processes one message at a time (`maxConcurrentCalls` in `host.json` applies to each function separately).
- Each function has its own business logic object, so state in `LicenseBusinessLogic` is separate from state in `BusinessLogic`.
- To remove a function, delete its two files and its two settings.

## Standalone server

The standalone server runs one extension function per copy. To add another, copy the whole `Server/dotnet` folder
(for example, to `Server/dotnet-license`), write the new logic in its `BusinessLogic.cs`, put the new queues in its
`config/settings.json`, and run it as a second process.
