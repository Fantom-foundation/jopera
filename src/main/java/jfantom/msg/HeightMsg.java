package jfantom.msg;

import java.util.Map;

/**
 * HeightMsg is structrue for sending message of request blocks
 */
public class HeightMsg {
	public Map<String,Integer> Heights;
	public String AddrFrom;
	public HeightMsg(Map<String, Integer> heights, String addrFrom) {
		super();
		Heights = heights;
		AddrFrom = addrFrom;
	}
}

