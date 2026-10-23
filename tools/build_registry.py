#!/usr/bin/env python3
"""
Alifbo paketlarini tekshiradi va registry/registry.json ni yangilaydi.

Alifbo oʻzgarganda:
  1) registry/packs/<id>.json ni tahrirlang va "version" ni 1 ga oshiring
  2) python3 tools/build_registry.py
  3) git commit && git push      -> telefonlar 6 soat ichida (yoki klaviatura ochilganda) yangilanadi

Tekshiruvlar ilovadagi PackParser (Kotlin) bilan bir xil.
"""
import datetime
import hashlib
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent / "registry"
PACKS = ROOT / "packs"
ID_RE = re.compile(r"^[a-z0-9][a-z0-9-]{1,31}$")
TOKENS = {"@shift", "@del", "@sym", "@abc", "@lang", "@space", "@enter"}
WEIGHTS = {"@shift": 1.5, "@del": 1.5, "@sym": 1.3, "@abc": 1.3, "@lang": 1.1, "@space": 4.0, "@enter": 1.5}


def key_info(e):
    if isinstance(e, dict):
        label = e["k"]
        w = e.get("w")
    else:
        label, w = str(e), None
    is_token = len(label) > 1 and label.startswith("@")
    if is_token:
        if label not in TOKENS:
            raise ValueError(f"nomaʼlum maxsus tugma: {label}")
    elif not label or len(label) > 4:
        raise ValueError(f"tugma yorligʻi notoʻgʻri: {label!r}")
    w = float(w) if w is not None else WEIGHTS.get(label, 1.0)
    if not 0.5 <= w <= 8:
        raise ValueError(f"tugma kengligi 0.5..8 boʻlishi kerak: {label} = {w}")
    return label, w, is_token


def validate(p, fname):
    warns = []
    if p.get("schema", 1) > 1:
        raise ValueError("schema > 1")
    if not ID_RE.match(p["id"]):
        raise ValueError(f"id notoʻgʻri: {p['id']}")
    if fname != p["id"] + ".json":
        raise ValueError(f"fayl nomi id ga mos emas: {fname} != {p['id']}.json")
    if not isinstance(p["version"], int) or p["version"] < 1:
        raise ValueError("version butun son va >= 1 boʻlishi kerak")
    if not p.get("name"):
        raise ValueError("name kerak")
    rows = p["rows"]
    if not 1 <= len(rows) <= 5:
        raise ValueError("rows soni 1..5 boʻlishi kerak")

    sums = []
    letters = []
    for r in rows:
        if not 1 <= len(r) <= 14:
            raise ValueError("qatordagi tugmalar soni 1..14 boʻlishi kerak")
        s = 0.0
        for e in r:
            label, w, tok = key_info(e)
            s += w
            if not tok:
                letters.append(label)
        sums.append(s)

    bottom = p.get("bottom")
    if bottom is not None:
        labels = []
        for e in bottom:
            label, w, tok = key_info(e)
            labels.append(label)
        if "@space" not in labels:
            raise ValueError("bottom qatorida @space boʻlishi kerak")

    if len(set(letters)) != len(letters):
        dup = sorted({x for x in letters if letters.count(x) > 1})
        warns.append(f"takroriy harflar: {dup}")
    if sums and max(sums) - min(sums) > 2.01:
        warns.append(f"qatorlar kengligi farqli: {sums}")

    for k, v in (p.get("alternates") or {}).items():
        if not isinstance(v, list) or not all(isinstance(x, str) for x in v):
            raise ValueError(f"alternates[{k}] satrlar roʻyxati boʻlishi kerak")

    idx = p.get("numberHintsRow", 0)
    if p.get("numberHints", True) and not 0 <= idx < len(rows):
        raise ValueError("numberHintsRow diapazondan tashqarida")
    return warns


def main():
    entries = []
    ok = True
    for f in sorted(PACKS.glob("*.json")):
        raw = f.read_bytes()
        try:
            p = json.loads(raw.decode("utf-8"))
            warns = validate(p, f.name)
        except Exception as ex:  # noqa: BLE001
            print(f"XATO  {f.name}: {ex}")
            ok = False
            continue
        for w in warns:
            print(f"ogohlantirish  {f.name}: {w}")
        entries.append({
            "id": p["id"],
            "version": p["version"],
            "name": p["name"],
            "country": p.get("country", ""),
            "url": f"packs/{f.name}",
            "sha256": hashlib.sha256(raw).hexdigest(),
        })
        print(f"OK    {f.name}  v{p['version']}")

    if not ok:
        print("\nRegistry yozilmadi: xatolarni tuzating.")
        sys.exit(1)

    reg = {
        "schema": 1,
        "updated": datetime.date.today().isoformat(),
        "packs": entries,
    }
    (ROOT / "registry.json").write_text(
        json.dumps(reg, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"\nregistry.json yozildi: {len(entries)} ta paket")


if __name__ == "__main__":
    main()
