# Running the Python standalone worker

How to install, configure, run, test and deploy the worker after you change it. For what the worker does and the
message contract, see the [README](README.md) and the [top-level README](../../README.md).

**The usual loop after a change:**

```sh
. .venv/bin/activate                    # Windows: .venv\Scripts\activate
python -m unittest discover -s tests    # unit tests
python worker.py                        # run; send a test request (step 5), check the reply, Ctrl+C to stop
```

## 1. Prerequisites

| Tool | Version | Check |
| --- | --- | --- |
| Python | 3.10 or newer (3.12 recommended) | `python3 --version` |
| Azure Service Bus | a namespace, two queues, one policy with Listen + Send | see [SERVICE_BUS_SETUP.md](../../SERVICE_BUS_SETUP.md) |

Install Python from [python.org](https://www.python.org/downloads/), or:
- **macOS:** `brew install python@3.12`
- **Windows:** `winget install Python.Python.3.12`
- **Linux:** your distribution's `python3` package (3.10+) with `python3-venv`

Then, from the project folder, create a virtual environment and install the packages:

```sh
python3 -m venv .venv
. .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -r requirements.txt
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
python -m unittest discover -s tests
```

This runs every `test_*.py` file in `tests/` and prints `OK` or the failures, with the expected and actual values.

### Testing your own business logic

You can unit-test your business logic without Service Bus by calling it through `create_reply`, the same way the
worker does. Add `tests/test_business_logic.py`:

```python
import json
import unittest

from business_logic import process_request
from core.reply import create_reply


class BusinessLogicTest(unittest.TestCase):
    def test_valid_request_succeeds(self):
        reply = json.loads(create_reply(process_request, b'{"email": "test.user@example.com"}', "test-1"))
        self.assertEqual(reply["status"], "success")
```

The test command above runs it with the other tests.

## 4. Run locally

```sh
python worker.py
```

The worker reads `config/settings.json` from the project folder. To use a file somewhere else, pass its path:

```sh
python worker.py /path/to/settings.json
```

When it is ready, it logs:

```
[INFO] Listening on extension-requests, replying to extension-replies
```

It keeps running until you press **Ctrl+C**. On Ctrl+C it stops taking new requests, finishes the current one, and
exits within a few seconds.

**Debugging in VS Code:** with the **Python** extension, open the project folder, select the `.venv` interpreter, set a
breakpoint in `business_logic.py`, open `worker.py`, and press **F5** → **Python File**.

## 5. Test end to end

Follow [Send a test request and read the reply](../../SERVICE_BUS_SETUP.md#send-a-test-request-and-read-the-reply).
While the worker runs, its console shows:

```
[INFO] Received request test-1
[INFO] Replied to request test-1
```

Also try a failure: send a body that is not JSON, such as `hello`. The reply should have `"status": "failure"`
and `"error_code": "500"`. If your business logic raises `BusinessLogicError`, the reply should carry your error code.

Finally, run a real extension function call from H2R and check that H2R picks up the reply.

## 6. Logs

The worker logs to the console with Python's `logging` module. Each request logs its CorrelationId when it arrives
and when the reply is sent; business-logic failures are logged with the traceback. Message bodies are never logged.
The Azure SDK's own logging is limited to warnings.

To keep the logs in a file:

```sh
python worker.py >> worker.log 2>&1
```

## 7. Deploy

Copy the project folder to the server **without** `.venv`, together with `config/settings.json` holding the real
values. Install Python 3.10+ on the server, then from the project folder:

```sh
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
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
ExecStart=/opt/extension-worker/.venv/bin/python /opt/extension-worker/worker.py
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
`<path>\.venv\Scripts\python.exe <path>\worker.py`.

### Container

```dockerfile
FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
CMD ["python", "worker.py"]
```

Mount the settings file instead of baking the key into the image:

```sh
docker build -t extension-worker .
docker run -d --restart unless-stopped -v /secure/path/settings.json:/app/config/settings.json:ro extension-worker
```

### Updating a running worker

1. Run the unit tests (they must pass).
2. Copy the changed files to the server; run `.venv/bin/pip install -r requirements.txt` if `requirements.txt` changed.
3. Restart the service (`sudo systemctl restart extension-worker`, or rebuild and restart the container).

A request being processed during the restart goes back to the queue and is picked up again after the restart.

## 8. Troubleshooting

| Symptom | Likely cause | What to do |
| --- | --- | --- |
| `.../config/settings.json: fill in ...` at startup | Values missing or still placeholders | Fill in the values it names |
| `FileNotFoundError: ... settings.json` | Wrong path passed as an argument | Check the path, or leave it out to use `config/settings.json` |
| `ModuleNotFoundError: No module named 'azure'` | Packages not installed, or `.venv` not active | Activate `.venv` and run `pip install -r requirements.txt` |
| `SyntaxError` or `TypeError` at startup about `\|` in a type hint | Python older than 3.10 | Install Python 3.10+ and recreate `.venv` |
| `Unauthorized` / `401` in the logs | Wrong key or policy name, or the policy lacks Listen/Send | Check `keyName` and `key`; give the policy Listen and Send |
| `MessagingEntityNotFound` | Queue name typo, or the queue is in another namespace | Check `sourceQueue`, `destinationQueue` and `namespace` |
| Worker runs but never logs `Received request` | Messages go to a different queue | Check that the test message (or H2R) uses `sourceQueue` |
| Requests end up in the dead-letter queue | The reply could not be sent, again and again | Check Send rights on the destination queue and that it exists; see the error in the logs |
| H2R doesn't get the reply | Reply queue mismatch, or H2R timed out | Check `destinationQueue` matches H2R's reply queue; check that the logic finishes within H2R's timeout |
