package jfantom.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class JsonEncoder {
	private static Logger logger = Logger.getLogger(JsonEncoder.class);
	SocketChannel w;

	public JsonEncoder(SocketChannel w) {
		this.w = w;
	}

	public int writeInt(int i) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(4).putInt(i);
		buffer.flip();
		if (w.isConnected()) {
			int wBytes = w.write(buffer);
			return wBytes;
		}
		return 0;
	}

	public error encode(Object o) {
		logger.field("o", o).debug("Encode(o) starts");
		try {
			String s = JsonUtils.ObjectToString(o);
			logger.field("s", s).debug("Encode(o) encoded result");
			if (w.isConnected()) {
				w.write(ByteBuffer.wrap(s.getBytes()));
			}
		} catch (IOException e) {
			e.printStackTrace();
			return error.Errorf("Encode(o) error=" + e.getMessage());
		}
		return null;
	}
}
