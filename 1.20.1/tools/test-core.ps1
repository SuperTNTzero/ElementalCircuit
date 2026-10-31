param([string]$GsonJar, [string]$JavaHome)
$ErrorActionPreference = 'Stop'
$modRoot = Split-Path $PSScriptRoot -Parent
if (-not $JavaHome) { $JavaHome = $env:JAVA_HOME }
if (-not $JavaHome) { $JavaHome = 'C:/Program Files/Java/jdk-17' }
$javacPath = Join-Path $JavaHome 'bin/javac.exe'
$javaPath = Join-Path $JavaHome 'bin/java.exe'
if (-not (Test-Path -LiteralPath $javacPath)) { throw '请用 -JavaHome 指定 JDK 17 路径' }
if (-not $GsonJar) {
    $candidate = Join-Path $env:USERPROFILE '.m2/repository/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar'
    if (Test-Path -LiteralPath $candidate) { $GsonJar = $candidate }
    else {
        $cached = Get-ChildItem (Join-Path $modRoot '.gradle-home') -Recurse -Filter 'gson-*.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($cached) { $GsonJar = $cached.FullName }
    }
}
if (-not $GsonJar -or -not (Test-Path -LiteralPath $GsonJar)) { throw '请先执行 Gradle 构建，或用 -GsonJar 指定 Gson 2.10.1 jar；本脚本不下载依赖' }
$output = Join-Path $modRoot 'build/core-test'
New-Item -ItemType Directory -Path $output -Force | Out-Null
$localGson = Join-Path $output 'gson.jar'
Copy-Item -LiteralPath $GsonJar -Destination $localGson -Force
$GsonJar = $localGson
$sources = @(Get-ChildItem (Join-Path $modRoot 'src/main/java/dev/elementalcircuits/domain') -Filter '*.java' | ForEach-Object FullName)
$sources += @(Get-ChildItem (Join-Path $modRoot 'src/main/java/dev/elementalcircuits/application') -Recurse -Filter '*.java' | ForEach-Object FullName)
$sources += Join-Path $modRoot 'src/main/java/dev/elementalcircuits/adapter/JsonCodecs.java'
$sources += Join-Path $modRoot 'src/main/java/dev/elementalcircuits/adapter/RuleResources.java'
$sources += Join-Path $modRoot 'src/main/java/dev/elementalcircuits/adapter/WandResources.java'
$sources += Join-Path $modRoot 'src/main/java/dev/elementalcircuits/adapter/WorldInteractionResources.java'
$sources += Join-Path $modRoot 'src/main/java/dev/elementalcircuits/adapter/BlueprintTextCode.java'
$sources += Join-Path $modRoot 'src/main/java/dev/elementalcircuits/adapter/RadianceResources.java'
$sources += Join-Path $modRoot 'src/main/java/dev/elementalcircuits/adapter/AssemblyResources.java'
$sources += Join-Path $modRoot 'src/main/java/dev/elementalcircuits/adapter/LogicResources.java'
$sources += Join-Path $modRoot 'src/main/java/dev/elementalcircuits/adapter/ProjectileResources.java'
$sources += Join-Path $modRoot 'src/main/java/dev/elementalcircuits/adapter/SecondaryResources.java'
$sources += Join-Path $modRoot 'src/main/java/dev/elementalcircuits/client/EditorDraft.java'
$sources += @(Get-ChildItem (Join-Path $modRoot 'src/main/java/dev/elementalcircuits/client/editor') -Filter '*.java' | ForEach-Object FullName)
$sources += Join-Path $modRoot 'src/test/java/dev/elementalcircuits/domain/CoreContracts.java'
$sources += Join-Path $modRoot 'src/test/java/dev/elementalcircuits/domain/EditorEquipmentContracts.java'
$sources += Join-Path $modRoot 'src/test/java/dev/elementalcircuits/domain/DFlipFlopContracts.java'
$sources += Join-Path $modRoot 'src/test/java/dev/elementalcircuits/domain/WorldEffectContracts.java'
$sources += Join-Path $modRoot 'src/test/java/dev/elementalcircuits/domain/RadianceBlueprintContracts.java'
$sources += Join-Path $modRoot 'src/test/java/dev/elementalcircuits/domain/EditorPointerContracts.java'
$sources += Join-Path $modRoot 'src/test/java/dev/elementalcircuits/domain/AssemblyContracts.java'
$sources += Join-Path $modRoot 'src/test/java/dev/elementalcircuits/domain/LogicContracts.java'
$sources += Join-Path $modRoot 'src/test/java/dev/elementalcircuits/domain/ProjectileContracts.java'
$sources += Join-Path $modRoot 'src/test/java/dev/elementalcircuits/domain/SecondaryContracts.java'
$sources += Join-Path $modRoot 'src/test/java/dev/elementalcircuits/domain/BookContracts.java'
$sources += Join-Path $modRoot 'src/test/java/dev/elementalcircuits/domain/RuntimeArchitectureContracts.java'
$sources += Join-Path $modRoot 'src/test/java/dev/elementalcircuits/domain/StateMachineContracts.java'
$sources += Join-Path $modRoot 'src/test/java/dev/elementalcircuits/domain/VisualContracts.java'
$sources += Join-Path $modRoot 'src/test/java/dev/elementalcircuits/domain/AudioContracts.java'
$sources += Join-Path $modRoot 'src/main/java/dev/elementalcircuits/adapter/AudioResources.java'
$sources += Join-Path $modRoot 'src/main/java/dev/elementalcircuits/client/effect/VisualStyles.java'
$sources += Join-Path $modRoot 'src/main/java/dev/elementalcircuits/client/effect/VisualTimeline.java'
& $javacPath -encoding UTF-8 --release 17 -cp $GsonJar -d $output @sources
if ($LASTEXITCODE -ne 0) { throw '核心契约编译失败' }
$classpath = $output + [IO.Path]::PathSeparator + $GsonJar + [IO.Path]::PathSeparator + (Join-Path $modRoot 'src/main/resources')
& $javaPath -cp $classpath dev.elementalcircuits.domain.CoreContracts
if ($LASTEXITCODE -ne 0) { throw '核心契约验证失败' }
