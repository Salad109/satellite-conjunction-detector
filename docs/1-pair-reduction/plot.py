import pandas as pd
import matplotlib.pyplot as plt

df = pd.read_csv('pair_reduction_benchmark.csv')

order_stats = df.groupby('order')['time_ms'].agg(['median', 'mean', 'std', 'min', 'max'])
orders_sorted = order_stats.sort_values('median').index.tolist()

# Plot 1: Box plot of total time by filter order
fig, ax = plt.subplots(figsize=(10, 6))
data_sorted = [df[df['order'] == order]['time_ms'].values for order in orders_sorted]
bp = ax.boxplot(data_sorted, positions=range(1, len(orders_sorted) + 1))
ax.set_xticks(range(1, len(orders_sorted) + 1))
ax.set_xticklabels(orders_sorted)
ax.set_xlabel('Filter Order (A=altitude, D=debris, P=plane)', fontsize=12)
ax.set_ylabel('Total Time (ms)', fontsize=12)
ax.set_title('Pair Reduction Time by Filter Order', fontsize=14, fontweight='bold')
ax.grid(True, alpha=0.3)

for i, order in enumerate(orders_sorted):
    median = order_stats.loc[order, 'median']
    ax.annotate(f'{median:.0f}ms', xy=(i + 1, median), ha='center', va='bottom', fontsize=10)

plt.tight_layout()
plt.savefig('1_total_time.png', dpi=300, bbox_inches='tight')
plt.close()

# Plot 2: Bar chart comparing median times with error bars
fig, ax = plt.subplots(figsize=(10, 6))
medians = [order_stats.loc[order, 'median'] for order in orders_sorted]
stds = [order_stats.loc[order, 'std'] for order in orders_sorted]

bars = ax.bar(orders_sorted, medians, yerr=stds, capsize=5, color='#1f77b4', alpha=0.8)
ax.set_xlabel('Filter Order (A=altitude, D=debris, P=plane)', fontsize=12)
ax.set_ylabel('Median Time (ms)', fontsize=12)
ax.set_title('Pair Reduction Time Comparison', fontsize=14, fontweight='bold')
ax.grid(True, alpha=0.3, axis='y')

# Add value labels on bars
for bar, median in zip(bars, medians):
    ax.text(bar.get_x() + bar.get_width()/2, bar.get_height() + 50,
            f'{median:.0f}ms', ha='center', va='bottom', fontsize=10, fontweight='bold')

# Add relative performance vs best
best_median = min(medians)
for i, (bar, median) in enumerate(zip(bars, medians)):
    if median > best_median:
        pct_slower = (median - best_median) / best_median * 100
        ax.text(bar.get_x() + bar.get_width()/2, bar.get_height() / 2,
                f'+{pct_slower:.0f}%', ha='center', va='center', fontsize=9, color='white', fontweight='bold')

plt.tight_layout()
plt.savefig('2_time_comparison.png', dpi=300, bbox_inches='tight')
plt.close()

