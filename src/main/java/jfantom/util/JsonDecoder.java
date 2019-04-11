package jfantom.util;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class JsonDecoder {
	private static Logger logger = Logger.getLogger(JsonDecoder.class);

	SocketChannel r;
	private static ByteBuffer buffer;

	public JsonDecoder(SocketChannel r) {
		this.r = r;
		buffer = ByteBuffer.allocate(9256);
	}

	int readInt() throws IOException {
		ByteBuffer buf = ByteBuffer.allocate(4);
		r.read(buf);
		return buf.getInt();
	}

	public RResult<Integer> readRpc() {
		int rpcType = 0;
		error err = null;
		try {
			rpcType = readInt();
			logger.field("rpcType", rpcType).debug("readRpc()");

		} catch (IOException e) {
			e.printStackTrace();
			err = error.Errorf(e.getMessage());
		}
		return new RResult<>(rpcType, err);
	}

	public <T> T decode(T resp) {
		logger.field("resp", resp).debug("decode(T) starts");

		try {
			String s = read();
			logger.field("s", s).debug("decode(T) parsed resp");
			if (s == null || s.isEmpty()) {
				return null;
			}

			T t = (T) JsonUtils.StringToObject(s, resp.getClass());
			logger.field("resp", resp).debug("decode(T) parsed resp");
			return t;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	public String read() throws IOException {
		Writer writer = new StringWriter();
		if (r.isConnected() && r.read(buffer) != -1) {
			buffer.clear();
			writer.write(new String(buffer.array()));
			buffer.compact();
		}
		writer.flush();
		writer.close();
		return writer.toString();
	}
}
