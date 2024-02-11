import json
import re

import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d import Axes3D
import numpy as np
import matplotlib.cm as cm
import matplotlib
import tikzplotlib


def plot_data(data, filename, lev):
    # Define a mapping from '60s' ... '5s' to numerical values
    # second_mapping = {f"{i}": i for i in range(33980, 339800, 33980)}
    # Use bottom and top of range from the data
    bot = min(int(k) for k in data[0][1].keys())
    top = max(int(k) for k in data[0][1].keys())
    second_mapping = {f"{i}": i for i in range(bot, top + 1, bot)}

    ind = np.arange(len(second_mapping))  # the x locations for the groups
    width = 0.35       # the width of the bars

    fig, ax = plt.subplots()
    bottom = np.zeros(len(second_mapping))
    colors = cm.rainbow(np.linspace(0, len(data)))  # Generate as many colors as there are rows

    # Inside your for loop, create each bar as a separate object and append them to a list:
    bars = []
    for i, (label, scores) in enumerate(reversed(data)):
        vals = [scores[s] for s in second_mapping]
        bar = ax.bar(ind, vals, width, bottom=np.zeros(len(second_mapping)), color=colors[i], label=label)
        bars.append(bar)

    # Now, instead of ax.legend(), use:
    # ax.legend(handles=[bar[0] for bar in bars])


    ax.set_xlabel('Seconds')
    ax.set_xticks(ind)
    ax.set_xticklabels([int(int(k) / 1000) for k in second_mapping.keys()])
    # ax.bar(ind, vals, width, bottom=bottom, color=colors[i], label=label)
    # ax.legend()
    # ax.legend(loc='upper left')
    ax.set_ylabel('Precision@k')
    ax.set_title(f'$\Delta{lev}$ Repair Precision')

    # ax.set_ylim([0,1])  # Explicitly set y-axis limits

    fig.set_size_inches(25, 6)

    # plt.savefig("test.png", dpi=500)
    tikzplotlib.save(filename)

# 1-edit repairs
lev_1_edits_sec = """
P@1= 9000s: 0.449, 8000s: 0.447, 7000s: 0.443, 6000s: 0.426, 5000s: 0.411, 4000s: 0.371, 3000s: 0.324, 2000s: 0.241, 1000s: 0.127, 
P@5= 9000s: 0.640, 8000s: 0.639, 7000s: 0.634, 6000s: 0.616, 5000s: 0.590, 4000s: 0.527, 3000s: 0.463, 2000s: 0.344, 1000s: 0.186, 
P@10= 9000s: 0.731, 8000s: 0.730, 7000s: 0.726, 6000s: 0.706, 5000s: 0.680, 4000s: 0.613, 3000s: 0.537, 2000s: 0.403, 1000s: 0.217, 
P@All= 9000s: 0.981, 8000s: 0.979, 7000s: 0.973, 6000s: 0.949, 5000s: 0.910, 4000s: 0.811, 3000s: 0.681, 2000s: 0.507, 1000s: 0.259, 
"""

# 2-edit repairs (384)
lev_2_edits_sec = """
P@1= 452110s: 0.306, 406899s: 0.296, 361688s: 0.296, 316477s: 0.278, 271266s: 0.278, 226055s: 0.269, 180844s: 0.259, 135633s: 0.259, 90422s: 0.250, 45211s: 0.185, 
P@5= 452110s: 0.361, 406899s: 0.352, 361688s: 0.352, 316477s: 0.333, 271266s: 0.333, 226055s: 0.324, 180844s: 0.315, 135633s: 0.315, 90422s: 0.296, 45211s: 0.231, 
P@10= 452110s: 0.380, 406899s: 0.370, 361688s: 0.370, 316477s: 0.352, 271266s: 0.352, 226055s: 0.343, 180844s: 0.333, 135633s: 0.333, 90422s: 0.315, 45211s: 0.250, 
P@All= 452110s: 0.750, 406899s: 0.741, 361688s: 0.741, 316477s: 0.704, 271266s: 0.704, 226055s: 0.676, 180844s: 0.648, 135633s: 0.630, 90422s: 0.593, 45211s: 0.500, 
"""

lev_3_edits_sec = """
P@1= 339800s: 0.180, 305820s: 0.160, 271840s: 0.160, 237860s: 0.160, 203880s: 0.140, 169900s: 0.120, 135920s: 0.100, 101940s: 0.090, 67960s: 0.050, 33980s: 0.030, 
P@5= 339800s: 0.240, 305820s: 0.220, 271840s: 0.220, 237860s: 0.220, 203880s: 0.200, 169900s: 0.170, 135920s: 0.150, 101940s: 0.140, 67960s: 0.100, 33980s: 0.050, 
P@10= 339800s: 0.240, 305820s: 0.220, 271840s: 0.220, 237860s: 0.220, 203880s: 0.200, 169900s: 0.170, 135920s: 0.150, 101940s: 0.140, 67960s: 0.100, 33980s: 0.050, 
P@All= 339800s: 0.510, 305820s: 0.490, 271840s: 0.480, 237860s: 0.460, 203880s: 0.440, 169900s: 0.390, 135920s: 0.360, 101940s: 0.310, 67960s: 0.250, 33980s: 0.160, 
"""

# All repairs (1233)
lev_all_edits_sec = """
P@1= 9000s: 0.370, 8000s: 0.365, 7000s: 0.356, 6000s: 0.343, 5000s: 0.324, 4000s: 0.285, 3000s: 0.244, 2000s: 0.180, 1000s: 0.092
P@5= 9000s: 0.422, 8000s: 0.414, 7000s: 0.405, 6000s: 0.392, 5000s: 0.369, 4000s: 0.325, 3000s: 0.282, 2000s: 0.206, 1000s: 0.110
P@10= 9000s: 0.477, 8000s: 0.469, 7000s: 0.459, 6000s: 0.446, 5000s: 0.421, 4000s: 0.374, 3000s: 0.323, 2000s: 0.239, 1000s: 0.128
P@All= 9000s: 0.666, 8000s: 0.645, 7000s: 0.630, 6000s: 0.605, 5000s: 0.569, 4000s: 0.501, 3000s: 0.412, 2000s: 0.303, 1000s: 0.149
"""

# conda deactivate && conda activate cstk
if __name__ == '__main__':
    pattern = r"(P@\w+)=((?:\s+\d+s:\s+\d+\.\d+,?)+)"
    # Get variable name with "3" in it
    dist = 3
    variable_name = f'lev_{dist}_edits_sec'
    lev_edits_sec = globals()[variable_name]
    matches = re.findall(pattern, lev_edits_sec)

    data = []
    for match in matches:
        key = match[0].strip()
        values = re.findall(r"(\d+)s:\s+(\d+\.\d+)", match[1])
        data.append((key, {k: float(v) for k, v in values}))

    print(json.dumps(data))

    plot_data(data, f'bar_hillel_repair_{dist}.tex', f'={dist}')