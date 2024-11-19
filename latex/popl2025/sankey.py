from matplotlib.sankey import Sankey
import matplotlib.pyplot as plt
import tikzplotlib

fig = plt.figure(figsize = [10,10])
ax = fig.add_subplot(1,1,1)

Sankey(
  ax=ax, flows = [5136,-1725,-737,-730,-1646,-108,-48,-142],
  labels = ['Total', 'Top-1', 'Top-[2-10]', 'Top-11-99', 'Top-100+', 'NG', 'NR', 'OOM'],
  orientations = [0, -1, -1, -1, -1, -1, -1, -1],
  scale= 1/2500, trunklength=0.5,
  edgecolor = '#099368', facecolor = '#099368'
).finish()
plt.axis("off")
plt.show()

# tikzplotlib.save("sankey.tex")