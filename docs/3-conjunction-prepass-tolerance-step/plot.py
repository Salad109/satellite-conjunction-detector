import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns

df = pd.read_csv('conjunction_benchmark.csv')
optimal = df.loc[df['total_s'].idxmin()]

# Plot 1: Heatmaps by ratio
fig, axes = plt.subplots(2, 2, figsize=(14, 10))
fig.suptitle('Total Time by Step Ratio (prepass vs tolerance)', fontsize=14, fontweight='bold')

for idx, ratio in enumerate([12, 15, 18, 20]):
    ax = axes[idx // 2, idx % 2]
    subset = df[df['ratio'] == ratio]
    pivot = subset.pivot(index='prepass_km', columns='tolerance_km', values='total_s')

    sns.heatmap(pivot, annot=True, fmt='.0f', cmap='RdYlGn_r', ax=ax, cbar_kws={'label': 'Time (s)'})
    ax.set_title(f'Ratio {ratio}', fontweight='bold')
    ax.set_xlabel('Tolerance (km)')
    ax.set_ylabel('Prepass (km)')

plt.tight_layout()
plt.savefig('1_ratio_heatmap.png', dpi=300, bbox_inches='tight')
plt.close()

# Plot 2: Heatmaps by prepass
fig, axes = plt.subplots(2, 4, figsize=(20, 9))
fig.suptitle('Total Time by Prepass Distance (tolerance vs ratio)', fontsize=14, fontweight='bold')

for idx, prepass in enumerate([180, 240, 300, 360, 420, 480, 540, 600]):
    ax = axes[idx // 4, idx % 4]
    subset = df[df['prepass_km'] == prepass]
    pivot = subset.pivot(index='ratio', columns='tolerance_km', values='total_s')

    sns.heatmap(pivot, annot=True, fmt='.0f', cmap='RdYlGn_r', ax=ax, cbar_kws={'label': 'Time (s)'})
    ax.set_title(f'Prepass {int(prepass)}km', fontweight='bold')
    ax.set_xlabel('Tolerance (km)')
    ax.set_ylabel('Ratio')

plt.tight_layout()
plt.savefig('2_prepass_heatmap.png', dpi=300, bbox_inches='tight')
plt.close()

# Plot 3: Deduplicated conjunctions vs tolerance, grouped by ratio, averaged across prepass
fig, ax = plt.subplots(figsize=(10, 6))
colors = {'12': '#2E86AB', '15': '#A23B72', '18': '#06A77D', '20': '#D62839'}

for ratio in [12, 15, 18, 20]:
    subset = df[df['ratio'] == ratio]
    avg_by_tol = subset.groupby('tolerance_km')['dedup'].mean()
    ax.plot(avg_by_tol.index, avg_by_tol.values, 'o-', label=f'Ratio {ratio}',
            color=colors[str(ratio)], linewidth=2, markersize=8)

ax.set_xlabel('Tolerance (km)', fontsize=12)
ax.set_ylabel('Deduplicated Conjunctions (avg across prepass)', fontsize=12)
ax.set_title('Conjunction Count vs Tolerance by Step Ratio', fontsize=14, fontweight='bold')
ax.legend(fontsize=11)
ax.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig('3_dedup_by_ratio.png', dpi=300, bbox_inches='tight')
plt.close()