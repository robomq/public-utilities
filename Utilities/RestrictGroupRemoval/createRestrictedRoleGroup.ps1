param(
    [string] $member,
    [string] $roleGroupName
)

$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path

. (Join-Path $scriptPath 'createRole.ps1')
. (Join-Path $scriptPath 'createRoleGroup.ps1')



try{


Connect-ExchangeOnline -Organization "*" -ShowBanner:$false

$requiredCommands = @("Add-DistributionGroupMember", "Get-DistributionGroupMember", "Get-Group" ,"Get-User", "Get-DistributionGroup", "Get-DynamicDistributionGroup")

# Create role with Parent as Distribution group to manage Entra Distribution Lists
$dlRoleName = "AddOnlyEDL $roleGroupName"
$parentName="Distribution Groups"
CreateRole -roleName $dlRoleName -parent $parentName -requiredCommands $requiredCommands

# Create role with Parent as Security Group Creation and Membership to manage Mail Enabled Security Group
$mesgRoleName = "AddOnlyMESG $roleGroupName"
$parentName="Security Group Creation and Membership"
CreateRole -roleName $mesgRoleName -parent $parentName -requiredCommands $requiredCommands

# Create role group 
CreateRoleGroup -roleGroupName $roleGroupName -roles $dlRoleName, $mesgRoleName -member $member
}
catch{
    Write-Host $_
}
Disconnect-ExchangeOnline -Confirm:$false