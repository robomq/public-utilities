# Python Azure Function

An Azure Function (Python v2 programming model) that answers H2R extensionFunction requests. Azure receives each
request from the **source queue**, the function runs your business logic, and the reply goes to the
**destination queue** with the request's CorrelationId.

**Start here: [RUN_GUIDE.md](RUN_GUIDE.md).** It covers everything, using VS Code: where to write your code, how to
test it, run it on your machine, deploy it to Azure, update it, and troubleshoot it.

## At a glance

| | |
| --- | --- |
| Your code goes in | `business_logic.py`, function `process_request(request, correlation_id)` |
| Settings | `ServiceBusConnection`, `SourceQueue`, `DestinationQueue` (`local.settings.json` locally, app settings in Azure) |
| Tools | Python 3.12 (3.10–3.13), VS Code with the Azure Functions, Python and Azurite extensions |
| Message contract | [Top-level README](../../README.md#message-contract) |
| Several extension functions in one app | [ADD_EXTENSION_FUNCTION.md](ADD_EXTENSION_FUNCTION.md) |

## Files

| File | Purpose |
| --- | --- |
| `business_logic.py` | **Your logic goes here.** The only file you need to edit |
| `function_app.py` | The function Azure calls once per request; it sends the reply |
| `core/` | Builds the reply H2R expects (`create_reply`) and defines `BusinessLogicError`. No changes needed |
| `tests/` | Unit tests, run with `python -m unittest discover -s tests` |
| `requirements.txt` | Packages, pinned to exact versions |
| `local.settings.json.example` | Copy to `local.settings.json` for running on your machine |
| `host.json` | Azure Functions runtime settings, including one request at a time |
| `.funcignore` | Files that are not deployed (tests, docs, local settings, `.venv`) |

## Design notes

- Plain functions: customers write `process_request` and raise `BusinessLogicError` (the only class, because Python
  exceptions are classes). One-time setup and state live at the top level of `business_logic.py`.
- The reply is sent with the Service Bus SDK, not an output binding: an output binding only carries the message body
  and can't set the CorrelationId H2R needs.
- Azure completes a request when the reply was sent. If sending fails, the function raises, so Azure retries the
  request and dead-letters it after the queue's max delivery count. A failure inside the business logic is not an
  error for Azure: it becomes a `failure` reply to H2R.
- `business_logic.py` is loaded once per running copy of the function, and requests are handled one at a time
  (`maxConcurrentCalls: 1` in `host.json`). Its module-level variables are kept between requests, in memory only.
- `azure-functions` is pinned to 1.25.0, the newest release that installs on every Python version Azure Functions
  supports (3.10–3.13).
