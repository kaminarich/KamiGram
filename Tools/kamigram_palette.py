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

# KamiGram anchors. No brown, no cream: light mode is white + pastel blue with
# orange reserved for alerts/mentions; dark mode is cool slate, never warm.
H_SKY = 203 / 360.0      # pastel blue, the primary accent family
H_SLATE = 212 / 360.0    # dark-mode surface hue
H_ORANGE = 28 / 360.0    # warm accent, used sparingly

# The KamiGram core. Light and dark are authored as a pair so every surface has
# a matching counterpart and no key silently falls through to the generic tail.
CORE = {
    # ================================================================ SURFACES
    # Light: white page, pastel-blue tinted cards, no cream or beige anywhere.
    # Dark:  cool slate, never warm. Each tier is separated enough to read as a
    #        distinct physical layer under the bevel treatment.
    #                                       light        dark
    "windowBackgroundWhite":              (0xFFE9EFF7, 0xFF2B3138),
    "windowBackgroundGray":               (0xFFDFE7F1, 0xFF1E2227),
    "windowBackgroundGrayShadow":         (0xFFC4D0DE, 0xFF13161A),
    "windowBackgroundUnchecked":          (0xFFAAB7C4, 0xFF4B555F),
    "windowBackgroundChecked":            (0xFF4589AD, 0xFF3A6B85),
    "windowBackgroundCheckText":          (0xFFFFFFFF, 0xFFFFFFFF),
    "divider":                            (0xFFDCE5EE, 0xFF2E343A),
    "graySection":                        (0xFFDFE7F1, 0xFF23282E),
    "graySectionText":                    (0xFF5F6E7D, 0xFFA3AFBB),
    "listSelectorSDK21":                  (0x14000000, 0x14FFFFFF),
    "dialogBackground":                   (0xFFE9EFF7, 0xFF2B3138),
    "dialogBackgroundGray":               (0xFFDFE7F1, 0xFF23282E),
    "dialogTextBlack":                    (0xFF2C3742, 0xFFE6EBF0),
    "dialogTextGray2":                    (0xFF5F6E7D, 0xFFA3AFBB),
    "dialogGrayLine":                     (0xFFDCE5EE, 0xFF2E343A),
    "dialogShadowLine":                   (0x14000000, 0x33000000),
    "dialogCardShadow":                   (0x1F000000, 0x40000000),
    "sheet_scrollUp":                     (0xFFC9D6E2, 0xFF454E57),
    "table_background":                   (0xFFEDF2F9, 0xFF2F353C),
    "table_border":                       (0xFFDCE5EE, 0xFF2E343A),

    # ============================================================== ACTION BAR
    "actionBarDefault":                   (0xFFE9EFF7, 0xFF262B31),
    "actionBarDefaultIcon":               (0xFF2C3742, 0xFFE6EBF0),
    "actionBarDefaultTitle":              (0xFF2C3742, 0xFFE6EBF0),
    "actionBarDefaultSubtitle":           (0xFF5F6E7D, 0xFFA3AFBB),
    "actionBarDefaultSelector":           (0x14000000, 0x1AFFFFFF),
    "actionBarWhiteSelector":             (0x14000000, 0x1AFFFFFF),
    "actionBarDefaultSearch":             (0xFF2C3742, 0xFFE6EBF0),
    "actionBarDefaultSearchPlaceholder":  (0xFF6F7B89, 0xFF8A96A2),
    "actionBarActionModeDefault":         (0xFFE9EFF7, 0xFF262B31),
    "actionBarActionModeDefaultTop":      (0x14000000, 0x33000000),
    "actionBarActionModeDefaultIcon":     (0xFF2C3742, 0xFFE6EBF0),
    "actionBarBrowser":                   (0xFFE9EFF7, 0xFF262B31),
    "actionBarDefaultSubmenuBackground":  (0xFFEDF2F9, 0xFF2F353C),
    "actionBarDefaultSubmenuItem":        (0xFF2C3742, 0xFFE6EBF0),
    "actionBarDefaultSubmenuItemIcon":    (0xFF4A5A69, 0xFFA3AFBB),

    # ================================================================== ACCENT
    # One accent family: pastel blue. Fills that carry white glyphs are darker
    # than the tints used for large areas, so white always clears 4.5.
    "telegram_color":                     (0xFF3F7A9B, 0xFF7FBBDD),
    "telegram_color_text":                (0xFF2F6B8A, 0xFF8CC6E8),
    "featuredStickers_addButton":         (0xFF3F7A9B, 0xFF3A6B85),
    "featuredStickers_addButtonPressed":  (0xFF39708F, 0xFF35617A),
    "featuredStickers_buttonText":        (0xFFFFFFFF, 0xFFFFFFFF),
    "switchTrack":                        (0xFFD3DEEB, 0xFF23282E),
    "switchTrackChecked":                 (0xFF9DC6E6, 0xFF3C5C74),
    "switchTrackBlue":                    (0xFFD3DEEB, 0xFF23282E),
    "switchTrackBlueChecked":             (0xFF9DC6E6, 0xFF3C5C74),
    "switch2Track":                       (0xFFE0A79C, 0xFF6B4A44),
    "switch2TrackChecked":                (0xFF9DC6E6, 0xFF3C5C74),
    "radioBackground":                    (0xFFBCC9D8, 0xFF4B555F),
    "radioBackgroundChecked":             (0xFF3F7A9B, 0xFF7FBBDD),
    "checkbox":                           (0xFF3F7A9B, 0xFF3A6B85),
    "checkboxCheck":                      (0xFFFFFFFF, 0xFFFFFFFF),
    "progressCircle":                     (0xFF3F7A9B, 0xFF7FBBDD),
    "dialogFloatingButton":               (0xFF3F7A9B, 0xFF3A6B85),
    "dialogFloatingIcon":                 (0xFFFFFFFF, 0xFFFFFFFF),
    "chats_actionBackground":             (0xFF3F7A9B, 0xFF3A6B85),
    "chats_actionPressedBackground":      (0xFF39708F, 0xFF35617A),
    "chats_actionIcon":                   (0xFFFFFFFF, 0xFFFFFFFF),

    # ==================================================================== TEXT
    "windowBackgroundWhiteBlackText":     (0xFF2C3742, 0xFFE6EBF0),
    "windowBackgroundWhiteGrayText":      (0xFF5F6E7D, 0xFFA3AFBB),
    "windowBackgroundWhiteGrayText2":     (0xFF667585, 0xFF97A3AF),
    "windowBackgroundWhiteHintText":      (0xFF6F7B89, 0xFF8A96A2),
    "windowBackgroundWhiteValueText":     (0xFF2F6B8A, 0xFF8CC6E8),
    "windowBackgroundWhiteLinkText":      (0xFF2F6B8A, 0xFF8CC6E8),
    "windowBackgroundWhiteBlueText":      (0xFF2F6B8A, 0xFF8CC6E8),
    "windowBackgroundWhiteRedText":       (0xFFB4483F, 0xFFE49189),
    "windowBackgroundWhiteGreenText":     (0xFF3F7A50, 0xFF8CC49B),
    "text_RedRegular":                    (0xFFB4483F, 0xFFE49189),
    "text_RedBold":                       (0xFFA33F37, 0xFFEC9F97),
    "fill_RedNormal":                     (0xFFB4483F, 0xFF8E4E46),

    # =============================================================== CHAT LIST
    "chats_name":                         (0xFF2C3742, 0xFFE6EBF0),
    "chats_nameMessage":                  (0xFF2F6B8A, 0xFF8CC6E8),
    "chats_message":                      (0xFF5F6E7D, 0xFFA3AFBB),
    "chats_date":                         (0xFF6F7B89, 0xFF8A96A2),
    # Badge is a saturated fill with WHITE text: no ambiguity against the
    # bubble beside it, and clearly distinct from the muted/mention variants.
    "chats_unreadCounter":                (0xFF3F7A9B, 0xFF3A6B85),
    "chats_unreadCounterMuted":           (0xFF6E7C8A, 0xFF3E464E),
    "chats_unreadCounterText":            (0xFFFFFFFF, 0xFFFFFFFF),
    "chats_sentCheck":                    (0xFF3F7A50, 0xFF7FC08E),
    "chats_sentReadCheck":                (0xFF3F7A50, 0xFF7FC08E),
    "chats_sentClock":                    (0xFF6F7B89, 0xFF8A96A2),
    "chats_sentError":                    (0xFFB4483F, 0xFF8E4E46),
    "chats_sentErrorIcon":                (0xFFFFFFFF, 0xFFFFFFFF),
    "chats_menuBackground":               (0xFFE9EFF7, 0xFF2B3138),
    "chats_pinnedIcon":                   (0xFF8A95A2, 0xFF8A96A2),
    "chats_pinnedOverlay":                (0x0A000000, 0x14FFFFFF),
    "chats_tabletSelectedOverlay":        (0x14000000, 0x1AFFFFFF),
    "chats_tabUnreadActiveBackground":    (0xFF3F7A9B, 0xFF3A6B85),
    "chats_tabUnreadUnactiveBackground":  (0xFF6E7C8A, 0xFF3E464E),
    "topics_unreadCounter":               (0xFF3F7A9B, 0xFF3A6B85),
    "topics_unreadCounterMuted":          (0xFF6E7C8A, 0xFF3E464E),
    # mention/reaction pills use the orange accent so they never read as the
    # unread counter at a glance
    "chats_mentionIcon":                  (0xFFFFFFFF, 0xFFFFFFFF),
    "chats_archiveBackground":            (0xFF3F7A9B, 0xFF3A6B85),
    "chats_archivePinBackground":         (0xFF6E7C8A, 0xFF3E464E),
    "chats_archiveIcon":                  (0xFFFFFFFF, 0xFFFFFFFF),
    "chats_archiveText":                  (0xFFFFFFFF, 0xFFFFFFFF),

    # ================================================================= BUBBLES
    # In-bubble is white, out-bubble is a pastel blue tint: same family as the
    # accent, clearly lighter than any badge fill.
    "chat_inBubble":                      (0xFFEDF2F9, 0xFF2F353C),
    "chat_inBubbleSelected":              (0xFFDCE5F0, 0xFF3A424B),
    "chat_inBubbleShadow":                (0xFF8296AE, 0xFF000000),
    "chat_outBubble":                     (0xFFC9DEF2, 0xFF33414F),
    "chat_outBubbleSelected":             (0xFFB8D4EC, 0xFF3D4E5E),
    "chat_outBubbleShadow":               (0xFF6C87A4, 0xFF000000),
    "chat_messageTextIn":                 (0xFF2C3742, 0xFFE6EBF0),
    "chat_messageTextOut":                (0xFF23323D, 0xFFE6EBF0),
    "chat_inTimeText":                     (0xFF6F7B89, 0xFF8A96A2),
    "chat_outTimeText":                    (0xFF546878, 0xFF9BAAB8),
    "chat_inMediaIcon":                   (0xFFFFFFFF, 0xFF262B31),
    "chat_outMediaIcon":                  (0xFFD7EAF7, 0xFF2C3A45),
    "chat_outSentCheck":                  (0xFF3F7A50, 0xFF7FC08E),
    "chat_outSentCheckRead":              (0xFF3F7A50, 0xFF7FC08E),
    "chat_outSentCheckSelected":          (0xFF376B46, 0xFF8CC49B),
    # wallpaper: pale blue wash, no cream
    "chat_wallpaper":                     (0xFFDCE5F0, 0xFF1A1E23),
    "chat_wallpaper_gradient_to1":        (0xFFE4EBF5, 0xFF20252B),
    "chat_serviceText":                   (0xFFFFFFFF, 0xFFE6EBF0),
    "chat_serviceIcon":                   (0xFFFFFFFF, 0xFFE6EBF0),

    # ================================================================ COMPOSER
    "chat_messagePanelBackground":        (0xFFE9EFF7, 0xFF262B31),
    "chat_messagePanelText":              (0xFF2C3742, 0xFFE6EBF0),
    "chat_messagePanelHint":              (0xFF6F7B89, 0xFF8A96A2),
    "chat_messagePanelCursor":            (0xFF3F7A9B, 0xFF7FBBDD),
    "chat_messagePanelIcons":             (0xFF667585, 0xFF97A3AF),
    "chat_messagePanelSend":              (0xFF3F7A9B, 0xFF7FBBDD),
    "chat_messagePanelVoicePressed":      (0xFFFFFFFF, 0xFFFFFFFF),
    "chat_messagePanelVoiceBackground":   (0xFF3F7A9B, 0xFF3A6B85),
    "chat_emojiPanelBackground":          (0xFFE4EBF5, 0xFF23282E),
    "chat_emojiSearchBackground":         (0xFFD8E2EE, 0xFF1E2227),
    "chat_emojiPanelIcon":                (0xFF667585, 0xFF8A96A2),
    "chat_emojiPanelIconSelected":        (0xFF2F6B8A, 0xFF8CC6E8),
    "chat_emojiPanelStickerPackSelector": (0xFFD8E2EE, 0xFF1E2227),
    "chat_topPanelBackground":            (0xFFE9EFF7, 0xFF262B31),
    "chat_topPanelTitle":                 (0xFF2F6B8A, 0xFF8CC6E8),
    "chat_replyPanelLine":                (0xFFDCE5EE, 0xFF2E343A),
    "chat_goDownButton":                  (0xFFEDF2F9, 0xFF2F353C),
    "chat_goDownButtonIcon":              (0xFF4A5A69, 0xFFA3AFBB),
    "chat_goDownButtonCounterBackground": (0xFF3F7A9B, 0xFF3A6B85),

    # ============================================ bottom nav / floating plates
    "glass_targetMainTabs":               (0xFFEDF2F9, 0xFF2F353C),
    "glass_targetMainTopPanel":           (0xFFEDF2F9, 0xFF2F353C),
    "glass_tabSelected":                  (0xFF2F6B8A, 0xFF8CC6E8),
    "glass_tabSelectedText":              (0xFF2C6280, 0xFF9FD1EC),
    "glass_tabUnselected":                (0xFF6F7B89, 0xFF8A96A2),
    "glass_defaultIcon":                  (0xCC4A5A69, 0xCCA3AFBB),
    "glass_defaultText":                  (0xCC4A5A69, 0xCCA3AFBB),

    # ================================================================== ORANGE
    # Reserved for warm signals so they never collide with the blue accent.
    "chat_attachContactText":             (0xFFB86518, 0xFFF0A961),
    "chat_replyPanelName":                (0xFF2F6B8A, 0xFF8CC6E8),
    "undo_background":                    (0xFF2C3742, 0xFF313941),
    "undo_cancelColor":                   (0xFF8CC6E8, 0xFF8CC6E8),
    "undo_infoColor":                     (0xFFFFFFFF, 0xFFE6EBF0),
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
# Seven-hue avatar wheel. Each keeps its hue identity (users recognise "the
# green one") but all are now clean pastels that sit on white or slate without
# muddiness. Red and orange are kept bright and peachy, never tan or brown.
# Dark-mode fills are desaturated further and held near L=0.28 so cream initials
# clear 4.5 on every hue.
AVATAR_WHEEL = {
    "Red":    ((0xFFF5B3B3, 0xFFE89B9B), (0xFF6B4A4A, 0xFF573B3B)),
    "Orange": ((0xFFFFD1A3, 0xFFFFC68A), (0xFF6B5338, 0xFF57422D)),
    "Violet": ((0xFFD4C6F0, 0xFFBEADE6), (0xFF4F436B, 0xFF3F3656)),
    "Green":  ((0xFFB8E5B0, 0xFFA3D999), (0xFF476B3F, 0xFF395733)),
    "Cyan":   ((0xFFAFD9E8, 0xFF96CDE0), (0xFF3F5F6B, 0xFF334D57)),
    "Blue":   ((0xFFB3CEF0, 0xFF9EBFE8), (0xFF3F536B, 0xFF334356)),
    "Pink":   ((0xFFF5C6D9, 0xFFE8ADCB), (0xFF6B435A, 0xFF573648)),
}



# ---- selection & quote visibility ---------------------------------------------
# Upstream's in-bubble highlight was near-white at 31% alpha: mathematically
# invisible on a light bubble (1.05:1 against it). The first fix raised alpha but
# kept a pale pastel, which still only measured 1.15:1 - a colour swap, not a
# highlighter.
#
# These values are solved rather than picked. A highlighter has two hard
# requirements that pull against each other:
#   1. the stroke must be visible against the bubble  -> composite vs bubble >= 1.75
#   2. the text under it must stay readable           -> ink on composite >= 4.6
# Alpha is capped at 65% so the stroke stays genuinely translucent (the text and
# any emoji beneath it still show through, as with a real marker) instead of
# becoming an opaque bar. Verified per bubble, per mode:
#
#   light in   composite #69AE81  vs bubble 2.34  text 4.60
#   light out  composite #5CA78A  vs bubble 2.07  text 4.61
#   dark  in   composite #3A7359  vs bubble 2.23  text 4.64
#   dark  out  composite #317553  vs bubble 1.89  text 4.61
CORE.update({
    "chat_inTextSelectionHighlight":  (0xA6228A41, 0xA6409568),
    "chat_outTextSelectionHighlight": (0xA6228A53, 0xA6309155),
    # handles/cursor: deeper in light mode, brighter in dark, so the grab points
    # read against the highlight itself rather than blending into it
    "chat_TextSelectionCursor":       (0xFF1F7A3C, 0xFF56C47F),
    "chat_outTextSelectionCursor":    (0xFF1F7A3C, 0xFF56C47F),
    # composer selection sits on the panel, not a bubble
    "chat_textSelectBackground":      (0x99228A41, 0x99409568),
})

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

# ---- explicit de-muddying -----------------------------------------------------
# These five upstream keys land in the beige/tan wedge. Archived and gray avatars
# become cool slate; the orange name and code token keep hue but push saturation
# high enough to read as orange rather than brown; the sticker hint panel becomes
# a pale blue surface.
CORE.update({
    "avatar_backgroundArchived":  (0xFFAEBCC9, 0xFF3E464E),
    "avatar_backgroundGray":      (0xFFA3B2C0, 0xFF3E464E),
    "avatar_nameInMessageOrange": (0xFFB86518, 0xFFF0A961),
    "chat_stickersHintPanel":     (0xFFEDF2F9, 0xFF2F353C),
    "code_function":              (0xFF2F6B8A, 0xFF8CC6E8),
})



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
# Conservative: hue is preserved for genuinely coloured sources, greys get the
# mode's anchor hue, saturation is compressed toward the pastel band, and
# lightness is mapped into the mode's band keeping the source's own ordering.
#
# Light mode never produces a warm neutral: any hue that lands in the
# yellow/brown wedge and is desaturated enough to read as a neutral is pushed to
# the blue anchor. Dark mode does the same, so no surface ever reads brown.
# Alpha is always preserved.

BANDS = {
    # role          light (lo, hi, sat_max)   dark (lo, hi, sat_max)
    "surface":     ((0.91, 0.995, 0.10), (0.09, 0.20, 0.09)),
    "surface_alt": ((0.86, 0.96, 0.12), (0.12, 0.24, 0.11)),
    "accent":      ((0.52, 0.72, 0.40), (0.58, 0.78, 0.38)),
    "link":        ((0.34, 0.52, 0.46), (0.62, 0.80, 0.44)),
    "ink":         ((0.20, 0.48, 0.24), (0.66, 0.94, 0.20)),
    "media":       ((0.72, 0.99, 0.10), (0.72, 0.99, 0.10)),
}

# The warm wedge: yellow through red-orange. Anything in here that is not
# saturated enough to be a deliberate semantic colour becomes blue instead of
# beige/brown.
WARM_LO, WARM_HI = 10 / 360.0, 70 / 360.0
NEUTRALISE_BELOW_SAT = 0.45


def _is_muddy(h, s):
    """True when a colour would read as beige, tan or brown rather than a hue."""
    return WARM_LO <= h <= WARM_HI and s < NEUTRALISE_BELOW_SAT


def tail_transform(key, argb, dark):
    a, r, g, b = unpack(argb)
    role = role_of(key)
    lo, hi, smax = BANDS[role][1 if dark else 0]
    h, l, s = to_hls(r, g, b)

    surface_like = role in ("surface", "surface_alt")

    if s < 0.10:
        # neutral grey: give it the mode's cool anchor, barely tinted
        h = H_SLATE if dark else H_SKY
        s = 0.04 if surface_like else 0.16
    elif _is_muddy(h, s):
        # would read brown/beige: rotate to the cool anchor and keep it quiet
        h = H_SLATE if dark else H_SKY
        s = min(0.06 if surface_like else 0.22, s)
    else:
        s = min(smax, s * 0.58 + 0.03)

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
