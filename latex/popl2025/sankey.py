from matplotlib.sankey import Sankey
import matplotlib.pyplot as plt
import tikzplotlib

fig = plt.figure(figsize = [10,10])
ax = fig.add_subplot(1,1,1)

Sankey(ax=ax,  flows = [2247,-622,-475,-751,-253,-45,-101],
       labels = ['Snippets', 'Top-1', 'Top-[2-10]', 'Top-11+', 'NoGen', 'NoRec', 'Error'],
       orientations = [0, -1, -1, -1, -1, -1, -1],
       scale=1/2500, trunklength=1,
       edgecolor = '#099368', facecolor = '#099368'
       ).finish()
plt.axis("off")
# plt.show()

tikzplotlib.save("sankey.tex")