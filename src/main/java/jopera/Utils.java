package jopera;

import java.io.File;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.Arrays;

import jopera.msg.BlocksMsg;
import jopera.util.JsonUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Utils {
	public static byte[] commandToBytes(String command) {
		int length = command.length();
		byte[] bytes = new byte[length];
		char c;
		for (int i = 0; i < length; ++i) {
			c = command.charAt(i);
			bytes[i] = (byte) c;
		}
		return bytes;
	}

	public static String bytesToCommand(byte[] bytes) {
		ByteBuffer buf = ByteBuffer.allocate(bytes.length);
		for (byte b : bytes) {
			if (b != 0x0) {
				buf.put(b);
			}
		}
		buf.rewind();

		String cmd = Arrays.toString(buf.array());
		return cmd;
	}

	public static byte[] gobEncode(Object data) {
		return JsonUtils.ObjectToString(data).getBytes();
	}

	public static <T> T gobDecode(byte[] bytes, Class<T> cls) {
		return (T) JsonUtils.StringToObject(new String(bytes), BlocksMsg.class);
	}

	public static boolean dbExists(String dbFile) {
		File file = Paths.get(dbFile).toFile();
		return file.exists();
	}

	// IntToHex converts an int64 to a byte array
	public static byte[] IntToHex(long num) {
		String hexString = "0x" + String.format("%040X", new BigInteger(num + "", 10));
		return hexString.getBytes();
	}

	// FindAddr find address based on name
	public static String FindAddr(String name) {
		String addr = String.format("localhost:%s", name);
		return addr;
	}

	// FindName find name based on address
	public static String FindName(String addr) {
		String name = addr.split(":")[1];
		return name;
	}

	/**
	 * File utils
	 */
	public static boolean fileExist(String filePath) {
		File file = Paths.get(filePath).toFile();
		return file.exists();
	}

	/**
	 * Hash utils
	 */
	public static byte[] SHA256(byte[] hashBytes) {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
			byte[] encodedhash = digest.digest(hashBytes);
			return encodedhash;
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return null;
	}
}
