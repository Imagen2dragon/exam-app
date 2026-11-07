"""Owner's cost report: python usage_report.py [days]   (reads USAGE_DB, default ./usage.db)"""
import sys

import usage

days = int(sys.argv[1]) if len(sys.argv) > 1 else 7
rows = usage.report(days)
if not rows:
    print("No AI usage recorded yet.")
else:
    print(f"{'day':<12}{'device':<14}{'requests':>9}{'cost USD':>10}{'cached':>8}")
    for day, device, n, usd, cached in rows:
        print(f"{day:<12}{device:<14}{n:>9}{usd:>10.4f}{cached:>8.0%}")
    print(f"\nDaily budget per device: ${usage.DAILY_BUDGET_USD:.2f} (set DAILY_BUDGET_USD; 0 = no limit)")
