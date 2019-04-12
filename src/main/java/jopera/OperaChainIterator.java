package jopera;

/**
 * OperaChainIterator is used to iterate over blockchain blocks
 */
public class OperaChainIterator {
	byte[] currentHash;
	Store store;

	public OperaChainIterator(byte[] currentHash, Store store) {
		super();
		this.currentHash = currentHash;
		this.store = store;
	}

	/**
	 * NextSelf returns next self parent block starting from the tip
	 */
	public void NextSelf() {
		Block block = getBlock();
		currentHash = block.PrevSelfHash;
	}

	/**
	 * NextOther returns next other parent block starting from the tip
	 */
	public void NextOther() {
		Block block = getBlock();
		currentHash = block.PrevOtherHash;
	}

	/**
	 * Show represent Current Block
	 *
	 * @return
	 */
	public Block Show() {
		return getBlock();
	}

	public Block getBlock() {
		byte[] encodedBlock = store.getBlock(currentHash);
		if (encodedBlock == null) {
			return null;
		}
		Block block = Block.DeserializeBlock(encodedBlock);
		return block;
	}
}
