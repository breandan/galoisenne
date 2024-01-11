import matplotlib.pyplot as plt
import numpy as np

# Data
holes = [3, 4, 5, 6]
enumSeq = [37.81, 192.52, 2674.49, 3558.9]
sampleSeq = [65.62, 545.72, 3816.67, 8136.24]
solveSeq = [79.61, 1084.87, 8473.4, 146614.8]

# Number of bar groups
n_groups = len(holes)

# Create a bar plot
fig, ax = plt.subplots()

index = np.arange(n_groups)
bar_width = 0.2
opacity = 0.8

rects1 = ax.bar(index, enumSeq, bar_width, alpha=opacity, color='b', label='enumSeq')
rects2 = ax.bar(index + bar_width, sampleSeq, bar_width, alpha=opacity, color='r', label='sampleSeq')
rects3 = ax.bar(index + 2*bar_width, solveSeq, bar_width, alpha=opacity, color='g', label='solveSeq')

ax.set_xlabel('Number of Holes')
ax.set_ylabel('Averages')
ax.set_title('Average Results by Number of Holes and Method')
ax.set_xticks(index + bar_width)
ax.set_xticklabels(holes)
ax.set_yscale('log')
ax.legend()

plt.tight_layout()
plt.savefig('histogram.pgf')
