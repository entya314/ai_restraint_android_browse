#!/usr/bin/env python3
"""Package a committed source snapshot and portable Git history without nesting old archives."""
import hashlib
import io
from pathlib import Path
import subprocess
import tarfile
import tempfile
import zipfile

root = Path(__file__).resolve().parents[1]
def git(*args):
    return subprocess.check_output(['git', '-C', str(root), *args])

commit = git('rev-parse', 'HEAD').decode().strip()
branch = git('branch', '--show-current').decode().strip()
if not branch:
    raise SystemExit('Package from a named branch.')
dist = root / 'dist'
archive = dist / 'restricted-browser-complete.zip'
apks = sorted(dist.glob('*.apk'))
apk_sums = ''.join(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name + '\n' for p in apks)
scratch = root.parent / 'work'
scratch.mkdir(exist_ok=True)
with tempfile.TemporaryDirectory(prefix='restricted-package-', dir=scratch) as temp:
    bundle = Path(temp) / 'GIT-HISTORY.bundle'
    git('bundle', 'create', str(bundle), branch)
    git('bundle', 'verify', str(bundle))
    snapshot = git('archive', '--format=tar', 'HEAD')
    with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED) as out:
        with tarfile.open(fileobj=io.BytesIO(snapshot)) as source:
            for member in source.getmembers():
                if not member.isfile() or member.name in ('dist/restricted-browser-complete.zip', 'dist/SHA256SUMS.txt'):
                    continue
                data = source.extractfile(member).read()
                entry = zipfile.ZipInfo(member.name)
                entry.external_attr = (member.mode & 0o777) << 16
                entry.compress_type = zipfile.ZIP_DEFLATED
                out.writestr(entry, data)
        out.write(bundle, 'GIT-HISTORY.bundle')
        out.writestr('dist/SHA256SUMS.txt', apk_sums)
        out.writestr('RESTORE-GIT.txt', f'Source and Git history through {commit}.\n\n'
                     f'git clone -b {branch} GIT-HISTORY.bundle restricted-browser\n\n'
                     'Application: dist/restricted-browser-test.apk\n'
                     'Read INSTALL-WINDOWS.md and TEST-REPORT.md.\n'
                     'The inner checksum list covers APKs. The outer list also covers this ZIP.\n')
with zipfile.ZipFile(archive) as check:
    if check.testzip() is not None:
        raise SystemExit('Archive integrity verification failed.')
    for apk in apks:
        if check.read('dist/' + apk.name) != apk.read_bytes():
            raise SystemExit('Archive APK differs from the working artifact.')
    if 'dist/restricted-browser-complete.zip' in check.namelist():
        raise SystemExit('Nested old archive detected.')
archive_sum = hashlib.sha256(archive.read_bytes()).hexdigest()
(dist / 'SHA256SUMS.txt').write_text(apk_sums + archive_sum + '  ' + archive.name + '\n')
print(f'Packaged {archive.name} from {commit[:7]} ({archive.stat().st_size} bytes)')
