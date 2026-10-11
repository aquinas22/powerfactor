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
"""
import json, math, os, random, struct, subprocess, tempfile, wave

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
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-c:a", "libvorbis", "-q:a", "4",
                    os.path.join(OUT, name + ".ogg")], check=True)
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


# a near-continuous square-ish tone is far louder than a transient at the same peak; keep the alarm civil
ONESHOT_PEAK = {"scada_alarm": 10 ** (-6 / 20)}


def write_sounds_json():
    events = {}
    for name in list(LOOPS) + list(ONESHOTS):
        entry = {"name": "powerfactor:" + name}
        if name in LOOPS:
            entry["stream"] = False
        events[name] = {"sounds": [entry], "subtitle": "subtitles.powerfactor." + name}
    with open(os.path.join(ASSETS, "sounds.json"), "w") as f:
        json.dump(events, f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for name, (gen, target) in LOOPS.items():
        s = rotate_to_quiet(normalize_rms(gen(), target))
        print(f"{name:24s} loop     {len(s) / SR:5.2f}s  rms {db(rms(s)):6.1f} dBFS  peak {db(max(abs(x) for x in s)):5.1f}")
        write_ogg(name, s)
    for name, gen in ONESHOTS.items():
        s = normalize_peak(gen(), ONESHOT_PEAK.get(name, PEAK_ONESHOT))
        print(f"{name:24s} one-shot {len(s) / SR:5.2f}s  rms {db(rms(s)):6.1f} dBFS  peak {db(max(abs(x) for x in s)):5.1f}")
        write_ogg(name, s)
    write_sounds_json()
    print("sounds written to", os.path.abspath(OUT))
