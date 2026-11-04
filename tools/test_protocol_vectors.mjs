import assert from "node:assert/strict";

function encodeVarint(value) {
  const bytes = [];
  while (value >= 0x80) {
    bytes.push((value & 0x7f) | 0x80);
    value >>>= 7;
  }
  bytes.push(value);
  return Buffer.from(bytes);
}

function singleFrame(command, payload, sequence) {
  const tlv = Buffer.alloc(5 + payload.length);
  tlv.writeUInt16LE(command, 0);
  tlv[2] = sequence;
  tlv.writeUInt16LE(payload.length, 3);
  Buffer.from(payload).copy(tlv, 5);
  return Buffer.concat([
    Buffer.from([0xaa]),
    encodeVarint(tlv.length + 2),
    Buffer.from([0, 0]),
    tlv,
  ]);
}

assert.equal(
  singleFrame(0x0404, [1, 1, 1], 0x4a).toString("hex"),
  "aa0a000004044a0300010101",
);
assert.equal(
  singleFrame(0x0404, [1, 1, 4], 0x48).toString("hex"),
  "aa0a00000404480300010104",
);
assert.equal(
  singleFrame(0x0404, [1, 1, 2], 0x46).toString("hex"),
  "aa0a00000404460300010102",
);
assert.equal(
  singleFrame(0x010c, [1, 1], 0x47).toString("hex"),
  "aa0900000c014702000101",
);
assert.equal(
  singleFrame(0x010c, [2, 1], 0x47).toString("hex"),
  "aa0900000c014702000201",
);
assert.equal(
  singleFrame(0x0106, [], 0x70).toString("hex"),
  "aa0700000601700000",
);
assert.equal(
  singleFrame(0x012f, [1, 6, 1, 0, 0], 0x70).toString("hex"),
  "aa0c00002f017005000106010000",
);
assert.equal(
  singleFrame(0x0100, [], 0x52).toString("hex"),
  "aa0700000001520000",
);
assert.equal(
  singleFrame(0x0200, [], 0x5c).toString("hex"),
  "aa07000000025c0000",
);
assert.equal(
  singleFrame(
    0x0205,
    [9, 0xf1, 0xf2, 0xf3, 1, 2, 3, 4, 8, 0x0b],
    0x6e,
  ).toString("hex"),
  "aa11000005026e0a0009f1f2f301020304080b",
);
assert.equal(
  singleFrame(
    0x0205,
    [9, 0xf1, 0xf2, 0xf3, 1, 2, 3, 4, 8, 0x0b],
    0x6f,
  ).toString("hex"),
  "aa11000005026f0a0009f1f2f301020304080b",
);

function parseBatteryData(payload) {
  const count = payload[1] & 0xff;
  if (count < 1 || count > 4 || payload.length < 2 + count * 2) return null;
  const result = {};
  let cursor = 2;
  for (let index = 0; index < count; index++) {
    result[payload[cursor]] = {
      level: payload[cursor + 1] & 0x7f,
      charging: (payload[cursor + 1] & 0x80) !== 0,
    };
    cursor += 2;
  }
  return result;
}

function parseBattery(data) {
  for (let offset = 0; offset + 7 < data.length; offset++) {
    const command = data.readUInt16LE(offset);
    if (command !== 0x0204 && command !== 0x0617 && command !== 0x812f) {
      continue;
    }
    const length = data.readUInt16LE(offset + 3);
    if (length < 4 || offset + 5 + length > data.length) continue;
    const payload = data.subarray(offset + 5, offset + 5 + length);
    if (command !== 0x812f) return parseBatteryData(payload);
    if (payload[0] !== 0) continue;
    const commandCount = payload[1] & 0xff;
    let cursor = 2;
    for (let index = 0; index < commandCount; index++) {
      if (cursor + 4 > payload.length) break;
      const subCommand = payload.readUInt16LE(cursor);
      const subLength = payload.readUInt16LE(cursor + 2);
      cursor += 4;
      if (cursor + subLength > payload.length) break;
      if (subCommand === 0x0106) {
        return parseBatteryData(payload.subarray(cursor, cursor + subLength));
      }
      cursor += subLength;
    }
  }
  return null;
}

assert.deepEqual(
  parseBattery(
    Buffer.from(
      "aa0f00000402ff08000103016402640314",
      "hex",
    ),
  ),
  {
    1: { level: 100, charging: false },
    2: { level: 100, charging: false },
    3: { level: 20, charging: false },
  },
);
assert.deepEqual(
  parseBattery(
    singleFrame(
      0x812f,
      [
        0,
        1,
        6,
        1,
        8,
        0,
        0,
        3,
        1,
        100,
        2,
        100,
        3,
        20,
      ],
      0x70,
    ),
  ),
  {
    1: { level: 100, charging: false },
    2: { level: 100, charging: false },
    3: { level: 20, charging: false },
  },
);

function parseNoiseMode(data) {
  for (let offset = 0; offset + 8 < data.length; offset++) {
    if (data.readUInt16LE(offset) !== 0x0204) continue;
    const length = data.readUInt16LE(offset + 3);
    if (length < 4 || offset + 5 + length > data.length) continue;
    const payload = data.subarray(offset + 5, offset + 5 + length);
    if (payload[0] !== 3 || payload[1] !== 1 || payload[2] !== 1) continue;
    let mask = 0;
    for (let index = 3; index < payload.length && index < 7; index++) {
      mask |= payload[index] << ((index - 3) * 8);
    }
    if (mask === 0) continue;
    let protocolIndex = 0;
    while (((mask >>> protocolIndex) & 1) === 0) protocolIndex++;
    if (protocolIndex === 0 || protocolIndex === 3) return "off";
    if ([1, 4, 5, 6, 7, 11].includes(protocolIndex)) return "anc";
    if (protocolIndex === 2 || protocolIndex === 8) return "transparency";
  }
  return null;
}

assert.equal(
  parseNoiseMode(Buffer.from("aa0c00000402ff05000301010001", "hex")),
  "transparency",
);
assert.equal(
  parseNoiseMode(Buffer.from("aa0c00000402ff05000301011000", "hex")),
  "anc",
);
assert.equal(
  parseNoiseMode(Buffer.from("aa0c00000402ff05000301010800", "hex")),
  "off",
);
for (const payload of [[3, 1, 1, 0x20], [3, 1, 1, 0x40], [3, 1, 1, 0x80], [3, 1, 1, 0, 8]]) {
  assert.equal(parseNoiseMode(singleFrame(0x0204, payload, 0xff)), "anc");
}

function parseEarStatus(data) {
  for (let offset = 0; offset + 7 < data.length; offset++) {
    if (data.readUInt16LE(offset) !== 0x0204) continue;
    const length = data.readUInt16LE(offset + 3);
    if (length < 6 || offset + 5 + length > data.length) continue;
    const payload = data.subarray(offset + 5, offset + 5 + length);
    if (payload[0] !== 2) continue;
    const count = payload[1] & 0xff;
    if (count < 1 || count > 4 || payload.length < 2 + count * 2) continue;
    const result = {};
    let cursor = 2;
    for (let index = 0; index < count; index++) {
      const type = payload[cursor];
      const flags = payload[cursor + 1];
      result[type] = { inBox: (flags & 1) === 0, inEar: (flags & 2) !== 0 };
      cursor += 2;
    }
    if (result[1] && result[2]) {
      return result[1].inBox && result[2].inBox;
    }
  }
  return null;
}

assert.equal(
  parseEarStatus(
    Buffer.from("aa0f00000402ff08000203010402040304", "hex"),
  ),
  true,
);
assert.equal(
  parseEarStatus(
    Buffer.from("aa0f00000402ff08000203010502040304", "hex"),
  ),
  false,
);

console.log("Protocol vectors passed.");
