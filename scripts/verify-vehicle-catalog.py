#!/usr/bin/env python3
"""Check every bundled catalog shard without Android or network access."""
import importlib.util
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('catalog_builder', ROOT / 'scripts/build-vehicle-catalog.py')
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)
folder = ROOT / 'app/src/main/assets/vehicle-catalog'
index = json.loads((folder / 'index.json').read_text())
shards = [json.loads((folder / f'models-{i:02d}.json').read_text()) for i in range(32)]
all_models = {}
years_total = 0
for k, name, shard, count in index['makes']:
    assert k == builder.key(name), name
    assert k not in all_models, name
    rows = shards[shard][k]
    assert count == len(rows), name
    names = set()
    all_models[k] = rows
    for model, years, sources in rows:
        normalized = builder.key(model)
        assert normalized and normalized not in names, (name, model)
        names.add(normalized)
        assert years == sorted(set(years), reverse=True), (name, model)
        assert all(isinstance(y, int) and 1990 <= y <= 2027 for y in years), (name, model)
        assert isinstance(sources, int) and sources > 0, (name, model)
        years_total += len(years)
assert len(all_models) == index['make_count'] >= 1900
assert sum(map(len, all_models.values())) == index['model_count'] >= 48000
assert years_total == index['referenced_year_count']
assert sum(map(len, shards)) == len(all_models)
additions = json.loads((ROOT / 'data/vehicle-catalog/additions.json').read_text())
for name in additions['makes']:
    if name != 'Autre marque':
        assert all_models[builder.key(name)], 'No suggestions for existing make: ' + name
for name, models in additions['models'].items():
    actual = {builder.key(m[0]) for m in all_models[builder.key(name)]}
    assert all(builder.key(model) in actual for model in models), name
for entry in json.loads((ROOT / 'data/vehicle-catalog/reviewed-models.json').read_text())['entries']:
    actual = {builder.key(m[0]) for m in all_models[builder.key(entry['make'])]}
    assert all(builder.key(model) in actual for model in entry['models']), entry['make']
credits = (folder / 'credits.txt').read_text()
assert all(text in credits for text in ['Vehicle data by VehiclesDB', 'Open Government Licence', 'MIT License', 'Creative Commons', 'aucune année de production'])
print(f"Catalog OK: {index['make_count']} makes, {index['model_count']} models, {years_total} referenced model/year pairs; 32 shards; attribution bundled.")
