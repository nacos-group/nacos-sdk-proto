#!/usr/bin/env node
// Packs the npm package, installs the tarball into a throwaway project and uses
// it the way consumers do. Compiling alone cannot catch a package that fails to
// load once installed, or one that ships without its build output.
//
//     node scripts/check-node-package.js
'use strict';

const assert = require('assert');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

const PACKAGE_NAME = '@nacos-group/sdk-proto';
const PACKAGE_DIR = path.resolve(__dirname, '..', 'nodejs');
const MIN_JS_FILES = 50;

function run(command, args, cwd) {
    return execFileSync(command, args, { cwd, encoding: 'utf8', stdio: 'pipe' });
}

function listFiles(dir, root = dir) {
    return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
        const file = path.join(dir, entry.name);
        return entry.isDirectory() ? listFiles(file, root) : [path.relative(root, file)];
    });
}

function packTarball(workDir) {
    run('npm', ['pack', '--ignore-scripts', '--pack-destination', workDir], PACKAGE_DIR);
    const tarball = fs.readdirSync(workDir).find((name) => name.endsWith('.tgz'));
    assert.ok(tarball, 'npm pack produced no tarball');
    return path.join(workDir, tarball);
}

function checkPackedFiles(tarball, workDir) {
    const extractDir = path.join(workDir, 'extracted');
    fs.mkdirSync(extractDir);
    run('tar', ['-xzf', tarball, '-C', extractDir]);

    const files = listFiles(path.join(extractDir, 'package'));
    for (const file of files) {
        assert.match(file, /^(package\.json|dist\/.+\.(js|d\.ts))$/, `unexpected packed file: ${file}`);
    }
    const jsFiles = files.filter((file) => file.endsWith('.js'));
    assert.ok(jsFiles.length > MIN_JS_FILES, `only ${jsFiles.length} JS files were packed`);
}

function installTarball(tarball, workDir) {
    const installDir = path.join(workDir, 'consumer');
    fs.mkdirSync(installDir);
    fs.writeFileSync(path.join(installDir, 'package.json'), '{"private":true}\n');
    run('npm', ['install', tarball, '--ignore-scripts', '--no-audit', '--no-fund'], installDir);
    return installDir;
}

function checkLoading(installDir) {
    const load = (id) => require(require.resolve(id, { paths: [installDir] }));

    assert.ok(load(PACKAGE_NAME).Instance, 'root entry does not export Instance');
    assert.ok(
        load(`${PACKAGE_NAME}/src/common/common`).ConnectionSetupRequest,
        'src/ deep path does not export ConnectionSetupRequest',
    );

    const esmImport = `import { Instance } from '${PACKAGE_NAME}'; if (!Instance) process.exit(1);`;
    run(process.execPath, ['--input-type=module', '-e', esmImport], installDir);

    const manifest = JSON.parse(fs.readFileSync(
        path.join(installDir, 'node_modules', PACKAGE_NAME, 'package.json'),
        'utf8',
    ));
    assert.strictEqual(manifest.dependencies, undefined, 'package must not declare dependencies');
}

function checkFieldValues(installDir) {
    const { ConfigBatchListenRequest, Instance, InstanceRequest } = require(
        require.resolve(PACKAGE_NAME, { paths: [installDir] }),
    );
    const ip = '1.1.1.1';
    const port = 8848;

    // Java defaults for these are true or 1.0, so dropping them changes the request.
    const zeroValues = { weight: 0, healthy: false, enabled: false, ephemeral: false };
    assert.deepStrictEqual(Instance.toJSON({ ip, port, ...zeroValues }), { ip, port, ...zeroValues });
    assert.deepStrictEqual(
        ConfigBatchListenRequest.toJSON({ listen: false, configListenContexts: [] }),
        { listen: false },
    );
    assert.deepStrictEqual(
        InstanceRequest.toJSON({ type: 'registerInstance', instance: { ip, port, enabled: false } }).instance,
        { ip, port, enabled: false },
    );

    // Unset fields must stay absent so that the server falls back to the Java defaults.
    assert.deepStrictEqual(Instance.toJSON({ ip, port }), { ip, port });
}

function main() {
    assert.ok(
        fs.existsSync(path.join(PACKAGE_DIR, 'dist', 'index.js')),
        'nodejs/dist/index.js is missing; run make build-nodejs',
    );

    const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'nacos-sdk-proto-pack-'));
    try {
        const tarball = packTarball(workDir);
        checkPackedFiles(tarball, workDir);
        const installDir = installTarball(tarball, workDir);
        checkLoading(installDir);
        checkFieldValues(installDir);
    } finally {
        fs.rmSync(workDir, { recursive: true, force: true });
    }
    console.log('Node package check passed');
}

try {
    main();
} catch (error) {
    console.error(`Node package check failed: ${error.message}`);
    process.exit(1);
}
