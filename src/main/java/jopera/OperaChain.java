package jopera;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.mapdb.DB;
import org.mapdb.DBMaker;

import jopera.dag.Graph;
import jopera.dag.Vertex;
import jopera.msg.BlocksMsg;
import jopera.msg.HeightMsg;
import jopera.util.Appender;
import jopera.util.ExecService;
import jopera.util.Logger;
import jopera.util.NetConn;
import jopera.util.NetUtils;
import jopera.util.RResult;
import jopera.util.TcpTransport;
import jopera.util.error;

public class OperaChain {

	static final String DB_FILE_FMT = "Operachain_%s.db";
	static final String BLOCKS_BUCKET = "blocks";

	static final byte[] GENESIS_BYTES = "l".getBytes();

	private static final Logger logger = Logger.getLogger(OperaChain.class);

	DB Db;
	Lock MakeMutex;
	String MyAddress;
	String[] KnownAddress;
	Map<String, Integer> KnownHeight;
	byte[] MyTip;
	Map<String, byte[]> KnownTips;
	Map<String, SocketChannel> SendConn;
	String MyName;
	Graph MyGraph;
	boolean UpdateChk;
	Store store;

	TcpTransport tcp;

	ConcurrentMap<String, Stack<NetConn>> connPool;

	public OperaChain(DB db, String myAddress, byte[] myTip, String myName) {
		super();
		connPool = new ConcurrentHashMap<String, Stack<NetConn>>();

		Db = db;
		MyAddress = myAddress;
		KnownAddress = null;
		MyTip = myTip;
		MyName = myName;

		MakeMutex = new ReentrantLock();
		KnownHeight = new HashMap<String, Integer>();
		KnownTips = new HashMap<String, byte[]>();
		SendConn = new HashMap<String, SocketChannel>();

		store = new Store(Db);
	}

	public OperaChain(DB db, Lock makeMutex, String myAddress, String[] knownAddress,
			Map<String, Integer> knownHeight, byte[] myTip, Map<String, byte[]> knownTips,
			Map<String, SocketChannel> sendConn, String myName) {
		super();

		connPool = new ConcurrentHashMap<String, Stack<NetConn>>();

		Db = db;
		MakeMutex = makeMutex;
		MyAddress = myAddress;
		KnownAddress = knownAddress;
		KnownHeight = knownHeight;
		MyTip = myTip;
		KnownTips = knownTips;
		SendConn = sendConn;
		MyName = myName;

		store = new Store(Db);
	}

	/**
	 * OpenOperachain is initialization of OperaChain
	 *
	 * @param name
	 * @return
	 */
	public static OperaChain OpenOperaChain(String name) {
		String dbFile = String.format(DB_FILE_FMT, name);

		OperaChain oc;
		if (!Utils.fileExist(dbFile)) {
			logger.debug("No existing operachain found. Create one first.");
			oc = CreateOperachain(name);
			oc.UpdateAddress();
			return oc;
		}

		DB db = DBMaker.fileDB(new File(dbFile)).transactionEnable().closeOnJvmShutdown().fileChannelEnable().make();
		//DB db = DBMaker.memoryDB().make();

		Store store = new Store(db);

		logger.debug("Store initiated");

		String address = String.format("localhost:%s", name);

		byte[] tip = store.getBlock(GENESIS_BYTES);

		logger.field("address", address).field("tip", new String(tip)).debug("creating new oc");

		// genesis block create
		oc = new OperaChain(db, address, tip, name);
		oc.UpdateAddress();
		oc.UpdateState();

		return oc;
	}

	// CreateOperachain creates a new blockchain DB
	public static OperaChain CreateOperachain(String name) {
		String address = Utils.FindAddr(name);

		String dbFile = String.format(DB_FILE_FMT, name);

		Block genesis = Block.NewBlock(name, null, null, 1);

		DB db = DBMaker.fileDB(new File(dbFile)).transactionEnable().closeOnJvmShutdown().fileChannelEnable().make();
		//DB db = DBMaker.memoryDB().make();

		Store store = new Store(db);
		store.updateBlock(genesis.Hash, genesis.Serialize());
		store.updateBlock(GENESIS_BYTES, genesis.Hash);
		byte[] tip = genesis.Hash;
		db.commit();

		Map<String, Integer> heights = new HashMap<String, Integer>();
		Map<String, byte[]> tips = new HashMap<String, byte[]>();
		heights.put(name, genesis.Height);
		tips.put(name, genesis.Hash);

		OperaChain oc = new OperaChain(db, new ReentrantLock(), address, new String[] {}, heights, tip, tips,
				new HashMap<String, SocketChannel>(), name);

		return oc;
	}

	/**
	 * UpdateAddress initializes IP
	 */
	public void UpdateAddress() {
		logger.field("dns-addresses", Arrays.asList(Main.DNS_ADDRESSES))
			.field("MyAddress", MyAddress).debug("UpdateAddress");
		for (String node : Main.DNS_ADDRESSES) {
			logger.field("node", node).debug("UpdateAddress");

			if (!node.equals(MyAddress)) {
				if (KnownAddress == null || !nodeIsKnown(node)) {
					KnownAddress = Appender.append(KnownAddress, node);
				}
			} else {
				KnownAddress = Appender.append(KnownAddress, node);

				byte[] tip = store.getBlock(GENESIS_BYTES);
				byte[] tipData = store.getBlock(tip);
				Block tipBlock = Block.DeserializeBlock(tipData);

				String nodeName = Utils.FindName(node);
				KnownTips.put(nodeName, tip);
				KnownHeight.put(nodeName, tipBlock.Height);
			}

			logger.field("KnownAddress",  Arrays.asList(KnownAddress))
				.field("KnownTips", Arrays.toString(KnownTips.entrySet().toArray()))
				.field("KnownHeight",Arrays.toString(KnownHeight.entrySet().toArray()))
				.debug("UpdateAddress");
		}
	}

	// UpdateState initializes state of Operachain
	public void UpdateState() {

		logger.field("MyTip",  MyTip).debug("UpdateState");

		HashMap<String, Boolean> chk = new HashMap<String, Boolean>();
		String[] mylist = new String[] { new String(MyTip) };
		chk.put(new String(MyTip), true);

		while (mylist.length > 0) {
			String currentHash = mylist[mylist.length - 1];
			mylist = Appender.slice(mylist, 0, mylist.length - 1);

			OperaChainIterator oci = Iterator(currentHash.getBytes());
			Block block = oci.Show();
			if (block == null) {
				break;
			}
			String blockAddr = String.format("localhost:%s", block.Signature);
			if (!nodeIsKnown(blockAddr)) {
				KnownAddress = Appender.append(KnownAddress, blockAddr);
				KnownHeight.put(block.Signature, block.Height);
				KnownTips.put(block.Signature, block.Hash);
			} else {
				if (KnownHeight.get(block.Signature) < block.Height) {
					KnownHeight.put(block.Signature, block.Height);
					KnownTips.put(block.Signature, block.Hash);
				}
			}

			if (block.PrevSelfHash != null) {
				if (!chk.get(new String(block.PrevSelfHash))) {
					mylist = Appender.append(mylist, new String(block.PrevSelfHash));
					chk.put(new String(block.PrevSelfHash), true);
				}
			}
			if (block.PrevOtherHash != null) {
				if (!chk.get(new String(block.PrevOtherHash))) {
					mylist = Appender.append(mylist, new String(block.PrevOtherHash));
					chk.put(new String(block.PrevOtherHash), true);
				}
			}
		}
	}

	// AddBlock saves the block into the blockchain
	public void AddBlock(Block block) {
		byte[] blockInDb = store.getBlock(block.Hash);
		if (blockInDb != null) {
			// error
		}

		byte[] blockData = block.Serialize();
		store.updateBlock(block.Hash, blockData);

		if (block.Signature.equals(MyName)) {
			store.updateBlock(GENESIS_BYTES, block.Hash);
			MyTip = block.Hash;
			KnownTips.put(block.Signature, block.Hash);
			KnownHeight.put(block.Signature, block.Height);
		} else {
			String blockAddr = Utils.FindAddr(block.Signature);
			if (nodeIsKnown(blockAddr)) {
				if (KnownHeight.get(block.Signature) < block.Height) {
					KnownHeight.put(block.Signature, block.Height);
					KnownTips.put(block.Signature, block.Hash);
				}
			} else {
				KnownAddress = Appender.append(KnownAddress, blockAddr);
				KnownHeight.put(block.Signature, block.Height);
				KnownTips.put(block.Signature, block.Hash);
			}
		}
	}

	public boolean nodeIsKnown(String addr) {
		logger.field("addr", addr)
		.field("KnownAddress", KnownAddress == null ? null : Arrays.asList(KnownAddress))
		.debug("nodeIsKnown()");

		for (String node : KnownAddress) {
			if (node.equals(addr)) {
				return true;
			}
		}

		return false;
	}

	// Iterator returns a OperachainIter
	public OperaChainIterator Iterator(byte[] setPtr) {
		byte[] ptr = (setPtr == null) ? MyTip : setPtr;

		OperaChainIterator oci = new OperaChainIterator(ptr, store);
		return oci;
	}

	// PrintChain initializes state of Operachain
	public void PrintChain() {
		Map<String, Boolean> chk = new HashMap<String, Boolean>();
		String[] mylist = new String[] { new String(MyTip) };
		chk.put(new String(MyTip), true);

		while (mylist.length > 0) {
			String currentHash = mylist[0];
			mylist = Appender.sliceFromToEnd(mylist, 1);

			OperaChainIterator oci = Iterator(currentHash.getBytes());
			Block block = oci.Show();

			logger.debugf("============ Block %x ============\n", block.Hash);
			logger.debugf("Signature: %s\n", block.Signature);
			logger.debugf("Height: %d\n", block.Height);
			logger.debugf("Prev.S block: %x\n", block.PrevSelfHash);
			logger.debugf("Prev.O block: %x\n", block.PrevOtherHash);
			logger.debugf("\n\n");

			if (block.PrevSelfHash != null) {
				if (!chk.get(new String(block.PrevSelfHash))) {
					mylist = Appender.append(mylist, new String(block.PrevSelfHash));
					chk.put(new String(block.PrevSelfHash), true);
				}
			}
			if (block.PrevOtherHash != null) {
				if (!chk.get(new String(block.PrevOtherHash))) {
					mylist = Appender.append(mylist, new String(block.PrevOtherHash));
					chk.put(new String(block.PrevOtherHash), true);
				}
			}
		}
	}

	/**
	 * Network Utils
	 */
	private SocketChannel lookupChannel(String addr) {
		SocketChannel socketChannel;
		socketChannel = SendConn.get(addr);
		if (socketChannel == null) {
			logger.debug("sendData() open new socketchannel");

			RResult<SocketChannel> connect = NetUtils.connect(addr);
			socketChannel = connect.result;

			SendConn.put(addr, socketChannel);
		}
		return socketChannel;
	}

	public void sendData(String addr, byte[] data) {
		logger.field("addr",  addr).field("MyAddress", MyAddress).debug("sendData()");

		try {
			SocketChannel socketChannel = lookupChannel(addr);
			socketChannel.write(ByteBuffer.wrap(data));
			logger.debug("sendData() finish writing");

			// conn.close();
		} catch (IOException e) {
			e.printStackTrace();

			logger.debugf("%s is not available\n", addr);

			// var updatedNodes []string
			// for _, node := range knownAddress {
			// if node != addr {
			// updatedNodes = append(updatedNodes, node)
			// }
			// }
			// knownAddress = updatedNodes
		}
	}

	public void receiveServer() {
		logger.field("MyAddress", MyAddress).debug("receiveServer()");

		RResult<ServerSocketChannel> bind = NetUtils.bind(MyAddress);
		if (bind.err != null) {
			logger.debug("Server channel error:" + bind.err);
		}
		tcp = new TcpTransport(null, bind.result);

		Selector selector = tcp.selector();
		try {
			while (true) {
				// Accept incoming connections
				selector.select();

	            Set<SelectionKey> selectedKeys = selector.selectedKeys();
	            Iterator<SelectionKey> iter = selectedKeys.iterator();

	            //ByteBuffer buffer = ByteBuffer.allocate(9056);
	            while (iter.hasNext()) {
	                SelectionKey key = iter.next();

	                if (key.isAcceptable()) {
	                	RResult<SocketChannel> accept = tcp.accept();
	        			SocketChannel conn = accept.result;
	        			error err = accept.err;
	        			if (err != null) {
	        				logger.field("error", err).error("Failed to accept connection");
	        				continue;
	        			}
	        			logger.field("node", conn.socket().getLocalAddress())
	        					.field("from", conn.socket().getRemoteSocketAddress())
	        					.field("conn", conn)
	        					.info("connection accepted. server socket");

	        			//ExecService.go(() -> handleConnection(new NetConn(MyAddress, conn)));
	        			handleConnection(new NetConn(MyAddress, conn));

		                iter.remove();
	                }
	            }
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		//tcp.close();
	}

	/**
	 * Sync is process for request other nodes blocks
	 */
	public void Sync() {
		Random ran = new Random();
		while (true) {
			logger.debug("Sync() loop");
			// requestVersion
			ExecService.sleep(10);

			// Peer selection to gossip
			int peerInd = ran.nextInt(KnownAddress.length);
			String peer = KnownAddress[peerInd];
			if (!peer.equals(MyAddress)) {
				byte[] payload = Utils.gobEncode(new HeightMsg(KnownHeight, MyAddress));
				byte[] request = Appender.append(Utils.commandToBytes(Constants.REQUEST_HEADER), payload);
				sendData(peer, request);
			}
		}
	}

	/**
	 * Handle a connection
	 * @param netConn
	 */
	public void handleConnection(NetConn netConn) {

		logger.debug("handleConnection()");

		byte[] request;
		try {
			request = netConn.getDec().read().getBytes();
			logger.field("request", new String(request)).debug("read request");

			if (request == null) {
				logger.field("request", new String(request)).debug("empty request !!");
				return;
			}

			String command = Utils.bytesToCommand(Appender.slice(request, 0, Constants.CMD_LENGTH));
			logger.debugf("Received %s command\n", command);
			switch (command) {
			case Constants.REQUEST_HEADER:
				handleRstBlocks(request);
			case Constants.GET_HEADER:
				handleGetBlocks(request);
			default:
				logger.debug("Unknown command!");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}  finally {
			//netConn.release();
		}
	}

	public void handleRstBlocks(byte[] request) {
		byte[] bytes = Appender.sliceFromToEnd(request, Constants.CMD_LENGTH);
		HeightMsg payload = Utils.gobDecode(bytes, HeightMsg.class);

		Block[] blocksData = null;

		for (String addr : KnownAddress) {
			String node = Utils.FindName(addr);
			if (KnownHeight.get(node) > payload.Heights.get(node)) {
				byte[] ptr = KnownTips.get(node);
				OperaChainIterator oci = Iterator(ptr);
				while (true) {
					if (oci.currentHash == null) {
						break;
					}
					Block block = oci.Show();

					if (block.Height > payload.Heights.get(node)) {
						blocksData = Appender.append(blocksData, block);
					} else {
						break;
					}

					oci.NextSelf();
				}
			}
		}

		BlocksMsg data = new BlocksMsg(MyAddress, blocksData);
		byte[] payload2 = Utils.gobEncode(data);
		byte[] request2 = Appender.append(Utils.commandToBytes(Constants.GET_HEADER), payload2);
		sendData(payload.AddrFrom, request2);

		/*
		 * if chk { data := EndMsg{myAddress} payload2 := gobEncode(data) reqeust :=
		 * append(commandToBytes("endBlocks"), payload2...) sendData(payload.AddrFrom,
		 * reqeust) }
		 */
	}

	public void handleGetBlocks(byte[] request) {
		byte[] bytes = Appender.sliceFromToEnd(request, Constants.CMD_LENGTH);
		BlocksMsg payload = Utils.gobDecode(bytes, BlocksMsg.class);

		MakeMutex.lock();

		Block[] blocksData = payload.Blocks;
		boolean chk = false;
		for (Block block : blocksData) {
			AddBlock(block);
			chk = true;
		}

		if (chk) {
			// logger.println("Received a new block")
			MakeBlock(Utils.FindName(payload.AddrFrom));
		}

		MakeMutex.unlock();
	}

	// MakeBlock creates a new block
	public void MakeBlock(String name) {
		byte[] tip = store.getBlock(GENESIS_BYTES);
		byte[] tipData = store.getBlock(tip);
		Block tipBlock = Block.DeserializeBlock(tipData);
		int newHeight = tipBlock.Height + 1;

		Block newBlock = new Block(MyName, tip, KnownTips.get(name), newHeight);
		AddBlock(newBlock);
		MyGraph.Tip = BuildGraph(newBlock.Hash, MyGraph.ChkVertex, MyGraph);
		logger.debug("create new block");
		// UpdateChk = true
	}

	/**********************
	 * Graph
	 **********/
	/**
	 * Creates a graph
	 * @return
	 */
	public Graph NewGraph() {
		Graph newGraph = new Graph(null, new HashMap<String, Vertex>(), new HashMap<String, Vertex>(),
				new HashMap<String, Vertex>(), new HashMap<String, Vertex>(), new HashMap<String, Map<String, Long>>(),
				new Vertex[] {});

		byte[] tip = store.getBlock(GENESIS_BYTES);
		newGraph.Tip = BuildGraph(tip, newGraph.ChkVertex, newGraph);

		return newGraph;
	}

	/**
	 * Builds a graph from the DB store
	 * @param hash
	 * @param rV
	 * @param g
	 * @return
	 */
	public Vertex BuildGraph(byte[] hash, Map<String, Vertex> rV, Graph g) {
		Vertex newVertex = rV.get(new String(hash));
		if (newVertex != null) {
			return newVertex;
		}
		newVertex = new Vertex();
		byte[] prevSelf, prevOther;

		byte[] encodedBlock = store.getBlock(hash);
		Block block = Block.DeserializeBlock(encodedBlock);

		newVertex.Signature = block.Signature;
		prevSelf = block.PrevSelfHash;
		prevOther = block.PrevOtherHash;
		newVertex.Hash = block.Hash;
		rV.put(new String(hash), newVertex);
		newVertex.Timestamp = block.Timestamp;

		if (prevSelf != null) {
			Vertex selfVertex = BuildGraph(prevSelf, rV, g);
			newVertex.PrevSelf = selfVertex;
		}
		if (prevOther != null) {
			Vertex otherVertex = BuildGraph(prevOther, rV, g);
			newVertex.PrevOther = otherVertex;
		}

		// Complete searching ancestor blocks of newVertex

		if (newVertex.PrevSelf != null) {
			if (newVertex.PrevSelf.Frame == newVertex.PrevOther.Frame) {
				newVertex.FlagTable = g.Merge(newVertex.PrevSelf.FlagTable, newVertex.PrevOther.FlagTable,
						newVertex.PrevSelf.Frame);
				if (newVertex.FlagTable.size() >= Constants.SUPRA_MAJOR) {
					newVertex.Root = true;
					newVertex.Frame = newVertex.PrevSelf.Frame + 1;
					newVertex.RootTable = new HashMap<String, Integer>(newVertex.FlagTable);
					newVertex.FlagTable = new HashMap<String, Integer>();
					newVertex.FlagTable.put(newVertex.Signature, newVertex.Frame);

					g.ChkClotho.put(newVertex.Frame + "_" + newVertex.Signature, newVertex);
					// Clotho check

					g.ClothoChecking(newVertex);
					g.AtroposTimeSelection(newVertex);
				} else {
					newVertex.Root = false;
					newVertex.Frame = newVertex.PrevSelf.Frame;
				}
			} else if (newVertex.PrevSelf.Frame > newVertex.PrevOther.Frame) {
				newVertex.Root = false;
				newVertex.Frame = newVertex.PrevSelf.Frame;
				newVertex.FlagTable = new HashMap<String, Integer>(newVertex.PrevSelf.FlagTable);
			} else {
				newVertex.Root = true;
				newVertex.Frame = newVertex.PrevOther.Frame;
				Vertex otherRoot = g.ChkClotho.get(newVertex.PrevOther.Frame + "_" + newVertex.PrevOther.Signature);
				newVertex.RootTable = g.Merge(newVertex.PrevSelf.FlagTable, otherRoot.RootTable, newVertex.Frame - 1);
				newVertex.FlagTable = new HashMap<String, Integer>(newVertex.PrevOther.FlagTable);
				newVertex.FlagTable.put(newVertex.Signature, newVertex.Frame);

				g.ChkClotho.put(newVertex.Frame + "_" + newVertex.Signature, newVertex);
				// Clotho check
				g.ClothoChecking(newVertex);

				g.AtroposTimeSelection(newVertex);
			}
		} else {
			newVertex.Root = true;
			newVertex.Frame = 0;
			newVertex.FlagTable.put(newVertex.Signature, newVertex.Frame);
			g.ClothoList.put(newVertex.Frame + "_" + newVertex.Signature, newVertex);
		}

		return newVertex;
	}
}
