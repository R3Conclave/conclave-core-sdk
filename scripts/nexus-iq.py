#!/usr/bin/python3
import re
import sys

if len(sys.argv) < 2:
    print("Usage: " + sys.argv[0] + ": <file>")
    exit(1)

file_name = sys.argv[1]

res = []
with open(file_name, 'r') as f:
  for line in f:
    match = re.search(r'\d+.*Set.*[{[].*["\'] *(\d+)["\'], ["\'](.+)["\'], ["\'](.+)["\']', line)
    if match is None:
        continue

    threat = match.group(1)
    policy = match.group(2)
    component_raw = match.group(3)

    match = re.search(r"^(Waived)?(?:\\n| )?(?:[DT]\s)?(.+)", component_raw)
    waived = match.group(1)
    component = match.group(2).replace(',',';')
    res.append([threat, policy, component, waived])

print("Threat,Policy,Component,Waived")
res = sorted(res, key=lambda x: x[2].replace('"','').lower())
for r in res:
    print(f"{r[0]},{r[1]},{r[2]},{r[3]}")
print("")
