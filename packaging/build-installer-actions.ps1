param([string]$Zig = (Join-Path $PSScriptRoot '../.build-tools/zig-x86_64-windows-0.15.2/zig.exe'),
      [string]$Output = (Join-Path $PSScriptRoot '../build/packaging/installer-actions.dll'))
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Output) | Out-Null
$env:ZIG_GLOBAL_CACHE_DIR = Join-Path $PSScriptRoot '../.build-tools/zig-cache'
$env:ZIG_LOCAL_CACHE_DIR = Join-Path $PSScriptRoot '../build/collector/zig-cache'
& $Zig cc -target x86_64-windows-gnu -std=c11 -Os -Wall -Wextra -Werror -shared -s (Join-Path $PSScriptRoot '../native/installer/actions.c') -ladvapi32 -o $Output
if ($LASTEXITCODE -ne 0) { throw "Falha ao compilar a limpeza da inicialização ($LASTEXITCODE)." }
