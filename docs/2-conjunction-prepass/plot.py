import pandas as pd
import matplotlib.pyplot as plt

df = pd.read_csv('conjunction_benchmark.csv')
param = 'prepass_km'
param_label = 'Prepass Tolerance (km)'
avg = df.groupby(param).mean(numeric_only=True).reset_index()

timing_columns = ['pair_reduction_s', 'filter_s', 'propagator_s', 'sgp4_s', 'interp_s', 'check_s', 'grouping_s', 'refine_s', 'probability_s']
colors = ['#1f77b4', '#ff7f0e', '#2ca02c', '#06A77D', '#e377c2', '#17becf', '#9467bd', '#D62839', '#8c564b']
labels = ['Pair Reduction', 'Filter', 'Propagator Build', 'SGP4', 'Interpolation', 'Check Pairs', 'Grouping', 'Refine', 'Probability']

# Plot 1 - Total time
fig, ax = plt.subplots(figsize=(10, 6))
ax.scatter(df[param], df['total_s'], alpha=0.3, color='#888888', s=30, label='Iterations')
ax.plot(avg[param], avg['total_s'], 'o-', linewidth=2, markersize=8, color='#D62839', label='Mean')
ax.set_xlabel(param_label, fontsize=12)
ax.set_ylabel('Total Time (s)', fontsize=12)
ax.set_title('Total Processing Time vs Prepass Tolerance', fontsize=14, fontweight='bold')
ax.legend(fontsize=11)
ax.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig('1_total_time.png', dpi=300, bbox_inches='tight')
plt.close()

# Plot 2 - Stacked bar
fig, ax = plt.subplots(figsize=(10, 6))
x = range(len(avg))
bottom = [0] * len(avg)
for col, color, label in zip(timing_columns, colors, labels):
    vals = avg[col].values
    ax.bar(x, vals, bottom=bottom, color=color, label=label, alpha=0.8)
    bottom = [b + v for b, v in zip(bottom, vals)]
ax.set_xticks(x)
ax.set_xticklabels([f'{v:.0f}' for v in avg[param]])
ax.set_xlabel(param_label, fontsize=12)
ax.set_ylabel('Time (s)', fontsize=12)
ax.set_title('Mean Time Breakdown by Prepass Tolerance', fontsize=14, fontweight='bold')
ax.legend(fontsize=8, loc='upper left', ncol=2)
ax.grid(True, alpha=0.3, axis='y')
plt.tight_layout()
plt.savefig('2_time_breakdown_stacked.png', dpi=300, bbox_inches='tight')
plt.close()

# Plot 3 - Conjunctions
fig, ax = plt.subplots(figsize=(10, 6))
ax.scatter(df[param], df['conj'], alpha=0.3, color='#888888', s=30, label='Iterations')
ax.plot(avg[param], avg['conj'], 'o-', linewidth=2, markersize=8, color='#2E86AB', label='Mean')
for _, row in avg.iterrows():
    ax.text(row[param], row['conj'], f'  {row["conj"]:.0f}', va='bottom', fontsize=9)
ax.set_xlabel(param_label, fontsize=12)
ax.set_ylabel('Conjunctions', fontsize=12)
ax.set_title('Conjunctions Detected vs Prepass Tolerance', fontsize=14, fontweight='bold')
ax.legend(fontsize=11)
ax.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig('3_conjunctions.png', dpi=300, bbox_inches='tight')
plt.close()
