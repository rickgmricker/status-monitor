param([string]$Resources = (Join-Path $PSScriptRoot '../build/packaging/windows-resources'),
      [string]$Wix = (Join-Path $PSScriptRoot '../.build-tools/wix3'),
      [string]$Output = (Join-Path $PSScriptRoot '../build/packaging/installer-validation'))
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath (Join-Path $Wix 'candle.exe'))) { $Wix = Join-Path $Wix 'bin' }
New-Item -ItemType Directory -Force -Path $Output | Out-Null
$resourcesPath = (Resolve-Path -LiteralPath $Resources).Path
$outputPath = (Resolve-Path -LiteralPath $Output).Path
$definitions = @(
    "-dJpConfigDir=$resourcesPath", '-dJpIsSystemWide=yes', '-dJpAllowUpgrades=yes',
    '-dJpProductCode=F9FBD900-D629-41A8-B138-CDE821C776B6',
    '-dJpProductUpgradeCode=18572358-095A-48FD-B932-E9EAF5AC0DF8',
    '-dJpAppName=Status Monitor', '-dJpAppVendor=GMRicker', '-dJpAppVersion=0.2.0',
    '-dJpAppDescription=Status Monitor', '-dJpAppSizeKb=90000',
    '-dJpDesktopShortcutPrompt=yes', '-dJpStartMenuShortcutPrompt=yes'
)
foreach ($name in @('main.wxs','ShortcutPromptDlg.wxs')) {
    $object = Join-Path $outputPath ([IO.Path]::GetFileNameWithoutExtension($name) + '.wixobj')
    & (Join-Path $Wix 'candle.exe') -nologo -arch x64 -ext (Join-Path $Wix 'WixUtilExtension.dll') -ext (Join-Path $Wix 'WixUIExtension.dll') @definitions -out $object (Join-Path $resourcesPath $name)
    if ($LASTEXITCODE -ne 0) { throw "WiX rejeitou $name ($LASTEXITCODE)." }
}
# Compile only: no MSI/EXE is linked, and nothing is installed.
[xml]$main = Get-Content -LiteralPath (Join-Path $outputPath 'main.wixobj') -Raw
$namespaces = New-Object Xml.XmlNamespaceManager($main.NameTable)
$namespaces.AddNamespace('w', 'http://schemas.microsoft.com/wix/2006/objects')
$tableNames = @($main.SelectNodes('//w:table', $namespaces) | ForEach-Object { $_.name })
foreach ($table in @('ServiceInstall','ServiceControl','MsiLockPermissionsEx','Registry','RemoveRegistry')) {
    if ($tableNames -notcontains $table) { throw "Tabela obrigatória ausente: $table" }
}
Write-Output 'Fontes WiX compiladas: serviço, permissões, idioma e inicialização. Nenhum instalador foi gerado.'
