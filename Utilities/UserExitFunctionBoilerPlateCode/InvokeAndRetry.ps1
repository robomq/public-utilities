# Retry policy implementation

function Invoke-WithRetry {
    param(
        [scriptblock]$ScriptBlock,
        [Parameter(Mandatory)]
        [psobject]$Config
    )

    $attempt = 1
    $delay = $Config.initialDelayInMs

    while ($attempt -le $Config.maxRetryCount) {
        try {
            return & $ScriptBlock
        }
        catch {
            if ($attempt -eq $Config.maxRetryCount) {
                Write-Log -Level "ERROR" -Message "Final attempt failed: $_"
                throw
            }

            Write-Log -Level "WARN" -Message "Attempt $attempt failed: $_. Retrying in $($delay/1000) seconds..."
            Start-Sleep -Milliseconds $delay
            $attempt++
            $delay *= 2 # Exponential backoff
        }
    }
}