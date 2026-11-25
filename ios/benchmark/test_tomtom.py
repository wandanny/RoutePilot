"""Benchmark TomTom geocoding against taiping-addresses.csv.

Set TOMTOM_API_KEY in your environment first (do not paste the key into code).
Usage: python ios/benchmark/test_tomtom.py
"""
import csv
import json
import math
import os
import urllib.parse
import urllib.request

KEY = os.environ.get("TOMTOM_API_KEY", "")
if not KEY:
    raise SystemExit("Set TOMTOM_API_KEY in your environment first.")

SAFE = "-._~,:()&"
CSV_PATH = os.path.join(os.path.dirname(__file__), "taiping-addresses.csv")


def get(url, payload=None):
    data = json.dumps(payload).encode() if payload is not None else None
    request = urllib.request.Request(
        url, data=data, headers={"User-Agent": "RoutePilot-benchmark/1.0",
                                 "Content-Type": "application/json"}
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def haversine(a, b):
    lat1, lon1 = map(math.radians, a)
    lat2, lon2 = map(math.radians, b)
    dlat, dlon = lat2 - lat1, lon2 - lon1
    h = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 2 * 6371000 * math.asin(math.sqrt(h))


rows = list(csv.DictReader(open(CSV_PATH, encoding="utf-8-sig")))
print(f"addresses: {len(rows)}")
distances = []
for row in rows:
    query = urllib.parse.quote(row["address"], safe=SAFE)
    url = f"https://api.tomtom.com/search/2/geocode/{query}.json?key={KEY}&limit=1&countrySet=MY"
    result = get(url)
    results = result.get("results") or []
    if not results:
        print(f"  NO RESULT: {row['label']}")
        continue
    pos = results[0]["position"]
    error = haversine(
        (float(row["latitude"]), float(row["longitude"])),
        (pos["lat"], pos["lon"]),
    )
    distances.append(error)
    print(f"  {row['label'][:40]:40s} {error:8.1f} m")

if distances:
    ordered = sorted(distances)
    print(f"\nwithin 50 m: {sum(d <= 50 for d in distances)}/{len(distances)}")
    print(f"within 300 m: {sum(d <= 300 for d in distances)}/{len(distances)}")
    print(f"median: {ordered[len(ordered)//2]:.1f} m  max: {ordered[-1]:.1f} m")
