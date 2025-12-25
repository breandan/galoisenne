#!/usr/bin/env python3
# Outputs a single pgfplots scatter figure (log y) from inline CSV.
# LaTeX preamble:
#   \usepackage{pgfplots}
#   \pgfplotsset{compat=1.18}
#   \usepackage{float} % for [H]

import csv
from io import StringIO
from textwrap import dedent

RAW_DATA = dedent(r"""
    n,k,mean_ttfs_ms
    20,1,90.050
    21,1,107.583
    22,1,121.583
    23,1,138.575
    24,1,157.417
    25,1,177.883
    26,1,200.250
    27,1,224.058
    28,1,250.108
    29,1,277.992
    30,1,307.892
    31,1,340.400
    32,1,374.892
    33,1,412.433
    34,1,450.767
    35,1,492.117
    36,1,536.100
    37,1,582.692
    38,1,632.592
    39,1,683.708
    40,1,737.258
    41,1,795.725
    42,1,854.600
    43,1,918.475
    44,1,983.417
    45,1,1013.075
    46,1,1073.392
    47,1,1140.708
    48,1,1209.050
    49,1,1283.958
    50,1,1371.008

    20,2,593.592
    21,2,678.575
    22,2,782.242
    23,2,893.350
    24,2,1016.708
    25,2,1153.542
    26,2,1301.808
    27,2,1459.767
    28,2,1593.225
    29,2,1799.558
    30,2,1963.267
    31,2,2158.008
    32,2,2418.117
    33,2,2674.167
    34,2,2883.358
    35,2,3160.475
    36,2,3452.642
    37,2,3701.042
    38,2,4037.458
    39,2,4366.708
    40,2,4689.883
    41,2,5062.783
    42,2,5510.000
    43,2,5914.250
    44,2,6313.275
    45,2,6776.467
    46,2,7254.558
    47,2,7679.833
    48,2,8176.067
    49,2,8726.433
    50,2,9265.800
""").strip()

# ---- Parse CSV (skip blanks/comments/garbage) ----
clean = []
for ln in RAW_DATA.splitlines():
    ln = ln.strip()
    if not ln or ln.startswith("#"):
        continue
    clean.append(ln)

rows = []
for r in csv.DictReader(StringIO("\n".join(clean))):
    try:
        n = int(r["n"])
        k = int(r["k"])
        y = float(r["mean_ttfs_ms"])
        if y <= 0:
            continue  # log-scale needs positive values
        rows.append({"n": n, "k": k, "y": y})
    except Exception:
        # Skip malformed lines (e.g., "...")
        continue

# ---- Group by k and sort by n ----
by_k = {}
for r in rows:
    by_k.setdefault(r["k"], []).append(r)
for k in by_k:
    by_k[k].sort(key=lambda x: x["n"])

marks = ["*", "square*", "triangle*", "diamond*", "o", "x"]  # pgfplots-safe

def table_for(series):
    header = "n,mean_ttfs_ms"
    body = "\n".join(f"{r['n']},{r['y']}" for r in series)
    return header + "\n" + body

# ---- Emit LaTeX ----
out = []
out += [
    r"\begin{figure}[H]",
    r"  \centering",
    r"  \begin{tikzpicture}",
    r"    \begin{axis}[",
    r"      width=\linewidth, height=3.6cm,",
    r"      xlabel={$n$}, ylabel={Mean TTFS (ms)},",
    r"      ymode=log,",
    r"      xmajorgrids, ymajorgrids,",
    r"      tick align=outside,",
    r"      tick label style={font=\scriptsize},",
    r"      label style={font=\scriptsize},",
    r"      legend style={draw=none, fill=none, font=\scriptsize, at={(0.98,0.98)}, anchor=north east},",
    r"      scaled y ticks=false,",
    r"    ]",
]

leg = []
for i, k in enumerate(sorted(by_k.keys())):
    mark = marks[i % len(marks)]
    tbl = table_for(by_k[k])
    out += [
        r"      \addplot+[only marks, mark=" + mark + r", mark size=1.5pt]",
        r"        table[col sep=comma, x=n, y=mean_ttfs_ms]{",
        tbl,
        r"        };",
        ]
    leg.append(r"$k=" + str(k) + r"$")

out += [
    r"      \legend{" + ",".join(leg) + r"}",
    r"    \end{axis}",
    r"  \end{tikzpicture}",
    r"  \vspace{-0.4em}",
    r"  \caption{Type inference: mean TTFS vs.\ sequence length $n$ (log-scaled y).}",
    r"  \vspace{-0.6em}",
    r"\end{figure}",
    ]

print("\n".join(out))
