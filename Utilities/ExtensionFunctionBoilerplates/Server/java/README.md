# Java standalone worker

A long-running process for a VM, an on-prem server or a container. It receives H2R extensionFunction requests from the source queue
with peek-lock, runs your business logic, and replies on the destination queue with the same CorrelationId.
See the [top-level README](../../README.md) for the message contract, the Service Bus rights and the end-to-end test.

## Files
| File | Purpose |
| --- | --- |
| `src/main/java/io/robomq/extension/BusinessLogic.java` | **Your logic goes here.** The only file you need to edit |
| `src/main/java/io/robomq/extension/Worker.java` | Entry point: reads the settings, connects the parts, listens until stopped |
| `config/settings.json` | Connection and queue details |

You don't need to change anything in these packages:

| Package | Purpose |
| --- | --- |
| `core` | Runs your logic and builds the success/failure reply H2R expects (`ExtensionRequestService`); defines `ExtensionLogic` and `BusinessLogicException`. No Azure code |
| `servicebus` | Receives requests with peek-lock and sends each reply with the CorrelationId (`ServiceBusRequestListener`) |
| `config` | Loads and checks `config/settings.json` (`FunctionConfig`) |

## Prerequisites
- JDK 17+
- Maven 3.8+

## Configure
Fill in `config/settings.json`. It uses the same keys as the PowerShell boilerplate:

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
```java
public class BusinessLogic implements ExtensionLogic {
    private final MyApiClient client;   // fields are kept between messages

    public BusinessLogic() {
        client = new MyApiClient();     // one-time setup, done once at startup
    }

    @Override
    public JSONObject process(JSONObject request, String correlationId) throws Exception {
        String email = request.getString("email");          // read a value from the request

        if (email.isEmpty()) {
            throw new BusinessLogicException("Email is missing", "400");   // report a failure with your own code
        }

        JSONObject data = new JSONObject();                 // optional: send data back to H2R
        data.put("accountId", client.createAccount(email));
        return data;                                        // or return null if there is nothing to send back
    }
}
```
Any other exception is reported to H2R as a failure with error code `500`.

**Make `process` safe to run twice for the same request.** If the reply can't be sent, Service Bus delivers the
request again, even though your logic already ran. For example, check whether the account already exists before creating it.

One `BusinessLogic` object is created at startup and reused for every message, so values you keep in its fields
(clients, tokens, caches) carry over between messages. Messages are processed one at a time, so you don't need to
worry about two messages changing those fields at once.

## Build, test and run
Step by step, including deployment and troubleshooting: [RUN_GUIDE.md](RUN_GUIDE.md).

```sh
mvn package        # compiles, runs the unit tests, builds the runnable jar
java -jar target/extension-function-worker-1.0.0-jar-with-dependencies.jar                  # reads ./config/settings.json
java -jar target/extension-function-worker-1.0.0-jar-with-dependencies.jar /path/settings.json   # or pass a path
```

## Deploy
Copy the jar and `config/settings.json` to the server. Run the jar as a service (systemd, a Windows service wrapper, or a container).
It stops cleanly on SIGTERM / Ctrl+C. Messages in flight are released and redelivered.

How it behaves:
- One message at a time. You can raise `maxConcurrentCalls` in `servicebus/ServiceBusRequestListener.java`, but then `BusinessLogic` must be thread-safe, because several messages will use it at once.
- State in `BusinessLogic` lives in memory only. It is lost when the worker restarts.
- The SDK retries connection errors on its own and renews message locks for up to 5 minutes, so slow logic does not lose the message.
- If the reply cannot be sent, the request is abandoned and redelivered. It is dead-lettered after the queue's max delivery count.
