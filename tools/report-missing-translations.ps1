#requires -Version 5.1
# Report which fork-owned string resources are missing in which locale.
$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false) } catch { }
$repo = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$res = Join-Path $repo 'app\src\main\res'

function Get-Names([string]$path) {
    $map = @{}
    if (-not (Test-Path $path)) { return $map }
    $text = [System.IO.File]::ReadAllText($path)
    foreach ($m in [regex]::Matches($text, '<string\s[^>]*?name="([^"]+)"[^>]*>(.*?)</string>', 'Singleline')) {
        $map[$m.Groups[1].Value] = $m.Groups[2].Value
    }
    return $map
}

$dirs = Get-ChildItem -Path $res -Directory -Filter 'values*' |
    Where-Object { Test-Path (Join-Path $_.FullName 'fake_strings.xml') } | Sort-Object Name

$default = Get-Names (Join-Path $res 'values\fake_strings.xml')
$forkNames = @($default.Keys | Sort-Object)

$missing = @{}
foreach ($d in $dirs) {
    $have = Get-Names (Join-Path $d.FullName 'fake_strings.xml')
    $miss = @($forkNames | Where-Object { -not $have.ContainsKey($_) })
    $missing[$d.Name] = $miss
}

Write-Host '=== English source of the names missing in every locale ===' -ForegroundColor Cyan
$everywhereCount = @($forkNames | Where-Object {
    $n = $_
    @($missing.Keys | Where-Object { $n -in $missing[$_] }).Count -eq $dirs.Count
})
foreach ($n in $everywhereCount) {
    Write-Host ("  {0,-42} {1}" -f $n, $default[$n])
}

Write-Host ''
Write-Host "=== per-locale gap ===" -ForegroundColor Cyan
foreach ($d in $dirs) {
    $miss = $missing[$d.Name]
    if ($miss.Count -eq 0) { continue }
    $unique = @($miss | Sort-Object -Unique)
    Write-Host ("  {0,-14} missing {1,3}  ({2})" -f $d.Name, $miss.Count, ($unique -join ', '))
}
Write-Host ''
Write-Host ("total missing entries: {0}" -f (@($missing.Values | ForEach-Object { $_ }).Count))
