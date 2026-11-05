param(
    [string[]]$Tasks = @('build'),
    [string]$JavaHome,
    [switch]$GuideSmoke,
    [switch]$RelationSmoke,
    [switch]$EcologySmoke,
    [switch]$EnchantmentSmoke,
    [switch]$NoPatchouli,
    [switch]$Mirror,
    [switch]$Offline,
    [switch]$EditorSmoke,
    [switch]$AudioSmoke,
    [switch]$VfxSmoke,
    [switch]$WandSmoke,
    [switch]$CombatSmoke,
    [switch]$EquipmentSmoke,
    [switch]$StandardSmoke,
    [switch]$ControlSmoke,
    [switch]$BlueprintSmoke,
    [switch]$CrystalSmoke,
    [switch]$BoardSmoke,
    [switch]$KeepTestWorldOpen,
    [switch]$FieldSmoke,
    [switch]$StationSmoke,
    [switch]$ShapeSmoke,
    [switch]$BookSmoke,
    [switch]$RecursionSmoke,
    [switch]$ProbeSmoke,
    [switch]$ProgressionSmoke,
    [switch]$ElementalSmoke,
    [switch]$SettingsSmoke,
    [switch]$Showcase
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$versionsRoot = Split-Path $projectRoot -Parent
function Test-Java21([string]$Candidate) {
    if (-not $Candidate) { return $false }
    $binary = Join-Path $Candidate 'bin/java.exe'
    if (-not (Test-Path -LiteralPath $binary)) { return $false }
    $versionText = (& $binary -version 2>&1 | Out-String)
    return $versionText -match 'version "21[.\"]'
}
if (-not $JavaHome -and (Test-Java21 $env:JAVA_HOME)) { $JavaHome = $env:JAVA_HOME }
if (-not $JavaHome) {
    $detectedJdk = Get-ChildItem 'C:/Program Files/Java' -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
    if ($detectedJdk) { $JavaHome = $detectedJdk.FullName }
}
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/javac.exe'))) { throw '请设置 JAVA_HOME 或传入 -JavaHome，指向 JDK 21。' }
if (-not (Test-Java21 $JavaHome)) { throw '本工程的Gradle启动和编译都使用JDK 21，请更正 -JavaHome。' }
$oldJavaHome = $env:JAVA_HOME
$oldGradleHome = $env:GRADLE_USER_HOME
try {
    $env:JAVA_HOME = $JavaHome
    $env:GRADLE_USER_HOME = Join-Path $versionsRoot 'tooling/gradle-home'
    $gradleCommand = Join-Path $versionsRoot 'tooling/gradle-9.2.1/bin/gradle.bat'
    if (-not (Test-Path -LiteralPath $gradleCommand)) { $gradleCommand = Join-Path $projectRoot 'gradlew.bat' }
    $gradleArgs = @('--no-daemon', '--console=plain')
    if ($EnchantmentSmoke) { $gradleArgs += '-PenchantmentSmoke=true' }
    if ($EcologySmoke) { $gradleArgs += '-PecologySmoke=true' }
    if ($RelationSmoke) { $gradleArgs += '-PrelationSmoke=true' }
    if ($GuideSmoke) { $gradleArgs += '-PguideSmoke=true' }
    if ($BoardSmoke) { $gradleArgs += '-PboardSmoke=true' }
    if ($NoPatchouli) { $gradleArgs += '-PwithoutPatchouli=true' }
    if ($Mirror) { $gradleArgs += '-PuseDependencyMirror=true' }
    if ($Offline) { $gradleArgs += '--offline' }
    if ($Tasks -contains 'runGameTestServer') { $gradleArgs += '-PwithGameTests=true' }
    if ($EditorSmoke) { $gradleArgs += '-PeditorSmoke=true' }
    if ($Showcase) { $gradleArgs += @('-PeditorSmoke=true', '-PshowcaseCapture=true') }
    if ($AudioSmoke) { $gradleArgs += '-PaudioSmoke=true' }
    if ($EquipmentSmoke) { $gradleArgs += '-PequipmentSmoke=true' }
    if ($CrystalSmoke) { $gradleArgs += '-PcrystalSmoke=true' }
    if ($BlueprintSmoke) { $gradleArgs += '-PblueprintSmoke=true' }
    if ($KeepTestWorldOpen) { $gradleArgs += '-PkeepTestWorldOpen=true' }
    if ($StandardSmoke) { $gradleArgs += '-PstandardSmoke=true' }
    if ($ControlSmoke) { $gradleArgs += '-PcontrolSmoke=true' }
    if ($CombatSmoke) { $gradleArgs += '-PcombatSmoke=true' }
    if ($WandSmoke) { $gradleArgs += '-PwandSmoke=true' }
    if ($StationSmoke) { $gradleArgs += '-PstationSmoke=true' }
    if ($ShapeSmoke) { $gradleArgs += '-PshapeSmoke=true' }
    if ($ProgressionSmoke) { $gradleArgs += '-PprogressionSmoke=true' }
    if ($ElementalSmoke) { $gradleArgs += '-PelementalSmoke=true' }
    if ($SettingsSmoke) { $gradleArgs += '-PsettingsSmoke=true' }
    if ($ProbeSmoke) { $gradleArgs += '-PprobeSmoke=true' }
    if ($RecursionSmoke) { $gradleArgs += '-PrecursionSmoke=true' }
    if ($BookSmoke) { $gradleArgs += '-PbookSmoke=true' }
    if ($FieldSmoke) { $gradleArgs += '-PfieldSmoke=true' }
    if ($VfxSmoke) { $gradleArgs += '-PvfxSmoke=true' }
    Push-Location $projectRoot
    try {
        & $gradleCommand @gradleArgs @Tasks
        if ($LASTEXITCODE -ne 0) { throw "Gradle 执行失败：$LASTEXITCODE" }
    } finally { Pop-Location }
} finally {
    $env:JAVA_HOME = $oldJavaHome
    $env:GRADLE_USER_HOME = $oldGradleHome
}
