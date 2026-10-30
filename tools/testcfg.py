"""Sets up the dev client's config for a test run, and puts it back afterwards.

  python tools/testcfg.py setup <module-id> [setting=value ...] [-- <module-id> [setting=value ...] ...]
  python tools/testcfg.py config <name> <module-id> [setting=value ...]   (an extra module config, for "^cfg load <name>")
  python tools/testcfg.py restore

setup backs up run/horizon/ and run/options.txt, stops the game pausing when the window loses focus, moves chat to Y
(the user's config binds T to Free Cam), and turns the given modules on with their settings unbound (so a key a script
presses can't toggle them). Settings not given keep the values in the config (or the module's defaults). restore puts
the backup back. Always restore, even after a failed run.
"""
import json
import os
import shutil
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'run')
HORIZON = os.path.join(ROOT, 'horizon')
OPTIONS = os.path.join(ROOT, 'options.txt')
BACKUP = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'test-backup')
UNBOUND = '{"key":-1,"hold":false,"mouse":false}'


def current_config():
    shared = json.load(open(os.path.join(HORIZON, 'shared.hzn'), encoding='utf-8'))
    return os.path.join(HORIZON, 'modules', shared['modulesConfig'] + '.hzn')


def groups(args):
    """[module, setting=value, ..., --, module, ...] -> {module: {setting: value}}."""
    out = {}
    cur = None
    for a in args:
        if a == '--':
            cur = None
        elif cur is None:
            cur = a
            out[cur] = {}
        else:
            k, v = a.split('=', 1)
            out[cur][k] = v
    return out


def apply(cfg, modules):
    for mid, settings in modules.items():
        entry = next((m for m in cfg['modules'] if m['id'] == mid), None)
        if entry is None:
            entry = {'id': mid, 'settings': []}
            cfg['modules'].append(entry)
        want = {'enabled': 'true', 'bind': UNBOUND, **settings}
        for k, v in want.items():
            s = next((s for s in entry['settings'] if s['id'] == k), None)
            if s is None:
                entry['settings'].append({'id': k, 'value': v})
            else:
                s['value'] = v


def setup(args):
    if os.path.exists(BACKUP):
        sys.exit('a backup already exists in %s: run restore first' % BACKUP)
    os.makedirs(BACKUP)
    shutil.copytree(HORIZON, os.path.join(BACKUP, 'horizon'))
    shutil.copy(OPTIONS, os.path.join(BACKUP, 'options.txt'))
    opts = open(OPTIONS, encoding='utf-8').read()
    opts = opts.replace('pauseOnLostFocus:true', 'pauseOnLostFocus:false').replace('key_key.chat:key.keyboard.t', 'key_key.chat:key.keyboard.y')
    open(OPTIONS, 'w', encoding='utf-8').write(opts)
    path = current_config()
    cfg = json.load(open(path, encoding='utf-8'))
    apply(cfg, groups(args))
    json.dump(cfg, open(path, 'w', encoding='utf-8'), indent=4)
    print('setup ok: chat is on Y; restore with: python tools/testcfg.py restore')


def config(args):
    """A copy of the current module config named <name>. Its "id" must be the name too, or it replaces the current one."""
    if not os.path.exists(BACKUP):
        sys.exit('run setup first (restore removes this config again)')
    name = args[0]
    cfg = json.load(open(current_config(), encoding='utf-8'))
    cfg['id'] = name
    apply(cfg, groups(args[1:]))
    json.dump(cfg, open(os.path.join(HORIZON, 'modules', name + '.hzn'), 'w', encoding='utf-8'), indent=4)
    print('config %s written: load it in a script with "key Y", "type ^cfg load %s", "key RETURN"' % (name, name))


def restore(_):
    if not os.path.exists(BACKUP):
        sys.exit('nothing to restore')
    shutil.rmtree(HORIZON)
    shutil.copytree(os.path.join(BACKUP, 'horizon'), HORIZON)
    shutil.copy(os.path.join(BACKUP, 'options.txt'), OPTIONS)
    shutil.rmtree(BACKUP)
    print('restore ok')


{'setup': setup, 'config': config, 'restore': restore}[sys.argv[1]](sys.argv[2:])
