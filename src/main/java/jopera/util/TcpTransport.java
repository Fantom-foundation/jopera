package jopera.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;

/**
 * TcpTransport
 */
public class TcpTransport {
	public static final error errNotAdvertisable = error.Errorf("local bind address is not advertisable");
	public static final error errNotTCP = error.Errorf("local address is not a TCP address");

	private static Logger logger = Logger.getLogger(TcpTransport.class);

	InetAddress advertise;
	ServerSocketChannel listener;
	Selector selector;

	public TcpTransport(InetAddress advertise, ServerSocketChannel listener) {
		logger.debug("init selector");
		this.advertise = advertise;
		this.listener = listener;

		try {
			selector = Selector.open();
	        listener.register(selector, SelectionKey.OP_ACCEPT);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public RResult<SocketChannel> dial(String address, Duration timeout) {
		logger.field("address", address).field("timeout", timeout.toMillis()).debug("Dial");

		SocketChannel socket;
		try {
			logger.field("listener", listener).debug("Connecting to " + address + " on port " + listener.socket().getLocalPort());
			socket = SocketChannel.open(new InetSocketAddress(address, listener.socket().getLocalPort()));
			logger.field("client socket", socket).debug("Just connected to " + socket.socket().getRemoteSocketAddress());
			socket.configureBlocking(false);
			socket.socket().setKeepAlive(true);
			socket.setOption(java.net.StandardSocketOptions.TCP_NODELAY, true);
			//socket.socket().setSoTimeout((int) timeout.toMillis());
		} catch (IOException e) {
			e.printStackTrace();
			return new RResult<>(null, error.Errorf(e.getMessage()));
		}

		logger.field("socket", socket).debug("Dial()");
		return new RResult<>(socket, null);
	}

	public RResult<SocketChannel> accept() {
		logger.debug("Accept()");
		SocketChannel client;

		try {
        	client = listener.accept();
			logger.field("accept", client).debug("Accept()");
			client.configureBlocking(false);
		} catch (IOException e) {
			return new RResult<>(null, error.Errorf(e.getMessage()));
		}
		return new RResult<>(client, null);
	}

	public Selector selector() {
		return selector;
	}

	public error close()  {
		try {
			listener.close();
			return null;
		} catch (IOException e) {
			return error.Errorf(e.getMessage());
		}
	}

	public InetAddress addr() {
		// Use an advertise addr if provided
		if (advertise != null) {
			return advertise;
		}
		return listener.socket().getInetAddress();
	}

	/**
	 * Creates a new TCPTransport
	 * @param bindAddr
	 * @param advertise
	 * @param maxPool
	 * @param timeout
	 * @param logger
	 * @return a NetworkTransport that is built on top of
	 * a TCP streaming transport layer, with log output going to the supplied Logger
	 */
	public RResult<TcpTransport> TCPTransport(String bindAddr, InetAddress advertise, int maxPool,
			Duration timeout, Logger logger) {
		// Try to bind
		RResult<ServerSocketChannel> bind = NetUtils.bind(bindAddr);
		ServerSocketChannel list = bind.result;

		error err = bind.err;
		if (err != null) {
			return new RResult<>(null, err);
		}

		// Create stream
		TcpTransport stream = new TcpTransport(advertise, list);

		// Verify that we have a usable advertise address
		InetAddress addr = stream.addr();

		boolean ok = addr != null;
		try {
			if (!ok) {
				list.close();
				return new RResult<>(null, errNotTCP);
			}
			if (addr.getHostAddress().isEmpty()) {
				list.close();
				return new RResult<>(null, errNotAdvertisable);
			}
		} catch (IOException e) {
			return new RResult<>(null, error.Errorf(e.getMessage()));
		}
		// Create the network transport
		return new RResult<>(stream, null);
	}
}