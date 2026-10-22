#requires -Version 5.1
# Self-test: build a synthetic upstream-repository commit (everything under
# manager/) and run the divergence verifier against it, exercising the code
# path CI uses when it fetches tiann/KernelSU directly.
$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false) } catch { }
$repo = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$base = '1f70e46'

$tmpIndex = Join-Path ([System.IO.Path]::GetTempPath()) ("fksu-test-index-" + [guid]::NewGuid().ToString('N'))
$env:GIT_INDEX_FILE = $tmpIndex
try {
    # read-tree --prefix puts the whole tree under manager/, which is exactly
    # the layout of upstream's own repository.
    & git -C $repo read-tree --prefix=manager/ $base
    if ($LASTEXITCODE -ne 0) { throw 'read-tree failed' }
    $tree = (& git -C $repo write-tree).Trim()
    $env:GIT_AUTHOR_NAME = 'FakeKernelSU test'
    $env:GIT_AUTHOR_EMAIL = 'test@local'
    $env:GIT_COMMITTER_NAME = 'FakeKernelSU test'
    $env:GIT_COMMITTER_EMAIL = 'test@local'
    $commit = (& git -C $repo commit-tree $tree -p $base -m 'synthetic upstream layout').Trim()
}
finally {
    Remove-Item Env:\GIT_INDEX_FILE -ErrorAction SilentlyContinue
    if (Test-Path $tmpIndex) { Remove-Item -LiteralPath $tmpIndex -Force }
}

Write-Host "synthetic upstream commit: $commit" -ForegroundColor Cyan
Write-Host ''
Write-Host '--- verify-divergence ---' -ForegroundColor Cyan
& (Join-Path $repo 'tools\verify-divergence.ps1') -UpstreamRef $commit
$verifyOk = ($LASTEXITCODE -eq 0)
Write-Host ''
Write-Host '--- sync-upstream (dry run) ---' -ForegroundColor Cyan
& (Join-Path $repo 'tools\sync-upstream.ps1') -UpstreamRef $commit
$syncOk = ($LASTEXITCODE -eq 0)

if (-not ($verifyOk -and $syncOk)) { exit 1 }
exit 0
