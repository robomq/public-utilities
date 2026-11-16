# Java Azure Function

An Azure Function that answers H2R extensionFunction requests. Azure receives each request from the **source queue**,
the function runs your business logic, and the reply goes to the **destination queue** with the request's CorrelationId.

**Start here: [RUN_GUIDE.md](RUN_GUIDE.md).** It covers everything, using VS Code: where to write your code, how to
test it, run it on your machine, deploy it to Azure, update it, and troubleshoot it.

## At a glance

| | |
| --- | --- |
| Your code goes in | `src/main/java/io/robomq/extension/BusinessLogic.java`, method `process(request, correlationId)` |
| Settings | `ServiceBusConnection`, `SourceQueue`, `DestinationQueue` (`local.settings.json` locally, app settings in Azure) |
| Tools | JDK 17+, Maven, VS Code with the Azure Functions, Java and Azurite extensions |
| Message contract | [Top-level README](../../README.md#message-contract) |
| Several extension functions in one app | [ADD_EXTENSION_FUNCTION.md](ADD_EXTENSION_FUNCTION.md) |

## Files

| File | Purpose |
| --- | --- |
| `src/main/java/io/robomq/extension/BusinessLogic.java` | **Your logic goes here.** The only file you need to edit |
| `src/main/java/io/robomq/extension/ExtensionFunction.java` | The function Azure calls once per request; it sends the reply |
| `src/main/java/io/robomq/extension/core/` | Runs your logic and builds the reply H2R expects (`ExtensionRequestService`); defines `ExtensionLogic` and `BusinessLogicException`. No changes needed |
| `local.settings.json.example` | Copy to `local.settings.json` for running on your machine |
| `host.json` | Azure Functions runtime settings, including one request at a time |

## Design notes

- The reply is sent with the Service Bus SDK, not an output binding: an output binding only carries the message body
  and can't set the CorrelationId H2R needs.
- Azure completes a request when the reply was sent. If sending fails, the function throws, so Azure retries the
  request and dead-letters it after the queue's max delivery count. A failure inside the business logic is not an
  error for Azure: it becomes a `failure` reply to H2R.
- `BusinessLogic` is created once per running copy of the function and handles one request at a time
  (`maxConcurrentCalls: 1` in `host.json`). Its fields are kept between requests, in memory only.
