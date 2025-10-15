function CreateRole{
    param (
        [string]$roleName,
        [string]$parent,
        [string[]]$requiredCommands
    )
    try{
        Write-Host "`n Creating Role With Name $roleName and Parent $parent `n"
        New-ManagementRole -Name $roleName -Parent $parent
        $commandsAllowed = Get-ManagementRoleEntry "$roleName\*"
         Write-Host "`n Currently Available Commands: `n $($commandsAllowed.Name) `n"
         Write-Host "`n Start Removing Unwanted Commands `n"
        foreach($command in $commandsAllowed){
            if($requiredCommands -notcontains $command.Name){
                 Remove-ManagementRoleEntry "$roleName\$($command.Name)" -Confirm:$false
            }
        }
        Write-Host "`n Removed Unwanted Commands Successfully`n"
        $remainingCmds = Get-ManagementRoleEntry "$roleName\*"
        Write-Host "`n Remaining Commands: `n $($remainingCmds.Name) `n"
    }
    catch{
        Write-Host $_
    }
}