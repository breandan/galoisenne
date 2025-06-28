input_string = """
(|σ|∈[0, 10), Δ=1): Top-1/total: 31 / 32 ≈ 0.96875
(|σ|∈[0, 10), Δ=2): Top-1/total: 24 / 28 ≈ 0.8571428571428571
(|σ|∈[0, 10), Δ=3): Top-1/total: 0 / 4 ≈ 0.0
(|σ|∈[10, 20), Δ=1): Top-1/total: 163 / 186 ≈ 0.8763440860215054
(|σ|∈[10, 20), Δ=2): Top-1/total: 84 / 95 ≈ 0.8842105263157894
(|σ|∈[10, 20), Δ=3): Top-1/total: 2 / 28 ≈ 0.07142857142857142
(|σ|∈[20, 30), Δ=1): Top-1/total: 210 / 251 ≈ 0.8366533864541833
(|σ|∈[20, 30), Δ=2): Top-1/total: 81 / 108 ≈ 0.75
(|σ|∈[20, 30), Δ=3): Top-1/total: 9 / 32 ≈ 0.28125
(|σ|∈[30, 40), Δ=1): Top-1/total: 176 / 213 ≈ 0.8262910798122066
(|σ|∈[30, 40), Δ=2): Top-1/total: 76 / 102 ≈ 0.7450980392156863
(|σ|∈[30, 40), Δ=3): Top-1/total: 11 / 38 ≈ 0.2894736842105263
(|σ|∈[40, 50), Δ=1): Top-1/total: 87 / 125 ≈ 0.696
(|σ|∈[40, 50), Δ=2): Top-1/total: 45 / 75 ≈ 0.6
(|σ|∈[40, 50), Δ=3): Top-1/total: 7 / 24 ≈ 0.2916666666666667
(|σ|∈[50, 60), Δ=1): Top-1/total: 58 / 96 ≈ 0.6041666666666666
(|σ|∈[50, 60), Δ=2): Top-1/total: 35 / 63 ≈ 0.5555555555555556
(|σ|∈[50, 60), Δ=3): Top-1/total: 3 / 12 ≈ 0.25
(|σ|∈[60, 70), Δ=1): Top-1/total: 28 / 60 ≈ 0.4666666666666667
(|σ|∈[60, 70), Δ=2): Top-1/total: 26 / 50 ≈ 0.52
(|σ|∈[60, 70), Δ=3): Top-1/total: 4 / 15 ≈ 0.26666666666666666
(|σ|∈[70, 80), Δ=1): Top-1/total: 34 / 68 ≈ 0.5
(|σ|∈[70, 80), Δ=2): Top-1/total: 20 / 35 ≈ 0.5714285714285714
(|σ|∈[70, 80), Δ=3): Top-1/total: 2 / 15 ≈ 0.13333333333333333
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