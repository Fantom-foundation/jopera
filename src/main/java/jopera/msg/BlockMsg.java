package jopera.msg;

/**
 * BlockMsg is structrue for sending message of sending blocks
 */
public class BlockMsg {
	public String AddrFrom;
	public byte[] Block;

	public BlockMsg(String addrFrom, byte[] block) {
		super();
		AddrFrom = addrFrom;
		Block = block;
	}
}
