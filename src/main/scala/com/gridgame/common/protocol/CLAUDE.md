# common/protocol

The wire: every packet's layout, serialization (`PacketSerializer`) and signing (`PacketSigner`), and
how a server's TLS identity is written (`ServerIdentity`). What
the server trusts of a packet, who owns a player's position and what a session is are in
design/networking.md.

80-byte packets (64-byte payload + 16-byte HMAC-SHA256) over TLS-encrypted TCP (reliable) and HMAC-signed UDP (fast updates), using Netty. Byte order: BIG_ENDIAN.

## Packet Format

```
┌─────────────────────────────────────────────────────────────┐
│                     80 bytes on wire                        │
├──────────────────────────────────────────┬──────────────────┤
│  64-byte payload (PACKET_PAYLOAD_SIZE)   │  16-byte HMAC    │
│  [0]     Packet type ID                  │  Truncated       │
│  [1-4]   Sequence number                 │  HMAC-SHA256     │
│  [5-20]  Player UUID                     │  (or zeroed      │
│  [21-63] Type-specific data              │   if pre-auth)   │
└──────────────────────────────────────────┴──────────────────┘
```

Serialization uses `Constants.PACKET_PAYLOAD_SIZE` (64 bytes). Transport uses `Constants.PACKET_SIZE` (80 bytes). The HMAC is an outer layer — `PacketSigner.sign()` wraps a 64-byte payload into an 80-byte signed packet, and `PacketSigner.verify()` unwraps it back.

Text travels in fixed fields (a name in 21 bytes, a lobby's in 24, a chat line in 42), as UTF-8,
zero-padded, read up to the first zero. What doesn't fit is cut at the last whole character
(`SerializeUtil.putText`): cut at a byte count, a character of two or three bytes was cut in half
and read at the far end as "�". The login's username and password are the exception, still cut at
a byte count: the account database holds every password by that cut, and a username is ASCII.

Whatever bytes arrive are read as a packet or refused with an `IllegalArgumentException`, which the
handlers count as malformed and drop; nothing else may come out of `deserialize`. A packet once read
is the same packet written and read again. `PacketFuzzTest` throws hundreds of thousands of random
payloads at both, and every packet's fields round-trip in `PacketRoundTripTest`.

`PLAYER_UPDATE` is the packet with the most in it, and the one new fields keep being found for.
Its payload:

| Bytes | Field |
|---|---|
| [21-28] | position x, y |
| [29-32] | colour (ARGB) |
| [33-36] | timestamp |
| [37-40] | health |
| [41] | charge level (0-100) |
| [42] | effect flags: 0x01 shield, 0x02 gem, 0x04 frozen, 0x08 phased, 0x10 burning, 0x20 speed boost, 0x40 rooted, 0x80 slowed |
| [43] | character id |
| [44] | team id |
| [45-48] | server moves (see *Position authority*) |
| [49] | effect flags 2: bit 0 stunned, bit 1 poisoned, bit 2 barrier up, bits 3-7 free |
| [50-51] | aim angle, `angle / 2pi * 65536` (`PlayerUpdatePacket.encodeAimAngle`) |
| [52] | slow strength as a percentage (0-100): how fast the player moves while slowed |
| [53-54] | how long a raised barrier has left, in ms (server -> everyone; 0 otherwise) |
| [55-58] | knockbacks: how many of the server moves were pushes (server -> everyone; 0 from a client), so a client draws the move a new one made as a shove |
| [59-63] | reserved, zero |

A stunned player has both 0x04 and flags2 bit 0 set: the freeze bit is what holds them, the
stun bit only says what to draw. Byte [49] bit 2 is a raised barrier, bytes [50-51] the way
it faces and [53-54] how long it has left (see *Barriers*): a client sends its aim there while
its barrier is up, and the server writes the barrier's facing and its remaining time. All are 0
otherwise.

## Packet Types (20 total)

| ID   | Name              | Transport | Description                    |
|------|-------------------|-----------|--------------------------------|
| 0x01 | PLAYER_JOIN       | TCP       | Player enters game             |
| 0x02 | PLAYER_UPDATE     | UDP       | Position/health updates        |
| 0x03 | PLAYER_LEAVE      | TCP       | Player disconnects             |
| 0x04 | WORLD_INFO        | TCP       | World filename                 |
| 0x05 | HEARTBEAT         | UDP       | Keep-alive signal              |
| 0x06 | PROJECTILE_UPDATE | UDP       | Projectile movement            |
| 0x07 | ITEM_UPDATE       | TCP       | Item spawns/pickups            |
| 0x08 | TILE_UPDATE       | TCP       | Tile changes                   |
| 0x09 | LOBBY_ACTION      | TCP       | Lobby operations               |
| 0x0A | GAME_EVENT        | TCP       | Kills, the clock, the match's opening |
| 0x0B | AUTH_REQUEST      | TCP       | Login/register, a refresh login, a logout (below) |
| 0x0C | AUTH_RESPONSE     | TCP       | Auth result                    |
| 0x0D | MATCH_HISTORY     | TCP       | Game statistics                |
| 0x0E | RANKED_QUEUE      | TCP       | Ranked matchmaking             |
| 0x0F | LEADERBOARD       | TCP       | Rankings                       |
| 0x10 | SESSION_TOKEN     | TCP       | Session token delivery (post-auth, and its renewal an hour in, signed with the token it replaces) |
| 0x11 | CHAT_MESSAGE      | TCP       | Lobby and match chat           |
| 0x12 | TRAP_UPDATE       | TCP       | Traps placed, sprung, removed  |
| 0x13 | REFRESH_TOKEN     | TCP       | The login's refresh token (server -> client): [21-52] the token, [53-60] when its login runs out (epoch ms) |
| 0x14 | SESSION_END       | TCP       | Why the server is closing the session (server -> client): [21] REPLACED (logged in elsewhere) or REVOKED (logged out) |

An `AUTH_REQUEST` carries one of these `AuthAction`s. The server takes it without checking a
signature: there is no session yet, or (a LOGOUT) the token it carries is the proof. TLS is what
keeps it private.

| Action | Bytes | Meaning |
|---|---|---|
| 0 LOGIN, 1 SIGNUP | [22-41] username, [42-61] password | log in with a password, or make the account |
| 2 REFRESH | [22-53] refresh token | log in with the token an earlier login was given (design/networking.md, *Staying logged in*) |
| 3 LOGOUT | [22-53] refresh token | end the token's login: no reply, and every session of it is closed |

A `PROJECTILE_UPDATE` carries one of these `ProjectileAction`s:

| Action | Meaning |
|---|---|
| 0 SPAWN | fired (a client's request carries its `AttackSlot` in the projectile-id field) |
| 1 MOVE | where it is now |
| 2 HIT | hit targetId and was used up |
| 3 DESPAWN | stopped by terrain or its range, or an explosive going off |
| 4 PIERCE | hit targetId and flies on |
| 5 BLOCKED | stopped on targetId's barrier, at x, y |

Every projectile packet from the server carries, in its timestamp field [33-36], the projectile
tick it went out on (`ProjectilePacket.getTick`; 30ms apart, counting from 1 in each match). A
MOVE is where the projectile is at the end of its tick; a SPAWN's position holds until the tick
after the one it carries. The client flies projectiles by it (see *Flown between the server's
ticks*).

A `TRAP_UPDATE` carries one of these `TrapAction`s. The packet's own player id is the trap's
owner; its layout is in `TrapPacket`.

| Action | Meaning |
|---|---|
| 0 PLACE | a client asking to put one down (its `AttackSlot` is in byte [44]) |
| 1 SPAWN | it is on the ground here |
| 2 TRIGGER | it went off under the victim in bytes [45-60], and is gone |
| 3 REMOVE | gone without going off: it ran out, its owner left, or a newer one pushed it off |
| 4 REJECTED | the placement was refused; the placer's client gives the cooldown back |

## Connection Flow

```
Client                              Server
   │                                   │
   │═══ TLS 1.3 Handshake ═══════════│  (encrypted TCP channel; the server's key must be
   │                                   │   the one pinned for its host and port, or nothing
   │                                   │   more is sent: design/networking.md, layer 1)
   │                                   │
   │──── AUTH_REQUEST ────────────────>│  (login/register, or a refresh token; no HMAC yet)
   │<─── SESSION_TOKEN ───────────────│  (32-byte token for HMAC signing; first, so the
   │<─── REFRESH_TOKEN ───────────────│   client holds it before it acts on the answer; the
   │<─── AUTH_RESPONSE ───────────────│   refresh token after it, signed with it)
   │                                   │
   │  ── all packets HMAC-signed ──   │
   │                                   │
   │──── LOBBY_ACTION (list) ────────>│  (browse/create/join lobbies)
   │<─── LOBBY_ACTION (lobby data) ───│
   │                                   │
   │──── LOBBY_ACTION (start) ───────>│  (host starts game)
   │<─── WORLD_INFO (filename) ───────│
   │<─── PLAYER_JOIN (broadcast) ─────│  (each client takes its spawn from its own)
   │                                   │
   │──── PLAYER_UPDATE ──────────────>│  (gameplay loop, validated)
   │<─── PLAYER_UPDATE (broadcast) ───│
   │<─── PROJECTILE_UPDATE ───────────│
   │<─── ITEM_UPDATE ─────────────────│
   │<─── GAME_EVENT ──────────────────│
   │                                   │
   │──── HEARTBEAT (every 3s) ───────>│  (rate limited)
   │                                   │
   │<─── SESSION_TOKEN ───────────────│  (an hour in: the renewal, signed with the old
   │                                   │   token; the server switches on our first use of it)
   │                                   │
   │<─── SESSION_END ─────────────────│  (logged in elsewhere, or logged out: then closed)
```

A client whose connection dropped logs in again on a new one with its refresh token
(AUTH_REQUEST REFRESH): the same replies, a new refresh token among them, and its first packet
after them (the join) confirms the exchange (design/networking.md, *Staying logged in*).

## Adding a New Packet Type
1. Add a case object to `PacketType.scala` with the next unique ID, and `tcp = true/false`
2. Add it to the `all` array in `PacketType`'s `lookupTable` (and grow the table if its id is past
   the last one)
3. Create packet class extending `Packet` (use `Constants.PACKET_PAYLOAD_SIZE` for `ByteBuffer.allocate` in `serialize()`)
4. Add deserialization case in `PacketSerializer.deserialize()`
5. Handle it: on the client, a case in `GameClient.processPacket` handing it to the trait whose state
   it updates (client/game/CLAUDE.md); on the server, in `GameServer.handleIncomingPacket` (lobby,
   account and global packets) or `ClientHandler.processPacket` and its `*Requests` traits (a
   match's), judged against `PacketValidator` before anything is taken from it
6. Round-trip it in `PacketRoundTripTest`
