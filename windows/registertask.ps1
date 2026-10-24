# Yangilanish tekshiruvi vazifasini yaratadi (kirishda va har 12 soatda). Ornatuvchi administrator sifatida chaqiradi.
$ErrorActionPreference = 'Stop'
$script = Join-Path $env:ProgramData 'Alifbo\updater.ps1'
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$script`""
$t1 = New-ScheduledTaskTrigger -AtLogOn
$t2 = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(5) -RepetitionInterval (New-TimeSpan -Hours 12)
$principal = New-ScheduledTaskPrincipal -GroupId 'BUILTIN\Users' -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit (New-TimeSpan -Minutes 10)
Register-ScheduledTask -TaskName 'Alifbo Update' -Action $action -Trigger @($t1, $t2) -Principal $principal -Settings $settings -Force | Out-Null
