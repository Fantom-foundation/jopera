package jfantom;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.mapdb.DB;
import org.mapdb.DBMaker;

import jfantom.dag.Graph;
import jfantom.dag.Vertex;
import jfantom.msg.BlocksMsg;
import jfantom.msg.HeightMsg;
import jfantom.util.Appender;
import jfantom.util.Common;
import jfantom.util.ExecService;
import jfantom.util.NetConn;
import jfantom.util.NetUtils;
import jfantom.util.RResult;

public class OperaChain {

	static final String dbFileFmt = "Operachain_%s.db";
	static final String blocksBucket = "blocks";

	static final byte[] GenesisBlock = "l".getBytes();

	DB Db;
	ReadWriteLock MakeMutex;
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

	public OperaChain(DB db, String myAddress, byte[] myTip, String myName) {
		super();
		Db = db;
		MyAddress = myAddress;
		MyTip = myTip;
		MyName = myName;

		MakeMutex = new ReentrantReadWriteLock();
		KnownHeight = new HashMap<String, Integer>();
		KnownTips = new HashMap<String, byte[]>();
		SendConn = new HashMap<String, SocketChannel>();

		store = new Store(Db);
	}

	public OperaChain(DB db, ReadWriteLock makeMutex, String myAddress, String[] knownAddress,
			Map<String, Integer> knownHeight, byte[] myTip, Map<String, byte[]> knownTips,
			Map<String, SocketChannel> sendConn, String myName) {
		super();
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
		String dbFile = String.format(dbFileFmt, name);

		OperaChain oc;
		if (!Utils.fileExist(dbFile)) {
			System.out.println("No existing operachain found. Create one first.");
			oc = CreateOperachain(name);
			oc.UpdateAddress();
			return oc;
		}

		DB db = DBMaker.fileDB(new File(dbFile)).transactionEnable().closeOnJvmShutdown().fileChannelEnable().make();
		Store store = new Store(db);

		String address = String.format("localhost:%s", name);

		byte[] tip = store.getBlock(GenesisBlock);

		// genesis block create
		oc = new OperaChain(db, address, tip, name);
		oc.UpdateAddress();
		oc.UpdateState();

		return oc;
	}

	// CreateOperachain creates a new blockchain DB
	public static OperaChain CreateOperachain(String name) {
		String address = Utils.FindAddr(name);

		String dbFile = String.format(dbFileFmt, name);

		Block genesis = Block.NewBlock(name, null, null, 1);

		DB db = DBMaker.fileDB(new File(dbFile)).transactionEnable().closeOnJvmShutdown().fileChannelEnable().make();
		Store store = new Store(db);
		store.updateBlock(genesis.Hash, genesis.Serialize());
		store.updateBlock(GenesisBlock, genesis.Hash);
		byte[] tip = genesis.Hash;
		db.commit();

		Map<String, Integer> heights = new HashMap<String, Integer>();
		Map<String, byte[]> tips = new HashMap<String, byte[]>();
		heights.put(name, genesis.Height);
		tips.put(name, genesis.Hash);

		OperaChain oc = new OperaChain(db, new ReentrantReadWriteLock(), address, new String[] {}, heights, tip, tips,
				new HashMap<String, SocketChannel>(), name);

		return oc;
	}

	// UpdateAddress initializes IP
	public void UpdateAddress() {
		for (String node : Main.DNS_ADDRESSES) {
			if (!node.equals(MyAddress)) {
				if (!nodeIsKnown(node)) {
					KnownAddress = Appender.append(KnownAddress, node);
				}
			} else {
				KnownAddress = Appender.append(KnownAddress, node);

				byte[] tip = store.getBlock(GenesisBlock);
				byte[] tipData = store.getBlock(tip);
				Block tipBlock = Block.DeserializeBlock(tipData);

				String nodeName = Utils.FindName(node);
				KnownTips.put(nodeName, tip);
				KnownHeight.put(nodeName, tipBlock.Height);
			}
		}
	}

	// UpdateState initializes state of Operachain
	public void UpdateState() {
		HashMap<String, Boolean> chk = new HashMap<String, Boolean>();
		String[] mylist = new String[] { new String(MyTip) };
		chk.put(new String(MyTip), true);

		while (mylist.length > 0) {
			String currentHash = mylist[mylist.length - 1];
			mylist = Appender.slice(mylist, 0, mylist.length - 1);

			OperaChainIterator oci = Iterator(currentHash.getBytes());
			Block block = oci.Show();
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

		if (block.Signature == MyName) {
			store.updateBlock(GenesisBlock, block.Hash);
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

			System.out.printf("============ Block %x ============\n", block.Hash);
			System.out.printf("Signature: %s\n", block.Signature);
			System.out.printf("Height: %d\n", block.Height);
			System.out.printf("Prev.S block: %x\n", block.PrevSelfHash);
			System.out.printf("Prev.O block: %x\n", block.PrevOtherHash);
			System.out.printf("\n\n");

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
	ServerSocketChannel ss;

	public void sendData(String addr, byte[] data) {
		SocketChannel socketChannel;
		try {
			socketChannel = SocketChannel.open();
			socketChannel.connect(new InetSocketAddress(addr, ss.socket().getLocalPort()));
			socketChannel.write(ByteBuffer.wrap(data));
			// conn.close();
		} catch (IOException e) {
			e.printStackTrace();

			System.out.printf("%s is not available\n", addr);

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
		try {
			RResult<ServerSocketChannel> bind = NetUtils.bind(MyAddress);
			if (bind.err != null) {
				System.out.println("Server channel error:" + bind.err);
			}
			ServerSocketChannel ss = bind.result;

			while (true) {
				SocketChannel conn = ss.accept();
				ExecService.go(() -> handleConnection(new NetConn(MyAddress, conn)));
			}
			// ss.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Sync is process for request other nodes blocks
	public void Sync() {
		while (true) {
			// requestVersion
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			for (String node : KnownAddress) {
				if (node != MyAddress) {
					byte[] payload = Utils.gobEncode(new HeightMsg(KnownHeight, MyAddress));
					byte[] request = Appender.append(Utils.commandToBytes("rstBlocks"), payload);
					sendData(node, request);
				}
			}
		}
	}

	public void handleConnection(NetConn netConn) {
		byte[] request;
		try {
			request = netConn.getDec().read().getBytes();

			String command = Utils.bytesToCommand(Appender.slice(request, 0, Common.CMD_LENGTH));
			// System.out.printf("Received %s command\n", command)
			switch (command) {
			case "rstBlocks":
				handleRstBlocks(request);
			case "getBlocks":
				handleGetBlocks(request);
			default:
				System.out.println("Unknown command!");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		netConn.release();
	}

	public void handleRstBlocks(byte[] request) {
		byte[] bytes = Appender.sliceFromToEnd(request, Common.CMD_LENGTH);
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
		byte[] request2 = Appender.append(Utils.commandToBytes("getBlocks"), payload2);
		sendData(payload.AddrFrom, request2);

		/*
		 * if chk { data := EndMsg{myAddress} payload2 := gobEncode(data) reqeust :=
		 * append(commandToBytes("endBlocks"), payload2...) sendData(payload.AddrFrom,
		 * reqeust) }
		 */
	}

	public void handleGetBlocks(byte[] request) {
		byte[] bytes = Appender.sliceFromToEnd(request, Common.CMD_LENGTH);
		BlocksMsg payload = Utils.gobDecode(bytes, BlocksMsg.class);

		MakeMutex.writeLock().lock();

		Block[] blocksData = payload.Blocks;
		boolean chk = false;
		for (Block block : blocksData) {
			AddBlock(block);
			chk = true;
		}

		if (chk) {
			// System.out.println("Received a new block")
			MakeBlock(Utils.FindName(payload.AddrFrom));
		}

		MakeMutex.writeLock().unlock();
	}

	// MakeBlock creates a new block
	public void MakeBlock(String name) {
		byte[] tip = store.getBlock(GenesisBlock);
		byte[] tipData = store.getBlock(tip);
		Block tipBlock = Block.DeserializeBlock(tipData);
		int newHeight = tipBlock.Height + 1;

		Block newBlock = new Block(MyName, tip, KnownTips.get(name), newHeight);
		AddBlock(newBlock);
		MyGraph.Tip = BuildGraph(newBlock.Hash, MyGraph.ChkVertex, MyGraph);
		System.out.println("create new block");
		// UpdateChk = true
	}

	/**
	 * Graph
	 */
	// NewGraph is creating graph
	public Graph NewGraph() {
		Graph newGraph = new Graph(null, new HashMap<String, Vertex>(), new HashMap<String, Vertex>(),
				new HashMap<String, Vertex>(), new HashMap<String, Vertex>(), new HashMap<String, Map<String, Long>>(),
				new Vertex[] {});

		byte[] tip = store.getBlock(GenesisBlock);
		newGraph.Tip = BuildGraph(tip, newGraph.ChkVertex, newGraph);

		return newGraph;
	}

	// BuildGraph initialize graph based on DB
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
				if (newVertex.FlagTable.size() >= Common.SUPRA_MAJOR) {
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
