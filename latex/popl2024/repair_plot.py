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
    second_mapping = {f"{i}": i for i in range(20, 300, 40)}

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
    ax.legend(handles=[bar[0] for bar in bars])


    ax.set_xlabel('Milliseconds')
    ax.set_xticks(ind)
    ax.set_xticklabels(list(second_mapping.keys()))
    # ax.bar(ind, vals, width, bottom=bottom, color=colors[i], label=label)
    # ax.legend()
    ax.legend(loc='upper left')
    ax.set_ylabel('Precision@k')
    ax.set_title('$\Delta\in[1,3]$ Repair Precision')

    # ax.set_ylim([0,1])  # Explicitly set y-axis limits

    fig.set_size_inches(25, 6)

#     plt.savefig("test.png", dpi=500)
    tikzplotlib.save(filename)

# 1-edit repairs
one_edits_sec = """
P@1=  300s: 0.471, 280s: 0.449, 260s: 0.442, 240s: 0.435, 220s: 0.428, 200s: 0.37, 180s: 0.355, 160s: 0.333, 140s: 0.312, 120s: 0.283, 100s: 0.268, 80s: 0.232, 60s: 0.203, 40s: 0.159, 20s: 0.116, 10s: 0.094
P@5=  300s: 0.710, 280s: 0.674, 260s: 0.667, 240s: 0.652, 220s: 0.638, 200s: 0.594, 180s: 0.565, 160s: 0.514, 140s: 0.493, 120s: 0.464, 100s: 0.42, 80s: 0.37, 60s: 0.304, 40s: 0.239, 20s: 0.145, 10s: 0.109
P@10= 300s: 0.768, 280s: 0.739, 260s: 0.732, 240s: 0.717, 220s: 0.703, 200s: 0.63, 180s: 0.594, 160s: 0.543, 140s: 0.507, 120s: 0.471, 100s: 0.42, 80s: 0.37, 60s: 0.304, 40s: 0.239, 20s: 0.145, 10s: 0.109
P@All= 300s: 0.783, 280s: 0.746, 260s: 0.739, 240s: 0.725, 220s: 0.71, 200s: 0.638, 180s: 0.594, 160s: 0.543, 140s: 0.507, 120s: 0.471, 100s: 0.42, 80s: 0.37, 60s: 0.304, 40s: 0.239, 20s: 0.145, 10s: 0.109
"""

# 2-edit repairs (384)
two_edits_sec = """
P@1=  300s: 0.048, 280s: 0.048, 260s: 0.032, 240s: 0.032, 220s: 0.032, 200s: 0.032, 180s: 0.048, 160s: 0.048, 140s: 0.065, 120s: 0.016, 100s: 0.016, 80s: 0.0, 60s: 0.0, 40s: 0.0, 20s: 0.016, 10s: 0.016
P@5=  300s: 0.129, 280s: 0.129, 260s: 0.113, 240s: 0.113, 220s: 0.129, 200s: 0.129, 180s: 0.113, 160s: 0.113, 140s: 0.129, 120s: 0.048, 100s: 0.065, 80s: 0.032, 60s: 0.032, 40s: 0.032, 20s: 0.048, 10s: 0.016
P@10= 300s: 0.258, 280s: 0.258, 260s: 0.242, 240s: 0.258, 220s: 0.258, 200s: 0.258, 180s: 0.226, 160s: 0.21, 140s: 0.21, 120s: 0.129, 100s: 0.113, 80s: 0.081, 60s: 0.081, 40s: 0.065, 20s: 0.048, 10s: 0.016
P@All= 300s: 0.419, 280s: 0.419, 260s: 0.387, 240s: 0.355, 220s: 0.355, 200s: 0.355, 180s: 0.306, 160s: 0.29, 140s: 0.274, 120s: 0.194, 100s: 0.177, 80s: 0.113, 60s: 0.081, 40s: 0.065, 20s: 0.048, 10s: 0.016
"""

# All repairs (1233)
all_edits_sec = """
P@1=  300s: 0.324, 280s: 0.318, 260s: 0.306, 240s: 0.301, 220s: 0.295, 200s: 0.272, 180s: 0.272, 160s: 0.254, 140s: 0.243, 120s: 0.208, 100s: 0.202, 80s: 0.179, 60s: 0.162, 40s: 0.127, 20s: 0.098, 10s: 0.081
P@5=  300s: 0.497, 280s: 0.486, 260s: 0.474, 240s: 0.468, 220s: 0.468, 200s: 0.462, 180s: 0.445, 160s: 0.41, 140s: 0.405, 120s: 0.358, 100s: 0.335, 80s: 0.295, 60s: 0.249, 40s: 0.202, 20s: 0.133, 10s: 0.092
P@10= 300s: 0.59, 280s: 0.584, 260s: 0.572, 240s: 0.572, 220s: 0.566, 200s: 0.538, 180s: 0.509, 160s: 0.468, 140s: 0.445, 120s: 0.393, 100s: 0.353, 80s: 0.312, 60s: 0.266, 40s: 0.214, 20s: 0.133, 10s: 0.092
P@All= 300s: 0.676, 280s: 0.665, 260s: 0.642, 240s: 0.624, 220s: 0.618, 200s: 0.59, 180s: 0.543, 160s: 0.503, 140s: 0.474, 120s: 0.422, 100s: 0.382, 80s: 0.329, 60s: 0.272, 40s: 0.22, 20s: 0.133, 10s: 0.092
"""

one_edits_ms = """
P@1=   1700s: 0.446, 1500s: 0.442, 1300s: 0.437, 1100s: 0.428, 900s: 0.418, 700s: 0.401, 500s: 0.378, 300s: 0.327, 100s: 0.211
P@5=   1700s: 0.791, 1500s: 0.785, 1300s: 0.777, 1100s: 0.762, 900s: 0.743, 700s: 0.724, 500s: 0.686, 300s: 0.601, 100s: 0.379
P@10=  1700s: 0.87, 1500s: 0.862, 1300s: 0.851, 1100s: 0.834, 900s: 0.812, 700s: 0.786, 500s: 0.741, 300s: 0.645, 100s: 0.402
P@All= 1700s: 0.912, 1500s: 0.904, 1300s: 0.891, 1100s: 0.873, 900s: 0.849, 700s: 0.822, 500s: 0.773, 300s: 0.672, 100s: 0.416
"""

two_edits_ms = """
P@1=   1700s: 0.078, 1500s: 0.076, 1300s: 0.072, 1100s: 0.064, 900s: 0.061, 700s: 0.057, 500s: 0.052, 300s: 0.038, 100s: 0.016
P@5=   1700s: 0.167, 1500s: 0.163, 1300s: 0.155, 1100s: 0.144, 900s: 0.142, 700s: 0.132, 500s: 0.123, 300s: 0.1, 100s: 0.05
P@10=  1700s: 0.238, 1500s: 0.23, 1300s: 0.221, 1100s: 0.208, 900s: 0.2, 700s: 0.182, 500s: 0.164, 300s: 0.128, 100s: 0.06
P@All= 1700s: 0.366, 1500s: 0.353, 1300s: 0.335, 1100s: 0.309, 900s: 0.292, 700s: 0.261, 500s: 0.225, 300s: 0.165, 100s: 0.067
"""

three_edits_ms = """
P@1=   1700s: 0.013, 1500s: 0.013, 1300s: 0.011, 1100s: 0.011, 900s: 0.011, 700s: 0.008, 500s: 0.008, 300s: 0.003, 100s: 0.0
P@5=   1700s: 0.018, 1500s: 0.014, 1300s: 0.011, 1100s: 0.011, 900s: 0.011, 700s: 0.01, 500s: 0.008, 300s: 0.005, 100s: 0.002
P@10=  1700s: 0.023, 1500s: 0.023, 1300s: 0.018, 1100s: 0.019, 900s: 0.018, 700s: 0.014, 500s: 0.01, 300s: 0.01, 100s: 0.003
P@All= 1700s: 0.145, 1500s: 0.133, 1300s: 0.116, 1100s: 0.109, 900s: 0.09, 700s: 0.072, 500s: 0.064, 300s: 0.035, 100s: 0.01
"""

all_edits_ms = """
P@1=   1700s: 0.265, 1500s: 0.262, 1300s: 0.258, 1100s: 0.250, 900s: 0.244, 700s: 0.233, 500s: 0.219, 300s: 0.187, 100s: 0.117
P@5=   1700s: 0.479, 1500s: 0.474, 1300s: 0.467, 1100s: 0.455, 900s: 0.444, 700s: 0.430, 500s: 0.406, 300s: 0.353, 100s: 0.218
P@10=  1700s: 0.546, 1500s: 0.540, 1300s: 0.530, 1100s: 0.517, 900s: 0.502, 700s: 0.481, 500s: 0.451, 300s: 0.387, 100s: 0.234
P@All= 1700s: 0.629, 1500s: 0.619, 1300s: 0.603, 1100s: 0.584, 900s: 0.562, 700s: 0.535, 500s: 0.496, 300s: 0.417, 100s: 0.245
"""

# conda deactivate && conda activate cstk
if __name__ == '__main__':
    pattern = r"(P@\w+)=((?:\s+\d+s:\s+\d+\.\d+,?)+)"
    matches = re.findall(pattern, all_edits_sec)

    data = []
    for match in matches:
        key = match[0].strip()
        values = re.findall(r"(\d+)s:\s+(\d+\.\d+)", match[1])
        data.append((key, {k: float(v) for k, v in values}))

    print(json.dumps(data))

    plot_data(data, "repair1-3_plot.tex")