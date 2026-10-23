#!/usr/bin/env python3
"""
Alifbo — Windows klaviatura sxemasi (yangi oʻzbek lotin alifbosi, 2026) uchun .klc faylini yaratadi.
.klc dan Microsoft Keyboard Layout Creator (kbdutool) tizim darajasidagi kbd*.dll yasaydi.

Ishlatish:  python3 windows/make_klc.py [chiqish_papkasi]   ->  alifbo26.klc (UTF-16LE, CRLF)

Sxema (QWERTY asosida, Ctrl+C / Ctrl+V kabi tezkor tugmalar oʻzgarmaydi — VK kodlari standart):
  W oʻrnida Ş, C oʻrnida Ç  (w, c — AltGr+Ş, AltGr+Ç)
  [  oʻrnida Ö,  ]  oʻrnida Ğ   (AltGr+[ , AltGr+] — [ ] )
  '  oʻrnida ʼ (tutuq belgisi)  (Shift — ", AltGr+' — oddiy ', AltGr+Shift+' — ʻ)
  Qolgan tinish belgilari oʻz joyida.
"""
import pathlib
import sys

NAME = "alifbo26"
TITLE = "Uzbek (Latin 2026) - Alifbo"

# Shift holatlari: 0 - asosiy, 1 - Shift, 2 - Ctrl, 6 - Ctrl+Alt (AltGr), 7 - Shift+Ctrl+Alt
STATES = [0, 1, 2, 6, 7]


def cp(ch):
    """Belgi -> KLC dagi 4 xonali hex (yoki -1)."""
    if ch is None:
        return "-1"
    return "%04x" % ord(ch)


def key(sc, vk, cap, base, shift, ctrl=None, alt=None, altshift=None):
    return (sc, vk, cap, base, shift, ctrl, alt, altshift)


def letters():
    L = []
    # raqamlar qatori
    digits = "1234567890"
    shifted = "!@#$%^&*()"
    for i, (d, s) in enumerate(zip(digits, shifted)):
        L.append(key("%02x" % (2 + i), d, 0, d, s))
    L.append(key("0c", "OEM_MINUS", 0, "-", "_", "\x1f"))
    L.append(key("0d", "OEM_PLUS", 0, "=", "+"))
    # 1-qator: Q Ş E R T Y U I O P  [Ö] [Ğ]
    L.append(key("10", "Q", 1, "q", "Q", "\x11"))
    L.append(key("11", "W", 5, "ş", "Ş", "\x17", "w", "W"))
    for sc, vk, ch in (("12", "E", "e"), ("13", "R", "r"), ("14", "T", "t"), ("15", "Y", "y"),
                       ("16", "U", "u"), ("17", "I", "i"), ("18", "O", "o"), ("19", "P", "p")):
        L.append(key(sc, vk, 1, ch, ch.upper(), chr(ord(ch) - 96)))
    L.append(key("1a", "OEM_4", 1, "ö", "Ö", "\x1b", "[", "{"))
    L.append(key("1b", "OEM_6", 1, "ğ", "Ğ", "\x1d", "]", "}"))
    # 2-qator
    for sc, vk, ch in (("1e", "A", "a"), ("1f", "S", "s"), ("20", "D", "d"), ("21", "F", "f"), ("22", "G", "g"),
                       ("23", "H", "h"), ("24", "J", "j"), ("25", "K", "k"), ("26", "L", "l")):
        L.append(key(sc, vk, 1, ch, ch.upper(), chr(ord(ch) - 96)))
    L.append(key("27", "OEM_1", 0, ";", ":"))
    L.append(key("28", "OEM_7", 0, "ʼ", '"', None, "'", "ʻ"))
    L.append(key("29", "OEM_3", 0, "`", "~"))
    L.append(key("2b", "OEM_5", 0, "\\", "|", "\x1c"))
    # 3-qator: Z X Ç V B N M
    L.append(key("2c", "Z", 1, "z", "Z", "\x1a"))
    L.append(key("2d", "X", 1, "x", "X", "\x18"))
    L.append(key("2e", "C", 5, "ç", "Ç", "\x03", "c", "C"))
    for sc, vk, ch in (("2f", "V", "v"), ("30", "B", "b"), ("31", "N", "n"), ("32", "M", "m")):
        L.append(key(sc, vk, 1, ch, ch.upper(), chr(ord(ch) - 96)))
    L.append(key("33", "OEM_COMMA", 0, ",", "<"))
    L.append(key("34", "OEM_PERIOD", 0, ".", ">"))
    L.append(key("35", "OEM_2", 0, "/", "?"))
    L.append(key("39", "SPACE", 0, " ", " ", " "))
    L.append(key("56", "OEM_102", 0, "<", ">", "\x1c", "|", None))
    L.append(key("53", "DECIMAL", 0, ".", ".", None))
    return L


def render():
    out = []
    out.append(f'KBD\t{NAME}\t"{TITLE}"\n')
    out.append('COPYRIGHT\t"(c) 2026 Alifbo Klaviatura"\n')
    out.append('COMPANY\t"Alifbo"\n')
    out.append('LOCALENAME\t"uz-Latn-UZ"\n')
    out.append('LOCALEID\t"00000443"\n')
    out.append("VERSION\t1.0\n")
    out.append("SHIFTSTATE\n")
    names = {0: "", 1: " Shft", 2: "       Ctrl", 6: "       Ctrl Alt", 7: " Shft  Ctrl Alt"}
    for i, s in enumerate(STATES):
        out.append(f"{s}\t//Column {4 + i} :{names[s]}")
    out.append("")
    out.append("LAYOUT\t\t;an extra '@' at the end is a dead key\n")
    out.append("//SC\tVK_\t\tCap\t0\t1\t2\t6\t7")
    out.append("//--\t----\t\t----\t----\t----\t----\t----\t----\n")
    for sc, vk, cap, base, shift, ctrl, alt, altshift in letters():
        cols = [cp(base), cp(shift), cp(ctrl), cp(alt), cp(altshift)]
        desc = ", ".join(("<none>" if c == "-1" else "U+" + c.upper()) for c in cols)
        out.append(f"{sc}\t{vk}\t\t{cap}\t" + "\t".join(cols) + f"\t// {desc}")
    out.append("")
    out.append("DESCRIPTIONS\n")
    out.append(f"0409\t{TITLE}")
    out.append(f"0443\t{TITLE}\n")
    out.append("LANGUAGENAMES\n")
    out.append("0409\tUzbek (Latin)")
    out.append("0443\tOʻzbek (lotin)\n")
    out.append("ENDKBD")
    return "\r\n".join(out) + "\r\n"


def main():
    outdir = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "windows/out")
    outdir.mkdir(parents=True, exist_ok=True)
    path = outdir / f"{NAME}.klc"
    # MSKLC UTF-16LE (BOM) kutadi
    path.write_bytes(b"\xff\xfe" + render().encode("utf-16-le"))
    print("yozildi:", path)


if __name__ == "__main__":
    main()
