package jfantom;

import java.nio.ByteBuffer;

import jfantom.util.Appender;
import jfantom.util.JsonUtils;

/**
 * Block is structure of event block
 */
public class Block {
	long Timestamp;
	String Signature;
	byte[] PrevSelfHash;
	byte[] PrevOtherHash;
	byte[] Hash;
	int Height;

	// NewBlock is creation of event block
	public Block(long timestamp, String name, byte[] prevSelfHash, byte[] prevOtherHash, byte[] bs, int height) {
		Timestamp = timestamp;
		Signature = name;
		PrevSelfHash = prevSelfHash;
		PrevOtherHash = prevOtherHash;
		Hash = bs;
		Height = height;
	}

	public Block(String name, byte[] tip, byte[] bs, int newHeight) {
		Signature = name;
		Hash = bs;
		Height = newHeight;
	}

	public static Block NewBlock(String name, byte[] PrevSelfHash, byte[] PrevOtherHash, int height) {
		Block block = new Block(System.currentTimeMillis(), name, PrevSelfHash, PrevOtherHash, new byte[] {}, height);
		byte[] data = prepareData(block);
		byte[] hash = Utils.SHA256(data);
		block.Hash = Appender.sliceFromToEnd(hash, 0);
		return block;
	}

	public static byte[] prepareData(Block b) {
		ByteBuffer buff = ByteBuffer.allocate(10000);

		buff.put(b.Signature.getBytes());
		buff.put(Utils.IntToHex(b.Timestamp));
		if (b.PrevSelfHash != null) {
			buff.put(b.PrevSelfHash);
		}
		if (b.PrevOtherHash != null) {
			buff.put(b.PrevOtherHash);
		}

		return buff.array();
	}

	// Serialize serializes the block
	public byte[] Serialize() {
		String s = JsonUtils.ObjectToString(this);
		return s.getBytes();
	}

	// DeserializeBlock deserializes a block
	public static Block DeserializeBlock(byte[] d) {
		Block block = JsonUtils.StringToObject(new String(d), Block.class);
		return block;
	}
}
