# Extension Function boilerplates

With **extensionFunction**, Hire2Retire (H2R) sends a JSON payload to an Azure Service Bus **source queue** and waits for the
result on a **destination queue**. Your code sits in between. It receives the request, runs your logic, and sends a reply
with the **same CorrelationId**.

These templates handle the queues and the reply. You only write your business logic in one function.

| Language | Azure Function | Standalone server (VM, on-prem, container) |
| --- | --- | --- |
| Java 17 | [AzureFunction/java](AzureFunction/java) | [Server/java](Server/java) |
| PowerShell 7 | – | [../UserExitFunctionBoilerPlateCode](../UserExitFunctionBoilerPlateCode) |

**Which one should I use?**
- **Azure Function**: no server to run. Azure receives the messages, retries failures, and dead-letters messages that keep failing.
  Settings come from the Function App's application settings (`local.settings.json` when run locally).
- **Standalone server**: use this when the logic has to run inside your network (for example, to reach on-prem AD). It is a long-running
  process that reads with peek-lock, so a message leaves the queue only after the reply is sent. Settings come from `config/settings.json`.

## Message contract

> This is what the current H2R integration and the PowerShell boilerplate use. Confirm it with the H2R team before go-live.

**Request** (H2R → source queue)
- Body: a JSON object. Your function receives it already parsed.
- `CorrelationId` (broker property): identifies the request. Your function receives it for logging.

**Reply** (your code → destination queue)
- `CorrelationId` is the request's CorrelationId. This is how H2R matches the reply. The templates set it for you.
- Content type `application/json`, with this body:

```json
{ "status": "success", "timestamp": "2026-11-16T10:00:00Z" }
{ "status": "success", "timestamp": "2026-11-16T10:00:00Z", "data": { "anything": "your function returned" } }
{ "status": "failure", "timestamp": "2026-11-16T10:00:00Z", "error_code": "500", "error_description": "what went wrong" }
```

| Your function... | Reply |
| --- | --- |
| returns null | `success` |
| returns a JSONObject | `success` with it in `data` |
| throws any exception | `failure`, `error_code` `"500"`, `error_description` = exception message |
| throws `BusinessLogicException("msg", "404")` | `failure` with `error_code` `"404"` |
| request body is not valid JSON | `failure`, `error_code` `"500"` |

A business-logic failure still produces a reply: H2R gets `failure`, and the request is completed.
The request goes back on the queue only when **the reply itself cannot be sent**. After the queue's *Max delivery count*,
it moves to the dead-letter queue.

## Service Bus rights

Use a Shared Access Policy with **Listen** on the source queue and **Send** on the destination queue.
A namespace-level policy with Listen + Send is the simplest.

## Running the templates

- [SERVICE_BUS_SETUP.md](SERVICE_BUS_SETUP.md): create the namespace, queues and access policy for testing
- [Server/java/RUN_GUIDE.md](Server/java/RUN_GUIDE.md): build, run, test and deploy the standalone worker
- [AzureFunction/java/RUN_GUIDE.md](AzureFunction/java/RUN_GUIDE.md): build, run locally, test and deploy the Azure Function

## End-to-end test

1. Run or deploy the template with your settings.
2. In the Azure portal, open the namespace → source queue → **Service Bus Explorer** → **Send messages**.
   Use content type `application/json`, a body such as `{"test": true}`, and under *Broker properties* set **Correlation Id** to `test-1`.
3. Open the destination queue → Service Bus Explorer → **Peek from start**. You should see a message with Correlation Id `test-1`
   and a `success` body.
4. Then run a real H2R extensionFunction call and check that H2R picks up the reply.
