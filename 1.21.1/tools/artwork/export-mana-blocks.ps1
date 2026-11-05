param([string]$Aseprite='E:/Aseprite-v1.3.15.5/build/bin/aseprite.exe')
$ErrorActionPreference='Stop'
$modRoot=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
& python (Join-Path $PSScriptRoot 'draw-mana-blocks.py')
if ($LASTEXITCODE -ne 0) { throw '像素资源生成失败' }
$source=Join-Path $modRoot 'art/aseprite/mana-blocks'
$destination=Join-Path $modRoot 'src/main/resources/assets/elementalcircuits/textures/block'
Get-ChildItem -LiteralPath $source -Filter '*.png' | ForEach-Object {
    $project=[IO.Path]::ChangeExtension($_.FullName,'.aseprite')
    & $Aseprite -b $_.FullName --save-as $project
    if ($LASTEXITCODE -ne 0) { throw 'Aseprite源文件保存失败' }
    & $Aseprite -b $project --save-as (Join-Path $destination $_.Name)
    if ($LASTEXITCODE -ne 0) { throw 'Aseprite贴图导出失败' }
}
