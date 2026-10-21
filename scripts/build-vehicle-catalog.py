#!/usr/bin/env python3
"""Rebuild the bundled, offline catalog from pinned, attributed source snapshots."""
import argparse
import hashlib
import json
from pathlib import Path
import unicodedata
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
SOURCES = {
    'meterapp.json': ('MeterApp/vehicle-db', 'ae8a9138c2d3f92b39e199cc1238cc642042417e', 'data/compact.json'),
    'meterapp-readme.md': ('MeterApp/vehicle-db', 'ae8a9138c2d3f92b39e199cc1238cc642042417e', 'README.md'),
    'vehiclesdb.json': ('vehiclesdb/vehiclesdb', 'd0e40c9661e36931089db1213ac383d2d7ba5a7a', 'dist/vehicles.min.json'),
    'vehiclesdb-attribution.md': ('vehiclesdb/vehiclesdb', 'd0e40c9661e36931089db1213ac383d2d7ba5a7a', 'ATTRIBUTION.md'),
    'global-models.json': ('serhatkildaci/global-car-models', '44da5c9e5e0f3162d65579033f7a641473308b11', 'data/models.json'),
    'global-models-license.txt': ('serhatkildaci/global-car-models', '44da5c9e5e0f3162d65579033f7a641473308b11', 'LICENSE'),
}

def key(value):
    return ''.join(c for c in unicodedata.normalize('NFD', value).lower()
                   if unicodedata.category(c)[0] in ('L', 'N'))

def build(source_dir, output):
    raw = {}
    for filename, (repo, revision, path) in SOURCES.items():
        if source_dir:
            raw[filename] = (source_dir / filename).read_text(encoding='utf-8')
        else:
            url = f'https://raw.githubusercontent.com/{repo}/{revision}/{path}'
            with urllib.request.urlopen(url, timeout=60) as response:
                raw[filename] = response.read().decode('utf-8')
    meter = json.loads(raw['meterapp.json'])
    vdb = json.loads(raw['vehiclesdb.json'])
    global_models = json.loads(raw['global-models.json'])
    additions = json.loads((ROOT / 'data/vehicle-catalog/additions.json').read_text())
    brands = {}
    preferred = {key(name): name for name in additions['makes'] if name != 'Autre marque'}
    preferred.update({key(name): preferred.get(key(name), name) for name in global_models})
    for _, name, _ in vdb['makes']:
        preferred.setdefault(key(name), name)

    def brand(name):
        k = key(name)
        if not k:
            raise ValueError('Empty make name')
        return brands.setdefault(k, {'name': preferred.get(k, name), 'models': {}})

    def add(make, model, years=(), source=0):
        b = brand(make)
        k = key(model)
        if not k:
            return
        item = b['models'].setdefault(k, {'name': model, 'years': set(), 'sources': 0})
        item['years'].update(years)
        item['sources'] |= source

    make_names = {m['make_id']: m['make_name'] for m in meter['makes']}
    for year, make_id, _, model_index, _, mask in meter['models']:
        add(make_names[make_id], meter['modelNames'][model_index], [year], mask)
    vdb_makes = {slug: name for slug, name, _ in vdb['makes']}
    for make_slug, _, name, *_ in vdb['models']:
        add(vdb_makes[make_slug], name, source=1 << 23)
        brands[key(vdb_makes[make_slug])]['models'][key(name)]['name'] = name
    for name, models in global_models.items():
        for model in models:
            add(name, model, source=1 << 24)
            brands[key(name)]['models'][key(model)]['name'] = model
    for name in additions['makes']:
        if name != 'Autre marque':
            brand(name)
    for name, models in additions['models'].items():
        for model in models:
            add(name, model, source=1 << 25)
    reviewed = json.loads((ROOT / 'data/vehicle-catalog/reviewed-models.json').read_text())
    for entry in reviewed['entries']:
        for model in entry['models']:
            add(entry['make'], model, source=1 << 26)

    output.mkdir(parents=True, exist_ok=True)
    shards = [{} for _ in range(32)]
    index = []
    for k, b in sorted(brands.items(), key=lambda pair: key(pair[1]['name'])):
        shard = int(hashlib.sha256(k.encode()).hexdigest()[:2], 16) % len(shards)
        models = [[m['name'], sorted(m['years'], reverse=True), m['sources']]
                  for _, m in sorted(b['models'].items())]
        index.append([k, b['name'], shard, len(models)])
        shards[shard][k] = models
    model_count = sum(row[3] for row in index)
    year_count = sum(len(m['years']) for b in brands.values() for m in b['models'].values())
    metadata = {
        'schema': 1, 'make_count': len(index), 'model_count': model_count,
        'referenced_year_count': year_count, 'reference_year_from': 1990,
        'reference_year_to': 2027, 'makes': index,
        'sources': [{'repository': repo, 'revision': revision, 'path': path}
                    for filename, (repo, revision, path) in SOURCES.items() if filename.endswith('.json')],
    }
    def write(path, data):
        path.write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8')
    write(output / 'index.json', metadata)
    for i, shard in enumerate(shards):
        write(output / f'models-{i:02d}.json', shard)
    write(output / 'source-metadata.json', meter['sources'])
    attribution = [
        'CATALOGUE VÉHICULES — SOURCES ET LICENCES',
        f'{len(index)} marques, {model_count} modèles. Suggestions hors connexion, couverture non exhaustive.',
        'Noms normalisés, doublons regroupés et années référencées réunies par Bilal27000diag.',
        'Une présence au catalogue ne garantit pas la compatibilité OBD-II.',
        'Les années référencées sont des millésimes, années de fabrication, de catalogue ou d’immatriculation selon la source. Elles ne constituent pas une période de production certifiée. Aucune année intermédiaire n’est inventée. La saisie manuelle reste disponible.',
        'MeterApp vehicle-db — https://github.com/MeterApp/vehicle-db — snapshot ae8a9138c2d3f92b39e199cc1238cc642042417e. Données amont conservant leurs licences :',
    ]
    for s in meter['sources']:
        attribution.append(f"{s['source_name']}\n{s['source_url']}\n{s['license']} — {s.get('license_url', s['source_url'])}\n{s['description']}")
    readme = raw['meterapp-readme.md']
    attribution.append(readme[readme.index('UK source attribution:'):readme.index('New sources in 2.13.0:')])
    attribution.extend([
        'Vehicle data by VehiclesDB (vehiclesdb.com) — https://vehiclesdb.com — CC BY 4.0 — https://creativecommons.org/licenses/by/4.0/. Version ' + vdb['v'] + '. Extraction des noms uniquement, sans inférence d’années.',
        raw['vehiclesdb-attribution.md'],
        'Global Car Models — https://github.com/serhatkildaci/global-car-models — extraction des noms uniquement.',
        raw['global-models-license.txt'],
        'Bilal27000diag : conservation des noms déjà proposés dans la version 0.6.1. Ces noms sans années sourcées ne reçoivent aucune année de production présumée.',
    ])
    attribution.append('Compléments de noms vérifiés le ' + reviewed['reviewed_at'] + '. Faits et noms de produits uniquement ; aucune description ni année de production reproduite :')
    for entry in reviewed['entries']:
        attribution.append(entry['make'] + ' : ' + ', '.join(entry['models']) + '\n' + '\n'.join(entry['urls']))
    (output / 'credits.txt').write_text('\n\n'.join(attribution) + '\n', encoding='utf-8')
    print(json.dumps({k: v for k, v in metadata.items() if k != 'makes'}, ensure_ascii=False))

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-dir', type=Path, help='Use local source files instead of downloading pinned snapshots')
    parser.add_argument('--output', type=Path, default=ROOT / 'app/src/main/assets/vehicle-catalog')
    args = parser.parse_args()
    build(args.source_dir, args.output)
