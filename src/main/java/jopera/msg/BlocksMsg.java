package jopera.msg;

import jopera.Block;

/**
 * BlocksMsg is structrue for sending message of sending blocks
 */
public class BlocksMsg {
	public String AddrFrom;
	public Block[] Blocks;

	public BlocksMsg(String addrFrom, Block[] blocks) {
		super();
		AddrFrom = addrFrom;
		Blocks = blocks;
	}
}
