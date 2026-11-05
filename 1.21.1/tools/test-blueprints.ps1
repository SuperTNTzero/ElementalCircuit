param([switch]$Interactive,[switch]$Offline,[switch]$Mirror,[string]$JavaHome)
$ErrorActionPreference = 'Stop'
# Uses only build/blueprint-smoke; never opens or modifies a normal game profile.
& (Join-Path $PSScriptRoot 'build.ps1') -Tasks runClient -BlueprintSmoke -KeepTestWorldOpen:$Interactive -Offline:$Offline -Mirror:$Mirror -JavaHome $JavaHome
if ($LASTEXITCODE -ne 0) { throw '蓝图簿注入测试失败，请检查客户端日志' }
