"""calib.py <candidate> <candidate>: what a log of damage and of health is worth in kills less deaths a
match, by regressing each character's change of score between two rosters played on the same seed
(the second with every lever moved at random: about a fifth either way) on its change of damage,
weighted by each attack's share of its kills, and of health."""
import sys, math, json
import numpy as np
from lib import *
import solve as sv
def rnd(name):
    return roster(f'{S}/cand/{name}/roster.tsv'), per_char(load(f'{S}/runs/{name}_A.csv')), per_char(load(f'{S}/runs/{name}_B.csv'))
(r0,A0,B0),(r1,A1,B1)=rnd(sys.argv[1]),rnd(sys.argv[2])
chars,projs,traps=r0
ids=sorted(chars)
for label,P0,P1 in (('A',A0,A1),('B',B0,B1)):
    W=sv.shares(chars,projs,traps,P0)
    x=[];h=[];y=[];roles=[]
    for c in ids:
        dl=sum(w*math.log(sv.lever_eff(r1,k)/sv.lever_eff(r0,k)) for k,w in W[c].items())
        x.append(dl); h.append(math.log(r1[0][c]['hp']/chars[c]['hp'])); y.append(P1[c]['kd']-P0[c]['kd']); roles.append(chars[c]['role'])
    x=np.array(x);h=np.array(h);y=np.array(y);roles=np.array(roles)
    for sel,name in ((roles!='', 'all'),(roles=='Ranged','ranged'),(roles!='Ranged','fighters')):
        X=np.column_stack([x[sel],h[sel],np.ones(sel.sum())])
        beta,res,_,_=np.linalg.lstsq(X,y[sel],rcond=None)
        pred=X.dot(beta); r=np.corrcoef(pred,y[sel])[0,1]
        resid=y[sel]-pred
        # standard errors
        cov=np.linalg.inv(X.T.dot(X))*resid.var(ddof=3)
        print(f"{label} {name:9s} S_dmg {beta[0]:6.1f} ±{math.sqrt(cov[0,0]):.1f}  S_hp {beta[1]:6.1f} ±{math.sqrt(cov[1,1]):.1f}  r={r:.2f} resid sd {resid.std():.2f}  (dx sd {x[sel].std():.3f}, dh sd {h[sel].std():.3f})")
