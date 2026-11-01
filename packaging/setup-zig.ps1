param([string]$Destination = (Join-Path $PSScriptRoot '../.build-tools'))
$ErrorActionPreference = 'Stop'
$version = '0.15.2'
$archive = Join-Path $Destination "zig-$version.zip"
$expected = '3a0ed1e8799a2f8ce2a6e6290a9ff22e6906f8227865911fb7ddedc3cc14cb0c'
New-Item -ItemType Directory -Force -Path $Destination | Out-Null
if (-not (Test-Path -LiteralPath $archive)) {
    Invoke-WebRequest -Uri "https://ziglang.org/download/$version/zig-x86_64-windows-$version.zip" -OutFile $archive
}
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) {
    throw 'O SHA-256 do compilador Zig não corresponde ao publicado pelo projeto.'
}
$compiler = Join-Path $Destination "zig-x86_64-windows-$version/zig.exe"
if (-not (Test-Path -LiteralPath $compiler)) { Expand-Archive -LiteralPath $archive -DestinationPath $Destination -Force }
Write-Output $compiler
