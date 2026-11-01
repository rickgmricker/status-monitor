param([string]$Zig = (Join-Path $PSScriptRoot '../../.build-tools/zig-x86_64-windows-0.15.2/zig.exe'),
      [string]$Output = (Join-Path $PSScriptRoot '../../build/collector/payload'))
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Zig)) { throw 'Compilador não encontrado. Execute packaging/setup-zig.ps1 ou informe -Zig.' }
New-Item -ItemType Directory -Force -Path $Output | Out-Null
$env:ZIG_GLOBAL_CACHE_DIR = Join-Path $PSScriptRoot '../../.build-tools/zig-cache'
$env:ZIG_LOCAL_CACHE_DIR = Join-Path $PSScriptRoot '../../build/collector/zig-cache'
& $Zig cc -target x86_64-windows-gnu -std=c11 -Os -Wall -Wextra -Werror -municode -s (Join-Path $PSScriptRoot 'collector.c') -ladvapi32 -o (Join-Path $Output 'StatusMonitorCollector.exe')
if ($LASTEXITCODE -ne 0) { throw "Falha ao compilar o coletor ($LASTEXITCODE)." }
& (Join-Path $Output 'StatusMonitorCollector.exe') --self-test
if ($LASTEXITCODE -ne 0) { throw 'Falha na validação do protocolo nativo.' }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot '../../tools/presentmon/PresentMon.exe') -Destination $Output -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot '../../tools/presentmon/LICENSE.txt') -Destination (Join-Path $Output 'PresentMon-LICENSE.txt') -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'manage-service.ps1') -Destination $Output -Force
