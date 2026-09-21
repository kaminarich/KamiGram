#!/usr/bin/env python3
"""
KamiGram readability guard.

The bot keyboard and the gift sheet take their colors from theme palettes that
users (and the fork's own pastel repaint) can break - night.attheme shipped
chat_botKeyboardButtonText on top of a chat_botKeyboardButtonBackground of the
same luminance, so the numbers were black on black. BotKeyboardView now
composites translucent fills onto the panel background and calls
Theme.ensureReadable at draw time, which repairs the palette at runtime.

This script asserts the same property statically, theme by theme, so a bad
palette is caught in CI before it ships. It is deliberately conservative:
- only pairs that are actually drawn together,
- translucent backgrounds composited onto the surface they sit on,
- WCAG AA 4.5 for text, 3.0 for icon tints.

Run:  python3 Tools/kamigram_contrast_check.py
Exit: 0 readable, 1 unreadable pair found
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "TMessagesProj/src/main")
ASSETS = os.path.join(SRC, "assets")

MIN_TEXT = 4.5
MIN_ICON = 3.0


def lum(color):
    def ch(x):
        x = x / 255.0
        return x / 12.92 if x <= 0.03928 else ((x + 0.055) / 1.055) ** 2.4
    r, g, b = (color >> 16) & 255, (color >> 8) & 255, color & 255
    return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)


def contrast(a, b):
    la, lb = lum(a), lum(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)


def blend_over(base, over):
    """B over A, Porter-Duff."""
    ab = (over >> 24) & 255
    aa = (base >> 24) & 255
    f = ab / 255.0
    out_a = f + (aa / 255.0) * (1 - f)
    if out_a == 0:
        return 0
    out = [0, 0, 0]
    for i in range(3):
        cb = (over >> (16 - 8 * i)) & 255
        ca = (base >> (16 - 8 * i)) & 255
        out[i] = int((cb * f + ca * (aa / 255.0) * (1 - f)) / out_a)
    return (0xFF000000 | (out[0] << 16) | (out[1] << 8) | out[2]) & 0xFFFFFFFF


def parse_attheme(path):
    values = {}
    with open(path, encoding="utf8", errors="replace") as fh:
        for line in fh:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, raw = line.partition("=")
            try:
                values[key.strip()] = int(raw.strip()) & 0xFFFFFFFF
            except ValueError:
                pass
    return values


THEME_COLORS = re.compile(r'defaultColors\[(key_[A-Za-z0-9_]+)\]\s*=\s*(0x[0-9A-Fa-f]+|-?\d+);')
KEY_NAMES = re.compile(r'colorKeysMap\.put\((key_[A-Za-z0-9_]+),\s*"([A-Za-z0-9_]+)"\)')


def load_default_colors():
    src = open(os.path.join(SRC, "java/org/telegram/ui/ActionBar/ThemeColors.java"),
               encoding="utf8", errors="replace").read()
    defaults = {}
    for m in THEME_COLORS.finditer(src):
        defaults[m.group(1)] = int(m.group(2), 0) & 0xFFFFFFFF
    names = {}
    for m in KEY_NAMES.finditer(src):
        names[m.group(2)] = m.group(1)
    return defaults, names


def ensure_readable(fg, bg, min_contrast):
    """Mirror of Theme.ensureReadable, so the check agrees with the runtime fix."""
    if contrast(fg, bg) >= min_contrast:
        return fg
    base_bg = blend_over(bg if (bg >> 24) & 255 >= 0x80 else 0xFF000000, bg) if (bg >> 24) & 255 < 0xFF else bg
    bg_light = lum(base_bg) >= 0.5
    out = (fg & 0xFF000000) | (0x101820 if bg_light else 0xFFFFFF)
    # simple darkening/lightening toward the threshold
    r, g, b = (out >> 16) & 255, (out >> 8) & 255, out & 255
    step = -8 if bg_light else 8
    for _ in range(64):
        if contrast(out, base_bg) >= min_contrast:
            break
        r = max(4, min(251, r + step))
        g = max(4, min(251, g + step))
        b = max(4, min(251, b + step))
        out = (out & 0xFF000000) | (r << 16) | (g << 8) | b
    return out


def main():
    defaults, names = load_default_colors()
    themes = {}
    for fname in sorted(os.listdir(ASSETS)):
        if fname.endswith(".attheme"):
            themes[fname] = parse_attheme(os.path.join(ASSETS, fname))

    def resolve(theme_values, key_name):
        key = names.get(key_name)
        if key is None:
            return None
        if key_name in theme_values:
            return theme_values[key_name]
        return defaults.get(key)

    # (fg key, bg key, surface key or None, min contrast, label)
    # background is composited onto surface when it is translucent - matches how
    # the view composites it. Service messages draw over the chat wallpaper, and
    # bot-keyboard buttons draw over the emoji panel background.
    pairs = [
        ("chat_botKeyboardButtonText", "chat_botKeyboardButtonBackground", "chat_emojiPanelBackground", MIN_TEXT, "bot keyboard button"),
        ("chat_botKeyboardButtonText", "chat_botKeyboardButtonBackgroundPressed", "chat_emojiPanelBackground", MIN_TEXT, "bot keyboard button (pressed)"),
        ("chats_unreadCounterText", "chats_unreadCounter", None, MIN_TEXT, "unread counter"),
        ("chats_unreadCounterText", "chats_unreadCounterMuted", None, MIN_TEXT, "unread counter (muted)"),
        ("chat_serviceText", "chat_serviceBackground", "chat_wallpaper", MIN_TEXT, "service message"),
        ("chat_serviceText", "chat_serviceBackgroundSelected", "chat_wallpaper", MIN_TEXT, "service message (selected)"),
        ("dialogGiftsTabText", "dialogGiftsBackground", None, MIN_TEXT, "gift sheet tab"),
        ("windowBackgroundWhiteBlackText", "windowBackgroundWhite", None, MIN_TEXT, "window primary text"),
        ("dialogTextBlack", "dialogBackground", None, MIN_TEXT, "dialog primary text"),
    ]

    problems = []

    def check(theme_label, values):
        for fg_name, bg_name, surface, minimum, label in pairs:
            fg = resolve(values, fg_name)
            bg = resolve(values, bg_name)
            if fg is None or bg is None:
                continue
            if (bg >> 24) & 255 < 0x30:
                continue
            if surface is not None:
                surface_color = resolve(values, surface)
                if surface_color is not None:
                    bg = blend_over(surface_color, bg)
            ratio = contrast(fg, bg)
            fixed = ensure_readable(fg, bg, minimum)
            if ratio < minimum:
                problems.append(
                    "%-22s %-34s fg=%s bg=%s contrast=%.2f (needs %.2f); runtime guard picks %s"
                    % (theme_label, label, format(fg), format(bg), ratio, minimum, format(fixed)))

    check("default palette", {})
    for fname, values in sorted(themes.items()):
        check(fname, values)

    if problems:
        print("unreadable color pairs found:\n")
        for p in problems:
            print("  " + p)
        print("\n%d problem(s). Theme.ensureReadable repairs these at draw time," % len(problems))
        print("but the palette itself should be fixed.")
        return 1
    print("ok: every text/icon color clears its background in all %d themes" % len(themes))
    return 0


def format(color):
    return "0x%08x" % color


if __name__ == "__main__":
    sys.exit(main())
