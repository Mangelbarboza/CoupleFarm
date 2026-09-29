"""Regenera la pista y los ambientes originales de CoupleFarm (solo stdlib)."""

from __future__ import annotations

import math
import random
import struct
import wave
from pathlib import Path


SAMPLE_RATE = 22_050
RAW_DIR = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res" / "raw"


def _write_mono(path: Path, samples: list[float]) -> None:
    peak = max(1.0, max(abs(sample) for sample in samples))
    pcm = bytearray()
    for sample in samples:
        value = int(max(-1.0, min(1.0, sample / peak)) * 32_000)
        pcm.extend(struct.pack("<h", value))
    with wave.open(str(path), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(SAMPLE_RATE)
        output.writeframes(pcm)


def _frequency(midi: int) -> float:
    return 440.0 * (2.0 ** ((midi - 69) / 12.0))


def _triangle(phase: float) -> float:
    return 2.0 / math.pi * math.asin(math.sin(phase))


def make_music() -> None:
    """Pista original de 16 compases: chip suave, sin percusión agresiva."""
    bpm = 76.0
    beat_seconds = 60.0 / bpm
    bars = 16
    duration = bars * 4.0 * beat_seconds
    chords = (
        (48, 52, 55, 59),  # Cmaj7
        (45, 48, 52, 55),  # Am7
        (41, 45, 48, 52),  # Fmaj7
        (43, 47, 50, 52),  # G6
    )
    melody = (67, 71, 72, 76, 74, 71, 69, 67, 64, 67, 69, 72, 71, 67, 64, 62)
    random.seed(8)
    samples: list[float] = []

    for index in range(round(duration * SAMPLE_RATE)):
        t = index / SAMPLE_RATE
        beat_total = t / beat_seconds
        bar_index = int(beat_total // 4)
        beat_in_bar = beat_total % 4.0
        chord = chords[bar_index % len(chords)]

        # Acorde muy suave, con forma triangular para conservar carácter 8-bit.
        bar_phase = beat_in_bar / 4.0
        pad_envelope = min(1.0, bar_phase * 18.0, (1.0 - bar_phase) * 18.0)
        pad = sum(_triangle(2.0 * math.pi * _frequency(note) * t) for note in chord) / 4.0

        # Arpegio de corcheas; el ataque corto evita clics y deja respirar el paisaje.
        half_beat = int(beat_total * 2.0)
        arp_fraction = (beat_total * 2.0) % 1.0
        arp_note = chord[half_beat % 4] + 12
        arp_envelope = min(1.0, arp_fraction * 12.0) * math.exp(-2.7 * arp_fraction)
        arp = _triangle(2.0 * math.pi * _frequency(arp_note) * t) * arp_envelope

        # Una melodía pequeña aparece solo en compases alternos.
        melody_fraction = beat_total % 1.0
        melody_note = melody[int(beat_total) % len(melody)]
        melody_envelope = min(1.0, melody_fraction * 10.0) * math.exp(-3.2 * melody_fraction)
        lead = 0.0
        if bar_index % 4 in (1, 2):
            lead = _triangle(2.0 * math.pi * _frequency(melody_note) * t) * melody_envelope

        bass_note = chord[0] - 12
        bass = math.sin(2.0 * math.pi * _frequency(bass_note) * t) * (0.72 + 0.28 * math.cos(math.pi * beat_in_bar / 2.0))

        # Respiración lenta casi imperceptible para que el bucle no resulte plano.
        breathe = 0.96 + 0.04 * math.sin(2.0 * math.pi * t / (beat_seconds * 8.0))
        mixed = (0.16 * pad * pad_envelope + 0.12 * arp + 0.055 * lead + 0.075 * bass) * breathe
        samples.append(mixed)

    # Suaviza únicamente unos milisegundos en los extremos; MediaPlayer repite sin golpe.
    edge = int(SAMPLE_RATE * 0.018)
    for i in range(edge):
        gain = i / edge
        samples[i] *= gain
        samples[-1 - i] *= gain
    _write_mono(RAW_DIR / "music_meadow_morning.wav", samples)


def make_bird() -> None:
    duration = 0.72
    samples: list[float] = []
    for index in range(round(duration * SAMPLE_RATE)):
        t = index / SAMPLE_RATE
        value = 0.0
        for start, length, base, rise, volume in (
            (0.04, 0.12, 1_650.0, 620.0, 0.42),
            (0.23, 0.10, 1_890.0, 780.0, 0.34),
            (0.43, 0.16, 1_520.0, 900.0, 0.28),
        ):
            local = t - start
            if 0.0 <= local < length:
                progress = local / length
                envelope = math.sin(math.pi * progress) ** 1.6
                frequency = base + rise * progress + 75.0 * math.sin(progress * math.pi * 2.0)
                value += math.sin(2.0 * math.pi * frequency * local) * envelope * volume
        samples.append(value)
    _write_mono(RAW_DIR / "sfx_bird_chirp.wav", samples)


def make_butterfly() -> None:
    duration = 0.48
    random.seed(18)
    samples: list[float] = []
    filtered = 0.0
    for index in range(round(duration * SAMPLE_RATE)):
        t = index / SAMPLE_RATE
        envelope = math.sin(math.pi * t / duration) ** 1.4
        wing = 0.5 + 0.5 * math.sin(2.0 * math.pi * 23.0 * t)
        noise = random.uniform(-1.0, 1.0)
        filtered = filtered * 0.82 + noise * 0.18
        shimmer = math.sin(2.0 * math.pi * 1_280.0 * t) * 0.08
        samples.append((filtered * 0.13 + shimmer) * wing * envelope)
    _write_mono(RAW_DIR / "sfx_butterfly_flutter.wav", samples)


if __name__ == "__main__":
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    make_music()
    make_bird()
    make_butterfly()
    print("Audio creado en", RAW_DIR)
