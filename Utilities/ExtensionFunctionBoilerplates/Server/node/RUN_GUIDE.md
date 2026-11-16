# Running the Node.js standalone worker

How to install, configure, run, test and deploy the worker after you change it. For what the worker does and the
message contract, see the [README](README.md) and the [top-level README](../../README.md).

**The usual loop after a change:**

```sh
npm test         # unit tests
npm start        # run; send a test request (step 5), check the reply, Ctrl+C to stop
```

## 1. Prerequisites

| Tool | Version | Check |
| --- | --- | --- |
| Node.js | 20 or newer | `node -v` |
| Azure Service Bus | a namespace, two queues, one policy with Listen + Send | see [SERVICE_BUS_SETUP.md](../../SERVICE_BUS_SETUP.md) |

Install Node.js from [nodejs.org](https://nodejs.org/), or:
- **macOS:** `brew install node@20`
- **Windows:** `winget install OpenJS.NodeJS.LTS`
- **Linux:** use your distribution's Node.js 20+ package or [NodeSource](https://github.com/nodesource/distributions)

Then, from the project folder:

```sh
npm install
```

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

## 3. Run the unit tests

```sh
npm test
```

This runs every `*.test.js` file under `test/` with Node's built-in test runner and prints `pass` / `fail` counts.
A failed test shows the expected and actual values.

### Testing your own business logic

You can unit-test your business logic without Service Bus by calling it through `createReply`, the same way
the worker does. Add `test/businessLogic.test.js`:

```js
"use strict";

const test = require("node:test");
const assert = require("node:assert");
const { processRequest } = require("../src/businessLogic");
const { createReply } = require("../src/core/reply");

test("valid request succeeds", async () => {
  const reply = await createReply(processRequest, { email: "test.user@example.com" }, "test-1");
  assert.strictEqual(reply.status, "success");
});
```

`npm test` runs it with the other tests.

## 4. Run locally

```sh
npm start
```

The worker reads `config/settings.json` from the project folder. To use a file somewhere else, pass its path:

```sh
node src/worker.js /path/to/settings.json
```

When it is ready, it logs:

```
Listening on extension-requests, replying to extension-replies
```

It keeps running until you press **Ctrl+C**. On Ctrl+C it stops taking new requests, lets the current one finish or
go back to the queue, and then exits.

**Debugging in VS Code:** open the project folder, set a breakpoint in `src/businessLogic.js`, then
**Run → Start Debugging** → **Node.js** and pick `src/worker.js`.

## 5. Test end to end

Follow [Send a test request and read the reply](../../SERVICE_BUS_SETUP.md#send-a-test-request-and-read-the-reply).
While the worker runs, its console shows:

```
Received request test-1
Replied to request test-1
```

Also try a failure: send a body that is not JSON, such as `hello`. The reply should have `"status": "failure"`
and `"error_code": "500"`. If your business logic throws `businessLogicError(...)`, the reply should carry your error code.

Finally, run a real extension function call from H2R and check that H2R picks up the reply.

## 6. Logs

The worker logs to the console (`console.log` / `console.error`). Each request logs its CorrelationId when it arrives
and when the reply is sent; business-logic failures are logged with the error. Message bodies are never logged.

To keep the logs in a file:

```sh
npm start >> worker.log 2>&1
```

## 7. Deploy

Copy the project folder to the server **without** `node_modules`, together with `config/settings.json` holding the
real values. Install Node.js 20+ on the server, then from the project folder:

```sh
npm ci --omit=dev
```

Then run the worker as a service so it starts on boot and restarts after a crash.

### Linux (systemd)

`/etc/systemd/system/extension-worker.service`:

```ini
[Unit]
Description=H2R extension function worker
After=network-online.target

[Service]
User=extension
WorkingDirectory=/opt/extension-worker
ExecStart=/usr/bin/node /opt/extension-worker/src/worker.js
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
`node <path>\src\worker.js`.

### Container

```dockerfile
FROM node:20-alpine
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev
COPY src ./src
CMD ["node", "src/worker.js"]
```

Mount the settings file instead of baking the key into the image:

```sh
docker build -t extension-worker .
docker run -d --restart unless-stopped -v /secure/path/settings.json:/app/config/settings.json:ro extension-worker
```

### Updating a running worker

1. `npm test` (the tests must pass).
2. Copy the changed files to the server; run `npm ci --omit=dev` if `package.json` changed.
3. Restart the service (`sudo systemctl restart extension-worker`, or rebuild and restart the container).

A request being processed during the restart goes back to the queue and is picked up again after the restart.

## 8. Troubleshooting

| Symptom | Likely cause | What to do |
| --- | --- | --- |
| `config/settings.json: fill in ...` at startup | Values missing or still placeholders | Fill in the values it names |
| `ENOENT: no such file or directory ... settings.json` | Wrong path passed as an argument | Check the path, or leave it out to use `config/settings.json` |
| `Cannot find module '@azure/service-bus'` | Packages not installed | Run `npm install` (or `npm ci --omit=dev` on the server) |
| `Unauthorized` / `401` in the logs | Wrong key or policy name, or the policy lacks Listen/Send | Check `keyName` and `key`; give the policy Listen and Send |
| `MessagingEntityNotFound` | Queue name typo, or the queue is in another namespace | Check `sourceQueue`, `destinationQueue` and `namespace` |
| Worker runs but never logs `Received request` | Messages go to a different queue | Check that the test message (or H2R) uses `sourceQueue` |
| Requests end up in the dead-letter queue | The reply could not be sent, again and again | Check Send rights on the destination queue and that it exists; see the error in the logs |
| H2R doesn't get the reply | Reply queue mismatch, or H2R timed out | Check `destinationQueue` matches H2R's reply queue; check that the logic finishes within H2R's timeout |
| `SyntaxError` or unsupported feature at startup | Node.js older than 20 | Install Node.js 20+ |
