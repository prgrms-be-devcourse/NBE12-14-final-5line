import { spawnSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../', import.meta.url));
const [reportPath, ...options] = process.argv.slice(2);
if (!reportPath || options.some(option => option !== '--apply') || options.length > 1) {
  console.error('사용법: node backend/scripts/classify-tour-place-filters.mjs <점검 CSV 경로> [--apply]');
  process.exit(1);
}

function mysql(sql) {
  const result = spawnSync('docker', [
    'compose', '-f', 'compose.yaml', 'exec', '-T', 'mysql', 'sh', '-c',
    'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --default-character-set=utf8mb4 --batch',
  ], { cwd: root, input: sql, encoding: 'utf8' });
  if (result.error || result.status !== 0) {
    // SQL 전체 또는 환경 변수를 오류 출력에 포함하지 않는다.
    throw new Error('로컬 Compose MySQL 실행에 실패했습니다.');
  }
  return result.stdout.trimEnd();
}

// 이름에 종목을 직접 드러내는 표현만 사용한다. 복합 시설, 지명, 일반 체육관은 남겨 둔다.
const rules = [
  [/실[내외]\s*수영장|야외\s*수영장|수영장\s*\(실[내외]\)/u, '수상', '수영'],
  [/실외\s*물놀이장|야외\s*물놀이장/u, '수상', '물놀이'],
  [/실내\s*빙상장|아이스링크/u, '실내', '아이스스케이팅'],
  [/눈\s*썰매장/u, '체험', '눈썰매'],
  [/레일\s*바이크/u, '체험', '레일바이크'],
  [/수상\s*스키/u, '수상', '수상스키'],
  [/요트\s*투어/u, '수상', '요트'],
  [/실탄\s*사격장/u, '체험', '사격'],
  [/캠핑장|글램핑/u, '체험', '캠핑'],
  [/자전거\s*(도로|길|코스|관광코스|하이킹)/u, '체험', '자전거'],
];

function candidateFor(rows) {
  const { name } = rows[0];
  const matches = rules.filter(([pattern]) => pattern.test(name));
  if (matches.length !== 1) return null;
  const [, category, activity] = matches[0];
  const known = rows.find(row => row.category_name === category && row.activity_name === activity);
  if (!known && rows.some(row => row.place_activity_id !== 'NULL')) return null;
  const indoor = /실내\s*(수영장|빙상장)|수영장\s*\(실내\)/u.test(name);
  const outdoor = /(?:실외|야외)\s*(?:수영장|물놀이장)|수영장\s*\(실외\)/u.test(name);
  const environment = indoor === outdoor ? null : indoor ? 'INDOOR' : 'OUTDOOR';
  return { category, activity, add: !known, environment: known && known.environment_type !== 'NULL'
    ? null : environment };
}

const targetSource = readFileSync(resolve(root,
  'backend/src/main/java/com/outpick/domain/place/importer/TourImportTarget.java'), 'utf8');
const targets = new Map([...targetSource.matchAll(/(?:withActivities|unclassified)\("(\d+)",\s*"([^"\\]*)"/gu)]
  .map(([, id, name]) => [id, name]));
if (!targets.size) throw new Error('수집 대상 목록을 읽지 못했습니다.');

const audit = mysql(readFileSync(resolve(root, 'backend/scripts/audit-place-filters.sql'), 'utf8'));
const [, , header, ...lines] = audit.split(/\r?\n/);
const columns = header.split('\t');
const rowsById = Map.groupBy(lines.map(line => Object.fromEntries(
  line.split('\t').map((value, index) => [columns[index], value]),
)), row => row.content_id);
const report = [['TourAPI ID', '장소', '현재 종목', '현재 실내외', '현재 시즌', '종목 추가 후보',
  '실내외 후보', '후속 확인', '공식 안내 후보 URL']];
const changes = [];
for (const [id, targetName] of targets) {
  const rows = rowsById.get(id);
  if (!rows) {
    report.push([id, targetName, '', '', '', '', '', '미수집', '']);
    continue;
  }
  const first = rows[0];
  const candidate = first.publish_status === '1' && first.name === targetName ? candidateFor(rows) : null;
  if (candidate && (candidate.add || candidate.environment)) changes.push({ id, name: first.name, ...candidate });
  const values = key => [...new Set(rows.map(row => row[key]).filter(value => value !== 'NULL'))].join(' / ');
  const pending = [
    rows.some(row => row.missing_activity === '1') && !candidate?.add ? '종목' : '',
    rows.some(row => row.missing_environment === '1' && (
      !candidate?.environment || (!candidate.add &&
        (row.category_name !== candidate.category || row.activity_name !== candidate.activity))
    )) ? '실내외' : '',
    rows.some(row => row.missing_seasons === '1') ? '시즌' : '',
  ].filter(Boolean).join(' / ');
  report.push([id, first.name, values('activity_name'), values('environment_type'), values('seasons'),
    candidate?.add ? candidate.activity : '', candidate?.environment ?? '', pending, values('homepage')]);
}

const csvCell = cell => {
  // Excel에서 장소명이나 URL을 수식으로 해석하지 않도록 한다.
  const safe = /^[=+@\-\t\r]/u.test(cell) ? "'" + cell : cell;
  return '"' + safe.replaceAll('"', '""') + '"';
};
writeFileSync(resolve(reportPath), '\uFEFF' + report.map(row => row.map(csvCell).join(',')).join('\r\n') + '\r\n',
  { encoding: 'utf8', flag: 'wx' });
console.log(`수집 대상 ${targets.size}곳, 분류 후보 ${changes.length}곳`);

if (options.includes('--apply') && changes.length) {
  // UTF-8 hex literal로 값과 SQL 구문을 분리한다.
  const literal = value => value === null ? 'NULL' : `CONVERT(X'${Buffer.from(value).toString('hex')}' USING utf8mb4)`;
  const entries = changes.map(row => [row.id, row.name, row.category, row.activity, row.environment]
    .map(literal).concat(row.add ? 'TRUE' : 'FALSE').join(', '));
  const sql = readFileSync(resolve(root, 'backend/scripts/apply-tour-place-filter-candidates.sql'), 'utf8');
  console.log(mysql(sql.replace('/* 후보 VALUES */', entries.map(entry => '(' + entry + ')').join(',\n'))));
}
