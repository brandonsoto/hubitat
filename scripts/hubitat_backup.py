#!/usr/bin/env python

import argparse
import requests
from datetime import datetime
from pathlib import Path


def get_args():
    args = argparse.ArgumentParser()
    args.add_argument('--backup-url', type=str, required=True)
    args.add_argument('--backup-dir', type=str, required=True)
    args.add_argument('--max-backups', type=int, default=10)
    args.add_argument('--timeout', type=int, default=60)
    return args.parse_args()


def download_latest_backup(backup_url, timeout):
    try:
        return requests.get(backup_url, timeout=timeout).content
    except Exception as e:
        print('Exception occurred: {}'.format(e))
        return None


def write_to_file(data, backup_dir):
    if not backup_dir.exists():
        print('Backup directory does not exist. Creating {}'.format(str(backup_dir)))
        backup_dir.mkdir(parents=True)

    if data is not None:
        datestr = datetime.now().strftime('%Y-%m-%d-%H-%M-%S')
        filepath = Path(backup_dir, '{}-backup.lzf'.format(datestr))
        with filepath.open(mode='wb') as f:
            print('Writing to {}'.format(str(filepath)))
            f.write(data)


def remove_old_backups(backup_dir, max_backups):
    backup_files = sorted(list(backup_dir.glob('*.lzf')))[::-1]
    while len(backup_files) > max_backups:
        backup = backup_files.pop()
        print('Removing {}'.format(str(backup)))
        backup.unlink()


def main():
    args = get_args()
    backup_dir = Path(args.backup_dir)

    data = download_latest_backup(args.backup_url, args.timeout)
    write_to_file(data, backup_dir)
    remove_old_backups(backup_dir, args.max_backups)


if __name__ == '__main__':
    main()
