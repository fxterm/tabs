# Publish a Tabs release. Run from this folder:
#
#     .\release.ps1 -Changes changes.md            build in a clean clone, publish the version in pom.xml
#     .\release.ps1 -DryRun                        build and checksum only; show what would be published
#
# What it does and why:
#  - HEAD must be pushed, so a release always matches a public commit (uncommitted edits here are left out).
#  - The build runs in a fresh CLONE under %TEMP%, not here. Here the running Tabs holds its exe, and an exe left
#    under app/target/jr would be found by a search tool and launched, running the RELEASED jar instead of the
#    newest build (it happened on 2026-10-31). In the clone nothing of this machine's own is picked up either.
#  - -Pjrexe -Djr.source=url: jr-maven-plugin builds fxterm-tabs.exe with the sha256 of exactly that jar and its
#    release url baked in, and writes the release folder (exe, jar, SHA256SUMS, version.txt, update file).
#  - After publishing, every asset is downloaded from its public url and its SHA-256 compared with the build's.
#    GitHub has served an asset with HTTP 500 right after upload before (cfucli 0.5.1), so look, do not assume.
#  - The update file is itself a release asset (releases/latest/download/fxterm-tabs.update.json); there is no site copy.
#
# $env:GH names the GitHub CLI (default gh); $env:MVN the Maven 3 to use.

param([switch]$DryRun, [string]$Changes)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$gh = if ($env:GH) { $env:GH } else { 'gh' }
$mvn = if ($env:MVN) { $env:MVN } else { 'C:\user\Apps\cmdtools\maven\apache-maven\bin\mvn.cmd' }
$repo = 'fxterm/tabs'
$version = ([xml](Get-Content pom.xml)).project.version
$tag = "v$version"
function Sha([string]$ref) { @(git rev-parse $ref) | Where-Object { $_ -match '^[0-9a-f]{40}$' } | Select-Object -Last 1 }

# Edits in this working tree do not matter: the build is a clone of HEAD, and HEAD must be the pushed commit.
git fetch -q origin
if ((Sha HEAD) -ne (Sha '@{u}')) { throw 'HEAD is not pushed - push first' }
if (-not $DryRun) {
    & $gh release view $tag --repo $repo *> $null
    if ($LASTEXITCODE -eq 0) { throw "$tag is already released" }
}

$work = Join-Path $env:TEMP "fxterm-tabs-release-$version"
if (Test-Path $work) { Remove-Item -Recurse -Force $work }
git clone -q --branch main . $work
if ((Sha HEAD) -ne (@(git -C $work rev-parse HEAD) | Where-Object { $_ -match '^[0-9a-f]{40}$' } | Select-Object -Last 1)) { throw 'the clone is not at HEAD' }

Write-Host "building $tag in $work"
for ($i = 1; $i -le 4; $i++) {
    # the network here drops connections mid-download often enough that one retry is not enough
    & $mvn -q -B -DskipTests -f (Join-Path $work 'pom.xml') -Pjrexe -Djr.source=url clean verify
    if ($LASTEXITCODE -eq 0) { break }
    if ($i -eq 4) { throw 'mvn failed' }
    Write-Host "build attempt $i failed, retrying"
}
$out = Join-Path $work 'app\target\jr\release'
foreach ($f in @('fxterm-tabs.exe', 'fxterm-tabs.jar', 'version.txt', 'SHA256SUMS', 'fxterm-tabs.update.json')) {
    if (-not (Test-Path (Join-Path $out $f))) { throw "the release folder has no $f - see the jr output above" }
}
Get-ChildItem $out | ForEach-Object { Write-Host ('  {0,-28} {1,7:N1} MB' -f $_.Name, ($_.Length / 1MB)) }
if ($DryRun) { Write-Host "dry run - nothing published. Assets are in $out"; return }

$notes = "Tabs $version`n`n"
if ($Changes) { $notes += [IO.File]::ReadAllText((Resolve-Path $Changes).Path).Trim() + "`n`n" }
$notes += "Windows only, needs Java 25. Existing release installs: fxterm-tabs.exe -Xjr:update"
# through a file: Windows PowerShell cuts a multi-line string passed to a native program at the first line break
$notesFile = Join-Path $work 'release-notes.md'
[IO.File]::WriteAllText($notesFile, $notes + "`n")
$assets = (Get-ChildItem $out -File).FullName
& $gh release create $tag $assets --repo $repo --target (Sha HEAD) --title "Tabs $version" --notes-file $notesFile --latest=true
if ($LASTEXITCODE -ne 0) { throw 'gh release create failed' }
Write-Host "published $tag"

# look at every asset as a user would get it
$check = Join-Path $work 'check'
New-Item -ItemType Directory -Force $check | Out-Null
$bad = 0
foreach ($f in (Get-ChildItem $out -File)) {
    $url = "https://github.com/$repo/releases/download/$tag/$($f.Name)"
    $dest = Join-Path $check $f.Name
    & curl.exe -fsSL -o $dest $url
    if ($LASTEXITCODE -ne 0) { Write-Host "FAILED to get $url"; $bad++; continue }
    $same = (Get-FileHash $dest -Algorithm SHA256).Hash -eq (Get-FileHash $f.FullName -Algorithm SHA256).Hash
    Write-Host ("  {0,-28} {1}" -f $f.Name, $(if ($same) { 'same bytes' } else { 'DIFFERENT' }))
    if (-not $same) { $bad++ }
}
& curl.exe -fsSL -o (Join-Path $check 'latest.update.json') "https://github.com/$repo/releases/latest/download/fxterm-tabs.update.json"
$stable = (Get-Content (Join-Path $check 'latest.update.json') -Raw | ConvertFrom-Json).channels.stable
Write-Host "releases/latest update file says stable = $stable"
if ($stable -ne $version) { $bad++ }
if ($bad) { throw "$bad asset check(s) failed - fix before anyone updates" }
Write-Host "every asset is as built; try: $check\fxterm-tabs.exe -Xjr:update-check"
