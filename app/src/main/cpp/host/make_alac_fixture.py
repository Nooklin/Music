#!/usr/bin/env python3
"""Makes the three files test_alac reads: encodes a test signal to ALAC with ffmpeg, cuts the magic cookie and the packets
out of the .m4a and lets ffmpeg decode the same file as the reference. Usage: make_alac_fixture.py outdir [mono|stereo] [s16|s32]"""
import json, os, struct, subprocess, sys

out = sys.argv[1]
ch = 1 if len(sys.argv) > 2 and sys.argv[2] == "mono" else 2
fmt = sys.argv[3] if len(sys.argv) > 3 else "s16"
os.makedirs(out, exist_ok=True)
m4a = os.path.join(out, "t.m4a")
src = f"sine=frequency=440:duration=3,aeval=val(0)*0.5+random(0)*0.05:c={'mono' if ch == 1 else 'stereo'}"
subprocess.run(["ffmpeg", "-y", "-v", "error", "-f", "lavfi", "-i", src, "-ar", "44100", "-c:a", "alac",
                "-sample_fmt", fmt + "p", m4a], check=True)
data = open(m4a, "rb").read()
i = data.find(b"\x00\x00\x00\x24alac")
open(os.path.join(out, "cookie.bin"), "wb").write(data[i:i + 36])
info = json.loads(subprocess.run(["ffprobe", "-v", "error", "-select_streams", "a:0", "-show_packets", "-show_entries",
                                  "packet=size,pos", "-of", "json", m4a], capture_output=True, check=True).stdout)
with open(os.path.join(out, "packets.bin"), "wb") as f:
    for p in info["packets"]:
        pos, size = int(p["pos"]), int(p["size"])
        f.write(struct.pack("<I", size) + data[pos:pos + size])
ref = os.path.join(out, "ref.s16")
subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", m4a, "-f", "s16le", "-ac", str(ch), ref], check=True)
if ch == 1:  # the decoder doubles mono into both channels
    raw = open(ref, "rb").read()
    open(ref, "wb").write(b"".join(raw[i:i + 2] * 2 for i in range(0, len(raw), 2)))
