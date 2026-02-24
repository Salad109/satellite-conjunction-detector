import pandas as pd
import matplotlib.pyplot as plt

df = pd.read_csv('pareto_benchmark.csv')

ground_truth = df['conj'].iloc[0]
df['accuracy_pct'] = (df['conj'] / ground_truth * 100).clip(upper=100)

# Compute Pareto frontier (maximize accuracy, minimize time)
sorted_df = df.sort_values(['accuracy_pct', 'total_s'], ascending=[False, True]).reset_index(drop=True)
frontier_mask = []
best_time = float('inf')
for _, row in sorted_df.iterrows():
    if row['total_s'] < best_time:
        frontier_mask.append(True)
        best_time = row['total_s']
    else:
        frontier_mask.append(False)
sorted_df['is_pareto'] = frontier_mask
frontier = sorted_df[sorted_df['is_pareto']].sort_values('accuracy_pct', ascending=False)

# Print table
print(f"\nGround truth: {ground_truth} conjunctions")
print(f"\nPareto frontier ({len(frontier)} points):")
print(f"| Step | Stride | Cell  | Cell (km) | Conj  | Accuracy | Time   |")
print(f"|------|--------|-------|-----------|-------|----------|--------|")
for _, row in frontier.iterrows():
    cell_km = row['tolerance_km'] / row['cell_ratio']
    print(f"| {int(row['step_ratio']):<4} | {int(row['interp_stride']):<6} | {row['cell_ratio']:<5.2f} "
          f"| {cell_km:>7.1f}   "
          f"| {int(row['conj']):>5} | {row['accuracy_pct']:>7.2f}% | {row['total_s']:>5.2f}s |")

# Print all evaluated points
print(f"\nAll evaluated points ({len(df)} total):")
print(f"| Step | Stride | Cell  | Conj  | Accuracy | Time   |")
print(f"|------|--------|-------|-------|----------|--------|")
for _, row in df.iterrows():
    print(f"| {int(row['step_ratio']):<4} | {int(row['interp_stride']):<6} | {row['cell_ratio']:<5.2f} "
          f"| {int(row['conj']):>5} | {row['accuracy_pct']:>7.2f}% | {row['total_s']:>5.1f}s |")

# Plot 1 - Accuracy vs Time scatter with Pareto frontier
fig, ax = plt.subplots(figsize=(12, 7))

ax.scatter(df['total_s'], df['accuracy_pct'],
           c='#AAAAAA', s=60, alpha=0.6, label='Evaluated points', zorder=2)

ax.plot(frontier['total_s'], frontier['accuracy_pct'],
        'o-', color='#D62839', linewidth=2, markersize=10,
        label='Pareto frontier', zorder=3)

for _, row in frontier.iterrows():
    label = f"s{int(row['step_ratio'])} i{int(row['interp_stride'])} c{row['cell_ratio']:.1f}"
    ax.annotate(label, (row['total_s'], row['accuracy_pct']),
                textcoords='offset points', xytext=(8, -4), fontsize=7,
                color='#D62839')

ax.axhline(y=98.0, color='#FF9900', linestyle='--', alpha=0.7, label='98% accuracy threshold')

ax.set_ylim(97.5, 100.2)
ax.set_xlim(right=frontier['total_s'].max() + 2)
ax.set_xlabel('Total Time (s)', fontsize=12)
ax.set_ylabel('Accuracy (%)', fontsize=12)
ax.set_title('Pareto Frontier: Speed vs Accuracy', fontsize=14, fontweight='bold')
ax.legend(fontsize=10)
ax.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig('1_pareto_frontier.png', dpi=300, bbox_inches='tight')
plt.close()

# Plot 2 - Frontier parameter evolution as accuracy decreases
f = frontier.sort_values('accuracy_pct', ascending=False).reset_index(drop=True)

fig, (ax1, ax2, ax3) = plt.subplots(3, 1, figsize=(12, 10), sharex=True)

ax1.plot(f['accuracy_pct'], f['step_ratio'], 'o-', color='#2ca02c', markersize=8, linewidth=2)
ax1.set_ylabel('Step Ratio', fontsize=12)
ax1.set_title('Pareto Frontier: Parameter Evolution as Accuracy Decreases', fontsize=14, fontweight='bold')
ax1.grid(True, alpha=0.3)
ax1.invert_xaxis()

ax2.plot(f['accuracy_pct'], f['interp_stride'], 'o-', color='#e377c2', markersize=8, linewidth=2)
ax2.set_ylabel('Interp Stride', fontsize=12)
ax2.grid(True, alpha=0.3)
ax2.invert_xaxis()

ax3.plot(f['accuracy_pct'], f['cell_ratio'], 'o-', color='#17becf', markersize=8, linewidth=2)
ax3.set_xlabel('Accuracy (%)', fontsize=12)
ax3.set_ylabel('Cell Ratio', fontsize=12)
ax3.grid(True, alpha=0.3)
ax3.invert_xaxis()

for ax in [ax1, ax2, ax3]:
    ax.axvline(x=98.0, color='#FF9900', linestyle='--', alpha=0.7)

plt.tight_layout()
plt.savefig('2_frontier_parameters.png', dpi=300, bbox_inches='tight')
plt.close()

print(f"\nPlots saved: 1_pareto_frontier.png, 2_frontier_parameters.png")
