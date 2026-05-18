#!/usr/bin/env python3

import argparse
import re
import sys
from pathlib import Path

import numpy as np
import matplotlib.pyplot as plt


MODELS = [
    # "MKC",
    # "RAND",
    # "WDFA",
    "CSTD"
]
DISPLAY_NAMES = {
    # "MKC": "MKC",
    # "RAND": "RAND",
    # "WDFA": "WDFA",
    "CSTD": "CSTD",
}

# Parse only the first integer after each desired label.
# Accept both MKC and MKV for robustness.
PATTERNS = {
    # "MKC": re.compile(r"\bORIG\s+MK[CV]\s+RANK:\s*(-?\d+)\b", re.IGNORECASE),
    # "RAND": re.compile(r"\bRAND\s+RANK:\s*(-?\d+)\b", re.IGNORECASE),
    # "WDFA": re.compile(r"\bWDFA\s+RANK:\s*(-?\d+)\b", re.IGNORECASE),
    # "NEURAL": re.compile(r"\bNEURAL\s+RANK:\s*(-?\d+)\b", re.IGNORECASE),
    "CSTD": re.compile(r"\bCSTD\s+RANK:\s*(-?\d+)\b", re.IGNORECASE),
}


def read_ranks(path: Path) -> dict[str, list[int]]:
    ranks = {name: [] for name in MODELS}

    with path.open("r", encoding="utf-8", errors="replace") as f:
        for line in f:
            for model, pat in PATTERNS.items():
                m = pat.search(line)
                if m is not None:
                    ranks[model].append(int(m.group(1)))
                    break

    return ranks


def exact_cdf_points(values: list[int]) -> tuple[np.ndarray, np.ndarray]:
    """
    Convert zero-based ranks to CDF points.

    Convention:
      -1  => not retrieved
       0  => best possible rank

    Since log-scale cannot show x=0, we plot x = rank + 1.
    The CDF denominator is the full number of examples, so the final plateau
    equals the retrieval rate.
    """
    if not values:
        return np.array([], dtype=int), np.array([], dtype=float)

    vals = np.asarray(values, dtype=int)
    retrieved = np.sort(vals[vals >= 0])

    if retrieved.size == 0:
        return np.array([], dtype=int), np.array([], dtype=float)

    xs = retrieved + 1
    ys = np.arange(1, retrieved.size + 1, dtype=float) / len(vals)
    return xs, ys


def collapse_duplicate_x(xs: np.ndarray, ys: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """
    If many examples share the same rank, keep only the last CDF height at that x.
    This preserves the visible CDF envelope while removing redundant points.
    """
    if len(xs) == 0:
        return xs, ys

    keep = np.r_[np.flatnonzero(xs[1:] != xs[:-1]), len(xs) - 1]
    return xs[keep], ys[keep]


def simplify_log_cdf(
        xs: np.ndarray,
        ys: np.ndarray,
        max_points: int = 500,
        exact_head: int = 50,
) -> tuple[np.ndarray, np.ndarray]:
    """
    Simplify a monotone CDF for plotting on a log-x axis.

    Strategy:
      1. Collapse repeated x-values.
      2. Keep all points with x <= exact_head.
      3. Keep endpoints.
      4. In the tail, keep a log-spaced subset of existing points.
    """
    xs, ys = collapse_duplicate_x(xs, ys)

    n = len(xs)
    if n == 0 or n <= max_points:
        return xs, ys

    keep = set()
    keep.add(0)
    keep.add(n - 1)

    # Keep all low-rank points exactly.
    for idx in np.flatnonzero(xs <= exact_head):
        keep.add(int(idx))

    remaining_budget = max_points - len(keep)
    if remaining_budget <= 0:
        idxs = np.array(sorted(keep), dtype=int)
        return xs[idxs], ys[idxs]

    tail_idxs = np.flatnonzero(xs > exact_head)
    if len(tail_idxs) > 0:
        tail_start = int(tail_idxs[0])
        keep.add(tail_start)

        xmin = xs[tail_start]
        xmax = xs[-1]

        if xmin < xmax:
            edges = np.geomspace(xmin, xmax, num=remaining_budget + 1)
            for edge in edges:
                idx = int(np.searchsorted(xs, edge, side="right") - 1)
                if 0 <= idx < n:
                    keep.add(idx)

    idxs = np.array(sorted(keep), dtype=int)

    if len(idxs) > max_points:
        chosen = np.linspace(0, len(idxs) - 1, max_points).round().astype(int)
        idxs = idxs[chosen]

    return xs[idxs], ys[idxs]


def pgf_coordinates(xs: np.ndarray, ys: np.ndarray) -> str:
    return "\n".join(f"({int(x)},{y:.8f})" for x, y in zip(xs, ys))


def make_pgfplots(series: dict[str, tuple[np.ndarray, np.ndarray]], top_k: int = 1000) -> str:
    blocks = []

    for model in MODELS:
        xs, ys = series[model]
        if len(xs) == 0:
            continue

        blocks.append(
            rf"""
\addplot+[mark=none, line width=3pt] coordinates {{
{pgf_coordinates(xs, ys)}
}};
\addlegendentry{{{DISPLAY_NAMES[model]}}}
""".strip()
        )

    blocks.append(
        rf"""
\addplot+[red, dashed, line width=3pt, mark=none] coordinates {{
({top_k},0)
({top_k},1)
}};
\addlegendentry{{Rank {top_k}}}
""".strip()
    )

    body = "\n\n".join(blocks)

    return rf"""
\begin{{tikzpicture}}
\begin{{axis}}[
  width=0.72\linewidth,
  height=0.45\linewidth,
  xmode=log,
  log basis x=10,
  ymin=0,
  ymax=0.8,
  ytick={{0.0,0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8}},
  xlabel={{Rank, plotted as raw rank + 1}},
  ylabel={{Cumulative probability}},
  title={{CDF of Ranks}},
  legend pos=south east,
  grid=major,
]

{body}

\end{{axis}}
\end{{tikzpicture}}
""".strip()


def summarize(model: str, values: list[int]) -> None:
    vals = np.asarray(values, dtype=int)

    if len(vals) == 0:
        print(f"{model}: n=0", file=sys.stderr)
        return

    retrieved = int(np.sum(vals >= 0))
    not_retrieved = int(np.sum(vals == -1))
    plateau = retrieved / len(vals)

    print(
        f"{model}: n={len(vals)}, retrieved={retrieved}, "
        f"not_retrieved={not_retrieved}, plateau={plateau:.6f}",
        file=sys.stderr,
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--log",
        type=Path,
        default=Path(__file__).with_name("reconstruct_cdf9.log"),
        help="Path to the log file",
    )
    parser.add_argument(
        "--max-points",
        type=int,
        default=500,
        help="Maximum number of points per simplified CDF",
    )
    parser.add_argument(
        "--exact-head",
        type=int,
        default=50,
        help="Keep all points with x <= this value before log-tail simplification",
    )
    parser.add_argument(
        "--top-k",
        type=int,
        default=1000,
        help="Reference vertical line at this plotted x-value",
    )
    parser.add_argument(
        "--no-display",
        action="store_true",
        help="Do not show the matplotlib window",
    )
    args = parser.parse_args()

    ranks = read_ranks(args.log)

    lengths = {k: len(v) for k, v in ranks.items()}
    if len(set(lengths.values())) > 1:
        print(
            f"WARNING: unequal number of parsed ranks: {lengths}",
            file=sys.stderr,
        )

    exact_series = {}
    simplified_series = {}

    for model in MODELS:
        xs, ys = exact_cdf_points(ranks[model])
        exact_series[model] = (xs, ys)

        xs_s, ys_s = simplify_log_cdf(
            xs, ys,
            max_points=args.max_points,
            exact_head=args.exact_head,
        )
        simplified_series[model] = (xs_s, ys_s)

    for model in MODELS:
        summarize(model, ranks[model])

    for model in MODELS:
        n0 = len(exact_series[model][0])
        n1 = len(simplified_series[model][0])
        if n0 > 0:
            print(f"{model}: tikz points {n0} -> {n1}", file=sys.stderr)

    if not args.no_display:
        plt.figure(figsize=(7, 4.5))

        # Plot the simplified curves, not the full curves.
        for model in MODELS:
            xs, ys = simplified_series[model]
            if len(xs):
                plt.plot(xs, ys, label=DISPLAY_NAMES[model])

        plt.xscale("log")
        plt.axvline(x=args.top_k, color="red", linestyle="--", label=f"Rank {args.top_k}")
        plt.xlabel("Rank, plotted as raw rank + 1")
        plt.ylabel("Cumulative Probability")
        plt.title("CDF of Ranks")
        plt.legend()
        plt.tight_layout()
        plt.show()

    # Emit PGFPlots/TikZ to stdout.
    print(make_pgfplots(simplified_series, top_k=args.top_k))


if __name__ == "__main__":
    main()