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
P@1= 90000s: 0.982, 80000s: 0.975, 70000s: 0.966, 60000s: 0.954, 50000s: 0.933, 40000s: 0.908, 30000s: 0.880, 20000s: 0.752, 10000s: 0.546
P@5= 90000s: 0.982, 80000s: 0.975, 70000s: 0.966, 60000s: 0.954, 50000s: 0.933, 40000s: 0.908, 30000s: 0.880, 20000s: 0.752, 10000s: 0.546
P@10= 90000s: 0.982, 80000s: 0.975, 70000s: 0.966, 60000s: 0.954, 50000s: 0.933, 40000s: 0.908, 30000s: 0.880, 20000s: 0.752, 10000s: 0.546
P@All= 90000s: 0.982, 80000s: 0.975, 70000s: 0.966, 60000s: 0.954, 50000s: 0.933, 40000s: 0.908, 30000s: 0.880, 20000s: 0.752, 10000s: 0.546
"""

# 2-edit repairs (384)
lev_2_edits_sec = """
P@1= 90000s: 0.329, 80000s: 0.320, 70000s: 0.311, 60000s: 0.302, 50000s: 0.283, 40000s: 0.258, 30000s: 0.218, 20000s: 0.160, 10000s: 0.092, 
P@5= 90000s: 0.360, 80000s: 0.351, 70000s: 0.342, 60000s: 0.329, 50000s: 0.311, 40000s: 0.283, 30000s: 0.237, 20000s: 0.178, 10000s: 0.102, 
P@10= 90000s: 0.366, 80000s: 0.357, 70000s: 0.348, 60000s: 0.335, 50000s: 0.317, 40000s: 0.289, 30000s: 0.243, 20000s: 0.178, 10000s: 0.102, 
P@All= 90000s: 0.717, 80000s: 0.702, 70000s: 0.677, 60000s: 0.649, 50000s: 0.603, 40000s: 0.554, 30000s: 0.455, 20000s: 0.326, 10000s: 0.182, 
"""

lev_3_edits_sec = """
P@1= 900000s: 0.369, 800000s: 0.366, 700000s: 0.363, 600000s: 0.358, 500000s: 0.358, 400000s: 0.349, 300000s: 0.307, 200000s: 0.268, 100000s: 0.162, 
P@5= 900000s: 0.394, 800000s: 0.391, 700000s: 0.388, 600000s: 0.383, 500000s: 0.383, 400000s: 0.374, 300000s: 0.332, 200000s: 0.291, 100000s: 0.176, 
P@10= 900000s: 0.399, 800000s: 0.397, 700000s: 0.394, 600000s: 0.388, 500000s: 0.388, 400000s: 0.380, 300000s: 0.338, 200000s: 0.296, 100000s: 0.179, 
P@All= 900000s: 0.737, 800000s: 0.735, 700000s: 0.732, 600000s: 0.723, 500000s: 0.709, 400000s: 0.684, 300000s: 0.615, 200000s: 0.522, 100000s: 0.327, 
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
    dist = 4
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