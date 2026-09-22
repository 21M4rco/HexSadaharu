"""Structural gates complement the Forge integration tests; no local Java compilation."""
import json,re
from pathlib import Path
r=Path(__file__).resolve().parents[1]
for p in (r/'src/main/resources').rglob('*.json'):json.loads(p.read_text())
rig=json.loads((r/'src/main/resources/assets/hexsadaharu/models/entity/sadaharu.json').read_text())
names=set()
for b in rig['bones']:
 assert b['name'] not in names
 assert b['parent'] is None or b['parent'] in names
 names.add(b['name'])
 for c in b['cubes']:assert all(x>0 for x in c['size'])
for b in ['root','body','chest','neck','head','upper_jaw','lower_jaw','tongue','ear_left','ear_right','tail_0','tail_1','tail_2','tail_3','brow_left','brow_right']:
 assert b in names,b
for s in ['front_left','front_right','rear_left','rear_right']:
 assert s+'_leg' in names and s+'_paw' in names
src='\n'.join(p.read_text() for p in (r/'src/main/java').rglob('*.java'))
assert 'extends Wolf' not in src
assert src.count('new KeyMapping(')==1
assert 'SpawnEggItem' not in src
assert '2F)' in src and 'nextIntervention=dog.now()+900' in src
assert 'addRegionTicket' in src and 'removeRegionTicket' in src
assert 'getDataStorage().computeIfAbsent' in src
assert (r/'src/main/resources/assets/hexsadaharu/textures/item/kibble.png').exists()
print('Resource, rig, uniqueness, control and damage contracts passed.')
