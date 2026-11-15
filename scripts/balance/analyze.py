"""analyze.py <roster.tsv> <A.csv> <B.csv>: each role's and each character's kills less deaths a match,
as the bots are (A), the way a player plays (B) and on the mix it is steered by."""
import sys, math
from lib import *
chars,projs,traps=roster(sys.argv[1])
A=per_char(load(sys.argv[2])); B=per_char(load(sys.argv[3]))
print("role means (K-D a match)   A      B    blend   winA   winB")
for role in ['Melee','Skirmisher','Ranged']:
    ids=[c for c in chars if chars[c]['role']==role and c in A and c in B]
    def mean(D,key): return sum(D[c][key]*D[c]['n'] for c in ids)/sum(D[c]['n'] for c in ids)
    print(f"  {role:10s} n={len(ids):3d}  {mean(A,'kd'):+6.2f} {mean(B,'kd'):+6.2f} {(2/3 if role=='Melee' else .5)*mean(A,'kd')+(1/3 if role=='Melee' else .5)*mean(B,'kd'):+6.2f}  {100*mean(A,'win'):5.1f}% {100*mean(B,'win'):5.1f}%   kills {mean(A,'k'):.1f}/{mean(B,'k'):.1f}")
rows=[]
for c in chars:
    if c in A and c in B:
        wa=2/3 if chars[c]['role']=='Melee' else 0.5
        bl=wa*A[c]['kd']+(1-wa)*B[c]['kd']; se=math.sqrt((wa*A[c]['se'])**2+((1-wa)*B[c]['se'])**2)
        rows.append((bl,c,se))
rows.sort()
print("\nchar                 role  hp   A     B    blend  se   winA winB  n")
for bl,c,se in rows:
    ch=chars[c]
    print(f"{ch['name']:20s} {ch['role'][:3]} {ch['hp']:4d} {A[c]['kd']:+6.2f} {B[c]['kd']:+6.2f} {bl:+6.2f} {se:4.2f} {100*A[c]['win']:4.0f}% {100*B[c]['win']:4.0f}% {A[c]['n']}")
for role in ['Melee','Skirmisher','Ranged']:
    xs=[bl for bl,c,se in rows if chars[c]['role']==role]
    m=sum(xs)/len(xs); sd=math.sqrt(sum((x-m)**2 for x in xs)/(len(xs)-1))
    ses=[se for bl,c,se in rows if chars[c]['role']==role]
    print(f"{role}: spread of blend sd {sd:.2f}  (noise alone {math.sqrt(sum(s*s for s in ses)/len(ses)):.2f})")
