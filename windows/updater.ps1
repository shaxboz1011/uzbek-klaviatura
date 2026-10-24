# Alifbo Windows: yangi versiya chiqqanini GitHub Releases'dan tekshiradi (rejalashtirilgan vazifa chaqiradi).
# ASCII-only: Windows PowerShell 5.1 BOM'siz fayllarni ANSI deb oqiydi.
param(
    [string]$Repo = 'shaxboz1011/uzbek-klaviatura',
    [string]$LatestJson = '',   # sinov: GitHub javobi orniga fayldan oqish
    [switch]$DryRun             # faqat natijani yozadi, oyna chiqarmaydi, ornatmaydi
)
$ErrorActionPreference = 'Stop'
try {
    $reg = 'HKLM:\SOFTWARE\Alifbo'
    $props = Get-ItemProperty $reg -ErrorAction SilentlyContinue
    if ($props -and $props.AutoUpdate -eq 0) { Write-Output 'DISABLED'; return }
    $current = [version]($props.Version)

    if ($LatestJson) {
        $rel = Get-Content $LatestJson -Raw | ConvertFrom-Json
    } else {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        $rel = Invoke-RestMethod "https://api.github.com/repos/$Repo/releases/latest" -Headers @{ 'User-Agent' = 'alifbo-updater' } -TimeoutSec 20
    }
    $latest = [version](($rel.tag_name) -replace '^v', '')
    if ($latest -le $current) { Write-Output "UPTODATE $current"; return }
    $asset = $rel.assets | Where-Object { $_.name -like 'Alifbo-Windows-Setup-v*.exe' } | Select-Object -First 1
    if (-not $asset) { Write-Output 'NOASSET'; return }
    Write-Output "UPDATE_AVAILABLE $latest"
    if ($DryRun) { return }

    $skip = (Get-ItemProperty 'HKCU:\SOFTWARE\Alifbo' -ErrorAction SilentlyContinue).Skip
    if ($skip -eq "$latest") { return }

    Add-Type -AssemblyName System.Windows.Forms
    $msg = "Alifbo klaviatura $latest versiyasi chiqdi (sizda: $current).`n`nHozir yangilansinmi?"
    $ans = [System.Windows.Forms.MessageBox]::Show($msg, 'Alifbo yangilanishi', 'YesNo', 'Information')
    if ($ans -ne 'Yes') {
        New-Item 'HKCU:\SOFTWARE\Alifbo' -Force | Out-Null
        Set-ItemProperty 'HKCU:\SOFTWARE\Alifbo' -Name Skip -Value "$latest"
        return
    }
    $file = Join-Path $env:TEMP $asset.name
    Invoke-WebRequest $asset.browser_download_url -OutFile $file -UseBasicParsing -TimeoutSec 300
    Start-Process $file -ArgumentList '/SILENT', '/SUPPRESSMSGBOXES', '/NORESTART' -Verb RunAs
} catch {
    Write-Output "ERROR $($_.Exception.Message)"
}
