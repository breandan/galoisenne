input_string = """
(|σ|∈[0, 10), Δ=1): Top-1/total: 32 / 38 ≈ 0.8421052631578947
(|σ|∈[0, 10), Δ=2): Top-1/total: 29 / 31 ≈ 0.9354838709677419
(|σ|∈[0, 10), Δ=3): Top-1/total: 19 / 58 ≈ 0.3275862068965517
(|σ|∈[10, 20), Δ=1): Top-1/total: 128 / 144 ≈ 0.8888888888888888
(|σ|∈[10, 20), Δ=2): Top-1/total: 55 / 67 ≈ 0.8208955223880597
(|σ|∈[10, 20), Δ=3): Top-1/total: 41 / 170 ≈ 0.2411764705882353
(|σ|∈[20, 30), Δ=1): Top-1/total: 144 / 167 ≈ 0.8622754491017964
(|σ|∈[20, 30), Δ=2): Top-1/total: 64 / 88 ≈ 0.7272727272727273
(|σ|∈[20, 30), Δ=3): Top-1/total: 41 / 174 ≈ 0.23563218390804597
(|σ|∈[30, 40), Δ=1): Top-1/total: 109 / 131 ≈ 0.8320610687022901
(|σ|∈[30, 40), Δ=2): Top-1/total: 45 / 68 ≈ 0.6617647058823529
(|σ|∈[30, 40), Δ=3): Top-1/total: 38 / 161 ≈ 0.2360248447204969
(|σ|∈[40, 50), Δ=1): Top-1/total: 84 / 108 ≈ 0.7777777777777778
(|σ|∈[40, 50), Δ=2): Top-1/total: 30 / 50 ≈ 0.6
(|σ|∈[40, 50), Δ=3): Top-1/total: 39 / 133 ≈ 0.2932330827067669
(|σ|∈[50, 60), Δ=1): Top-1/total: 48 / 61 ≈ 0.7868852459016393
(|σ|∈[50, 60), Δ=2): Top-1/total: 31 / 52 ≈ 0.5961538461538461
(|σ|∈[50, 60), Δ=3): Top-1/total: 44 / 124 ≈ 0.3548387096774194
(|σ|∈[60, 70), Δ=1): Top-1/total: 36 / 47 ≈ 0.7659574468085106
(|σ|∈[60, 70), Δ=2): Top-1/total: 35 / 50 ≈ 0.7
(|σ|∈[60, 70), Δ=3): Top-1/total: 36 / 110 ≈ 0.32727272727272727
(|σ|∈[70, 80), Δ=1): Top-1/total: 40 / 51 ≈ 0.7843137254901961
(|σ|∈[70, 80), Δ=2): Top-1/total: 28 / 50 ≈ 0.56
(|σ|∈[70, 80), Δ=3): Top-1/total: 19 / 78 ≈ 0.24358974358974358
"""

# Split the input string into lines
lines = input_string.strip().splitlines()

# Initialize lists for each Δ category
green = []  # Δ=1
blue = []   # Δ=2
orange = [] # Δ=3

# Process lines in groups of three (one group per bucket)
for i in range(0, 24, 3):
    green.append(float(lines[i].split("≈")[1].strip()))
    blue.append(float(lines[i+1].split("≈")[1].strip()))
    orange.append(float(lines[i+2].split("≈")[1].strip()))

# Define x-values for the eight buckets
x_values = [0, 10, 20, 30, 40, 50, 60, 70]

# Generate coordinate strings
green_coords = " ".join([f"({x}, {val})" for x, val in zip(x_values, green)])
blue_coords = " ".join([f"({x}, {val})" for x, val in zip(x_values, blue)])
orange_coords = " ".join([f"({x}, {val})" for x, val in zip(x_values, orange)])

# Define the TikZ template
tikz_template = r"""
\addplot[green, fill=green!50] coordinates {{ {green_coords} }};
\addplot[blue, fill=blue!50] coordinates {{ {blue_coords} }};
\addplot[orange, fill=orange!50] coordinates {{ {orange_coords} }};
"""

# Generate the TikZ code
tikz_code = tikz_template.format(green_coords=green_coords, blue_coords=blue_coords, orange_coords=orange_coords)

# Output the result
print(tikz_code)