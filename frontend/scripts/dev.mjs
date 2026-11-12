import { createHash } from 'node:crypto';
import { existsSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const frontendDirectory = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const markerPath = resolve(frontendDirectory, 'node_modules/.outpick-install-state');

/** npm 명령을 Frontend 디렉터리에서 실행하고 실패하면 개발 서버 실행을 중단합니다. */
function runNpm(args) {
  const result = spawnSync('npm', args, {
    cwd: frontendDirectory,
    stdio: 'inherit',
  });

  if (result.error) {
    console.error(`npm 실행 실패: ${result.error.message}`);
    process.exit(1);
  }

  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

/** 의존성 파일과 실행 도구 버전을 비교할 수 있는 설치 기준값을 만듭니다. */
function getInstallState() {
  const npmVersion = spawnSync('npm', ['--version'], {
    cwd: frontendDirectory,
    encoding: 'utf8',
  });

  if (npmVersion.error || npmVersion.status !== 0) {
    console.error('npm 버전을 확인하지 못했습니다. Node.js와 npm 설치를 확인하세요.');
    process.exit(1);
  }

  const hash = createHash('sha256');
  for (const filename of ['package.json', 'package-lock.json']) {
    hash.update(readFileSync(resolve(frontendDirectory, filename)));
  }
  hash.update(`${process.version}:${npmVersion.stdout.trim()}:${process.platform}:${process.arch}`);
  return hash.digest('hex');
}

/** 마지막으로 성공한 설치 기록이 없거나 기준값이 바뀌면 npm ci로 다시 설치합니다. */
function ensureDependencies() {
  const installState = getInstallState();
  const previousState = existsSync(markerPath) ? readFileSync(markerPath, 'utf8') : null;

  if (previousState === installState
    && existsSync(resolve(frontendDirectory, 'node_modules/.bin/next'))) {
    console.log('의존성 변경이 없어 패키지 설치를 건너뜁니다.');
    return;
  }

  console.log('패키지 설치가 필요하여 npm ci를 실행합니다.');
  // 실패한 설치를 다음 실행에서 성공한 설치로 오인하지 않도록 이전 기록을 제거합니다.
  rmSync(markerPath, { force: true });
  runNpm(['ci']);
  writeFileSync(markerPath, installState);
}

ensureDependencies();
runNpm(['run', 'dev']);
