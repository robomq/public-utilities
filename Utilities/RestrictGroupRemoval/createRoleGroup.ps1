function CreateRoleGroup{
    param(
        [string] $roleGroupName,
        [string] $member,
        [string[]]$roles
    )

    try {
        Write-Host "`n Creating Role Group $roleGroupName with member $member and roles '$roles' `n"
        New-RoleGroup -Name $roleGroupName -Roles $roles -Members $member
        Write-Host "`n Role Group $roleGroupName created successfully with member $member and roles '$roles' `n"
    }
    catch {
        Write-Host $_
    }
}