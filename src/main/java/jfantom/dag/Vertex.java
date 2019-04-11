package jfantom.dag;

import java.util.HashMap;
import java.util.Map;

/**
 * Vertex is imaginary event block
 * @author qn
 */
public class Vertex {
	public boolean Root;
	public boolean Clotho;
	public boolean Atropos;
	public long AtroposTime;
	public long Timestamp;
	public String Signature;
	public Vertex PrevSelf;
	public Vertex PrevOther;
	public int Frame;
	public Map<String,Integer> FlagTable;
	public byte[] Hash;
	public Map<String,Integer> RootTable;


	/**
	 * Constructor
	 */
	public Vertex() {
		FlagTable = new HashMap<String,Integer>();
	}
}

