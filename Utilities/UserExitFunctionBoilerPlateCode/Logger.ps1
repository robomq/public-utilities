function Write-Log {
    param(
        [string]$Message,
        [string]$Level = "INFO",
        [string]$LogPath = (Join-Path $scriptPath 'logs/processor.log')
    )
    
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $logMessage = "$timestamp [$Level] $Message"
    
    # Ensure log directory exists
    $logDir = Split-Path $LogPath -Parent
    if (-not (Test-Path $logDir)) {
        New-Item -ItemType Directory -Path $logDir -Force | Out-Null
    }
    
    Add-Content -Path $LogPath -Value $logMessage
    Write-Host $logMessage
}