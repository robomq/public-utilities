# Adding another extension function

Each extension function in H2R has its own pair of queues: H2R sends requests to a **source queue** and waits for
replies on a **destination queue**. This template has one function, `ExtensionFunction`, for one pair of queues.

If you need a second one (for example, one to assign a license and one to create a mailbox), you can add it to the
**same Function App**. You add two files and two settings. You do not change any existing file: every file in
`src/functions/` is picked up automatically.

The steps below use **License** as the example name. Replace it with your own name everywhere.

| What | Existing function | New function (example) |
| --- | --- | --- |
| Business logic file | `src/businessLogic.js` | `src/licenseBusinessLogic.js` |
| Function file | `src/functions/extensionFunction.js` | `src/functions/licenseFunction.js` |
| Function name in Azure | `ExtensionFunction` | `LicenseFunction` |
| Source queue setting | `SourceQueue` | `LicenseSourceQueue` |
| Destination queue setting | `DestinationQueue` | `LicenseDestinationQueue` |
| Connection setting | `ServiceBusConnection` | `ServiceBusConnection` (shared) |

## Step 1: Create the business logic file

Create `src/licenseBusinessLogic.js`. Write the logic for the new function in `processRequest`, the same way as in
`businessLogic.js` (see [RUN_GUIDE.md section 3](RUN_GUIDE.md#3-writing-your-business-logic)).

```js
"use strict";

// eslint-disable-next-line no-unused-vars
const { businessLogicError } = require("./core/businessLogicError");

// One-time setup goes here, at the top level. Example: create your API client.

async function processRequest(request, correlationId) {
  // Write the logic for this function here.
  return null;
}

module.exports = { processRequest };
```

## Step 2: Create the function file

Create `src/functions/licenseFunction.js`. This is a copy of `extensionFunction.js` with the four numbered names
changed. Don't change anything else.

```js
"use strict";

const { app } = require("@azure/functions");
const { ServiceBusClient } = require("@azure/service-bus");
const { processRequest } = require("../licenseBusinessLogic");                        // 1. your logic file
const { createReply } = require("../core/reply");

const CONNECTION_SETTING = "ServiceBusConnection";
const DESTINATION_QUEUE_SETTING = "LicenseDestinationQueue";                          // 2. destination queue setting

const sender = createSender();

app.serviceBusQueue("LicenseFunction", {                                              // 3. function name
  connection: CONNECTION_SETTING,
  queueName: "%LicenseSourceQueue%",                                                  // 4. source queue setting
  handler: async (message, context) => {
    const correlationId = context.triggerMetadata.correlationId;
    context.log(`Received request ${correlationId}`);
    const reply = await createReply(processRequest, message, correlationId);
    await sender.sendMessages({ body: reply, correlationId, contentType: "application/json" });
    context.log(`Replied to request ${correlationId}`);
  },
});

function createSender() {
  const connectionString = process.env[CONNECTION_SETTING];
  const destinationQueue = process.env[DESTINATION_QUEUE_SETTING];
  if (!connectionString || !destinationQueue) {
    throw new Error(`Set the ${CONNECTION_SETTING} and ${DESTINATION_QUEUE_SETTING} app settings`);
  }
  return new ServiceBusClient(connectionString).createSender(destinationQueue);
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
`LicenseServiceBusConnection`, and set `CONNECTION_SETTING` in `licenseFunction.js` to that name.

Every function in the app must have its settings. If a function's settings are missing, that function fails to start.

## Step 4: Run it

Start Azurite and press **F5**, as in [RUN_GUIDE.md section 7](RUN_GUIDE.md#7-run-on-your-machine).
The Terminal should now list both functions:

```
Functions:
        ExtensionFunction: serviceBusTrigger
        LicenseFunction: serviceBusTrigger
```

If `LicenseFunction` is missing, check that the file is in `src/functions/` and that the function name was changed.

## Step 5: Test and deploy

1. Send a test request ([RUN_GUIDE.md section 8](RUN_GUIDE.md#8-send-a-test-request)) to the **new** source queue,
   and check the reply on the **new** destination queue.
2. Deploy as usual ([RUN_GUIDE.md section 10](RUN_GUIDE.md#10-update-after-a-change)). Both functions are deployed together.
3. Run **Azure Functions: Upload Local Settings** so Azure gets the new queue settings.

## Good to know

- Each function processes one message at a time (`maxConcurrentCalls` in `host.json` applies to each function separately).
- Each function has its own business logic file, so variables in `licenseBusinessLogic.js` are separate from those in `businessLogic.js`.
- To remove a function, delete its two files and its two settings.

## Standalone server

The standalone server runs one extension function per copy. To add another, copy the whole `Server/node` folder
(for example, to `Server/node-license`), write the new logic in its `src/businessLogic.js`, put the new queues in its
`config/settings.json`, and run it as a second process.
