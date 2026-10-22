#requires -Version 5.1
# Print the English source of the fork-owned strings that are missing in the
# non-default locales, plus one existing locale for style reference.
$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false) } catch { }
$repo = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$res = Join-Path $repo 'app\src\main\res'

$missing = @(
    'fake_edit_reset', 'fake_edit_save',
    'fake_feature_status_managed', 'fake_feature_status_supported', 'fake_feature_status_unsupported',
    'fake_follow_system',
    'fake_home_edit_hint', 'fake_home_edit_summary',
    'fake_jailbreak_confirm', 'fake_jailbreak_enabled', 'fake_jailbreak_rebooted',
    'fake_panel_brand', 'fake_panel_brand_in_use', 'fake_panel_brand_summary',
    'fake_panel_compare_value',
    'fake_panel_feature_status', 'fake_panel_feature_status_summary',
    'fake_panel_hide_entry_message',
    'fake_panel_ksu_version', 'fake_panel_ksu_version_summary',
    'fake_panel_lkm_update', 'fake_panel_lkm_update_summary',
    'fake_panel_long_press_hint',
    'fake_panel_manager_version_code', 'fake_panel_manager_version_code_summary',
    'fake_panel_manager_version_name', 'fake_panel_manager_version_name_summary',
    'fake_panel_mode', 'fake_panel_naming', 'fake_panel_runtime',
    'fake_panel_seccomp_status', 'fake_panel_selinux_mode', 'fake_panel_stealth_summary',
    'fake_panel_system_info', 'fake_panel_version_mode'
)

function Get-Strings([string]$path) {
    $map = @{}
    if (-not (Test-Path $path)) { return $map }
    $text = [System.IO.File]::ReadAllText($path)
    foreach ($m in [regex]::Matches($text, '<string\s[^>]*?name="([^"]+)"[^>]*>(.*?)</string>', 'Singleline')) {
        $map[$m.Groups[1].Value] = $m.Groups[2].Value
    }
    return $map
}

$en = Get-Strings (Join-Path $res 'values\fake_strings.xml')
Write-Host '=== English source ===' -ForegroundColor Cyan
foreach ($n in $missing) { Write-Host ("{0,-42} {1}" -f $n, $en[$n]) }

foreach ($loc in @('values-de', 'values-fr', 'values-ru', 'values-ja')) {
    $t = Get-Strings (Join-Path $res "$loc\fake_strings.xml")
    Write-Host ''
    Write-Host "=== $loc : existing translations for style reference ===" -ForegroundColor Cyan
    foreach ($n in @($t.Keys | Sort-Object)) {
        if ($n -like 'fake_panel_*' -or $n -like 'fake_follow*' -or $n -like 'fake_feature*') {
            Write-Host ("{0,-42} {1}" -f $n, $t[$n])
        }
    }
}
