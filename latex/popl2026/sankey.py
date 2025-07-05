from matplotlib.sankey import Sankey
import matplotlib.pyplot as plt
import matplot2tikz

fig = plt.figure(figsize = [10,10])
ax = fig.add_subplot(1,1,1)

Sankey(
  ax=ax, flows = [2211,-677,-344,-97,-377,-716],
  labels = ['Total', 'Top-1', 'Top-[2-10]', 'Top-11-99', 'Top-100+', 'NR'],
  orientations = [0, 1, 1, 1, 1, -1],
  scale= 1/3500, trunklength=1,
  edgecolor = '#099368', facecolor = '#099368'
).finish()
plt.axis("off")
# plt.show()

matplot2tikz.save("sankey.tex")