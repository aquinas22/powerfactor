"""Synthesizes PowerFactor's original sound effects (pure Python -> WAV -> OGG via ffmpeg libvorbis).
Run: python3 tools/gen_sounds.py

Every sound is generated from scratch (oscillators, filtered noise, envelopes) with fixed random
seeds, so the output is deterministic. Loops are built to wrap seamlessly:
  * tonal parts use frequencies that complete a whole number of cycles in the loop length
    (and are filtered "periodically": rendered twice through the filter, second pass kept);
  * noise beds are rendered a little longer than the loop and the tail is crossfaded into the head;
  * discrete events (crackles, thumps, ticks) are mixed into a circular buffer, so an event that
    starts near the end wraps around to the beginning.
Loops are normalized by RMS (quiet, they play continuously); one-shots are peak-normalized to -1 dBFS.
Every loop must pass check_loop() (seam continuity) before it is encoded.

Run with sound names (or event names) as arguments to regenerate only those files, e.g.
  python3 tools/gen_sounds.py sword_hum sword_swing
sounds.json is always rewritten from the full tables.
"""
import json, math, os, random, struct, subprocess, sys, tempfile, wave

SR = 44100
ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "powerfactor")
OUT = os.path.join(ASSETS, "sounds")
TAU = 2 * math.pi

PEAK_ONESHOT = 10 ** (-1 / 20)   # -1 dBFS
PEAK_CEILING = 10 ** (-1 / 20)


# ---------------------------------------------------------------- output

def db(x):
    return 20 * math.log10(max(1e-12, x))


def rms(s):
    return math.sqrt(sum(x * x for x in s) / len(s))


def normalize_peak(s, peak=PEAK_ONESHOT):
    p = max(1e-9, max(abs(x) for x in s))
    return [x / p * peak for x in s]


def normalize_rms(s, target_db):
    g = 10 ** (target_db / 20) / max(1e-9, rms(s))
    s = [x * g for x in s]
    p = max(abs(x) for x in s)
    if p > PEAK_CEILING:  # never clip: back off if the crest factor is high
        s = [x * PEAK_CEILING / p for x in s]
    return s


def rotate_to_quiet(s, win=256):
    """Start a (seamless) loop at its quietest spot. Vorbis codes the file edges without knowing they
    wrap, so its small edge errors are proportional to the signal level there; a quiet wrap point keeps
    the loop seam inaudible after encoding."""
    n = len(s)
    e = sum(s[i] * s[i] for i in range(-win, win))
    best, best_i = e, 0
    for i in range(1, n):
        e += s[(i + win - 1) % n] ** 2 - s[(i - win - 1) % n] ** 2
        if e < best:
            best, best_i = e, i
    # nudge to the nearest zero crossing
    for k in range(200):
        j = (best_i + k) % n
        if s[j - 1] <= 0 < s[j]:
            best_i = j
            break
    return s[best_i:] + s[:best_i]


def write_ogg(name, samples):
    data = b"".join(struct.pack("<h", int(round(max(-1.0, min(1.0, x)) * 32767))) for x in samples)
    fd, path = tempfile.mkstemp(suffix=".wav")
    os.close(fd)
    with wave.open(path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(data)
    # +bitexact: fixed Ogg stream serial, so re-running produces byte-identical files
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-c:a", "libvorbis", "-q:a", "4",
                    "-fflags", "+bitexact", os.path.join(OUT, name + ".ogg")], check=True)
    os.remove(path)


# ---------------------------------------------------------------- building blocks

class Biquad:
    """RBJ cookbook biquad (lowpass / highpass / bandpass with constant 0 dB peak)."""
    def __init__(self, kind, freq, q=0.707):
        w0 = TAU * freq / SR
        cw, sw = math.cos(w0), math.sin(w0)
        alpha = sw / (2 * q)
        if kind == "lp":
            b0, b1, b2 = (1 - cw) / 2, 1 - cw, (1 - cw) / 2
        elif kind == "hp":
            b0, b1, b2 = (1 + cw) / 2, -(1 + cw), (1 + cw) / 2
        elif kind == "bp":
            b0, b1, b2 = alpha, 0.0, -alpha
        else:
            raise ValueError(kind)
        a0, a1, a2 = 1 + alpha, -2 * cw, 1 - alpha
        self.b0, self.b1, self.b2, self.a1, self.a2 = b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0
        self.x1 = self.x2 = self.y1 = self.y2 = 0.0

    def __call__(self, x):
        y = self.b0 * x + self.b1 * self.x1 + self.b2 * self.x2 - self.a1 * self.y1 - self.a2 * self.y2
        self.x2, self.x1, self.y2, self.y1 = self.x1, x, self.y1, y
        return y


def filt(sig, *stages):
    """Run a signal through a chain of (kind, freq, q) biquads."""
    for st in stages:
        f = Biquad(*st)
        sig = [f(x) for x in sig]
    return sig


def filt_periodic(sig, *stages):
    """Filter a periodic signal so its steady state wraps cleanly: run two periods, keep the second."""
    n = len(sig)
    return filt(sig + sig, *stages)[n:]


def noise(n, r):
    return [r.uniform(-1, 1) for _ in range(n)]


def loop_noise(n, r, *stages, xfade=4410):
    """Filtered noise bed of length n whose end flows into its start (equal-power crossfade)."""
    pre = 2048  # let the filters settle before the part we keep
    raw = filt(noise(n + xfade + pre, r), *stages)[pre:]
    out = raw[:n]
    for i in range(xfade):
        w = i / xfade
        out[i] = raw[i] * math.sin(0.5 * math.pi * w) + raw[n + i] * math.cos(0.5 * math.pi * w)
    return out


def mix(*parts):
    n = max(len(p) for p, _ in parts)
    out = [0.0] * n
    for p, g in parts:
        for i, x in enumerate(p):
            out[i] += x * g
    return out


def add_at(buf, grain, start, gain=1.0, circular=False):
    n = len(buf)
    for i, x in enumerate(grain):
        j = start + i
        if circular:
            buf[j % n] += x * gain
        elif 0 <= j < n:
            buf[j] += x * gain


def tone(n, partials, lfo=None):
    """Sum of sines. partials: list of (freq, amp, phase). lfo(t) -> amplitude multiplier."""
    out = []
    for i in range(n):
        t = i / SR
        x = 0.0
        for f, a, ph in partials:
            x += a * math.sin(TAU * f * t + ph)
        out.append(x * (lfo(t) if lfo else 1.0))
    return out


def fade_edges(s, fin=0.002, fout=0.01):
    a, b = max(1, int(fin * SR)), max(1, int(fout * SR))
    n = len(s)
    for i in range(min(a, n)):
        s[i] *= i / a
    for i in range(min(b, n)):
        s[n - 1 - i] *= i / b
    return s


def ring(n, modes, r=None, start=0):
    """Struck-metal ring: inharmonic partials (freq, amp, decay_seconds)."""
    out = [0.0] * n
    for f, a, d in modes:
        ph = r.uniform(0, TAU) if r else 0.0
        k = math.exp(-1 / (d * SR))
        env = a
        for i in range(start, n):
            out[i] += env * math.sin(TAU * f * (i - start) / SR + ph)
            env *= k
    return out


def burst(n, r, decay, stages=(), attack=0.0005):
    """Exponentially decaying filtered noise burst (impacts, clicks, puffs)."""
    raw = filt(noise(n, r), *stages) if stages else noise(n, r)
    at = max(1, int(attack * SR))
    return [x * min(1.0, i / at) * math.exp(-i / (decay * SR)) for i, x in enumerate(raw)]


def thump(n, f0, f1, decay, drop=0.04):
    """Low sine kick with a downward pitch drop."""
    out, ph = [], 0.0
    for i in range(n):
        t = i / SR
        f = f1 + (f0 - f1) * math.exp(-t / drop)
        ph += f / SR
        out.append(math.sin(TAU * ph) * math.exp(-t / decay) * min(1.0, i / 40))
    return out


def soft_clip(s, drive=1.0):
    return [math.tanh(x * drive) / math.tanh(drive) for x in s]


# ---------------------------------------------------------------- loops

def turbine_run(seconds=3.0):
    n = int(SR * seconds)  # all tonal frequencies are multiples of 1/3 Hz -> whole cycles in 3 s
    r = random.Random(101)
    f0 = 700.0
    whine = tone(n, [
        (f0, 1.0, 0.0),
        (f0 + 1 / 3, 0.35, 1.1),        # detuned twin: one slow beat per loop ("shimmer")
        (2 * f0, 0.45, 0.4),
        (2 * f0 + 1.0, 0.18, 2.0),
        (3 * f0, 0.22, 1.7),
        (4 * f0, 0.10, 0.9),
        (1.5 * f0, 0.08, 0.3),          # blade-pass subharmonic colour
        (5 * f0, 0.04, 2.5),
    ], lfo=lambda t: 1.0 + 0.06 * math.sin(TAU * 5 * t) + 0.04 * math.sin(TAU * (7 + 1 / 3) * t))
    whine = filt_periodic(whine, ("lp", 3500, 0.7))
    steam = loop_noise(n, r, ("hp", 900, 0.7), ("lp", 5000, 0.6))
    steam = [x * (1.0 + 0.15 * math.sin(TAU * (2 / 3) * i / SR)) for i, x in enumerate(steam)]
    rumble_t = tone(n, [(50.0, 1.0, 0.0), (100.0, 0.35, 0.5), (75.0, 0.2, 1.0)])
    rumble_n = loop_noise(n, r, ("lp", 140, 0.7), ("lp", 140, 0.7))
    return mix((whine, 0.30), (steam, 0.55), (rumble_t, 0.18), (rumble_n, 3.0))


def motor_hum(n, r, base=60.0, bright=1.0, whine_f=None, whine_a=0.0):
    """Induction motor: 120 Hz magnetostriction hum with 240/360 Hz, a touch of 60 Hz, optional rotor whine."""
    parts = [(base, 0.25, 0.0), (2 * base, 1.0, 0.3), (4 * base, 0.45 * bright, 1.2),
             (6 * base, 0.25 * bright, 2.1), (8 * base, 0.08 * bright, 0.7), (10 * base, 0.04 * bright, 1.9)]
    if whine_f:
        parts += [(whine_f, whine_a, 0.0), (whine_f + 0.5, whine_a * 0.5, 1.0)]
    hum = tone(n, parts, lfo=lambda t: 1.0 + 0.05 * math.sin(TAU * 1.5 * t))
    return filt_periodic(hum, ("lp", 2500, 0.7))


def crusher_run(seconds=2.0):
    n = int(SR * seconds)
    r = random.Random(202)
    hum = motor_hum(n, r)
    grind = [0.0] * n
    # jaw strokes: 3 per loop (1.5 Hz eccentric), each a low thump plus a cluster of rock cracks
    for k in range(3):
        start = int((k / 3 + r.uniform(-0.02, 0.02)) * n)
        add_at(grind, thump(int(0.25 * SR), 110, 52, 0.07), start, 0.9, circular=True)
        for _ in range(r.randint(9, 14)):
            g = burst(int(0.04 * SR), r, r.uniform(0.003, 0.012),
                      [("bp", r.uniform(600, 2600), r.uniform(0.8, 2.0))])
            add_at(grind, g, start + int(r.uniform(0.0, 0.35) * SR), r.uniform(0.6, 1.6), circular=True)
    # continuous gritty bed: sparse small crackles everywhere
    for _ in range(140):
        g = burst(int(0.015 * SR), r, r.uniform(0.001, 0.004), [("bp", r.uniform(900, 3500), 1.2)])
        add_at(grind, g, r.randrange(n), r.uniform(0.15, 0.5), circular=True)
    bed = loop_noise(n, r, ("bp", 450, 0.8), ("lp", 1800, 0.7))
    # amplitude-modulate the rubble bed with the jaw cycle (1.5 Hz -> 3 cycles per loop)
    bed = [x * (0.6 + 0.4 * math.sin(TAU * 1.5 * i / SR) ** 2) for i, x in enumerate(bed)]
    grind = filt_periodic(grind, ("lp", 4500, 0.7))
    return mix((hum, 0.40), (grind, 0.55), (bed, 0.45))


def crusher_idle(seconds=2.0):
    n = int(SR * seconds)
    r = random.Random(203)
    # unloaded motor spins faster: a higher rotor whine on top of the same mains hum
    hum = motor_hum(n, r, bright=1.2, whine_f=1450.0, whine_a=0.12)
    air = loop_noise(n, r, ("bp", 1200, 0.7))
    return mix((hum, 1.0), (air, 0.05))


def electric_furnace_run(seconds=2.0):
    n = int(SR * seconds)
    r = random.Random(303)
    # transformer buzz: 120 Hz rich in harmonics (slightly clipped), muffled
    buzz = tone(n, [(120.0, 1.0, 0.0), (240.0, 0.5, 0.6), (360.0, 0.35, 1.3), (480.0, 0.2, 0.2),
                    (600.0, 0.15, 2.2), (720.0, 0.08, 1.0)],
                lfo=lambda t: 1.0 + 0.04 * math.sin(TAU * 0.5 * t))
    buzz = filt_periodic(soft_clip(buzz, 1.6), ("lp", 1600, 0.7))
    sizzle = loop_noise(n, r, ("hp", 3500, 0.7), ("lp", 9000, 0.7))
    sizzle = [x * (0.7 + 0.3 * math.sin(TAU * 1.0 * i / SR + 0.5)) for i, x in enumerate(sizzle)]
    ticks = [0.0] * n
    for _ in range(9):  # thermal expansion ticks of the elements
        g = burst(int(0.02 * SR), r, r.uniform(0.0015, 0.004), [("bp", r.uniform(2500, 5000), 2.0)])
        add_at(ticks, g, r.randrange(n), r.uniform(0.5, 1.2), circular=True)
    for _ in range(120):  # fine crackle in the sizzle
        g = burst(int(0.004 * SR), r, 0.0006, [("hp", 4000, 0.7)])
        add_at(ticks, g, r.randrange(n), r.uniform(0.05, 0.2), circular=True)
    return mix((buzz, 0.35), (sizzle, 0.22), (ticks, 0.45))


def charger_run(seconds=2.0):
    """Shepard-style whine: rises one octave per loop forever, so the loop rises seamlessly."""
    n = int(SR * seconds)
    r = random.Random(404)
    T = seconds
    base = 41.25          # lowest/highest components sit ~5 octaves from the centre: inaudible at the swap
    centre = math.log2(1500.0)
    comps = 9
    # phase offsets chosen so component k at t=T lands exactly on component k+1 at t=0
    ph0 = [0.0] * comps
    for k in range(1, comps):
        ph0[k] = (ph0[k - 1] + base * 2 ** (k - 1) * T / math.log(2)) % 1.0
    out = []
    for i in range(n):
        t = i / SR
        u = t / T
        x = 0.0
        for k in range(comps):
            f = base * 2 ** (k + u)
            ph = ph0[k] + base * 2 ** k * T / math.log(2) * (2 ** u - 1)
            a = math.exp(-0.5 * ((math.log2(f) - centre) / 1.0) ** 2)
            x += a * math.sin(TAU * ph)
        out.append(x)
    # coil buzz: 100 Hz-ish rectified-mains hum (whole cycles in 2 s)
    coil = tone(n, [(100.0, 1.0, 0.0), (200.0, 0.5, 0.4), (300.0, 0.3, 1.0), (400.0, 0.12, 2.0)],
                lfo=lambda t: 1.0 + 0.1 * math.sin(TAU * 2 * t))
    coil = filt_periodic(coil, ("lp", 900, 0.7))
    hiss = loop_noise(n, r, ("bp", 6000, 0.8))
    return mix((out, 0.35), (coil, 0.25), (hiss, 0.03))


# ---------------------------------------------------------------- one-shots

def turbine_breaker_close(seconds=0.6):
    n = int(SR * seconds)
    r = random.Random(501)
    body = thump(n, 120, 48, 0.09, drop=0.03)
    impact = burst(n, r, 0.012, [("bp", 900, 0.9)])
    latch = [0.0] * n
    add_at(latch, burst(int(0.05 * SR), r, 0.004, [("hp", 2000, 0.7)]), int(0.035 * SR), 0.6)
    metal = ring(n, [(612, 0.25, 0.20), (1237, 0.35, 0.28), (1871, 0.25, 0.22), (2953, 0.18, 0.16),
                     (3611, 0.10, 0.12), (4420, 0.06, 0.08)], r)
    return fade_edges(mix((body, 1.0), (impact, 0.9), (latch, 0.7), (metal, 0.28)), fin=0.0005)


def turbine_breaker_open(seconds=0.8):
    n = int(SR * seconds)
    r = random.Random(502)
    body = thump(n, 100, 55, 0.07, drop=0.025)
    impact = burst(n, r, 0.010, [("bp", 1100, 0.9)])
    metal = ring(n, [(1311, 0.3, 0.18), (2087, 0.25, 0.14), (3390, 0.12, 0.10)], r)
    # arc: crackle + hiss + a little 120 Hz buzz, starting as the contacts part (~15 ms in)
    arc = [0.0] * n
    a0 = int(0.015 * SR)
    hiss = filt(noise(n, r), ("hp", 2500, 0.7))
    for i in range(a0, n):
        t = (i - a0) / SR
        env = math.exp(-t / 0.12) * min(1.0, t / 0.003)
        buzz = math.sin(TAU * 120 * t) * (0.5 + 0.5 * math.sin(TAU * 240 * t))
        arc[i] += env * (0.6 * hiss[i] + 0.25 * buzz)
        if r.random() < 0.05 * math.exp(-t / 0.15):
            add_at(arc, burst(int(0.006 * SR), r, 0.0008, [("hp", 1500, 0.7)]), i, r.uniform(0.6, 1.4))
    return fade_edges(mix((body, 0.9), (impact, 0.8), (metal, 0.22), (arc, 0.6)), fin=0.0005)


def motor_trip(seconds=1.5):
    n = int(SR * seconds)
    r = random.Random(601)
    clack = mix((burst(n, r, 0.006, [("hp", 1500, 0.7)]), 1.0),
                (ring(n, [(2210, 0.4, 0.04), (3480, 0.3, 0.03)], r), 0.5))
    # wind-down: hum harmonics with exponentially falling frequency and fading level
    wind = []
    phs = [0.0] * 4
    harm = [(1, 0.25), (2, 1.0), (4, 0.45), (6, 0.25)]
    start = int(0.05 * SR)
    for i in range(n):
        if i < start:
            wind.append(0.0); continue
        t = (i - start) / SR
        speed = math.exp(-t / 0.45)
        x = 0.0
        for k, (h, a) in enumerate(harm):
            phs[k] += 60 * h * (0.15 + 0.85 * speed) / SR
            x += a * math.sin(TAU * phs[k])
        wind.append(x * speed ** 0.8 * min(1.0, t / 0.01))
    wind = filt(wind, ("lp", 1500, 0.7))
    return fade_edges(mix((clack, 0.8), (wind, 0.5)), fin=0.0005, fout=0.05)


def engine_chuff(seconds=0.35):
    n = int(SR * seconds)
    r = random.Random(701)
    body = thump(n, 95, 55, 0.06, drop=0.03)
    puff = burst(n, r, 0.06, [("bp", 700, 0.7), ("lp", 2500, 0.7)], attack=0.012)
    hiss = burst(n, r, 0.05, [("hp", 3000, 0.7)], attack=0.02)
    tick = burst(n, r, 0.002, [("bp", 2400, 1.5)])
    return fade_edges(mix((body, 1.0), (puff, 1.4), (hiss, 0.15), (tick, 0.25)), fin=0.0005, fout=0.03)


def engine_seize(seconds=1.5):
    n = int(SR * seconds)
    r = random.Random(801)
    # stick-slip screech: inharmonic partials, pitch sagging, amplitude chopped irregularly
    screech = [0.0] * n
    modes = [(1830, 1.0), (2710, 0.6), (3460, 0.4), (4890, 0.2)]
    phs = [r.uniform(0, 1) for _ in modes]
    chop, chop_target = 1.0, 1.0
    vib = 0.0
    for i in range(n):
        t = i / SR
        if i % 441 == 0:
            chop_target = r.uniform(0.25, 1.0)
            vib = r.uniform(-1, 1)
        chop += (chop_target - chop) * 0.004
        sag = 1.0 - 0.18 * (t / seconds) + 0.01 * vib
        x = 0.0
        for k, (f, a) in enumerate(modes):
            phs[k] += f * sag / SR
            x += a * math.sin(TAU * phs[k])
        env = min(1.0, t / 0.05) * math.exp(-t / 0.55)
        screech[i] = x * chop * env
    grind = burst(n, r, 0.5, [("bp", 1200, 0.7)], attack=0.03)
    grind = [x * (0.5 + 0.5 * abs(math.sin(TAU * 23 * i / SR))) for i, x in enumerate(grind)]
    hiss = filt(noise(n, r), ("hp", 2500, 0.7), ("lp", 9000, 0.7))
    hiss = [x * min(1.0, (i / SR) / 0.15) * math.exp(-max(0.0, i / SR - 0.2) / 0.6) for i, x in enumerate(hiss)]
    clunk = thump(n, 90, 45, 0.08)
    return fade_edges(mix((screech, 0.30), (grind, 0.45), (hiss, 0.35), (clunk, 0.7)), fin=0.0005, fout=0.08)


def contactor(seconds, bright, seed):
    n = int(SR * seconds)
    r = random.Random(seed)
    hp = 1800 if bright else 700
    hit = burst(n, r, 0.004 if bright else 0.006, [("hp", hp, 0.7)] + ([] if bright else [("lp", 3000, 0.7)]))
    body = thump(n, 180 if bright else 140, 90, 0.025, drop=0.01)
    modes = [(3210, 0.35, 0.035), (4730, 0.25, 0.025), (6150, 0.12, 0.02)] if bright else \
            [(1820, 0.3, 0.03), (2690, 0.18, 0.02)]
    metal = ring(n, modes, r)
    out = mix((hit, 1.0), (body, 0.6), (metal, 0.35 if bright else 0.25))
    # armature bounce: a second, smaller click a few ms later
    add_at(out, burst(int(0.03 * SR), r, 0.002, [("hp", hp, 0.7)]), int((0.009 if bright else 0.012) * SR), 0.35)
    return fade_edges(out, fin=0.0003, fout=0.03)


def scada_click(seconds=0.08):
    n = int(SR * seconds)
    r = random.Random(901)
    beep = tone(n, [(1200, 1.0, 0.0), (2400, 0.12, 0.0)])
    beep = [x * min(1.0, i / (0.002 * SR)) * math.exp(-i / (0.025 * SR)) for i, x in enumerate(beep)]
    tick = burst(n, r, 0.0008, [("hp", 3000, 0.7)])
    return fade_edges(mix((beep, 1.0), (tick, 0.15)), fin=0.0003, fout=0.008)


def scada_alarm(seconds=1.2):
    n = int(SR * seconds)
    seg = n // 6
    out, ph = [], 0.0
    for i in range(n):
        k = min(5, i // seg)
        f = 880.0 if k % 2 == 0 else 660.0
        ph += f / SR
        # soft square: odd harmonics rolling off faster than a true square
        x = sum(math.sin(TAU * h * ph) / h ** 1.6 for h in (1, 3, 5, 7, 9))
        j = i - k * seg
        env = min(1.0, j / (0.006 * SR), (seg - j) / (0.006 * SR)) if k < 5 else \
            min(1.0, j / (0.006 * SR), (n - i) / (0.03 * SR))
        out.append(x * max(0.0, env))
    return filt(out, ("lp", 3500, 0.7))


def arc_zap(seconds=0.5):
    n = int(SR * seconds)
    r = random.Random(1001)
    crack = [0.0] * n
    for _ in range(90):  # dense crackle front, thinning out
        t = r.expovariate(1 / 0.06)
        if t >= seconds - 0.02:
            continue
        g = burst(int(0.01 * SR), r, r.uniform(0.0005, 0.002), [("hp", r.uniform(1200, 4000), 0.7)])
        add_at(crack, g, int(t * SR), r.uniform(0.4, 1.0) * math.exp(-t / 0.12))
    snap = burst(n, r, 0.008, [("hp", 1000, 0.7)])
    # buzzy tail: jittery 180 Hz saw, chopped like an unstable arc
    tail, ph, f = [], 0.0, 180.0
    for i in range(n):
        t = i / SR
        if i % 220 == 0:
            f = 180.0 * r.uniform(0.92, 1.08)
        ph += f / SR
        saw = 2.0 * (ph - math.floor(ph + 0.5))
        gate = 0.6 + 0.4 * math.sin(TAU * 37 * t) * math.sin(TAU * 11 * t)
        tail.append(saw * gate * min(1.0, t / 0.01) * math.exp(-t / 0.16))
    tail = filt(tail, ("lp", 2200, 0.7), ("hp", 120, 0.7))
    hiss = burst(n, r, 0.1, [("hp", 4000, 0.7)])
    out = mix((crack, 1.0), (snap, 0.9), (tail, 0.45), (hiss, 0.2))
    out = soft_clip(normalize_peak(out, 1.0), 2.5)  # tame the crackle spikes so the zap has body
    return fade_edges(out, fin=0.0003, fout=0.04)


# ---------------------------------------------------------------- batch 2: helpers

def check_loop(name, s, win=512):
    """Seam test: the wrap step (last sample -> first) must look like its neighbourhood, both in slope
    (first difference) and curvature (second difference). A click or a phase jump at the seam shows up
    as a step several times larger than anything around it."""
    n = len(s)
    d1 = lambda i: abs(s[i % n] - s[(i - 1) % n])
    d2 = lambda i: abs(s[i % n] - 2 * s[(i - 1) % n] + s[(i - 2) % n])
    seam1 = max(d1(0), d1(1))
    seam2 = max(d2(0), d2(1), d2(2))
    near = [i for i in range(-win, win) if i not in (-1, 0, 1, 2, 3)]
    lim1 = max(d1(i) for i in near)
    lim2 = max(d2(i) for i in near)
    pre = rms([s[i] for i in range(n - 2048, n)])
    post = rms(s[:2048])
    jump_db = abs(db(pre) - db(post))
    ok = seam1 <= 1.5 * lim1 and seam2 <= 1.5 * lim2 and jump_db < 6.0
    if not ok:
        raise SystemExit(f"{name}: loop seam check FAILED (step {seam1:.4f} vs {lim1:.4f}, "
                         f"curv {seam2:.4f} vs {lim2:.4f}, level jump {jump_db:.1f} dB)")
    return seam1 / max(1e-9, lim1)


def loop_env(n, r, points, lo, hi):
    """Smooth random envelope that wraps: `points` random levels, cosine-interpolated circularly."""
    vals = [r.uniform(lo, hi) for _ in range(points)]
    out = []
    for i in range(n):
        u = i * points / n
        k = int(u)
        w = (1 - math.cos(math.pi * (u - k))) / 2
        a, b = vals[k % points], vals[(k + 1) % points]
        out.append(a + (b - a) * w)
    return out


def phase_track(n, f0, mods=()):
    """Phase (in cycles) of f(t) = f0 + sum d*cos(2pi*m*t + p), in closed form: it wraps exactly when
    f0*T and every m*T are whole numbers."""
    out = []
    for i in range(n):
        t = i / SR
        p = f0 * t
        for d, m, ph in mods:
            p += d / (TAU * m) * math.sin(TAU * m * t + ph)
        out.append(p)
    return out


def phase_accum(freqs):
    """Phase (in cycles) of an arbitrary per-sample frequency trajectory (one-shots)."""
    out, p = [], 0.0
    for f in freqs:
        out.append(p)
        p += f / SR
    return out


def harm(ph, amps):
    """Harmonic series on a phase track: sum amps[k-1] * sin(k * theta), via the Chebyshev recurrence."""
    out = []
    for p in ph:
        th = TAU * p
        c2 = 2 * math.cos(th)
        s0, s1, x = 0.0, math.sin(th), 0.0
        for a in amps:
            x += a * s1
            s0, s1 = s1, c2 * s1 - s0
        out.append(x)
    return out


def filt_sweep(sig, kind, f_fn, q=0.707, block=32):
    """Biquad whose centre/cutoff follows f_fn(t) (coefficients refreshed every `block` samples)."""
    f = Biquad(kind, 1000.0, q)
    out = []
    for i, x in enumerate(sig):
        if i % block == 0:
            g = Biquad(kind, max(20.0, min(SR * 0.45, f_fn(i / SR))), q)
            f.b0, f.b1, f.b2, f.a1, f.a2 = g.b0, g.b1, g.b2, g.a1, g.a2
        out.append(f(x))
    return out


def bubble(f0, decay, rise=1.6):
    """Water bubble: a damped sine whose pitch rises as it decays."""
    n = int(decay * 6 * SR)
    out, ph = [], 0.0
    for i in range(n):
        t = i / SR
        ph += f0 * (1 + (rise - 1) * t / (decay * 6)) / SR
        out.append(math.sin(TAU * ph) * math.exp(-t / decay) * min(1.0, i / 30))
    return out


def squeal(r, dur, modes, glide, decay):
    """Stick-slip metal squeal: inharmonic partials sagging in pitch, with a little roughness."""
    n = int(dur * SR)
    phs = [r.random() for _ in modes]
    out = []
    for i in range(n):
        t = i / SR
        sag = 1.0 - glide * t / dur
        x = 0.0
        for k, (f, a) in enumerate(modes):
            phs[k] += f * sag / SR
            x += a * math.sin(TAU * phs[k])
        out.append(x * min(1.0, t / 0.02) * math.exp(-t / decay) * (0.8 + 0.2 * math.sin(TAU * 31 * t)))
    return out


def reverb(s, rt60=1.0, wet=0.35, damp=3500):
    """Small Schroeder reverb (4 parallel combs + 2 allpasses), wet path darkened."""
    n = len(s)
    acc = [0.0] * n
    for d_ms in (29.7, 37.1, 41.1, 43.7):
        D = int(d_ms * SR / 1000)
        g = 10 ** (-3 * D / (rt60 * SR))
        y = [0.0] * n
        for i in range(n):
            y[i] = s[i] + (g * y[i - D] if i >= D else 0.0)
        for i in range(n):
            acc[i] += 0.25 * y[i]
    for d_ms, g in ((5.0, 0.7), (1.7, 0.7)):
        D = int(d_ms * SR / 1000)
        y = [0.0] * n
        for i in range(n):
            xd = acc[i - D] if i >= D else 0.0
            yd = y[i - D] if i >= D else 0.0
            y[i] = -g * acc[i] + xd + g * yd
        acc = y
    acc = filt(acc, ("lp", damp, 0.7))
    return [a * (1 - wet) + b * wet for a, b in zip(s, acc)]


def scatter_bursts(buf, r, count, place, dur, decay, stages, gain, circular=True):
    """Mix `count` small noise bursts into buf. place() -> sample index; decay/gain: (lo, hi);
    stages(r) -> filter chain for each burst."""
    for _ in range(count):
        start = place()
        if start is None:
            continue
        g = burst(int(dur * SR), r, r.uniform(*decay), stages(r))
        add_at(buf, g, start, r.uniform(*gain), circular=circular)


SABER_AMPS = [1 / k ** 1.15 for k in range(1, 37)]
SABER_DETUNE = 92.5 / 90.0


def saber_voice(pa, pb, ps):
    """Energy-blade voice: two detuned buzzy (saw-like) tones plus a sine subharmonic, lightly saturated.
    pa/pb/ps: phase tracks (cycles) of the main tone, the detuned twin and the subharmonic."""
    a, b = harm(pa, SABER_AMPS), harm(pb, SABER_AMPS)
    sub = [math.sin(TAU * p) for p in ps]
    x = soft_clip([0.35 * u + 0.30 * v for u, v in zip(a, b)], 1.4)
    return [u + 0.3 * w for u, w in zip(x, sub)]


# ---------------------------------------------------------------- batch 2: loops

def alloy_furnace_run(seconds=2.0):
    n = int(SR * seconds)
    r = random.Random(1201)
    hum = tone(n, [(120.0, 1.0, 0.0), (240.0, 0.45, 0.7), (360.0, 0.2, 1.9), (480.0, 0.08, 0.4)],
               lfo=lambda t: 1.0 + 0.03 * math.sin(TAU * 0.5 * t))
    hum = filt_periodic(soft_clip(hum, 1.3), ("lp", 1200, 0.7))
    # induction coil whine: ~3.6 kHz with a slow wobble and a detuned twin (beats 1.5 Hz)
    coil = harm(phase_track(n, 3600.0, [(6.0, 3.0, 0.0)]), [1.0, 0.12])
    coil2 = tone(n, [(3601.5, 1.0, 1.0)])
    sizzle = loop_noise(n, r, ("bp", 3600, 4.0))
    return mix((hum, 0.5), (coil, 0.045), (coil2, 0.025), (sizzle, 0.06))


def arc_furnace_run(seconds=3.0):
    n = int(SR * seconds)
    r = random.Random(1202)
    env = loop_env(n, r, 24, 0.35, 1.0)                 # irregular roar level
    buzz = tone(n, [(100.0, 1.0, 0.0), (200.0, 0.5, 0.3), (300.0, 0.4, 1.1), (400.0, 0.25, 2.0),
                    (500.0, 0.2, 0.5), (120.0, 0.8, 1.3), (240.0, 0.4, 0.2), (360.0, 0.35, 2.4),
                    (480.0, 0.2, 0.8), (600.0, 0.15, 1.7)])
    buzz = filt_periodic(soft_clip(buzz, 2.5), ("lp", 3000, 0.7))
    flick = loop_env(n, r, 150, 0.4, 1.0)               # fast arc-length flicker
    buzz = [x * e * f for x, e, f in zip(buzz, env, flick)]
    roar = loop_noise(n, r, ("bp", 700, 0.5), ("lp", 4000, 0.7))
    roar = [x * (0.4 + 0.6 * e) for x, e in zip(roar, env)]
    crack = [0.0] * n
    hiband = lambda r: [("hp", r.uniform(1500, 5000), 0.7)]
    scatter_bursts(crack, r, 650, lambda: r.randrange(n), 0.01, (0.0003, 0.002), hiband, (0.2, 1.0))
    for _ in range(9):                                  # sputters: dense bursts of crackle + a pop
        c = r.randrange(n)
        add_at(crack, thump(int(0.08 * SR), 160, 70, 0.02, drop=0.01), c, r.uniform(0.6, 1.0), circular=True)
        scatter_bursts(crack, r, r.randint(15, 35), lambda: c + int(r.uniform(0, 0.09) * SR), 0.012,
                       (0.0005, 0.003), lambda r: [("bp", r.uniform(700, 3500), 0.9)], (0.6, 1.5))
    crack = [x * (0.5 + 0.5 * e) for x, e in zip(crack, env)]
    out = mix((buzz, 0.35), (roar, 0.6), (crack, 0.45))
    return soft_clip(normalize_peak(out, 1.0), 1.8)     # keep the crest factor down so it can be loud


def pump_run(seconds=2.0):
    n = int(SR * seconds)
    r = random.Random(1203)
    hum = motor_hum(n, r, bright=0.8)
    strokes = [0.0] * n
    for k in range(4):                                  # 2 strokes/s
        s0 = int((k / 4 + r.uniform(-0.005, 0.005)) * n)
        add_at(strokes, thump(int(0.2 * SR), 80, 45, 0.05), s0, 0.6, circular=True)
        add_at(strokes, burst(int(0.4 * SR), r, 0.12, [("lp", 900, 0.7), ("hp", 150, 0.7)], attack=0.05),
               s0, 0.9, circular=True)
        for _ in range(r.randint(6, 10)):
            add_at(strokes, bubble(r.uniform(250, 800), r.uniform(0.01, 0.03)),
                   s0 + int(r.uniform(0.02, 0.3) * SR), r.uniform(0.3, 0.7), circular=True)
        add_at(strokes, burst(int(0.03 * SR), r, 0.003, [("bp", 2200, 1.5)]), s0 + int(0.24 * SR), 0.3,
               circular=True)                           # check-valve tick
    flow = loop_noise(n, r, ("lp", 600, 0.7), ("hp", 80, 0.7))
    flow = [x * (0.7 + 0.3 * math.cos(TAU * 2 * i / SR)) for i, x in enumerate(flow)]
    return mix((hum, 0.45), (strokes, 0.8), (flow, 0.4))


def ore_washer_run(seconds=3.0):
    n = int(SR * seconds)
    r = random.Random(1204)
    rot = 2 / 3                                         # drum turns twice per loop
    hum = motor_hum(n, r, bright=0.7)
    slosh = loop_noise(n, r, ("lp", 900, 0.7), ("hp", 120, 0.7))
    slosh = [x * (0.55 + 0.45 * math.sin(TAU * rot * i / SR) ** 2) for i, x in enumerate(slosh)]
    splash = loop_noise(n, r, ("bp", 1800, 1.0))
    splash = [x * e for x, e in zip(splash, loop_env(n, r, 30, 0.15, 1.0))]
    gravel = [0.0] * n

    def cascade():                                      # rocks tumble mostly as the load cascades
        while True:
            i = r.randrange(n)
            if r.random() < 0.25 + 0.75 * math.sin(math.pi * rot * i / SR) ** 4:
                return i
    scatter_bursts(gravel, r, 750, cascade, 0.012, (0.0008, 0.003),
                   lambda r: [("bp", r.uniform(1200, 4500), 1.5)], (0.2, 1.0))
    for _ in range(14):                                 # bigger stones knocking the shell
        i = cascade()
        add_at(gravel, thump(int(0.06 * SR), 320, 160, 0.015, drop=0.01), i, r.uniform(0.3, 0.6), circular=True)
        add_at(gravel, burst(int(0.03 * SR), r, 0.004, [("bp", r.uniform(600, 1200), 1.2)]), i,
               r.uniform(0.5, 1.0), circular=True)
    bub = [0.0] * n
    for _ in range(30):
        add_at(bub, bubble(r.uniform(300, 1100), r.uniform(0.008, 0.02)), r.randrange(n),
               r.uniform(0.3, 1.0), circular=True)
    return mix((hum, 0.3), (slosh, 0.55), (splash, 0.15), (gravel, 0.5), (bub, 0.2))


def rolling_mill_run(seconds=2.0):
    n = int(SR * seconds)
    r = random.Random(1205)
    rumble = loop_noise(n, r, ("lp", 110, 0.7), ("lp", 110, 0.7))
    drone = tone(n, [(37.5, 1.0, 0.0), (75.0, 0.6, 0.7), (112.5, 0.25, 1.9)],
                 lfo=lambda t: 1.0 + 0.3 * math.sin(TAU * 1.5 * t))
    hum = motor_hum(n, r, bright=0.5)
    hits = [0.0] * n
    for k in range(3):                                  # 1.5 Hz: squeal as the slab bites, then the thunk
        s0 = int((k / 3 + r.uniform(-0.01, 0.01)) * n)
        sq = squeal(r, 0.3, [(1730, 1.0), (2590, 0.5), (3920, 0.25)], 0.07, 0.12)
        add_at(hits, sq, s0, 0.22, circular=True)
        t0 = s0 + int(0.27 * SR)
        add_at(hits, thump(int(0.3 * SR), 90, 40, 0.09), t0, 1.0, circular=True)
        add_at(hits, burst(int(0.1 * SR), r, 0.015, [("bp", 500, 0.8)]), t0, 0.7, circular=True)
        add_at(hits, ring(int(0.35 * SR), [(410, 0.3, 0.12), (870, 0.25, 0.09), (1460, 0.12, 0.06)], r),
               t0, 0.35, circular=True)
    hits = filt_periodic(hits, ("lp", 5000, 0.7))
    return mix((rumble, 3.0), (drone, 0.15), (hum, 0.25), (hits, 0.55))


def wire_drawer_run(seconds=2.0):
    n = int(SR * seconds)
    r = random.Random(1206)
    # spool whir: rises and falls by ~3% once per loop
    whir = harm(phase_track(n, 1100.0, [(30.0, 0.5, 0.0)]), [1.0, 0.35, 0.2, 0.08])
    whir2 = harm(phase_track(n, 550.0, [(15.0, 0.5, 0.0)]), [0.5, 0.0, 0.15])
    whir = filt_periodic([(a + b) * (1 + 0.1 * math.cos(TAU * 0.5 * i / SR))
                          for i, (a, b) in enumerate(zip(whir, whir2))], ("lp", 5000, 0.7))
    hiss = loop_noise(n, r, ("hp", 4500, 0.7), ("lp", 11000, 0.7))
    hiss = [x * (0.8 + 0.2 * math.cos(TAU * 0.5 * i / SR)) for i, x in enumerate(hiss)]
    rasp = loop_noise(n, r, ("bp", 7000, 3.0))
    motor = motor_hum(n, r, bright=0.6)
    return mix((whir, 0.16), (hiss, 0.25), (rasp, 0.12), (motor, 0.3))


def sword_hum(seconds=2.0):
    n = int(SR * seconds)
    r = random.Random(1207)
    # 90 / 92.5 Hz twins beat at 2.5 Hz; tiny vibratos at 1.5 and 2 Hz; all whole cycles in 2 s
    voice = saber_voice(phase_track(n, 90.0, [(0.6, 1.5, 0.0)]),
                        phase_track(n, 92.5, [(0.5, 2.0, 1.0)]),
                        phase_track(n, 45.0, [(0.3, 1.5, 0.0)]))
    voice = filt_periodic(voice, ("lp", 2800, 0.7), ("hp", 35, 0.7))
    voice = [x * (1 + 0.05 * math.sin(TAU * 4 * i / SR)) for i, x in enumerate(voice)]
    shimmer = loop_noise(n, r, ("bp", 4200, 2.0))
    shimmer = [x * (0.6 + 0.4 * math.sin(TAU * 3.5 * i / SR)) for i, x in enumerate(shimmer)]
    glint = tone(n, [(2430.0, 1.0, 0.0), (2433.5, 0.7, 1.0)])   # faint high partial pair, slow shimmer
    return mix((voice, 1.0), (shimmer, 0.05), (glint, 0.012))


def drill_run(seconds=2.0):
    n = int(SR * seconds)
    r = random.Random(1208)
    motor = harm(phase_track(n, 400.0, [(4.0, 3.0, 0.0)]), [1.0, 0.6, 0.45, 0.3, 0.2, 0.12, 0.08, 0.05])
    motor = filt_periodic(motor, ("lp", 4500, 0.7))
    brush = loop_noise(n, r, ("hp", 3000, 0.7))
    brush = [x * (0.5 + 0.5 * math.sin(TAU * 400 * i / SR)) ** 2 for i, x in enumerate(brush)]
    # gear mesh chatter: 267 tooth contacts per loop (133.5 Hz), uneven
    clicks = [burst(int(0.004 * SR), r, 0.0006, [("bp", r.uniform(2200, 3200), 2.0)]) for _ in range(8)]
    chatter = [0.0] * n
    count = 267
    for k in range(count):
        s0 = int(k * n / count) + r.randint(-6, 6)
        a = r.uniform(0.3, 1.0) * (0.7 + 0.3 * math.sin(TAU * 3 * k / count))
        add_at(chatter, clicks[r.randrange(8)], s0, a, circular=True)
    return mix((motor, 0.35), (brush, 0.12), (chatter, 0.3))


def wireless_hum(seconds=2.0):
    n = int(SR * seconds)
    r = random.Random(1209)
    drone = tone(n, [(110.0, 1.0, 0.0), (110.5, 0.8, 1.3), (165.0, 0.5, 0.4), (165.5, 0.4, 2.2),
                     (220.0, 0.35, 0.9), (277.5, 0.18, 1.7), (278.0, 0.15, 0.2), (440.5, 0.1, 2.9),
                     (659.5, 0.06, 0.6)])
    # phasing: mix with a slowly sweeping delayed copy (circular, so it stays periodic)
    ph = []
    for i in range(n):
        d = (1.0 + 1.5 * (1 - math.cos(TAU * 0.5 * i / SR))) * SR / 1000
        j = i - d
        k = math.floor(j)
        fr = j - k
        ph.append(0.6 * drone[i] + 0.5 * (drone[k % n] * (1 - fr) + drone[(k + 1) % n] * fr))
    sparkle = [0.0] * n
    for _ in range(70):
        f = r.uniform(3000, 8000)
        d = r.uniform(0.03, 0.09)
        g = [math.sin(TAU * f * (1 + 0.1 * i / (d * 4 * SR)) * i / SR) * min(1.0, i / (0.005 * SR))
             * math.exp(-i / (d * SR)) for i in range(int(d * 4 * SR))]
        add_at(sparkle, g, r.randrange(n), r.uniform(0.3, 1.0), circular=True)
    air = loop_noise(n, r, ("bp", 2500, 0.7))
    air = [x * (0.6 + 0.4 * math.cos(TAU * 0.5 * i / SR)) for i, x in enumerate(air)]
    return mix((ph, 0.5), (sparkle, 0.08), (air, 0.06))


def transformer_hum(seconds=2.0):
    n = int(SR * seconds)
    r = random.Random(1210)
    hum = tone(n, [(120.0, 1.0, 0.0), (240.0, 0.55, 0.5), (360.0, 0.4, 1.4), (480.0, 0.15, 0.9),
                   (600.0, 0.08, 2.2), (720.0, 0.05, 0.3), (60.0, 0.05, 0.0)])
    hum = filt_periodic(soft_clip(hum, 1.2), ("lp", 1500, 0.7))
    bed = loop_noise(n, r, ("lp", 400, 0.7))
    return mix((hum, 1.0), (bed, 0.04))


def wind_turbine_run(seconds=2.5):
    """Steady nacelle bed heard near the tower: gearbox mesh whine, generator hum, cooling fan, wind on the
    tower. No blade swish here; the game plays wind_turbine_swoosh in step with the visible blades.
    Tones sit on a 0.4 Hz grid so the loop wraps."""
    n = int(SR * seconds)
    r = random.Random(1211)
    gear = harm(phase_track(n, 412.0, [(2.0, 0.4, 0.0)]), [1.0, 0.35, 0.12, 0.05])
    gear = filt_periodic(gear, ("bp", 900, 0.8))
    gen = tone(n, [(120.0, 1.0, 0.0), (240.0, 0.35, 0.7), (360.0, 0.12, 1.3)])
    fan = loop_noise(n, r, ("bp", 2400, 1.4), ("lp", 5000, 0.7))
    fan = [x * (0.8 + 0.2 * math.sin(TAU * 12.0 * i / SR)) for i, x in enumerate(fan)]
    air = loop_noise(n, r, ("bp", 260, 0.5), ("lp", 900, 0.7))
    air = [x * (0.8 + 0.2 * math.sin(TAU * 0.4 * i / SR)) for i, x in enumerate(air)]
    rumble = loop_noise(n, r, ("lp", 70, 0.7), ("lp", 70, 0.7))
    return mix((gear, 0.06), (gen, 0.05), (fan, 0.12), (air, 0.55), (rumble, 2.2))


def wind_turbine_swoosh(seconds=1.6):
    """One blade sweeping down past the tower: a deep 'whoomph'. Band-passed noise whose centre falls
    as the blade passes (a doppler-ish sweep), a low pressure pulse under it, and a thin tip hiss that
    leads slightly."""
    n = int(SR * seconds)
    r = random.Random(1271)
    def env(t, peak=0.55, rise=2.2, fall=0.32):
        return (t / peak) ** rise if t < peak else math.exp(-(t - peak) / fall)
    body = filt_sweep(noise(n, r), "bp", lambda t: 760 - 430 * min(1.0, t / 1.0), q=1.1)
    body = filt(body, ("lp", 1800, 0.7))
    body = [x * env(i / SR) for i, x in enumerate(body)]
    hiss = filt_sweep(noise(n, r), "bp", lambda t: 2600 - 900 * min(1.0, t / 0.8), q=1.6)
    hiss = [x * env(i / SR, peak=0.47, rise=3.0, fall=0.18) for i, x in enumerate(hiss)]
    pulse = [math.sin(TAU * (48 * i / SR + 6 * (i / SR) ** 2)) * env(i / SR, peak=0.58, rise=2.0, fall=0.25)
             for i in range(n)]
    out = mix((body, 1.0), (hiss, 0.22), (pulse, 0.55))
    return fade_edges(reverb(out, rt60=1.2, wet=0.25, damp=2500), fin=0.01, fout=0.1)


def wind_turbine_brake(seconds=3.6):
    """Storm shutdown: pitch motors whirr the blades to feather, the rotor brake bites and squeals,
    and the rotor lock clunks home."""
    n = int(SR * seconds)
    r = random.Random(1281)
    servo_f = [180 + 140 * min(1.0, (i / SR) / 1.2) for i in range(n)]
    servo = harm(phase_accum(servo_f), [1.0, 0.5, 0.3, 0.15, 0.08])
    servo = [x * min(1.0, (i / SR) / 0.05) * (1.0 if i / SR < 1.5 else math.exp(-((i / SR) - 1.5) / 0.08))
             for i, x in enumerate(servo)]
    servo = filt(servo, ("lp", 2500, 0.7))
    hiss = burst(n, r, 0.9, (("bp", 3200, 0.8),), attack=0.2)
    sq = squeal(r, 2.0, [(1870, 1.0), (2915, 0.45), (4310, 0.2)], 0.12, 0.9)
    clunk = mix((thump(int(0.6 * SR), 120, 55, 0.18), 1.0),
                (burst(int(0.4 * SR), r, 0.05, (("bp", 900, 1.0),)), 0.6))
    out = [0.0] * n
    add_at(out, servo, 0, 0.35)
    add_at(out, hiss, int(0.1 * SR), 0.08)
    add_at(out, sq, int(1.2 * SR), 0.22)
    add_at(out, clunk, int(3.0 * SR), 0.9)
    return fade_edges(reverb(out, rt60=0.9, wet=0.2), fin=0.005, fout=0.08)


def hydro_turbine_run(seconds=2.0):
    n = int(SR * seconds)
    r = random.Random(1212)
    water = loop_noise(n, r, ("hp", 250, 0.7), ("lp", 3500, 0.6))
    water = [x * e for x, e in zip(water, loop_env(n, r, 20, 0.75, 1.0))]
    spray = loop_noise(n, r, ("hp", 3000, 0.7), ("lp", 9000, 0.7))
    drone = tone(n, [(50.0, 1.0, 0.0), (100.0, 0.5, 0.6), (150.0, 0.4, 1.2), (300.0, 0.15, 2.0)],
                 lfo=lambda t: 1.0 + 0.06 * math.sin(TAU * 1.5 * t))
    drone = filt_periodic(drone, ("lp", 600, 0.7))
    rumble = loop_noise(n, r, ("lp", 100, 0.7), ("lp", 100, 0.7))
    bub = [0.0] * n
    for _ in range(40):
        add_at(bub, bubble(r.uniform(400, 1400), r.uniform(0.005, 0.015)), r.randrange(n),
               r.uniform(0.3, 1.0), circular=True)
    return mix((water, 0.55), (spray, 0.12), (drone, 0.3), (rumble, 3.0), (bub, 0.1))


def diesel_run(seconds=2.0):
    n = int(SR * seconds)
    r = random.Random(1213)
    accent = [1.0, 0.65, 0.85, 0.7]                    # four-stroke, four-cylinder unevenness
    swing = [0.0, 0.0015, -0.001, 0.0005]
    pulses, rattle = [0.0] * n, [0.0] * n
    for k in range(50):                                 # 25 Hz firing
        s0 = int((k / 25 + swing[k % 4] + r.uniform(-0.0003, 0.0003)) * SR)
        a = accent[k % 4] * r.uniform(0.9, 1.1)
        add_at(pulses, thump(int(0.06 * SR), 75, 38, 0.018, drop=0.008), s0, a, circular=True)
        add_at(pulses, burst(int(0.05 * SR), r, 0.012, [("lp", 700, 0.7)], attack=0.001), s0, 0.8 * a,
               circular=True)
        add_at(rattle, burst(int(0.03 * SR), r, 0.004, [("bp", r.uniform(2000, 3200), 1.2)]),
               s0 + int(0.004 * SR), a, circular=True)
    crank = tone(n, [(12.5, 0.3, 0.0), (25.0, 1.0, 0.5), (50.0, 0.5, 1.1), (75.0, 0.3, 2.0)])
    crank = filt_periodic(crank, ("lp", 300, 0.7))
    tin = loop_noise(n, r, ("hp", 1800, 0.7), ("lp", 6000, 0.7))
    tin = [x * (0.3 + 0.7 * max(0.0, math.sin(TAU * 25 * i / SR)) ** 4) for i, x in enumerate(tin)]
    return mix((pulses, 0.8), (rattle, 0.25), (crank, 0.25), (tin, 0.12))


def condenser_run(seconds=3.0):
    n = int(SR * seconds)
    r = random.Random(1214)
    whine = tone(n, [(540.0, 1.0, 0.0), (540 + 1 / 3, 0.4, 1.0), (1080.0, 0.25, 0.5), (810.0, 0.1, 2.0)],
                 lfo=lambda t: 1.0 + 0.08 * math.sin(TAU * t / 3))
    whine = filt_periodic(whine, ("lp", 2000, 0.7))
    deep = tone(n, [(30.0, 0.5, 0.0), (60.0, 1.0, 0.4), (120.0, 0.7, 1.0), (180.0, 0.2, 2.1), (240.0, 0.25, 0.3)])
    deep = filt_periodic(deep, ("lp", 800, 0.7))
    wind = loop_noise(n, r, ("bp", 300, 0.5))
    wind = [x * (0.85 + 0.15 * math.sin(TAU * (2 / 3) * i / SR)) for i, x in enumerate(wind)]
    rumble = loop_noise(n, r, ("lp", 90, 0.7), ("lp", 90, 0.7))
    return mix((whine, 0.12), (deep, 0.45), (wind, 0.25), (rumble, 2.0))


# ---------------------------------------------------------------- batch 2: one-shots

def saber_from_freqs(freqs):
    return saber_voice(phase_accum(freqs), phase_accum([f * SABER_DETUNE for f in freqs]),
                       phase_accum([f * 0.5 for f in freqs]))


def sword_ignite(seconds=0.8):
    n = int(SR * seconds)
    r = random.Random(1301)
    T = [i / SR for i in range(n)]
    freqs = [90 * (0.3 + 0.7 * (1 - math.exp(-t / 0.06))) * (1 + 0.2 * math.exp(-((t - 0.15) / 0.07) ** 2))
             for t in T]
    amp = [min(1.0, t / 0.01) * (0.8 + 0.5 * math.exp(-((t - 0.14) / 0.08) ** 2)) for t in T]
    voice = filt([x * a for x, a in zip(saber_from_freqs(freqs), amp)], ("lp", 3200, 0.7))
    snap = burst(n, r, 0.006, [("hp", 1500, 0.7)])
    hiss = filt_sweep(noise(n, r), "bp", lambda t: 1500 + 5000 * min(1.0, t / 0.2), 1.2)
    hiss = [x * min(1.0, t / 0.005) * math.exp(-t / 0.18) for x, t in zip(hiss, T)]
    crack = [0.0] * n
    scatter_bursts(crack, r, 30, lambda: int(r.uniform(0, 0.25) * SR), 0.008, (0.0004, 0.0015),
                   lambda r: [("hp", r.uniform(2000, 5000), 0.7)], (0.2, 0.7), circular=False)
    return fade_edges(mix((voice, 1.0), (snap, 0.7), (hiss, 0.8), (crack, 0.4)), fin=0.0005, fout=0.12)


def sword_retract(seconds=0.7):
    n = int(SR * seconds)
    r = random.Random(1302)
    T = [i / SR for i in range(n)]
    freqs = [max(12.0, 92 * (1 + 0.12 * math.exp(-t / 0.03)) * math.exp(-(t / 0.4) ** 1.5)) for t in T]
    amp = [min(1.0, t / 0.005) * max(0.0, 1 - t / seconds) ** 1.3 for t in T]
    voice = filt([x * a for x, a in zip(saber_from_freqs(freqs), amp)], ("lp", 3000, 0.7))
    hiss = filt_sweep(noise(n, r), "bp", lambda t: 800 + 4500 * math.exp(-t / 0.15), 1.2)
    hiss = [x * min(1.0, t / 0.01) * math.exp(-t / 0.15) for x, t in zip(hiss, T)]
    return fade_edges(mix((voice, 1.0), (hiss, 0.5)), fin=0.002, fout=0.05)


def sword_swing(seed, seconds=0.45, centre=0.2, width=0.08, up=0.3):
    n = int(SR * seconds)
    r = random.Random(seed)
    T = [i / SR for i in range(n)]
    bell = lambda t: math.exp(-((t - centre) / width) ** 2)
    # pitch peaks slightly before the loudness peak (approaching blade), then falls past it (doppler)
    freqs = [90 * (0.92 + up * bell(t + 0.3 * width) - 0.08 * math.tanh((t - centre) / width)) for t in T]
    amp = [0.25 + 0.75 * bell(t) for t in T]
    voice = filt([x * a for x, a in zip(saber_from_freqs(freqs), amp)], ("lp", 3500, 0.7))
    whoosh = filt_sweep(noise(n, r), "bp", lambda t: 400 + 2600 * bell(t + 0.2 * width), 1.2)
    whoosh = [x * bell(t) ** 1.5 for x, t in zip(whoosh, T)]
    return fade_edges(mix((voice, 1.0), (whoosh, 0.35)), fin=0.02, fout=0.08)


def sword_hit(seconds=0.4):
    n = int(SR * seconds)
    r = random.Random(1321)
    T = [i / SR for i in range(n)]
    crack = [0.0] * n
    scatter_bursts(crack, r, 70, lambda: min(n - 1, int(r.expovariate(1 / 0.05) * SR)), 0.01,
                   (0.0004, 0.002), lambda r: [("hp", r.uniform(1500, 5000), 0.7)], (0.3, 1.0), circular=False)
    freqs = [90 * r.uniform(0.85, 1.4) if i % 300 == 0 else 0.0 for i in range(n)]
    f = 120.0
    for i in range(n):                                  # jittery, briefly raised pitch while the blades bind
        if freqs[i]:
            f = freqs[i] * (1 + 0.6 * math.exp(-T[i] / 0.05))
        freqs[i] = f
    clash = saber_from_freqs(freqs)
    clash = soft_clip([x * 2.0 * min(1.0, t / 0.002) * math.exp(-t / 0.12) for x, t in zip(clash, T)], 3.0)
    clash = filt(clash, ("lp", 4000, 0.7))
    snap = thump(n, 160, 60, 0.05)
    metal = ring(n, [(1510, 0.3, 0.08), (2270, 0.25, 0.06), (3390, 0.2, 0.05)], r)
    hiss = burst(n, r, 0.15, [("hp", 3000, 0.7)])
    out = mix((crack, 1.0), (clash, 0.6), (snap, 0.6), (metal, 0.3), (hiss, 0.25))
    return fade_edges(soft_clip(normalize_peak(out, 1.0), 2.0), fin=0.0003, fout=0.05)


def arc_gun_charge(seconds=0.6):
    n = int(SR * seconds)
    r = random.Random(1331)
    T = [i / SR for i in range(n)]
    freqs = [300 * 2 ** (3.3 * (t / seconds) ** 1.3) for t in T]
    whine = harm(phase_accum(freqs), [1.0, 0.4, 0.25, 0.12])
    whine = [x * (0.85 + 0.15 * math.sin(TAU * (30 + 40 * t / seconds) * t)) for x, t in zip(whine, T)]
    buzz = harm(phase_accum([100 + 150 * t / seconds for t in T]), [1.0, 0.5, 0.33, 0.25, 0.2])
    hiss = filt(noise(n, r), ("hp", 3000, 0.7))
    u = [t / seconds for t in T]
    env = [min(1.0, t / 0.03) * (0.35 + 0.65 * v) for t, v in zip(T, u)]
    out = [e * (0.6 * w + 0.15 * b + 0.15 * v * h) for e, w, b, h, v in zip(env, whine, buzz, hiss, u)]
    return fade_edges(filt(out, ("lp", 9000, 0.7)), fin=0.001, fout=0.015)


def arc_gun_fire(seconds=0.7):
    n = int(SR * seconds)
    r = random.Random(1341)
    T = [i / SR for i in range(n)]
    boom = thump(n, 140, 40, 0.18, drop=0.03)
    snap = burst(n, r, 0.004)
    body = burst(n, r, 0.03, [("lp", 1200, 0.7)])
    crack = [0.0] * n

    def when():
        t = r.expovariate(1 / 0.09)
        return int(t * SR) if t < seconds - 0.03 else None
    scatter_bursts(crack, r, 220, when, 0.012, (0.0004, 0.003),
                   lambda r: [("hp", r.uniform(800, 5000), 0.7)], (0.4, 1.0), circular=False)
    scatter_bursts(crack, r, 40, when, 0.02, (0.002, 0.006),
                   lambda r: [("bp", r.uniform(400, 1200), 0.9)], (0.5, 1.2), circular=False)
    crack = [x * math.exp(-t / 0.2) for x, t in zip(crack, T)]
    tail, ph, f, gate = [], 0.0, 120.0, 1.0
    for i, t in enumerate(T):                           # heavy, unstable arc buzz
        if i % 180 == 0:
            f = 120.0 * r.uniform(0.85, 1.15)
            gate = r.uniform(0.2, 1.0)
        ph += f / SR
        saw = 2.0 * (ph - math.floor(ph + 0.5))
        tail.append(saw * gate * min(1.0, t / 0.008) * math.exp(-t / 0.25))
    tail = filt(tail, ("lp", 3000, 0.7), ("hp", 90, 0.7))
    thunder = filt(noise(n, r), ("lp", 300, 0.7), ("lp", 300, 0.7))
    thunder = [x * min(1.0, t / 0.02) * math.exp(-t / 0.25) for x, t in zip(thunder, T)]
    hiss = burst(n, r, 0.2, [("hp", 4000, 0.7)])
    out = mix((boom, 0.9), (snap, 0.7), (body, 0.7), (crack, 1.0), (tail, 0.5), (thunder, 4.0), (hiss, 0.2))
    return fade_edges(soft_clip(normalize_peak(out, 1.0), 3.0), fin=0.0003, fout=0.06)


def drill_start(seconds=0.4):
    n = int(SR * seconds)
    r = random.Random(1351)
    T = [i / SR for i in range(n)]
    speed = [1 - math.exp(-t / 0.12) for t in T]
    motor = harm(phase_accum([400 * s for s in speed]), [1.0, 0.6, 0.45, 0.3, 0.2, 0.12, 0.08, 0.05])
    motor = filt([x * (0.3 + 0.7 * s) * min(1.0, t / 0.01) for x, s, t in zip(motor, speed, T)], ("lp", 4500, 0.7))
    clicks = [burst(int(0.004 * SR), r, 0.0006, [("bp", r.uniform(2200, 3200), 2.0)]) for _ in range(8)]
    chatter = [0.0] * n
    p = 0.0
    for i, s in enumerate(speed):                       # tooth contacts at 133.5 Hz x speed
        p += 133.5 * s / SR
        if p >= 1.0:
            p -= 1.0
            add_at(chatter, clicks[r.randrange(8)], i, r.uniform(0.3, 1.0) * (0.5 + 0.5 * s))
    trigger = burst(n, r, 0.002, [("hp", 2500, 0.7)])
    return fade_edges(mix((motor, 0.35), (chatter, 0.3), (trigger, 0.3)), fin=0.0005, fout=0.02)


def breaker_trip(seconds=1.0):
    n = int(SR * seconds)
    r = random.Random(1361)
    T = [i / SR for i in range(n)]
    body = thump(n, 130, 45, 0.12, drop=0.03)
    impact = burst(n, r, 0.015, [("bp", 800, 0.8)])
    hit = burst(n, r, 0.005, [("hp", 2000, 0.7)])
    metal = ring(n, [(480, 0.3, 0.25), (1093, 0.35, 0.3), (1720, 0.25, 0.22), (2650, 0.18, 0.18),
                     (3980, 0.1, 0.1)], r)
    latch = [0.0] * n
    add_at(latch, burst(int(0.05 * SR), r, 0.004, [("hp", 1500, 0.7)]), int(0.06 * SR), 0.6)
    for _ in range(6):                                  # spring / linkage rattle
        add_at(latch, burst(int(0.02 * SR), r, 0.002, [("bp", r.uniform(1500, 3500), 1.5)]),
               int(r.uniform(0.12, 0.4) * SR), r.uniform(0.15, 0.4))
    a0 = int(0.01 * SR)                                 # arc flash as the contacts part
    hiss = filt(noise(n, r), ("hp", 2500, 0.7))
    flash = filt(noise(n, r), ("lp", 1500, 0.7))
    arc = [0.0] * n
    for i in range(a0, n):
        t = (i - a0) / SR
        env = math.exp(-t / 0.18) * min(1.0, t / 0.002)
        buzz = math.sin(TAU * 120 * t) * (0.5 + 0.5 * math.sin(TAU * 240 * t))
        arc[i] += env * (0.6 * hiss[i] + 0.3 * buzz) + 0.8 * flash[i] * math.exp(-t / 0.05) * min(1.0, t / 0.002)
        if r.random() < 0.06 * math.exp(-t / 0.2):
            add_at(arc, burst(int(0.006 * SR), r, 0.0008, [("hp", 1500, 0.7)]), i, r.uniform(0.6, 1.4))
    out = mix((body, 1.0), (impact, 1.0), (hit, 0.6), (metal, 0.25), (latch, 0.6), (arc, 0.7))
    return fade_edges(out, fin=0.0005, fout=0.1)


def wireless_surge(seconds=1.2):
    n = int(SR * seconds)
    r = random.Random(1371)
    T = [i / SR for i in range(n)]
    dry_len = 0.7
    freqs = [600 * 2 ** (-1.0 * min(1.0, t / dry_len) + 0.5 * math.sin(TAU * 7 * t) * max(0.0, 1 - t / dry_len)
                         + 0.8 * math.exp(-t / 0.05)) for t in T]
    v1 = harm(phase_accum(freqs), [1.0, 0.5, 0.33, 0.25, 0.2])
    v2 = harm(phase_accum([f * 1.498 for f in freqs]), [1.0, 0.3, 0.15])
    env = [min(1.0, t / 0.005) * max(0.0, 1 - t / dry_len) ** 1.5 for t in T]
    dry = [e * (0.6 * a + 0.35 * b) * (0.7 + 0.3 * math.sin(TAU * 37 * t))
           for e, a, b, t in zip(env, v1, v2, T)]
    crack = [0.0] * n
    scatter_bursts(crack, r, 45, lambda: int(r.uniform(0, 0.5) * SR), 0.01, (0.0004, 0.002),
                   lambda r: [("hp", r.uniform(2000, 6000), 0.7)], (0.3, 1.0), circular=False)
    dry = filt(mix((dry, 1.0), (crack, 0.5)), ("lp", 7000, 0.7))
    return fade_edges(reverb(dry, rt60=1.0, wet=0.45), fin=0.0005, fout=0.15)


def transformer_buzz_overload(seconds=1.5):
    n = int(SR * seconds)
    r = random.Random(1381)
    T = [i / SR for i in range(n)]
    hum = tone(n, [(120.0, 1.0, 0.0), (240.0, 0.55, 0.5), (360.0, 0.4, 1.4), (480.0, 0.15, 0.9),
                   (60.0, 0.1, 0.0), (600.0, 0.1, 2.2)])
    out = []
    for x, t in zip(hum, T):
        u = t / seconds
        swell = 0.3 + 0.7 * math.sin(0.5 * math.pi * min(1.0, t / 1.1)) ** 2
        d = 1 + 4 * u                                   # drive rises: the core saturates, buzz goes ragged
        out.append(math.tanh(x * swell * d) * (0.6 + 0.4 * swell))
    rattle = filt(noise(n, r), ("bp", 1800, 1.0))      # loose laminations chattering at 120 Hz
    rattle = [x * max(0.0, math.sin(TAU * 120 * t)) ** 6 * (t / seconds) ** 1.5 for x, t in zip(rattle, T)]
    crack = [0.0] * n

    def when():
        i = r.randrange(n)
        return i if r.random() < (i / n) ** 2 else None
    scatter_bursts(crack, r, 160, when, 0.01, (0.0004, 0.002),
                   lambda r: [("hp", r.uniform(1500, 5000), 0.7)], (0.3, 1.0), circular=False)
    out = filt(mix((out, 0.8), (rattle, 1.2), (crack, 0.5)), ("lp", 4500, 0.7))
    return fade_edges(out, fin=0.05, fout=0.3)


def pipe_click(seconds=0.15):
    n = int(SR * seconds)
    r = random.Random(1391)
    tick = burst(n, r, 0.0012, [("hp", 3000, 0.7)])
    metal = ring(n, [(3820, 0.4, 0.025), (5310, 0.3, 0.018), (7170, 0.15, 0.012)], r)
    body = thump(n, 600, 300, 0.006, drop=0.003)
    out = mix((tick, 1.0), (metal, 0.3), (body, 0.3))
    add_at(out, burst(int(0.02 * SR), r, 0.0008, [("hp", 3000, 0.7)]), int(0.006 * SR), 0.3)
    return fade_edges(out, fin=0.0002, fout=0.02)


def tank_slosh(seconds=0.6):
    n = int(SR * seconds)
    r = random.Random(1392)
    T = [i / SR for i in range(n)]
    bump = lambda t, c, w: math.exp(-((t - c) / w) ** 2)
    env = [bump(t, 0.13, 0.08) + 0.6 * bump(t, 0.36, 0.1) for t in T]
    wash = filt_sweep(noise(n, r), "bp", lambda t: 300 + 600 * bump(t, 0.15, 0.1) + 250 * bump(t, 0.38, 0.1), 1.5)
    body = filt(noise(n, r), ("lp", 500, 0.7))
    liquid = [e * (w + 0.6 * b) for e, w, b in zip(env, wash, body)]
    bub = [0.0] * n
    for _ in range(14):
        c = r.choice([0.13, 0.36])
        add_at(bub, bubble(r.uniform(250, 900), r.uniform(0.008, 0.025)),
               int(max(0.0, r.gauss(c, 0.06)) * SR), r.uniform(0.3, 1.0))
    mass = [0.0] * n
    add_at(mass, thump(int(0.3 * SR), 70, 50, 0.08, drop=0.05), int(0.06 * SR))
    return fade_edges(mix((liquid, 1.0), (bub, 0.35), (mass, 0.3)), fin=0.01, fout=0.06)


# ---------------------------------------------------------------- main

LOOPS = {
    # name: (generator, target RMS dBFS)
    "turbine_run": (turbine_run, -13.0),
    "crusher_run": (crusher_run, -13.0),
    "crusher_idle": (crusher_idle, -16.0),
    "electric_furnace_run": (electric_furnace_run, -14.0),
    "charger_run": (charger_run, -15.0),
}

ONESHOTS = {
    "turbine_breaker_close": turbine_breaker_close,
    "turbine_breaker_open": turbine_breaker_open,
    "motor_trip": motor_trip,
    "engine_chuff": engine_chuff,
    "engine_seize": engine_seize,
    "capacitor_step_in": lambda: contactor(0.25, True, 1101),
    "capacitor_step_out": lambda: contactor(0.25, False, 1102),
    "scada_click": scada_click,
    "scada_alarm": scada_alarm,
    "arc_zap": arc_zap,
}

# batch 2: processing machines, generators, tools and weapons
LOOPS_2 = {
    "alloy_furnace_run": (alloy_furnace_run, -15.0),
    "arc_furnace_run": (arc_furnace_run, -13.0),
    "pump_run": (pump_run, -14.0),
    "ore_washer_run": (ore_washer_run, -14.0),
    "rolling_mill_run": (rolling_mill_run, -13.0),
    "wire_drawer_run": (wire_drawer_run, -15.0),
    "sword_hum": (sword_hum, -15.0),
    "drill_run": (drill_run, -14.0),
    "wireless_hum": (wireless_hum, -16.0),
    "transformer_hum": (transformer_hum, -16.0),
    "wind_turbine_run": (wind_turbine_run, -14.0),
    "hydro_turbine_run": (hydro_turbine_run, -13.0),
    "diesel_run": (diesel_run, -13.0),
    "condenser_run": (condenser_run, -15.0),
}

ONESHOTS_2 = {
    "sword_ignite": sword_ignite,
    "sword_retract": sword_retract,
    "sword_swing1": lambda: sword_swing(1311, centre=0.2, width=0.08, up=0.3),
    "sword_swing2": lambda: sword_swing(1312, centre=0.17, width=0.07, up=0.4),
    "sword_swing3": lambda: sword_swing(1313, centre=0.23, width=0.09, up=0.22),
    "sword_hit": sword_hit,
    "arc_gun_charge": arc_gun_charge,
    "arc_gun_fire": arc_gun_fire,
    "drill_start": drill_start,
    "breaker_trip": breaker_trip,
    "wireless_surge": wireless_surge,
    "transformer_buzz_overload": transformer_buzz_overload,
    "pipe_click": pipe_click,
    "tank_slosh": tank_slosh,
    "wind_turbine_swoosh": wind_turbine_swoosh,
    "wind_turbine_brake": wind_turbine_brake,
}

# ---------------------------------------------------------------- batch 3: power tools and devices

def chainsaw_idle(seconds=2.0):
    """Electric chainsaw idling: brushless motor whir, chain slapping the bar, a light rattle."""
    n = int(SR * seconds)
    r = random.Random(1401)
    motor = harm(phase_track(n, 186.0, [(3.0, 1.5, 0.0)]), [1.0, 0.55, 0.35, 0.2, 0.12, 0.07])
    motor = filt_periodic(motor, ("lp", 3000, 0.7))
    whine = tone(n, [(1488.0, 1.0, 0.0)], lfo=lambda t: 0.7 + 0.3 * math.sin(TAU * 1.5 * t))
    chain = [0.0] * n
    scatter_bursts(chain, r, 260, lambda: r.randrange(n), 0.006, (0.0005, 0.0015),
                   lambda r: [("bp", r.uniform(2500, 5500), 2.0)], (0.1, 0.4))
    air = loop_noise(n, r, ("bp", 700, 0.6))
    return mix((motor, 0.25), (whine, 0.03), (chain, 0.35), (air, 0.12))


def chainsaw_cut(seconds=2.0):
    """Biting into wood: the motor drops and strains, the chain screams, chips spray."""
    n = int(SR * seconds)
    r = random.Random(1402)
    motor = harm(phase_track(n, 151.0, [(9.0, 2.0, 0.0), (4.0, 1.0, 1.0)]), [1.0, 0.8, 0.6, 0.45, 0.3, 0.2, 0.12])
    motor = filt_periodic(soft_clip(motor, 1.6), ("lp", 4000, 0.7))
    grind = loop_noise(n, r, ("bp", 2600, 0.9), ("lp", 7000, 0.7))
    grind = [x * e for x, e in zip(grind, loop_env(n, r, 40, 0.55, 1.0))]
    chips = [0.0] * n
    scatter_bursts(chips, r, 900, lambda: r.randrange(n), 0.008, (0.0006, 0.002),
                   lambda r: [("bp", r.uniform(1500, 6000), 1.4)], (0.2, 1.0))
    low = loop_noise(n, r, ("lp", 220, 0.7))
    return mix((motor, 0.35), (grind, 0.5), (chips, 0.4), (low, 0.6))


def tree_fall(seconds=3.6):
    """Timber: fibres creak and tear, the crown rushes through the air, the trunk hits the ground."""
    n = int(SR * seconds)
    r = random.Random(1403)
    creak = []
    ph = 0.0
    for i in range(n):
        t = i / SR
        f = 210 + 90 * math.sin(TAU * 0.9 * t) + 40 * math.sin(TAU * 3.1 * t)
        ph += f / SR
        rough = 0.6 + 0.4 * math.sin(TAU * 23 * t + 2 * math.sin(TAU * 7 * t))
        env = min(1.0, t / 0.25) * (1.0 if t < 1.3 else math.exp(-(t - 1.3) / 0.12))
        creak.append((2.0 * (ph - math.floor(ph + 0.5))) * rough * env)
    creak = filt(creak, ("bp", 600, 1.2), ("lp", 2500, 0.7))
    snaps = [0.0] * n
    for _ in range(18):
        t = r.uniform(0.3, 1.5)
        add_at(snaps, burst(int(0.05 * SR), r, r.uniform(0.004, 0.012), [("bp", r.uniform(800, 3000), 1.0)]), int(t * SR),
               r.uniform(0.3, 1.0))
    rush = burst(n, r, 0.45, (("bp", 2400, 0.6),), attack=0.5)
    rush = [0.0] * int(1.0 * SR) + rush[: n - int(1.0 * SR)]
    impact = [0.0] * n
    hit = int(2.15 * SR)
    add_at(impact, thump(int(1.2 * SR), 90, 38, 0.35, drop=0.05), hit, 1.0)
    add_at(impact, burst(int(0.8 * SR), r, 0.18, [("lp", 1800, 0.7)]), hit, 0.7)
    for _ in range(40):                                   # branches snapping, leaves settling
        add_at(impact, burst(int(0.03 * SR), r, r.uniform(0.003, 0.01), [("bp", r.uniform(1200, 5000), 1.3)]),
               hit + int(r.expovariate(1 / 0.25) * SR), r.uniform(0.1, 0.5))
    out = mix((creak, 0.5), (snaps, 0.45), (rush, 0.35), (impact, 1.0))
    return fade_edges(reverb(out, rt60=1.4, wet=0.2), fin=0.01, fout=0.15)


def jetpack_thrust(seconds=2.0):
    """Twin ducted fans: blade-pass whine on a rushing roar, beating slightly against each other."""
    n = int(SR * seconds)
    r = random.Random(1404)
    fan_a = harm(phase_track(n, 520.0, [(6.0, 0.5, 0.0)]), [1.0, 0.4, 0.15])
    fan_b = harm(phase_track(n, 527.5, [(4.0, 1.0, 1.3)]), [1.0, 0.4, 0.15])
    fans = filt_periodic([a + b for a, b in zip(fan_a, fan_b)], ("bp", 1100, 0.6))
    roar = loop_noise(n, r, ("lp", 2200, 0.7), ("hp", 90, 0.7))
    roar = [x * e for x, e in zip(roar, loop_env(n, r, 12, 0.75, 1.0))]
    hiss = loop_noise(n, r, ("bp", 5500, 0.8))
    motor = harm(phase_track(n, 260.0), [1.0, 0.3])
    return mix((fans, 0.12), (roar, 0.8), (hiss, 0.12), (motor, 0.04))


def meter_beep(seconds=0.12):
    n = int(SR * seconds)
    return fade_edges(tone(n, [(2400.0, 1.0, 0.0), (4800.0, 0.15, 0.0)]), fin=0.004, fout=0.03)


def tesla_zap(seconds=0.9):
    """A big Tesla discharge: a hard crack, a ripping buzz as the channel stays lit, and a sizzle."""
    n = int(SR * seconds)
    r = random.Random(1405)
    crack = [0.0] * n
    for _ in range(160):
        t = r.expovariate(1 / 0.08)
        if t >= seconds - 0.05:
            continue
        add_at(crack, burst(int(0.012 * SR), r, r.uniform(0.0004, 0.0018), [("hp", r.uniform(900, 5000), 0.7)]), int(t * SR),
               r.uniform(0.4, 1.0) * math.exp(-t / 0.18))
    snap = burst(n, r, 0.012, [("hp", 600, 0.7)])
    boom = thump(n, 160, 50, 0.12, drop=0.02)
    buzz, ph = [], 0.0
    for i in range(n):
        t = i / SR
        f = 240.0 * (1 + 0.06 * math.sin(TAU * 17 * t))
        ph += f / SR
        sq = 1.0 if (ph % 1.0) < 0.5 else -1.0
        buzz.append(sq * min(1.0, t / 0.005) * math.exp(-t / 0.22) * (0.7 + 0.3 * r.random()))
    buzz = filt(buzz, ("lp", 3500, 0.7))
    out = mix((crack, 1.0), (snap, 0.9), (boom, 0.6), (buzz, 0.35))
    out = soft_clip(normalize_peak(out, 1.0), 2.2)
    return fade_edges(reverb(out, rt60=0.8, wet=0.2), fin=0.0003, fout=0.06)


def tesla_hum(seconds=2.0):
    """A charged coil: harsh 120 Hz corona buzz and occasional small crackles."""
    n = int(SR * seconds)
    r = random.Random(1406)
    buzz = harm(phase_track(n, 120.0, [(1.0, 1.0, 0.0)]), [1.0, 0.0, 0.45, 0.0, 0.3, 0.0, 0.2, 0.0, 0.14, 0.0, 0.1])
    buzz = filt_periodic(buzz, ("lp", 3500, 0.7))
    crackle = [0.0] * n
    scatter_bursts(crackle, r, 70, lambda: r.randrange(n), 0.01, (0.0004, 0.0015),
                   lambda r: [("hp", r.uniform(2000, 6000), 0.7)], (0.2, 1.0))
    hiss = loop_noise(n, r, ("hp", 6000, 0.7))
    return mix((buzz, 0.3), (crackle, 0.5), (hiss, 0.05))


def induction_hum(seconds=2.0):
    """Resonant inductive charger: a soft high whine over mains hum, gently beating."""
    n = int(SR * seconds)
    r = random.Random(1407)
    whine = tone(n, [(3150.0, 1.0, 0.0), (3152.0, 0.8, 0.0), (6300.0, 0.15, 0.0)])
    hum = harm(phase_track(n, 120.0), [1.0, 0.4, 0.1])
    shimmer = loop_noise(n, r, ("bp", 3150, 3.0))
    return mix((whine, 0.06), (hum, 0.25), (shimmer, 0.1))


def electrolyzer_run(seconds=3.0):
    """Gas streaming off the electrodes: a dense fizz of small bubbles, the odd big one, and the rectifier."""
    n = int(SR * seconds)
    r = random.Random(1408)
    bub = [0.0] * n
    for _ in range(420):
        add_at(bub, bubble(r.uniform(900, 2600), r.uniform(0.002, 0.006)), r.randrange(n), r.uniform(0.2, 1.0), circular=True)
    for _ in range(25):
        add_at(bub, bubble(r.uniform(250, 600), r.uniform(0.01, 0.025)), r.randrange(n), r.uniform(0.5, 1.0), circular=True)
    fizz = loop_noise(n, r, ("bp", 4500, 0.9))
    rect = harm(phase_track(n, 360.0), [1.0, 0.5, 0.2])
    return mix((bub, 0.45), (fizz, 0.12), (rect, 0.03))


def fuel_cell_run(seconds=2.5):
    """Fuel cell stack: the air blower, an inverter's faint whine, and a periodic purge hiss."""
    n = int(SR * seconds)
    r = random.Random(1409)
    blower = loop_noise(n, r, ("bp", 1300, 0.7), ("lp", 4000, 0.7))
    blade = harm(phase_track(n, 412.0, [(2.0, 0.8, 0.0)]), [1.0, 0.3])
    inverter = tone(n, [(4000.0, 1.0, 0.0)], lfo=lambda t: 0.8 + 0.2 * math.sin(TAU * 0.4 * t))
    purge = [0.0] * n
    add_at(purge, burst(int(0.6 * SR), r, 0.15, [("hp", 2500, 0.7)], attack=0.03), int(1.6 * SR), 1.0, circular=True)
    return mix((blower, 0.45), (blade, 0.05), (inverter, 0.015), (purge, 0.25))


LOOPS_3 = {
    "chainsaw_idle": (chainsaw_idle, -16.0),
    "chainsaw_cut": (chainsaw_cut, -12.0),
    "jetpack_thrust": (jetpack_thrust, -13.0),
    "tesla_hum": (tesla_hum, -18.0),
    "induction_hum": (induction_hum, -20.0),
    "electrolyzer_run": (electrolyzer_run, -16.0),
    "fuel_cell_run": (fuel_cell_run, -18.0),
}

ONESHOTS_3 = {
    "tree_fall": tree_fall,
    "meter_beep": meter_beep,
    "tesla_zap": tesla_zap,
}


def upgrade_install(seconds=0.7):
    """Module seated: a mechanical clack, then a rising synth chirp as it links up."""
    n = int(SR * seconds)
    r = random.Random(1501)
    clack = mix((burst(n, r, 0.012, [("bp", 2200, 1.2)]), 1.0), (thump(n, 300, 160, 0.03, drop=0.01), 0.5))
    chirp = []
    ph = 0.0
    for i in range(n):
        t = i / SR - 0.08
        if t < 0:
            chirp.append(0.0)
            continue
        f = 700 * 2 ** (t / 0.18)
        ph += f / SR
        chirp.append(math.sin(TAU * ph) * math.exp(-t / 0.16) * min(1.0, t / 0.01))
    out = mix((clack, 0.9), (chirp, 0.35))
    return fade_edges(reverb(out, rt60=0.6, wet=0.2), fin=0.0005, fout=0.05)


def upgrade_remove(seconds=0.6):
    """Module unseated: a release click and a falling chirp."""
    n = int(SR * seconds)
    r = random.Random(1502)
    click = burst(n, r, 0.008, [("bp", 3000, 1.5)])
    chirp, ph = [], 0.0
    for i in range(n):
        t = i / SR - 0.03
        if t < 0:
            chirp.append(0.0)
            continue
        f = 1400 * 2 ** (-t / 0.15)
        ph += f / SR
        chirp.append(math.sin(TAU * ph) * math.exp(-t / 0.12) * min(1.0, t / 0.01))
    return fade_edges(reverb(mix((click, 0.8), (chirp, 0.3)), rt60=0.5, wet=0.2), fin=0.0005, fout=0.05)


def wire_shock(seconds=0.6):
    """Touching a live wire: a sharp snap and a short angry 60 Hz buzz."""
    n = int(SR * seconds)
    r = random.Random(1503)
    snap = burst(n, r, 0.006, [("hp", 1500, 0.7)])
    buzz, ph = [], 0.0
    for i in range(n):
        t = i / SR
        ph += 120.0 / SR
        sq = 1.0 if (ph % 1.0) < 0.5 else -1.0
        buzz.append(sq * math.exp(-t / 0.14) * (0.6 + 0.4 * r.random()))
    buzz = filt(buzz, ("lp", 2500, 0.7))
    crack = [0.0] * n
    for _ in range(40):
        t = r.expovariate(1 / 0.05)
        if t < seconds - 0.02:
            add_at(crack, burst(int(0.008 * SR), r, 0.001, [("hp", 2500, 0.7)]), int(t * SR), r.uniform(0.3, 1.0))
    out = soft_clip(normalize_peak(mix((snap, 1.0), (buzz, 0.5), (crack, 0.6)), 1.0), 1.8)
    return fade_edges(out, fin=0.0003, fout=0.05)


def wrench(seconds=0.35):
    """A ratchet: a quick run of clicks off a steel pawl."""
    n = int(SR * seconds)
    r = random.Random(1504)
    out = [0.0] * n
    for k in range(5):
        t = 0.02 + k * 0.045
        add_at(out, burst(int(0.04 * SR), r, 0.004, [("bp", r.uniform(3500, 4500), 2.0)]), int(t * SR), 1.0 - k * 0.12)
        add_at(out, ring(int(0.06 * SR), [(2900, 0.3, 0.02), (5100, 0.15, 0.012)], r), int(t * SR), 0.4)
    return fade_edges(out, fin=0.0003, fout=0.03)


ONESHOTS_3.update({"upgrade_install": upgrade_install, "upgrade_remove": upgrade_remove, "wire_shock": wire_shock, "wrench": wrench})


def laser_engraver_run(seconds=2.0):
    """Fibre laser at work: a pure high whine from the galvo/driver, the raster stepper ticking,
    a soft extraction fan, and the sizzle of material ablating in short bursts."""
    n = int(SR * seconds)
    r = random.Random(1601)
    whine = tone(n, [(2500.0, 1.0, 0.0), (5000.0, 0.2, 0.0)], lfo=lambda t: 0.75 + 0.25 * math.sin(TAU * 4.0 * t))
    stepper = harm(phase_track(n, 220.0, [(6.0, 1.5, 0.0)]), [1.0, 0.6, 0.4, 0.25])
    stepper = filt_periodic(stepper, ("bp", 1200, 1.0))
    fan = loop_noise(n, r, ("bp", 700, 0.6))
    sizzle = loop_noise(n, r, ("hp", 3500, 0.7))
    sizzle = [x * e for x, e in zip(sizzle, loop_env(n, r, 16, 0.0, 1.0))]
    ticks = [0.0] * n
    scatter_bursts(ticks, r, 60, lambda: r.randrange(n), 0.008, (0.0006, 0.0015), lambda r: [("bp", r.uniform(3000, 6000), 2.0)], (0.2, 0.7))
    return mix((whine, 0.03), (stepper, 0.08), (fan, 0.3), (sizzle, 0.25), (ticks, 0.3))


LOOPS_3["laser_engraver_run"] = (laser_engraver_run, -18.0)


def radio_key(seconds=0.12):
    """The PTT key: a plastic click and a short chirp from the radio."""
    n = int(SR * seconds)
    r = random.Random(1701)
    click = burst(n, r, 0.004, [("bp", 2500, 1.5)])
    chirp = tone(n, [(1750.0, 1.0, 0.0)], lfo=lambda t: (1.0 if 0.015 < t < 0.07 else 0.0))
    chirp = filt(chirp, ("bp", 1750, 3.0))
    return fade_edges(mix((click, 0.8), (chirp, 0.35)), fin=0.0005, fout=0.02)


def radio_squelch(seconds=0.32):
    """End of transmission: the classic squelch tail, a burst of band-limited static cut off sharply."""
    n = int(SR * seconds)
    r = random.Random(1702)
    hiss = filt(noise(n, r), ("bp", 1800, 0.6), ("hp", 400, 0.7))
    env = [min(1.0, (i / SR) / 0.01) * (1.0 if i / SR < 0.22 else math.exp(-((i / SR) - 0.22) / 0.015)) for i in range(n)]
    return fade_edges([x * e for x, e in zip(hiss, env)], fin=0.0005, fout=0.01)


ONESHOTS_3.update({"radio_key": radio_key, "radio_squelch": radio_squelch})


def pumpjack_run(seconds=4.0):
    """A pumpjack: slow gearbox grind with the beam's rhythm (two strokes per loop), a creak at the top of
    each stroke and the clunk of the rod at the bottom."""
    n = int(SR * seconds)
    r = random.Random(1801)
    motor = harm(phase_track(n, 50.0, [(0.5, 3.0, 0.0)]), [1.0, 0.6, 0.4, 0.25, 0.15])
    motor = filt_periodic(motor, ("lp", 900, 0.7))
    grind = loop_noise(n, r, ("bp", 300, 0.7))
    grind = [x * (0.6 + 0.4 * math.sin(TAU * 0.5 * i / SR) ** 2) for i, x in enumerate(grind)]
    hits = [0.0] * n
    for k in range(2):
        top = int((k * 2.0 + 0.4) * SR)
        bottom = int((k * 2.0 + 1.4) * SR)
        add_at(hits, squeal(r, 0.45, [(420, 1.0), (760, 0.4)], 0.15, 0.25), top, 0.25, circular=True)
        add_at(hits, mix((thump(int(0.4 * SR), 120, 60, 0.12), 1.0), (burst(int(0.2 * SR), r, 0.03, [("bp", 700, 1.0)]), 0.5)),
               bottom, 0.6, circular=True)
    return mix((motor, 0.3), (grind, 0.3), (hits, 0.8))


def refinery_run(seconds=3.0):
    """Refinery: burner roar under the column, liquid bubbling in the trays, and a soft steam hiss."""
    n = int(SR * seconds)
    r = random.Random(1802)
    roar = loop_noise(n, r, ("lp", 400, 0.7), ("hp", 40, 0.7))
    roar = [x * e for x, e in zip(roar, loop_env(n, r, 10, 0.7, 1.0))]
    bub = [0.0] * n
    for _ in range(160):
        add_at(bub, bubble(r.uniform(180, 700), r.uniform(0.006, 0.02)), r.randrange(n), r.uniform(0.2, 1.0), circular=True)
    hiss = loop_noise(n, r, ("hp", 3000, 0.7))
    return mix((roar, 0.7), (bub, 0.35), (hiss, 0.08))


LOOPS_3["pumpjack_run"] = (pumpjack_run, -16.0)
LOOPS_3["refinery_run"] = (refinery_run, -17.0)


# events whose sounds list holds several files (Minecraft picks one at random)
VARIANTS = {"sword_swing": ["sword_swing1", "sword_swing2", "sword_swing3"]}

ALL_LOOPS = {**LOOPS, **LOOPS_2, **LOOPS_3}
ALL_ONESHOTS = {**ONESHOTS, **ONESHOTS_2, **ONESHOTS_3}

# a near-continuous square-ish tone is far louder than a transient at the same peak; keep the alarm civil
ONESHOT_PEAK = {"scada_alarm": 10 ** (-6 / 20),
                "meter_beep": 10 ** (-12 / 20),
                "radio_key": 10 ** (-8 / 20), "radio_squelch": 10 ** (-10 / 20),
                # sustained tones: same reasoning
                "arc_gun_charge": 10 ** (-4 / 20),
                "transformer_buzz_overload": 10 ** (-4 / 20),
                "drill_start": 10 ** (-6 / 20),     # ends at roughly drill_run's level, which it hands over to
                "sword_swing1": 10 ** (-3 / 20), "sword_swing2": 10 ** (-3 / 20), "sword_swing3": 10 ** (-3 / 20)}


def event_of(name):
    for ev, files in VARIANTS.items():
        if name in files:
            return ev
    return name


def write_sounds_json():
    events = {}
    for name in list(LOOPS) + list(ONESHOTS) + list(LOOPS_2) + list(ONESHOTS_2) + list(LOOPS_3) + list(ONESHOTS_3):
        entry = {"name": "powerfactor:" + name}
        if name in ALL_LOOPS:
            entry["stream"] = False
        ev = event_of(name)
        events.setdefault(ev, {"sounds": [], "subtitle": "subtitles.powerfactor." + ev})["sounds"].append(entry)
    with open(os.path.join(ASSETS, "sounds.json"), "w") as f:
        json.dump(events, f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    want = set()
    for a in sys.argv[1:]:
        want.update(VARIANTS.get(a, [a]))
    unknown = want - set(ALL_LOOPS) - set(ALL_ONESHOTS)
    if unknown:
        raise SystemExit("unknown sound(s): " + ", ".join(sorted(unknown)))
    pick = lambda name: not want or name in want
    for name, (gen, target) in ALL_LOOPS.items():
        if not pick(name):
            continue
        s = normalize_rms(gen(), target)
        seam = check_loop(name, s)           # the generator's own wrap point
        s = rotate_to_quiet(s)
        check_loop(name, s)                  # and the (rotated) file edges
        print(f"{name:26s} loop     {len(s) / SR:5.2f}s  rms {db(rms(s)):6.1f} dBFS  peak "
              f"{db(max(abs(x) for x in s)):5.1f}  seam {seam:4.2f}")
        write_ogg(name, s)
    for name, gen in ALL_ONESHOTS.items():
        if not pick(name):
            continue
        s = normalize_peak(gen(), ONESHOT_PEAK.get(name, PEAK_ONESHOT))
        print(f"{name:26s} one-shot {len(s) / SR:5.2f}s  rms {db(rms(s)):6.1f} dBFS  peak {db(max(abs(x) for x in s)):5.1f}")
        write_ogg(name, s)
    write_sounds_json()
    print("sounds written to", os.path.abspath(OUT))
