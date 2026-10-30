"""Seeds the dev client's config (run/horizon) with the Windows mockup's sample entries, for modern-windows.txt:
waypoints, proxies (documentation IPs, so they show offline), macros and homies (made-up names).

  python tools/testcfg.py setup UI 'bind={"key":12,"hold":false,"mouse":false}' style=NEW
  python tools/examples/windows-seed.py
  ...run the script...
  python tools/testcfg.py restore        # puts the empty lists back
"""
import json
import os

KC = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'run', 'horizon')
if not os.path.exists(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'test-backup')):
    raise SystemExit('run "python tools/testcfg.py setup ..." first (restore removes these entries again)')


def bind(key=-1, mouse=False):
    return {'key': key, 'hold': False, 'mouse': mouse}


def write(name, data):
    json.dump(data, open(os.path.join(KC, name), 'w', encoding='utf-8'), indent=4)


write('waypoints.hzn', {'wayPoints': [
    {'id': 'spawn', 'x': 0, 'y': 64, 'z': 0, 'server': '6b6t.org', 'dimension': 0, 'visible': True},
    {'id': 'nether-hub', 'x': 0, 'y': 120, 'z': 0, 'server': '6b6t.org', 'dimension': 1, 'visible': True},
    {'id': 'death-1', 'x': -12480, 'y': 71, 'z': 3315, 'server': '6b6t.org', 'dimension': 0, 'visible': True},
    {'id': '+x highway 50k', 'x': 50000, 'y': 120, 'z': 0, 'server': '6b6t.org', 'dimension': 1, 'visible': False},
    {'id': 'end portal', 'x': 1888, 'y': 30, 'z': -1424, 'server': '6b6t.org', 'dimension': 0, 'visible': False},
]})
write('proxy.hzn', {'proxies': [
    {'id': 'eu-fra', 'ip': '203.0.113.24', 'port': 1080, 'user': 'horizon', 'password': 'hunter2', 'socks4': False},
    {'id': 'us-east', 'ip': '198.51.100.7', 'port': 1080, 'user': '', 'password': '', 'socks4': False},
    {'id': 'backup', 'ip': '192.0.2.150', 'port': 9050, 'user': '', 'password': '', 'socks4': True},
]})
# SDL scancodes: J 13, G 10, LAlt 226
write('macro.hzn', {
    'settingMacros': [{'id': 'timer', 'bind': bind(226), 'enabled': False, 'module': 'timer', 'setting': 'value',
                       'pressValue': '2.0', 'releaseValue': '1.0', 'hold': True}],
    'chatMacros': [{'id': 'tpa homie', 'bind': bind(13), 'enabled': True, 'message': '/tpa Steve'},
                   {'id': 'ez', 'bind': bind(), 'enabled': True, 'message': 'gg, Horizon on top'}],
    'itemMacros': [{'id': 'pearl', 'bind': bind(10), 'enabled': True, 'item': 'ender_pearl', 'legit': False}],
})
write('homies.hzn', ['Steve', 'Alex', 'kitsune_ax', 'xX_Obby_Xx', 'lunarpvp'])
print('seeded run/horizon with the Windows sample entries')
