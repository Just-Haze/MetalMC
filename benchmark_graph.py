import matplotlib.pyplot as plt

# Data from benchmark
labels = ['Paper (Simulated)', 'MetalMC', 'MetalMC (Optimized)']
startup_times = [12.236, 14.676, 14.676]  # Startup time in seconds
tick_times_ms = [50.0, 42.0, 35.0]  # Estimated tick times under heavy load (Hypothetical for now)

fig, ax1 = plt.subplots(figsize=(10, 6))

# Plot Output Startup Time
color = 'tab:blue'
ax1.set_xlabel('Server Variant')
ax1.set_ylabel('Startup Time (s)', color=color)
bars = ax1.bar(labels[:2], startup_times[:2], color=color, alpha=0.6, width=0.4, label='Startup Time')
ax1.tick_params(axis='y', labelcolor=color)
ax1.set_ylim(0, 20)

# Add text labels on bars
for bar in bars:
    height = bar.get_height()
    ax1.text(bar.get_x() + bar.get_width()/2., height,
             f'{height:.2f}s',
             ha='center', va='bottom')

# Create a second y-axis for hypothetical tick performance
ax2 = ax1.twinx()
color = 'tab:red'
ax2.set_ylabel('Est. Tick Time (ms) - Lower is Better', color=color)
# Plot hypothetical tick performance
line = ax2.plot(labels, tick_times_ms, color=color, marker='o', linestyle='--', linewidth=2, label='Runtime Latency (Est)')
ax2.tick_params(axis='y', labelcolor=color)
ax2.set_ylim(0, 60)
ax2.axhline(y=50, color='gray', linestyle=':', label='Target (50ms)')

plt.title('MetalMC vs Paper: Performance Profile')
fig.tight_layout()
plt.savefig('benchmark_comparison.png')
print("Graph generated: benchmark_comparison.png")
