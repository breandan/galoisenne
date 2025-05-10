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
P@1= 90000s: 0.520, 80000s: 0.520, 70000s: 0.519, 60000s: 0.518, 50000s: 0.515, 40000s: 0.504, 30000s: 0.467, 20000s: 0.400, 10000s: 0.316, 
P@5= 90000s: 0.671, 80000s: 0.670, 70000s: 0.668, 60000s: 0.667, 50000s: 0.663, 40000s: 0.648, 30000s: 0.600, 20000s: 0.517, 10000s: 0.406, 
P@10= 90000s: 0.715, 80000s: 0.714, 70000s: 0.712, 60000s: 0.710, 50000s: 0.707, 40000s: 0.690, 30000s: 0.640, 20000s: 0.556, 10000s: 0.434, 
P@All= 90000s: 0.989, 80000s: 0.988, 70000s: 0.985, 60000s: 0.982, 50000s: 0.976, 40000s: 0.956, 30000s: 0.891, 20000s: 0.781, 10000s: 0.619, 
"""

# 2-edit repairs (384)
lev_2_edits_sec = """
P@1= 90000s: 0.232, 80000s: 0.227, 70000s: 0.222, 60000s: 0.219, 50000s: 0.209, 40000s: 0.196, 30000s: 0.180, 20000s: 0.160, 10000s: 0.123, 
P@5= 90000s: 0.283, 80000s: 0.278, 70000s: 0.270, 60000s: 0.263, 50000s: 0.251, 40000s: 0.234, 30000s: 0.214, 20000s: 0.190, 10000s: 0.142, 
P@10= 90000s: 0.295, 80000s: 0.290, 70000s: 0.281, 60000s: 0.274, 50000s: 0.261, 40000s: 0.243, 30000s: 0.221, 20000s: 0.197, 10000s: 0.147, 
P@All= 90000s: 0.902, 80000s: 0.874, 70000s: 0.842, 60000s: 0.802, 50000s: 0.748, 40000s: 0.680, 30000s: 0.597, 20000s: 0.480, 10000s: 0.299, 
"""

lev_3_edits_sec = """
P@1= 90000s: 0.146, 80000s: 0.143, 70000s: 0.138, 60000s: 0.134, 50000s: 0.129, 40000s: 0.119, 30000s: 0.113, 20000s: 0.094, 10000s: 0.076, 
P@5= 90000s: 0.256, 80000s: 0.251, 70000s: 0.241, 60000s: 0.233, 50000s: 0.223, 40000s: 0.205, 30000s: 0.183, 20000s: 0.141, 10000s: 0.111, 
P@10= 90000s: 0.291, 80000s: 0.286, 70000s: 0.273, 60000s: 0.266, 50000s: 0.254, 40000s: 0.234, 30000s: 0.204, 20000s: 0.158, 10000s: 0.117, 
P@All= 90000s: 0.711, 80000s: 0.690, 70000s: 0.655, 60000s: 0.625, 50000s: 0.590, 40000s: 0.543, 30000s: 0.472, 20000s: 0.372, 10000s: 0.247, 
"""

lev_4_edits_sec = """
P@1= 900000s: 0.292, 800000s: 0.292, 700000s: 0.292, 600000s: 0.277, 500000s: 0.246, 400000s: 0.200, 300000s: 0.185, 200000s: 0.138, 100000s: 0.077, 
P@5= 900000s: 0.323, 800000s: 0.323, 700000s: 0.323, 600000s: 0.308, 500000s: 0.277, 400000s: 0.231, 300000s: 0.215, 200000s: 0.169, 100000s: 0.092, 
P@10= 900000s: 0.323, 800000s: 0.323, 700000s: 0.323, 600000s: 0.308, 500000s: 0.277, 400000s: 0.231, 300000s: 0.215, 200000s: 0.169, 100000s: 0.092, 
P@All= 900000s: 0.523, 800000s: 0.523, 700000s: 0.523, 600000s: 0.508, 500000s: 0.477, 400000s: 0.431, 300000s: 0.415, 200000s: 0.323, 100000s: 0.123,
"""

# All repairs (1233)
lev_all_edits_sec = """
P@1= 900000s: 0.564, 800000s: 0.563, 700000s: 0.562, 600000s: 0.560, 500000s: 0.558, 400000s: 0.552, 300000s: 0.536, 200000s: 0.516, 100000s: 0.457, 
P@5= 900000s: 0.585, 800000s: 0.584, 700000s: 0.583, 600000s: 0.580, 500000s: 0.578, 400000s: 0.573, 300000s: 0.557, 200000s: 0.534, 100000s: 0.472, 
P@10= 900000s: 0.588, 800000s: 0.588, 700000s: 0.587, 600000s: 0.584, 500000s: 0.582, 400000s: 0.576, 300000s: 0.561, 200000s: 0.538, 100000s: 0.475, 
P@All= 900000s: 0.848, 800000s: 0.847, 700000s: 0.846, 600000s: 0.843, 500000s: 0.836, 400000s: 0.825, 300000s: 0.797, 200000s: 0.750, 100000s: 0.637, 
"""

# conda deactivate && conda activate cstk
if __name__ == '__main__':
    pattern = r"(P@\w+)=((?:\s+\d+s:\s+\d+\.\d+,?)+)"
    # Get variable name with "3" in it
    dist = 2
    variable_name = f'lev_{dist}_edits_sec'
    lev_edits_sec = globals()[variable_name]
    matches = re.findall(pattern, lev_edits_sec)

    data = []
    for match in matches:
        key = match[0].strip()
        values = re.findall(r"(\d+)s:\s+(\d+\.\d+)", match[1])
        data.append((key, {k: float(v) for k, v in values}))

    print(json.dumps(data))

    # plot_data(data, f'bar_hillel_repair.tex', f'\in[1,4]')
    plot_data(data, f'bar_hillel_repair_{dist}.tex', f'={dist}')