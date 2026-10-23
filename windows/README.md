# Alifbo — Windows 10 / 11 klaviatura sxemasi

Yangi oʻzbek lotin alifbosi (2026): **Ş Ç Ö Ğ ʼ** — Windows'ning oʻziga **tizim darajasida** oʻrnatiladigan klaviatura sxemasi
(Android ilovasi bilan bir xil alifbo). Fonda hech qanday dastur ishlamaydi: sxema Windows yuklaydigan `alifbo26.dll` fayli, xotira va
protsessorni deyarli ishlatmaydi, administrator huquqi bilan ochilgan oynalarda ham ishlaydi.

## Oʻrnatish

1. [Releases](../../releases) sahifasidan **`Alifbo-Windows-Setup-vX.Y.Z.exe`** ni yuklab oling.
2. Ishga tushiring va administrator ruxsatini bering.
   *Windows «Kompyuteringiz himoyalandi» (SmartScreen) desa: «Batafsil» → «Baribir ishga tushirish». Oʻrnatuvchi hozircha raqamli imzosiz.*
3. `Win + Boʻsh joy` bosib **«Uzbek (Latin 2026) - Alifbo»** ni tanlang. Roʻyxatda koʻrinmasa, tizimdan chiqib qayta kiring.

Oʻchirish: *Sozlamalar → Ilovalar → Alifbo Klaviatura (Windows)*. (Oldin Til sozlamalaridan sxemani olib tashlashingiz mumkin.)

## Tugmalar

QWERTY asosida, `Ctrl+C`, `Ctrl+V` va boshqa tezkor tugmalar **oʻzgarmaydi**.

| Tugma | Oddiy | Shift | AltGr (`Ctrl+Alt` yoki oʻng `Alt`) | Shift + AltGr |
|---|---|---|---|---|
| `W` oʻrni | ş | Ş | w | W |
| `C` oʻrni | ç | Ç | c | C |
| `[` | ö | Ö | [ | { |
| `]` | ğ | Ğ | ] | } |
| `'` | ʼ (tutuq) | " | ' | ʻ |

Qolgan harflar, raqamlar va tinish belgilari (`; , . / \ - =` va h.k.) oʻz joyida.
`ng` alohida harf emas. Ş va Ç uchun Caps Lock ishlaydi.

## Texnik maʼlumot

* `windows/make_klc.py` — sxemani (`.klc`) yaratadi; `windows/build.ps1` — Microsoft Keyboard Layout Creator (`kbdutool`) bilan x86/x64/WOW64 DLL yigʻadi;
  `windows/installer.iss` — Inno Setup oʻrnatuvchisi (DLL → `System32`/`SysWOW64`, reestr yozuvi, foydalanuvchi tillar roʻyxatiga qoʻshish).
* CI (`.github/workflows/windows.yml`) har safar sxemani **haqiqiy Windows'da oʻrnatib**, `ToUnicodeEx` bilan 20 ta tugma natijasini tekshiradi va oʻchirishni sinaydi.
* Cheklov: ARM64 Windows hozircha qoʻllab-quvvatlanmaydi (MSKLC ARM64 DLL yasamaydi). Oʻrnatuvchi imzolanmagan.

---

## English

System-level keyboard layout for Windows 10/11 implementing the new Uzbek Latin alphabet (Ş Ç Ö Ğ ʼ). It is a native layout DLL loaded by Windows
itself: no background process, works everywhere including elevated windows. Download `Alifbo-Windows-Setup-vX.Y.Z.exe` from
[Releases](../../releases), run it as administrator, then pick *Uzbek (Latin 2026) - Alifbo* with `Win + Space`.
Keys: Ş on `W`, Ç on `C`, Ö on `[`, Ğ on `]`, tutuq ʼ on `'`; `w`/`c` via AltGr. All standard shortcuts (`Ctrl+C`…) are unchanged.
