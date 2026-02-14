import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from scipy.optimize import curve_fit, minimize_scalar
import glob

# Read all CSV files in the directory
csv_files = glob.glob('*.csv')
print(f"Found {len(csv_files)} CSV files")

# Load and concatenate all CSVs
dfs = []
for csv_file in csv_files:
    df_temp = pd.read_csv(csv_file)
    dfs.append(df_temp)

all_data = pd.concat(dfs, ignore_index=True)

# Group by tolerance_km and average all other numeric columns
df = all_data.groupby('tolerance_km', as_index=False).mean(numeric_only=True)

def model(x, a, b, c, d):
    return a / x + b * x + c * x**2 + d

x = df['tolerance_km'].values
y = df['total_s'].values
params, _ = curve_fit(model, x, y)
a, b, c, d = params

y_pred = model(x, *params)
r2 = 1 - np.sum((y - y_pred)**2) / np.sum((y - np.mean(y))**2)

result = minimize_scalar(lambda x: model(x, *params), bounds=(x.min(), x.max()), method='bounded')
x_min, y_min = result.x, result.fun

# Plot 1
fig, ax = plt.subplots(figsize=(10, 6))
x_smooth = np.linspace(x.min(), x.max(), 500)
ax.plot(x, y, 'o', color='#2E86AB', markersize=8, label='Data')
ax.plot(x_smooth, model(x_smooth, *params), '-', color='red', linewidth=2, label=f'${a:.0f}/x + {b:.3f}x + {c:.2e}x^2 + {d:.1f}$')
ax.axvline(x=x_min, color='red', linestyle='--', linewidth=2, alpha=0.5, label=f'Optimal: {x_min:.1f} km')
ax.text(0.02, 0.98, f'$R^2 = {r2:.4f}$', transform=ax.transAxes, fontsize=11, verticalalignment='top', bbox=dict(boxstyle='round', facecolor='white', alpha=0.8))
ax.set_xlabel('Tolerance (km)', fontsize=12)
ax.set_ylabel('Total Time (s)', fontsize=12)
ax.set_title('Total Processing Time vs Tolerance', fontsize=14, fontweight='bold')
ax.legend(fontsize=11)
ax.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig('1_total_time.png', dpi=300, bbox_inches='tight')
plt.close()

# Plot 2 - All timing components
fig, ax = plt.subplots(figsize=(12, 7))
timing_columns = ['pair_reduction_s', 'filter_s', 'propagator_s', 'propagate_s', 'check_s', 'grouping_s', 'refine_s', 'probability_s']
colors = ['#1f77b4', '#ff7f0e', '#2ca02c', '#06A77D', '#17becf', '#9467bd', '#D62839', '#8c564b']
markers = ['o', 's', '^', 'd', 'x', 'v', 'p', '*']
labels = ['Pair Reduction', 'Filter', 'Propagator Build', 'Propagate', 'Check Pairs', 'Grouping', 'Refine', 'Probability']

for col, color, marker, label in zip(timing_columns, colors, markers, labels):
    ax.plot(df['tolerance_km'], df[col], marker=marker, linestyle='-', label=label,
            color=color, linewidth=2, markersize=8)

ax.set_xlabel('Tolerance (km)', fontsize=12)
ax.set_ylabel('Time (s)', fontsize=12)
ax.set_title('Time Breakdown', fontsize=14, fontweight='bold')
ax.legend(fontsize=10, ncol=2)
ax.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig('2_time_breakdown.png', dpi=300, bbox_inches='tight')
plt.close()

# Plot 3 - Stacked area chart with all components
fig, ax = plt.subplots(figsize=(12, 7))
timing_columns = ['pair_reduction_s', 'filter_s', 'propagator_s', 'propagate_s', 'check_s', 'grouping_s', 'refine_s', 'probability_s']
colors = ['#1f77b4', '#ff7f0e', '#2ca02c', '#06A77D', '#17becf', '#9467bd', '#D62839', '#8c564b']
labels = ['Pair Reduction', 'Filter', 'Propagator Build', 'Propagate', 'Check Pairs', 'Grouping', 'Refine', 'Probability']

# Prepare data for stacked area
y_stack = np.vstack([df[col].values for col in timing_columns])
ax.stackplot(df['tolerance_km'], y_stack, labels=labels, colors=colors, alpha=0.8)

ax.set_xlabel('Tolerance (km)', fontsize=12)
ax.set_ylabel('Time (s)', fontsize=12)
ax.set_title('Time Breakdown Stacked', fontsize=14, fontweight='bold')
ax.legend(fontsize=10, loc='upper left', ncol=2)
ax.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig('3_time_breakdown_stacked.png', dpi=300, bbox_inches='tight')
plt.close()

# Plot 4
fig, ax = plt.subplots(figsize=(10, 6))
ax.plot(df['tolerance_km'], df['conj'], 'o-', color='#5E2C99', linewidth=2, markersize=8)
ax.set_xlabel('Tolerance (km)', fontsize=12)
ax.set_ylabel('Conjunctions', fontsize=12)
ax.set_title('Conjunctions Detected', fontsize=14, fontweight='bold')
ax.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig('4_conjunctions.png', dpi=300, bbox_inches='tight')
plt.close()
