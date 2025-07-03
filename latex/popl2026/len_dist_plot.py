input_string = """
(|σ|∈[0, 10), Δ=1): Top-1/total: 141 / 152 ≈ 0.9276315789473685
(|σ|∈[0, 10), Δ=2): Top-1/total: 100 / 105 ≈ 0.9523809523809523
(|σ|∈[0, 10), Δ=3): Top-1/total: 20 / 54 ≈ 0.37037037037037035
(|σ|∈[10, 20), Δ=1): Top-1/total: 286 / 299 ≈ 0.9565217391304348
(|σ|∈[10, 20), Δ=2): Top-1/total: 266 / 299 ≈ 0.8896321070234113
(|σ|∈[10, 20), Δ=3): Top-1/total: 42 / 146 ≈ 0.2876712328767123
(|σ|∈[20, 30), Δ=1): Top-1/total: 264 / 293 ≈ 0.9010238907849829
(|σ|∈[20, 30), Δ=2): Top-1/total: 228 / 289 ≈ 0.7889273356401384
(|σ|∈[20, 30), Δ=3): Top-1/total: 40 / 150 ≈ 0.26666666666666666
(|σ|∈[30, 40), Δ=1): Top-1/total: 253 / 290 ≈ 0.8724137931034482
(|σ|∈[30, 40), Δ=2): Top-1/total: 218 / 289 ≈ 0.754325259515571
(|σ|∈[30, 40), Δ=3): Top-1/total: 51 / 154 ≈ 0.33116883116883117
(|σ|∈[40, 50), Δ=1): Top-1/total: 259 / 289 ≈ 0.8961937716262975
(|σ|∈[40, 50), Δ=2): Top-1/total: 205 / 292 ≈ 0.702054794520548
(|σ|∈[40, 50), Δ=3): Top-1/total: 34 / 114 ≈ 0.2982456140350877
(|σ|∈[50, 60), Δ=1): Top-1/total: 244 / 288 ≈ 0.8472222222222222
(|σ|∈[50, 60), Δ=2): Top-1/total: 144 / 202 ≈ 0.7128712871287128
(|σ|∈[50, 60), Δ=3): Top-1/total: 46 / 120 ≈ 0.38333333333333336
(|σ|∈[60, 70), Δ=1): Top-1/total: 213 / 240 ≈ 0.8875
(|σ|∈[60, 70), Δ=2): Top-1/total: 101 / 147 ≈ 0.6870748299319728
(|σ|∈[60, 70), Δ=3): Top-1/total: 30 / 95 ≈ 0.3157894736842105
(|σ|∈[70, 80), Δ=1): Top-1/total: 158 / 176 ≈ 0.8977272727272727
(|σ|∈[70, 80), Δ=2): Top-1/total: 73 / 110 ≈ 0.6636363636363637
(|σ|∈[70, 80), Δ=3): Top-1/total: 19 / 63 ≈ 0.30158730158730157
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