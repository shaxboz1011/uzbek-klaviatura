#!/usr/bin/env python3
"""
Surib yozish (swipe) lugʻatlarini yaratadi: registry/dict/<til>.txt  (har qatorda: soʻz<TAB>chastota).
en, ru — hermitdave/FrequencyWords (OpenSubtitles 2018, CC-BY-SA 4.0) dan yuklanadi.
uz — tools/uz_words.txt (qoʻlda yigʻilgan boshlangʻich roʻyxat, chastota tartibida); chastota oʻrniga tartib raqami ishlatiladi.
"""
import pathlib
import re
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "registry" / "dict"
URL = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/{l}/{l}_50k.txt"
LIMIT = 30000


def freq_list(lang, pattern):
    raw = urllib.request.urlopen(URL.format(l=lang), timeout=60).read().decode("utf-8")
    ok = re.compile(pattern)
    out = []
    for line in raw.splitlines():
        p = line.split()
        if len(p) != 2 or not p[1].isdigit():
            continue
        w = p[0].lower()
        if (len(w) >= 2 or w in ("a", "i")) and ok.fullmatch(w):
            out.append((w, int(p[1])))
        if len(out) >= LIMIT:
            break
    return out


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for lang, pat in (("en", r"[a-z]+(?:'[a-z]+)?"), ("ru", r"[а-яё]+")):
        rows = freq_list(lang, pat)
        (OUT / f"{lang}.txt").write_text("".join(f"{w}\t{c}\n" for w, c in rows), encoding="utf-8")
        print(lang, len(rows))
    words = []
    for line in (ROOT / "tools" / "uz_words.txt").read_text(encoding="utf-8").split():
        w = line.strip().lower()
        if w and w not in words:
            words.append(w)
    n = len(words)
    (OUT / "uz.txt").write_text("".join(f"{w}\t{max(1, 1000000 // (i + 5))}\n" for i, w in enumerate(words)), encoding="utf-8")
    print("uz", n)


if __name__ == "__main__":
    main()
