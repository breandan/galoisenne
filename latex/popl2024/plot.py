import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import scipy.stats as stats
import tikzplotlib

# Assuming data is already loaded into DataFrame 'data'

data = pd.read_csv('log.csv')  # replace with your actual file path

# Create bins
bin_size = 2
bins = np.arange(min(data['numTks']), max(data['numTks']) + bin_size, bin_size)

# Create a color dictionary
color_dict = {1: 'green', 2: 'blue', 3: 'red'}

# Create a new column 'bins' in the dataframe to categorize 'numTks' into bins
data['bins'] = pd.cut(data['numTks'], bins, include_lowest=True)

# Define a function to filter outliers in a Series
def filter_outliers(s):
    Q1 = s.quantile(0.25)
    Q3 = s.quantile(0.75)
    IQR = Q3 - Q1
    return s[(s >= Q1 - 50.5 * IQR) & (s <= Q3 + 50.5 * IQR)]

# Apply this function to 'totalValid' for each 'totalEdts' type and bin
data['totalValid'] = data.groupby(['bins', 'totalEdts'])['totalValid'].transform(filter_outliers)

# Calculate means and confidence intervals for each bin and each type
grouped = data.groupby(['bins', 'totalEdts']).agg(
    mean=('totalValid', 'mean'),
    low=('totalValid', lambda x: stats.t.interval(0.95, len(x)-1, loc=np.mean(x), scale=stats.sem(x))[0]),
    high=('totalValid', lambda x: stats.t.interval(0.95, len(x)-1, loc=np.mean(x), scale=stats.sem(x))[1])
).reset_index()

# Create figure and axis objects
fig, ax = plt.subplots()

# Plot mean lines with error bars for each type
for edts_type in data['totalEdts'].unique():
    temp_data = grouped[grouped['totalEdts'] == edts_type]
    ax.errorbar(range(len(temp_data)), temp_data['mean'],
                yerr=[temp_data['mean']-temp_data['low'], temp_data['high']-temp_data['mean']],
                color=color_dict[edts_type],
                fmt='-o')

# Set labels and title
ax.set_xticks(range(len(grouped['bins'].unique())))
ax.set_xticklabels([str(b) for b in grouped['bins'].unique()])
ax.set_xlabel('numTks (binned)')
ax.set_ylabel('totalValid (mean)')
ax.set_title('Line plot of numTks vs totalValid with error bars')
ax.set_yscale('log')

# Plot mean lines with error bars for each type
for edts_type in data['totalEdts'].unique():
    temp_data = grouped[grouped['totalEdts'] == edts_type]
    ax.errorbar(range(len(temp_data)), temp_data['mean'],
                yerr=[temp_data['mean']-temp_data['low'], temp_data['high']-temp_data['mean']],
                color=color_dict[edts_type],
                fmt='-o', label=f'Type {edts_type}')

# Add a legend
ax.legend()

# Display the plot
# plt.show()
tikzplotlib.save("throughput.tex")