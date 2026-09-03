#!/usr/bin/env python3
"""
KamiGram palette engine.

Turns upstream Telegram's saturated palette into a pastel skeuomorphic one for
both light and dark themes.

Three tiers, in priority order:

1. CORE      Hand-authored hex for the keys that define the look: surfaces,
             bubbles, accent, ink, badges, dividers. Never guessed.
2. FAMILY    Rules for keys that belong to a set with shared semantics
             (avatar_*, name colours, semantic red/green). Hue identity is
             preserved; only saturation and lightness are retuned.
3. TAIL      Conservative fallback for the long tail of rarely-seen keys.
             Preserves hue and alpha, nudges saturation and lightness into the
             mode's band without flattening the colour.

Functional colours are protected: pure white on a coloured chip stays white in
light mode, chart series and brand gradients are untouched, and any text/surface
pair that lands below its contrast floor is nudged until it passes.
"""

import colorsys
import re

# ------------------------------------------------------------------ primitives


def unpack(argb):
    return (argb >> 24) & 0xFF, (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF


def pack(a, r, g, b):
    c = lambda v: max(0, min(255, int(round(v))))
    return (c(a) << 24) | (c(r) << 16) | (c(g) << 8) | c(b)


def to_hls(r, g, b):
    return colorsys.rgb_to_hls(r / 255.0, g / 255.0, b / 255.0)


def from_hls(h, l, s):
    r, g, b = colorsys.hls_to_rgb(h % 1.0, clamp(l), clamp(s))
    return r * 255, g * 255, b * 255


def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


def relative_luminance(r, g, b):
    def ch(v):
        v /= 255.0
        return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4

    return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)


def contrast(c1, c2):
    l1 = relative_luminance(*unpack(c1)[1:])
    l2 = relative_luminance(*unpack(c2)[1:])
    hi, lo = max(l1, l2), min(l1, l2)
    return (hi + 0.05) / (lo + 0.05)


def rehue(h, target, amount):
    """Rotate h toward target by `amount` along the shorter arc."""
    d = (target - h + 0.5) % 1.0 - 0.5
    return (h + d * amount) % 1.0


# --------------------------------------------------------------------- anchors

H_CREAM = 38 / 360.0
H_SKY = 199 / 360.0
H_MINT = 128 / 360.0

# The KamiGram core. Light and dark are authored as a pair so every surface has
# a matching counterpart and no key silently falls through to the generic tail.
CORE = {
    # ---- global surfaces ---------------------------------------------- light / dark
    "windowBackgroundWhite":              (0xFFFAF7F2, 0xFF23201C),
    "windowBackgroundGray":               (0xFFF0EAE0, 0xFF191714),
    "windowBackgroundGrayShadow":         (0xFFDCD3C4, 0xFF100E0C),
    "windowBackgroundUnchecked":          (0xFFB9AE9C, 0xFF5A5348),
    "windowBackgroundChecked":            (0xFF8EC9E8, 0xFF5E93AF),
    "windowBackgroundCheckText":          (0xFFFFFFFF, 0xFF15130F),
    "divider":                            (0xFFE3DACA, 0xFF2E2A25),
    "graySection":                        (0xFFF2EDE4, 0xFF1F1C19),
    "graySectionText":                    (0xFF70695E, 0xFF9A9186),
    "listSelectorSDK21":                  (0x14000000, 0x14FFFFFF),
    "dialogBackground":                   (0xFFFAF7F2, 0xFF23201C),
    "dialogBackgroundGray":               (0xFFF2EDE4, 0xFF1B1815),
    "dialogTextBlack":                    (0xFF4A4A55, 0xFFEDE7DD),
    "dialogTextGray2":                    (0xFF766E62, 0xFFA39A8E),
    "dialogGrayLine":                     (0xFFE3DACA, 0xFF302C27),
    "dialogShadowLine":                   (0x12000000, 0x24000000),
    "dialogCardShadow":                   (0x17000000, 0x2E000000),
    "sheet_scrollUp":                     (0xFFD9CFBE, 0xFF453F37),
    "table_background":                   (0xFFF7F2E9, 0xFF201D19),
    "table_border":                       (0xFFE6DED0, 0xFF322D27),
    # ---- action bar ---------------------------------------------------
    "actionBarDefault":                   (0xFFFAF7F2, 0xFF201D19),
    "actionBarDefaultIcon":               (0xFF4A4A55, 0xFFEDE7DD),
    "actionBarDefaultTitle":              (0xFF4A4A55, 0xFFEDE7DD),
    "actionBarDefaultSubtitle":           (0xFF766E62, 0xFFA39A8E),
    "actionBarDefaultSelector":           (0x14000000, 0x1AFFFFFF),
    "actionBarWhiteSelector":             (0x14000000, 0x1AFFFFFF),
    "actionBarDefaultSearch":             (0xFF4A4A55, 0xFFEDE7DD),
    "actionBarDefaultSearchPlaceholder":  (0xFF8A8172, 0xFF9A9186),
    "actionBarActionModeDefault":         (0xFFFAF7F2, 0xFF201D19),
    "actionBarActionModeDefaultTop":      (0x10000000, 0x28000000),
    "actionBarActionModeDefaultIcon":     (0xFF4A4A55, 0xFFEDE7DD),
    "actionBarBrowser":                   (0xFFFAF7F2, 0xFF201D19),
    # ---- accent -------------------------------------------------------
    "telegram_color":                     (0xFF8EC9E8, 0xFF7FB4D2),
    "telegram_color_text":                (0xFF5E93AF, 0xFF9CCAE4),
    "featuredStickers_addButton":         (0xFF8EC9E8, 0xFF6FA3C0),
    "featuredStickers_addButtonPressed":  (0xFF7FB4D2, 0xFF5E8FA9),
    "featuredStickers_buttonText":        (0xFFFFFFFF, 0xFF14120F),
    "switchTrack":                        (0xFFCFC5B4, 0xFF4A443C),
    "switchTrackChecked":                 (0xFF8EC9E8, 0xFF6FA3C0),
    "switchTrackBlue":                    (0xFFCFC5B4, 0xFF4A443C),
    "switchTrackBlueChecked":             (0xFF8EC9E8, 0xFF6FA3C0),
    "switch2Track":                       (0xFFE49A9A, 0xFFA86A6A),
    "switch2TrackChecked":                (0xFF8EC9E8, 0xFF6FA3C0),
    "radioBackground":                    (0xFFC2B8A6, 0xFF5A5348),
    "radioBackgroundChecked":             (0xFF8EC9E8, 0xFF7FB4D2),
    "checkbox":                           (0xFF8EC9E8, 0xFF6FA3C0),
    "checkboxCheck":                      (0xFFFFFFFF, 0xFF14120F),
    "progressCircle":                     (0xFF8EC9E8, 0xFF7FB4D2),
    "dialogFloatingButton":               (0xFF8EC9E8, 0xFF6FA3C0),
    "dialogFloatingIcon":                 (0xFFFFFFFF, 0xFF14120F),
    "chats_actionBackground":             (0xFF9BC8E4, 0xFF6FA3C0),
    "chats_actionPressedBackground":      (0xFF87B8D6, 0xFF5E8FA9),
    "chats_actionIcon":                   (0xFFFFFFFF, 0xFF14120F),
    # ---- text ---------------------------------------------------------
    "windowBackgroundWhiteBlackText":     (0xFF4A4A55, 0xFFEDE7DD),
    "windowBackgroundWhiteGrayText":      (0xFF766E62, 0xFFA39A8E),
    "windowBackgroundWhiteGrayText2":     (0xFF766D5F, 0xFF968D82),
    "windowBackgroundWhiteHintText":      (0xFF8A8172, 0xFF9A9186),
    "windowBackgroundWhiteValueText":     (0xFF5E93AF, 0xFF9CCAE4),
    "windowBackgroundWhiteLinkText":      (0xFF4785A6, 0xFF9CCAE4),
    "windowBackgroundWhiteBlueText":      (0xFF4785A6, 0xFF9CCAE4),
    "windowBackgroundWhiteRedText":       (0xFFC46A6A, 0xFFE09B9B),
    "windowBackgroundWhiteGreenText":     (0xFF63996B, 0xFF9BC9A2),
    "text_RedRegular":                    (0xFFC46A6A, 0xFFE09B9B),
    "text_RedBold":                       (0xFFB35C5C, 0xFFE8A9A9),
    "fill_RedNormal":                     (0xFFDD8A8A, 0xFFB86E6E),
    # ---- chat list ----------------------------------------------------
    "chats_name":                         (0xFF4A4A55, 0xFFEDE7DD),
    "chats_nameMessage":                  (0xFF5E93AF, 0xFF9CCAE4),
    "chats_message":                      (0xFF766E62, 0xFFA39A8E),
    "chats_date":                         (0xFF8A8172, 0xFF9A9186),
    "chats_unreadCounter":                (0xFF8EC9E8, 0xFF3F6B84),
    "chats_unreadCounterMuted":           (0xFFC9C0B0, 0xFF413B34),
    "chats_unreadCounterText":            (0xFF2E4756, 0xFFEDE7DD),
    "chats_sentCheck":                    (0xFF6FB07A, 0xFF8CC496),
    "chats_sentReadCheck":                (0xFF6FB07A, 0xFF8CC496),
    "chats_sentClock":                    (0xFF8A8172, 0xFF9A9186),
    "chats_sentError":                    (0xFFDD8A8A, 0xFFB86E6E),
    "chats_sentErrorIcon":                (0xFFFFFFFF, 0xFF14120F),
    "chats_menuBackground":               (0xFFFAF7F2, 0xFF23201C),
    "chats_pinnedIcon":                   (0xFFB4AC9E, 0xFF8C8478),
    "chats_pinnedOverlay":                (0x08000000, 0x14FFFFFF),
    "chats_tabletSelectedOverlay":        (0x14000000, 0x1AFFFFFF),
    "chats_tabUnreadActiveBackground":    (0xFF8EC9E8, 0xFF3F6B84),
    "chats_tabUnreadUnactiveBackground":  (0xFFC9C0B0, 0xFF413B34),
    "topics_unreadCounter":               (0xFF8EC9E8, 0xFF3F6B84),
    "topics_unreadCounterMuted":          (0xFFC9C0B0, 0xFF413B34),
    # ---- bubbles ------------------------------------------------------
    "chat_inBubble":                      (0xFFFDFAF4, 0xFF2B2823),
    "chat_inBubbleSelected":              (0xFFF3ECE1, 0xFF3A362F),
    "chat_inBubbleShadow":                (0xFF9D8F7D, 0xFF000000),
    "chat_outBubble":                     (0xFFE3F2DF, 0xFF2F3A2E),
    "chat_outBubbleSelected":             (0xFFD4E8CF, 0xFF3C4A3A),
    "chat_outBubbleShadow":               (0xFF7E8F79, 0xFF000000),
    "chat_messageTextIn":                 (0xFF3D3D46, 0xFFEDE7DD),
    "chat_messageTextOut":                (0xFF2E4231, 0xFFDCE9D9),
    "chat_inTimeText":                     (0xFF8E8576, 0xFF9A9186),
    "chat_outTimeText":                    (0xFF61805F, 0xFF9AB09C),
    "chat_inMediaIcon":                   (0xFFFDFAF4, 0xFF2B2823),
    "chat_outMediaIcon":                  (0xFFE3F2DF, 0xFF2F3A2E),
    "chat_outSentCheck":                  (0xFF57A86B, 0xFF8CC496),
    "chat_outSentCheckRead":              (0xFF57A86B, 0xFF8CC496),
    "chat_wallpaper":                     (0xFFF3EAD9, 0xFF17150F),
    "chat_wallpaper_gradient_to1":        (0xFFE9DCE8, 0xFF1B1720),
    "chat_serviceText":                   (0xFFFFFFFF, 0xFFEDE7DD),
    "chat_serviceIcon":                   (0xFFFFFFFF, 0xFFEDE7DD),
    # ---- composer -----------------------------------------------------
    "chat_messagePanelBackground":        (0xFFFDFAF4, 0xFF201D19),
    "chat_messagePanelText":              (0xFF3D3D46, 0xFFEDE7DD),
    "chat_messagePanelHint":              (0xFF8A8172, 0xFF9A9186),
    "chat_messagePanelCursor":            (0xFF8EC9E8, 0xFF7FB4D2),
    "chat_messagePanelIcons":             (0xFF9A9182, 0xFF9A9186),
    "chat_messagePanelSend":              (0xFF8EC9E8, 0xFF7FB4D2),
    "chat_messagePanelVoicePressed":      (0xFFFFFFFF, 0xFF14120F),
    "chat_messagePanelVoiceBackground":   (0xFF8EC9E8, 0xFF6FA3C0),
    "chat_emojiPanelBackground":          (0xFFF5EFE4, 0xFF1D1A17),
    "chat_emojiSearchBackground":         (0xFFEAE2D4, 0xFF2A2622),
    "chat_emojiPanelIcon":                (0xFF9A9182, 0xFF8C8478),
    "chat_emojiPanelIconSelected":        (0xFF5E93AF, 0xFF9CCAE4),
    "chat_emojiPanelStickerPackSelector": (0xFFE8E0D2, 0xFF2A2622),
    "chat_topPanelBackground":            (0xFFFDFAF4, 0xFF201D19),
    "chat_replyPanelLine":                (0xFFE6DED0, 0xFF322D27),
    "chat_goDownButton":                  (0xFFFDFAF4, 0xFF2B2823),
    "chat_goDownButtonIcon":              (0xFF6E665A, 0xFFC9C1B5),
    "chat_goDownButtonCounterBackground": (0xFF8EC9E8, 0xFF3F6B84),
    # ---- bottom nav (glass package, restyled as raised plate) ---------
    "glass_targetMainTabs":               (0xFFFDFAF4, 0xFF262220),
    "glass_targetMainTopPanel":           (0xFFFDFAF4, 0xFF262220),
    "glass_tabSelected":                  (0xFF5E93AF, 0xFF9CCAE4),
    "glass_tabSelectedText":              (0xFF4A7B95, 0xFFB4D6E9),
    "glass_tabUnselected":                (0xFF7E7566, 0xFF9A9186),
    "glass_defaultIcon":                  (0xCC6E665A, 0xCCC9C1B5),
    "glass_defaultText":                  (0xCC6E665A, 0xCCC9C1B5),
}

# The seven-colour avatar wheel, hue identity preserved so users still recognise
# "the green one" while it sits quietly next to cream surfaces.
#
# Initials are ink, not white: dark ink on the light-mode wheel, cream ink on the
# dark-mode wheel. White-on-pastel measured 1.73 and was unreadable, and turning
# the wheel dark enough for white would have cost the pastel identity. The
# dark-mode wheel is held near L=0.31 so cream initials clear 4.5 on every hue.
#
# Layout: (light_fill, light_gradient2), (dark_fill, dark_gradient2)
AVATAR_WHEEL = {
    "Red":    ((0xFFE9A79B, 0xFFD98C7E), (0xFF674237, 0xFF53342B)),
    "Orange": ((0xFFF0C89A, 0xFFE0AC76), (0xFF6B5133, 0xFF56402A)),
    "Violet": ((0xFFC8B6EC, 0xFFA694DC), (0xFF463B63, 0xFF382F4F)),
    "Green":  ((0xFFB4D9A4, 0xFF94C489), (0xFF465F3F, 0xFF384C33)),
    "Cyan":   ((0xFF9FD3E2, 0xFF7FB8CE), (0xFF3B5863, 0xFF2F464F)),
    "Blue":   ((0xFFA6C9EA, 0xFF85AEDA), (0xFF3C5262, 0xFF30424F)),
    "Pink":   ((0xFFEFB2C6, 0xFFDC93AC), (0xFF643A48, 0xFF502E39)),
}

AVATAR_INK = (0xFF2B3640, 0xFFEDE7DD)


def _avatar_core():
    out = {}
    for name, ((l1, l2), (d1, d2)) in AVATAR_WHEEL.items():
        out["avatar_background" + name] = (l1, d1)
        out["avatar_background2" + name] = (l2, d2)
        # name-in-message is ink on a bubble, not a fill: darken for light,
        # lift for dark, keep the hue so the sender stays identifiable
        h, l, s = to_hls(*unpack(l2)[1:])
        out["avatar_nameInMessage" + name] = (
            pack(0xFF, *from_hls(h, 0.38, min(0.42, s))),
            pack(0xFF, *from_hls(h, 0.76, min(0.40, s))),
        )
    out["avatar_backgroundSaved"] = (0xFF9FD3E2, 0xFF3B5863)
    out["avatar_background2Saved"] = (0xFF7FB8CE, 0xFF2F464F)
    out["avatar_backgroundArchived"] = (0xFFCFC5B4, 0xFF44403A)
    out["avatar_backgroundArchivedHidden"] = (0xFF9FD3E2, 0xFF3B5863)
    out["avatar_backgroundGray"] = (0xFFC2B8A6, 0xFF44403A)
    out["avatar_backgroundInProfileBlue"] = (0xFFFAF7F2, 0xFF23201C)
    out["avatar_backgroundActionBarBlue"] = (0xFFF6F1E8, 0xFF201D19)
    out["avatar_subtitleInProfileBlue"] = (0xFF766E62, 0xFFA39A8E)
    out["avatar_actionBarIconBlue"] = (0xFF4A4A55, 0xFFEDE7DD)
    out["avatar_actionBarSelectorBlue"] = (0x14000000, 0x1AFFFFFF)
    out["avatar_text"] = AVATAR_INK
    return out


CORE.update(_avatar_core())


# ------------------------------------------------------------------- protected

# Geometry, alpha carriers, chart series and brand gradients: pass through.
PROTECTED = re.compile(
    r"wallpaperFileOffset|_gradient_rotation|BlurAlpha|"
    r"statisticChart|^color_|^premium|Gradient\d$|"
    r"^voipgroup_|^chat_BlurAlpha$",
    re.I,
)

# Keys whose colour is functional rather than decorative: pure white/black on a
# coloured chip. Left untouched in light mode.
FUNCTIONAL_WHITE = re.compile(
    r"(CounterText|checkboxCheck|CheckText|_addButtonText|buttonText|"
    r"FloatingIcon|actionIcon|ErrorIcon|VoicePressed|serviceText|serviceIcon|"
    r"avatar_text|mediaInfoText|photoTitle|_text$)$",
    re.I,
)


# ------------------------------------------------------------------ family rules

MODIFIERS = re.compile(
    r"(In|Out|Selected|Unselected|Pressed|Checked|Unchecked|Disabled|Active|"
    r"Unactive|Inactive|Enabled|Hidden|Archived|Old|Muted|Local|Cats|Dark|"
    r"Night|Day|Highlighted|Focused|\d+)+$"
)


def semantic_tail(key):
    prev, out = None, key
    while out != prev:
        prev, out = out, MODIFIERS.sub("", out)
    return out or key


ROLE_RULES = [
    ("media", re.compile(
        r"^chat_media|mediaInfoText|^iv_|serviceText|serviceIcon|serviceLink|"
        r"photoTitle|photoStat|Highlight$", re.I)),
    ("link", re.compile(
        r"Link$|TextLink$|messageLink|BlueText$|BlueHeader$|blueText|"
        r"^dialogTextBlue|fieldOverlayText|LinkSelection$", re.I)),
    ("ink", re.compile(
        r"(Text|Title|Subtitle|Name|Hint|Icon|Cursor|Value|Placeholder|"
        r"Check|Letter|Initials|Time|Date|Status|Counter|Arrow|Dot|Mark|"
        r"Caption|Label|Message)$", re.I)),
    ("surface_alt", re.compile(
        r"graySection|Section$|BackgroundGray$|windowBackgroundGray|"
        r"searchBackground|emojiPanel|panelBackground|inputField|"
        r"stickerPackSelector|[Dd]ivider|GrayLine$|Line$|Shadow$|"
        r"Separator$|Border$|Track$|Progress$", re.I)),
    ("surface", re.compile(
        r"[Bb]ackground|[Bb]ubble|wallpaper|actionBar|[Ff]ill|"
        r"[Ss]heet|^table_|Selector$|Overlay$|Circle$|Plate$|Ribbon$", re.I)),
    ("accent", re.compile(r".", re.I)),
]


def role_of(key):
    tail = semantic_tail(key)
    for role, rx in ROLE_RULES:
        if rx.search(tail) or rx.search(key):
            return role
    return "accent"


# ------------------------------------------------------------------------ tail
#
# Conservative: hue is preserved (only greys get an anchor hue), saturation is
# compressed toward the pastel band, lightness is mapped into the mode's band
# while keeping the colour's own ordering. Alpha is always preserved.

BANDS = {
    # role          light (lo, hi, sat_max)   dark (lo, hi, sat_max)
    "surface":     ((0.88, 0.98, 0.18), (0.11, 0.24, 0.16)),
    "surface_alt": ((0.83, 0.94, 0.20), (0.14, 0.28, 0.18)),
    "accent":      ((0.62, 0.82, 0.44), (0.54, 0.74, 0.40)),
    "link":        ((0.42, 0.60, 0.52), (0.60, 0.78, 0.48)),
    "ink":         ((0.26, 0.58, 0.32), (0.62, 0.92, 0.28)),
    "media":       ((0.72, 0.99, 0.14), (0.72, 0.99, 0.14)),
}


def tail_transform(key, argb, dark):
    a, r, g, b = unpack(argb)
    role = role_of(key)
    lo, hi, smax = BANDS[role][1 if dark else 0]
    h, l, s = to_hls(r, g, b)

    if s < 0.10:  # grey: give it the mode's anchor warmth
        h = H_CREAM if role in ("surface", "surface_alt", "ink") else H_SKY
        s = 0.05 if role in ("surface", "surface_alt") else 0.22
    else:
        s = min(smax, s * 0.62 + 0.04)

    # map the source lightness into the band, preserving relative ordering
    l = lo + l * (hi - lo)
    return pack(a, *from_hls(h, l, s))


# -------------------------------------------------------------------- entry point


def transform(key, argb, dark):
    """Map one upstream ARGB to its KamiGram equivalent."""
    if PROTECTED.search(key):
        return argb

    hit = CORE.get(key)
    if hit is not None:
        return hit[1] if dark else hit[0]

    a, r, g, b = unpack(argb)

    # functional white/black on a coloured chip
    if FUNCTIONAL_WHITE.search(key):
        h, l, s = to_hls(r, g, b)
        if l > 0.92 and s < 0.12:
            return argb if not dark else pack(a, 0x17, 0x15, 0x0F)

    # fully transparent or alpha-only scrims: keep as-is
    if a == 0:
        return argb

    return tail_transform(key, argb, dark)


def ensure_contrast(fg, bg, minimum, dark):
    """Nudge fg lightness until it clears `minimum` against bg."""
    if contrast(fg, bg) >= minimum:
        return fg
    a, r, g, b = unpack(fg)
    h, l, s = to_hls(r, g, b)
    step = 0.02 if dark else -0.02
    for _ in range(50):
        l = clamp(l + step)
        cand = pack(a, *from_hls(h, l, s))
        if contrast(cand, bg) >= minimum:
            return cand
        if l in (0.0, 1.0):
            break
    return pack(a, *from_hls(h, 1.0 if dark else 0.0, s))
