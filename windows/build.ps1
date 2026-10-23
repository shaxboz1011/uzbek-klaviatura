# Alifbo Windows klaviatura sxemasini yigʻadi: KLC -> kbdutool -> DLL (x86, x64, WOW64).
# Talab: Microsoft Keyboard Layout Creator 1.4  (choco install keyboard-layout-creator)
param([string]$Out = "windows\out")
$ErrorActionPreference = 'Stop'
$Out = [System.IO.Path]::GetFullPath($Out)   # Push-Location dan oldin mutlaq yoʻlga aylantiramiz

$root = "C:\Program Files (x86)\Microsoft Keyboard Layout Creator 1.4"
if (-not (Test-Path "$root\bin\i386\kbdutool.exe")) { throw "MSKLC topilmadi: $root" }
$env:PATH = "$root\bin\i386;$env:PATH"

$repo = Split-Path -Parent $PSScriptRoot
$work = Join-Path $env:TEMP "alifbo-klc"
if (Test-Path $work) { Remove-Item $work -Recurse -Force }
New-Item -ItemType Directory -Force -Path $work | Out-Null

python (Join-Path $PSScriptRoot "make_klc.py") $work
if ($LASTEXITCODE -ne 0) { throw "make_klc.py xato" }

$targets = [ordered]@{ '-x' = 'i386'; '-m' = 'amd64'; '-o' = 'wow64' }
Push-Location $work
foreach ($flag in $targets.Keys) {
    $dir = Join-Path $Out $targets[$flag]
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    & kbdutool.exe -u $flag alifbo26.klc
    if ($LASTEXITCODE -ne 0) { throw "kbdutool $flag xato ($LASTEXITCODE)" }
    Move-Item -Force alifbo26.dll (Join-Path $dir "alifbo26.dll")
}
Pop-Location
Get-ChildItem $Out -Recurse -File | Select-Object FullName, Length | Format-Table -AutoSize | Out-String -Width 200
