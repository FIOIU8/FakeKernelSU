#requires -Version 5.1
<#
.SYNOPSIS
  Verify that this fork's divergence from upstream tiann/KernelSU stays
  explicit and safe.

.DESCRIPTION
  Two kinds of reference are used.

    baseline  the fork's import commit, recorded in
              tools/upstream/upstream-ref.txt. Its tree is upstream's
              manager/ subtree, so the paths line up 1:1 with the fork root.
              The fork's own rules are always checked against this.

    sync      an upstream commit, optionally from tiann/KernelSU itself
              (its manager/ subtree is detected automatically). This is where
              "is the fork still in sync with upstream" is answered.

  STRICT RULES - always failures
    1. Every upstream file whose content the fork changes is declared in
       tools/upstream/allowlist.txt.
    2. Every upstream file the fork removes on purpose is declared there.
    3. No resource name is defined twice within one configuration.
    4. Every name in res/values*/fake_strings.xml that starts with "fake_"
       is unknown to upstream, so upstream can never collide with it.
    5. No name in fake_strings.xml is also provided by upstream for the same
       locale. If upstream starts shipping that translation, aapt2 would
       reject the build with a duplicate resource error - this rule catches
       that before the build does.
    6. Every name in values/fake_strings.xml is also present in each
       values-<locale>/fake_strings.xml, and keeps the same %1$s / %1$d
       placeholders. A missing translation silently falls back to English,
       which for a 45-language manager is a regression nobody notices.
    7. The lineage commit recorded in tools/upstream/upstream-ref.txt still
       exists, still merges the fork with the upstream import source, and is
       still an ancestor of HEAD. Losing it silently turns "git merge
       -X subtree=manager upstream/main" back into a file-by-file overwrite.

  SYNC DRIFT - reported against the sync reference
    * files the fork still has that upstream no longer has
    * files whose content differs from upstream's current content
    * locales whose res/values*/strings.xml no longer matches upstream
    These are warnings by default and failures with -SyncRequired.

.PARAMETER UpstreamRef
  Commit to check for sync drift against. Defaults to the baseline.

.PARAMETER SyncRequired
  Turn sync drift into failures. Used by the scheduled CI job that tells the
  maintainer "upstream moved on, run tools/sync-upstream.ps1".
#>
[CmdletBinding()]
param(
    [string] $UpstreamRef,
    [switch] $SyncRequired
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false) } catch { }

$repo = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$resRoot = Join-Path $repo 'app\src\main\res'
$allowlistPath = Join-Path $repo 'tools\upstream\allowlist.txt'
$refFile = Join-Path $repo 'tools\upstream\upstream-ref.txt'

$failures = New-Object System.Collections.ArrayList
$warnings = New-Object System.Collections.ArrayList

function Write-Section([string]$title) {
    Write-Host ''
    Write-Host "== $title" -ForegroundColor Cyan
}

function Invoke-Git([string[]]$gitArgs) {
    $out = & git -C $repo -c core.safecrlf=false -c core.quotepath=false @gitArgs
    if ($LASTEXITCODE -ne 0) { throw "git $($gitArgs -join ' ') failed" }
    return , @($out)
}

function Get-UpstreamText([string]$ref, [string]$rel) {
    $out = & git -C $repo show "${ref}:$rel"
    if ($LASTEXITCODE -ne 0) { return $null }
    return ((($out -join "`n") -replace "`r`n", "`n").TrimEnd("`r", "`n"))
}

function Get-Names([string]$xml) {
    if (-not $xml) { return @() }
    $n = @()
    foreach ($m in [regex]::Matches($xml, '<string\s[^>]*?name="([^"]+)"')) { $n += $m.Groups[1].Value }
    return $n
}

function Get-LocalText([string]$path) {
    if (-not (Test-Path $path)) { return $null }
    return (([System.IO.File]::ReadAllText($path) -replace "`r`n", "`n").TrimEnd("`r", "`n"))
}

# Resolve a reference into { sha, prefix }. The prefix is "manager/" when the
# reference lives in upstream's own repository.
function Resolve-Reference([string]$ref) {
    $sha = (Invoke-Git @('rev-parse', '--verify', "$ref^{commit}"))[0].Trim()
    $prefix = ''
    foreach ($line in (Invoke-Git @('ls-tree', $sha))) {
        if ($line -match '^\d+\s+tree\s+[0-9a-f]+\s+manager/?$') { $prefix = 'manager/' }
    }
    return [pscustomobject]@{ Sha = $sha; Prefix = $prefix }
}

# Compare the fork's working tree with one reference.
function Compare-WithReference($reference) {
    $upFiles = @{}
    $tree = if ($reference.Prefix) { Invoke-Git @('ls-tree', '-r', $reference.Sha, '--', $reference.Prefix) }
            else { Invoke-Git @('ls-tree', '-r', $reference.Sha) }
    foreach ($line in $tree) {
        if ($line -notmatch '^(\d+)\s+\w+\s+([0-9a-f]+)\s+(.+)$') { continue }
        $path = $Matches[3]
        if ($reference.Prefix -and $path.StartsWith($reference.Prefix)) { $path = $path.Substring($reference.Prefix.Length) }
        $upFiles[$path] = @{ sha = $Matches[2]; mode = $Matches[1] }
    }

    $indexModes = @{}
    foreach ($line in (Invoke-Git @('ls-files', '-s'))) {
        if ($line -notmatch '^(\d+)\s+([0-9a-f]+)\s+\d+\t(.+)$') { continue }
        $indexModes[$Matches[3]] = $Matches[1]
    }

    $divergent = @{}
    $missing = @()
    foreach ($path in $upFiles.Keys) {
        $localPath = Join-Path $repo ($path -replace '/', '\')
        if (-not (Test-Path $localPath)) { $missing += $path; continue }
        $mode = if ($indexModes.ContainsKey($path)) { $indexModes[$path] } else { '100644' }
        # hash the working tree file, not the index: the index may still hold
        # the pre-edit blob for files that were changed but not staged.
        $sha = (Invoke-Git @('hash-object', "--path=$path", $localPath))[0].Trim()
        if ($sha -ne $upFiles[$path].sha -or $mode -ne $upFiles[$path].mode) { $divergent[$path] = $true }
    }
    return [pscustomobject]@{ UpFiles = $upFiles; Divergent = $divergent; Missing = $missing }
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

$baseline = Resolve-Reference $baselineSha
Write-Host "baseline           : $($baseline.Sha)" -ForegroundColor Cyan

$sync = $baseline
if ($UpstreamRef) {
    $sync = Resolve-Reference $UpstreamRef
    Write-Host "sync reference     : $($sync.Sha)" -ForegroundColor Cyan
} else {
    Write-Host "sync reference     : (baseline)" -ForegroundColor DarkGray
}

# --------------------------------------------------------------------------
# strict rules 1-2: the fork's declared divergence from the baseline
# --------------------------------------------------------------------------
Write-Section 'strict rules 1-2: declared divergence from the baseline'

$allowed = @{}
foreach ($line in (Get-Content $allowlistPath -Encoding UTF8)) {
    $t = $line.Trim()
    if ($t -eq '' -or $t.StartsWith('#')) { continue }
    if ($t -match '^([MD])\s+(\S+)') { $allowed[$Matches[2]] = $Matches[1] }
}

$baseCompare = Compare-WithReference $baseline

foreach ($path in ($baseCompare.Divergent.Keys | Sort-Object)) {
    if (-not $allowed.ContainsKey($path)) {
        [void]$failures.Add("upstream file '$path' is changed by the fork but is not declared in tools/upstream/allowlist.txt")
    } elseif ($allowed[$path] -ne 'M') {
        [void]$failures.Add("upstream file '$path' is declared '$($allowed[$path])' in the allowlist but is actually modified")
    }
}
foreach ($path in ($baseCompare.Missing | Sort-Object)) {
    if (-not $allowed.ContainsKey($path)) {
        [void]$failures.Add("upstream file '$path' was removed by the fork but is not declared in tools/upstream/allowlist.txt")
    } elseif ($allowed[$path] -ne 'D') {
        [void]$failures.Add("upstream file '$path' is absent in the fork but the allowlist says '$($allowed[$path])'")
    }
}
foreach ($path in ($allowed.Keys | Sort-Object)) {
    if (-not $baseCompare.Divergent.ContainsKey($path) -and -not ($baseCompare.Missing -contains $path)) {
        [void]$warnings.Add("allowlist entry '$path' is stale: the fork no longer diverges from upstream there")
    }
}

Write-Host ("  upstream files compared : {0}" -f $baseCompare.UpFiles.Count)
Write-Host ("  changed by the fork     : {0}" -f $baseCompare.Divergent.Count)
Write-Host ("  removed by the fork     : {0}" -f $baseCompare.Missing.Count)
Write-Host ("  allowlist entries       : {0}" -f $allowed.Count)

# --------------------------------------------------------------------------
# strict rule 7: the upstream lineage is intact
# --------------------------------------------------------------------------
# The import is a squash, so the fork's own 3-way merge base comes from a
# graft merge (see docs/upstream-sync.md). If that commit is ever dropped -
# for example by re-importing upstream as a fresh squash - "git merge
# -X subtree=manager upstream/main" silently degrades to a file-by-file
# overwrite again, which is exactly the situation the graft fixed. Checking it
# here costs nothing and needs no network.
Write-Section 'strict rule 7: upstream lineage'

$lineageSha = $null
if (Test-Path $refFile) {
    $lineageLine = Get-Content $refFile -Encoding UTF8 |
        Where-Object { $_ -match '^\s*lineage\s+([0-9a-f]{7,40})\b' } | Select-Object -First 1
    if ($lineageLine) { $lineageSha = ([regex]::Match($lineageLine, '([0-9a-f]{7,40})')).Groups[1].Value }
}

if (-not $lineageSha) {
    Write-Host '  lineage                 : (not recorded - git merges from upstream are not possible)' -ForegroundColor DarkGray
} else {
    $resolved = & git -C $repo rev-parse --verify --quiet "$lineageSha^{commit}" 2>$null
    if (-not $resolved) {
        [void]$failures.Add("lineage commit $lineageSha is recorded in tools/upstream/upstream-ref.txt but does not exist here")
    } else {
        # Two parents (ours + the upstream commit the import was taken from).
        $parentsLine = (& git -C $repo rev-list --parents -n 1 $lineageSha) -split '\s+'
        $parentCount = @($parentsLine | Where-Object { $_ -ne '' }).Count - 1
        if ($parentCount -lt 2) {
            [void]$failures.Add("lineage commit $lineageSha has $parentCount parent(s); it must merge the fork with the upstream import source")
        }
        # And it must still be in the history that is actually built.
        & git -C $repo merge-base --is-ancestor $lineageSha HEAD 2>$null
        if ($LASTEXITCODE -ne 0) {
            [void]$failures.Add("lineage commit $lineageSha is no longer an ancestor of HEAD - the graft was lost (re-imported history?)")
        }
    }
    Write-Host ("  lineage                 : {0}" -f $lineageSha)
}

# --------------------------------------------------------------------------
# sync drift against the sync reference
# --------------------------------------------------------------------------
if ($sync.Sha -ne $baseline.Sha) {
    Write-Section 'sync drift against the upstream reference'
    $syncCompare = Compare-WithReference $sync

    # Which files did UPSTREAM itself change? Comparing the two references'
    # blobs tells us that; the fork's own edits must not be counted as drift.
    $upstreamChanged = @{}
    foreach ($path in $syncCompare.UpFiles.Keys) {
        if (-not $baseCompare.UpFiles.ContainsKey($path)) { $upstreamChanged[$path] = 'added' ; continue }
        if ($baseCompare.UpFiles[$path].sha -ne $syncCompare.UpFiles[$path].sha) { $upstreamChanged[$path] = 'changed' }
    }
    foreach ($path in $baseCompare.UpFiles.Keys) {
        if (-not $syncCompare.UpFiles.ContainsKey($path)) { $upstreamChanged[$path] = 'removed' }
    }

    foreach ($path in ($upstreamChanged.Keys | Sort-Object)) {
        $what = $upstreamChanged[$path]
        $localPath = Join-Path $repo ($path -replace '/', '\')
        $hasLocal = Test-Path $localPath
        if ($what -eq 'removed') {
            if ($hasLocal -and -not ($allowed.ContainsKey($path) -and $allowed[$path] -eq 'D')) {
                $msg = "upstream removed '$path' but the fork still has it - run tools/sync-upstream.ps1"
                if ($SyncRequired) { [void]$failures.Add($msg) } else { [void]$warnings.Add($msg) }
            }
            continue
        }
        if (-not $hasLocal) {
            $msg = "upstream added '$path' but the fork does not have it - run tools/sync-upstream.ps1"
            if ($SyncRequired) { [void]$failures.Add($msg) } else { [void]$warnings.Add($msg) }
            continue
        }
        $sha = (Invoke-Git @('hash-object', "--path=$path", $localPath))[0].Trim()
        if ($sha -ne $syncCompare.UpFiles[$path].sha) {
            $msg = "upstream changed '$path' and the fork has not been updated - run tools/sync-upstream.ps1"
            if ($SyncRequired) { [void]$failures.Add($msg) } else { [void]$warnings.Add($msg) }
        }
    }

    $manual = @($upstreamChanged.Keys | Where-Object {
        $allowed.ContainsKey($_) -and $upstreamChanged[$_] -ne 'removed'
    })
    Write-Host ("  upstream files compared : {0}" -f $syncCompare.UpFiles.Count)
    Write-Host ("  files upstream changed  : {0}" -f $upstreamChanged.Count)
    Write-Host ("  manual merge candidates : {0} (upstream and the fork both touched them)" -f $manual.Count)
}

# --------------------------------------------------------------------------
# strict rules 3-5: resources, checked against the newest upstream we know
# --------------------------------------------------------------------------
Write-Section 'strict rules 3-5: resource files'

$upstreamGlobal = New-Object 'System.Collections.Generic.HashSet[string]'
$resFiles = if ($sync.Prefix) { Invoke-Git @('ls-tree', '-r', '--name-only', $sync.Sha, '--', $sync.Prefix) }
            else { Invoke-Git @('ls-tree', '-r', '--name-only', $sync.Sha) }
foreach ($f in $resFiles) {
    if ($f -match '(?:^|/)values[^/]*/.*\.xml$') {
        # ls-tree already prints the path including the manager/ prefix
        foreach ($n in (Get-Names (Get-UpstreamText $sync.Sha $f))) { [void]$upstreamGlobal.Add($n) }
    }
}

$locales = Get-ChildItem -Path $resRoot -Directory -Filter 'values*' |
    Where-Object { Test-Path (Join-Path $_.FullName 'strings.xml') } | Sort-Object Name

$outOfSync = 0
foreach ($d in $locales) {
    $rel = "app/src/main/res/$($d.Name)/strings.xml"
    $upText = Get-UpstreamText $sync.Sha ($sync.Prefix + $rel)
    $localText = Get-LocalText (Join-Path $d.FullName 'strings.xml')
    if (-not $upText) { continue }
    if ($localText -ne $upText) {
        $outOfSync++
        $msg = "$($d.Name)/strings.xml differs from upstream - run tools/sync-upstream.ps1"
        if ($SyncRequired) { [void]$failures.Add($msg) } else { [void]$warnings.Add($msg) }
        continue
    }

    $fakePath = Join-Path $d.FullName 'fake_strings.xml'
    if (-not (Test-Path $fakePath)) { continue }
    $fakeText = Get-LocalText $fakePath
    $localNames = @(Get-Names $localText)
    $fakeNames = @(Get-Names $fakeText)
    $upNames = @(Get-Names $upText)

    # rule 3: no name twice in one configuration
    foreach ($n in @($fakeNames | Where-Object { $localNames -contains $_ })) {
        [void]$failures.Add("$($d.Name): '$n' is defined in both strings.xml and fake_strings.xml (aapt2 duplicate resource)")
    }
    # rule 5: upstream must not define a name the fork defines in this locale
    foreach ($n in @($fakeNames | Where-Object { $upNames -contains $_ })) {
        [void]$failures.Add("$($d.Name): upstream now provides '$n' - remove the fork's copy from $($d.Name)/fake_strings.xml")
    }
    # rule 4: fake_ names must be unknown to upstream
    foreach ($n in @($fakeNames | Where-Object { $_.StartsWith('fake_') })) {
        if ($upstreamGlobal.Contains($n)) {
            [void]$failures.Add("$($d.Name): '$n' uses the fake_ prefix but upstream also defines it")
        }
    }
}

Write-Host ("  locales checked         : {0}" -f $locales.Count)
Write-Host ("  upstream resource names  : {0}" -f $upstreamGlobal.Count)
Write-Host ("  locales out of sync      : {0}" -f $outOfSync)

# --------------------------------------------------------------------------
# strict rule 6: every fork string is actually translated everywhere
# --------------------------------------------------------------------------
Write-Section 'strict rule 6: locale completeness'

# "<string name="x">text</string>" -> name => text, so placeholders can be
# compared as well as presence.
function Get-LocalizedStrings([string]$path) {
    $map = @{}
    if (-not (Test-Path $path)) { return $map }
    $text = [System.IO.File]::ReadAllText($path)
    foreach ($m in [regex]::Matches($text, '<string\s[^>]*?name="([^"]+)"[^>]*>(.*?)</string>', 'Singleline')) {
        $map[$m.Groups[1].Value] = $m.Groups[2].Value
    }
    return $map
}

function Get-Placeholders([string]$text) {
    return , @([regex]::Matches($text, '%\d+\$[sd]') | ForEach-Object { $_.Value } | Sort-Object)
}

$defaultFake = Get-LocalizedStrings (Join-Path $resRoot 'values\fake_strings.xml')
$sourceNames = @($defaultFake.Keys | Sort-Object)

$localizedFiles = Get-ChildItem -Path $resRoot -Directory -Filter 'values-*' |
    Where-Object { Test-Path (Join-Path $_.FullName 'fake_strings.xml') } | Sort-Object Name

$incompleteLocales = 0
$missingTranslations = 0
foreach ($d in $localizedFiles) {
    $have = Get-LocalizedStrings (Join-Path $d.FullName 'fake_strings.xml')
    $missing = @($sourceNames | Where-Object { -not $have.ContainsKey($_) })
    if ($missing.Count -gt 0) {
        $incompleteLocales++
        $missingTranslations += $missing.Count
        $preview = if ($missing.Count -le 4) { $missing -join ', ' } else { "$($missing[0..3] -join ', '), ..." }
        [void]$failures.Add("$($d.Name): $($missing.Count) fork string(s) not translated ($preview)")
        continue
    }

    # A translation that drops or renames a placeholder crashes at format time.
    foreach ($n in $sourceNames) {
        $want = Get-Placeholders $defaultFake[$n]
        $got = Get-Placeholders $have[$n]
        if (@($want).Count -eq 0 -and @($got).Count -eq 0) { continue }
        if ((@($want) -join ',') -ne (@($got) -join ',')) {
            [void]$failures.Add("$($d.Name): placeholder mismatch in '$n' (expected [$(@($want) -join ', ')], got [$(@($got) -join ', ')])")
        }
    }
}

Write-Host ("  fork strings (default)   : {0}" -f $sourceNames.Count)
Write-Host ("  locales with fake_strings: {0}" -f $localizedFiles.Count)
Write-Host ("  incomplete locales       : {0} ({1} entries)" -f $incompleteLocales, $missingTranslations)

# --------------------------------------------------------------------------
# report
# --------------------------------------------------------------------------
Write-Section 'result'
foreach ($w in $warnings) { Write-Host "  WARN  $w" -ForegroundColor Yellow }
foreach ($f in $failures) { Write-Host "  FAIL  $f" -ForegroundColor Red }

if ($failures.Count -gt 0) {
    Write-Host ''
    Write-Host "$($failures.Count) rule violation(s)." -ForegroundColor Red
    exit 1
}
Write-Host ''
Write-Host 'no rule violations.' -ForegroundColor Green
exit 0
