# Python standalone worker

A long-running Python process for a VM, an on-prem server or a container. It receives H2R extensionFunction requests
from the source queue with peek-lock, runs your business logic, and replies on the destination queue with the same
CorrelationId. See the [top-level README](../../README.md) for the message contract, the Service Bus rights and the
end-to-end test.

## Files
| File | Purpose |
| --- | --- |
| `business_logic.py` | **Your logic goes here.** The only file you need to edit |
| `worker.py` | Entry point: reads the settings, listens for requests, runs until stopped |
| `config/settings.json` | Connection and queue details |
| `tests/` | Unit tests, run with `python -m unittest discover -s tests` |

You don't need to change anything in these folders:

| Folder | Purpose |
| --- | --- |
| `core` | Builds the success/failure reply H2R expects (`create_reply`) and defines `BusinessLogicError`. No Azure code |
| `servicebus` | Receives requests with peek-lock and sends each reply with the CorrelationId (`run_request_listener`) |
| `configuration` | Loads and checks `config/settings.json` (`load_function_config`) |

## Prerequisites
- Python 3.10 or newer (3.12 recommended)

## Configure
Fill in `config/settings.json`. It uses the same keys as the PowerShell, Java, Node and C# boilerplates:

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
```python
client = create_my_api_client()                         # one-time setup: top-level code runs once at startup


def process_request(request, correlation_id):
    email = request.get("email")                        # read a value from the request
    if not email:
        raise BusinessLogicError("Email is missing", "400")   # report a failure with your own code
    account_id = client.create_account(email)
    return {"accountId": account_id}                    # optional: data sent back to H2R; or return None
```
Any other exception is reported to H2R as a failure with error code `500`.

**Make `process_request` safe to run twice for the same request.** If the reply can't be sent, Service Bus delivers
the request again, even though your logic already ran. For example, check whether the account already exists before creating it.

`business_logic.py` is loaded once at startup, so variables you define at its top level (clients, tokens, caches)
carry over between requests. Requests are processed one at a time.

## Build, test and run
Step by step, including deployment and troubleshooting: [RUN_GUIDE.md](RUN_GUIDE.md).

```sh
python3 -m venv .venv && . .venv/bin/activate   # once
pip install -r requirements.txt                 # once, and after changing requirements.txt
python -m unittest discover -s tests            # unit tests
python worker.py                                # reads config/settings.json
python worker.py /path/settings.json            # or pass a path
```

## Deploy
Copy the folder (without `.venv`) and `config/settings.json` to the server, create a virtual environment there,
`pip install -r requirements.txt`, and run `python worker.py` as a service (systemd, a Windows service wrapper, or a container).
It stops cleanly on SIGTERM / Ctrl+C. A request in flight finishes or is released and redelivered.

How it behaves:
- One request at a time.
- Variables in `business_logic.py` live in memory only. They are lost when the worker restarts.
- It reconnects on its own after connection errors, and renews message locks while your logic runs, so slow logic does not lose the request.
- If the reply cannot be sent, the request is abandoned and redelivered. It is dead-lettered after the queue's max delivery count.
