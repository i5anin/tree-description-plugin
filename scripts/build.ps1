<#
.SYNOPSIS
Проверяет тесты и собирает ZIP для установки в JetBrains IDE.
.EXAMPLE
.\scripts\build.ps1 -JavaHome 'C:\Java\jdk-21' -LocalIdePath 'C:\SDK\idea-2023.1.5'
#>
param(
    [Parameter(Mandatory = $true)][string]$JavaHome,
    [string]$LocalIdePath
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) {
    throw 'JavaHome должен указывать на JDK 17 или 21.'
}
$previousJavaHome = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $JavaHome
    $buildArguments = @('-p', $projectRoot, 'test', 'buildPlugin', '--console=plain')
    if ($LocalIdePath) { $buildArguments += "-PlocalIdePath=$LocalIdePath" }
    & (Join-Path $projectRoot 'gradlew.bat') @buildArguments
    if ($LASTEXITCODE -ne 0) { throw "Сборка завершилась с кодом $LASTEXITCODE" }
    Get-ChildItem -LiteralPath (Join-Path $projectRoot 'build\distributions') -Filter '*.zip'
} finally {
    $env:JAVA_HOME = $previousJavaHome
}
