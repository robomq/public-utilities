# Node.js Azure Function

An Azure Function that answers H2R extensionFunction requests. Azure receives each request from the **source queue**,
the function runs your business logic, and the reply goes to the **destination queue** with the request's CorrelationId.

**Start here: [RUN_GUIDE.md](RUN_GUIDE.md).** It covers everything, using VS Code: where to write your code, how to
test it, run it on your machine, deploy it to Azure, update it, and troubleshoot it.

## At a glance

| | |
| --- | --- |
| Your code goes in | `src/businessLogic.js`, function `async processRequest(request, correlationId)` |
| Settings | `ServiceBusConnection`, `SourceQueue`, `DestinationQueue` (`local.settings.json` locally, app settings in Azure) |
| Tools | Node.js 20+, VS Code with the Azure Functions and Azurite extensions |
| Message contract | [Top-level README](../../README.md#message-contract) |
| Several extension functions in one app | [ADD_EXTENSION_FUNCTION.md](ADD_EXTENSION_FUNCTION.md) |

## Files

| File | Purpose |
| --- | --- |
| `src/businessLogic.js` | **Your logic goes here.** The only file you need to edit |
| `src/functions/extensionFunction.js` | The function Azure calls once per request; it sends the reply |
| `src/core/` | Runs your logic and builds the reply H2R expects (`createReply`); defines `businessLogicError`. No changes needed |
| `test/` | Unit tests, run with `npm test` |
| `local.settings.json.example` | Copy to `local.settings.json` for running on your machine |
| `host.json` | Azure Functions runtime settings, including one request at a time |
| `.funcignore` | Files that are not deployed (tests, docs, local settings) |

## Design notes

- Uses the Azure Functions Node.js v4 programming model: every file in `src/functions/` registers its own function.
- The reply is sent with the Service Bus SDK, not an output binding: an output binding only carries the message body
  and can't set the CorrelationId H2R needs.
- Azure completes a request when the reply was sent. If sending fails, the function throws, so Azure retries the
  request and dead-letters it after the queue's max delivery count. A failure inside the business logic is not an
  error for Azure: it becomes a `failure` reply to H2R.
- `businessLogic.js` is loaded once per running copy of the function, and requests are handled one at a time
  (`maxConcurrentCalls: 1` in `host.json`). Its top-level variables are kept between requests, in memory only.
