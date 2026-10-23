#!/usr/bin/env python3
"""
Oʻzbekcha lugʻat generatori.

tools/uz_lemmas_*.txt dagi asosiy shakllardan (lemma) qoʻshimchalar (koʻplik, egalik, kelishik, fe'l zamonlari va h.k.)
qoida boʻyicha hosil qilib, registry/dict/uz.txt (lotin) va registry/dict/uz_cyrl.txt (kirill) fayllarini yozadi.
Format: soʻz<TAB>chastota. Ilova ikkala faylni ham surib yozish va soʻz tavsiyalari uchun ishlatadi.

Ishlatish:  python3 tools/make_uz_lexicon.py
Yangi soʻz qoʻshish: tegishli uz_lemmas_*.txt ga lemmani yozing ([N] ot, [A] sifat, [V] fe'l ildizi, [P] oʻzgarmas soʻzlar).
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "registry" / "dict"
MAXN = 150000

OKINA = "ʻ"   # ʻ  (oʻ, gʻ)
TUTUQ = "ʼ"   # ʼ  (tutuq)


def norm(w):
    w = w.lower().replace("’", "'").replace("‘", "'").replace("`", "'")
    out = []
    for ch in w:
        if ch == "'":
            prev = out[-1] if out else ""
            out.append(OKINA if prev in ("o", "g") else TUTUQ)
        else:
            out.append(ch)
    return "".join(out)


def vowel_final(w):
    return w[-1] in "aeiou" or w.endswith("o" + OKINA)


def syllables(w):
    return sum(1 for ch in w if ch in "aeiou")


# ---------------------------------------------------------------- ot / sifat

def dat(w):
    if w.endswith("k"):
        return ["ka"]
    if w.endswith("q"):
        return ["qa"]
    if w.endswith("g" + OKINA):
        return ["qa", "ga"]
    return ["ga"]


CASE_W = {"ning": 0.45, "ni": 0.5, "ga": 0.5, "da": 0.45, "dan": 0.45, "dek": 0.08, "day": 0.08,
          "cha": 0.08, "dagi": 0.2, "niki": 0.1, "gacha": 0.1}


def case_forms(s):
    out = {}
    for c, w in CASE_W.items():
        if c == "ga":
            for d in dat(s):
                out[s + d] = w
        elif c == "gacha":
            for d in dat(s):
                out[s + d[:-1] + "gacha" if False else s + ("kacha" if d == "ka" else "qacha" if d == "qa" else "gacha")] = w
        else:
            out[s + c] = w
    return out


def possessives(s):
    """[(shakl, shaxs)] — k/q bilan tugagan koʻp boʻgʻinli soʻzlarda undosh almashadi (yurak -> yuragi)."""
    v = vowel_final(s)
    res = []
    stems = [s]
    if not v and syllables(s) >= 2 and s[-1] in "kq":
        stems = [s[:-1] + ("g" if s[-1] == "k" else "g" + OKINA)]
    for st in stems:
        res.append((st + ("m" if v else "im"), "1s"))
        res.append((st + ("ng" if v else "ing"), "2s"))
        res.append((st + ("si" if v else "i"), "3"))
        res.append((st + ("miz" if v else "imiz"), "1p"))
        res.append((st + ("ngiz" if v else "ingiz"), "2p"))
    return res


def noun_forms(lemma, w=1.0, rich=True):
    forms = {}

    def add(f, x):
        if x > forms.get(f, 0):
            forms[f] = x

    for stem, sw in ((lemma, 1.0), (lemma + "lar", 0.5)):
        add(stem, w * sw)
        for f, cw in case_forms(stem).items():
            add(f, w * sw * cw)
        if not rich:
            continue
        for p, who in possessives(stem):
            add(p, w * sw * 0.3)
            for f, cw in case_forms(p).items():
                add(f, w * sw * 0.12 * (cw / 0.5))
    if rich:
        for suf in ("man", "san", "miz", "siz", "dir"):
            add(lemma + suf, w * 0.08)
        for suf, sw in (("li", 0.1), ("siz", 0.1), ("lik", 0.15), ("chi", 0.1)):
            d = lemma + suf
            add(d, w * sw)
            add(d + "lar", w * sw * 0.4)
            for c in ("ni", "ning", "da", "dan"):
                add(d + c, w * sw * 0.3)
            for g in dat(d):
                add(d + g, w * sw * 0.3)
    return forms


# ---------------------------------------------------------------- fe'l

PERS_PRES = ["man", "san", "di", "miz", "siz", "dilar"]
PERS_PAST = ["m", "ng", "", "k", "ngiz", "lar"]
PERS_NOM = ["man", "san", "", "miz", "siz", "lar"]


def gan_suffix(st, base="gan"):
    if st.endswith("k"):
        return "k" + base[1:]
    if st.endswith("q"):
        return "q" + base[1:]
    return base


def verb_forms(st, w=1.0):
    v = vowel_final(st) and st[-1] != OKINA
    if st.endswith("o" + OKINA):
        v = True
    forms = {}

    def add(f, x):
        if x > forms.get(f, 0):
            forms[f] = x

    pres = st + ("y" if v else "a")
    neg = st + "ma"

    add(st, w * 0.5)
    add(st + "moq", w * 0.3)
    for e in ("chi", "chiman", "chisan", "chimiz", "chisiz"):
        add(st + "moq" + e, w * 0.06)
    # hozirgi-kelasi zamon
    for e in PERS_PRES:
        add(pres + e, w * (0.55 if e in ("di", "man", "miz") else 0.25))
        add(neg + "y" + e, w * (0.35 if e in ("di", "man") else 0.12))
    # davom etayotgan
    for e in ("man", "san", "ti", "miz", "siz", "tilar"):
        add(st + "yap" + e, w * (0.3 if e in ("ti", "man") else 0.1))
        add(neg + "yap" + e, w * 0.06)
        add(st + ("yotgan" if v else "ayotgan") + ("" if e in ("ti",) else ""), w * 0.2)
    for e in PERS_NOM:
        add(st + ("yotgan" if v else "ayotgan") + e, w * 0.08)
        add(st + "moqda" + e, w * 0.15 if e == "" else w * 0.06)
        add(st + "moqchi" + e, w * 0.1 if e == "" else w * 0.04)
    # oʻtgan zamon
    for e in PERS_PAST:
        add(st + "di" + e, w * (0.5 if e in ("", "m", "k") else 0.2))
        add(neg + "di" + e, w * (0.25 if e in ("", "m") else 0.08))
    # -gan sifatdosh
    g = gan_suffix(st)
    for e in PERS_NOM + ["da", "dan", "ni", "i", "im", "ing", "imiz", "ingiz", "ligi", "lar" + "ni", "ga", "ning"]:
        add(st + g + e, w * (0.4 if e == "" else 0.1))
    gm = "magan"
    for e in PERS_NOM + ["da", "ni", "i"]:
        add(st + gm + e, w * 0.05)
    add(st + g + "da", w * 0.2)
    for suf in ("guncha", "gach", "gani", "ganday", "gandan", "gancha"):
        s2 = gan_suffix(st, suf)
        add(st + s2, w * 0.1)
    # -ib, -ish, -uv, -uvchi, -ma
    add(st + ("b" if v else "ib"), w * 0.4)
    ish = st + ("sh" if v else "ish")
    for f, x in noun_forms(ish, w * 0.35, rich=False).items():
        add(f, x)
    add(ish + "i", w * 0.15)
    add(ish + "im", w * 0.06)
    add(ish + "imiz", w * 0.06)
    uv = (st[:-1] if st.endswith("i") else st) + "uv"
    if not (v and not st.endswith("i")):
        add(uv, w * 0.1)
        add(uv + "chi", w * 0.12)
        add(uv + "chilar", w * 0.06)
        add(uv + "ni", w * 0.04)
    add(st + "ma", w * 0.12)
    add(st + "malar", w * 0.04)
    # buyruq va istak
    for suf, x in (("ing", 0.3), ("gin", 0.1), ("inglar", 0.1), ("sin", 0.2), ("sinlar", 0.1), ("ay", 0.15),
                   ("aylik", 0.15), ("ay" + "lik", 0.0), ("mang", 0.2), ("manglar", 0.05), ("maylik", 0.04),
                   ("masin", 0.08), ("sa", 0.35), ("masa", 0.15), ("ar", 0.08), ("mas", 0.15)):
        if v and suf in ("ing", "inglar", "ay", "aylik"):
            suf = {"ing": "ng", "inglar": "nglar", "ay": "y", "aylik": "ylik"}[suf]
        if suf == "gin":
            suf = gan_suffix(st, "gin")
        if suf == "sa":
            for e in PERS_PAST:
                add(st + "sa" + e, w * (0.3 if e in ("", "m") else 0.1))
                add(neg + "sa" + e, w * 0.05)
            continue
        add(st + suf, w * x)
    return forms


def derived_stems(st):
    v = vowel_final(st)
    out = []
    # majhul nisbat
    if st.endswith("l"):
        out.append(st + "in")
    elif v:
        out.append(st + "n")
    else:
        out.append(st + "il")
    # ortirma nisbat
    if st[-1] in "kqptsfxh" or st.endswith(("ch", "sh")):
        out.append(st + "tir")
    elif v or st[-1] in "lr":
        out.append(st + "t")
    else:
        out.append(st + "dir")
    # oʻzlik
    if not st.endswith("n"):
        out.append(st + ("n" if v else "in"))
    return out


# ---------------------------------------------------------------- kirill

def to_cyrillic(w):
    w = w.replace(OKINA, "ʻ").replace(TUTUQ, "ʼ")
    res = []
    i = 0
    n = len(w)
    vow = set("aeiouоʻ")

    def prev_is_vowel_or_start():
        if not res:
            return True
        return res[-1] in "аеёиоуўэюя"

    while i < n:
        two = w[i:i + 2]
        c = w[i]
        if two == "sh":
            res.append("ш"); i += 2; continue
        if two == "ch":
            res.append("ч"); i += 2; continue
        if two == "oʻ":
            res.append("ў"); i += 2; continue
        if two == "gʻ":
            res.append("ғ"); i += 2; continue
        if two == "ng":
            res.append("нг"); i += 2; continue
        if two == "yo":
            res.append("ё"); i += 2; continue
        if two == "yu":
            res.append("ю"); i += 2; continue
        if two == "ya":
            res.append("я"); i += 2; continue
        if two == "ye":
            res.append("е"); i += 2; continue
        if c == "e":
            res.append("э" if prev_is_vowel_or_start() else "е"); i += 1; continue
        m = {"a": "а", "b": "б", "d": "д", "f": "ф", "g": "г", "h": "ҳ", "i": "и", "j": "ж", "k": "к", "l": "л",
             "m": "м", "n": "н", "o": "о", "p": "п", "q": "қ", "r": "р", "s": "с", "t": "т", "u": "у", "v": "в",
             "x": "х", "y": "й", "z": "з", "ʼ": "ъ", "ʻ": "", "c": "ц"}
        res.append(m.get(c, c))
        i += 1
    return "".join(res)


# ---------------------------------------------------------------- yigʻish

def read_lemmas():
    items = []   # (section, lemma)
    for p in sorted((ROOT / "tools").glob("uz_lemmas_*.txt")):
        sec = "N"
        for line in p.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            m = re.fullmatch(r"\[(\w)\]", line)
            if m:
                sec = m.group(1)
                continue
            for tok in line.split():
                if "-" in tok:
                    continue
                items.append((sec, norm(tok)))
    # Qoʻlda yigʻilgan qoʻshimcha soʻzlar (tayyor shakllar, qoʻshimchasiz) — eng oxirida.
    extra = ROOT / "tools" / "uz_words.txt"
    if extra.exists():
        for tok in extra.read_text(encoding="utf-8").split():
            if "-" not in tok:
                items.append(("P", norm(tok)))
    return items


def main():
    items = read_lemmas()
    scores = {}

    def add(f, x):
        if not f or not re.fullmatch(r"[a-zʻʼ]+", f):
            return
        if x > scores.get(f, 0):
            scores[f] = x

    seen = set()
    for rank, (sec, lemma) in enumerate(items):
        key = (sec, lemma)
        if key in seen:
            continue
        seen.add(key)
        prior = 1.0 / (1.0 + rank / 250.0)
        if sec == "P":
            add(lemma, prior * 1.0)
        elif sec in ("N", "A"):
            for f, x in noun_forms(lemma, prior).items():
                add(f, x)
            if sec == "A":
                for f, x in noun_forms(lemma + "roq", prior * 0.3, rich=False).items():
                    add(f, x)
        elif sec == "V":
            for f, x in verb_forms(lemma, prior).items():
                add(f, x)
            for d in derived_stems(lemma):
                for f, x in verb_forms(d, prior * 0.3).items():
                    add(f, x)
    ranked = sorted(scores.items(), key=lambda kv: -kv[1])[:MAXN]
    OUT.mkdir(parents=True, exist_ok=True)
    top = ranked[0][1]
    lat = []
    cyr = []
    for w, x in ranked:
        c = max(1, int(x / top * 1_000_000))
        lat.append(f"{w}\t{c}\n")
        cyr.append(f"{to_cyrillic(w)}\t{c}\n")
    (OUT / "uz.txt").write_text("".join(lat), encoding="utf-8")
    (OUT / "uz_cyrl.txt").write_text("".join(cyr), encoding="utf-8")
    print(f"lemmalar: {len(seen)}, shakllar: {len(scores)}, yozildi: {len(ranked)}")


if __name__ == "__main__":
    main()
