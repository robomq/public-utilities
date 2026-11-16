# Running the C# (.NET) standalone worker

How to build, configure, run, test and deploy the worker after you change it. For what the worker does and the
message contract, see the [README](README.md) and the [top-level README](../../README.md).

**The usual loop after a change:**

```sh
dotnet test tests     # unit tests
dotnet run            # run; send a test request (step 5), check the reply, Ctrl+C to stop
```

## 1. Prerequisites

| Tool | Version | Check |
| --- | --- | --- |
| .NET SDK | 10 | `dotnet --list-sdks` |
| Azure Service Bus | a namespace, two queues, one policy with Listen + Send | see [SERVICE_BUS_SETUP.md](../../SERVICE_BUS_SETUP.md) |

Install the .NET 10 SDK from [dotnet.microsoft.com](https://dotnet.microsoft.com/download/dotnet/10.0), or:
- **macOS:** `brew install --cask dotnet-sdk@10`
- **Windows:** `winget install Microsoft.DotNet.SDK.10`
- **Linux:** follow [Install .NET on Linux](https://learn.microsoft.com/dotnet/core/install/linux)

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
- The file is copied next to the program when you build, so **build again after editing it** (`dotnet run` does this for you).
- The worker checks the file at startup. If a value is empty or still a `<placeholder>`, it stops and names every
  value that needs filling in.
- The file holds a secret. Don't commit it with a real key.

## 3. Run the unit tests

```sh
dotnet test tests
```

This builds the worker and the test project and runs every test. A failed test shows the expected and actual values.
In VS Code with the **C# Dev Kit** extension, the **Testing** view (flask icon) runs the same tests.

### Testing your own business logic

You can unit-test `BusinessLogic` without Service Bus by calling it through `ExtensionRequestService`, the same way
the worker does. Add `tests/BusinessLogicTests.cs`:

```csharp
using System.Text.Json.Nodes;
using RoboMQ.Extension;
using RoboMQ.Extension.Core;
using Xunit;

public class BusinessLogicTests
{
    [Fact]
    public async Task ValidRequestSucceeds()
    {
        var reply = await new ExtensionRequestService(new BusinessLogic())
            .CreateReplyAsync("""{"email":"test.user@example.com"}""", "test-1");
        Assert.Equal("success", (string?)JsonNode.Parse(reply)!["status"]);
    }
}
```

`dotnet test tests` runs it with the other tests.

## 4. Run locally

```sh
dotnet run
```

The worker reads `config/settings.json` from its build folder (a copy of the one in the project). To use a file
somewhere else, pass its path:

```sh
dotnet run -- /path/to/settings.json
```

When it is ready, it logs:

```
Listening on extension-requests, replying to extension-replies
```

It keeps running until you press **Ctrl+C**. On Ctrl+C it stops taking new requests, lets the current one finish or
go back to the queue, and then exits.

**Debugging in VS Code:** with the **C# Dev Kit** extension, open the project folder, set a breakpoint in
`BusinessLogic.cs`, and press **F5**.

## 5. Test end to end

Follow [Send a test request and read the reply](../../SERVICE_BUS_SETUP.md#send-a-test-request-and-read-the-reply).
While the worker runs, its console shows:

```
Received request test-1
Replied to request test-1
```

Also try a failure: send a body that is not JSON, such as `hello`. The reply should have `"status": "failure"`
and `"error_code": "500"`. If your business logic throws `BusinessLogicException`, the reply should carry your error code.

Finally, run a real extension function call from H2R and check that H2R picks up the reply.

## 6. Logs

The worker logs to the console (`Console.WriteLine` / `Console.Error.WriteLine`). Each request logs its CorrelationId
when it arrives and when the reply is sent; business-logic failures are logged with the exception. Message bodies are
never logged.

To keep the logs in a file:

```sh
dotnet run >> worker.log 2>&1
```

## 7. Deploy

```sh
dotnet publish -c Release -o out
```

`out/` contains `ExtensionWorker.dll` and `config/settings.json`. Copy it to the server, put the real values in
`out/config/settings.json`, and install the **.NET 10 runtime** on the server. Then run the worker as a service so it
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
ExecStart=/usr/bin/dotnet /opt/extension-worker/ExtensionWorker.dll
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

Make the settings file readable only by the service user:
`chmod 600 /opt/extension-worker/config/settings.json`.

### Windows

Run it as a Windows service with a service wrapper such as NSSM or WinSW, pointing it at
`dotnet <path>\ExtensionWorker.dll`.

### Container

```dockerfile
FROM mcr.microsoft.com/dotnet/runtime:10.0
WORKDIR /app
COPY out/ ./
CMD ["dotnet", "ExtensionWorker.dll"]
```

Mount the settings file instead of baking the key into the image:

```sh
dotnet publish -c Release -o out
docker build -t extension-worker .
docker run -d --restart unless-stopped -v /secure/path/settings.json:/app/config/settings.json:ro extension-worker
```

### Updating a running worker

1. `dotnet test tests` (the tests must pass).
2. `dotnet publish -c Release -o out` and copy `out/` to the server, **keeping the server's own `config/settings.json`**.
3. Restart the service (`sudo systemctl restart extension-worker`, or rebuild and restart the container).

A request being processed during the restart goes back to the queue and is picked up again after the restart.

## 8. Troubleshooting

| Symptom | Likely cause | What to do |
| --- | --- | --- |
| `.../config/settings.json: fill in ...` at startup | Values missing or still placeholders | Fill in the values it names; build again |
| Changed `settings.json` but the worker still uses old values | The build folder has an old copy | Run `dotnet run` (it rebuilds), or edit the copy next to the published program |
| `NETSDK1045` / "does not support targeting .NET 10.0" | Older .NET SDK | Install the .NET 10 SDK |
| `You must install or update .NET to run this application` | .NET 10 runtime missing on the server | Install the .NET 10 runtime |
| `Unauthorized` / `401` in the logs | Wrong key or policy name, or the policy lacks Listen/Send | Check `keyName` and `key`; give the policy Listen and Send |
| `MessagingEntityNotFound` | Queue name typo, or the queue is in another namespace | Check `sourceQueue`, `destinationQueue` and `namespace` |
| Worker runs but never logs `Received request` | Messages go to a different queue | Check that the test message (or H2R) uses `sourceQueue` |
| Requests end up in the dead-letter queue | The reply could not be sent, again and again | Check Send rights on the destination queue and that it exists; see the error in the logs |
| H2R doesn't get the reply | Reply queue mismatch, or H2R timed out | Check `destinationQueue` matches H2R's reply queue; check that the logic finishes within H2R's timeout |
