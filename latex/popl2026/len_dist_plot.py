input_string = """
(|σ|∈[0, 10), Δ=1): Top-1/total: 29 / 33 ≈ 0.8787878787878788
(|σ|∈[0, 10), Δ=2): Top-1/total: 21 / 22 ≈ 0.9545454545454546
(|σ|∈[0, 10), Δ=3): Top-1/total: 22 / 54 ≈ 0.4074074074074074
(|σ|∈[10, 20), Δ=1): Top-1/total: 116 / 120 ≈ 0.9666666666666667
(|σ|∈[10, 20), Δ=2): Top-1/total: 53 / 59 ≈ 0.8983050847457628
(|σ|∈[10, 20), Δ=3): Top-1/total: 39 / 143 ≈ 0.2727272727272727
(|σ|∈[20, 30), Δ=1): Top-1/total: 130 / 145 ≈ 0.896551724137931
(|σ|∈[20, 30), Δ=2): Top-1/total: 65 / 80 ≈ 0.8125
(|σ|∈[20, 30), Δ=3): Top-1/total: 42 / 151 ≈ 0.2781456953642384
(|σ|∈[30, 40), Δ=1): Top-1/total: 106 / 118 ≈ 0.8983050847457628
(|σ|∈[30, 40), Δ=2): Top-1/total: 41 / 62 ≈ 0.6612903225806451
(|σ|∈[30, 40), Δ=3): Top-1/total: 36 / 142 ≈ 0.2535211267605634
(|σ|∈[40, 50), Δ=1): Top-1/total: 78 / 91 ≈ 0.8571428571428571
(|σ|∈[40, 50), Δ=2): Top-1/total: 25 / 42 ≈ 0.5952380952380952
(|σ|∈[40, 50), Δ=3): Top-1/total: 34 / 108 ≈ 0.3148148148148148
(|σ|∈[50, 60), Δ=1): Top-1/total: 44 / 51 ≈ 0.8627450980392157
(|σ|∈[50, 60), Δ=2): Top-1/total: 32 / 48 ≈ 0.6666666666666666
(|σ|∈[50, 60), Δ=3): Top-1/total: 44 / 106 ≈ 0.41509433962264153
(|σ|∈[60, 70), Δ=1): Top-1/total: 34 / 38 ≈ 0.8947368421052632
(|σ|∈[60, 70), Δ=2): Top-1/total: 29 / 37 ≈ 0.7837837837837838
(|σ|∈[60, 70), Δ=3): Top-1/total: 32 / 92 ≈ 0.34782608695652173
(|σ|∈[70, 80), Δ=1): Top-1/total: 41 / 46 ≈ 0.8913043478260869
(|σ|∈[70, 80), Δ=2): Top-1/total: 30 / 43 ≈ 0.6976744186046512
(|σ|∈[70, 80), Δ=3): Top-1/total: 22 / 69 ≈ 0.3188405797101449
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