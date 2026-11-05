#!/usr/bin/env python3
"""Generates a stub `R` object (stubs/R.kt) from app/src/main/res so the non-UI Kotlin can be type-checked
without the Android build (see README.md in this folder)."""
import pathlib, re

here = pathlib.Path(__file__).resolve().parent
res = here.parents[1] / "app" / "src" / "main" / "res"
groups = {}

def add(g, n):
    groups.setdefault(g, set()).add(n)

for d in res.iterdir():
    if not d.is_dir():
        continue
    t = d.name.split("-")[0]
    for f in d.iterdir():
        if t in ("layout", "drawable", "font", "xml", "mipmap", "anim", "menu"):
            add(t, f.stem)
        if t == "layout":
            for m in re.finditer(r"@\+id/(\w+)", f.read_text(encoding="utf-8")):
                add("id", m.group(1))
        if t == "values":
            txt = f.read_text(encoding="utf-8")
            for m in re.finditer(r'<string name="(\w+)"', txt):
                add("string", m.group(1))
            for m in re.finditer(r'<color name="(\w+)"', txt):
                add("color", m.group(1))

out = ["package app.personal.clock", "object R {"]
n = 1
for g, names in sorted(groups.items()):
    out.append(f"    object {g} {{")
    for nm in sorted(names):
        out.append(f"        const val {nm} = {n}")
        n += 1
    out.append("    }")
out.append("}")
(here / "fwcheck" / "stubs").mkdir(parents=True, exist_ok=True)
(here / "fwcheck" / "stubs" / "R.kt").write_text("\n".join(out))
print({g: len(v) for g, v in groups.items()})
