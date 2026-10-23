# Alifbo Klaviatura — Android va Windows

Android uchun klaviatura (IME) va **Windows 10/11 uchun tizim darajasidagi klaviatura sxemasi** ([windows/README.md](windows/README.md)). Yangi oʻzbek lotin alifbosi (Ş Ç Ö Ğ ʼ), eski lotin, kirill va boshqa tillar.
Alifbolar **JSON paketlari** sifatida saqlanadi va **serverdan yangilanadi** — alifbo oʻzgarsa, ilovani qayta
chiqarish shart emas.

## Holat

Kod yozilgan, lekin **hali kompilyatsiya qilinmagan va telefonda sinalmagan** (build muhitida Android SDK yoʻq edi).
Alifbo paketlari (`registry/packs`) Python bilan tekshirilgan. Birinchi build'da kichik kompilyatsiya xatolari chiqishi mumkin.

## APK olish

**Tayyor versiyalar:** [Releases](../../releases) sahifasidan `Alifbo-vX.Y.Z.apk` ni yuklab oling (zip emas, toʻgʻridan-toʻgʻri APK). Yangi versiya eskisining ustiga oʻrnatiladi.

### Yangi versiya chiqarish (maintainer uchun)

Bir martalik sozlash — repo **Settings → Secrets and variables → Actions** ga 4 ta secret: `ANDROID_KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Kalitni yoʻqotmang: usiz keyingi versiyalar eskisining ustiga oʻrnatilmaydi. Kalitni repoga **yuklamang**.

Keyin: `git tag v1.2.0 && git push origin v1.2.0` (yoki Actions → *Release APK* → *Run workflow*). `versionCode` tegdan hisoblanadi
(`major*10000 + minor*100 + patch`), shuning uchun versiyalar faqat oshib borishi kerak.

## APK olish (GitHub Actions orqali sinov build)

1. Bu papkani GitHub'ga yuklang (private repo ham boʻladi).
2. `Actions` → `Build APK` → `Run workflow` (yoki `main` ga push qiling).
3. Tugagach `alifbo-klaviatura-debug` artifactidan APK ni yuklab oling, telefonga oʻrnating.

Mahalliy: Android Studio (JDK 17) da oching → `Run`. Yoki `gradle :app:assembleDebug`.

## Telefonda yoqish

Ilovani oching → «1. Klaviaturani yoqish» → roʻyxatda «Alifbo Klaviatura» ni yoqing →
«2. Klaviaturani tanlash». Globus tugmasi: bosish — keyingi til, uzoq bosish — tizim klaviatura tanlagichi.

## Funksiyalar (Samsung klaviaturasiga oʻxshash)

- Tepada asboblar qatori: emoji, tarjima, bufer, ovozli kiritish, sozlamalar, «…». Yozayotganda shu qator **soʻz tavsiyalariga** aylanadi.
- Doimiy raqamlar qatori (sozlamalardan oʻchirsa boʻladi).
- **Emoji**: 8 toifa + oxirgi ishlatilganlar.
- **Bufer tarixi**: nusxalangan matnlar (30 tagacha), uzoq bosib «qadash». Faqat shu telefonda saqlanadi; parol menejerlari belgilagan maxfiy nusxalar saqlanmaydi.
- **Ovozli kiritish**: tizimning nutq aniqlash oynasi orqali (Google ilovasi kerak; til — joriy klaviatura tili).
- **Kursor**: boʻsh joy tugmasi ustida barmoqni surish.
- **Bir qoʻl rejimi**: asboblar qatoridagi tugma — oʻchiq → oʻng → chap.
- **Tezkor tarjima** (asboblar qatoridagi «A文»): maydondagi yoki belgilangan matnni 15 tilga tarjima qiladi, keyin «Almashtirish» / «Qoʻshish» / «Nusxa». Matn MyMemory (mymemory.translated.net) xizmatiga yuboriladi — birinchi marta rozilik soʻraladi; parol/email/URL maydonlarida ishlamaydi.
- **«…» menyusi**: hammasini tanlash, nusxa, kesish, qoʻyish, kursor, hammasini oʻchirish, bir qoʻl rejimi, mavzu (tizim/och/toʻq), tovush, sozlamalar, yopish.
- **Mavzu va urgʻu rangi**, tugma tovushi, tugma ustida harf chiqishi, ikki marta boʻsh joy — nuqta, ikki sahifali belgilar (1/2, 2/2), bir qoʻl rejimidan chiqish tugmasi.
- **Surib yozish (swipe)**: harflar ustidan barmoqni surib soʻz yozing; qoʻshimcha variantlar tepada, orqaga (⌫) bosish butun soʻzni oʻchiradi. Lugʻat: `registry/dict/<til>.txt` (en, ru — 30 000 soʻz; uz — boshlangʻich ~650 soʻz, oʻzbekcha lugʻat siz yozgan/surgan soʻzlar bilan toʻlib boradi). Yangi til uchun lugʻat qoʻshish: `tools/make_dicts.py`. en/ru lugʻati — hermitdave/FrequencyWords (CC-BY-SA 4.0).
- **Soʻz tavsiyalari**: yozgan soʻzlaringizdan oʻrganadi. Parol, email va URL maydonlarida ishlamaydi va oʻrganmaydi.

## Tugmalar

- **Yangi oʻzbek lotin**: Windows'dagi Savodxon sxemasiga mos — `W→Ş`, `C→Ç`, `Ö` va `Ğ` alohida tugmalar, `ʼ` pastki qatorda.
  `w` va `c` kerak boʻlsa (URL, brend nomlari): `Ş` / `Ç` ni uzoq bosing.
- Raqamlar qatori oʻchirilsa: yuqori qatordagi harflarni uzoq bosish — raqam (1…0).
- Backspace ni bosib turish — takroriy oʻchirish. Shift ga ikki marta bosish — Caps Lock.

## Alifbo oʻzgarganda (yoki yangi davlat qoʻshish)

1. `registry/packs/<id>.json` ni tahrirlang (yangi fayl qoʻshsangiz ham boʻladi) va `"version"` ni 1 ga oshiring.
2. `python3 tools/build_registry.py` — tekshiradi va `registry/registry.json` ni (sha256 bilan) yangilaydi.
3. `git push`. Telefonlar har 6 soatda va klaviatura ochilganda (30 daqiqadan tez-tez emas) tekshiradi;
   Sozlamalarda «Hozir yangilash» tugmasi bor. Klaviatura ochiq tursa ham yangi alifbo darrov almashadi.

Paket formati: `registry/packs/uz-latn-2026.json` ga qarang. Maxsus tugmalar: `@shift @del @sym @abc @lang @space @enter`.

### Registry manzili

Standart: `https://raw.githubusercontent.com/OWNER/REPO/main/registry/registry.json`.
GitHub Actions build'i `OWNER/REPO` ni oʻz repongizga avtomatik almashtiradi. Qoʻlda build qilsangiz:
`gradle :app:assembleDebug -PregistryUrl=https://.../registry.json`, yoki ilova Sozlamalarida oʻzgartiring.
Faqat `https://` qabul qilinadi.

## Cheklovlar

- «Barcha davlatlar»: hozir 10 ta paket (UZ ×3, RU, EN, TR, AZ, KZ, KG, TJ). Qolganlari registry orqali qoʻshiladi,
  lekin har bir alifbo va uning oʻzgarishlarini **kimdir kuzatib, JSON ni yangilashi kerak** — buni avtomatlashtirib boʻlmaydi.
- Qozoq lotin alifbosi qoʻshilmagan (rasmiy variant barqaror emas).
- Yangilanish — polling (6 soat + klaviatura ochilganda). Bir zumda push kerak boʻlsa, keyinroq FCM qoʻshiladi.
- Registry xavfsizligi: HTTPS + sha256 (uzatish butunligi). Paketlar faqat maʼlumot (kod emas), lekin
  repo egasi ishonchli boʻlishi kerak. Keyingi qadam: paketlarga imzo (Ed25519).
- Soʻz tavsiyalari boshida boʻsh: boshlangʻich oʻzbek lugʻati yoʻq, siz yozgan sari toʻladi. Sifatli lugʻat (korpus) kerak — u ham registry orqali yangilanadigan qilib qoʻshiladi.
- Hali yoʻq: swipe bilan yozish (glide typing), avto-tuzatish, tarjima va AI tugmalari, mavzular/ranglar tanlash, GIF/stiker, emoji rang variantlari.
- Avto-oʻtkazish (eski `sh`, `ch` yozsangiz yangi `ş`, `ç` boʻlishi) hali yoʻq.
- Debug APK. Play Market uchun release imzosi va `targetSdk` talablari alohida.
