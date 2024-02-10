import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import tikzplotlib

# Load the data into a pandas DataFrame
data = pd.read_csv('bar_hillel_niagara.csv', sep=r'\s*,\s*')

max_samples = data['samples'].max() + 1
print(f"Max samples: {max_samples}")
# Create buckets for the 'samples' column in increments of 5
buckets = np.arange(0, max_samples, 100)
bucket_labels = buckets[1:]  # Label each bucket by its upper limit

# Initialize a DataFrame to hold the percentage of instances per bucket and level
percentages = pd.DataFrame(columns=bucket_labels)

# Calculate percentages for each 'lev'
for lev in sorted(data['lev'].unique()):
    print("Calculating percentages for lev =", lev)
    lev_data = data[data['lev'] == lev]
    counts, _ = np.histogram(lev_data['samples'], bins=buckets)
    cum_counts = np.cumsum(counts) / len(lev_data) * 100  # Calculate cumulative percentage
    percentages.loc[lev] = cum_counts

print('Done calculating percentages')

# Plotting
fig, ax = plt.subplots(figsize=(10, 6))

# Plot a separate bar for each 'lev'
for lev in percentages.index:
    print(lev)
    ax.bar(np.arange(len(percentages.columns)), percentages.loc[lev], 1.0, label=f"$\Delta_L={lev}$")

ax.set_xlabel('Samples drawn (log scale)')
ax.set_ylabel('% matching human repair in $\leq N$ samples')
ax.set_title('Sample Efficiency of Levenshtein-Bar-Hillel Sampler on StackOverflow Repairs $\Delta_L(\sigma, \sigma\')\in[1, 3]$')

# Calculate tick spacing to only show about ten or so ticks across the x-axis
total_buckets = len(bucket_labels)
desired_ticks = 10
tick_spacing = max(1, total_buckets // desired_ticks)  # Ensure at least 1 to avoid division by zero

ticks = np.arange(0, total_buckets, tick_spacing)
# tick_labels = [bucket_labels[int(i)] for i in ticks]
tick_labels = [f'$10^{i}$' for i in range(-2, 6)]

ax.set_xticks(ticks)
ax.set_xscale('log')

ax.set_xticklabels(tick_labels)  # Optional rotation for better label readability

# Put the legend in the bottom right corner
# ax.legend()

plt.tight_layout()

# Show the plot
# plt.show()
tikzplotlib.save("sample_efficiency.tex")
