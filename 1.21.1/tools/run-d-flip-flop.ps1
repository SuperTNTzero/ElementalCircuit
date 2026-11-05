param([string]$JavaHome = 'C:/Program Files/Java/jdk-21.0.10', [switch]$Plot)
$ErrorActionPreference = 'Stop'
$experimentRoot = Split-Path $PSScriptRoot -Parent
& (Join-Path $PSScriptRoot 'test-core.ps1') -JavaHome $JavaHome
$experimentClasspath = (Join-Path $experimentRoot 'build/core-test') + [IO.Path]::PathSeparator +
    (Join-Path $experimentRoot 'build/core-test/gson.jar') + [IO.Path]::PathSeparator +
    (Join-Path $experimentRoot 'src/main/resources')
& (Join-Path $JavaHome 'bin/java.exe') '-Dfile.encoding=UTF-8' -cp $experimentClasspath dev.elementalcircuits.domain.DFlipFlopContracts $experimentRoot
if ($LASTEXITCODE -ne 0) { throw 'D触发器实验失败' }
if ($Plot) {
    python (Join-Path $PSScriptRoot 'plot-d-flip-flop.py')
    if ($LASTEXITCODE -ne 0) { throw '实验图生成失败；需要Python及matplotlib' }
}
