# Node.js Azure Function: developer guide

This guide takes you from an empty template to a deployed H2R extension function, using **VS Code**. It covers where to
write your code, how to test it, how to run it on your machine, and how to deploy and update it in Azure.

**Contents**
1. [How it works](#1-how-it-works)
2. [Where to write your code](#2-where-to-write-your-code)
3. [Writing your business logic](#3-writing-your-business-logic)
4. [Set up VS Code (one time)](#4-set-up-vs-code-one-time)
5. [Configure the settings](#5-configure-the-settings)
6. [Test your logic with unit tests](#6-test-your-logic-with-unit-tests)
7. [Run on your machine](#7-run-on-your-machine)
8. [Send a test request](#8-send-a-test-request)
9. [Deploy to Azure](#9-deploy-to-azure)
10. [Update after a change](#10-update-after-a-change)
11. [Logs and monitoring](#11-logs-and-monitoring)
12. [More than one extension function](#12-more-than-one-extension-function)
13. [Troubleshooting](#13-troubleshooting)
14. [Checklist](#14-checklist)

---

## 1. How it works

```
H2R ──request──▶ source queue ──▶ ExtensionFunction ──▶ your processRequest()
                                        │
H2R ◀──reply──── destination queue ◀────┘  (same CorrelationId as the request)
```

1. H2R sends a JSON request to the **source queue**. Each request has a **CorrelationId**.
2. Azure starts `ExtensionFunction` for the request and passes it to your `processRequest` function.
3. Whatever your code returns or throws is turned into a **reply**, which is sent to the **destination queue** with
   the same CorrelationId. H2R uses the CorrelationId to match the reply to its request.

You write step 2. The template does everything else: receiving, building the reply, sending it, and retrying.

## 2. Where to write your code

```
AzureFunction/node/
├── src/
│   ├── businessLogic.js            ◀── WRITE YOUR CODE HERE
│   ├── functions/
│   │   └── extensionFunction.js    the function Azure calls; no changes needed
│   └── core/                       builds the reply H2R expects; no changes needed
├── test/
│   └── (your tests go here, see section 6)
├── package.json                    libraries your code needs are listed here
├── local.settings.json.example     copy to local.settings.json (section 5)
└── host.json                       Azure Functions runtime settings; no changes needed
```

| File                                                | Do you change it?                                                         |
| --------------------------------------------------- | ------------------------------------------------------------------------- |
| `src/businessLogic.js`                              | **Yes.** Your business logic goes here                                    |
| `package.json`                                      | Only through `npm install` when you add a library (see [Using a library](#using-a-library)) |
| `local.settings.json`                               | Yes, with your own values (section 5). Never commit it                    |
| `test/...`                                          | Yes, if you add tests (section 6)                                         |
| `src/functions/*`, `src/core/*`, `host.json`        | No                                                                        |

## 3. Writing your business logic

Open `src/businessLogic.js`. It has one function to fill in:

```js
async function processRequest(request, correlationId) {
  return null;
}
```

| Parameter | What it is |
| --- | --- |
| `request` | The request body from H2R, already parsed from JSON into a plain object |
| `correlationId` | The ID of this request. Use it in your log messages |

`processRequest` is `async`, so you can `await` API calls inside it. Keep its name: the function file calls it by that name.

### Reading the request

`request` is a plain JavaScript object:

```js
const email = request.email;                  // undefined if missing
const department = request.department ?? "";  // "" if missing
const hasManager = "manager" in request;
const city = request.address?.city;           // nested object, safe if address is missing
```

The fields depend on how the extension function is configured in H2R.

### Replying with success

| Your code | Reply sent to H2R |
| --- | --- |
| `return null;` | `{"status": "success", "timestamp": "..."}` |
| `return { accountId: "12345" };` | `{"status": "success", "timestamp": "...", "data": {"accountId": "12345"}}` |

### Replying with a failure

| Your code | Reply sent to H2R |
| --- | --- |
| `throw businessLogicError("Email is missing", "400");` | `{"status": "failure", "error_code": "400", "error_description": "Email is missing", ...}` |
| Any other error, e.g. `throw new Error("API is down");` | `{"status": "failure", "error_code": "500", "error_description": "API is down", ...}` |

Use `businessLogicError` (already imported at the top of `businessLogic.js`) when you want to choose the error code.
A failure is still a reply, so H2R always hears back. The `error_description` is sent to H2R, so don't put secrets in
error messages.

### A complete example

```js
"use strict";

const { businessLogicError } = require("./core/businessLogicError");
const { createMyApiClient } = require("./myApiClient");

// Runs once, when the function starts. Create clients here, not inside processRequest().
const client = createMyApiClient(process.env.MyApiKey);

async function processRequest(request, correlationId) {
  const email = request.email;
  if (!email) {
    throw businessLogicError("Email is missing", "400");
  }

  // Safe to repeat: don't create the account twice if this request is delivered again.
  let accountId = await client.findAccount(email);
  if (!accountId) {
    accountId = await client.createAccount(email);
  }
  console.log(`Account ready for request ${correlationId}`);

  return { accountId };
}

module.exports = { processRequest };
```

### Rules to follow

- **Set things up once, at the top of the file.** Code outside `processRequest` runs once, when the function starts.
  Variables declared there (API clients, tokens, caches) are kept between requests.
- **Make `processRequest` safe to run twice for the same request.** If the reply can't be sent, Service Bus delivers the
  request again, even though your code already ran. Check before you create, as in the example.
- **One request at a time.** Each running copy of the function handles one request at a time, so two requests never
  change your variables at the same moment.
- **Always `await`.** If you start an async call without `await`, the reply is sent before it finishes, and errors
  from it are lost.
- **Variables are memory only.** Azure can restart the function or run more than one copy. Each copy has its own
  variables, and they are lost on restart. Keep anything that must survive in a database or storage.
- **Finish quickly.** H2R waits for the reply. Long-running work makes H2R time out.
- **Don't log personal data or secrets.** Log the `correlationId` and what happened, not the request body.

### Settings and secrets your code needs

Don't put API keys or URLs in the code. Add them as settings and read them from `process.env`:

1. Add them to `local.settings.json` under `Values`, for example `"MyApiKey": "..."`.
2. Read them at the top of `businessLogic.js`: `process.env.MyApiKey`.
3. When you deploy, **Upload Local Settings** copies them to Azure (section 9).

### Logging

Use `console.log` for information and `console.error` for errors, as in the example. Log lines appear in the VS Code
Terminal when you run locally, and in the Function App's logs in Azure.

### Using a library

In the VS Code Terminal (**Terminal → New Terminal**), from the project folder:

```sh
npm install axios --save-exact
```

This adds the library to `package.json` with an exact version, and Azure installs it when you deploy. Then
`require("axios")` in `businessLogic.js`.

## 4. Set up VS Code (one time)

1. **Install Node.js 20 or newer (LTS).** Download it from [nodejs.org](https://nodejs.org/), or:
   - macOS: `brew install node@20`
   - Windows: `winget install OpenJS.NodeJS.LTS`
2. **Install [VS Code](https://code.visualstudio.com/)**, then open **Extensions** (`Ctrl+Shift+X` / `Cmd+Shift+X`)
   and install these two, both free:
   - **Azure Functions** (Microsoft)
   - **Azurite** (Azurite): a local storage emulator the Azure Functions runtime needs to run on your machine
3. **Open the project:** **File → Open Folder** → select `AzureFunction/node`, the folder that contains `package.json`.
4. **Install the packages:** **Terminal → New Terminal**, then run `npm install`.
5. **Connect the project to the Azure Functions extension:** open the Command Palette
   (`Ctrl+Shift+P` / `Cmd+Shift+P`) and run **Azure Functions: Initialize Project for Use with VS Code**.
   This adds a `.vscode` folder with the run and deploy settings.
   If VS Code says **Azure Functions Core Tools** are missing, click **Install**.
6. **Sign in to Azure:** click the **Azure** icon in the left bar → **Sign in to Azure**.

## 5. Configure the settings

You need a Service Bus namespace with two queues and an access policy with **Listen** and **Send**. If you don't have
them yet, follow [SERVICE_BUS_SETUP.md](../../SERVICE_BUS_SETUP.md).

In VS Code, copy `local.settings.json.example` to a new file called `local.settings.json` in the same folder and fill it in:

```json
{
  "IsEncrypted": false,
  "Values": {
    "AzureWebJobsStorage": "UseDevelopmentStorage=true",
    "FUNCTIONS_WORKER_RUNTIME": "node",
    "ServiceBusConnection": "Endpoint=sb://<namespace>.servicebus.windows.net/;SharedAccessKeyName=<policy>;SharedAccessKey=<key>",
    "SourceQueue": "<queue H2R sends requests to>",
    "DestinationQueue": "<queue H2R reads replies from>"
  }
}
```

| Setting | What to put | Where to find it |
| --- | --- | --- |
| `ServiceBusConnection` | The policy's primary connection string | Azure portal → Service Bus namespace → **Shared access policies** → your policy |
| `SourceQueue` | Name of the queue H2R sends requests to | Azure portal → namespace → **Queues** |
| `DestinationQueue` | Name of the queue H2R reads replies from | Azure portal → namespace → **Queues** |
| `AzureWebJobsStorage`, `FUNCTIONS_WORKER_RUNTIME` | Leave as they are | – |

`local.settings.json` contains a secret. Git ignores it; don't share it.

## 6. Test your logic with unit tests

You can test your business logic without Azure or Service Bus. Create `test/businessLogic.test.js`:

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

test("missing email fails with 400", async () => {
  const reply = await createReply(processRequest, {}, "test-2");
  assert.strictEqual(reply.error_code, "400");
});
```

The second test assumes your logic rejects a missing email with code 400, as in the example in section 3. Adjust
both tests to your own logic.

To run the tests, open the VS Code Terminal and run:

```sh
npm test
```

It runs every `*.test.js` file and prints `pass` / `fail` counts. A failed test shows the expected and actual values.

`createReply` is what the function uses, so the reply you test is exactly the reply H2R gets.

## 7. Run on your machine

1. Command Palette → **Azurite: Start**. Leave it running.
2. Press **F5** (or **Run → Start Debugging**). VS Code starts the function with the debugger attached.
   The **Terminal** panel shows this when it's ready:
   ```
   Functions:
           ExtensionFunction: serviceBusTrigger
   ```
3. The function now handles requests from your **real** source queue. Send one (section 8).
4. Press **Shift+F5** to stop.

**Debugging:** click left of a line number in `src/businessLogic.js` to set a breakpoint, then press F5. When a request
arrives, execution stops there and you can inspect `request` and step through your code.

**After every code change:** Shift+F5, then F5.

If the function is also deployed in Azure and listens on the same source queue, the two copies compete for requests.
Use separate test queues, or stop the Azure copy while you test (Azure icon → your Function App → **Stop**).

## 8. Send a test request

1. Azure portal → your Service Bus namespace → **Queues** → your **source queue** → **Service Bus Explorer** → **Send messages**.
2. Set **Content Type** to `application/json` and the body to a request your logic expects, for example:
   ```json
   { "email": "test.user@example.com" }
   ```
3. Expand **Broker properties**, set **Correlation Id** to `test-1`, and click **Send**.
4. In VS Code's Terminal you should see:
   ```
   Received request test-1
   Replied to request test-1
   ```
5. Open your **destination queue** → **Service Bus Explorer** → **Peek from start**. There should be a message with
   Correlation Id `test-1` and your reply as the body.

Also check a failure: send a body that is not JSON, such as `hello`. The reply should have `"status": "failure"`
and `"error_code": "500"`.

Finally, trigger the extension function from H2R and check that H2R shows the result.

## 9. Deploy to Azure

### First time

1. Click the **Azure** icon → **Workspace** section (bottom of the panel) → the **Deploy** (cloud with arrow) icon →
   **Deploy to Function App...**
2. Choose **+ Create new Function App in Azure... (Advanced)** and answer the prompts:
   - **Name:** globally unique, for example `acme-h2r-extension`
   - **Runtime stack:** Node.js, version **20** or newer
   - **Resource group** and **region:** your choice
   - Accept the defaults for the hosting plan, storage account and Application Insights, unless your organisation
     says otherwise
   VS Code creates the app, then deploys your code. This takes a few minutes.
3. **Copy your settings to Azure:** Command Palette → **Azure Functions: Upload Local Settings** → pick your Function App.
   This copies every value in `local.settings.json` (Service Bus connection, queue names and your own settings) to the app.
4. Send a test request (section 8) and check the reply. To see the logs while you test, use **Start Streaming Logs**
   (section 11).

### Keeping the connection string in Key Vault (optional)

Azure portal → Function App → **Settings → Environment variables** → edit `ServiceBusConnection` and set it to
`@Microsoft.KeyVault(SecretUri=https://<vault>.vault.azure.net/secrets/<name>/)`. The Function App needs a managed
identity with permission to read that secret.

## 10. Update after a change

1. Run your unit tests (section 6).
2. Run locally and send a test request (sections 7 and 8).
3. **Deploy** icon → **Deploy to Function App...** → pick the **same** Function App → confirm.
4. If you added or changed settings in `local.settings.json`, run **Azure Functions: Upload Local Settings** again.
5. Send a test request against Azure.

A request being processed while Azure restarts with the new code goes back to the queue and is processed again.

## 11. Logs and monitoring

| Where | How |
| --- | --- |
| On your machine | VS Code **Terminal** panel while the function runs |
| Azure, live | Azure icon → **Resources** → your subscription → **Function App** → right-click your app → **Start Streaming Logs** |
| Azure, history | Azure portal → Function App → **Monitor**, or its Application Insights resource |
| Requests that keep failing | Azure portal → source queue → **Service Bus Explorer** → **Dead-letter** tab |

Each request logs `Received request <id>` and `Replied to request <id>`. Business-logic failures are logged with the
error. Message bodies are never logged.

A request lands in the dead-letter queue only when its **reply could not be sent** after several attempts (the
queue's *Max delivery count*). A failure inside your business logic doesn't go there; it becomes a `failure` reply.

## 12. More than one extension function

One Function App can serve several extension functions, each with its own queues and its own business logic file.
Follow [ADD_EXTENSION_FUNCTION.md](ADD_EXTENSION_FUNCTION.md).

## 13. Troubleshooting

| What you see                                                     | Why                                                      | What to do                                                                         |
| ---------------------------------------------------------------- | -------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| F5 does nothing or asks for a configuration                      | The project wasn't initialized                           | Run **Azure Functions: Initialize Project for Use with VS Code** (section 4)       |
| "Azure Functions Core Tools" not found                           | Core Tools not installed                                 | Click **Install** when VS Code offers, or run the Initialize command again         |
| Storage or `AzureWebJobsStorage` error at startup                | Azurite isn't running                                    | Command Palette → **Azurite: Start**                                               |
| `Cannot find module '@azure/functions'` (or another package)     | Packages not installed                                   | Run `npm install` in the VS Code Terminal                                          |
| `node` or `npm` not found                                        | Node.js missing, or VS Code started before it was installed | Install Node.js 20+, then restart VS Code                                       |
| `No job functions found`                                         | Function file not loaded                                 | Check that `package.json` has `"main": "src/functions/*.js"` and run `npm install` |
| `'%SourceQueue%' does not resolve to a value`                    | `SourceQueue` missing                                    | Add it to `local.settings.json`; in Azure, run **Upload Local Settings**           |
| `Set the ServiceBusConnection and DestinationQueue app settings` | One of them is missing                                   | Same as above                                                                      |
| `Unauthorized` / `401`                                           | Wrong connection string, or the policy lacks Listen/Send | Copy the policy's primary connection string again; give the policy Listen and Send |
| `MessagingEntityNotFound`                                        | Queue name typo, or the queue is in another namespace    | Check both queue names and the namespace in the connection string                  |
| Function runs but never logs `Received request`                  | Request sent to another queue, or another copy took it   | Check `SourceQueue`; stop the deployed copy while testing locally                  |
| Requests end up in the dead-letter queue                         | The reply couldn't be sent                               | Check Send rights and the `DestinationQueue` name; read the error in the logs      |
| H2R doesn't show the result                                      | Wrong reply queue, or H2R timed out                      | Check `DestinationQueue` matches H2R's reply queue; make `processRequest` faster   |
| Works locally, fails in Azure                                    | Settings not copied to Azure                             | Run **Azure Functions: Upload Local Settings**                                     |
| Tests fail after changing `businessLogic.js`                     | The tests expect the old behavior                        | Update `test/businessLogic.test.js` to match your logic                            |

## 14. Checklist

**Before your first run**
- [ ] Node.js 20+, VS Code and the two extensions installed
- [ ] `npm install` run, project initialized for VS Code, and signed in to Azure
- [ ] Service Bus queues and a Listen + Send policy exist
- [ ] `local.settings.json` filled in

**Before every deploy**
- [ ] Business logic written in `processRequest` in `src/businessLogic.js`, every async call awaited, and safe to run twice
- [ ] `npm test` passes
- [ ] Tested locally with a real request; the reply has the right CorrelationId
- [ ] New settings uploaded with **Upload Local Settings**
- [ ] Tested in Azure, then with a real call from H2R
