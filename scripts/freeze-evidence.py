#!/usr/bin/env python3
"""Freeze the verified Chapter16 source and exact executed artifacts once."""
from pathlib import Path
import hashlib, json, shutil, subprocess, xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
REPO=ROOT.parents[1]
OUT=ROOT/'evidence/2026-10-06'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
rel=lambda p:str(p.relative_to(REPO))
assert not (OUT/'verification.json').exists(), 'Preserve a completed freeze before making a new one'

def copy(file,target):
    target.parent.mkdir(parents=True,exist_ok=True)
    # APFS copy-on-write clone retains independent file identity without duplicating APK storage.
    result=subprocess.run(['cp','-c',str(file),str(target)],capture_output=True)
    if result.returncode: shutil.copy2(file,target)
    assert sha(file)==sha(target)

def tree(source,target):
    for file in sorted(source.rglob('*')):
        if file.is_file(): copy(file,target/file.relative_to(source))

reports=[('cart-tests',ROOT/'cart-impl/build/test-results/test',16,0),
         ('planner-tests',ROOT/'boundary-lab/build/test-results/test',21,0),
         ('learner-tests',ROOT/'boundary-lab/build/test-results/exerciseTest',21,14)]
for module,count in [('app',8),('repaired',8),('monolith',2)]:
    reports.append((module+'-android-tests',ROOT/module/'build/outputs/androidTest-results/connected/debug',count,0))
counts={}
for name,source,expected,failures in reports:
    files=list(source.rglob('TEST-*.xml'))
    suites=[suite for file in files for suite in ET.parse(file).getroot().iter('testsuite')]
    actual={key:sum(int(s.get(key,'0')) for s in suites) for key in ['tests','failures','errors','skipped']}
    assert actual=={'tests':expected,'failures':failures,'errors':0,'skipped':0},(name,actual)
    if failures:
        for file in files:
            assert all('AssertionFailedError' in f.get('type','') for f in ET.parse(file).getroot().iter('failure'))
    counts[name]=actual
    for file in files: copy(file,OUT/'reference'/name/file.name)

lint={}
for module in ['app','repaired','monolith']:
    file=ROOT/module/'build/reports/lint-results-debug.xml'
    issues=list(ET.parse(file).getroot())
    assert all(i.get('severity')=='Warning' and i.get('id')=='MissingApplicationIcon' for i in issues)
    lint[module]=[i.get('id') for i in issues]
    copy(file,OUT/'reference'/f'{module}-lint.xml')
copy(ROOT/'build/reports/module-edges.tsv',OUT/'reference/module-edges.tsv')
assert len((OUT/'reference/module-edges.tsv').read_text().splitlines())==7

hosts=sorted((ROOT/'evidence/host-journeys').iterdir())
host=hosts[-1]
result=json.loads((host/'result.json').read_text())
assert result['status']=='PASS' and len(list(host.glob('*.png')))==13
for module,item in result['installed_artifacts'].items():
    apk=ROOT/module/'build/outputs/apk/debug'/f'{module}-debug.apk'
    assert sha(apk)==item['sha256']==sha(host/item['file'])
assert sha(ROOT/'scripts/host-journey.py')==result['host_script_sha256']
tree(host,OUT/'final-host-journey')
for earlier in hosts[:-1]: tree(earlier,OUT/'earlier-host-journeys'/earlier.name)
probes=sorted((ROOT/'evidence/boundary-probes').iterdir())
probe_result=json.loads((probes[-1]/'result.json').read_text())
assert probe_result['status']=='PASS' and len(probe_result['witnesses'])==8
tree(probes[-1],OUT/'final-boundary-probes')
for earlier in probes[:-1]: tree(earlier,OUT/'earlier-boundary-probes'/earlier.name)

artifacts=[]
files=[]
for module in ['app','repaired','monolith']:
    files.extend([ROOT/module/'build/outputs/apk/debug'/f'{module}-debug.apk',
                  ROOT/module/'build/outputs/apk/androidTest/debug'/f'{module}-debug-androidTest.apk'])
files.extend(ROOT/module/'build/libs'/f'{module}.jar' for module in ['cart-api','cart-impl'])
for file in files:
    target=OUT/'artifacts'/file.name;copy(file,target)
    artifacts.append({'path':rel(target),'sha256':sha(target),'built_from':rel(file)})
sources=[]
for file in sorted(ROOT.rglob('*')):
    local=file.relative_to(ROOT)
    if not file.is_file() or set(local.parts)&{'evidence','build','.gradle','.kotlin','__pycache__'}: continue
    if file.name=='local.properties': continue
    if file.name not in {'.gitignore','README.md','verification.md','gradlew','gradlew.bat'} and file.suffix not in {'.kt','.kts','.toml','.properties','.xml','.json','.py','.jar'}: continue
    target=OUT/'source-snapshot'/local;copy(file,target)
    sources.append({'path':rel(file),'snapshot':rel(target),'sha256':sha(file)})
evidence=[{'path':rel(f),'sha256':sha(f)} for f in sorted(OUT.rglob('*'))
          if f.is_file() and 'source-snapshot' not in f.relative_to(OUT).parts and f.name!='verification.json']
manifest={'status':'PASS','date':'2026-10-06','edition_cutoff':'2026-10-04',
          'runtime':'JDK27 / Gradle9.8.0 / Kotlin2.4.20 / JVM26 / AGP9.4.1 / debug API36 ARM64 emulator',
          'reference_checks':{'cart_jvm':16,'planner_jvm':21,'modular_android':8,'singlemodule_android':8,'total':53},
          'defect_witnesses':{'monolith_android':2},
          'learner':{'cases':21,'intended_assertion_failures':14,'passes':7,'errors':0,'skipped':0},
          'test_counts':counts,'boundary_probes':probe_result,
          'final_build':{'tasks':315,'executed':247,'from_cache':63,'up_to_date':5,'fresh_all_tasks_run':False},
          'lint':lint,'final_host':{'path':rel(OUT/'final-host-journey'),'result':result},
          'sources':sources,'artifacts':artifacts,'evidence_files':evidence,
          'limits':['In-memory architecture fixture; process recreation reseeds quantity3',
                    'Kotlin compiler access and declared project policy, not Java/reflection security',
                    'No release shrinking, physical-device timing, API26/API37 device or build-speed claim',
                    'Debug SIGKILL is abrupt-process simulation, not observed OS low-memory kill']}
(OUT/'verification.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps({'status':'PASS','sources':len(sources),'artifacts':len(artifacts),'evidence_files':len(evidence)}))
