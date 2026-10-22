#requires -Version 5.1
<#
.SYNOPSIS
  Pull upstream tiann/KernelSU changes into this fork.

.DESCRIPTION
  The fork keeps res/values*/strings.xml byte-identical to upstream and puts
  everything the fork itself adds into res/values*/fake_strings.xml. That
  makes the bulk of an upstream sync mechanical:

    * files the fork does not touch are overwritten with upstream's version
    * files upstream added are copied in
    * files upstream removed are deleted
    * files that BOTH upstream and the fork changed are left alone and listed
      as "manual merge" - those are the ones in
      tools/upstream/allowlist.txt
    * app/src/main/{cpp,aidl,proto,jniLibs} are deleted again if upstream
      brought them back: this fork has no native surface

  Because the build files themselves are in the allowlist, they are never
  overwritten - no patch replay step is needed after a sync.

  Run without -Apply first to see what would change.

.PARAMETER UpstreamRef
  Upstream commit to sync to. A commit in this repository whose tree is
  upstream's manager/ subtree, or a commit in tiann/KernelSU itself (its
  manager/ subtree is detected automatically). Defaults to refs/remotes/upstream/main
  when that ref exists, otherwise the recorded baseline.

.PARAMETER Apply
  Actually write the changes. Without it the script only reports.
#>
[CmdletBinding()]
param(
    [string] $UpstreamRef,
    [switch] $Apply
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false) } catch { }

$repo = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$refFile = Join-Path $repo 'tools\upstream\upstream-ref.txt'
$allowlistPath = Join-Path $repo 'tools\upstream\allowlist.txt'

function Invoke-Git([string[]]$gitArgs) {
    $out = & git -C $repo -c core.safecrlf=false -c core.quotepath=false @gitArgs
    if ($LASTEXITCODE -ne 0) { throw "git $($gitArgs -join ' ') failed" }
    return , @($out)
}

function Get-BlobSha([string]$ref, [string]$path) {
    $out = & git -C $repo rev-parse "$ref`:$path" 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }
    return $out.Trim()
}

# Read a blob as raw bytes. Piping git through PowerShell would decode it as
# text and corrupt binary assets.
function Get-GitBlobBytes([string]$spec) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = 'git'
    $psi.Arguments = "-C `"$repo`" cat-file blob $spec"
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.CreateNoWindow = $true
    $proc = [System.Diagnostics.Process]::Start($psi)
    $ms = New-Object System.IO.MemoryStream
    $proc.StandardOutput.BaseStream.CopyTo($ms)
    $proc.WaitForExit()
    if ($proc.ExitCode -ne 0) { throw "git cat-file blob $spec failed" }
    return $ms.ToArray()
}

function Write-BlobToFile([string]$spec, [string]$destination) {
    $bytes = Get-GitBlobBytes $spec
    $dir = Split-Path -Parent $destination
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllBytes($destination, $bytes)
}

# --------------------------------------------------------------------------
# references
# --------------------------------------------------------------------------
$baselineSha = $null
if (Test-Path $refFile) {
    $line = Get-Content $refFile -Encoding UTF8 |
        Where-Object { $_ -match '^\s*baseline\s+([0-9a-f]{7,40})\b' } | Select-Object -First 1
    if ($line) { $baselineSha = ([regex]::Match($line, '([0-9a-f]{7,40})')).Groups[1].Value }
}
if (-not $baselineSha) { throw 'no baseline recorded in tools/upstream/upstream-ref.txt' }

if (-not $UpstreamRef) {
    # --quiet makes rev-parse exit non-zero when the ref is missing, so this
    # one call is made directly instead of through Invoke-Git.
    $remote = & git -C $repo rev-parse --verify --quiet 'refs/remotes/upstream/main'
    if ($remote) { $UpstreamRef = 'refs/remotes/upstream/main' } else { $UpstreamRef = $baselineSha }
}

$syncSha = (Invoke-Git @('rev-parse', '--verify', "$UpstreamRef^{commit}"))[0].Trim()
$prefix = ''
foreach ($line in (Invoke-Git @('ls-tree', $syncSha))) {
    if ($line -match '^\d+\s+tree\s+[0-9a-f]+\s+manager/?$') { $prefix = 'manager/' }
}
$basePrefix = ''
foreach ($line in (Invoke-Git @('ls-tree', $baselineSha))) {
    if ($line -match '^\d+\s+tree\s+[0-9a-f]+\s+manager/?$') { $basePrefix = 'manager/' }
}

Write-Host "baseline       : $baselineSha" -ForegroundColor Cyan
Write-Host "sync reference : $syncSha ($UpstreamRef)" -ForegroundColor Cyan
Write-Host "layout         : $(if ($prefix) { 'upstream repository (manager/)' } else { 'fork-local baseline' })" -ForegroundColor DarkGray

# --------------------------------------------------------------------------
# file maps
# --------------------------------------------------------------------------
function Get-TreeMap([string]$sha, [string]$pfx) {
    $map = @{}
    $tree = if ($pfx) { Invoke-Git @('ls-tree', '-r', $sha, '--', $pfx) } else { Invoke-Git @('ls-tree', '-r', $sha) }
    foreach ($line in $tree) {
        if ($line -notmatch '^(\d+)\s+\w+\s+([0-9a-f]+)\s+(.+)$') { continue }
        $path = $Matches[3]
        if ($pfx -and $path.StartsWith($pfx)) { $path = $path.Substring($pfx.Length) }
        $map[$path] = @{ sha = $Matches[2]; mode = $Matches[1]; upstreamPath = ($pfx + $Matches[3]) }
    }
    return $map
}

$baseMap = Get-TreeMap $baselineSha $basePrefix
$syncMap = Get-TreeMap $syncSha $prefix

$allowed = @{}
foreach ($line in (Get-Content $allowlistPath -Encoding UTF8)) {
    $t = $line.Trim()
    if ($t -eq '' -or $t.StartsWith('#')) { continue }
    if ($t -match '^([MD])\s+(\S+)') { $allowed[$Matches[2]] = $Matches[1] }
}

# --------------------------------------------------------------------------
# plan
# --------------------------------------------------------------------------
$updated = @(); $added = @(); $removed = @(); $manual = @(); $unchanged = 0

foreach ($path in ($syncMap.Keys | Sort-Object)) {
    $info = $syncMap[$path]
    $localPath = Join-Path $repo ($path -replace '/', '\')
    $localSha = if (Test-Path $localPath) { (Invoke-Git @('hash-object', "--path=$path", $localPath))[0].Trim() } else { $null }

    if ($allowed.ContainsKey($path)) {
        # the fork owns this file: only upstream's changes need a manual merge
        $baseSha = if ($baseMap.ContainsKey($path)) { $baseMap[$path].sha } else { $null }
        if ($baseSha -ne $info.sha) { $manual += $path }
        continue
    }

    if ($null -eq $localSha) { $added += $path; continue }
    if ($localSha -eq $info.sha) { $unchanged++; continue }
    $updated += $path
}

foreach ($path in ($baseMap.Keys | Sort-Object)) {
    if ($syncMap.ContainsKey($path)) { continue }
    if ($allowed.ContainsKey($path) -and $allowed[$path] -eq 'D') { continue }
    $localPath = Join-Path $repo ($path -replace '/', '\')
    if (Test-Path $localPath) { $removed += $path }
}

# --------------------------------------------------------------------------
# report
# --------------------------------------------------------------------------
Write-Host ''
Write-Host "updated : $($updated.Count)   added : $($added.Count)   removed : $($removed.Count)   manual merge : $($manual.Count)   unchanged : $unchanged" -ForegroundColor Cyan
foreach ($p in $updated) { Write-Host "  update  $p" }
foreach ($p in $added) { Write-Host "  add     $p" }
foreach ($p in $removed) { Write-Host "  remove  $p" }
foreach ($p in $manual) { Write-Host "  MANUAL  $p  (upstream and the fork both changed it)" -ForegroundColor Yellow }

if (-not $Apply) {
    Write-Host ''
    Write-Host 'dry run - re-run with -Apply to write the changes.' -ForegroundColor DarkGray
    exit 0
}

# --------------------------------------------------------------------------
# apply
# --------------------------------------------------------------------------
foreach ($path in $updated) {
    $localPath = Join-Path $repo ($path -replace '/', '\')
    Write-BlobToFile $syncMap[$path].upstreamPath $localPath
    Write-Host "updated $path" -ForegroundColor Green
}
foreach ($path in $added) {
    $localPath = Join-Path $repo ($path -replace '/', '\')
    Write-BlobToFile $syncMap[$path].upstreamPath $localPath
    Write-Host "added $path" -ForegroundColor Green
}
foreach ($path in $removed) {
    $localPath = Join-Path $repo ($path -replace '/', '\')
    Remove-Item -LiteralPath $localPath -Force
    Write-Host "removed $path" -ForegroundColor Green
}

# --------------------------------------------------------------------------
# strip: the fork has no native / root surface
# --------------------------------------------------------------------------
# Upstream still ships app/src/main/{cpp,aidl,proto,jniLibs}. Every one of
# those files is declared as removed in allowlist.txt, so the copy above skips
# the ones that exist at the baseline - but a file upstream ADDS later under
# those directories has no allowlist entry and would be copied in. Dropping the
# whole directories keeps the invariant instead of chasing new file names.
# app/build.gradle.kts is allowlisted, so externalNativeBuild / the protobuf
# plugin never come back and no patch replay is needed here.
$stripDirs = @('app/src/main/cpp', 'app/src/main/aidl', 'app/src/main/jniLibs', 'app/src/main/proto')
$stripped = @()
foreach ($d in $stripDirs) {
    $full = Join-Path $repo ($d -replace '/', '\')
    if (Test-Path $full) {
        Remove-Item -LiteralPath $full -Recurse -Force
        $stripped += $d
    }
}
if ($stripped.Count -gt 0) {
    Write-Host ''
    Write-Host "stripped (no native surface in this fork): $($stripped -join ', ')" -ForegroundColor Green
}

# A build file that lost its allowlist entry would silently re-enable the
# native build; fail loudly instead.
$buildFile = Join-Path $repo 'app\build.gradle.kts'
$nativeRef = if (Test-Path $buildFile) {
    Select-String -Path $buildFile -Pattern 'externalNativeBuild' -SimpleMatch
} else { $null }
if ($nativeRef) {
    Write-Host ''
    Write-Host 'ERROR: app/build.gradle.kts now has externalNativeBuild - merge it by hand and drop it again.' -ForegroundColor Red
    exit 1
}

# record the new upstream reference
$date = (Get-Date).ToString('yyyy-MM-dd')
$lines = Get-Content $refFile -Encoding UTF8
$outLines = @()
foreach ($l in $lines) {
    if ($l -match '^\s*upstream\s+') {
        $outLines += "upstream  $syncSha  $date  synced by tools/sync-upstream.ps1"
    } else { $outLines += $l }
}
[System.IO.File]::WriteAllLines($refFile, $outLines, (New-Object System.Text.UTF8Encoding($false)))
Write-Host ''
Write-Host "recorded upstream reference $syncSha in tools/upstream/upstream-ref.txt" -ForegroundColor Green
if ($manual.Count -gt 0) {
    Write-Host ''
    Write-Host "$($manual.Count) file(s) need a manual merge - see the MANUAL list above." -ForegroundColor Yellow
}
