#!/usr/bin/env python3
"""Lugʻat generatori uchun tezkor tekshiruv (CI da ishga tushadi): muhim shakllar mavjudligi va fayl formati."""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
O, T = "ʻ", "ʼ"


def load(name):
    d = {}
    for line in (ROOT / "registry" / "dict" / name).read_text(encoding="utf-8").splitlines():
        w, c = line.split("\t")
        d[w] = int(c)
    return d


def main():
    lat = load("uz.txt")
    cyr = load("uz_cyrl.txt")
    must = ["salom", "rahmat", "kitobim", "kitoblarimizni", "yuragim", "yozaman", "yozmayman", "yoziladi",
            f"o{O}qiyman", f"o{O}qiydi", "bormoqchiman", "keldingiz", "kelmadi", f"tog{O}qa", "maktabga", "maktabda",
            "shahrida", "shahrimiz", f"o{O}g{O}lim", "yaxshiroq", "tinchlikmi", "klaviaturani",
            "telefonimdan", "bajarildi", "kutilmoqda", "kelolmayman"]
    missing = [m for m in must if m not in lat]
    bad = []
    if missing:
        bad.append("lotin lugʻatda yoʻq: " + ", ".join(missing))
    for w in ("салом", "раҳмат", "китобим", "ўқийман"):
        if w not in cyr:
            bad.append("kirill lugʻatda yoʻq: " + w)
    if len(lat) < 100000:
        bad.append(f"lugʻat juda kichik: {len(lat)}")
    if any(not w or any(c in w for c in " \t") for w in lat):
        bad.append("lugʻatda boʻsh joyli soʻz bor")
    if bad:
        print("\n".join(bad))
        sys.exit(1)
    print(f"OK: lotin {len(lat)}, kirill {len(cyr)}")


if __name__ == "__main__":
    main()
