import pandas as pd
import matplotlib.pyplot as plt
import math

df = pd.read_csv('conjunction_benchmark.csv')
ratio_values = df['step_ratio'].unique()
datasets = [(r, df[df['step_ratio'] == r]) for r in sorted(ratio_values)]

# Plot 1 - Total time
fig, ax = plt.subplots(figsize=(10, 6))
for i, (ratio, data) in enumerate(datasets):
    ax.plot(data['tolerance_km'], data['total_s'], 'o-', linewidth=2, markersize=8, label=f'Ratio {ratio}')
ax.set_xlabel('Tolerance (km)', fontsize=12)
ax.set_ylabel('Total Time (s)', fontsize=12)
ax.set_title('Total Processing Time vs Tolerance', fontsize=14, fontweight='bold')
ax.legend(fontsize=11)
ax.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig('1_total_time.png', dpi=300, bbox_inches='tight')
plt.close()

# Plot 2 - Stacked area per ratio
timing_columns = ['pair_reduction_s', 'filter_s', 'propagator_s', 'propagate_s', 'check_s', 'grouping_s', 'refine_s']
colors = ['#1f77b4', '#ff7f0e', '#2ca02c', '#06A77D', '#17becf', '#9467bd', '#D62839']
labels = ['Pair Reduction', 'Filter', 'Propagator Build', 'Propagate', 'Check Pairs', 'Grouping', 'Refine']
n_datasets = len(datasets)
n_cols = 4
n_rows = math.ceil(n_datasets / n_cols)
fig, axes = plt.subplots(n_rows, n_cols, figsize=(6*n_cols, 6*n_rows))
axes = axes.flatten()

for i, (ratio, data) in enumerate(datasets):
    y_stack = [data[col].values for col in timing_columns]
    axes[i].stackplot(data['tolerance_km'], y_stack, labels=labels, colors=colors, alpha=0.8)
    axes[i].set_xlabel('Tolerance (km)', fontsize=12)
    axes[i].set_ylabel('Time (s)', fontsize=12)
    axes[i].set_title(f'Ratio {ratio} Time Breakdown', fontsize=14, fontweight='bold')
    axes[i].legend(fontsize=8, loc='upper left', ncol=2)
    axes[i].grid(True, alpha=0.3)

for i in range(n_datasets, len(axes)):
    axes[i].axis('off')
plt.tight_layout()
plt.savefig('2_time_breakdown_stacked.png', dpi=300, bbox_inches='tight')
plt.close()

# Plot 3 - Conjunctions
fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(16, 6))
for i, (ratio, data) in enumerate(datasets):
    ax1.plot(data['tolerance_km'], data['conj'], 'o-', linewidth=2, markersize=8, label=f'Ratio {ratio}')
ax1.set_xlabel('Tolerance (km)', fontsize=12)
ax1.set_ylabel('Conjunctions', fontsize=12)
ax1.set_title('Conjunctions Detected', fontsize=14, fontweight='bold')
ax1.legend(fontsize=11)
ax1.grid(True, alpha=0.3)

ratio_list = [r for r, _ in datasets]
avg_conj = [data['conj'].mean() for _, data in datasets]
bars = ax2.bar(range(len(ratio_list)), avg_conj, color='#2E86AB', alpha=0.7)
ax2.set_xticks(range(len(ratio_list)))
ax2.set_xticklabels([f'{r}' for r in ratio_list])
ax2.set_xlabel('Step Second Ratio', fontsize=12)
ax2.set_ylabel('Average Conjunctions', fontsize=12)
ax2.set_title('Average Conjunctions by Ratio', fontsize=14, fontweight='bold')
ax2.set_ylim(bottom=min(avg_conj) * 0.95)
ax2.grid(True, alpha=0.3, axis='y')
for bar, val in zip(bars, avg_conj):
    height = bar.get_height()
    ax2.text(bar.get_x() + bar.get_width()/2., height,
             f'{val:.1f}', ha='center', va='bottom', fontsize=10)

plt.tight_layout()
plt.savefig('3_conjunctions.png', dpi=300, bbox_inches='tight')
plt.close()
