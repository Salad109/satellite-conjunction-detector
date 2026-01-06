import pandas as pd
import matplotlib.pyplot as plt
import glob

csv_files = glob.glob('conjunction_benchmark_ratio*.csv')
datasets = [(int(f.split('ratio')[1].split('.')[0].split('_')[0]), pd.read_csv(f)) for f in csv_files]
datasets.sort(key=lambda x: x[0])

# Plot 1
fig, ax = plt.subplots(figsize=(10, 6))
for i, (ratio, df) in enumerate(datasets):
    ax.plot(df['tolerance_km'], df['total_s'], 'o-', linewidth=2, markersize=8, label=f'Ratio {ratio}')
    min_time_idx = df['total_s'].idxmin()
    ax.axvline(x=df.loc[min_time_idx, 'tolerance_km'], linestyle='--', linewidth=1, alpha=0.5)
ax.set_xlabel('Tolerance (km)', fontsize=12)
ax.set_ylabel('Total Time (s)', fontsize=12)
ax.set_title('Total Processing Time vs Tolerance', fontsize=14, fontweight='bold')
ax.legend(fontsize=11)
ax.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig('1_total_time.png', dpi=300, bbox_inches='tight')
plt.close()

# Plot 2
fig, ax = plt.subplots(figsize=(10, 6))
for i, (ratio, df) in enumerate(datasets):
    ax.plot(df['tolerance_km'], df['detections']/1e6, 'o-', linewidth=2, markersize=8, label=f'Ratio {ratio}')
ax.set_xlabel('Tolerance (km)', fontsize=12)
ax.set_ylabel('Detections (millions)', fontsize=12)
ax.set_title('Detections vs Tolerance', fontsize=14, fontweight='bold')
ax.legend(fontsize=11)
ax.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig('2_detections.png', dpi=300, bbox_inches='tight')
plt.close()

# Plot 3
fig, ax = plt.subplots(figsize=(10, 6))
for i, (ratio, df) in enumerate(datasets):
    ax.plot(df['tolerance_km'], df['coarse_s'], 'o-', linewidth=2, markersize=8, label=f'Ratio {ratio} Coarse')
    ax.plot(df['tolerance_km'], df['refine_s'], 's-', linewidth=2, markersize=8, alpha=0.6, label=f'Ratio {ratio} Refine')
ax.set_xlabel('Tolerance (km)', fontsize=12)
ax.set_ylabel('Time (s)', fontsize=12)
ax.set_title('Coarse vs Refine Processing Time', fontsize=14, fontweight='bold')
ax.legend(fontsize=11)
ax.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig('3_coarse_vs_refine.png', dpi=300, bbox_inches='tight')
plt.close()

# Plot 4
fig, axes = plt.subplots(1, len(datasets), figsize=(6*len(datasets), 6))
for i, (ratio, df) in enumerate(datasets):
    axes[i].fill_between(df['tolerance_km'], 0, df['coarse_s'], label='Coarse', alpha=0.7)
    axes[i].fill_between(df['tolerance_km'], df['coarse_s'], df['total_s'], label='Refine', alpha=0.5)
    axes[i].set_xlabel('Tolerance (km)', fontsize=12)
    axes[i].set_ylabel('Time (s)', fontsize=12)
    axes[i].set_title(f'Ratio {ratio} Time Breakdown', fontsize=14, fontweight='bold')
    axes[i].legend(fontsize=11)
    axes[i].grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig('4_time_breakdown.png', dpi=300, bbox_inches='tight')
plt.close()

# Plot 5
fig, ax = plt.subplots(figsize=(10, 6))
for i, (ratio, df) in enumerate(datasets):
    ax.plot(df['tolerance_km'], df['conj'], 'o-', linewidth=2, markersize=8, label=f'Ratio {ratio}')
ax.set_xlabel('Tolerance (km)', fontsize=12)
ax.set_ylabel('Conjunctions', fontsize=12)
ax.set_title('Conjunctions Detected', fontsize=14, fontweight='bold')
ax.legend(fontsize=11)
ax.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig('5_conjunctions.png', dpi=300, bbox_inches='tight')
plt.close()
