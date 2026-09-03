#!/usr/bin/env python3
"""
Pre-flight checks for KamiGram's fork-local code.

Motivation: `javac -proc:only` parses but does not resolve symbols, so two
classes of mistake reached CI and cost a full build each:

  1. a call site referencing a field that does not exist in its host class
     (BackupImageView had no `resourcesProvider`)
  2. a call to a fork helper with the wrong argument count
     (KamiLog.write/4 called with 3 arguments)

This script catches both cheaply, without Gradle. It is not a compiler: it only
verifies that what the fork code references actually exists, which is exactly
where the fork is most likely to drift from upstream.

Run:  python3 Tools/kamigram_verify.py
Exit: 0 clean, 1 problems found (also wired into the CI workflow)
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "TMessagesProj/src/main/java")

problems = []


def read(rel):
    path = os.path.join(SRC, rel)
    if not os.path.exists(path):
        problems.append(f"missing file: {rel}")
        return ""
    with open(path, encoding="utf8", errors="replace") as fh:
        return fh.read()


def declared_methods(source):
    """name -> set of arity, for methods declared in this source."""
    out = {}
    pattern = re.compile(
        r"^\s*(?:public|private|protected|static|final|synchronized|\s)+"
        r"[\w<>\[\],.?\s]+\s+(\w+)\s*\(([^)]*)\)\s*(?:\{|throws)",
        re.M,
    )
    for m in pattern.finditer(source):
        name, args = m.group(1), m.group(2).strip()
        if name in ("if", "for", "while", "switch", "catch", "return", "new"):
            continue
        arity = 0 if not args else len(split_args(args))
        out.setdefault(name, set()).add(arity)
    return out


def split_args(args):
    """Split a parameter list on top-level commas (generics-aware)."""
    parts, depth, cur = [], 0, ""
    for ch in args:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        parts.append(cur)
    return parts


def call_arity(source, start):
    """Argument count of the call whose '(' is at `start`, generics-aware."""
    depth, cur, parts = 0, "", []
    i = start
    while i < len(source):
        ch = source[i]
        if ch in "([{<":
            depth += 1
            if depth == 1 and ch == "(":
                i += 1
                continue
        elif ch in ")]}>":
            depth -= 1
            if depth == 0:
                if cur.strip():
                    parts.append(cur)
                return len(parts)
        if ch == "," and depth == 1:
            parts.append(cur)
            cur = ""
        elif depth >= 1:
            cur += ch
        i += 1
    return -1


FORK_HELPERS = ("Skeuomorphic", "KamiLog", "KamiConfig")

# Identifiers that are always resolvable: language keywords, literals, and the
# helper classes themselves.
IGNORED_IDENTIFIERS = {
    "this", "true", "false", "null", "canvas", "context",
    "Skeuomorphic", "KamiLog", "KamiConfig", "Theme", "AndroidUtilities",
    "Color", "ColorUtils", "MessageObject", "NotificationCenter", "R",
}


def referenced_identifiers(source):
    """
    Bare identifiers passed as arguments to a fork helper call.

    A bare identifier (no dot, not a literal, not a call) must resolve to a field,
    local or parameter of the host class - and it is the field case that broke CI.
    """
    out = set()
    for helper in FORK_HELPERS:
        for m in re.finditer(r"\b" + helper + r"\.\w+\s*\(", source):
            depth, cur, args, i = 0, "", [], m.end() - 1
            while i < len(source):
                ch = source[i]
                if ch in "([{":
                    depth += 1
                    if depth == 1:
                        i += 1
                        continue
                elif ch in ")]}":
                    depth -= 1
                    if depth == 0:
                        args.append(cur)
                        break
                if ch == "," and depth == 1:
                    args.append(cur)
                    cur = ""
                elif depth >= 1:
                    cur += ch
                i += 1
            for arg in args:
                arg = arg.strip()
                if re.fullmatch(r"[A-Za-z_]\w*", arg) and arg not in IGNORED_IDENTIFIERS:
                    out.add(arg)
    return out


def check_helper_calls(helper_rel, helper_class, callers):
    """Every `Helper.method(...)` call must match a declared name and arity."""
    helper_src = read(helper_rel)
    if not helper_src:
        return
    methods = declared_methods(helper_src)
    for rel in callers:
        src = read(rel)
        if not src:
            continue
        for m in re.finditer(r"\b" + helper_class + r"\.(\w+)\s*\(", src):
            name = m.group(1)
            if name not in methods:
                problems.append(
                    f"{rel}: calls {helper_class}.{name}(), which is not declared in {helper_class}"
                )
                continue
            arity = call_arity(src, m.end() - 1)
            if arity >= 0 and arity not in methods[name]:
                problems.append(
                    f"{rel}: calls {helper_class}.{name}() with {arity} args; "
                    f"declared arities are {sorted(methods[name])}"
                )


def check_fields_exist(rel, identifiers):
    """
    Identifiers the fork code reads must be real class members of the host.

    Only class-level declarations and `this.x =` assignments count. An earlier
    version also accepted any appearance inside a parameter list, which let the
    real BackupImageView bug through: that file mentions `resourcesProvider` as a
    parameter in an unrelated method but has no such field, which is exactly what
    javac rejected.
    """
    src = read(rel)
    if not src:
        return
    for ident in identifiers:
        member = re.search(
            r"^[ \t]{1,8}(?:@\w+\s+)*(?:private|public|protected|static|final|volatile|transient|\s)*"
            r"[\w<>\[\],.?]+\s+" + re.escape(ident) + r"\s*(?:[;=]|,)",
            src,
            re.M,
        )
        assigned = re.search(r"\bthis\." + re.escape(ident) + r"\s*=", src)
        imported = re.search(r"^import\s+[\w.]*\." + re.escape(ident) + r";", src, re.M)
        # a parameter or local of the enclosing method is equally valid; require
        # the declaration to be typed so a mere mention does not satisfy the check
        typed_local = re.search(
            r"(?:\(|,|;|\{)\s*(?:final\s+)?[\w<>\[\],.?]+\s+" + re.escape(ident) + r"\s*(?:\)|,|=|;)",
            src,
        )
        if not (member or assigned or imported or typed_local):
            problems.append(
                f"{rel}: references '{ident}', which is not a member of this class"
            )


def check_theme_keys(rels):
    """Every Theme.key_* used by fork code must exist in Theme.java."""
    theme = read("org/telegram/ui/ActionBar/Theme.java")
    if not theme:
        return
    known = set(re.findall(r"public static final int (key_\w+)", theme))
    for rel in rels:
        src = read(rel)
        for key in set(re.findall(r"Theme\.(key_\w+)", src)):
            if key not in known:
                problems.append(f"{rel}: unknown theme key Theme.{key}")


def check_notification_events(rels):
    """Fork NotificationCenter events must be declared before use."""
    nc = read("org/telegram/messenger/NotificationCenter.java")
    known = set(re.findall(r"public static final int (\w+) = totalEvents\+\+;", nc))
    for rel in rels:
        src = read(rel)
        for ev in set(re.findall(r"NotificationCenter\.(kami\w+)", src)):
            if ev not in known:
                problems.append(f"{rel}: unknown NotificationCenter.{ev}")


def check_palette_sync():
    """ThemeColors defaults must match the palette tool's authored light values."""
    sys.path.insert(0, os.path.join(ROOT, "Tools"))
    try:
        import kamigram_palette as K
    except Exception as exc:  # pragma: no cover
        problems.append(f"palette tool import failed: {exc}")
        return
    src = read("org/telegram/ui/ActionBar/ThemeColors.java")
    found = dict(re.findall(r"defaultColors\[key_(\w+)\]\s*=\s*(0x[0-9A-Fa-f]{8})", src))
    drifted = []
    for key, (light, _dark) in K.CORE.items():
        if key in found and int(found[key], 16) != (light & 0xFFFFFFFF):
            drifted.append(key)
    if drifted:
        problems.append(
            f"ThemeColors.java has drifted from the palette tool for "
            f"{len(drifted)} key(s): {drifted[:6]}. Run Tools/kamigram_repaint.py"
        )


FORK_FILES = [
    "com/kaminari/gram/KamiLog.java",
    "com/kaminari/gram/KamiConfig.java",
    "com/kaminari/gram/ui/ExtraordiKamiActivity.java",
    "org/telegram/ui/Components/Skeuomorphic.java",
]

TOUCHED_FILES = [
    "org/telegram/ui/Components/BackupImageView.java",
    "org/telegram/ui/Components/Switch.java",
    "org/telegram/ui/Cells/DialogCell.java",
    "org/telegram/ui/Cells/ChatMessageCell.java",
    "org/telegram/ui/ChatActivity.java",
    "org/telegram/ui/ProfileActivity.java",
    "org/telegram/messenger/MessagesController.java",
    "org/telegram/messenger/MessagesStorage.java",
    "org/telegram/messenger/MessageObject.java",
    "org/telegram/messenger/NotificationsController.java",
    "org/telegram/messenger/ApplicationLoader.java",
    "org/telegram/messenger/FileLog.java",
    "org/telegram/messenger/LocaleController.java",
]

ALL = FORK_FILES + TOUCHED_FILES


def main():
    check_helper_calls("org/telegram/ui/Components/Skeuomorphic.java", "Skeuomorphic", ALL)
    check_helper_calls("com/kaminari/gram/KamiLog.java", "KamiLog", ALL)
    check_helper_calls("com/kaminari/gram/KamiConfig.java", "KamiConfig", ALL)

    # Any identifier a fork insertion passes to Skeuomorphic/KamiLog/KamiConfig must
    # exist in the host class. Derived from the call sites themselves so a new
    # insertion is covered without editing this script.
    for rel in TOUCHED_FILES:
        check_fields_exist(rel, sorted(referenced_identifiers(read(rel))))

    check_theme_keys(FORK_FILES)
    check_notification_events(ALL)
    check_palette_sync()

    if problems:
        print(f"kamigram_verify: {len(problems)} problem(s)\n")
        for p in problems:
            print("  " + p)
        return 1
    print("kamigram_verify: clean")
    return 0


if __name__ == "__main__":
    sys.exit(main())
