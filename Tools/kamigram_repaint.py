#!/usr/bin/env python3
"""
Repaint KamiGram's theme layer with the pastel skeuomorphic palette.

Targets:
  1. ThemeColors.java  -> light-mode compiled defaults
  2. *.attheme assets  -> per-theme overrides, light or dark depending on theme

Idempotent: re-running produces the same output, because every value is derived
from the ORIGINAL upstream colour recorded in the baseline file, not from the
current on-disk value. The baseline is captured on first run.

Usage:
    python3 Tools/kamigram_repaint.py [--check]

--check reports what would change without writing.
"""

import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kamigram_palette as K  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
THEME_COLORS = os.path.join(
    ROOT, "TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ThemeColors.java"
)
ASSETS = os.path.join(ROOT, "TMessagesProj/src/main/assets")
BASELINE = os.path.join(ROOT, "Tools/kamigram_baseline.json")

# Which asset themes are dark. Everything else is treated as light.
DARK_ASSETS = {"night.attheme", "darkblue.attheme"}

CHECK = "--check" in sys.argv


# --------------------------------------------------------------- key name maps


def load_key_names():
    """java key name -> .attheme string name, from colorKeysMap."""
    src = open(THEME_COLORS, encoding="utf8").read()
    pairs = re.findall(r'colorKeysMap\.put\(key_(\w+),\s*"([^"]+)"\)', src)
    to_str = {k: n for k, n in pairs}
    to_key = {n: k for k, n in pairs}
    return to_str, to_key


# ------------------------------------------------------------------- baseline


def load_baseline():
    if os.path.exists(BASELINE):
        with open(BASELINE, encoding="utf8") as fh:
            return json.load(fh)
    return {"themecolors": {}, "assets": {}}


def save_baseline(data):
    if CHECK:
        return
    with open(BASELINE, "w", encoding="utf8") as fh:
        json.dump(data, fh, indent=1, sort_keys=True)


# --------------------------------------------------------------- ThemeColors.java


def repaint_theme_colors(baseline):
    src = open(THEME_COLORS, encoding="utf8").read()
    base = baseline["themecolors"]
    first_run = not base

    hex_asgn = re.compile(r"(defaultColors\[key_(\w+)\]\s*=\s*)(0x[0-9A-Fa-f]{6,8})(\s*;)")

    changed = [0]

    def sub(m):
        prefix, key, literal, tail = m.group(1), m.group(2), m.group(3), m.group(4)
        if first_run:
            base[key] = literal
        original = base.get(key, literal)
        argb = int(original, 16)
        # 6-digit literals are opaque by convention in this file
        if len(original) <= 8 and (argb >> 24) == 0 and argb != 0:
            argb |= 0xFF000000
        out = K.transform(key, argb, dark=False)
        new_literal = "0x%08X" % (out & 0xFFFFFFFF)
        if new_literal.lower() != literal.lower():
            changed[0] += 1
        return prefix + new_literal + tail

    out = hex_asgn.sub(sub, src)

    # the three shared constants that many keys reference
    consts = {
        "TELEGRAM_COLOR": K.CORE["telegram_color"][0],
        "TELEGRAM_COLOR_TEXT": K.CORE["telegram_color_text"][0],
        "DEFAULT_BLACK_TEXT": K.CORE["windowBackgroundWhiteBlackText"][0],
    }
    for name, value in consts.items():
        out = re.sub(
            r"(public static final int %s\s*=\s*)0x[0-9A-Fa-f]+" % name,
            lambda m, v=value: m.group(1) + "0x%08X" % (v & 0xFFFFFFFF),
            out,
        )

    if not CHECK:
        open(THEME_COLORS, "w", encoding="utf8").write(out)
    print(f"ThemeColors.java: {changed[0]} colour literals repainted (light defaults)")
    return out


# ------------------------------------------------------------------- attheme


def repaint_assets(baseline, to_key):
    for name in sorted(os.listdir(ASSETS)):
        if not name.endswith(".attheme"):
            continue
        path = os.path.join(ASSETS, name)
        dark = name in DARK_ASSETS
        raw = open(path, encoding="utf8", errors="surrogateescape").read()

        store = baseline["assets"].setdefault(name, {})
        first_run = not store

        lines_out = []
        changed = unknown = 0
        for line in raw.split("\n"):
            if "=" not in line or line.startswith(("WLS=", "WPS")):
                lines_out.append(line)
                continue
            sname, _, val = line.partition("=")
            key = to_key.get(sname)
            if key is None:
                unknown += 1
                lines_out.append(line)
                continue
            if first_run:
                store[sname] = val
            original = store.get(sname, val)
            try:
                argb = int(original) & 0xFFFFFFFF
            except ValueError:
                lines_out.append(line)
                continue
            out = K.transform(key, argb, dark=dark) & 0xFFFFFFFF
            signed = out - (1 << 32) if out >= (1 << 31) else out
            if str(signed) != val:
                changed += 1
            lines_out.append(f"{sname}={signed}")

        if not CHECK:
            open(path, "w", encoding="utf8", errors="surrogateescape").write(
                "\n".join(lines_out)
            )
        mode = "dark " if dark else "light"
        print(f"{name:24s} [{mode}] {changed:4d} repainted, {unknown} unmapped keys")


# ---------------------------------------------------------------------- report


def report_contrast():
    """Sanity-check the pairs a user actually reads, in both modes."""
    pairs = [
        ("chat_messageTextIn", "chat_inBubble"),
        ("chat_messageTextOut", "chat_outBubble"),
        ("windowBackgroundWhiteBlackText", "windowBackgroundWhite"),
        ("windowBackgroundWhiteGrayText", "windowBackgroundWhite"),
        ("chats_name", "windowBackgroundWhite"),
        ("chats_message", "windowBackgroundWhite"),
        ("chats_date", "windowBackgroundWhite"),
        ("actionBarDefaultTitle", "actionBarDefault"),
        ("actionBarDefaultSubtitle", "actionBarDefault"),
        ("chats_unreadCounterText", "chats_unreadCounter"),
        ("graySectionText", "graySection"),
        ("chat_messagePanelText", "chat_messagePanelBackground"),
        ("chat_messagePanelHint", "chat_messagePanelBackground"),
        ("glass_tabSelected", "glass_targetMainTabs"),
        ("glass_tabUnselected", "glass_targetMainTabs"),
        ("dialogTextBlack", "dialogBackground"),
        ("avatar_text", "avatar_backgroundBlue"),
        ("chat_inTimeText", "chat_inBubble"),
        ("chat_outTimeText", "chat_outBubble"),
    ]
    print("\ncontrast audit (WCAG ratio, 4.5 body / 3.0 large-or-glyph)")
    worst = []
    for dark in (False, True):
        print("  " + ("dark" if dark else "light"))
        for fg, bg in pairs:
            if fg not in K.CORE or bg not in K.CORE:
                continue
            f = K.CORE[fg][1 if dark else 0]
            b = K.CORE[bg][1 if dark else 0]
            c = K.contrast(f, b)
            tag = "ok" if c >= 4.5 else ("large" if c >= 3.0 else "LOW")
            if c < 3.0:
                worst.append((c, fg, bg, "dark" if dark else "light"))
            print(f"    {c:5.2f}  {tag:5s}  {fg} on {bg}")
    if worst:
        print("\n  below 3.0:")
        for c, fg, bg, mode in worst:
            print(f"    {c:5.2f}  {mode:5s}  {fg} on {bg}")
    else:
        print("\n  no pair below 3.0")


def main():
    to_str, to_key = load_key_names()
    baseline = load_baseline()
    repaint_theme_colors(baseline)
    repaint_assets(baseline, to_key)
    save_baseline(baseline)
    report_contrast()
    if CHECK:
        print("\n(--check: nothing written)")


if __name__ == "__main__":
    main()
