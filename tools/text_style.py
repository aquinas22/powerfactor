"""PowerFactor text style (see docs/gui-style-guide.md).

Interface labels use Title Case: every word starts with a capital except the articles a, an and the
("Mod Bench", "Structure Incomplete", "Waiting for Night" -> "Waiting For Night"). Sentences (help text,
tooltips explaining something, the handbook) stay sentence case. Nothing is written in capitals except
real acronyms, and unit symbols keep their own case (mB/t, kV, m/s).

`normalize` applies the rule to the language table at generation time and `violations` lets
check_resources.py reject text that breaks it.
"""
import re

ACRONYMS = {
    "AND", "OR", "XOR", "NAND", "SCADA", "HV", "MV", "LV", "AC", "DC", "GUI", "RGB", "HP", "MK", "RX", "TX", "NV", "HMI", "LED", "FE", "EE",
    "CE", "PF", "NBT", "VFD", "RPM", "HUD", "OK", "LCD", "IO", "AE2", "JEI", "EMI", "GPS", "AI", "RF", "UV", "TNT", "DIY", "ID", "MW", "GW", "KW",
}
ARTICLES = {"a", "an", "the"}
UNITS = {"mB", "m", "s", "t", "pu", "kV", "V", "Hz", "kHz", "ms", "km", "h", "x", "k", "M", "lag", "lead", "hp", "mk"}
WORD = re.compile(r"(?<![%A-Za-z0-9$])[A-Za-z][A-Za-z0-9']*")   # never the "s" of a %s placeholder

# Labels: short interface text. Explanations (help, tips, hints, descriptions) and long or punctuated
# sentences are left in sentence case.
LABEL_PREFIX = ("icon.", "gui.", "scada.", "tool_kind.", "container.", "subtitles.", "message.", "key.")
SENTENCE_SUFFIX = (".help", ".tip", ".desc", ".hint", ".tooltip", "_help", "_tip", ".explain", ".intro", ".body", ".info", ".note")


def _shout(word):
    return len(word) >= 2 and word.isupper() and word not in ACRONYMS and not re.search(r"\d", word)


def is_label(key, value):
    if not isinstance(value, str) or not key.startswith(LABEL_PREFIX) or key.endswith(SENTENCE_SUFFIX):
        return False
    if "help" in key.split(".")[-1] or "hint" in key.split(".")[-1]:
        return False
    return len(value) <= 48 and ". " not in value and not value.rstrip().endswith(".") and "\n" not in value


def _unit(text, m):
    w = m.group(0)
    before = text[m.start() - 1] if m.start() > 0 else " "
    after = text[m.end()] if m.end() < len(text) else " "
    return w in UNITS or before == "/" or after == "/" or before.isdigit()


def _title(text):
    chars = list(text)
    first = True
    for m in WORD.finditer(text):
        w = m.group(0)
        lead = text[max(0, m.start() - 2):m.start()]
        phrase_start = first or lead.endswith((": ", "· ", "- ", "( "))
        first = False
        if _unit(text, m) or w.isupper() and w in ACRONYMS:
            continue
        if w.lower() in ARTICLES and not phrase_start:
            for i in range(m.start(), m.end()):
                chars[i] = chars[i].lower()
            continue
        chars[m.start()] = chars[m.start()].upper()
    return "".join(chars)


def fix(key, value):
    out = WORD.sub(lambda m: m.group(0).lower() if _shout(m.group(0)) else m.group(0), value)
    return _title(out) if is_label(key, out) else out


def normalize(lang):
    for k, v in list(lang.items()):
        if isinstance(v, str):
            lang[k] = fix(k, v)
    return lang


def violations(lang):
    return [(k, v) for k, v in lang.items() if isinstance(v, str) and fix(k, v) != v]
