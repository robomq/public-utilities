# Running the Java standalone worker

How to build, configure, run, test and deploy the worker after you change it. For what the worker does and the
message contract, see the [README](README.md) and the [top-level README](../../README.md).

**The usual loop after a change:**

```sh
mvn package                                                          # compile + unit tests + runnable jar
java -jar target/extension-function-worker-1.0.0-jar-with-dependencies.jar   # run
# send a test request (step 5), check the reply, Ctrl+C to stop
```

## 1. Prerequisites

| Tool | Version | Check |
| --- | --- | --- |
| JDK | 17 or newer | `java -version` |
| Maven | 3.8 or newer | `mvn -v` |
| Azure Service Bus | a namespace, two queues, one policy with Listen + Send | see [SERVICE_BUS_SETUP.md](../../SERVICE_BUS_SETUP.md) |

Install the JDK and Maven:
- **macOS:** `brew install openjdk@17 maven`
- **Windows:** `winget install Microsoft.OpenJDK.17` and `winget install Apache.Maven`
- **Linux (Debian/Ubuntu):** `sudo apt install openjdk-17-jdk maven`

## 2. Configure

Edit `config/settings.json`. Every value is required:

```json
{
  "namespace": "sb-extension-test",
  "keyName": "extension-function",
  "key": "<primary key of the policy>",
  "sourceQueue": "extension-requests",
  "destinationQueue": "extension-replies"
}
```

- `namespace` is the name only, without `.servicebus.windows.net`.
- The worker checks the file at startup. If a value is empty or still a `<placeholder>`, it stops and names every
  value that needs filling in.
- The file holds a secret. Don't commit it with a real key.

## 3. Build and run the unit tests

```sh
mvn package
```

This compiles the code, runs the unit tests, and builds
`target/extension-function-worker-1.0.0-jar-with-dependencies.jar`. If a test fails, the build stops and Maven
prints which test failed and why. Detailed results are in `target/surefire-reports/`.

To run only the tests: `mvn test`.

### Testing your own business logic

You can unit-test `BusinessLogic` without Service Bus by calling it through `ExtensionRequestService`, the same way
the worker does. Add a file such as `src/test/java/io/robomq/extension/BusinessLogicTest.java`:

```java
package io.robomq.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.robomq.extension.core.ExtensionRequestService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class BusinessLogicTest {
    @Test
    void validRequestSucceeds() {
        String reply = new ExtensionRequestService(new BusinessLogic())
                .createReply("{\"email\":\"test.user@example.com\"}", "test-1");
        assertEquals("success", new JSONObject(reply).getString("status"));
    }
}
```

`mvn package` runs it with the other tests.

## 4. Run locally

```sh
java -jar target/extension-function-worker-1.0.0-jar-with-dependencies.jar
```

The worker reads `config/settings.json` from the **current directory**, so run it from the project folder. To use a
file somewhere else, pass its path:

```sh
java -jar target/extension-function-worker-1.0.0-jar-with-dependencies.jar /path/to/settings.json
```

When it is ready, it logs:

```
INFO: Listening on extension-requests, replying to extension-replies
```

It keeps running until you press **Ctrl+C**. On Ctrl+C it stops taking new requests, lets the current one finish or
go back to the queue, and then exits.

## 5. Test end to end

Follow [Send a test request and read the reply](../../SERVICE_BUS_SETUP.md#send-a-test-request-and-read-the-reply).
While the worker runs, its console shows:

```
INFO: Received request test-1
INFO: Replied to request test-1
```

Also try a failure: send a body that is not JSON, such as `hello`. The reply should have `"status": "failure"`
and `"error_code": "500"`. If your business logic throws `BusinessLogicException`, the reply should carry your error code.

Finally, run a real extension function call from H2R and check that H2R picks up the reply.

## 6. Logs

The worker logs to the console with `java.util.logging`. Each request logs its CorrelationId when it arrives and
when the reply is sent; business-logic failures are logged with the stack trace. Message bodies are never logged.

To keep the logs in a file:

```sh
java -jar target/extension-function-worker-1.0.0-jar-with-dependencies.jar >> worker.log 2>&1
```

## 7. Deploy

Copy two things to the server: the jar (`target/extension-function-worker-1.0.0-jar-with-dependencies.jar`) and
`config/settings.json` with the real values. Install Java 17+ on the server. Then run the worker as a service so it
starts on boot and restarts after a crash.

### Linux (systemd)

`/etc/systemd/system/extension-worker.service`:

```ini
[Unit]
Description=H2R extension function worker
After=network-online.target

[Service]
User=extension
WorkingDirectory=/opt/extension-worker
ExecStart=/usr/bin/java -jar /opt/extension-worker/extension-function-worker-1.0.0-jar-with-dependencies.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```sh
sudo systemctl daemon-reload
sudo systemctl enable --now extension-worker
sudo journalctl -u extension-worker -f        # follow the logs
```

`WorkingDirectory` must contain `config/settings.json`. Make the file readable only by the service user:
`chmod 600 /opt/extension-worker/config/settings.json`.

### Windows

Run it as a Windows service with a service wrapper such as NSSM or WinSW, pointing it at
`java -jar <path>\extension-function-worker-1.0.0-jar-with-dependencies.jar` with the folder that contains
`config\settings.json` as the working directory.

### Container

```dockerfile
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/extension-function-worker-1.0.0-jar-with-dependencies.jar worker.jar
CMD ["java", "-jar", "worker.jar"]
```

Mount the settings file instead of baking the key into the image:

```sh
docker build -t extension-worker .
docker run -d --restart unless-stopped -v /secure/path/settings.json:/app/config/settings.json:ro extension-worker
```

### Updating a running worker

1. `mvn package` (the tests must pass).
2. Copy the new jar over the old one.
3. Restart the service (`sudo systemctl restart extension-worker`, or restart the container).

A request being processed during the restart goes back to the queue and is picked up again after the restart.

## 8. Troubleshooting

| Symptom | Likely cause | What to do |
| --- | --- | --- |
| `config/settings.json: fill in ...` at startup | Values missing or still placeholders | Fill in the values it names |
| `NoSuchFileException: config/settings.json` | Started from another folder | Run from the project folder, or pass the file path as an argument |
| `Unauthorized` / `401` in the logs | Wrong key or policy name, or the policy lacks Listen/Send | Check `keyName` and `key`; give the policy Listen and Send |
| `MessagingEntityNotFound` | Queue name typo, or the queue is in another namespace | Check `sourceQueue`, `destinationQueue` and `namespace` |
| Worker runs but never logs `Received request` | Messages go to a different queue | Check that the test message (or H2R) uses `sourceQueue` |
| Requests end up in the dead-letter queue | The reply could not be sent, again and again | Check Send rights on the destination queue and that it exists; see the error in the logs |
| H2R doesn't get the reply | Reply queue mismatch, or H2R timed out | Check `destinationQueue` matches H2R's reply queue; check that the logic finishes within H2R's timeout |
| `UnsupportedClassVersionError` | Java older than 17 | Install Java 17+ |
