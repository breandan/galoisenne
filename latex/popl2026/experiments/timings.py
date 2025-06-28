import matplotlib.pyplot as plt
import pandas as pd
import matplot2tikz as tikzplotlib


# Load your data (assuming it's in 'data.csv')
df = pd.read_csv("timings.csv")

# Calculate Y values
df['y'] = df['total_ms']

# Color mapping for lev_dist
color_map = {1: 'green', 2: 'blue', 3: 'red', 4: 'violet'}

fig, ax = plt.subplots()

# Scatter plot with different colors based on lev_dist
for lev_dist in df['lev_dist'].unique():
    data = df[df['lev_dist'] == lev_dist]
    ax.scatter(data['length'], data['y'], color=color_map[lev_dist], label=f'lev\_dist = {lev_dist}', s=60)

# Labels and legend
ax.set_yscale('log')

ax.set_xlabel("Length")
ax.set_ylabel('log(ms)')
# ax.legend()

plt.rcParams.update({"text.usetex": True, "pgf.rcfonts": False})

# Save as a .pgf file (LaTeX ready)
# plt.show()
# plt.savefig('timings.pgf')
tikzplotlib.save("timings.tex")
