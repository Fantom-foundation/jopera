package jopera;

import java.util.concurrent.ConcurrentNavigableMap;

import org.mapdb.DB;
import org.mapdb.Serializer;

public class Store {
	public static final String blocksBucket = "blocks";

	public static final String blockPrefix = "block";

	private DB Db;
	private ConcurrentNavigableMap<byte[], byte[]> blockMap;

	public Store(DB Db) {
		this.Db = Db;
		initDBMaps();
	}

	private void initDBMaps() {
		blockMap = Db.treeMap(blocksBucket, Serializer.BYTE_ARRAY, Serializer.BYTE_ARRAY).createOrOpen();

	}

	public byte[] blockKey(long index) {
		return String.format("%s_%09d", blockPrefix, index).getBytes();
	}

	public void updateBlock(byte[] key, byte[] val) {
		blockMap.put(key, val);
		Db.commit();
	}

	public byte[] getBlock(byte[] key) {
		return blockMap.get(key);
	}
}