import re,sys
s=open(sys.argv[1]).read()
# map: question -> steps it must gate (progress row id) and a prompt marker that must name it
need={'Q1':['4','7'],'Q2':['7'],'Q3':['5'],'Q4':['D1','5'],'Q5':['A4'],'Q6':['A1','I1']}
rows={m.group(1):m.group(0) for m in re.finditer(r'^\| (\w+) \|.*$',s,re.M)}
fail=0
for q,steps in need.items():
    for st in steps:
        ok=q in rows.get(st,'')
        print(f'{q} in Progress row {st}: {"ok" if ok else "MISSING"}'); fail|= not ok
    ok=re.search(rf'^\| {q} \| [^|]+\|',s,re.M) is not None
    print(f'{q} has an answer row: {"ok" if ok else "MISSING"}'); fail|= not ok
# every gated prompt checks the answers table
for marker in ['Step <A-n>','Step <I-n>','Do RUNBOOK Step D1','Do RUNBOOK Step 4','Do RUNBOOK Step 5','Do RUNBOOK Step 7','Do RUNBOOK Step 1']:
    i=s.find(marker); blk=s[max(0,s.rfind('```',0,i)):s.find('```',i)]
    ok="Robin's answers" in " ".join(blk.split())
    print(f'prompt "{marker}" checks Robin\'s answers: {"ok" if ok else "MISSING"}'); fail|= not ok
print('RESULT', 'FAIL' if fail else 'PASS'); sys.exit(fail)
