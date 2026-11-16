# Adding another extension function

Each extension function in H2R has its own pair of queues: H2R sends requests to a **source queue** and waits for
replies on a **destination queue**. This template has one function, `ExtensionFunction`, for one pair of queues.

If you need a second one (for example, one to assign a license and one to create a mailbox), you can add it to the
**same Function App**. You add two files and two settings. You do not change any existing file.

The steps below use **License** as the example name. Replace it with your own name everywhere.

| What | Existing function | New function (example) |
| --- | --- | --- |
| Business logic class | `BusinessLogic.java` | `LicenseBusinessLogic.java` |
| Function class | `ExtensionFunction.java` | `LicenseFunction.java` |
| Function name in Azure | `ExtensionFunction` | `LicenseFunction` |
| Source queue setting | `SourceQueue` | `LicenseSourceQueue` |
| Destination queue setting | `DestinationQueue` | `LicenseDestinationQueue` |
| Connection setting | `ServiceBusConnection` | `ServiceBusConnection` (shared) |

## Step 1: Create the business logic class

Create `src/main/java/io/robomq/extension/LicenseBusinessLogic.java`. Write the logic for the new function in `process`.

```java
package io.robomq.extension;

import io.robomq.extension.core.ExtensionLogic;
import org.json.JSONObject;

public class LicenseBusinessLogic implements ExtensionLogic {

    public LicenseBusinessLogic() {
        // One-time setup, done once at startup. Example: create your API client here.
    }

    @Override
    public JSONObject process(JSONObject request, String correlationId) throws Exception {
        // Write the logic for this function here, the same way as in BusinessLogic.java.
        return null;
    }
}
```

## Step 2: Create the function class

Create `src/main/java/io/robomq/extension/LicenseFunction.java`. This is a copy of `ExtensionFunction.java`
with the five highlighted names changed. Don't change anything else.

```java
package io.robomq.extension;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.ServiceBusQueueTrigger;
import io.robomq.extension.core.ExtensionRequestService;
import java.util.logging.Logger;

public class LicenseFunction {                                                          // 1. class name
    private static final Logger LOG = Logger.getLogger(LicenseFunction.class.getName());
    private static final String CONNECTION_SETTING = "ServiceBusConnection";
    private static final String DESTINATION_QUEUE_SETTING = "LicenseDestinationQueue";  // 2. destination queue setting

    private static final ExtensionRequestService REQUEST_SERVICE =
            new ExtensionRequestService(new LicenseBusinessLogic());                    // 3. your logic class
    private static final ServiceBusSenderClient SENDER = createSender();

    @FunctionName("LicenseFunction")                                                    // 4. function name
    public void run(
            @ServiceBusQueueTrigger(name = "message", queueName = "%LicenseSourceQueue%",  // 5. source queue setting
                                    connection = CONNECTION_SETTING)
            String message,
            @BindingName("CorrelationId") String correlationId) {
        LOG.info("Received request " + correlationId);
        String reply = REQUEST_SERVICE.createReply(message, correlationId);
        SENDER.sendMessage(new ServiceBusMessage(reply).setCorrelationId(correlationId).setContentType("application/json"));
        LOG.info("Replied to request " + correlationId);
    }

    private static ServiceBusSenderClient createSender() {
        String connectionString = System.getenv(CONNECTION_SETTING);
        String destinationQueue = System.getenv(DESTINATION_QUEUE_SETTING);
        if (connectionString == null || destinationQueue == null) {
            throw new IllegalStateException("Set the " + CONNECTION_SETTING + " and " + DESTINATION_QUEUE_SETTING + " app settings");
        }
        return new ServiceBusClientBuilder().connectionString(connectionString)
                .sender().queueName(destinationQueue).buildClient();
    }
}
```

Keep the `%` signs around the source queue setting name. They tell Azure to read the queue name from that setting.

## Step 3: Add the settings

Add the two new queue settings next to the existing ones.

**Locally**, in `local.settings.json`:

```json
"LicenseSourceQueue": "<Queue H2R sends license requests to>",
"LicenseDestinationQueue": "<Queue H2R reads license replies from>"
```

**In Azure**, after you deploy, run **Azure Functions: Upload Local Settings** from the Command Palette and pick your
Function App. It copies the new settings along with the existing ones.

The Shared Access Policy in `ServiceBusConnection` also needs **Listen** on the new source queue and **Send** on the
new destination queue. A namespace-level policy with Listen + Send already covers them.
If the new queues are in a different Service Bus namespace, also add a new connection setting, for example `LicenseServiceBusConnection`,
and set `CONNECTION_SETTING` in `LicenseFunction.java` to that name.

Every function in the app must have its settings. If a function's queue setting is missing, that function fails to start.

## Step 4: Run it

Start Azurite and press **F5**, as in [RUN_GUIDE.md section 7](RUN_GUIDE.md#7-run-on-your-machine).
The Terminal should now list both functions:

```
Functions:
        ExtensionFunction: serviceBusTrigger
        LicenseFunction: serviceBusTrigger
```

If `LicenseFunction` is missing, check that the class and the `@FunctionName` were renamed and that the file is in
`src/main/java/io/robomq/extension/`.

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

The standalone server runs one extension function per copy. To add another, copy the whole `Server/java` folder
(for example, to `Server/java-license`), write the new logic in its `BusinessLogic.java`, put the new queues in its
`config/settings.json`, and run it as a second process.
