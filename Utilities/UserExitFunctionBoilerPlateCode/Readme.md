# User Function Processor

This PowerShell project processes messages from an Azure Service Bus queue, applies custom business logic, and forwards the processed messages to another queue.

---

## 📁 Project Structure

```
User Function/
├── Logger.ps1                                              # Logging utilities
├── Config.ps1                                              # Configuration loader
├── SasToken.ps1                                            # SAS token generation
├── InvokeAndRetry.ps1                                      # Retry logic
├── ProcessMessage.ps1                                      # Message processing logic
├── BusinessLogic.ps1                                       # Your business logic
├── config/
│   └── settings.json                                       # Configuration file
├── QueueProcessor.ps1                                      # Entry point script
└── logs/
    └── processor.log                                       # Application logs
```

---

## 🚀 Getting Started

### Prerequisites

- PowerShell 7.0 or higher
- Azure Service Bus namespace and access credentials

---

### Configuration

Update the `config/settings.json` file with your Azure Service Bus details:

```json
{
  "namespace": "<Your Azure Service Bus namespace>",
  "keyName": "<Name of the Shared Access Policy>",
  "key": "<Access key for authentication>",
  "sourceQueue": "<Queue to receive messages from>",
  "destinationQueue": "<Queue to send processed messages to>"
}
```

---

### Running the Application

1. Ensure PowerShell 7.0 or higher is installed.  
2. Open a PowerShell terminal and navigate to the project directory.
3. Make sure every file in the project are under the same root directory. 
4. Run the entry point script:

```powershell
./QueueProcessor.ps1
```

The script will start processing messages from the `sourceQueue` and send them to the `destinationQueue`.

---

## 💡 Business Logic

You need to implement your own logic in `BusinessLogic.ps1`.

```powershell
function Invoke-BusinessLogic {
    param (
        [PSObject]$messagePayload,
        [string]$correlationId
    )

    # TODO: Implement your custom business logic here
}
```

- **Parameters**:  
  - `messagePayload`: Message body as a `PSObject`  
  - `correlationId`: Unique identifier for tracking logs and operations

---
