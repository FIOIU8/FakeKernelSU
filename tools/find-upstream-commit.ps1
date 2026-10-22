#requires -Version 5.1
<#
.SYNOPSIS
  Identify the exact upstream tiann/KernelSU commit this fork was imported from.

.DESCRIPTION
  The fork's import commit (see tools/upstream/upstream-ref.txt) contains
  upstream's "manager/" subtree at the repository root, so its tree hash is
  the hash of upstream's manager/ directory. The GitHub API can report the
  tree hash of manager/ for any upstream commit, which identifies the source
  commit exactly - no guessing from commit messages or dates.
#>
$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false) } catch { }
$repo = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$importCommit = if ($args.Count -ge 1) { $args[0] } else { '1f70e46' }

$headers = @{ 'User-Agent' = 'mcode'; 'Accept' = 'application/vnd.github+json' }
$target = (& git -C $repo rev-parse "$importCommit^{tree}").Trim()
Write-Host "fork import tree hash : $target" -ForegroundColor Cyan
Write-Host ""

$api = 'https://api.github.com/repos/tiann/KernelSU'
$found = $null

# walk upstream's main history newest -> oldest and compare manager/'s tree hash
$page = 1
$stop = $false
while (-not $stop -and $page -le 6) {
    $commits = Invoke-RestMethod -Uri "$api/commits?sha=main&per_page=100&page=$page" -Headers $headers -TimeoutSec 90
    if (-not $commits -or $commits.Count -eq 0) { break }
    foreach ($c in $commits) {
        $sha = $c.sha
        $tree = Invoke-RestMethod -Uri "$api/git/trees/$sha" -Headers $headers -TimeoutSec 90
        $entry = $tree.tree | Where-Object { $_.path -eq 'manager' -and $_.type -eq 'tree' }
        if (-not $entry) { continue }
        $marker = if ($entry.sha -eq $target) { ' <== MATCH' } else { '' }
        Write-Host ("{0}  manager={1}  {2}{3}" -f $sha.Substring(0, 12), $entry.sha.Substring(0, 12), $c.commit.author.date, $marker)
        if ($entry.sha -eq $target) { $found = [pscustomobject]@{ Sha = $sha; Tree = $entry.sha; Message = $c.commit.message; Date = $c.commit.author.date } }
    }
    if ($found) { $stop = $true }
    $page++
}

Write-Host ''
if ($found) {
    Write-Host "UPSTREAM COMMIT: $($found.Sha)" -ForegroundColor Green
    Write-Host "date            : $($found.Date)"
    Write-Host "message         : $($found.Message -replace "`n", ' ' | Select-Object -First 1)"
} else {
    Write-Host 'no matching upstream commit found in the scanned window.' -ForegroundColor Yellow
}
