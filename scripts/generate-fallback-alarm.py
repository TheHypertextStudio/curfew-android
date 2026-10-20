#!/usr/bin/env python3
"""Generate Curfew's original, dependency-free fallback alarm sound."""
from pathlib import Path
import math
import struct
import wave

sample_rate = 22050
samples = bytearray()
for index in range(2 * sample_rate):
    at = index / sample_rate
    pulse = int(at / 0.25)
    within = at % 0.25
    if pulse < 4 and within < 0.18:
        envelope = min(1, within / 0.005, (0.18 - within) / 0.01)
        frequency = 660 if pulse % 2 == 0 else 880
        value = int(14000 * envelope * math.sin(2 * math.pi * frequency * at))
    else:
        value = 0
    samples.extend(struct.pack('<h', value))
output = Path(__file__).resolve().parents[1] / 'app/src/main/res/raw/sunrise_fallback.wav'
with wave.open(str(output), 'wb') as sound:
    sound.setnchannels(1)
    sound.setsampwidth(2)
    sound.setframerate(sample_rate)
    sound.writeframes(samples)
print(output)
