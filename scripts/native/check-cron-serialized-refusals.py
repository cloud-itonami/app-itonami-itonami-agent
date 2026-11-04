#!/usr/bin/env python3
"""Exercise the real serialized cron refusal entry using disposable FS2 files.

Clock and job body are test stubs; this does not prove successful dispatch,
workload lifetime, leases, GitHub, production host or resource governance.
"""
import argparse
import base64
import fcntl
import hashlib
import importlib.util
import json
import os
import platform
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('decisions',Path(__file__).with_name('check-cron-decisions.py'))
helper=importlib.util.module_from_spec(spec); spec.loader.exec_module(helper)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--unity',type=Path,required=True); p.add_argument('--unity-sha256',required=True)
    p.add_argument('--compiler',type=Path,required=True); p.add_argument('--compiler-sha256',required=True)
    p.add_argument('--packager',type=Path,required=True); p.add_argument('--packager-sha256',required=True)
    a=p.parse_args()
    if (platform.system(),platform.machine())!=('Darwin','arm64'): raise ValueError('Mac ARM64 required')
    if a.unity.is_symlink(): raise ValueError('unity symlink refused')
    raw=a.unity.read_bytes()
    if helper.digest(raw)!=a.unity_sha256: raise ValueError('unity hash mismatch')
    source=raw.decode(); current=(ROOT/'kotoba/itonami/gateway_guarded_job_node.kotoba').read_text()
    for name in ('cjg-run','cjg-cursor-problem','cron-serialized-job-node-main'):
        i,j=helper.form(current,name); x,y=helper.form(source,name)
        if current[i:j]!=source[x:y]: raise ValueError('guard source mismatch: '+name)
    replacements={
        'ln-now':'(defn- ln-now [s :string] :i64 1792638000000)',
        'jn-run-with-verified-input':'(defn- jn-run-with-verified-input [source :string source-admitted :bool verified-input :string use-verified-input :bool expected-signer :string] :i64 (let [w (cp-write "/case/dispatched" (cp-bytes "unexpected"))] 0))',
        'main':'(defn main [] :i64 (cron-serialized-job-node-main))',
    }
    edits=[]
    for name,replacement in replacements.items():
        i,j=helper.form(source,name); edits.append((i,j,replacement))
    for i,j,replacement in sorted(edits,reverse=True): source=source[:i]+replacement+source[j:]
    x,y=helper.form(source,'cjg-cursor-problem'); guard=source[x:y]; anchor='(not (= turn "0"))'
    if guard.count(anchor)!=1: raise ValueError('turn guard control changed')
    mutant=source[:x]+guard.replace(anchor,'false')+source[y:]
    positive_replacements={
        'cc-plan-problem':'(defn- cc-plan-problem [definition :vector-i64 pin :string cursor :vector-i64 now :i64] :string "trigger/ok")',
        'cj-problem':'(defn- cj-problem [now :i64 previous :i64 pin :string definition :vector-i64 profile :string input :string policy :string rail :string wrapper :vector-i64 source :vector-i64] :string "consume/ok")',
        'next-cron-ms':'(defn next-cron-ms [e :string after-ms :i64 offset :i64] :i64 60000)',
        'verified-cron-job-id':'(defn verified-cron-job-id [active :bool now :i64 previous :i64 slot :i64 expected :string definition :vector-i64] :string "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")',
        'key-seed':'(defn key-seed [key-path :string] :vector-i64 [1])',
        'node-did':'(defn node-did [seed :vector-i64] :string "did:test")',
        'jn-run-with-verified-input':'(defn- jn-run-with-verified-input [source :string source-admitted :bool verified-input :string use-verified-input :bool expected-signer :string] :i64 (if (and source-admitted use-verified-input (= source "source") (= verified-input "wrapper") (= expected-signer "did:test") (= (cp-fs "D" "/case/cursor.turn-intent" "") "1") (cp-write "/case/dispatched" (cp-bytes "called"))) 0 1))',
    }
    positive=source; edits=[]
    for name,replacement in positive_replacements.items():
        i,j=helper.form(positive,name); edits.append((i,j,replacement))
    for i,j,replacement in sorted(edits,reverse=True): positive=positive[:i]+replacement+positive[j:]
    compiler=helper.verified(a.compiler,a.compiler_sha256); packager=helper.verified(a.packager,a.packager_sha256)
    with tempfile.TemporaryDirectory(prefix='awai167-serialized-',dir='/private/tmp') as tmp:
        root=Path(tmp); case=root/'case'; case.mkdir(mode=0o700)
        (case/'cron.lock').write_text(''); (case/'cron.lock').chmod(0o600)
        (case/'definition').write_text('{}'); (case/'cursor').write_text('{}')
        (root/'fixture.kotoba').write_text(source)
        (root/'mutant.kotoba').write_text(mutant)
        (root/'positive.kotoba').write_text(positive)
        fd=os.open(root,os.O_RDONLY|os.O_DIRECTORY); saved=None
        if fd!=3:
            try: saved=os.dup(3)
            except OSError: pass
            os.dup2(fd,3)
        try:
            def run(argv,expected=0):
                r=subprocess.run([str(x) for x in argv],cwd=root,pass_fds=(3,),capture_output=True,timeout=180,
                                 env={'PATH':'/nonexistent'})
                if r.returncode!=expected or r.stderr: raise RuntimeError((str(argv[0]),r.returncode,r.stdout[-300:],r.stderr[-300:]))
                return r.stdout
            def build(name):
                run([compiler,'compile',name+'.kotoba','--target','aarch64-macos','--output',name+'.kseed'])
                out=run([compiler,'extract-native',name+'.kseed','--symbol','main','--output',name+'.raw'])
                m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',out)
                if not m or int(m.group(2))!=(root/(name+'.raw')).stat().st_size: raise ValueError('extract mismatch')
                run([packager,name+'-unsigned.macho',name+'.raw','fs2-cli-stdio-general',m.group(1).decode()])
                image=root/(name+'.macho'); shutil.copyfile(root/(name+'-unsigned.macho'),image); image.chmod(0o700)
                subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
                subprocess.run(['/usr/bin/codesign','--verify','--strict',str(image)],check=True,capture_output=True,timeout=30)
                return image
            image=build('fixture'); mutant_image=build('mutant'); positive_image=build('positive')
            args=['run-cron','profile','input','policy','rail','node-a','/case/key','fd','admitted','clock',
                  'other','/case/policy','/case/environment','ports','x','y','/case/input','/case/token',
                  '/case/attempts','0','pin','/case/definition','/case/source','capture-output','/case/cursor','/case/cron.lock']
            def expect(message):
                out=run([image,*args],1).decode()
                if out!=message+'\n': raise ValueError(('unexpected response',out,message))
                if (case/'dispatched').exists(): raise ValueError('refusal reached job body')
            with (case/'cron.lock').open('r+') as lock:
                fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB); expect('cron-lock/busy-or-unknown')
            for suffix,message in [('.pending','cron-guard/pending-unknown'),('.intent','cron-guard/intent-unknown'),
                                   ('.head-intent','cron-guard/head-intent-unknown'),('.turn-intent','cron-guard/turn-intent-unknown')]:
                marker=case/('cursor'+suffix); marker.write_text('synthetic')
                expect(message)
                if marker.read_text()!='synthetic': raise ValueError('marker changed')
                marker.unlink()
            expect('cron-guard/plan-refused')
            marker=case/'cursor.turn-intent'; marker.write_text('synthetic')
            if run([mutant_image,*args],1).decode()!='cron-guard/plan-refused\n':
                raise ValueError('turn-guard removal control not detected')
            if marker.read_text()!='synthetic' or (case/'dispatched').exists():
                raise ValueError('negative control changed state')
            marker.unlink()
            cursor=b'{"kind":"itonami.cron-cursor/1","schedule":"pin","slot":0}'
            (case/'cursor').write_bytes(cursor); (case/'source').write_text('source'); (case/'input').write_text('wrapper')
            if run([positive_image,*args])!=b'': raise ValueError('unexpected positive output')
            if (case/'dispatched').read_text()!='called': raise ValueError('job stub did not run')
            cid='b'+base64.b32encode(b'\x01\x55\x12\x20'+hashlib.sha256(cursor).digest()).decode().lower().rstrip('=')
            h=lambda x: hashlib.sha256(x.encode()).hexdigest()
            intent=('jti4\npin\n'+cid+'\n'+'a'*64+'\n60000\n'+h('/case/cron.lock')+'\n'+h('/case/token')+'\n'+
                    h('ports')+'\n'+h('node-a')+'\n'+h('did:test')+'\n')
            if marker.read_text()!=intent: raise ValueError('turn marker binding mismatch')
            (case/'dispatched').write_text('existing')
            if run([positive_image,*args],1).decode()!='cron-guard/turn-intent-unknown\n':
                raise ValueError('duplicate turn was not refused')
            if marker.read_text()!=intent or (case/'dispatched').read_text()!='existing':
                raise ValueError('duplicate turn changed state')
            print(json.dumps({'unitySha256':a.unity_sha256,'fixtureSha256':helper.digest(source.encode()),
                              'positiveFixtureSha256':helper.digest(positive.encode()),
                              'guardSourceSha256':helper.digest(current.encode()),'compilerSha256':a.compiler_sha256,
                              'packagerSha256':a.packager_sha256,'busyAndFourMarkersRefused':True,
                              'invalidPlanRefused':True,'turnGuardRemovalDetected':True,'jobStubNotReached':True,
                              'stubbedPositiveMarkerBeforeJob':True,'duplicateTurnRefused':True,
                              'scope':'native guard, diagnostic FS2 and flock; clock/job stubs; no production qualification'}))
        finally:
            if fd!=3:
                if saved is None: os.close(3)
                else: os.dup2(saved,3); os.close(saved)
            os.close(fd)


if __name__=='__main__': main()
