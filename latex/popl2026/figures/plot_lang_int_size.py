import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import matplot2tikz

# Read the CSV file
df = pd.read_csv('intersections.csv')

# Strip any leading/trailing spaces from column names
df.columns = df.columns.str.strip()

# Define the required columns
required_cols = ['length', 'lev_margin', 'lang_size']

# Check if all required columns are present
missing_cols = [col for col in required_cols if col not in df.columns]
if missing_cols:
    raise ValueError(f"Missing columns in CSV: {missing_cols}. Check the CSV file for correct column names.")

# Select the relevant columns
df = df[required_cols]

# Compute the natural logarithm of lang_size
df['log_lang_size'] = np.log(df['lang_size'])

# Get unique lev_margin values
unique_margins = df['lev_margin'].unique()

# Create a color map with distinct colors
cmap = plt.get_cmap('tab10')
colors = [cmap(i % cmap.N) for i in range(len(unique_margins))]

# Create figure and axes with width 3x height
fig, ax = plt.subplots(figsize=(12, 4))

# Create the scatter plot with smaller dots (s=18)
for i, margin in enumerate(unique_margins):
    subset = df[df['lev_margin'] == margin]
    ax.scatter(subset['length'], subset['log_lang_size'], s=18, color=colors[i], label=f'Lev Margin {margin}')

# Add labels and title with LaTeX formatting
ax.set_xlabel(r'$|\sigma|$')
ax.set_ylabel(r'$\log_2|\ell_\cap|$')
ax.set_title('Scatter Plot of Length vs. Log(Lang Size) by Lev Margin')

# Add legend
ax.legend()

# Remove top and right borders, set ticks to bottom and left
ax.spines['top'].set_visible(False)
ax.spines['right'].set_visible(False)
ax.xaxis.set_ticks_position('bottom')
ax.yaxis.set_ticks_position('left')

# Save the plot as TikZ code
matplot2tikz.save('scatter_plot.tex')

# Close the plot to free memory
plt.close()