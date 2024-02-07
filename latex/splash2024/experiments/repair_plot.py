import json
import re

import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d import Axes3D
import numpy as np
import matplotlib.cm as cm
import matplotlib
import tikzplotlib


def plot_data(data, filename):
    # Define a mapping from '60s' ... '5s' to numerical values
    second_mapping = {f"{i}": i for i in range(6000, 66000, 6000)}

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
    ax.set_xticklabels(list(second_mapping.keys()))
    # ax.bar(ind, vals, width, bottom=bottom, color=colors[i], label=label)
    # ax.legend()
    # ax.legend(loc='upper left')
    ax.set_ylabel('Precision@k')
    ax.set_title('$\Delta\in[1,3]$ Repair Precision')

    # ax.set_ylim([0,1])  # Explicitly set y-axis limits

    fig.set_size_inches(25, 6)

    # plt.savefig("test.png", dpi=500)
    tikzplotlib.save(filename)

# 1-edit repairs
one_edits_sec = """
P@1= 9000s: 0.449, 8000s: 0.447, 7000s: 0.443, 6000s: 0.426, 5000s: 0.411, 4000s: 0.371, 3000s: 0.324, 2000s: 0.241, 1000s: 0.127, 
P@5= 9000s: 0.640, 8000s: 0.639, 7000s: 0.634, 6000s: 0.616, 5000s: 0.590, 4000s: 0.527, 3000s: 0.463, 2000s: 0.344, 1000s: 0.186, 
P@10= 9000s: 0.731, 8000s: 0.730, 7000s: 0.726, 6000s: 0.706, 5000s: 0.680, 4000s: 0.613, 3000s: 0.537, 2000s: 0.403, 1000s: 0.217, 
P@All= 9000s: 0.981, 8000s: 0.979, 7000s: 0.973, 6000s: 0.949, 5000s: 0.910, 4000s: 0.811, 3000s: 0.681, 2000s: 0.507, 1000s: 0.259, 
"""

# 2-edit repairs (384)
two_edits_sec = """
P@1= 40000s: 0.262, 36000s: 0.259, 32000s: 0.256, 28000s: 0.246, 24000s: 0.244, 20000s: 0.233, 16000s: 0.218, 12000s: 0.174, 8000s: 0.126, 4000s: 0.054, 
P@5= 40000s: 0.313, 36000s: 0.310, 32000s: 0.308, 28000s: 0.297, 24000s: 0.295, 20000s: 0.279, 16000s: 0.264, 12000s: 0.213, 8000s: 0.154, 4000s: 0.072, 
P@10= 40000s: 0.336, 36000s: 0.333, 32000s: 0.331, 28000s: 0.321, 24000s: 0.318, 20000s: 0.297, 16000s: 0.282, 12000s: 0.231, 8000s: 0.162, 4000s: 0.072, 
P@All= 40000s: 0.705, 36000s: 0.700, 32000s: 0.697, 28000s: 0.682, 24000s: 0.672, 20000s: 0.631, 16000s: 0.569, 12000s: 0.464, 8000s: 0.305, 4000s: 0.149, 
"""

three_edits_sec = """
P@1= 60000s: 0.130, 54000s: 0.130, 48000s: 0.117, 42000s: 0.104, 36000s: 0.091, 30000s: 0.091, 24000s: 0.065, 18000s: 0.052, 12000s: 0.013, 6000s: 0.000, 
P@5= 60000s: 0.143, 54000s: 0.143, 48000s: 0.130, 42000s: 0.117, 36000s: 0.104, 30000s: 0.104, 24000s: 0.065, 18000s: 0.052, 12000s: 0.013, 6000s: 0.000, 
P@10= 60000s: 0.156, 54000s: 0.156, 48000s: 0.143, 42000s: 0.130, 36000s: 0.117, 30000s: 0.104, 24000s: 0.065, 18000s: 0.052, 12000s: 0.013, 6000s: 0.000, 
P@All= 60000s: 0.506, 54000s: 0.468, 48000s: 0.455, 42000s: 0.403, 36000s: 0.377, 30000s: 0.325, 24000s: 0.221, 18000s: 0.156, 12000s: 0.065, 6000s: 0.013, 
"""

# All repairs (1233)
all_edits_sec = """
P@1= 9000s: 0.370, 8000s: 0.365, 7000s: 0.356, 6000s: 0.343, 5000s: 0.324, 4000s: 0.285, 3000s: 0.244, 2000s: 0.180, 1000s: 0.092
P@5= 9000s: 0.422, 8000s: 0.414, 7000s: 0.405, 6000s: 0.392, 5000s: 0.369, 4000s: 0.325, 3000s: 0.282, 2000s: 0.206, 1000s: 0.110
P@10= 9000s: 0.477, 8000s: 0.469, 7000s: 0.459, 6000s: 0.446, 5000s: 0.421, 4000s: 0.374, 3000s: 0.323, 2000s: 0.239, 1000s: 0.128
P@All= 9000s: 0.666, 8000s: 0.645, 7000s: 0.630, 6000s: 0.605, 5000s: 0.569, 4000s: 0.501, 3000s: 0.412, 2000s: 0.303, 1000s: 0.149
"""

# conda deactivate && conda activate cstk
if __name__ == '__main__':
    pattern = r"(P@\w+)=((?:\s+\d+s:\s+\d+\.\d+,?)+)"
    matches = re.findall(pattern, three_edits_sec)

    data = []
    for match in matches:
        key = match[0].strip()
        values = re.findall(r"(\d+)s:\s+(\d+\.\d+)", match[1])
        data.append((key, {k: float(v) for k, v in values}))

    print(json.dumps(data))

    plot_data(data, "3_bar_hillel_repair.tex")