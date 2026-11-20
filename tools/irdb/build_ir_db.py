#!/usr/bin/env python3
"""Builds app/src/main/assets/ir/<category>.json from Flipper-IRDB (CC0).

Usage:
    git clone --depth 1 https://github.com/Lucaslhm/Flipper-IRDB /tmp/irdb
    python3 tools/irdb/build_ir_db.py /tmp/irdb

Each output file holds the brands of one device category and their code sets. A code set maps
remote keys to codes: "<PROTOCOL>:<address hex>:<command hex>" for parsed signals (address and
command follow Flipper's protocol semantics) or "RAW:<freq>:<mark space ...>" for captures.
"""
import collections
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT_DIR = os.path.join(ROOT, "app/src/main/assets/ir")
LEGACY = os.path.join(os.path.dirname(os.path.abspath(__file__)), "legacy_codesets.json")

CATEGORIES = ["TV", "STB", "AC", "PROJECTOR", "OTT", "DVD", "FAN", "ROBOT", "PURIFIER", "AUDIO"]

PRIMARY_FOLDERS = {
    "TVs": "TV",
    "Cable_Boxes": "STB", "DVB-T": "STB", "TV_Tuner": "STB",
    "ACs": "AC",
    "Projectors": "PROJECTOR",
    "Streaming_Devices": "OTT",
    "DVD_Players": "DVD", "Blu-Ray": "DVD",
    "Fans": "FAN",
    "Vacuum_Cleaners": "ROBOT",
    "Air_Purifiers": "PURIFIER", "Humidifiers": "PURIFIER",
    "Audio_and_Video_Receivers": "AUDIO", "SoundBars": "AUDIO", "Speakers": "AUDIO",
}

# Device-type names in the converted databases are free text; order matters (first match wins).
NAME_RULES = [
    (re.compile(r"PROJECT|BEAMER"), "PROJECTOR"),
    (re.compile(r"SAT|CABLE|DSS|DVB|STB|SET.?TOP|DECODER|FREEVIEW|TIVO|DVR|PVR|IPTV"), "STB"),
    (re.compile(r"DVD|BLU.?RAY|(^|_)BD"), "DVD"),
    (re.compile(r"AIR.?COND|(^|_)AC($|_)|CLIMA"), "AC"),
    (re.compile(r"(^|_)FANS?($|_)"), "FAN"),
    (re.compile(r"STREAM|MEDIA.?PLAYER|ROKU|APPLE.?TV|FIRE.?TV|ANDROID.?BOX|TV.?BOX"), "OTT"),
    (re.compile(r"(^|_|-)TV($|_|-|\d)|TELEVISION|PLASMA|HDTV|LCD"), "TV"),
    (re.compile(r"RECEIVER|AMPLIFIER|(^|_)AMP|SURROUND|AUDIO|SOUND.?BAR|SPEAKER|HI.?FI|HOME.?THEAT|STEREO"), "AUDIO"),
]

BRAND_CASE = {
    "lg": "LG", "tcl": "TCL", "jvc": "JVC", "rca": "RCA", "aoc": "AOC", "nec": "NEC", "benq": "BenQ",
    "gree": "Gree", "viewsonic": "ViewSonic", "jbl": "JBL", "hp": "HP", "zte": "ZTE", "kdk": "KDK",
    "infocus": "InFocus", "irobot": "iRobot", "iclebo": "iCLEBO", "ilife": "iLife", "ihome": "iHome",
    "onn": "onn", "mag": "MAG", "evo": "EVO", "bbk": "BBK", "awa": "AWA", "cce": "CCE", "gpx": "GPX",
    "seg": "SEG", "pdi": "PDi", "dyon": "DYON", "finlux": "Finlux", "nad": "NAD", "aiwa": "Aiwa",
    "directv": "DirecTV", "dstv": "DStv", "foxtel": "Foxtel", "arris": "ARRIS", "humax": "Humax",
    "teac": "TEAC", "akg": "AKG", "bose": "Bose", "kef": "KEF", "lasko": "Lasko", "aux": "Aux",
    "mi": "Mi", "smart": "SMART", "wen": "WEN", "iqair": "IQAir", "3m": "3M", "she": "SHE", "tcp": "TCP",
    "koenic": "Koenic", "technic": "Technic", "pvo": "PVO", "rif6": "RIF6", "sakawa": "Sakawa",
    "toptro": "Toptro", "abox": "ABOX", "onoayo": "Onoayo", "eiki": "Eiki", "hisense": "Hisense",
}

# Brands sold in Indonesia (or requested explicitly) that no public database covers. They get the
# category's universal code sets, which is how universal remotes handle OEM-chassis devices.
LOCAL_BRANDS = {
    "TV": ["Polytron", "Coocaa", "Akari", "Changhong", "Skyworth", "Weyon", "Aqua", "Ichiko", "Digitec",
           "Mito", "Konka", "Sansui", "Xiaomi", "Haier", "Sanyo", "Advance", "Sanken", "Toshiba", "Vizio",
           "Hitachi", "Pioneer", "Insignia", "JVC", "RCA", "Akai", "Hisense", "Philips", "Sharp", "Panasonic"],
    "STB": ["Matrix Apple", "Luby", "Tanaka", "Polytron", "Evercoss", "Advance", "Welhome", "Sanex", "Noise",
            "Goldsat", "Venus", "Huawei", "ZTE", "Fiberhome", "Nexmedia", "Transvision", "K-Vision", "MNC Vision",
            "IndiHome", "First Media", "Ariang", "Skybox", "Getmecom", "Kaonsat", "Mytech", "Taffware",
            "Sharp", "Strong", "Ichiko", "Akari"],
    "AC": ["Daikin", "Panasonic", "Sharp", "Gree", "LG", "Midea", "Aux", "Changhong", "Mitsubishi Electric",
           "Mitsubishi Heavy", "Samsung", "Haier", "TCL", "Aqua", "Polytron", "Hisense", "Toshiba", "Hitachi",
           "Fujitsu", "Electrolux", "Denpoo", "Uchida", "Maspion", "Sansui", "Akari", "Modena", "Beko",
           "Chigo", "York", "Carrier", "General", "Kris"],
    "PROJECTOR": ["Wanbo", "Xiaomi", "Vivibright", "Everycom", "Cheerlux", "Hikvision", "Yaber", "Hisense"],
    "OTT": ["ZTE", "Fiberhome", "Huawei", "Xiaomi", "Nvidia", "Tanix", "Mecool", "Google", "Polytron", "Advan",
            "Evercoss", "Venus"],
    "DVD": ["Polytron", "Advance", "Ichiko", "Simbadda", "GMC", "Sharp", "Akari", "Samsung", "LG", "Sony"],
    "FAN": ["Miyako", "Cosmos", "Maspion", "Sekai", "Kris", "Panasonic", "Sharp", "Uchida", "Arashi",
            "Regency", "Kirin", "Denpoo", "Mitochiba", "Sanken", "Xiaomi", "Polytron", "Advance", "Gree",
            "Midea", "Akari", "Cosmos Wadah"],
    "ROBOT": ["Xiaomi", "Roborock", "Ecovacs", "iRobot", "Eufy", "Dreame", "Ilife", "Proscenic", "Midea"],
    "PURIFIER": ["Sharp", "Philips", "Xiaomi", "Coway", "Honeywell", "Panasonic", "Daikin", "LG", "Samsung",
                 "Cosmos", "Kris", "Levoit"],
    "AUDIO": ["Polytron", "Simbadda", "Advance", "Sharp", "Sony", "Yamaha", "JBL", "Samsung", "LG", "Philips",
              "Edifier", "Denon", "Pioneer", "Onkyo", "Bose"],
}

POPULAR = {
    "TV": ["Samsung", "LG", "Sony", "Panasonic", "Sharp", "TCL", "Hisense", "Philips", "Toshiba", "Polytron",
           "Sanyo", "Vizio", "Xiaomi", "Coocaa", "Changhong", "Akari"],
    "STB": ["Matrix Apple", "Luby", "Tanaka", "Polytron", "Evercoss", "Advance", "Huawei", "ZTE", "Fiberhome",
            "Nexmedia", "Transvision", "K-Vision"],
    "AC": ["Daikin", "Panasonic", "Sharp", "Gree", "LG", "Midea", "Aux", "Changhong", "Mitsubishi Electric",
           "Samsung", "Haier", "Polytron"],
    "PROJECTOR": ["Epson", "BenQ", "InFocus", "Acer", "ViewSonic", "Optoma", "Sony", "Wanbo", "Xiaomi"],
    "OTT": ["Xiaomi", "ZTE", "Fiberhome", "Huawei", "Amazon", "Apple", "Roku", "Mecool"],
    "DVD": ["Samsung", "LG", "Sony", "Philips", "Panasonic", "Polytron", "Pioneer"],
    "FAN": ["Miyako", "Cosmos", "Maspion", "Sekai", "Kris", "Panasonic", "Sharp", "Xiaomi", "Dyson", "Honeywell"],
    "ROBOT": ["Xiaomi", "Roborock", "Ecovacs", "iRobot", "Eufy", "Samsung", "LG"],
    "PURIFIER": ["Sharp", "Philips", "Xiaomi", "Dyson", "Honeywell", "Coway", "Levoit", "Blueair"],
    "AUDIO": ["Polytron", "Sony", "Yamaha", "JBL", "Samsung", "LG", "Denon", "Pioneer", "Bose"],
}

# Stateful AC encoders implemented in the app (AcEncoder); listed brands get them before captures.
AC_ENCODERS = {
    "Daikin": "AC_DAIKIN", "Panasonic": "AC_PANASONIC", "Sharp": "AC_SHARP", "Gree": "AC_GREE",
    "LG": "AC_LG", "Aux": "AC_AUX",
}

SKIP_BRANDS = {"unknown", "unknownbrand", "generic", "brandunknown", "universal", "hotels", "android", "misc", "other"}

UNIVERSAL_COUNT = {"TV": 10, "STB": 10, "AC": 6, "PROJECTOR": 6, "OTT": 6, "DVD": 6, "FAN": 8, "ROBOT": 5,
                   "PURIFIER": 5, "AUDIO": 6}
MAX_SETS_PER_BRAND = 24
MAX_RAW_MICROS = 1_500_000

KEY_ALIASES = {
    "POWER": ["power", "power_toggle", "on_off", "onoff", "on/off", "standby", "pwr", "power_on_off", "power_toggle"],
    "POWER_ON": ["on", "power_on", "poweron", "turn_on"],
    "POWER_OFF": ["off", "power_off", "poweroff", "turn_off"],
    "VOL_UP": ["vol_up", "vol+", "volume_up", "volup", "vol_plus", "volume+", "v+"],
    "VOL_DOWN": ["vol_dn", "vol_down", "vol-", "volume_down", "voldown", "vol_minus", "volume-", "v-", "vol_dwn"],
    "MUTE": ["mute", "mute_toggle"],
    "CH_UP": ["ch_next", "ch_up", "ch+", "channel_up", "ch_plus", "chan_up", "channel+", "p+"],
    "CH_DOWN": ["ch_prev", "ch_dn", "ch_down", "ch-", "channel_down", "ch_minus", "chan_down", "channel-", "p-"],
    "UP": ["up", "arrow_up", "cursor_up", "nav_up"],
    "DOWN": ["down", "arrow_down", "cursor_down", "nav_down"],
    "LEFT": ["left", "arrow_left", "cursor_left", "nav_left"],
    "RIGHT": ["right", "arrow_right", "cursor_right", "nav_right"],
    "OK": ["ok", "enter", "select", "center", "confirm"],
    "BACK": ["back", "return", "ret"],
    "HOME": ["home", "smart_hub", "smarthub"],
    "MENU": ["menu", "setup", "settings"],
    "EXIT": ["exit", "esc"],
    "INFO": ["info", "display"],
    "EPG": ["guide", "epg"],
    "INPUT": ["source", "input", "av", "tv/av", "input_source", "tv_av", "av/tv"],
    "PLAY": ["play"], "PAUSE": ["pause"], "PLAY_PAUSE": ["play_pause", "play/pause", "playpause"],
    "STOP": ["stop"], "REWIND": ["rewind", "rew", "rwd"], "FAST_FORWARD": ["fast_forward", "ff", "fastforward", "fwd", "forward"],
    "PREV": ["prev", "previous", "skip_prev", "skip_back", "prev_track"],
    "NEXT": ["next", "skip_next", "skip", "next_track"],
    "NETFLIX": ["netflix"], "YOUTUBE": ["youtube"],
    "TEMP_UP": ["temp+", "temp_up", "temperature_up", "temp_plus", "temp_+", "tempup"],
    "TEMP_DOWN": ["temp-", "temp_down", "temp_dn", "temperature_down", "temp_minus", "temp_-", "tempdown"],
    "MODE": ["mode"],
    "FAN_SPEED": ["fan", "fan_speed", "speed", "wind", "wind_speed", "fanspeed"],
    "SPEED_UP": ["speed_up", "fan_up", "speed+", "faster", "fan+", "increase"],
    "SPEED_DOWN": ["speed_down", "fan_down", "speed-", "slower", "fan-", "decrease"],
    "SWING": ["swing", "osc", "oscillate", "oscillation", "rotate", "rotation", "turn", "spin", "direction"],
    "TIMER": ["timer", "time"],
    "SLEEP": ["sleep", "night", "night_mode", "nightmode", "moon/sleep"],
    "LIGHT": ["light", "led", "mood_light", "display_light"],
    "TURBO": ["turbo", "max", "boost", "jet"],
    "COOL": ["cool"], "DRY": ["dry", "dh"], "HEAT": ["heat"], "AUTO": ["auto"],
}
ROBOT_ALIASES = {
    "START": ["start", "clean", "start_stop", "start/pause", "start_pause", "stop_start", "start/stop", "auto",
              "clean_start", "play_pause", "power", "on_off"],
    "DOCK": ["home", "recharge", "dock", "charge", "go_home", "return_home", "base"],
    "SPOT": ["spot", "spot_clean", "spiral", "spotclean"],
    "EDGE": ["edge", "edge_clean", "edgeclean", "wall_follow"],
    "UP": ["up", "forward", "foward"],
    "DOWN": ["down", "reverse", "back"],
    "LEFT": ["left"], "RIGHT": ["right"],
    "TURBO": ["max", "turbo", "suction", "suction_level"],
    "TIMER": ["timer", "clock", "time_scheduling", "schedule"],
    "MODE": ["mode"],
}

def build_alias_table(table):
    out = {}
    for key, names in table.items():
        for n in names:
            out.setdefault(n, key)
    return out

ALIASES = build_alias_table(KEY_ALIASES)
ALIASES.update({str(d): f"NUM_{d}" for d in range(10)})
ROBOT_TABLE = build_alias_table(ROBOT_ALIASES)
# Robot remotes use "home" for docking; generic aliases still fill in keys like power or timer.
TEST_KEYS = ["POWER", "POWER_ON", "START", "POWER_OFF"]


def map_button(category, name):
    raw = name.strip().lower()
    for candidate in (raw, re.sub(r"[\s\-]+", "_", raw).strip("_"), re.sub(r"\s+", "_", raw)):
        if category == "ROBOT" and candidate in ROBOT_TABLE:
            return ROBOT_TABLE[candidate]
        if candidate in ALIASES:
            key = ALIASES[candidate]
            if category not in ("AC", "FAN", "PURIFIER") and key in ("COOL", "DRY", "HEAT", "AUTO", "TEMP_UP", "TEMP_DOWN"):
                return None
            return key
    return None


def parse_ir_file(path):
    signals = []
    cur = {}
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.strip()
            if line.startswith("#"):
                if cur:
                    signals.append(cur)
                cur = {}
                continue
            if ":" not in line:
                continue
            k, v = line.split(":", 1)
            cur[k.strip().lower()] = v.strip()
    if cur:
        signals.append(cur)
    return [s for s in signals if "name" in s and "type" in s]


def le_hex(value, nbytes=4):
    parts = value.split()
    return int("".join(reversed(parts[:nbytes])), 16)


def encode_signal(sig):
    t = sig["type"].lower()
    if t == "parsed":
        proto = sig.get("protocol", "")
        try:
            addr = le_hex(sig["address"])
            cmd = le_hex(sig["command"])
        except (KeyError, ValueError):
            return None
        mapping = {
            "NEC": ("NEC", 0xFF, 0xFF), "NECext": ("NEC_EXT", 0xFFFF, 0xFFFF), "Samsung32": ("SAMSUNG", 0xFF, 0xFF),
            "RC5": ("RC5", 0x1F, 0x3F), "RC5X": ("RC5X", 0x1F, 0x3F), "RC6": ("RC6", 0xFF, 0xFF),
            "SIRC": ("SIRC12", 0x1F, 0x7F), "SIRC15": ("SIRC15", 0xFF, 0x7F), "SIRC20": ("SIRC20", 0x1FFF, 0x7F),
            "Kaseikyo": ("KASEIKYO", 0x3FFFFFF, 0x3FF), "RCA": ("RCA", 0xF, 0xFF), "Pioneer": ("PIONEER", 0xFF, 0xFF),
        }
        if proto not in mapping:
            return None
        name, amask, cmask = mapping[proto]
        if addr & ~amask or cmd & ~cmask:
            return None
        return f"{name}:{addr:X}:{cmd:X}"
    if t == "raw":
        try:
            freq = int(float(sig.get("frequency", "38000")))
            data = [abs(int(x)) for x in sig.get("data", "").split()]
        except ValueError:
            return None
        if len(data) < 6 or not 20000 <= freq <= 60000:
            return None
        if len(data) % 2 == 0:
            data = data[:-1]  # trailing space carries no information for transmit()
        if sum(data) > MAX_RAW_MICROS or min(data) <= 0:
            return None
        return f"RAW:{freq}:" + " ".join(str(x) for x in data)
    return None


def display_brand(raw):
    name = raw.replace("_", " ").strip()
    key = re.sub(r"[^a-z0-9]", "", name.lower())
    if key in BRAND_CASE:
        return BRAND_CASE[key]
    if name.isupper() and len(name) > 3:
        name = " ".join(w.capitalize() for w in name.split())
    return name[:1].upper() + name[1:]


def brand_key(name):
    return re.sub(r"[^a-z0-9]", "", name.lower())


def classify_name(text):
    up = text.upper().replace(" ", "_")
    for rx, cat in NAME_RULES:
        if rx.search(up):
            return cat
    return None


def clean_model(stem, brand):
    s = stem.replace("_", " ").strip()
    if s.lower().startswith(brand.lower()):
        s = s[len(brand):].strip(" -")
    return s or "Remote"


def collect(irdb):
    """Yields (category, brand_display, model, path)."""
    for folder, cat in PRIMARY_FOLDERS.items():
        base = os.path.join(irdb, folder)
        if not os.path.isdir(base):
            continue
        for brand_dir in sorted(os.listdir(base)):
            bpath = os.path.join(base, brand_dir)
            if not os.path.isdir(bpath) or brand_key(brand_dir) in SKIP_BRANDS:
                continue
            brand = display_brand(brand_dir)
            for dirpath, _, files in os.walk(bpath):
                for fn in sorted(files):
                    if not fn.endswith(".ir"):
                        continue
                    b = brand
                    if cat == "AC" and brand == "Mitsubishi":
                        b = "Mitsubishi Heavy" if re.search(r"SRK|FDT|heavy", fn, re.I) else "Mitsubishi Electric"
                    sub = os.path.relpath(dirpath, bpath)
                    model = clean_model((sub + " " if sub != "." else "") + fn[:-3], brand_dir.replace("_", " "))
                    yield cat, b, model, os.path.join(dirpath, fn), "primary"
    conv = os.path.join(irdb, "_Converted_")
    for source in ("CSV", "IR_Plus", "Pronto"):
        sbase = os.path.join(conv, source)
        if not os.path.isdir(sbase):
            continue
        for letter in sorted(os.listdir(sbase)):
            lpath = os.path.join(sbase, letter)
            if not os.path.isdir(lpath):
                continue
            for brand_dir in sorted(os.listdir(lpath)):
                bpath = os.path.join(lpath, brand_dir)
                if not os.path.isdir(bpath) or brand_key(brand_dir) in SKIP_BRANDS:
                    continue
                brand = display_brand(brand_dir)
                for dirpath, _, files in os.walk(bpath):
                    rel = os.path.relpath(dirpath, bpath)
                    for fn in sorted(files):
                        if not fn.endswith(".ir"):
                            continue
                        label = (rel + "_" if rel != "." else "") + fn[:-3]
                        if label.lower().startswith("unknown"):
                            continue
                        cat = classify_name(label)
                        if cat is None:
                            continue
                        model = label.replace("/", " ").replace("_", " ")
                        yield cat, brand, model, os.path.join(dirpath, fn), source


def build(irdb):
    with open(LEGACY, encoding="utf-8") as f:
        legacy = json.load(f)

    # category -> brand_key -> {"name", "sets": [set ids]}
    brands = {c: collections.OrderedDict() for c in CATEGORIES}
    sets = {c: {} for c in CATEGORIES}
    signatures = {c: {} for c in CATEGORIES}
    power_votes = {c: collections.defaultdict(set) for c in CATEGORIES}
    counters = collections.Counter()

    def add_brand(cat, name):
        bk = brand_key(name)
        if bk not in brands[cat]:
            brands[cat][bk] = {"name": name, "sets": []}
        return brands[cat][bk]

    def add_set(cat, brand_name, model, keys, universal=False, set_id=None):
        test = next((k for k in TEST_KEYS if k in keys), None)
        if test is None:
            return None
        sig = json.dumps(sorted(keys.items()))
        b = add_brand(cat, brand_name)
        if sig in signatures[cat]:
            sid = signatures[cat][sig]
        else:
            counters[cat] += 1
            sid = set_id or f"{cat.lower()}_{counters[cat]}"
            sets[cat][sid] = {"model": model, "keys": keys}
            if universal:
                sets[cat][sid]["universal"] = True
            signatures[cat][sig] = sid
        if sid not in b["sets"]:
            b["sets"].append(sid)
        power_votes[cat][keys[test]].add(brand_key(brand_name))
        return sid

    for cat, brand, model, path, source in collect(irdb):
        keys = {}
        for sig in parse_ir_file(path):
            key = map_button(cat, sig["name"])
            if key is None or key in keys:
                continue
            code = encode_signal(sig)
            if code:
                keys[key] = code
        if "POWER" not in keys and "POWER_ON" in keys and cat not in ("AC",):
            keys["POWER"] = keys["POWER_ON"]
        if len(keys) >= 2 or (cat in ("AC", "FAN", "PURIFIER", "ROBOT") and keys):
            add_set(cat, brand, model, keys)

    # Hand-maintained code sets from the previous database (Indonesian DVB-T2 STBs, Sharp classic).
    for cat, items in legacy["codeSets"].items():
        for sid, entry in items.items():
            sets[cat][sid] = {"model": entry["model"], "keys": entry["keys"]}
            if entry.get("universal"):
                sets[cat][sid]["universal"] = True
            signatures[cat][json.dumps(sorted(entry["keys"].items()))] = sid
            for b in entry.get("brands", []):
                add_brand(cat, b)["sets"].insert(0, sid)

    universal = {}
    for cat in CATEGORIES:
        # A code shared by many brands is the best bet for unbranded OEM hardware.
        ranked = sorted(power_votes[cat].items(), key=lambda kv: -len(kv[1]))
        chosen = [sid for sid, s in sets[cat].items() if s.get("universal")]
        for code, voters in ranked:
            if len(chosen) >= UNIVERSAL_COUNT[cat]:
                break
            candidates = [sid for sid, s in sets[cat].items()
                          if any(s["keys"].get(k) == code for k in TEST_KEYS) and sid not in chosen]
            if candidates:
                best = max(candidates, key=lambda sid: (len(sets[cat][sid]["keys"]), -len(sid)))
                chosen.append(best)
        universal[cat] = chosen

    def test_code(cat, sid):
        keys = sets[cat][sid]["keys"]
        return next(keys[k] for k in TEST_KEYS if k in keys)

    for cat in CATEGORIES:
        for name in LOCAL_BRANDS.get(cat, []):
            add_brand(cat, name)
        for bk, b in brands[cat].items():
            # Most widely shared Power code first, so the pairing wizard usually succeeds early.
            b["sets"].sort(key=lambda sid: (-len(power_votes[cat][test_code(cat, sid)]), -len(sets[cat][sid]["keys"])))
            if cat == "AC":
                enc = AC_ENCODERS.get(b["name"])
                if enc:
                    b["sets"].insert(0, "@" + enc)
            if len(b["sets"]) < 3 or not any(not sets[cat].get(s, {}).get("universal") for s in b["sets"] if not s.startswith("@")):
                for sid in universal[cat]:
                    if sid not in b["sets"]:
                        b["sets"].append(sid)
            if cat == "AC" and not any(s.startswith("@") for s in b["sets"]) and len(b["sets"]) < 4:
                b["sets"].extend("@" + e for e in AC_ENCODERS.values())
            b["sets"] = b["sets"][:MAX_SETS_PER_BRAND]

    meta = {"source": "Flipper-IRDB (CC0 1.0) + Remotku legacy sets", "revision": irdb_revision(irdb)}
    os.makedirs(OUT_DIR, exist_ok=True)
    totals = {}
    for cat in CATEGORIES:
        used = {s for b in brands[cat].values() for s in b["sets"] if not s.startswith("@")}
        popular = {brand_key(p) for p in POPULAR.get(cat, [])}
        out_brands = []
        for bk, b in sorted(brands[cat].items(), key=lambda kv: kv[1]["name"].lower()):
            if not b["sets"]:
                continue
            entry = {"name": b["name"], "sets": b["sets"]}
            if bk in popular:
                entry["popular"] = POPULAR[cat].index(next(p for p in POPULAR[cat] if brand_key(p) == bk)) + 1
            out_brands.append(entry)
        doc = {"version": 2, "category": cat, **meta,
               "brands": out_brands,
               "codeSets": {sid: sets[cat][sid] for sid in sorted(used, key=natural_key)}}
        with open(os.path.join(OUT_DIR, f"{cat.lower()}.json"), "w", encoding="utf-8") as f:
            json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))
        totals[cat] = (len(out_brands), len(used))
    for cat, (nb, ns) in totals.items():
        print(f"{cat:10s} brands={nb:4d} codeSets={ns:5d}")
    print("total brands:", sum(t[0] for t in totals.values()),
          "unique brand names:", len({brand_key(b['name']) for c in CATEGORIES for b in brands[c].values()}))


def natural_key(s):
    return [int(t) if t.isdigit() else t for t in re.split(r"(\d+)", s)]


def irdb_revision(irdb):
    try:
        return subprocess.check_output(["git", "-C", irdb, "rev-parse", "--short", "HEAD"], text=True).strip()
    except Exception:
        return "unknown"


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    build(sys.argv[1])
