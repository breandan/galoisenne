#!/usr/bin/env python3

import argparse
import re
import sys
from pathlib import Path

import numpy as np
import matplotlib.pyplot as plt


MODELS = ["MKC", "WFA", "NEURAL", "RAND"]

rank_re = re.compile(
    r"""(?:ORIG\s+)?(MKC|WFA|NEURAL|RAND)\s+RANK:\s*(-?\d+)(?:\s*/\s*(\d+))?(?=\s|$)""",
    re.IGNORECASE,
)


def read_ranks(path: Path) -> dict[str, list[int]]:
    ranks = {name: [] for name in MODELS}

    with path.open("r", encoding="utf-8", errors="replace") as f:
        for line_no, line in enumerate(f, start=1):
            m = rank_re.search(line)
            if m is None:
                continue

            model = m.group(1).upper()
            rank = int(m.group(2))
            ranks[model].append(rank)

    return ranks


def exact_cdf_points(values: list[int]) -> tuple[np.ndarray, np.ndarray]:
    values = np.asarray(values, dtype=int)

    # -1 means not retrieved.
    #  0 means best possible rank.
    # Since log-scale cannot plot x=0, plot raw rank r as x = r + 1.
    retrieved = values[values >= 0]
    retrieved = np.sort(retrieved)

    if len(values) == 0 or len(retrieved) == 0:
        return np.array([], dtype=int), np.array([], dtype=float)

    xs = retrieved + 1
    ys = np.arange(1, len(retrieved) + 1, dtype=float) / len(values)

    return xs, ys


def collapse_duplicate_x(xs: np.ndarray, ys: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """
    If many items have the same rank, keep only the final CDF height
    for that x-coordinate. This is exact for the visible CDF envelope.
    """
    if len(xs) == 0:
        return xs, ys

    last_idx = np.r_[np.flatnonzero(xs[1:] != xs[:-1]), len(xs) - 1]
    return xs[last_idx], ys[last_idx]


def simplify_log_cdf(
        xs: np.ndarray,
        ys: np.ndarray,
        max_points: int = 500,
        exact_head: int = 50,
) -> tuple[np.ndarray, np.ndarray]:
    """
    Reduce PGFPlots size by keeping:
      1. one point per distinct x-coordinate,
      2. all small-rank points up to exact_head,
      3. a log-spaced subset of the remaining tail.

    The curve remains monotone and visually faithful on a log x-axis.
    """
    xs, ys = collapse_duplicate_x(xs, ys)

    n = len(xs)
    if n <= max_points:
        return xs, ys

    keep = set()

    # Always keep endpoints.
    keep.add(0)
    keep.add(n - 1)

    # Keep low ranks exactly. These are visually important and few.
    for idx in np.flatnonzero(xs <= exact_head):
        keep.add(int(idx))

    remaining_budget = max_points - len(keep)
    if remaining_budget <= 0:
        idxs = np.array(sorted(keep), dtype=int)
        return xs[idxs], ys[idxs]

    tail_start_candidates = np.flatnonzero(xs > exact_head)

    if len(tail_start_candidates) > 0:
        tail_start = int(tail_start_candidates[0])
        xmin = xs[tail_start]
        xmax = xs[-1]

        if xmin < xmax:
            # Pick actual existing points at log-spaced x thresholds.
            edges = np.geomspace(xmin, xmax, num=remaining_budget + 1)

            for edge in edges:
                idx = int(np.searchsorted(xs, edge, side="right") - 1)
                if 0 <= idx < n:
                    keep.add(idx)

        keep.add(tail_start)

    idxs = np.array(sorted(keep), dtype=int)

    # If duplicate selections or a huge exact head went over budget, thin again.
    if len(idxs) > max_points:
        chosen = np.linspace(0, len(idxs) - 1, max_points).round().astype(int)
        idxs = idxs[chosen]

    return xs[idxs], ys[idxs]


def pgf_coordinates(xs: np.ndarray, ys: np.ndarray) -> str:
    return "\n".join(f"({int(x)},{y:.8f})" for x, y in zip(xs, ys))


def make_pgfplots(series: dict[str, tuple[np.ndarray, np.ndarray]]) -> str:
    plots = []

    for name, (xs, ys) in series.items():
        if len(xs) == 0:
            continue

        plots.append(
            rf"""
\addplot+[mark=none, line width=3pt] coordinates {{
{pgf_coordinates(xs, ys)}
}};
\addlegendentry{{{name}}}
""".strip()
        )

    plots.append(
        r"""
\addplot+[red, dashed, line width=3.4pt, mark=none] coordinates {
(1000,0)
(1000,1)
};
\addlegendentry{Rank 1000}
""".strip()
    )

    body = "\n\n".join(plots)

    return rf"""
\begin{{tikzpicture}}
\begin{{axis}}[
  width=0.72\linewidth,
  height=0.45\linewidth,
  xmode=log,
  log basis x=10,
  xlabel={{Rank, plotted as raw rank $+1$}},
  ylabel={{Cumulative probability}},
  title={{CDF of Ranks}},
  legend pos=south east,
  grid=major,
]

{body}

\end{{axis}}
\end{{tikzpicture}}
""".strip()


def summarize(name: str, values: list[int]) -> None:
    values_np = np.asarray(values, dtype=int)

    retrieved = int(np.sum(values_np >= 0))
    rank0 = int(np.sum(values_np == 0))
    positive_rank = int(np.sum(values_np > 0))
    not_retrieved = int(np.sum(values_np == -1))
    other_negative = int(np.sum(values_np < -1))
    plateau = retrieved / len(values_np) if len(values_np) else 0.0

    print(
        f"{name}: n={len(values_np)}, "
        f"retrieved={retrieved}, "
        f"rank0={rank0}, "
        f"positive_rank={positive_rank}, "
        f"not_retrieved={not_retrieved}, "
        f"other_negative={other_negative}, "
        f"cdf_plateau={plateau:.6f}",
        file=sys.stderr,
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--log",
        type=Path,
        default=Path(__file__).with_name("reconstruct_cdf.log"),
        help="Path to reconstruct_cdf.log",
    )
    parser.add_argument(
        "--max-points",
        type=int,
        default=500,
        help="Maximum PGFPlots points per CDF curve after simplification",
    )
    parser.add_argument(
        "--exact-head",
        type=int,
        default=50,
        help="Keep ranks <= this plotted x-value exactly before log-binning",
    )
    parser.add_argument(
        "--no-display",
        action="store_true",
        help="Do not open the matplotlib display window",
    )
    args = parser.parse_args()

    ranks = read_ranks(args.log)

    lengths = {name: len(values) for name, values in ranks.items() if len(values) > 0}
    if len(set(lengths.values())) > 1:
        print(f"WARNING: unequal number of parsed ranks: {lengths}", file=sys.stderr)
        print("This usually means one rank line is missing or malformed in the log.", file=sys.stderr)

    exact_series = {}
    simplified_series = {}

    for name, values in ranks.items():
        xs, ys = exact_cdf_points(values)
        exact_series[name] = (xs, ys)
        simplified_series[name] = simplify_log_cdf(
            xs,
            ys,
            max_points=args.max_points,
            exact_head=args.exact_head,
        )

    for name in MODELS:
        summarize(name, ranks[name])

    for name in MODELS:
        exact_n = len(exact_series[name][0])
        simp_n = len(simplified_series[name][0])
        if exact_n:
            print(
                f"{name}: tikz points {exact_n} -> {simp_n}",
                file=sys.stderr,
            )

    if not args.no_display:
        plt.figure(figsize=(7, 4.5))

        # Display the simplified curves too, so the local plot matches the TeX.
        for name, (xs, ys) in simplified_series.items():
            if len(xs):
                plt.plot(xs, ys, label=name)

        plt.xscale("log")
        plt.axvline(x=1000, color="red", linestyle="--", label="Rank 1000")
        plt.xlabel("Rank, plotted as raw rank + 1")
        plt.ylabel("Cumulative Probability")
        plt.title("CDF of Ranks")
        plt.legend()
        plt.tight_layout()
        plt.show()

    # Print only the PGFPlots/TikZ code to stdout.
    print(make_pgfplots(simplified_series))


if __name__ == "__main__":
    main()