import pandas as pd
import matplotlib.pyplot as plt
import math

df = pd.read_csv('pair_reduction_benchmark_.csv')

order_stats = df.groupby('order')['total_ms'].agg(['median', 'mean', 'std'])
orders_sorted = sorted(order_stats.index.tolist())

# Plot 1
fig, ax = plt.subplots(figsize=(10, 6))
data_sorted = [df[df['order'] == order]['total_ms'].values for order in orders_sorted]
bp = ax.boxplot(data_sorted, positions=range(1, len(orders_sorted) + 1))
ax.set_xticks(range(1, len(orders_sorted) + 1))
ax.set_xticklabels(orders_sorted)
ax.set_xlabel('Filter Order (A=altitude, D=debris, P=plane)', fontsize=12)
ax.set_ylabel('Median Total Time (ms)', fontsize=12)
ax.set_title('Total Time by Filter Order', fontsize=14, fontweight='bold')
ax.grid(True, alpha=0.3)

for i, order in enumerate(orders_sorted):
    median = order_stats.loc[order, 'median']
    ax.annotate(f'{median:.0f}ms', xy=(i + 1, median), ha='center', va='bottom', fontsize=10)

plt.tight_layout()
plt.savefig('1_total_time.png', dpi=300, bbox_inches='tight')
plt.close()

# Plot 2
n_orders = len(orders_sorted)
n_cols = 3
n_rows = math.ceil(n_orders / n_cols)
fig, axes = plt.subplots(n_rows, n_cols, figsize=(6*n_cols, 6*n_rows))
axes = axes.flatten()

filter_colors = {'debris': '#D62839', 'altitude': '#2ca02c', 'plane': '#1f77b4'}

for i, order in enumerate(orders_sorted):
    order_data = df[df['order'] == order]
    filter_names = [order_data['filter1'].iloc[0], order_data['filter2'].iloc[0], order_data['filter3'].iloc[0]]
    filter_times = [order_data['time1_ms'].median(), order_data['time2_ms'].median(), order_data['time3_ms'].median()]
    colors = [filter_colors[f] for f in filter_names]

    wedges, texts, autotexts = axes[i].pie(filter_times, labels=filter_names, autopct='%1.1f%%',
                                            colors=colors, startangle=90, textprops={'fontsize': 12})
    for autotext in autotexts:
        autotext.set_fontsize(11)
        autotext.set_fontweight('bold')

    total = order_stats.loc[order, 'median']
    axes[i].set_title(f'{order} ({total:.0f}ms)', fontsize=14, fontweight='bold')

for i in range(n_orders, len(axes)):
    axes[i].axis('off')

plt.tight_layout()
plt.savefig('2_time_breakdown_pie.png', dpi=300, bbox_inches='tight')
plt.close()

# Plot 3
fig, axes = plt.subplots(n_rows, n_cols, figsize=(6*n_cols, 5*n_rows))
axes = axes.flatten()

for i, order in enumerate(orders_sorted):
    order_data = df[df['order'] == order].iloc[0]
    filter_names = [order_data['filter1'], order_data['filter2'], order_data['filter3']]
    counts = [order_data['total_pairs'], order_data['after1'], order_data['after2'], order_data['after3']]
    labels = ['Start'] + filter_names
    colors_bars = ['#888888'] + [filter_colors[f] for f in filter_names]

    axes[i].bar(range(len(counts)), counts, color=colors_bars)
    axes[i].set_xticks(range(len(counts)))
    axes[i].set_xticklabels(labels, fontsize=10)
    axes[i].set_ylabel('Pairs', fontsize=10)
    axes[i].set_title(f'{order}', fontsize=14, fontweight='bold')
    axes[i].grid(True, alpha=0.3, axis='y')

    for j, (count, label) in enumerate(zip(counts, labels)):
        axes[i].text(j, count, f'{count/1e6:.0f}M', ha='center', va='bottom', fontsize=9)

for i in range(n_orders, len(axes)):
    axes[i].axis('off')

plt.tight_layout()
plt.savefig('3_pair_reduction.png', dpi=300, bbox_inches='tight')
plt.close()
