$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path

. (Join-Path $scriptPath 'Logger.ps1')
. (Join-Path $scriptPath 'Config.ps1')
. (Join-Path $scriptPath 'SasToken.ps1')
. (Join-Path $scriptPath 'InvokeAndRetry.ps1')
. (Join-Path $scriptPath 'ProcessMessage.ps1')


# Main processing loop with health checks
<#
*  - Start-QueueProcessor(): Entry point for the queue processor.
*    - Loads configuration.
*    - Continuously receives messages from the source queue.
*    - Processes and forwards the messages to the destination queue.
*    - Implements retry logic for API calls.
*    - Logs key steps and errors for monitoring and troubleshooting.
#>
function Start-QueueProcessor {
    try {
        # Load configuration
        $config = Get-Config
        
        Write-Log -Level "INFO" -Message "Starting queue processor..."
        
        while ($true) {
            try {
        
                # Get fresh SAS token
                $token = Get-SasToken -Namespace $config.namespace `
                                    -AccessPolicyName $config.keyName `
                                    -AccessPolicyKey $config.key `
                                    -validityInSeconds $config.validityInSeconds

                $headers = @{
                    "Authorization" = $token
                    "Accept" = "application/json;type=entry;charset=utf-8"
                }

                $receiveUrl = "https://$($config.namespace).servicebus.windows.net/$($config.sourceQueue)/messages/head?timeout=60&api-version=2017-04"

                # Receive and process message with retry policy
                
                $result = Invoke-WithRetry -ScriptBlock {
                    $responseHeaders = @{}
                    $response = Invoke-RestMethod -Uri $receiveUrl -Method DELETE -Headers $headers -ErrorAction Stop -ResponseHeadersVariable responseHeaders
                    return @{
                        Response = $response
                        Headers = $responseHeaders
                    }
                } -Config $config

                if ($result.Response) {
                    $brokerProperties = $result.Headers["BrokerProperties"][0] | ConvertFrom-Json
                    $correlationId = $brokerProperties.CorrelationId
                    
                    Write-Log -Level "INFO" -Message "Received message: $correlationId"
                    
                    # Process message
                    $processedMessage = ProcessMessage -message $result.Response -correlationId $correlationId
                    
                    # Send to destination queue
                    $destinationUrl = "https://$($config.namespace).servicebus.windows.net/$($config.destinationQueue)/messages?api-version=2017-04"
                    
                    $sendHeaders = @{
                        "Authorization" = $token
                        "Content-Type" = "application/json"
                        "BrokerProperties" = ($brokerProperties | ConvertTo-Json -Compress)
                    }

                    Invoke-WithRetry -ScriptBlock {
                        Invoke-RestMethod -Method POST `
                                        -Uri $destinationUrl `
                                        -Headers $sendHeaders `
                                        -Body ($processedMessage | ConvertTo-Json) `
                                        -ErrorAction Stop
                    } -Config $config

                    Write-Log -Level "INFO" -Message "Successfully processed and forwarded message: $correlationId"
                }
                else {
                    Write-Log -Level "INFO" -Message "No messages received. Waiting for messages..."
                    Start-Sleep -Seconds 60
                }
            }
            catch {
                Write-Log -Level "ERROR" -Message "Processing error: $_"
                Start-Sleep -Seconds 5
            }
        }
    }
    catch {
        Write-Log -Level "FATAL" -Message "Fatal error in queue processor: $_"
        throw
    }
}

# Start the processor
try {
    Start-QueueProcessor
}
catch {
    Write-Log -Level "FATAL" -Message "Queue processor terminated: $_"
    exit 1
}