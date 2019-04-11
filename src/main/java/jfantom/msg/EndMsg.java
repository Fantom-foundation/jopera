package jfantom.msg;

/**
 * EndMsg is structure for sending close packet
 */
public class EndMsg {
	public String AddrFrom;

	public EndMsg(String addrFrom) {
		super();
		AddrFrom = addrFrom;
	}
}

