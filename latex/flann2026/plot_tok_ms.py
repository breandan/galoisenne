#!/usr/bin/env python3
import re
import argparse
from pathlib import Path
from statistics import mean
import matplotlib.pyplot as plt

TOK_RE = re.compile(r"^(MKV|WDFA|TRR)\s+tok/ms\s*=\s*([0-9]+(?:\.[0-9]+)?)\s*$")

def parse_tok_ms(text: str):
    vals = {
        "MKV": [],
        "WDFA": [],
        "TRR": [],
    }

    for line in text.splitlines():
        m = TOK_RE.match(line.strip())
        if m:
            name, value = m.groups()
            vals[name].append(float(value))

    return vals

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("log", help="Path to log file")
    ap.add_argument("--out", default="tok_ms_bar.png", help="Output plot filename")
    args = ap.parse_args()

    text = Path(args.log).read_text(errors="replace")
    vals = parse_tok_ms(text)

    avgs = {}
    for name, xs in vals.items():
        if not xs:
            raise ValueError(f"No `{name} tok/ms = ...` lines found")
        avgs[name] = mean(xs)

    print("Average tok/ms:")
    for name in ["MKV", "WDFA", "TRR"]:
        print(f"  {name}: {avgs[name]:.6f}  n={len(vals[name])}")

    names = ["MKV", "WDFA", "TRR"]
    ys = [avgs[n] for n in names]

    plt.figure(figsize=(6, 4))
    plt.bar(names, ys)
    plt.yscale("log")
    plt.ylabel("Average tok/ms (log scale)")
    plt.title("Average token throughput")
    plt.tight_layout()
    plt.savefig(args.out, dpi=200)
    print(f"Wrote {args.out}")

if __name__ == "__main__":
    main()