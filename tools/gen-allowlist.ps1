#requires -Version 5.1
<#
.SYNOPSIS
  Generate the initial divergence allowlist from the current tree.

.DESCRIPTION
  Produces tools/upstream/allowlist.txt listing every upstream file this
  fork deliberately modifies or removes, with the reason. Run once; after
  that the list is maintained by hand and enforced by
  tools/verify-divergence.ps1.
#>
$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false) } catch { }
$repo = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$ref = if ($args.Count -ge 1) { $args[0] } else { '1f70e46' }

$out = & git -C $repo -c core.safecrlf=false diff --name-status $ref
if ($LASTEXITCODE -ne 0) { throw "git diff failed" }

$rows = @()
foreach ($line in $out) {
    if ($line -notmatch '^([AMDR])\s+(.+)$') { continue }
    $status = $Matches[1]
    $path = $Matches[2]
    if ($status -eq 'A') { continue }   # fork-only file, not an upstream divergence
    $rows += [pscustomobject]@{ Status = $status; Path = $path }
}

Write-Host "upstream divergence surface: $($rows.Count) file(s) (modified=$(@($rows | Where-Object Status -eq 'M').Count), deleted=$(@($rows | Where-Object Status -eq 'D').Count))"
$rows | Sort-Object Path | ForEach-Object { "{0}  {1}" -f $_.Status, $_.Path } | Write-Host
