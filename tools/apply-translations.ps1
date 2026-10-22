#requires -Version 5.1
<#
.SYNOPSIS
  Insert fork-owned translations into res/values*/fake_strings.xml.

.DESCRIPTION
  Reads a TSV file with one translation per line:

      <locale>\t<resource name>\t<raw text>

  and appends the entry to that locale's fake_strings.xml, creating the file
  if needed. The raw text is escaped for Android string resources:

      &  ->  &amp;
      <  ->  &lt;
      '  ->  \'

  An apostrophe must be escaped in an Android string resource, otherwise
  aapt2 fails with "Apostrophe not preceded by \".

  Entries that are already present are skipped, so the script can be run
  repeatedly.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $DataFile
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false) } catch { }
$repo = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$res = Join-Path $repo 'app\src\main\res'

function Escape-Android([string]$text) {
    $t = $text.Replace('&', '&amp;').Replace('<', '&lt;').Replace("'", "\'")
    return $t
}

function Get-ExistingNames([string]$path) {
    $set = @{}
    if (-not (Test-Path $path)) { return $set }
    $text = [System.IO.File]::ReadAllText($path)
    foreach ($m in [regex]::Matches($text, '<string\s[^>]*?name="([^"]+)"')) { $set[$m.Groups[1].Value] = $true }
    return $set
}

$lines = [System.IO.File]::ReadAllLines($DataFile, [System.Text.Encoding]::UTF8)
$byLocale = @{}
foreach ($line in $lines) {
    if ($line.Trim() -eq '' -or $line.StartsWith('#')) { continue }
    $parts = $line -split '\|', 3
    if ($parts.Count -lt 3) { throw "malformed line: $line" }
    $locale = $parts[0].Trim()
    $name = $parts[1].Trim()
    $value = $parts[2]
    if (-not $byLocale.ContainsKey($locale)) { $byLocale[$locale] = New-Object System.Collections.ArrayList }
    [void]$byLocale[$locale].Add(@($name, $value))
}

$added = 0
$skipped = 0
foreach ($locale in ($byLocale.Keys | Sort-Object)) {
    $dir = Join-Path $res $locale
    if (-not (Test-Path $dir)) { throw "unknown locale directory: $locale" }
    $path = Join-Path $dir 'fake_strings.xml'
    if (-not (Test-Path $path)) { throw "missing $path" }

    $existing = Get-ExistingNames $path
    $new = New-Object System.Collections.ArrayList
    foreach ($entry in $byLocale[$locale]) {
        if ($existing.ContainsKey($entry[0])) { $skipped++; continue }
        [void]$new.Add("    <string name=`"$($entry[0])`">$(Escape-Android $entry[1])</string>")
        $added++
    }
    if ($new.Count -eq 0) { continue }

    $text = [System.IO.File]::ReadAllText($path)
    $eol = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
    $marker = '</resources>'
    $idx = $text.LastIndexOf($marker)
    if ($idx -lt 0) { throw "no </resources> in $path" }

    $head = $text.Substring(0, $idx).TrimEnd("`r", "`n")
    $block = "    <!-- FakeKernelSU: lab panel -->`n" + ($new -join "`n")
    $out = $head + "`n`n" + $block + "`n" + $marker + "`n"
    $out = $out.Replace("`n", $eol)
    [System.IO.File]::WriteAllText($path, $out, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host ("{0,-16} +{1}" -f $locale, $new.Count)
}

Write-Host ''
Write-Host ("added {0}, skipped {1}" -f $added, $skipped)
