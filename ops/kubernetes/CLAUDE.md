# ops/kubernetes

The game's servers on Kubernetes: one central server (logins, accounts, lobbies, matchmaking,
problem reports) and a fleet of match servers that play the matches, many to a pod
(design/networking.md, *Match servers*; server/CLAUDE.md, *cluster*), run by
[Agones](https://agones.dev). And a local cluster on kind to try it all on this machine, and a load
test that measures what one match server plays at once.

```bash
ops/kubernetes/kind-up.sh       # cluster, Agones, the image, the servers; again after a code change
GRIDGAME_AUDIO=off bazel run //src/test/scala/com/gridgame/e2e:cluster_smoke -- 127.0.0.1 25570 4
ops/kubernetes/kind-down.sh     # all of it gone, accounts too
ops/kubernetes/loadtest.sh /tmp/lt --pin=6-7 --others=0-5 --hog=2 -- --stages=8,16,24   # below
```

Then log in at `127.0.0.1:25570` with the client (its login screen asks for the host and port). The
first time, the client pins the cluster's central server as it would any server; a second cluster
made later at the same address shows another key, and the client says so ("Trust the new
identity"). The match servers' keys are never pinned: the central server names each to its players.

The servers' logs: `kubectl -n gridgame logs deploy/central -f`, and a match server's
`kubectl -n gridgame logs <pod> -c match-server -f` (`kubectl -n gridgame get gameservers` names
them), or every one at once: `kubectl -n gridgame logs -l agones.dev/role=gameserver -c match-server
-f --prefix` (its pods carry no fleet label). A match server says every 30s,
while it plays anything, how many matches and players it has, how late their ticks ran, and the CPU,
GC and heap it used (its status line).

## What is here

| File | What it is |
|---|---|
| `Dockerfile` | one image for both servers (`gridgame:dev`): the two deploy jars and the worlds, on Temurin 21's JRE, as uid 10001 |
| `build.sh` | builds the deploy jars with Bazel and the image from them, for this machine's processor |
| `base/` | the servers, for any cluster with Agones in it (`kubectl apply -k base`, the secret made first: below) |
| `overlays/kind/` | the same reached on this machine's localhost |
| `kind-cluster.yaml` | the local cluster: one node, its ports on 127.0.0.1 only |
| `kind-up.sh`, `kind-down.sh` | the local cluster up (idempotent: run it again to roll a new build out) and down |
| `loadtest.sh`, `loadtest_report.py` | what one match server plays at once, measured (*How many matches a match server plays*) |

## How it is laid out

- **Namespace `gridgame`.** Agones is told to run GameServers there (`gameservers.namespaces`).
- **The central server: a Deployment of one** (`strategy: Recreate`: never two at once). Its lobbies
  are in its memory and its accounts in SQLite, so it can't be more than one; its working directory
  is a volume (`central-data`) holding the account database, its TLS identity (which players' clients
  pin: losing it makes every client warn that the server changed) and the problem reports. It listens
  for players on 25565 (TCP and UDP) and for match servers on 25575 (`--control-port`), which only
  the `central-control` ClusterIP Service exposes. Players reach it through the `central`
  LoadBalancer Service, with `externalTrafficPolicy: Local` so their own addresses reach it: its
  per-address limits and its check that a player's datagrams come from where their TCP did need them.
- **The match servers: an Agones Fleet** (`match-servers`) of pods that live on, each playing as
  many matches at once as its `matches` counter's capacity: 16 slots, an eight-player match being one
  (`MatchSpec.slotsFor`: bigger matches take more; *How many matches a match server plays*). Each
  dials `central-control` and registers with its capacity (read from the counter), and tells Agones
  it is Ready. The central server packs matches onto the fullest with room. A match server playing
  anything is Allocated, which a fleet scaling down leaves be, and keeps its counter at the slots it
  has taken; playing nothing, it is Ready again, and may be taken away. Agones gives each a port
  (`portPolicy: Dynamic`, `protocol: TCPUDP`: TCP and UDP on one number) on its node's address, which
  is what it tells the central server its players are to use. `GRIDGAME_PUBLIC_HOST` /
  `GRIDGAME_PUBLIC_PORT` say otherwise (the kind overlay says 127.0.0.1).
- **The autoscaler keeps 8 slots free** across the fleet (`type: Counter` on `matches`: one pod at
  least, ten at most, looked at every 10s): a lobby starting never waits for a pod to start. Played
  with ten matches at once on kind, one pod took all ten and the autoscaler added a second as only six
  slots were left; once they ended the first was Ready again and the fleet went back to one.
- **A match server deleted drains** (the fleet scaling down or rolled onto a new image, a node
  drained, `kubectl delete gameserver`): it takes no new match and plays its own to their end
  (`GRIDGAME_DRAIN_SECONDS`, 30 minutes, a match's longest; `terminationGracePeriodSeconds` 30s more),
  then stops. Deleted with a match under way on kind, its pod stayed Terminating for the minute the
  match had left, the match played to its scoreboard, and the fleet started another in its place.
- **Its pod: 500m of CPU and 512Mi requested, 1Gi of memory at most, no CPU limit** (a limit's
  throttling stalls every match on the pod at once), and a JVM held to G1, to threads for two cores
  (`-XX:ActiveProcessorCount=2`: with no limit it would size them for every core of the node) and to
  a heap of half the pod's memory.
- **The cluster's secret** (`gridgame-cluster`, key `secret`), which both prove they hold on the
  control link: made by `kind-up.sh` once (`openssl rand -hex 32`) and never in a manifest. Anywhere
  else, make it first: `kubectl -n gridgame create secret generic gridgame-cluster
  --from-literal=secret="$(openssl rand -hex 32)"`.
- Telemetry is off (`OTEL_SDK_DISABLED`): there is no collector in the cluster yet
  (ops/observability is the local one).

## How many matches a match server plays

**Sixteen slots on a pod requesting half a core and 512Mi, with no CPU limit.** Measured 2026-11-08
with `loadtest.sh`: the central server, one match server and a driver of real clients
(`e2e:load_driver`) as containers on one Docker network (Linux, cgroups as a pod has them, the
kernel's own networking, one clock), on this machine (an M4, 8 cores, Docker's VM given all of
them). The driver adds matches stage by stage, every player walking nonstop and firing every
0.6-1s (busier than any real match), and holds each stage a minute once its matches' openings are
over. What the match server's status lines said in that minute: how late its ticks ran against when
each was due (`TickStats`), its CPU and GC; and Docker's count of its memory.

| Match server | Matches of 8 | CPU used | Ticks late: p99 / worst | Memory |
|---|---|---|---|---|
| 4 cores to itself | 16 / 24 / 40 | 0.51 / 0.61 / 0.77 cores | ≤2ms / 7-10ms | 310-343MiB |
| a full node's share of 1 core | 8 / 16 | 0.20 / 0.25 | ≤5ms / 10-14ms | 180-190MiB |
| a full node's share of 1 core | 24 / 32 / 40 | 0.43 / 0.58 / 0.62 | ≤5-50ms / 13-57ms | 210-250MiB |
| a full node's share of ½ core | 8 / 16 / 20 | 0.11 / 0.25-0.29 / 0.31 | ≤5-50ms / 14-87ms | 170-180MiB |
| a hard limit of ½ core | 8 / 16 / 24 | 0.34 / 0.43 / 0.50 | >100ms / 180-490ms | 150-180MiB |

"A full node's share" is the match server pinned to CPUs it shares with busy loops of the same
weight (`--pin`, `--hog`): a node whose every core is taken, where a pod gets what it requests and no
more. And the machine's own floor: Docker's VM delivers timers late (a 30ms timer in an idle
container: 43% within 2ms, the worst 11ms), so a tick a few milliseconds late here is the VM, not the
server; a cloud node's timers are tighter.

What it says:
- **A busy match of eight costs 0.011-0.018 of a core and 1.5-2.5MiB**: 0.011 added by each match from
  16 to 40 with cores to spare, about 0.015 under contention. Memory never runs out first (the JVM's
  own 150-250MiB, a live heap under 100MB at 40 matches).
- **A hard CPU limit is ruinous.** A limit is a quota every 100ms: a pod that spends it (a GC, the
  JIT, matches starting) is stopped for the rest of the 100ms, every match on it at once. Half a
  core's limit stalled ticks 180-490ms with only eight matches (0.34 of a core used), and at forty
  the server fell behind until every player was dropped. A request without a limit gets the same CPU
  on a full node, by its share, without the stops.
- **What a pod can count on is its request.** Up to about 30% of it used by busy matches, the ticks
  run as on an idle machine; at 40-60%, a worst tick of 20-90ms now and then (p99 still within 10ms
  mostly); past that it degrades. Sixteen busy slots use 0.25-0.3 of a core, about 55% of half a
  core: the worst case (every match the busiest, on a node with nothing to spare) is the occasional
  hitch, and a node of such pods, each using about half what it requests, has spare cores to burst
  into. With room to spare one pod ran forty busy matches cleanly (0.77 of a core).
- **Matches starting together are the hardest moment**: eight starting at once (their maps loaded, a
  TLS handshake a player, everything of the match sent to each joining client) stalled every match on
  a one-core share for 80-160ms. Packing sends a burst of new lobbies to one pod; sixteen slots bound
  it.
- **Bigger matches cost more, bots less**: a match of sixteen humans costs about three of eight (the
  same players in matches twice the size cost 10% more each), and one human with seven bots about
  0.7 of one. `slotsFor` counts `players × (8 + humans) / 128` rounded up: eight humans 1, sixteen 3,
  thirty-two 10 (beyond what was measured: it errs high), practice 1.

Why sixteen and no more: a pod's matches all go together when it does (a crash; an eviction past its
drain), and sixteen keeps that to about 128 players and the autoscaler's steps small, while a slot
costs 31m of CPU and 32Mi requested, against 230m and 384Mi for a match when each had a pod of its
own (its 200m and the Agones sidecar's 30m). To change it: the counter's capacity in
`base/match-servers.yaml` (the match servers read it, and the central server goes by what they
register), the pod's CPU request in proportion (0.03 of a core a slot at least), and the autoscaler's
`minCapacity`.

To measure again: `ops/kubernetes/loadtest.sh <out-dir> [--cpus=n | --pin=6-7 --others=0-5 --hog=2]
[--memory=1g] [--java="..."] [--matches=n] -- --stages=8,16,24 [--players=8] [--bots=0]
[--settle=40] [--hold=60]`. A stage named twice is held twice (the first stage's first hold is the
JVM warming up). It builds the image as `gridgame:load` and prints a row a stage
(`loadtest_report.py`): matches, players, their round trips (which take in the driver's own pauses:
a loose upper bound), the match server's CPU, ticks, GC, heap and threads, its container's CPU and
memory, and the driver's CPU, which must not be the one running out. The driver needs about 9MB a
client (3GB at 320), which holds a run on this machine to about forty busy matches of eight.

## The local cluster

kind runs the cluster's one node as a Docker container, and maps its ports to this machine's
localhost (`kind-cluster.yaml`, `listenAddress: 127.0.0.1`: not the network's): 25570 for the
central server (the pod's 25565 as a `hostPort` of the node, as there is no load balancer), and
26000-26009 for the match servers (Agones' port range is set to them: its default, 7000-8000, takes
7000, which macOS's AirPlay receiver listens on, and kind can only map ports it is told of, so ten
match servers at once at most: 160 slots). The node image is pinned (`kindest/node:v1.34.0`) and so
is Agones (1.62.0, in `kind-up.sh`), which supports that version of Kubernetes and has its
`matches` counter (Counters and Lists, on by default). The image is loaded into the node (`kind load
docker-image`), never pushed anywhere.

Every client comes into kind through Docker's NAT, from one address, which the central server's
limit of five connections a minute from an address would hold every player to together: the overlay
raises it (`GRIDGAME_CONNECTIONS_PER_MINUTE`, a server's environment, never below five). The smoke
tool plays three players a match.

After this machine restarts, Docker brings the node back but not its pods, which stay as they were
(`Unknown`): `kind-up.sh` again, or restart the central server and delete the GameServers. If
`docker build` hangs at "load metadata" for the base image, Docker's credential helper is waiting
on the macOS keychain (locked after a restart): unlock it, or build with a Docker config that has
none (`DOCKER_CONFIG=<a folder with {} in config.json>
DOCKER_HOST=unix://$HOME/.docker/run/docker.sock`).

What it was checked with (2026-11-08): `cluster_smoke` playing one match, then four at once
(each through sign-up, the lobby, the match server, a walk the others saw, a leaver gone from their
screens, a shot, and the scoreboard), and the central server's identity the same after it was
rolled; with matches many to a pod, ten at once all played on one pod, the autoscaler adding a
second and taking it away again after, and a pod deleted mid-match draining. Not yet with the app's
own client (its windows), nor on a real cloud cluster.

## Not done yet

- **A match doesn't survive either end restarting**: rolling the central server ends every match
  being played (their match servers let their players go), and the lobbies with them. A match
  server drains when its pod is deleted, but one that crashes takes all its matches with it.
- **Images go nowhere but kind**: a real cluster needs `gridgame:dev` pushed to a registry it can
  pull from, and the image's tag in `base/` set to it.
- **No monitoring here**: the servers' telemetry has no collector in the cluster; a match server's
  status line in its log is what there is.
- **Measured on a laptop's VM**: the capacity was set on this machine, whose timers and cores (four
  fast, four slow, shared by the VM as macOS sees fit) are not a cloud node's; worth measuring again
  there before trusting it to the last slot. Matches of more than sixteen humans weren't measured.
- **The central server is one process**: logins and lobbies don't scale with the match servers
  (the load test, 2026-11: a match costs about 0.015 of a core and 1.1 Mbit/s; one server process
  ran 80 at once on a laptop without reaching its limit).
