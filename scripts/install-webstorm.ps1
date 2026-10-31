<#
.SYNOPSIS
Устанавливает собранный ZIP в указанный профиль WebStorm с резервной копией.
.EXAMPLE
.\scripts\install-webstorm.ps1 -PluginZip '.\build\distributions\tree-description-plugin-2.8.1.zip' -ConfigPath "$env:APPDATA\JetBrains\WebStorm2026.2"
#>
param(
    [Parameter(Mandatory = $true)][string]$PluginZip,
    [Parameter(Mandatory = $true)][string]$ConfigPath
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$config = (Resolve-Path -LiteralPath $ConfigPath).Path
if ((Split-Path -Leaf $config) -notmatch '^WebStorm\d{4}\.\d') { throw 'Укажите профиль WebStorm.' }
$plugins = Join-Path $config 'plugins'
$target = [IO.Path]::GetFullPath((Join-Path $plugins 'tree-description-plugin'))
if ([IO.Path]::GetDirectoryName($target) -ne [IO.Path]::GetFullPath($plugins)) { throw 'Путь установки вне plugins.' }
$stage = Join-Path ([IO.Path]::GetTempPath()) ('tree-description-install-' + [guid]::NewGuid().ToString('N'))
Expand-Archive -LiteralPath (Resolve-Path -LiteralPath $PluginZip).Path -DestinationPath $stage
$source = Join-Path $stage 'tree-description-plugin'
$pluginId = 'com.github.chinese-annotations'
$version = $null
Get-ChildItem -LiteralPath (Join-Path $source 'lib') -Filter '*.jar' | ForEach-Object {
    $jar = [IO.Compression.ZipFile]::OpenRead($_.FullName)
    try {
        $entry = $jar.GetEntry('META-INF/plugin.xml')
        if ($entry) {
            $reader = [IO.StreamReader]::new($entry.Open())
            try { [xml]$descriptor = $reader.ReadToEnd() } finally { $reader.Dispose() }
            if ($descriptor.'idea-plugin'.id -ne $pluginId) { throw 'Неверный ID плагина.' }
            $version = $descriptor.'idea-plugin'.version
        }
    } finally { $jar.Dispose() }
}
if (-not $version) { throw 'В ZIP не найден дескриптор Tree Description.' }
$backup = Join-Path $config ('tree-description-backups\' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$backup = [IO.Path]::GetFullPath($backup)
if (-not $backup.StartsWith($config + [IO.Path]::DirectorySeparatorChar)) { throw 'Путь копии вне профиля.' }
New-Item -ItemType Directory -Path $backup -Force | Out-Null
New-Item -ItemType Directory -Path $plugins -Force | Out-Null
$disabled = Join-Path $config 'disabled_plugins.txt'
if (Test-Path -LiteralPath $disabled) { Copy-Item -LiteralPath $disabled -Destination $backup }
$previous = Join-Path $backup 'tree-description-plugin'
if (Test-Path -LiteralPath $target) { Move-Item -LiteralPath $target -Destination $previous }
try {
    Move-Item -LiteralPath $source -Destination $target
} catch {
    if ((Test-Path -LiteralPath $previous) -and -not (Test-Path -LiteralPath $target)) {
        Move-Item -LiteralPath $previous -Destination $target
    }
    throw
}
if (Test-Path -LiteralPath $disabled) {
    $lines = @(Get-Content -LiteralPath $disabled | Where-Object { $_.Trim() -ne $pluginId })
    [IO.File]::WriteAllLines($disabled, [string[]]$lines, [Text.UTF8Encoding]::new($false))
}
Write-Output "Установлен Tree Description $version. Перезапустите WebStorm."
Write-Output "Резервная копия: $backup"
