. (Join-Path $scriptPath 'BusinessLogic.ps1')

# Message processing with proper error handling
function ProcessMessage {
    param($message, $correlationId)
    Write-Host "Inside Process message"
    try {
        Write-Log -Level "INFO" -Message "Processing message $correlationId"
        
        Invoke-BusinessLogic -messagePayload $message -correlationId $correlationId | Out-Null
        
        $processedData = @{
            "status" = "success"
            "timestamp" = (Get-Date).ToUniversalTime().ToString('o')
        }
        
        return $processedData
    }
    catch {
        Write-Log -Level "ERROR" -Message "Failed to process message ${correlationId}: $_"
        
        # Return failure response with error details
        $errorResponse = @{
            "status" = "failure"
            "timestamp" = (Get-Date).ToUniversalTime().ToString('o')
            "error_code" = $null -eq $_.Exception.Response.StatusCode.value__  ? "500": $_.Exception.Response.StatusCode.value__.toString()
              # Get HTTP status code if available, default to 500
            "error_description" = $_.Exception.Message
        }
        
        return $errorResponse
    }
}