# Joriy foydalanuvchining kiritish tillari roʻyxatiga "Alifbo" sxemasini qoʻshadi (oʻrnatuvchi tomonidan chaqiriladi).
$ErrorActionPreference = 'SilentlyContinue'
$tip = '0443:A0260443'
$list = Get-WinUserLanguageList
$uz = $list | Where-Object { $_.LanguageTag -like 'uz-Latn*' } | Select-Object -First 1
if (-not $uz) {
    $new = New-WinUserLanguageList 'uz-Latn-UZ'
    $uz = $new[0]
    $uz.InputMethodTips.Clear()
    $list.Add($uz)
}
if ($uz.InputMethodTips -notcontains $tip) { $uz.InputMethodTips.Add($tip) }
Set-WinUserLanguageList $list -Force
