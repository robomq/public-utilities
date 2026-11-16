# Node.js standalone worker

A long-running process for a VM, an on-prem server or a container. It receives H2R extensionFunction requests from the source queue
with peek-lock, runs your business logic, and replies on the destination queue with the same CorrelationId.
See the [top-level README](../../README.md) for the message contract, the Service Bus rights and the end-to-end test.

## Files
| File | Purpose |
| --- | --- |
| `src/businessLogic.js` | **Your logic goes here.** The only file you need to edit |
| `src/worker.js` | Entry point: reads the settings, starts listening, runs until stopped |
| `config/settings.json` | Connection and queue details |

You don't need to change anything in these folders:

| Folder | Purpose |
| --- | --- |
| `src/core` | Runs your logic and builds the success/failure reply H2R expects (`createReply`); defines `businessLogicError`. No Azure code |
| `src/servicebus` | Receives requests with peek-lock and sends each reply with the CorrelationId (`startRequestListener`) |
| `src/config` | Loads and checks `config/settings.json` (`loadFunctionConfig`) |

## Prerequisites
- Node.js 20+

## Configure
Fill in `config/settings.json`. It uses the same keys as the PowerShell and Java boilerplates:

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
```js
const client = createMyApiClient();                   // one-time setup: top-level code runs once at startup

async function processRequest(request, correlationId) {
  const email = request.email;                        // read a value from the request
  if (!email) {
    throw businessLogicError("Email is missing", "400");   // report a failure with your own code
  }
  const accountId = await client.createAccount(email);
  return { accountId };                               // optional: data sent back to H2R; or return null
}
```
Any other error is reported to H2R as a failure with error code `500`.

**Make `processRequest` safe to run twice for the same request.** If the reply can't be sent, Service Bus delivers the
request again, even though your logic already ran. For example, check whether the account already exists before creating it.

`businessLogic.js` is loaded once at startup, so variables you declare at its top level (clients, tokens, caches)
carry over between requests. Requests are processed one at a time.

## Build, test and run
Step by step, including deployment and troubleshooting: [RUN_GUIDE.md](RUN_GUIDE.md).

```sh
npm install        # once, and after changing package.json
npm test           # unit tests
npm start          # reads config/settings.json
node src/worker.js /path/settings.json   # or pass a path
```

## Deploy
Copy the folder (without `node_modules`) and `config/settings.json` to the server, run `npm ci --omit=dev`, and run
`node src/worker.js` as a service (systemd, a Windows service wrapper, or a container).
It stops cleanly on SIGTERM / Ctrl+C. A request in flight is released and redelivered.

How it behaves:
- One request at a time. You can raise `maxConcurrentCalls` in `src/servicebus/serviceBusRequestListener.js`, but then
  your business logic must handle several requests at once.
- Variables in `businessLogic.js` live in memory only. It is lost when the worker restarts.
- The SDK retries connection errors on its own and renews message locks for up to 5 minutes, so slow logic does not lose the request.
- If the reply cannot be sent, the request is abandoned and redelivered. It is dead-lettered after the queue's max delivery count.
