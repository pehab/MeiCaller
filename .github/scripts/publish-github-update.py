"""Publish a tested, persistently signed APK and its immutable download manifest."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

repo = os.environ['GH_REPO']
name = repo.split('/')[1]
metadata = json.loads(Path('apk/output-metadata.json').read_text())['elements'][0]
code = int(metadata['versionCode'])
version = metadata['versionName']
if code <= 0 or code > 2147483647 or not version or any(ord(c) < 32 for c in version):
    raise SystemExit('Invalid APK version')
tag = 'github-v' + str(code)
filename = name + '-' + str(code) + '.apk'
apk = Path(filename)
shutil.copyfile('apk/app-debug.apk', apk)
manifest = {'schema': 1, 'versionCode': code, 'versionName': version,
            'url': 'https://github.com/' + repo + '/releases/download/' + tag + '/' + filename,
            'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'size': apk.stat().st_size}
feed = Path(name + '-update.json')
feed.write_text(json.dumps(manifest, indent=2) + '\n')
def exists(release):
    return subprocess.run(['gh', 'release', 'view', release], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0
def run(*args):
    subprocess.run(['gh', *args], check=True)
if exists(tag):
    print('This versionCode is already published. Increase versionCode for the next update; existing APKs remain immutable.')
else:
    run('release', 'create', tag, str(apk), str(feed), '--target', os.environ['GITHUB_SHA'], '--prerelease',
        '--title', name + ' ' + version, '--notes', 'Signierte GitHub-Version. Vorhandene App-Daten bleiben beim Update mit gleicher Signatur erhalten.')
    if not exists('updates'):
        run('release', 'create', 'updates', '--target', os.environ['GITHUB_SHA'], '--prerelease',
            '--title', name + ' · Update-Kanal', '--notes', 'Update-Metadaten für direkt installierte APKs.')
    run('release', 'upload', 'updates', str(feed), '--clobber')
    print('Published verified update metadata for ' + name + ' ' + version)
