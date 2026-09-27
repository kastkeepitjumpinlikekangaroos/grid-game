# common/protocol

The wire: every packet's layout, serialization (`PacketSerializer`) and signing (`PacketSigner`). What
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
| [55-63] | reserved, zero |

A stunned player has both 0x04 and flags2 bit 0 set: the freeze bit is what holds them, the
stun bit only says what to draw. Byte [49] bit 2 is a raised barrier, bytes [50-51] the way
it faces and [53-54] how long it has left (see *Barriers*): a client sends its aim there while
its barrier is up, and the server writes the barrier's facing and its remaining time. All are 0
otherwise.

## Packet Types (18 total)

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
| 0x0B | AUTH_REQUEST      | TCP       | Login/register                 |
| 0x0C | AUTH_RESPONSE     | TCP       | Auth result                    |
| 0x0D | MATCH_HISTORY     | TCP       | Game statistics                |
| 0x0E | RANKED_QUEUE      | TCP       | Ranked matchmaking             |
| 0x0F | LEADERBOARD       | TCP       | Rankings                       |
| 0x10 | SESSION_TOKEN     | TCP       | Session token delivery (post-auth) |
| 0x11 | CHAT_MESSAGE      | TCP       | Lobby and match chat           |
| 0x12 | TRAP_UPDATE       | TCP       | Traps placed, sprung, removed  |

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
   │═══ TLS 1.3 Handshake ═══════════│  (encrypted TCP channel)
   │                                   │
   │──── AUTH_REQUEST ────────────────>│  (login/register, no HMAC yet)
   │<─── AUTH_RESPONSE ───────────────│
   │<─── SESSION_TOKEN ───────────────│  (32-byte token for HMAC signing)
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
```

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
