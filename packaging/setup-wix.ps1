$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $projectRoot '.build-tools'
$archive = Join-Path $toolRoot 'wix314-binaries.zip'
$destination = Join-Path $toolRoot 'wix3'
New-Item -ItemType Directory -Force -Path $toolRoot | Out-Null
$expected = '6AC824E1642D6F7277D0ED7EA09411A508F6116BA6FAE0AA5F2C7DAA2FF43D31'
if (!(Test-Path -LiteralPath $archive) -or (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expected) {
    Invoke-WebRequest 'https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip' -OutFile $archive
}
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expected) { throw 'SHA-256 do WiX inválido.' }
Expand-Archive -LiteralPath $archive -DestinationPath $destination -Force
Write-Host "WiX 3.14.1 pronto em $destination"
