#!/usr/bin/env python3
"""
Builds the word-suggestion dictionaries in app/src/main/assets/dictionaries/.

Source: FrequencyWords by Hermit Dave (OpenSubtitles 2018 frequency lists),
https://github.com/hermitdave/FrequencyWords – content licensed CC BY-SA 4.0.
Pinned to one commit so the output is reproducible.

Output: one "<word> <count>" line per word, lowercase letters only, most frequent first.
Serbian: the source mixes scripts, so both scripts are merged into one list and written
as sr_latn.txt and (transliterated) sr_cyrl.txt.

Run from the repository root:  python3 tools/build_dictionaries.py
"""
import os
import re
import urllib.request

COMMIT = "525f9b560de45753a5ea01069454e72e9aa541c6"
URL = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/{commit}/content/2018/{lang}/{lang}_50k.txt"
OUT = os.path.join("app", "src", "main", "assets", "dictionaries")

# Output file -> source language
SIMPLE = {"en": "en", "hr": "hr", "bs": "bs", "de": "de", "ru": "ru"}

LAT_TO_CYR = [("lj", "љ"), ("nj", "њ"), ("dž", "џ")] + [(a, b) for a, b in zip(
    "abvgdđežzijklmnoprstćufhcčš", "абвгдђежзијклмнопрстћуфхцчш")]
CYR_TO_LAT = {b: a for a, b in LAT_TO_CYR}

WORD = re.compile(r"^[^\W\d_]+(?:'[^\W\d_]+)?$")  # letters, optional inner apostrophe


def fetch(lang):
    with urllib.request.urlopen(URL.format(commit=COMMIT, lang=lang), timeout=60) as r:
        for line in r.read().decode("utf-8").splitlines():
            parts = line.split(" ")
            if len(parts) == 2 and parts[1].isdigit():
                yield parts[0].lower(), int(parts[1])


def clean(pairs):
    counts = {}
    for word, n in pairs:
        if WORD.match(word):
            counts[word] = counts.get(word, 0) + n
    return counts


def to_cyrillic(word):
    out, i = [], 0
    while i < len(word):
        for lat, cyr in LAT_TO_CYR:
            if word.startswith(lat, i):
                out.append(cyr)
                i += len(lat)
                break
        else:
            out.append(word[i])
            i += 1
    return "".join(out)


def to_latin(word):
    return "".join(CYR_TO_LAT.get(ch, ch) for ch in word)


def write(name, counts):
    path = os.path.join(OUT, name + ".txt")
    ordered = sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))
    with open(path, "w", encoding="utf-8") as f:
        for word, n in ordered:
            f.write(f"{word} {n}\n")
    print(f"{name}: {len(ordered)} words")


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, lang in SIMPLE.items():
        write(name, clean(fetch(lang)))

    # Serbian: merge both scripts in Latin, then write Latin and Cyrillic versions
    sr = {}
    for word, n in clean(fetch("sr")).items():
        latin = to_latin(word)
        sr[latin] = sr.get(latin, 0) + n
    write("sr_latn", sr)
    sr_cyrl = {}
    for word, n in sr.items():
        cyr = to_cyrillic(word)
        sr_cyrl[cyr] = sr_cyrl.get(cyr, 0) + n
    write("sr_cyrl", sr_cyrl)


if __name__ == "__main__":
    main()
