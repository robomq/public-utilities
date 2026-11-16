# Service Bus setup for testing

Both templates need an Azure Service Bus namespace with two queues and one access policy. If H2R is already set up
with its extension function queues, use those and skip to [Collect the values](#collect-the-values).

You can create everything in the Azure portal or with the Azure CLI. The names below are examples; use your own.

| Item | Example name |
| --- | --- |
| Resource group | `rg-extension-test` |
| Namespace | `sb-extension-test` (must be globally unique) |
| Source queue (H2R → your code) | `extension-requests` |
| Destination queue (your code → H2R) | `extension-replies` |
| Access policy | `extension-function` with **Listen** and **Send** |

## Option A: Azure CLI

```sh
az login
az group create --name rg-extension-test --location eastus
az servicebus namespace create --name sb-extension-test --resource-group rg-extension-test --location eastus --sku Standard
az servicebus queue create --name extension-requests --namespace-name sb-extension-test --resource-group rg-extension-test
az servicebus queue create --name extension-replies  --namespace-name sb-extension-test --resource-group rg-extension-test
az servicebus namespace authorization-rule create --name extension-function \
    --namespace-name sb-extension-test --resource-group rg-extension-test --rights Listen Send
```

## Option B: Azure portal

1. **Create a resource → Service Bus**. Choose the resource group, a unique namespace name, a region, and the Standard tier.
2. Open the namespace → **Queues → + Queue**. Create `extension-requests`, then `extension-replies`. Keep the defaults.
3. Open the namespace → **Settings → Shared access policies → + Add**. Name it `extension-function`, tick **Listen** and **Send**, and save.

## Collect the values

Open the namespace → **Shared access policies → extension-function**, or run:

```sh
az servicebus namespace authorization-rule keys list --name extension-function \
    --namespace-name sb-extension-test --resource-group rg-extension-test
```

| You need | Where it comes from | Used by |
| --- | --- | --- |
| Namespace name | `sb-extension-test` (without `.servicebus.windows.net`) | Server `settings.json` → `namespace` |
| Policy name | `extension-function` | Server `settings.json` → `keyName` |
| Primary key | `primaryKey` | Server `settings.json` → `key` |
| Primary connection string | `primaryConnectionString` | Function → `ServiceBusConnection` |
| Queue names | `extension-requests`, `extension-replies` | Both |

Treat the key and the connection string as secrets. Don't commit them or paste them into tickets.

## Send a test request and read the reply

Both run guides use this check.

1. Portal → namespace → **Queues → extension-requests → Service Bus Explorer → Send messages**.
2. Set **Content Type** to `application/json` and the body to:
   ```json
   { "email": "test.user@example.com" }
   ```
3. Expand **Broker properties** and set **Correlation Id** to `test-1`. Then send.
4. Open **Queues → extension-replies → Service Bus Explorer → Peek from start**.
   You should see one message with Correlation Id `test-1` and a body like:
   ```json
   { "status": "success", "timestamp": "2026-11-16T10:00:00.123Z" }
   ```
5. Check **extension-requests** again: its active message count should be back to 0, and its **dead-letter** count should be 0.

*Peek* doesn't remove messages. To empty the reply queue between tests, use **Receive messages** with *Receive and delete*.

## Clean up

```sh
az group delete --name rg-extension-test
```
