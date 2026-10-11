#!/usr/bin/env python3
"""Run compile and graph-policy witnesses in a disposable copy, never in learner sources."""
from pathlib import Path
import datetime, hashlib, json, os, re, shutil, subprocess

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'evidence/boundary-probes' / datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
OUT.mkdir(parents=True, exist_ok=False)
WORK = ROOT / 'build/boundary-probe-work'
if WORK.exists(): shutil.rmtree(WORK)
shutil.copytree(ROOT, WORK, ignore=shutil.ignore_patterns('build', '.gradle', '.kotlin', 'evidence', '__pycache__'))
(WORK/'settings.gradle.kts').write_text((WORK/'settings.gradle.kts').read_text() + '\ninclude(":probe")\n')
probe = WORK/'probe/src/main/kotlin/Probe.kt'
probe.parent.mkdir(parents=True)
build = WORK/'probe/build.gradle.kts'
results = []

def configure(dependency, code):
    build.write_text('''plugins { alias(libs.plugins.kotlinJvm); `java-library` }
kotlin { jvmToolchain(27); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26) } }
java { sourceCompatibility = JavaVersion.VERSION_26; targetCompatibility = JavaVersion.VERSION_26 }
dependencies { implementation(project("''' + dependency + '''")) }
''')
    probe.write_text(code)

def witness(name, task, success, pattern=None):
    snapshot = OUT/name
    snapshot.mkdir()
    for relative in ['settings.gradle.kts','build.gradle.kts','cart-api/build.gradle.kts',
                     'feature-saved/build.gradle.kts','probe/build.gradle.kts','probe/src/main/kotlin/Probe.kt',
                     'repaired/src/main/kotlin/InternalWitness.kt']:
        file = WORK/relative
        if file.exists():
            target = snapshot/relative
            target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copy2(file,target)
    command = ['./gradlew', task, '--no-configuration-cache', '--console=plain']
    run = subprocess.run(command,cwd=WORK,capture_output=True,text=True)
    output = run.stdout + run.stderr
    (snapshot/'gradle.log').write_text(output)
    matched = [line for line in output.splitlines() if pattern and re.search(pattern,line,re.I)]
    assert (run.returncode == 0) == success, (name,run.returncode,output[-4000:])
    if not success: assert matched, (name,'failure lacked the expected diagnostic',output[-4000:])
    results.append({'name':name,'command':command,'expected_success':success,'exit_code':run.returncode,
                    'diagnostic':matched,'status':'PASS'})
    print(name + ': PASS',flush=True)

try:
    api_code = '''import staff.android.boundaries.api.CartRepository
fun read(cart: CartRepository): Int = cart.state.value.quantityOf("tea")
'''
    configure(':cart-api',api_code)
    witness('01-api-exports-stateflow',':probe:compileKotlin',True)
    configure(':cart-impl','''import staff.android.boundaries.impl.createCart
import staff.android.boundaries.api.CartItem
import staff.android.boundaries.api.CartRepository
fun make(): CartRepository = createCart(setOf("tea"), 4, listOf(CartItem("tea", 3)))
''')
    witness('02-factory-exports-contract',':probe:compileKotlin',True)
    configure(':cart-api','''import staff.android.boundaries.impl.createCart
fun make() = createCart(setOf("tea"), 4, emptyList())
''')
    witness('03-api-cannot-see-implementation',':probe:compileKotlin',False,r'Unresolved reference.*(impl|createCart)')
    configure(':cart-impl','''import staff.android.boundaries.impl.DefaultCart
internal fun concrete(value: DefaultCart) = value
''')
    witness('04-internal-is-hidden-from-external-kotlin',':probe:compileKotlin',False,r'(Cannot access|invisible).*DefaultCart')
    configure(':cart-api',api_code)
    api_build = WORK/'cart-api/build.gradle.kts'
    original = api_build.read_text()
    assert 'api(libs.coroutinesCore)' in original
    api_build.write_text(original.replace('api(libs.coroutinesCore)','implementation(libs.coroutinesCore)'))
    witness('05-wrong-export-breaks-consumer',':probe:compileKotlin',False,r'(Cannot access|Unresolved reference).*(StateFlow|value)')
    api_build.write_text(original)
    feature_build = WORK/'feature-saved/build.gradle.kts'
    original = feature_build.read_text()
    feature_build.write_text(original + '\ndependencies { implementation(project(":feature-cart")) }\n')
    witness('06a-forbidden-feature-edge-compiles',':feature-saved:compileDebugKotlin',True)
    witness('06b-forbidden-feature-edge-policy',':verifyBoundaries',False,r'Boundary violation.*feature-saved.*feature-cart')
    feature_build.write_text(original)
    (WORK/'repaired/src/main/kotlin/InternalWitness.kt').write_text('''import staff.android.boundaries.impl.DefaultCart
import staff.android.boundaries.impl.CartRules
internal fun makeInsideOneCompilerModule() = DefaultCart(CartRules(setOf("tea"), 4), emptyList())
''')
    witness('07-single-module-can-see-internal',':repaired:compileDebugKotlin',True)
    report = {'status':'PASS','witnesses':results,
              'scope':'Ordinary Kotlin consumer compilation and declared direct production dependency policy; not Java/reflection security or build performance.'}
    (OUT/'result.json').write_text(json.dumps(report,indent=2)+'\n')
    print(OUT)
except Exception as error:
    (OUT/'result.json').write_text(json.dumps({'status':'FAIL','completed':results,'error':str(error)},indent=2)+'\n')
    raise
