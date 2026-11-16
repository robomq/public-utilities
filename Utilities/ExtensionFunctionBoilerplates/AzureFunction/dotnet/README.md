# C# (.NET) Azure Function

An Azure Function (.NET 10, isolated worker) that answers H2R extensionFunction requests. Azure receives each request
from the **source queue**, the function runs your business logic, and the reply goes to the **destination queue** with
the request's CorrelationId.

**Start here: [RUN_GUIDE.md](RUN_GUIDE.md).** It covers everything, using VS Code: where to write your code, how to
test it, run it on your machine, deploy it to Azure, update it, and troubleshoot it.

## At a glance

| | |
| --- | --- |
| Your code goes in | `BusinessLogic.cs`, method `ProcessAsync(request, correlationId)` |
| Settings | `ServiceBusConnection`, `SourceQueue`, `DestinationQueue` (`local.settings.json` locally, app settings in Azure) |
| Tools | .NET 10 SDK, VS Code with the Azure Functions, C# Dev Kit and Azurite extensions |
| Message contract | [Top-level README](../../README.md#message-contract) |
| Several extension functions in one app | [ADD_EXTENSION_FUNCTION.md](ADD_EXTENSION_FUNCTION.md) |

## Files

| File | Purpose |
| --- | --- |
| `BusinessLogic.cs` | **Your logic goes here.** The only file you need to edit |
| `ExtensionFunction.cs` | The function Azure calls once per request; it sends the reply |
| `Program.cs` | Starts the isolated worker. No changes needed |
| `Core/` | Runs your logic and builds the reply H2R expects (`ExtensionRequestService`); defines `IExtensionLogic` and `BusinessLogicException`. No changes needed |
| `tests/` | Unit tests (xUnit), run with `dotnet test tests` or VS Code's Testing view |
| `local.settings.json.example` | Copy to `local.settings.json` for running on your machine |
| `host.json` | Azure Functions runtime settings, including one request at a time |

## Design notes

- The reply is sent with the Service Bus SDK (`ServiceBusSender`) so the CorrelationId H2R needs is always set. The
  same SDK is used for the reply in every language template.
- Azure completes a request when the reply was sent. If sending fails, the function throws, so Azure retries the
  request and dead-letters it after the queue's max delivery count. A failure inside the business logic is not an
  error for Azure: it becomes a `failure` reply to H2R.
- `BusinessLogic` is created once per running copy of the function (a static field, because Azure creates a new
  `ExtensionFunction` object for every request) and handles one request at a time (`maxConcurrentCalls: 1` in
  `host.json`). Its fields are kept between requests, in memory only.
