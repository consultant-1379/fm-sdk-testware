#!/usr/bin/env python3

import argparse
import glob
import json
import os
import shutil
import subprocess
import sys
from getpass import getuser
from os import makedirs
from os.path import isdir, join, dirname, abspath
from pwd import getpwnam
from typing import List

MVN = shutil.which('mvn')
if not MVN:
    MVN = os.environ.get('MVN')
    if not MVN:
        raise SystemExit("No mvn executable found (Not on $PATH nor $MVN)")


class Maven:
    @staticmethod
    def execute(command: List[str], cwd=None) -> None:
        print('\n\t'.join(command))
        subprocess.run(command, check=True, cwd=cwd)

    @staticmethod
    def grep(command: List[str], expression: str) -> bool:
        cmd = subprocess.Popen(command, stdout=subprocess.PIPE)
        grep = subprocess.Popen(['grep', expression],
                                stdin=cmd.stdout,
                                stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE)
        cmd.stdout.close()
        grep.wait()
        return grep.returncode == 0

    @staticmethod
    def generate(artifact_id: str, args: dict, build_dir: str) -> None:
        command = [
            MVN, 'archetype:generate', '--batch-mode', '--fail-fast',
        ]
        for prop_name, prop_val in args.items():
            if prop_name.startswith('_'):
                continue
            command.append(f'-D{prop_name}={prop_val}')

        _dir = join(build_dir, artifact_id)
        if isdir(_dir):
            shutil.rmtree(_dir)

        Maven.execute(command, build_dir)

    @staticmethod
    def copy_targets(package_type: str, data: dict, build_dir: str):
        targets = data.get('_targets')
        for target_type, target_files in targets.items():
            for target_file_path in target_files:
                target_file_path = target_file_path.replace('$', '')
                target_file_path = target_file_path.format(**data)
                target_file_path = join(build_dir, target_file_path)

                files = glob.glob1(target_file_path, '*.rpm')
                if len(files) == 0:
                    raise Exception(f'No RPMs found in {target_file_path}')

                dest_dir = join(build_dir, package_type, target_type)
                print(f'Coping {files[0]} to {dest_dir}')
                if not isdir(dest_dir):
                    os.makedirs(dest_dir, exist_ok=True)
                shutil.copyfile(join(target_file_path, files[0]),
                                join(dest_dir, files[0]))

    @staticmethod
    def package_install(artifact_id: str, build_dir: str) -> None:
        command = [MVN, '-f', f'{artifact_id}/pom.xml',
                   'clean', 'install', 'package']
        Maven.execute(command, cwd=build_dir)

    @staticmethod
    def package_uninstall(artifact_id: str, build_dir: str) -> None:
        command = [MVN, '-f', f'{artifact_id}/pom.xml', 'clean']
        Maven.execute(command, cwd=build_dir)

        command = [MVN, '-f', f'{artifact_id}/pom.xml', 'install',
                   'package', '-Premove-models']
        Maven.execute(command, cwd=build_dir)

    @staticmethod
    def build(args: dict, build_dir: str):
        artifact_id = args.get('artifactId')

        args['version'] = args['_version_install']
        Maven.generate(artifact_id, args, build_dir)

        Maven.package_install(artifact_id, build_dir)
        Maven.copy_targets('install', args, build_dir)

        pom = f'{build_dir}/{artifact_id}/pom.xml'
        if Maven.grep([MVN, '-f', pom, 'help:all-profiles'], 'remove-models'):
            version = args['_version_uninstall']
            Maven.execute([MVN, '-f', pom, 'versions:set', f'-DnewVersion={version}'])
            Maven.package_uninstall(artifact_id, build_dir)
            Maven.copy_targets('uninstall', args, build_dir)
        else:
            print(f'Artifact {artifact_id} has no "remove-models" '
                  f'profile, skipping.')


def main() -> None:
    _parser = argparse.ArgumentParser()
    _parser.add_argument('-a', dest='archetypes', required=True,
                         help='SDK archetypes')
    _parser.add_argument('-t', dest='archetype_type', required=True,
                         help='SDK archetype type i.e PM or FM')

    _parser.add_argument('-i', dest='version_install', default='1.0.0',
                         required=True, help='Version to use for model ')
    _parser.add_argument('-u', dest='version_uninstall', default='1.0.1',
                         required=True, help='Version to use to uninstall models')

    _parser.add_argument('-d', dest='destination', required=False,
                         help='Build dir, defaults to ${CWD}/[archetype type]')

    if len(sys.argv) <= 1:
        _parser.print_usage()
        exit(2)

    _args = _parser.parse_args()
    if _args.destination:
        build_dir = _args.destination
        if isdir(build_dir):
            uid = getpwnam(getuser()).pw_uid
            shutil.chown(build_dir, uid)
        else:
            makedirs(build_dir)
    else:
        build_dir = dirname(__file__)

    build_dir = join(abspath(build_dir), _args.archetype_type)
    if not isdir(build_dir):
        print(f'Creating build dir {build_dir}')
        makedirs(build_dir, exist_ok=True)
    print(f'Build output dir: {build_dir}')

    _archetypes_file = join(dirname(__file__), _args.archetypes)
    with open(_archetypes_file) as _reader:
        archetypes = json.load(_reader)

    for archetype_artifact_id, data in archetypes.items():
        if archetype_artifact_id.startswith('__'):
            continue
        data['_version_install'] = _args.version_install
        data['_version_uninstall'] = _args.version_uninstall
        Maven.build(data, build_dir)


if __name__ == '__main__':
    main()
