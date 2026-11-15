"""What the balance scripts share: the work folder, the roster as BalanceSim's RosterDump writes it,
and a run's results (scripts/CLAUDE.md, *Balance*)."""
import collections, math, os
# The work folder (setup.sh makes it): orig/, jars/, cand/<name>/, runs/<name>_A.csv and _B.csv
S = os.environ.get('BALANCE_DIR') or exit('set BALANCE_DIR to the work folder setup.sh made')

def roster(path):
    chars={}; projs={}; traps={}
    for line in open(path):
        f=line.rstrip('\n').split('\t')
        if f[0]=='C':
            chars[int(f[1])]=dict(id=int(f[1]),name=f[2],role=f[3],hp=int(f[4]),p=f[5],q=f[6],qcast=f[7],qcd=int(f[8]),qname=f[9],e=f[10],ecast=f[11],ecd=int(f[12]),ename=f[13])
        elif f[0]=='P':
            projs[int(f[1])]=dict(id=int(f[1]),name=f[2],damage=int(f[3]),charge=f[4],dist=f[5],aoehit=f[6],aoemax=f[7],expl=f[8],dots=f[9],range=int(f[10]),speed=float(f[11]),radius=float(f[12]),pierce=int(f[13]),fx=f[14])
        elif f[0]=='T':
            traps[int(f[1])]=dict(id=int(f[1]),name=f[2],damage=int(f[3]),expl=f[4],fx=f[5] if len(f)>5 else '')
    return chars,projs,traps

def load(path):
    """rows: dict(match, map, slot, char, role, k, d, place, how{})"""
    rows=[]
    for line in open(path):
        f=line.rstrip('\n').split(',',9)
        how={}
        if len(f)>9 and f[9]:
            for kv in f[9].split(';'):
                i=kv.rfind('=')
                how[kv[:i]]=int(kv[i+1:])
        rows.append(dict(match=int(f[0]),map=int(f[2]),slot=int(f[3]),char=int(f[4]),role=f[5],k=int(f[6]),d=int(f[7]),place=int(f[8]),how=how))
    return rows

def per_char(rows):
    """char -> dict(n, kd mean, se, win, k, d, how totals)"""
    by=collections.defaultdict(list)
    for r in rows: by[r['char']].append(r)
    out={}
    for c,rs in by.items():
        n=len(rs); kd=[r['k']-r['d'] for r in rs]
        m=sum(kd)/n
        var=sum((x-m)**2 for x in kd)/max(1,n-1)
        how=collections.Counter()
        for r in rs:
            for w,x in r['how'].items(): how[w]+=x
        out[c]=dict(n=n,kd=m,se=math.sqrt(var/n),sd=math.sqrt(var),win=sum(1 for r in rs if r['place']==1)/n,k=sum(r['k'] for r in rs)/n,d=sum(r['d'] for r in rs)/n,how=how,role=rs[0]['role'])
    return out
