# Adding another extension function

Each extension function in H2R has its own pair of queues: H2R sends requests to a **source queue** and waits for
replies on a **destination queue**. This template has one function, `ExtensionFunction`, for one pair of queues.

If you need a second one (for example, one to assign a license and one to create a mailbox), you can add it to the
**same Function App**. You add one file, add a block to `function_app.py`, and add two settings. (In the Python v2
programming model, every function in the app is registered in `function_app.py`.)

The steps below use **License** as the example name. Replace it with your own name everywhere.

| What | Existing function | New function (example) |
| --- | --- | --- |
| Business logic file | `business_logic.py` | `license_business_logic.py` |
| Function in `function_app.py` | `extension_function` | `license_function` |
| Function name in Azure | `ExtensionFunction` | `LicenseFunction` |
| Source queue setting | `SourceQueue` | `LicenseSourceQueue` |
| Destination queue setting | `DestinationQueue` | `LicenseDestinationQueue` |
| Connection setting | `ServiceBusConnection` | `ServiceBusConnection` (shared) |

## Step 1: Create the business logic file

Create `license_business_logic.py` next to `business_logic.py`. Write the logic for the new function in
`process_request`, the same way as in `business_logic.py` (see [RUN_GUIDE.md section 3](RUN_GUIDE.md#3-writing-your-business-logic)).

```python
"""Business logic for LicenseFunction."""

from core.business_logic_error import BusinessLogicError  # noqa: F401  (raise it to choose the error code)

# One-time setup goes here, at the top level. Example: create your API client.


def process_request(request, correlation_id):
    # Write the logic for this function here.
    return None
```

## Step 2: Add the function to `function_app.py`

Add this block at the **end** of `function_app.py`. It is a copy of the existing function with the six numbered names
changed. Don't change anything else.

```python
from license_business_logic import process_request as process_license_request         # 1. your logic file

LICENSE_DESTINATION_QUEUE_SETTING = "LicenseDestinationQueue"                          # 2. destination queue setting
_license_sender = _create_sender(LICENSE_DESTINATION_QUEUE_SETTING)


@app.function_name("LicenseFunction")                                                  # 3. function name
@app.service_bus_queue_trigger(arg_name="message", queue_name="%LicenseSourceQueue%",  # 4. source queue setting
                               connection=CONNECTION_SETTING)
def license_function(message: func.ServiceBusMessage) -> None:                         # 5. Python function name
    correlation_id = message.correlation_id
    logging.info("Received request %s", correlation_id)
    reply = create_reply(process_license_request, message.get_body(), correlation_id)  # 6. your logic function
    _license_sender.send_messages(ServiceBusMessage(reply, correlation_id=correlation_id, content_type="application/json"))
    logging.info("Replied to request %s", correlation_id)
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

Every function in the app must have its settings. Because all functions live in `function_app.py`, a missing setting
for **any** function stops the **whole** app from loading, with an error naming the missing setting.

## Step 4: Run it

Start Azurite and press **F5**, as in [RUN_GUIDE.md section 7](RUN_GUIDE.md#7-run-on-your-machine).
The Terminal should now list both functions:

```
Functions:
        ExtensionFunction: serviceBusTrigger
        LicenseFunction: serviceBusTrigger
```

If `LicenseFunction` is missing, check that the block is at the end of `function_app.py` and that the
`@app.function_name` was changed.

## Step 5: Test and deploy

1. Send a test request ([RUN_GUIDE.md section 8](RUN_GUIDE.md#8-send-a-test-request)) to the **new** source queue,
   and check the reply on the **new** destination queue.
2. Deploy as usual ([RUN_GUIDE.md section 10](RUN_GUIDE.md#10-update-after-a-change)). Both functions are deployed together.
3. Run **Azure Functions: Upload Local Settings** so Azure gets the new queue settings.

## Good to know

- Each function processes one message at a time (`maxConcurrentCalls` in `host.json` applies to each function separately).
- Each function has its own business logic file, so variables in `license_business_logic.py` are separate from those in `business_logic.py`.
- To remove a function, delete its file, its block in `function_app.py`, and its two settings.

## Standalone server

The standalone server runs one extension function per copy. To add another, copy the whole `Server/python` folder
(for example, to `Server/python-license`), write the new logic in its `business_logic.py`, put the new queues in its
`config/settings.json`, and run it as a second process.
