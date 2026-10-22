#requires -Version 5.1
<#
.SYNOPSIS
  Give every fork-owned string resource a "fake_" prefix.

.DESCRIPTION
  Upstream tiann/KernelSU ships 375 resource names. This fork adds 90 more.
  86 of them already start with "fake_", but 6 do not:

      launcher_label_fake / launcher_label_ksu / launcher_label_manager / launcher_label_short
      home_edit_summary / home_edit_hint

  Those 6 live in the fork-only file res/values*/fake_strings.xml, so they are
  invisible to upstream. The risk is that upstream later introduces a resource
  with one of those exact names inside its own res/values*/strings.xml. Because
  both files belong to the same resource configuration, aapt2 then reports a
  duplicate resource and the build fails - a much harder failure mode than a
  resolvable git conflict.

  Prefixing them with "fake_" makes collision impossible by construction and
  gives the whole resource split one mechanical invariant:

      every <string> in fake_strings.xml starts with "fake_"
      every <string> in strings.xml      belongs to upstream

  The script is idempotent and preserves each file's BOM/line-ending style.
#>
[CmdletBinding(SupportsShouldProcess)]
param(
    [switch] $DryRun
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $PSCommandPath)

$renameMap = [ordered]@{
    'launcher_label_fake'    = 'fake_launcher_label_fake'
    'launcher_label_ksu'     = 'fake_launcher_label_ksu'
    'launcher_label_manager' = 'fake_launcher_label_manager'
    'launcher_label_short'   = 'fake_launcher_label_short'
    'home_edit_summary'      = 'fake_home_edit_summary'
    'home_edit_hint'         = 'fake_home_edit_hint'
}

function Read-Text([string]$path) {
    $bytes = [System.IO.File]::ReadAllBytes($path)
    $bom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
    $text = [System.Text.Encoding]::UTF8.GetString($bytes)
    if ($bom) { $text = $text.Substring(1) }
    return [pscustomobject]@{ Text = $text; Bom = $bom }
}

function Write-Text([string]$path, [string]$text, [bool]$bom) {
    $enc = New-Object System.Text.UTF8Encoding($bom)
    [System.IO.File]::WriteAllText($path, $text, $enc)
}

$targets = @()
$targets += Get-ChildItem -Path (Join-Path $repo 'app\src\main\res') -Directory -Filter 'values*' |
    ForEach-Object { Join-Path $_.FullName 'strings.xml' } |
    Where-Object { Test-Path $_ }
$targets += Join-Path $repo 'app\src\main\AndroidManifest.xml'
$targets += Join-Path $repo 'app\src\main\java\me\weishu\kernelsu\fake\FakeState.kt'
$targets += Join-Path $repo 'app\src\main\java\me\weishu\kernelsu\ui\screen\home\EditSystemInfoDialog.kt'
$targets = @($targets | Where-Object { Test-Path $_ })

$totalHits = 0
$changedFiles = 0

foreach ($path in $targets) {
    $info = Read-Text $path
    $text = $info.Text
    $hits = 0
    foreach ($old in $renameMap.Keys) {
        $new = $renameMap[$old]
        # Only rename the resource identifier, never a substring of a longer one.
        $pattern = '(?<![A-Za-z0-9_])' + [regex]::Escape($old) + '(?![A-Za-z0-9_])'
        $count = ([regex]::Matches($text, $pattern)).Count
        if ($count -gt 0) {
            $text = [regex]::Replace($text, $pattern, $new)
            $hits += $count
        }
    }
    if ($hits -gt 0) {
        $totalHits += $hits
        $changedFiles++
        Write-Host ("  {0,-60} {1} occurrence(s)" -f (Split-Path -Leaf $path), $hits)
        if (-not $DryRun -and $PSCmdlet.ShouldProcess($path, 'rename fork resource identifiers')) {
            Write-Text $path $text $info.Bom
        }
    }
}

Write-Host ""
Write-Host ("renamed {0} occurrence(s) across {1} file(s){2}" -f $totalHits, $changedFiles, $(if ($DryRun) { ' [dry run]' } else { '' }))

if ($totalHits -eq 0) {
    Write-Host 'nothing to do - already renamed.'
}
