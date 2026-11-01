param([ValidateSet('Install','Remove')][string]$Action = 'Install',
      [string]$Payload = $PSScriptRoot)
$ErrorActionPreference = 'Stop'
$serviceName = 'GmrickerMonitorCollector'
if (Get-ItemPropertyValue -LiteralPath 'HKLM:\Software\GMRicker\Status Monitor\Installer' -Name ManagedCollector -ErrorAction SilentlyContinue) {
    throw 'O serviço é gerenciado pelo instalador do Status Monitor. Use a manutenção ou desinstalação do aplicativo.'
}
$installDirectory = Join-Path ([Environment]::GetFolderPath('ProgramFiles')) 'Status Monitor/collector'
$executable = Join-Path $installDirectory 'StatusMonitorCollector.exe'
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
if (-not ([Security.Principal.WindowsPrincipal]$identity).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'A instalação ou remoção do serviço exige autorização de administrador.'
}

function Invoke-ServiceCommand([string[]]$Arguments) {
    & "$env:SystemRoot/System32/sc.exe" @Arguments | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "Falha no gerenciamento do serviço ($LASTEXITCODE)." }
}

function Assert-NoReparsePoint([string]$Path) {
    $item = Get-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue
    while ($item) {
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Caminho redirecionado não permitido: $($item.FullName)" }
        $item = $item.Parent
    }
}

function Stop-Collector {
    $controller = Get-Service -Name $serviceName
    try {
        if ($controller.Status -ne 'Stopped') { $controller.Stop() }
        $controller.WaitForStatus('Stopped', [TimeSpan]::FromSeconds(15))
    } finally { $controller.Dispose() }
}

Assert-NoReparsePoint (Split-Path -Parent $installDirectory)
Assert-NoReparsePoint $installDirectory
$existing = Get-CimInstance Win32_Service -Filter "Name='$serviceName'"
$quotedExecutable = '"' + $executable + '"'
if ($existing -and $existing.PathName -ne $quotedExecutable -and $existing.PathName -ne $executable) {
    throw 'Já existe um serviço com esse nome apontando para outro executável.'
}

if ($Action -eq 'Install') {
    foreach ($name in @('StatusMonitorCollector.exe','PresentMon.exe','PresentMon-LICENSE.txt')) {
        if (-not (Test-Path -LiteralPath (Join-Path $Payload $name) -PathType Leaf)) { throw "Arquivo do coletor ausente: $name" }
    }
    # Stop the previous version before replacing files. Users cannot change this configuration.
    if ($existing) { Stop-Collector }
    New-Item -ItemType Directory -Path $installDirectory -Force | Out-Null
    $permissions = New-Object Security.AccessControl.DirectorySecurity
    $permissions.SetAccessRuleProtection($true, $false)
    $permissions.SetOwner([Security.Principal.SecurityIdentifier]'S-1-5-32-544')
    foreach ($sid in @('S-1-5-18','S-1-5-32-544')) {
        $permissions.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule(
            ([Security.Principal.SecurityIdentifier]$sid), 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')))
    }
    $permissions.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule(
        ([Security.Principal.SecurityIdentifier]'S-1-5-32-545'), 'ReadAndExecute', 'ContainerInherit,ObjectInherit', 'None', 'Allow')))
    Set-Acl -LiteralPath $installDirectory -AclObject $permissions
    foreach ($name in @('StatusMonitorCollector.exe','PresentMon.exe','PresentMon-LICENSE.txt')) {
        $destination = Join-Path $installDirectory $name
        Assert-NoReparsePoint $destination
        Copy-Item -LiteralPath (Join-Path $Payload $name) -Destination $destination -Force
        # Reset file ACLs as well when updating a previous installation.
        & "$env:SystemRoot/System32/icacls.exe" $destination /reset | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao proteger os arquivos instalados.' }
    }
    if (-not $existing) {
        # Pass the quoted path through the SCM API, not native command-line re-quoting.
        $created = New-Service -Name $serviceName -BinaryPathName $quotedExecutable -StartupType Manual -DisplayName 'Status Monitor — Coletor'
        $created.Dispose()
    } else {
        $result = Invoke-CimMethod -InputObject $existing -MethodName Change -Arguments @{PathName=$quotedExecutable; StartMode='Manual'}
        if ($result.ReturnValue -ne 0) { throw "Falha ao atualizar o caminho do serviço ($($result.ReturnValue))." }
    }
    # Interactive users can query and start this service, never stop/reconfigure/delete it.
    Invoke-ServiceCommand -Arguments @('sdset', $serviceName, 'D:P(A;;GA;;;SY)(A;;GA;;;BA)(A;;LCRP;;;IU)')
    Invoke-ServiceCommand -Arguments @('description', $serviceName, 'Coletor local do Status Monitor. Inicia sob demanda e para automaticamente sem clientes.')
    Write-Output 'Serviço instalado e parado. Será iniciado somente durante uma coleta Ingame.'
} else {
    if ($existing) {
        Stop-Collector
        Invoke-ServiceCommand -Arguments @('delete', $serviceName)
    }
    # Delete only our known files, never recursively remove the application directory.
    foreach ($name in @('StatusMonitorCollector.exe','PresentMon.exe','PresentMon-LICENSE.txt')) {
        $file = Join-Path $installDirectory $name
        Assert-NoReparsePoint $file
        if (Test-Path -LiteralPath $file) { Remove-Item -LiteralPath $file -Force }
    }
    if ((Test-Path -LiteralPath $installDirectory) -and -not (Get-ChildItem -LiteralPath $installDirectory -Force)) {
        Remove-Item -LiteralPath $installDirectory
    }
    Write-Output 'Serviço removido.'
}
