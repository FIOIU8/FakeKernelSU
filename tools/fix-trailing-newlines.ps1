#requires -Version 5.1
# Restore the exact trailing whitespace of the Kotlin sources to what HEAD
# has. Reading a file into lines and joining it back loses trailing blank
# lines, which shows up as a pointless diff against upstream.
$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false) } catch { }
$repo = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$srcRoot = Join-Path $repo 'app\src\main\java'

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

$ErrorActionPreference = 'Continue'
$fixed = 0
foreach ($f in (Get-ChildItem -Path $srcRoot -Recurse -Filter '*.kt')) {
    $rel = 'app/src/main/java/' + ($f.FullName.Substring($srcRoot.Length + 1) -replace '\\', '/')
    $headOut = & git -C $repo cat-file -e "HEAD:$rel"
    if ($LASTEXITCODE -ne 0) { continue }

    $headBytes = Get-GitBlobBytes "HEAD:$rel"
    $curBytes = [System.IO.File]::ReadAllBytes($f.FullName)

    # count the trailing newline run of each side
    function Get-TailLen([byte[]]$b) {
        $n = 0
        for ($i = $b.Length - 1; $i -ge 0; $i--) {
            if ($b[$i] -eq 0x0A) { $n++ } else { break }
        }
        return $n
    }
    $headTail = Get-TailLen $headBytes
    $curTail = Get-TailLen $curBytes
    if ($headTail -eq $curTail) { continue }

    # only touch the file when the body is identical apart from the tail
    $headBody = $headBytes.Length - $headTail
    $curBody = $curBytes.Length - $curTail
    if ($headBody -ne $curBody) { continue }
    $same = $true
    for ($i = 0; $i -lt $headBody; $i++) {
        if ($headBytes[$i] -ne $curBytes[$i]) { $same = $false; break }
    }
    if (-not $same) { continue }

    $out = New-Object byte[] ($curBody + $headTail)
    [Array]::Copy($curBytes, $out, $curBody)
    for ($i = 0; $i -lt $headTail; $i++) { $out[$curBody + $i] = 0x0A }
    [System.IO.File]::WriteAllBytes($f.FullName, $out)
    Write-Host "fixed trailing newline: $rel"
    $fixed++
}
Write-Host "fixed $fixed file(s)"
