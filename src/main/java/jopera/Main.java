package jopera;

import jopera.util.ExecService;

public class Main {

	/**
	 * DNSaddress initializes ip
	 */
	public static final String[] DNS_ADDRESSES = new String[] {
		"localhost:9001",
		"localhost:9002",
		"localhost:9003",
//		"localhost:9004",
//		"localhost:9005",
//		"localhost:9006",
//		"localhost:9007",
//		"localhost:9008",
//		"localhost:9009",
//		"localhost:9010",
//		"localhost:9011",
//		"localhost:9012",
	};

	public static void main(String[] args) {
		// Initialize
		String myName = args[0];

		// Operachain start
		OperaChain oc = OperaChain.OpenOperaChain(myName);
		oc.MyGraph = oc.NewGraph();
		switch (args[1]) {
		case "run":
			ExecService.go(() -> oc.receiveServer());

			ExecService.go(() -> oc.Sync());

			ExecService.sleep(300 * 1000); // 300 seconds
		case "print":
			oc.PrintChain();

		default:
			System.out.println("Fault command!");
		}
	}
}
