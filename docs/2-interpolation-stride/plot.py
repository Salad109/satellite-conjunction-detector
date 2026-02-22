import pandas as pd
import matplotlib.pyplot as plt

df = pd.read_csv('conjunction_benchmark.csv')
param = 'interp_stride'
param_label = 'Interpolation Stride'
avg_all = df.groupby(param).mean(numeric_only=True).reset_index()
avg = avg_all[avg_all[param] != 1]

timing_columns = ['propagator_s', 'sgp4_s', 'interp_s', 'check_s', 'grouping_s', 'refine_s', 'probability_s']
colors = ['#2ca02c', '#06A77D', '#e377c2', '#17becf', '#9467bd', '#D62839', '#8c564b']
labels = ['Propagator Build', 'SGP4', 'Interpolation', 'Check Pairs', 'Grouping', 'Refine', 'Probability']

# Plot 1 - Total time
fig, ax = plt.subplots(figsize=(10, 6))
ax.plot(avg[param], avg['total_s'], 'o-', linewidth=2, markersize=8, color='#D62839')
ax.set_xlabel(param_label, fontsize=12)
ax.set_ylabel('Total Time (s)', fontsize=12)
ax.set_title('Total Processing Time vs Interpolation Stride', fontsize=14, fontweight='bold')
ax.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig('1_total_time.png', dpi=300, bbox_inches='tight')
plt.close()

# Plot 2 - Line per component
markers = ['^', 'd', 'D', 'x', 'v', 'p', '*']
fig, ax = plt.subplots(figsize=(12, 7))
for col, color, marker, label in zip(timing_columns, colors, markers, labels):
    ax.plot(avg[param], avg[col], marker=marker, linestyle='-', label=label,
            color=color, linewidth=2, markersize=8)
ax.set_xlabel(param_label, fontsize=12)
ax.set_ylabel('Time (s)', fontsize=12)
ax.set_title('Time Breakdown by Interpolation Stride', fontsize=14, fontweight='bold')
ax.legend(fontsize=10, ncol=2)
ax.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig('2_time_breakdown.png', dpi=300, bbox_inches='tight')
plt.close()

# Plot 3 - Stacked area
import numpy as np
fig, ax = plt.subplots(figsize=(12, 7))
y_stack = np.vstack([avg[col].values for col in timing_columns])
ax.stackplot(avg[param], y_stack, labels=labels, colors=colors, alpha=0.8)
ax.set_xlabel(param_label, fontsize=12)
ax.set_ylabel('Time (s)', fontsize=12)
ax.set_title('Time Breakdown Stacked by Interpolation Stride', fontsize=14, fontweight='bold')
ax.legend(fontsize=8, loc='upper left', ncol=2)
ax.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig('3_time_breakdown_stacked.png', dpi=300, bbox_inches='tight')
plt.close()

# Plot 4 - Conjunctions (includes stride=1)
fig, ax = plt.subplots(figsize=(10, 6))
ax.plot(avg_all[param], avg_all['conj'], 'o-', linewidth=2, markersize=8, color='#2E86AB')
for _, row in avg_all.iterrows():
    ax.text(row[param], row['conj'], f'  {row["conj"]:.0f}', va='bottom', fontsize=9)
ax.set_xlabel(param_label + ' (all values)', fontsize=12)
ax.set_ylabel('Conjunctions', fontsize=12)
ax.set_title('Conjunctions Detected vs Interpolation Stride', fontsize=14, fontweight='bold')
ax.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig('4_conjunctions.png', dpi=300, bbox_inches='tight')
plt.close()
