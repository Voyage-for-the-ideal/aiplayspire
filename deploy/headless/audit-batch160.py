"""Read-only preservation audit for the isolated, finite 160-case server job."""
import hashlib
import json
import os
from pathlib import Path
import runpy
import subprocess
import sys
import tarfile
import time
import urllib.error
import urllib.request

P = Path('/home/ubuntu/headless-spire-poc')
B = Path(sys.argv[1]).resolve(strict=True)
MODE = sys.argv[2]
PREFIXES = {P/f'runtime-acceptance-batch160-v{suffix}': unit for suffix, unit in (
    ('1','headless-batch160'), ('2','headless-batch160-r2'), ('3','headless-batch160-r3'),
    ('3-unfinished','headless-batch160-r3-unfinished'))}
assert B in PREFIXES
PREFIX = PREFIXES[B]
STAGED = False
assert MODE in ('--capture', '--preflight', '--verify')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def command(*args):
    return subprocess.check_output(args, text=True).strip()


def http(method, path):
    request = urllib.request.Request('http://127.0.0.1:5000'+path, method=method)
    try:
        with urllib.request.urlopen(request, timeout=5) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def snapshot():
    original = json.loads((P/'reports/http-tooling-manifest.json').read_text())
    for name, digest in original['files'].items():
        assert sha(P/name) == digest, name
    assert sha(Path('/etc/systemd/system/headless-spire-state.service')) == original['installed_unit_sha256']
    services = {}
    for name, pid in (('headless-spire-state', '74516'), ('soulnpc', '57389'), ('napcat', '21131'), ('napcat2', '0')):
        data = dict(line.split('=', 1) for line in command('systemctl', 'show', name+'.service',
                    '-p', 'MainPID', '-p', 'NRestarts', '-p', 'ActiveState', '-p', 'UnitFileState').splitlines())
        assert data['MainPID'] == pid and data['NRestarts'] == '0', (name, data)
        assert data['ActiveState'] == ('inactive' if name == 'napcat2' else 'active')
        services[name] = data
    assert services['napcat2']['UnitFileState'] == 'disabled'
    assert b'-q\x003681174958\x00' in Path('/proc/21131/cmdline').read_bytes()
    soul = []
    for process in Path('/proc').iterdir():
        if not process.name.isdigit(): continue
        try:
            argv = (process/'cmdline').read_bytes().split(b'\0')
            if b'backend/main.py' in argv and (process/'cwd').resolve() == Path('/home/ubuntu/SoulNPC'):
                soul.append(int(process.name))
        except (FileNotFoundError, PermissionError, ProcessLookupError): pass
    assert soul == [57389], soul
    code, state = http('GET', '/state'); h = state['headless']
    assert code == 200 and state['screen_type'] == 'MAIN_MENU' and h['engine_phase'] == 'BOOT'
    assert h['read_only'] is True and h['gl_present'] is False
    for key in ('run_started', 'collection_enabled', 'player_initialized', 'map_initialized',
                'room_initialized', 'seed_initialized', 'rng_streams_initialized'):
        assert h[key] is False, key
    codes = {m+' '+p: http(m, p)[0] for m, p in (('GET', '/action'), ('POST', '/action'), ('POST', '/state'))}
    assert codes == {'GET /action': 404, 'POST /action': 404, 'POST /state': 405}
    for gate in (P/'ALLOW_COLLECTION', Path('/home/ubuntu/aiplayspire-runtime/ALLOW_COLLECTION')):
        assert not gate.exists() and not gate.is_symlink()
    for name in ('aiplayspire', 'aiplayspire-runtime', 'sts'):
        assert not Path('/home/ubuntu', name).exists()
    return {'original_runtime_files': len(original['files']), 'original_unit_sha256': original['installed_unit_sha256'],
            'services': services, 'readonly_state': state, 'action_codes': codes, 'soul_pids': soul,
            'napcat_account': '3681174958', 'global_gates_absent': True}


def frozen_inputs():
    manifest = json.loads((B/'frozen-runtime-manifest.json').read_text())
    for name, digest in manifest['files'].items():
        path = (B/name).resolve(strict=True)
        assert path.is_relative_to(B) and sha(path) == digest, name
    for name in (['pilot','lane0','lane1'] if STAGED else ['lane0','lane1']):
        w = B/name
        assert runpy.run_path(str(w/'tools/check-profile.py'))['check'](w) == 39
        assert sha(w/'value-format12.pth') == '73d092fe8330d916e662bac5e8999eb251b34404085d171abe92389bbeacf0f6'
    return {'files': len(manifest['files']), 'manifest_sha256': sha(B/'frozen-runtime-manifest.json')}


def pinned_source():
    patch = runpy.run_path(str(P/'tools/patch_upstream.py'))
    assert sha(P/'install/upstream-source.tar.gz') == patch['ARCHIVE_SHA256']
    count = 0
    with tarfile.open(P/'install/upstream-source.tar.gz') as archive:
        for item in archive.getmembers():
            if not item.isfile(): continue
            relative = Path(*Path(item.name).parts[1:]); name = relative.as_posix()
            assert not relative.is_absolute() and '..' not in relative.parts
            data = archive.extractfile(item).read()
            expected = patch['patched'](name, data) if name in patch['EDITS'] else data
            assert (P/'upstream'/relative).read_bytes() == expected, name
            count += 1
    assert count == 5041
    return count


try:
    current = snapshot()
    inputs = frozen_inputs()
    baseline = B/'preservation-baseline.json'
    if MODE == '--capture':
        assert not baseline.exists()
        baseline.write_text(json.dumps(current, ensure_ascii=False, indent=2)+'\n')
    else:
        assert current == json.loads(baseline.read_text()), 'Original services or read-only state changed'
    result = {'passed': True, 'mode': MODE, 'verified_unix': time.time(),
              'preserved': current, 'frozen_inputs': inputs}
    if MODE == '--verify':
        result['pinned_upstream_files_verified'] = pinned_source()
        for name in (['pilot','lane0','lane1'] if STAGED else ['lane0','lane1']):
            unit = f'headless-batch24-v2-{name}.service' if STAGED else f'{PREFIX}-{name}.service'
            reply = subprocess.run(['systemctl', 'show', unit, '-p', 'MainPID', '-p', 'ActiveState', '-p', 'LoadState'],
                                   capture_output=True, text=True)
            data = dict(line.split('=', 1) for line in reply.stdout.splitlines() if '=' in line)
            if data.get('LoadState') != 'not-found':
                assert reply.returncode == 0 and data.get('MainPID') == '0', (unit, data)
                assert data.get('ActiveState') in ('inactive', 'failed'), (unit, data)
            for permit in ('ALLOW_RUN_SMOKE', 'ALLOW_COLLECTION'):
                assert not (B/name/permit).exists() and not (B/name/permit).is_symlink()
                archive=B/name/'reports/permit-archive'
                assert (archive/f'{permit}.batch-finished').is_file() or (STAGED and (archive/f'{permit}.not-opened').is_file())
        orphan_workers = []
        for process in Path('/proc').iterdir():
            if not process.name.isdigit() or int(process.name) == os.getpid(): continue
            try:
                argv = (process/'cmdline').read_bytes().split(b'\0')
                actor = any(a in (b'HeadlessSession',
                                  b'sts_ai_framework.headless.batch', b'sts_ai_framework.headless.batch160',
                                  b'sts_ai_framework.headless.live_confirmation',
                                  b'sts_ai_framework.headless_batch', b'sts_ai_framework.headless_batch160',
                                  b'sts_ai_framework.headless_live_confirmation')
                            or a.endswith(b'/battle-engine-worker') for a in argv)
                if actor and ((process/'cwd').resolve().is_relative_to(B)
                              or any(a.startswith(str(B).encode()+b'/') for a in argv)):
                    orphan_workers.append(int(process.name))
            except (FileNotFoundError, PermissionError, ProcessLookupError): pass
        assert not orphan_workers, orphan_workers
        result['orphan_worker_pids'] = orphan_workers
        result['scoped_permits_closed'] = True
except Exception as error:
    result = {'passed': False, 'mode': MODE, 'verified_unix': time.time(), 'error': repr(error)}
if MODE == '--verify':
    (B/'reports/preservation-report.json').write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n')
print(json.dumps(result, ensure_ascii=False, indent=2))
sys.exit(0 if result['passed'] else 1)
