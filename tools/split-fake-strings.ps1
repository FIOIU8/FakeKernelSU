#requires -Version 5.1
<#
.SYNOPSIS
  Split the fork's own strings out of the shared res/values*/strings.xml files.

.DESCRIPTION
  WHY
  ----
  res/values*/strings.xml is a file this fork shares with upstream
  tiann/KernelSU. Every line the fork adds to it is a potential merge
  conflict when upstream ships a new translation set. Today the fork appends
  ~95-112 lines to each of the 47 shared files.

  Measured facts about upstream tiann/KernelSU @ 1f70e46 that drive the
  design:

    * 45 of the 47 locale files keep their OWN resource order; they do not
      follow the default locale's order. So new upstream translations are
      appended to the end of each locale file, not inserted in source
      position.
    * 40 resource names (script_*, module_preinit_*) exist upstream in
      English only; the fork contributed the translations for the other 46
      locales.

  Consequence: anything the fork keeps in strings.xml sits exactly where
  upstream appends its next translation. A single upstream Weblate sync would
  then conflict in all 45 shared locale files. So the fork keeps NOTHING in
  strings.xml.

  THE SPLIT
  ---------
    res/values*/strings.xml      -> byte-identical to upstream
    res/values*/fake_strings.xml -> everything the fork adds:
                                       - fork-owned resources (fake_*)
                                       - translations of upstream resources
                                         that upstream ships in English only

  Both files are checked by the invariants at the bottom of this script and
  re-checked in CI by tools/verify-divergence.ps1.

  RESIDUAL RISK (documented, guarded)
  ----------------------------------
  If upstream later translates one of the 40 names into a locale, that
  locale would carry the name twice - once in strings.xml, once in
  fake_strings.xml - and aapt2 would reject the build with a duplicate
  resource error. tools/verify-divergence.ps1 detects exactly that case and
  names the line to delete. The alternative (keeping those translations in
  strings.xml) turns every upstream translation sync into 45 merge
  conflicts, which is strictly worse.

  INVARIANTS
  ----------
    1. The multiset of resource names per configuration is unchanged.
    2. No name appears in both strings.xml and fake_strings.xml.
    3. strings.xml is byte-identical to the upstream file for that locale.
    4. Every name in fake_strings.xml is either fork-owned (absent from
       upstream entirely) or a backfill (present upstream, but absent from
       upstream's file for this locale).

  The script is idempotent: re-running it on an already split tree keeps
  fake_strings.xml untouched.
#>
[CmdletBinding(SupportsShouldProcess)]
param(
    [string] $UpstreamRef = '1f70e46',
    [switch] $DryRun
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$resRoot = Join-Path $repo 'app\src\main\res'

# Windows PowerShell decodes native command output with [Console]::OutputEncoding,
# which defaults to the system ANSI code page. git emits UTF-8, so any non-ASCII
# resource text would be mangled and every byte comparison would fail.
try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false) } catch { }

# --------------------------------------------------------------------------
# upstream reference data
# --------------------------------------------------------------------------
function Get-UpstreamText([string]$rel) {
    $out = & git -C $repo show "${UpstreamRef}:$rel" 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }
    return ($out -join "`n")
}

function Get-Names([string]$xml) {
    if (-not $xml) { return @() }
    $n = @()
    foreach ($m in [regex]::Matches($xml, '<string\s[^>]*?name="([^"]+)"')) { $n += $m.Groups[1].Value }
    return $n
}

$canonText = Get-UpstreamText 'app/src/main/res/values/strings.xml'
if (-not $canonText) { throw "cannot read upstream ${UpstreamRef}:app/src/main/res/values/strings.xml" }
$canon = @(Get-Names $canonText)
$upstreamNames = New-Object 'System.Collections.Generic.HashSet[string]'
foreach ($n in $canon) { [void]$upstreamNames.Add($n) }

Write-Host "upstream reference : $UpstreamRef ($($canon.Count) upstream resource names)" -ForegroundColor Cyan

# --------------------------------------------------------------------------
# file helpers
# --------------------------------------------------------------------------
function Read-ResourceFile([string]$path) {
    $bytes = [System.IO.File]::ReadAllBytes($path)
    $bom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
    $text = [System.Text.Encoding]::UTF8.GetString($bytes)
    if ($bom) { $text = $text.Substring(1) }
    $eol = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
    $norm = $text.Replace("`r`n", "`n")
    $lines = [string[]]$norm.Split([char]10)
    return [pscustomobject]@{ Lines = $lines; Eol = $eol; Bom = $bom }
}

function Write-ResourceFile([string]$path, [string[]]$lines, [string]$eol, [bool]$bom) {
    $text = ($lines -join $eol)
    if (-not $text.EndsWith($eol)) { $text += $eol }
    $enc = New-Object System.Text.UTF8Encoding($bom)
    [System.IO.File]::WriteAllText($path, $text, $enc)
}

# Parse a strings.xml into header lines, body blocks and footer lines.
function Parse-ResourceFile([string[]]$lines) {
    $hdr = -1; $ftr = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($hdr -lt 0 -and $lines[$i].Contains('<resources')) { $hdr = $i }
        if ($lines[$i].Contains('</resources>')) { $ftr = $i }
    }
    if ($hdr -lt 0 -or $ftr -lt 0) { throw 'malformed resources file (missing <resources>)' }

    $header = @($lines[0..$hdr])
    $footer = @($lines[$ftr..($lines.Count - 1)])

    $blocks = New-Object System.Collections.ArrayList
    $i = $hdr + 1
    while ($i -lt $ftr) {
        $t = $lines[$i].Trim()
        if ($t -eq '') {
            [void]$blocks.Add([pscustomobject]@{ Kind = 'blank'; Lines = @($lines[$i]); Name = $null })
            $i++
            continue
        }
        if ($t.StartsWith('<!--')) {
            $s = $i
            while ($i -lt $ftr -and -not $lines[$i].Contains('-->')) { $i++ }
            [void]$blocks.Add([pscustomobject]@{ Kind = 'comment'; Lines = @($lines[$s..$i]); Name = $null })
            $i++
            continue
        }
        if ($t -match '^<string(\s|>|/)') {
            $s = $i
            # Locate the end of the opening tag. The value may itself contain
            # markup such as <br/>, so "the line ends with />" is not a
            # reliable test for a self-closed element.
            $tagEnd = $i
            $selfClosing = $false
            for ($j = $i; $j -lt $ftr; $j++) {
                $pos = $lines[$j].IndexOf('>')
                if ($pos -ge 0) {
                    $tagEnd = $j
                    $selfClosing = ($pos -gt 0 -and $lines[$j][$pos - 1] -eq '/')
                    break
                }
            }
            $i = $tagEnd
            if (-not $selfClosing) {
                while ($i -lt $ftr -and -not $lines[$i].Contains('</string>')) { $i++ }
            }
            $raw = ($lines[$s..$i]) -join "`n"
            $m = [regex]::Match($raw, '<string\s[^>]*?name="([^"]+)"')
            $nm = if ($m.Success) { $m.Groups[1].Value } else { $null }
            [void]$blocks.Add([pscustomobject]@{ Kind = 'string'; Lines = @($lines[$s..$i]); Name = $nm })
            $i++
            continue
        }
        # any other resource element: keep it with upstream
        $s = $i
        $m = [regex]::Match($t, '<([A-Za-z][\w-]*)')
        $el = $m.Groups[1].Value
        $tagEnd = $i
        for ($j = $i; $j -lt $ftr; $j++) {
            $pos = $lines[$j].IndexOf('>')
            if ($pos -ge 0) { $tagEnd = $j; break }
        }
        $i = $tagEnd
        while ($i -lt $ftr -and -not $lines[$i].Contains("</$el>")) { $i++ }
        [void]$blocks.Add([pscustomobject]@{ Kind = 'other'; Lines = @($lines[$s..$i]); Name = $null })
        $i++
    }
    return [pscustomobject]@{ Header = $header; Footer = $footer; Blocks = @($blocks) }
}

function Test-Xml([string[]]$lines) {
    $doc = New-Object System.Xml.XmlDocument
    $doc.LoadXml(($lines -join "`n"))
}

# --------------------------------------------------------------------------
# main loop
# --------------------------------------------------------------------------
$dirs = Get-ChildItem -Path $resRoot -Directory -Filter 'values*' |
    Where-Object { Test-Path (Join-Path $_.FullName 'strings.xml') } |
    Sort-Object Name

$report = @()
$errors = New-Object System.Collections.ArrayList

foreach ($d in $dirs) {
    $stringsPath = Join-Path $d.FullName 'strings.xml'
    $fakePath = Join-Path $d.FullName 'fake_strings.xml'
    $upRel = "app/src/main/res/$($d.Name)/strings.xml"

    $cur = Read-ResourceFile $stringsPath
    $parsed = Parse-ResourceFile $cur.Lines
    $upText = Get-UpstreamText $upRel
    if (-not $upText) { [void]$errors.Add("$($d.Name): no upstream baseline at ${UpstreamRef}"); continue }
    $upNames = @{}
    foreach ($n in (Get-Names $upText)) { $upNames[$n] = $true }

    $upstreamBlocks = @()
    $forkBlocks = @()

    if (Test-Path $fakePath) {
        # already split: keep fake_strings.xml as it is
        $fakeDoc = Read-ResourceFile $fakePath
        $fakeParsed = Parse-ResourceFile $fakeDoc.Lines
        foreach ($b in $parsed.Blocks) {
            if ($b.Kind -eq 'string') { $upstreamBlocks += $b }
            elseif ($b.Kind -eq 'other') { $upstreamBlocks += $b }
        }
        $forkBlocks = @($fakeParsed.Blocks)
    } else {
        foreach ($b in $parsed.Blocks) {
            switch ($b.Kind) {
                'string' {
                    if ($upNames.ContainsKey($b.Name)) { $upstreamBlocks += $b } else { $forkBlocks += $b }
                }
                'other' { $upstreamBlocks += $b }
                'comment' {
                    # A comment belongs to upstream when upstream ships the very
                    # same comment (e.g. "Don't translate this string!"). The
                    # fork's own comments never appear upstream.
                    $text = ($b.Lines -join "`n").Trim()
                    if ($upText.Contains($text)) { $upstreamBlocks += $b } else { $forkBlocks += $b }
                }
                default { $forkBlocks += $b }   # blank lines
            }
        }
    }

    # ---- build strings.xml: exactly upstream ------------------------------
    $newStrings = @()
    $newStrings += $parsed.Header
    foreach ($b in $upstreamBlocks) { $newStrings += $b.Lines }
    $newStrings += $parsed.Footer

    # ---- build fake_strings.xml ------------------------------------------
    $fakeBodyList = New-Object System.Collections.ArrayList
    $prevBlank = $true
    foreach ($b in $forkBlocks) {
        if ($b.Kind -eq 'blank') {
            if (-not $prevBlank -and $fakeBodyList.Count -gt 0) { [void]$fakeBodyList.Add($b.Lines[0]); $prevBlank = $true }
            continue
        }
        foreach ($l in $b.Lines) { [void]$fakeBodyList.Add($l) }
        $prevBlank = $false
    }
    while ($fakeBodyList.Count -gt 0 -and ([string]$fakeBodyList[$fakeBodyList.Count - 1]).Trim() -eq '') {
        $fakeBodyList.RemoveAt($fakeBodyList.Count - 1)
    }
    $newFake = @()
    $newFake += $parsed.Header
    $newFake += @($fakeBodyList)
    $newFake += $parsed.Footer

    # ---- validation ------------------------------------------------------
    $origNames = @($parsed.Blocks | Where-Object { $_.Kind -eq 'string' } | ForEach-Object { $_.Name } | Sort-Object)
    $splitNames = @(@($upstreamBlocks | Where-Object { $_.Kind -eq 'string' } | ForEach-Object { $_.Name }) +
                     @($forkBlocks | Where-Object { $_.Kind -eq 'string' } | ForEach-Object { $_.Name }) | Sort-Object)
    if (($origNames -join '|') -ne ($splitNames -join '|')) {
        [void]$errors.Add("$($d.Name): resource name set changed during split")
    }
    $dup = @(@($upstreamBlocks | Where-Object { $_.Kind -eq 'string' } | ForEach-Object { $_.Name }) |
             Where-Object { $_ -in @($forkBlocks | Where-Object { $_.Kind -eq 'string' } | ForEach-Object { $_.Name }) })
    if ($dup.Count -gt 0) {
        [void]$errors.Add("$($d.Name): names present in both files: $($dup -join ', ')")
    }
    # invariant 4: every fork-side name is either fork-owned or a backfill
    $forkNames = @($forkBlocks | Where-Object { $_.Kind -eq 'string' } | ForEach-Object { $_.Name })
    foreach ($n in $forkNames) {
        if ($upNames.ContainsKey($n)) {
            [void]$errors.Add("$($d.Name): '$n' already exists upstream for this locale and must not be redefined")
        }
    }
    try { [void](Test-Xml $newStrings) } catch { [void]$errors.Add("$($d.Name): generated strings.xml is not well-formed: $_") }
    try { [void](Test-Xml $newFake) } catch { [void]$errors.Add("$($d.Name): generated fake_strings.xml is not well-formed: $_") }

    # invariant 3: strings.xml is byte-identical to upstream
    $rebuiltText = ($newStrings -join "`n").TrimEnd("`r", "`n")
    $upTextTrim = ($upText -replace "`r`n", "`n").TrimEnd("`r", "`n")
    if ($rebuiltText -ne $upTextTrim) {
        [void]$errors.Add("$($d.Name): strings.xml is not byte-identical to ${UpstreamRef}")
    }

    if (-not $DryRun -and $PSCmdlet.ShouldProcess("$($d.Name)", 'split strings')) {
        Write-ResourceFile $stringsPath $newStrings $cur.Eol $cur.Bom
        Write-ResourceFile $fakePath $newFake $cur.Eol $cur.Bom
    }

    $report += [pscustomobject]@{
        Locale    = $d.Name
        Upstream  = @($upstreamBlocks | Where-Object { $_.Kind -eq 'string' }).Count
        ForkOwned = @($forkNames | Where-Object { -not $upstreamNames.Contains($_) }).Count
        Backfill  = @($forkNames | Where-Object { $upstreamNames.Contains($_) }).Count
        Eol       = $(if ($cur.Eol -eq "`r`n") { 'CRLF' } else { 'LF' })
    }
}

Write-Host ""
$report | Format-Table -AutoSize | Out-String -Width 120 | Write-Host

if ($errors.Count -gt 0) {
    Write-Host 'VALIDATION FAILED:' -ForegroundColor Red
    foreach ($e in $errors) { Write-Host "  - $e" -ForegroundColor Red }
    throw "split validation failed ($($errors.Count) problem(s)); no file was written"
}

Write-Host 'all invariants hold.' -ForegroundColor Green
