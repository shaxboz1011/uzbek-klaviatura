; Alifbo Windows klaviatura sxemasi ornatuvchisi (Inno Setup 6).
; Yigish:  ISCC /DAppVersion=1.2.0 windows\installer.iss   (avval windows\build.ps1 bajarilgan bolishi kerak)
#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif

[Setup]
AppId={{B7A4D2C1-5E3F-4C8A-9A55-A11F8B026001}
AppName=Alifbo Klaviatura (Windows)
AppVersion={#AppVersion}
AppPublisher=Alifbo
AppPublisherURL=https://github.com/shaxboz1011/uzbek-klaviatura
CreateAppDir=no
DisableProgramGroupPage=yes
DisableDirPage=yes
DisableReadyPage=no
PrivilegesRequired=admin
ArchitecturesAllowed=x86 x64os
ArchitecturesInstallIn64BitMode=x64os
UninstallFilesDir={commonappdata}\Alifbo\Uninstall
UninstallDisplayName=Alifbo Klaviatura (Windows)
OutputDir=out
OutputBaseFilename=Alifbo-Windows-Setup-v{#AppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern

[Languages]
Name: "uz"; MessagesFile: "compiler:Default.isl"

[Messages]
FinishedLabel=Alifbo keyboard layout is installed.%n%nTo use it press Win + Space and pick "Uzbek (Latin 2026) - Alifbo". If it is not in the list yet, sign out and sign in again.

[Files]
Source: "out\amd64\alifbo26.dll"; DestDir: "{sys}"; Flags: ignoreversion; Check: Is64BitInstallMode
Source: "out\wow64\alifbo26.dll"; DestDir: "{syswow64}"; Flags: ignoreversion; Check: Is64BitInstallMode
Source: "out\i386\alifbo26.dll"; DestDir: "{sys}"; Flags: ignoreversion; Check: not Is64BitInstallMode
Source: "addlayout.ps1"; DestDir: "{tmp}"; Flags: deleteafterinstall

[Registry]
Root: HKLM; Subkey: "SYSTEM\CurrentControlSet\Control\Keyboard Layouts\a0260443"; ValueType: string; ValueName: "Layout File"; ValueData: "alifbo26.dll"; Flags: uninsdeletekey
Root: HKLM; Subkey: "SYSTEM\CurrentControlSet\Control\Keyboard Layouts\a0260443"; ValueType: string; ValueName: "Layout Text"; ValueData: "Uzbek (Latin 2026) - Alifbo"
Root: HKLM; Subkey: "SYSTEM\CurrentControlSet\Control\Keyboard Layouts\a0260443"; ValueType: string; ValueName: "Layout Display Name"; ValueData: "@%SystemRoot%\system32\alifbo26.dll,-1000"
Root: HKLM; Subkey: "SYSTEM\CurrentControlSet\Control\Keyboard Layouts\a0260443"; ValueType: string; ValueName: "Layout Id"; ValueData: "00ca"

[Run]
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{tmp}\addlayout.ps1"""; Flags: runasoriginaluser runhidden
