import matplotlib.pyplot as plt
import numpy as np

# Data
cores = np.array([1, 2, 3, 4, 5, 6, 7, 8, 9, 10])
holes = [2, 3, 4, 5, 6]

# Relative improvements for each core count and hole count
# Each sublist corresponds to a different hole count (2, 3, 4, 5, 6)
relative_improvements = [
    [0.0, 0.1343, 0.1955, 0.2249, 0.2475, 0.2760, 0.2994, 0.3073, 0.3151, 0.3229], # holes = 2
    [0.0, 0.2655, 0.4353, 0.5614, 0.6644, 0.7462, 0.7783, 0.8347, 0.8005, 0.7798], # holes = 3
    [0.0, 0.3972, 0.6928, 0.9327, 1.1834, 1.3138, 1.3988, 1.6039, 1.5500, 1.5691], # holes = 4
    [0.0, 0.4863, 0.8326, 1.1368, 1.3879, 1.5873, 1.7494, 1.8802, 1.9059, 1.9625], # holes = 5
    [0.0, 0.4315, 0.7583, 1.0122, 1.2593, 1.4586, 1.6349, 1.7813, 1.8324, 1.8695]  # holes = 6
]

# Plotting
fig, ax = plt.subplots(figsize=(12, 8))

# Width of a bar
barWidth = 0.15

# Set position of bar on X axis
positions = [np.arange(len(cores))]

for i in range(1, len(holes)):
    positions.append([x + barWidth for x in positions[i-1]])

# Make the plot
for i in range(len(holes)):
    ax.bar(positions[i], relative_improvements[i], width=barWidth, edgecolor='grey', label=f'Holes={holes[i]}')

# Add xticks on the middle of the group bars
ax.set_xlabel('Number of Cores', fontweight='bold')
ax.set_ylabel('Relative Improvement over Single Core', fontweight='bold')
ax.set_xticks([r + barWidth for r in range(len(cores))])
ax.set_xticklabels(cores)

# Create legend & Show graphic
ax.legend()
plt.title('Relative Number of Distinct Solutions Found in 10s vs. Single Core')
# plt.savefig('relative_speedup.pgf')
plt.show()