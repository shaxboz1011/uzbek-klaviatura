# Oʻrnatilgan sxemani Windows'ning oʻzi orqali tekshiradi: LoadKeyboardLayout + ToUnicodeEx.
$ErrorActionPreference = 'Stop'
Add-Type @"
using System;
using System.Runtime.InteropServices;
using System.Text;
public class KL {
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern IntPtr LoadKeyboardLayout(string klid, uint flags);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int ToUnicodeEx(uint vk, uint sc, byte[] state, StringBuilder buf, int cch, uint flags, IntPtr hkl);
}
"@
$hkl = [KL]::LoadKeyboardLayout("a0260443", 0)
if ($hkl -eq [IntPtr]::Zero) { throw "LoadKeyboardLayout muvaffaqiyatsiz (sxema oʻrnatilmagan?)" }
Write-Host ("HKL = 0x{0:X}" -f $hkl.ToInt64())

function Press([uint32]$vk, [uint32]$sc, [string]$mods) {
    $st = New-Object byte[] 256
    if ($mods -match 'S') { $st[0x10] = 0x80 }                  # Shift
    if ($mods -match 'A') { $st[0x11] = 0x80; $st[0x12] = 0x80 } # AltGr = Ctrl+Alt
    if ($mods -match 'C') { $st[0x14] = 0x01 }                  # CapsLock
    $sb = New-Object System.Text.StringBuilder 8
    $n = [KL]::ToUnicodeEx($vk, $sc, $st, $sb, 8, 0, $hkl)
    if ($n -le 0) { return "" }
    return $sb.ToString(0, $n)
}

$tests = @(
    @{ n='W    -> ş'; vk=0x57; sc=0x11; m='';   e=[string][char]0x015F },
    @{ n='Sh+W -> Ş'; vk=0x57; sc=0x11; m='S';  e=[string][char]0x015E },
    @{ n='Caps+W -> Ş'; vk=0x57; sc=0x11; m='C'; e=[string][char]0x015E },
    @{ n='AltGr+W -> w'; vk=0x57; sc=0x11; m='A'; e='w' },
    @{ n='C    -> ç'; vk=0x43; sc=0x2E; m='';   e=[string][char]0x00E7 },
    @{ n='Sh+C -> Ç'; vk=0x43; sc=0x2E; m='S';  e=[string][char]0x00C7 },
    @{ n='AltGr+C -> c'; vk=0x43; sc=0x2E; m='A'; e='c' },
    @{ n='[    -> ö'; vk=0xDB; sc=0x1A; m='';   e=[string][char]0x00F6 },
    @{ n='Sh+[ -> Ö'; vk=0xDB; sc=0x1A; m='S';  e=[string][char]0x00D6 },
    @{ n=']    -> ğ'; vk=0xDD; sc=0x1B; m='';   e=[string][char]0x011F },
    @{ n='Sh+] -> Ğ'; vk=0xDD; sc=0x1B; m='S';  e=[string][char]0x011E },
    @{ n="'    -> tutuq ʼ"; vk=0xDE; sc=0x28; m=''; e=[string][char]0x02BC },
    @{ n="AltGr+' -> '"; vk=0xDE; sc=0x28; m='A'; e="'" },
    @{ n="Sh+AltGr+' -> ʻ"; vk=0xDE; sc=0x28; m='SA'; e=[string][char]0x02BB },
    @{ n='Sh+'' -> "'; vk=0xDE; sc=0x28; m='S'; e='"' },
    @{ n='Q    -> q'; vk=0x51; sc=0x10; m='';   e='q' },
    @{ n=';    -> ;'; vk=0xBA; sc=0x27; m='';   e=';' },
    @{ n=',    -> ,'; vk=0xBC; sc=0x33; m='';   e=',' },
    @{ n='1    -> 1'; vk=0x31; sc=0x02; m='';   e='1' },
    @{ n='Sh+1 -> !'; vk=0x31; sc=0x02; m='S';  e='!' }
)
$fail = 0
foreach ($t in $tests) {
    $got = Press $t.vk $t.sc $t.m
    $ok = ($got -ceq $t.e)
    if (-not $ok) { $fail++ }
    Write-Host ("{0,-22} {1}  (kutilgan U+{2:X4}, chiqdi {3})" -f $t.n, $(if ($ok) {'OK  '} else {'XATO'}), [int][char]$t.e[0], $(if ($got) { 'U+{0:X4}' -f [int][char]$got[0] } else { '<boʻsh>' }))
}
if ($fail -gt 0) { throw "$fail ta test oʻtmadi" }
Write-Host "Hamma testlar oʻtdi."
