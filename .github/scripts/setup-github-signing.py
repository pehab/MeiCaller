"""Restore an existing private APK key; never generate a replacement or print key material."""
import base64
import json
import os
from pathlib import Path

existing = os.environ.get('EXISTING_SIGNING_READY') == 'true'
raw = os.environ.get('MEI_SIGNING_JSON', '')
ready = existing
if raw and not existing:
    data = json.loads(raw)
    password = data['password']
    alias = data['alias']
    key_password = data.get('keyPassword', password)
    if not all(isinstance(v, str) and v and '\n' not in v and '\r' not in v for v in (password, alias, key_password)):
        raise SystemExit('Invalid signing configuration')
    print('::add-mask::' + password)
    print('::add-mask::' + key_password)
    path = Path(os.environ['RUNNER_TEMP']) / 'mei-github-signing.p12'
    path.write_bytes(base64.b64decode(data['keystore'], validate=True))
    path.chmod(0o600)
    with open(os.environ['GITHUB_ENV'], 'a') as env:
        for key, value in {'MEI_GITHUB_STORE_FILE': str(path), 'MEI_GITHUB_STORE_PASSWORD': password,
                           'MEI_GITHUB_KEY_ALIAS': alias, 'MEI_GITHUB_KEY_PASSWORD': key_password}.items():
            env.write(key + '=' + value + '\n')
    ready = True
with open(os.environ['GITHUB_OUTPUT'], 'a') as out:
    out.write('ready=' + str(ready).lower() + '\n')
with open(os.environ['GITHUB_ENV'], 'a') as env:
    env.write('MEI_GITHUB_SIGNING_READY=' + str(ready).lower() + '\n')
print('Persistent APK signing is configured.' if ready else 'No existing signing identity configured; validation builds are not advertised as updates.')
