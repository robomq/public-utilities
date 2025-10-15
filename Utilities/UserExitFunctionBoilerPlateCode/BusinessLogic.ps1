$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path

. (Join-Path $scriptPath 'Logger.ps1')

function Invoke-BusinessLogic {
    param(
        [Parameter(Mandatory)] [psobject] $MessagePayload,
        [string] $correlationId
)

	#Implement Your Business Logic Here 

}