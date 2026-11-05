param(
    [string]$JavaHome = 'C:/Program Files/Java/jdk-21.0.10',
    [string]$FormatterJar,
    [switch]$Check
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent

if (-not $FormatterJar) {
    $FormatterJar = Join-Path (Split-Path $projectRoot -Parent) 'tooling/formatters/google-java-format-1.24.0-all-deps.jar'
}

if (-not (Test-Path -LiteralPath $FormatterJar)) {
    throw '请提供 google-java-format 1.24.0 的 all-deps jar，或通过 -FormatterJar 指定路径。'
}

$files = @(Get-ChildItem (Join-Path $projectRoot 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
$formatArgs = @('--aosp')
if ($Check) { $formatArgs += @('--dry-run', '--set-exit-if-changed') }
else { $formatArgs += '-i' }

# Batch paths to stay below Windows command-line limits.
for ($offset = 0; $offset -lt $files.Count; $offset += 24) {
    $last = [Math]::Min($offset + 23, $files.Count - 1)
    & (Join-Path $JavaHome 'bin/java.exe') -jar $FormatterJar @formatArgs $files[$offset..$last]
    if ($LASTEXITCODE -ne 0) { throw 'Java 排版检查或格式化失败。' }
}

Write-Host "Java 排版完成：$($files.Count) 个文件，AOSP 四空格风格。"
