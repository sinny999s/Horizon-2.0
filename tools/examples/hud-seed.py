"""Seeds the dev client's config (run/horizon) for modern-hud.txt: a HUD config "hudtest" with every widget on, each in
its own spot (the same spots tools render the mockup with, in GUI units on the 800x450 dev window), an item macro
(ender pearl on G) and a homie (Steve, the fake player).

  python tools/testcfg.py setup UI 'bind={"key":12,"hold":false,"mouse":false}' style=NEW -- sprint 'bind={"key":5,"hold":false,"mouse":false}' -- full-bright 'bind={"key":14,"hold":false,"mouse":false}' -- swing-animations -- move-fix -- totem-animation
  python tools/examples/hud-seed.py
  ...run the script...
  python tools/testcfg.py restore        # puts the HUD config back
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
KC = os.path.join(HERE, '..', '..', 'run', 'horizon')
if not os.path.exists(os.path.join(HERE, '..', 'test-backup')):
    raise SystemExit('run "python tools/testcfg.py setup ..." first (restore removes these entries again)')

# (left, top) in GUI units on the 800x450 window
SPOTS = {
    'watermark-widget': (4, 4), 'ping-widget': (4, 33), 'fps-widget': (4, 47), 'target-info': (10, 75),
    'media-player-widget': (10, 140), 'potions-widget': (4, 225), 'phase-widget': (150, 75), 'macro-widget': (150, 135),
    'inventory-widget': (150, 160), 'flight-widget': (150, 240), 'chest-counter-widget': (150, 305),
    'crystal-speed-widget': (150, 322), 'speed-widget': (150, 339), 'tps-widget': (150, 356), 'keybinds-widget': (150, 380),
    'coord-widget': (546, 430), 'pvp-resources-widget': (500, 60), 'armor-widget': (500, 100), 'cooldowns-widget': (500, 135),
    'staff-widget': (500, 190), 'player-list-widget': (500, 250), 'array-list-widget': (718, 4),
    'notifications': (400, 225), 'crosshair': (400, 225),
}

if __name__ == '__main__':
    cfg = json.load(open(os.path.join(KC, 'widgets', 'default.hzn'), encoding='utf-8'))
    cfg['id'] = 'hudtest'
    for w in cfg['widgets']:
        x, y = SPOTS[w['id']]
        for s in w['settings']:
            if s['id'] == 'enabled':
                s['value'] = 'true'
            elif s['id'] == 'position':
                s['value'] = json.dumps({'x': x / 800, 'y': y / 450}, separators=(',', ':'))
    json.dump(cfg, open(os.path.join(KC, 'widgets', 'hudtest.hzn'), 'w', encoding='utf-8'), indent=4)
    shared_path = os.path.join(KC, 'shared.hzn')
    shared = json.load(open(shared_path, encoding='utf-8'))
    shared['widgetsConfig'] = 'hudtest'
    json.dump(shared, open(shared_path, 'w', encoding='utf-8'), indent=4)
    # SDL scancode: G 10
    json.dump({'settingMacros': [], 'chatMacros': [],
               'itemMacros': [{'id': 'pearl', 'bind': {'key': 10, 'hold': False, 'mouse': False}, 'enabled': True,
                               'item': 'ender_pearl', 'legit': False}]},
              open(os.path.join(KC, 'macro.hzn'), 'w', encoding='utf-8'), indent=4)
    json.dump(['Steve'], open(os.path.join(KC, 'homies.hzn'), 'w', encoding='utf-8'), indent=4)
    print('seeded run/horizon with the HUD test config (hudtest), a pearl macro and a homie')
