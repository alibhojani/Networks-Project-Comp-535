package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;
import socs.network.message.SOSPFPacket;
import socs.network.util.Configuration;

import javax.net.SocketFactory;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;

public class Router {
	public static final int INITIAL_PORT = 50000;
	public static final int HEARTBEAT_INT = 5000; // ms
	public static final int HEARTBEAT_MAX = 10000; // ms
	protected LinkStateDatabase lsd;
	RouterDescription rd = new RouterDescription();
	Link[] ports = new Link[4]; // links (4 links)
	long[] portsHeartbeat = new long[4]; // last heard from router
	ServerSocket listenSocket = null;
	private boolean didRunStart = false;

	public Router(Configuration config) {
		String ip;
		try {
			ip = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			System.out.println("IP address not assigned to Router due to the following error: " + e.toString());
			ip = "";
		}
		rd.processIPAddress = ip;
		rd.simulatedIPAddress = config.getString("socs.network.router.ip");
		rd.processPortNumber = assignPort();
		System.out.println(ip + ' ' + rd.processPortNumber);
		lsd = new LinkStateDatabase(rd);
		heartbeat();
		new Thread(new Runnable() {
			public void run() {
				Socket clientSocket = null;
				while (clientSocket == null) {
					try {
						clientSocket = listenSocket.accept();
						ObjectInputStream ois = new ObjectInputStream(clientSocket.getInputStream());
						SOSPFPacket packet;
						try {
							while ((packet = (SOSPFPacket) ois.readObject()) != null) {
								processReceivedMessage(packet);
							}
						} catch (ClassNotFoundException e) {
							e.printStackTrace();
						} catch (EOFException eof) {
							// read last object
						} finally {
							ois.close();
							clientSocket.close();
						}
					} catch (IOException e) {
						e.printStackTrace();
					} finally {
						clientSocket = null;
					}
				}
			}
		}).start();
	}

	private int assignPort() {
		int port = INITIAL_PORT;
		while (listenSocket == null) {
			try {
				listenSocket = new ServerSocket(port);
			} catch (IOException e) {
				port++;
			}
		}
		return port;
	}

	private void heartbeat() {
		synchronized (ports) {
			for (short i = 0; i < ports.length; i++) {
				if (ports[i] != null && (System.currentTimeMillis() - portsHeartbeat[i]) > HEARTBEAT_MAX) {
					handleDisconnect(i);
				}
			}
		}
		sendHeartbeat();
		new Timer().schedule(new TimerTask() {
			@Override
			public void run() {
				heartbeat();
			}
		}, HEARTBEAT_INT);
	}

	private void sendHeartbeat() {
		synchronized (ports) {
			for (short i = 0; i < ports.length; i++) {
				if (ports[i] != null) {
					Link link = ports[i];
					// send "heartbeat" message
					String remoteIP = link.router2.processIPAddress;
					int remotePort = link.router2.processPortNumber;
					SOSPFPacket heartbeatMsg = new SOSPFPacket();
					heartbeatMsg.srcProcessIP = link.router1.processIPAddress;
					heartbeatMsg.srcProcessPort = link.router1.processPortNumber;
					heartbeatMsg.srcIP = link.router1.simulatedIPAddress;
					heartbeatMsg.dstIP = link.router2.simulatedIPAddress;
					heartbeatMsg.sospfType = 3;
					heartbeatMsg.neighborID = link.router1.simulatedIPAddress;
					heartbeatMsg.routerID = link.router1.simulatedIPAddress;
					sendMessage(heartbeatMsg, remoteIP, remotePort);
				}
			}
		}
	}

	private void handleReceivedHeartbeat(short portNumber) {
		portsHeartbeat[portNumber] = System.currentTimeMillis();
	}

	/**
	 * output the shortest path to the given destination ip
	 * <p/>
	 * format: source ip address -> ip address -> ... -> destination ip
	 *
	 * @param destinationIP
	 *            the ip adderss of the destination simulated router
	 */
	private void processDetect(String destinationIP) {
		String r = lsd.getShortestPath(destinationIP);
		// String toPrint = "";
		if (r != null) {
			System.out.println(r);
		} else
			System.out.println("Shortest Path Not Found");

	}

	/**
	 * disconnect with the router identified by the given destination ip address
	 * Notice: this command should trigger the synchronization of database
	 *
	 * @param portNumber
	 *            the port number which the link attaches at
	 */
	private void processDisconnect(short portNumber) {
		if (portNumber >= 0 && portNumber < ports.length && ports[portNumber] != null) {
			Link link = handleDisconnect(portNumber);

			// send "disconnect" message
			String remoteIP = link.router2.processIPAddress;
			int remotePort = link.router2.processPortNumber;
			SOSPFPacket disconnectMsg = new SOSPFPacket();
			disconnectMsg.srcProcessIP = link.router1.processIPAddress;
			disconnectMsg.srcProcessPort = link.router1.processPortNumber;
			disconnectMsg.srcIP = link.router1.simulatedIPAddress;
			disconnectMsg.dstIP = link.router2.simulatedIPAddress;
			disconnectMsg.sospfType = 2;
			disconnectMsg.neighborID = link.router1.simulatedIPAddress;
			disconnectMsg.routerID = link.router1.simulatedIPAddress;
			sendMessage(disconnectMsg, remoteIP, remotePort);
		}
	}

	/**
	 * attach the link to the remote router, which is identified by the given
	 * simulated ip; to establish the connection via socket, you need to
	 * indentify the process IP and process Port; additionally, weight is the
	 * cost to transmitting data through the link
	 * <p/>
	 * NOTE: this command should not trigger link database synchronization
	 */
	private void processAttach(String processIP, int processPort, String simulatedIP, short weight) {
		if (rd.processIPAddress.equals(processIP) && rd.processPortNumber == processPort) {
			return; // can't connect to ourself
		}
		int portFound = addRouterToPorts(processIP, processPort, simulatedIP);
		if (portFound != -1) { // search the lsd's hashmap for lsa with rd2's
								// simIP.
			LSA newLSA = new LSA();
			// LSA newLSA2 = new LSA();
			newLSA.linkStateID = rd.simulatedIPAddress;
			// newLSA2.linkStateID = simulatedIP;
			synchronized (lsd._store) {
				if (lsd._store.containsKey(rd.simulatedIPAddress)) {
					newLSA.lsaSeqNumber = lsd._store.get(rd.simulatedIPAddress).lsaSeqNumber + 1;
					newLSA.links = (LinkedList<LinkDescription>) lsd._store.get(rd.simulatedIPAddress).links.clone();
				} else
					newLSA.lsaSeqNumber = 1;
				// if (lsd._store.containsKey(simulatedIP)) {
				// newLSA2.lsaSeqNumber =
				// lsd._store.get(simulatedIP).lsaSeqNumber + 1;
				// newLSA2.links = (LinkedList<LinkDescription>)
				// lsd._store.get(simulatedIP).links.clone();
				// } else
				// newLSA2.lsaSeqNumber = 1;
				// create new LinkDescription:
				LinkDescription ld = new LinkDescription();
				ld.tosMetrics = weight;
				ld.portNum = portFound;
				ld.linkID = simulatedIP;
				// temp
				// LinkDescription ld2 = new LinkDescription();
				// ld2.tosMetrics = weight;
				// ld2.portNum = -Integer.MAX_VALUE; // means that it is holding
				// info
				// // about reverse connection
				// ld2.linkID = rd.simulatedIPAddress;
				newLSA.links.add(ld);
				// newLSA2.links.add(ld2);
				lsd._store.put(rd.simulatedIPAddress, newLSA);
				// lsd._store.put(simulatedIP, newLSA2);
			}

		}

	}

	/**
	 * broadcast Hello to neighbors
	 */
	private void processStart() {
		if (!didRunStart) {
			didRunStart = true;
			for (Link l : ports) {
				if (l == null)
					continue;
				String remoteIP = l.router2.processIPAddress;
				int remotePort = l.router2.processPortNumber;
				SOSPFPacket helloMsg = new SOSPFPacket();
				for (LinkDescription ld : lsd._store.get(rd.simulatedIPAddress).links) {
					if (ld.linkID.equals(l.router2.simulatedIPAddress)) {
						helloMsg.weight = ld.tosMetrics;
						break;
					}
				}
				helloMsg.srcProcessIP = l.router1.processIPAddress;
				helloMsg.srcProcessPort = l.router1.processPortNumber;
				helloMsg.srcIP = l.router1.simulatedIPAddress;
				helloMsg.dstIP = l.router2.simulatedIPAddress;
				helloMsg.sospfType = 0;
				helloMsg.neighborID = l.router1.simulatedIPAddress;
				helloMsg.routerID = l.router1.simulatedIPAddress;
				sendMessage(helloMsg, remoteIP, remotePort);
			}
			sendLSAUpdate(null);
		}
	}

	private int addRouterToPorts(String processIP, int processPort, String simulatedIP) {
		RouterDescription rd2 = new RouterDescription();
		rd2.simulatedIPAddress = simulatedIP;
		rd2.processPortNumber = processPort;
		rd2.processIPAddress = processIP;
		rd2.status = RouterStatus.INIT;

		Link l = new Link(rd, rd2);

		int portFound = -1;

		if (rd.processIPAddress.equals(rd2.processIPAddress) && rd.processPortNumber == rd2.processPortNumber) {
			return -1; // cycle
		}

		synchronized (ports) {
			for (int i = 0; i < 4; i++) {
				if (ports[i] != null && ports[i].equals(l)) {
					return -1; // router already in array
				}
			}
			for (int i = 0; i < 4; i++) {
				if (ports[i] == null) {
					ports[i] = l;
					portsHeartbeat[i] = System.currentTimeMillis();
					portFound = i;
					break;
				}
			}
		}

		return portFound;
	}

	private boolean setRouterStatus(String simulatedIPAddress, RouterStatus newStatus) {
		boolean found = false;
		for (Link l : ports) {
			if (l == null)
				continue;
			if (l.router2.simulatedIPAddress.equals(simulatedIPAddress)) {
				found = true;
				l.router2.status = newStatus;
			}
		}
		return found;
	}

	private void sendMessage(final Serializable message, final String remoteIP, final int remotePort) {
		new Thread(new Runnable() {
			public void run() {
				try {
					Socket connection = new Socket(remoteIP, remotePort);
					ObjectOutputStream oos = new ObjectOutputStream(connection.getOutputStream());
					oos.writeObject(message);
					oos.close();
					connection.close();
				} catch (IOException e) {
					// e.printStackTrace();
					// System.out.println("Failed to connect to " + remoteIP +
					// ":" + remotePort);
					synchronized (ports) {
						for (short portNumber = 0; portNumber < ports.length; portNumber++) {
							Link l = ports[portNumber];
							if (l != null && l.router2.simulatedIPAddress.equals(((SOSPFPacket) message).dstIP)) {
								handleDisconnect(portNumber);
								break;
							}
						}
					}
				}
			}
		}).start();
	}

	private void processReceivedMessage(final SOSPFPacket message) {
		new Thread(new Runnable() {
			public void run() {
				if (message.sospfType == 0) {
					// System.out.println("received HELLO from
					// "+message.neighborID+";"); //TODO: message.srcIP?
					System.out.println(rd.simulatedIPAddress + " received HELLO from " + message.neighborID + ";"); // TODO:

					// HELLO message. Try setting status to TWO_WAY;
					if (!setRouterStatus(message.neighborID, RouterStatus.TWO_WAY)) {
						// router is not in ports, INIT then :)
						int result = addRouterToPorts(message.srcProcessIP, message.srcProcessPort, message.neighborID);
						if (result != -1) {
							System.out
									.println(rd.simulatedIPAddress + " set " + message.neighborID + " state to INIT;");
							LSA newLSA2 = lsd._store.get(rd.simulatedIPAddress);
							newLSA2.lsaSeqNumber += 1;
							LinkDescription ld2 = new LinkDescription();
							ld2.tosMetrics = message.weight;
							ld2.portNum = result; // means that it is holding
													// info
							// about reverse connection
							ld2.linkID = message.srcIP;
							newLSA2.links.add(ld2);
							// lsd._store.put(newLSA2);
							// good. reply now.
							SOSPFPacket helloMsg = new SOSPFPacket();
							helloMsg.srcProcessIP = rd.processIPAddress;
							helloMsg.srcProcessPort = rd.processPortNumber;
							helloMsg.srcIP = rd.simulatedIPAddress;
							helloMsg.dstIP = message.neighborID;
							helloMsg.sospfType = 0;
							helloMsg.neighborID = rd.simulatedIPAddress;
							helloMsg.routerID = message.routerID;
							sendMessage(helloMsg, message.srcProcessIP, message.srcProcessPort);
						} else {
							// no ports left
							// TODO: do something
						}
					} else {
						if (message.routerID.equals(rd.simulatedIPAddress)) {
							// good. reply now.
							SOSPFPacket helloMsg = new SOSPFPacket();
							helloMsg.srcProcessIP = rd.processIPAddress;
							helloMsg.srcProcessPort = rd.processPortNumber;
							helloMsg.srcIP = rd.simulatedIPAddress;
							helloMsg.dstIP = message.neighborID;
							helloMsg.sospfType = 0;
							helloMsg.neighborID = rd.simulatedIPAddress;
							helloMsg.routerID = message.routerID;
							sendMessage(helloMsg, message.srcProcessIP, message.srcProcessPort);
						}
						// printPorts();
						System.out.println(rd.simulatedIPAddress + " set " + message.neighborID + " state to TWO_WAY;");
						sendLSAUpdate(null);
					}
				} else if (message.sospfType == 1) {
					// LSAUpdate
					for (LSA lsa : message.lsaArray) {
						// System.out.println (lsa.linkStateID);
						// System.out.println (lsa.links);
						processLSA(lsa);
					}
					sendLSAUpdate(message.srcIP);
				} else if (message.sospfType == 2) {
					// disconnect
					synchronized (ports) {
						for (short portNumber = 0; portNumber < ports.length; portNumber++) {
							Link l = ports[portNumber];
							if (l != null && l.router2.simulatedIPAddress.equals(message.srcIP)) {
								handleDisconnect(portNumber);
								break;
							}
						}
					}
				} else if (message.sospfType == 3) {
					// heartbeat
					synchronized (ports) {
						for (short portNumber = 0; portNumber < ports.length; portNumber++) {
							Link l = ports[portNumber];
							if (l != null && l.router2.simulatedIPAddress.equals(message.srcIP)) {
								handleReceivedHeartbeat(portNumber);
								break;
							}
						}
					}
				}
			}
		}).start();
	}

	private void sendLSAUpdate(String forwardedFrom) { // forwardedFrom is null
														// if sent from start
		final SOSPFPacket p = new SOSPFPacket();
		p.srcIP = rd.simulatedIPAddress;
		p.srcProcessIP = rd.processIPAddress;
		p.srcProcessPort = rd.processPortNumber;
		p.sospfType = 1;
		p.neighborID = rd.simulatedIPAddress;
		p.lsaArray = new Vector<LSA>();
		// p.lsaArray.add (lsd._store.get (rd.simulatedIPAddress));
		synchronized (lsd._store) {
			for (LSA lsa : lsd._store.values()) {
				if (lsa != null)
					p.lsaArray.add(lsa);
			}
		}

		for (final Link l : ports) {
			if (l != null) {
				if (forwardedFrom != null) {
					if (!forwardedFrom.equals(l.router2.simulatedIPAddress)) {
						p.dstIP = l.router2.simulatedIPAddress;
						// send p through socket to all neighbours but
						// forwardedFrom
						sendMessage(p, l.router2.processIPAddress, l.router2.processPortNumber);
					}
				} else {
					p.dstIP = l.router2.simulatedIPAddress;
					// send p through socket to all
					sendMessage(p, l.router2.processIPAddress, l.router2.processPortNumber);
				}
			}
		}

	}

	private void processLSA(LSA lsa) {

		synchronized (lsd._store) {
			if (lsd._store.get(lsa.linkStateID) == null) {
				lsd._store.put(lsa.linkStateID, lsa); // add new
				// sendLSAUpdate(null);
			}

			else {
				if (lsd._store.get(lsa.linkStateID).lsaSeqNumber < lsa.lsaSeqNumber) {
					// for (int i = 0; i <
					// lsd._store.get(lsa.linkStateID).links.size(); i++){
					// if
					// (!lsa.findLinkInLSA(lsd._store.get(lsa.linkStateID).links.get(i)))
					// {
					// //System.out.println ("RAN");
					// lsa.links.add
					// (lsd._store.get(lsa.linkStateID).links.get(i));
					// }
					// }
					lsd._store.put(lsa.linkStateID, lsa); // update

				}
			}
		}
	}

	/**
	 * attach the link to the remote router, which is identified by the given
	 * simulated ip; to establish the connection via socket, you need to
	 * indentify the process IP and process Port; additionally, weight is the
	 * cost to transmitting data through the link
	 * <p/>
	 * This command does trigger the link database synchronization
	 */
	private void processConnect(String processIP, int processPort, String simulatedIP, short weight) {
		if (!didRunStart || (rd.processIPAddress.equals(processIP) && rd.processPortNumber == processPort)) {
			return; // can't connect to ourself
		}
		int portFound = addRouterToPorts(processIP, processPort, simulatedIP);
		if (portFound != -1) { // search the lsd's hashmap for lsa with rd2's
								// simIP.
			synchronized (lsd._store) {
				LSA newLSA = lsd._store.get(rd.simulatedIPAddress);
				newLSA.lsaSeqNumber += 1;
				LinkDescription ld = new LinkDescription();
				ld.tosMetrics = weight;
				ld.portNum = portFound;
				ld.linkID = simulatedIP;
				newLSA.links.add(ld);
			}
			Link l = ports[portFound];
			String remoteIP = l.router2.processIPAddress;
			int remotePort = l.router2.processPortNumber;
			SOSPFPacket helloMsg = new SOSPFPacket();
			for (LinkDescription ld : lsd._store.get(rd.simulatedIPAddress).links) {
				if (ld.linkID.equals(l.router2.simulatedIPAddress)) {
					helloMsg.weight = ld.tosMetrics;
					break;
				}
			}
			helloMsg.srcProcessIP = l.router1.processIPAddress;
			helloMsg.srcProcessPort = l.router1.processPortNumber;
			helloMsg.srcIP = l.router1.simulatedIPAddress;
			helloMsg.dstIP = l.router2.simulatedIPAddress;
			helloMsg.sospfType = 0;
			helloMsg.neighborID = l.router1.simulatedIPAddress;
			helloMsg.routerID = l.router1.simulatedIPAddress;
			sendMessage(helloMsg, remoteIP, remotePort);
			sendLSAUpdate(null);
		}

	}

	/**
	 * output the neighbors of the routers
	 */
	private void processNeighbors() {
		synchronized (lsd._store) {
			for (LSA lsa : lsd._store.values()) {
				System.out.println(lsa.linkStateID + ", " + lsa.lsaSeqNumber);
				System.out.println(lsa.links.toString());
			}
		}
	}

	/**
	 * disconnect with all neighbors and quit the program
	 */
	private void processQuit() {
		System.exit(0);
	}

	private Link handleDisconnect(short portNumber) {
		//System.out.println("LSDStore before disconnect: \n" + lsd);
		Link link = ports[portNumber];
		// remove link from port
		ports[portNumber] = null;
		// remove router's LSAs
		synchronized (lsd._store) {
			// lsd._store.remove(link.router2.simulatedIPAddress); - aging will
			// do it
			LSA l = lsd._store.get(rd.simulatedIPAddress);
			for (LinkDescription ld : l.links) {
				if (ld.linkID.equals(link.router2.simulatedIPAddress)) {
					l.links.remove(ld);
					l.lsaSeqNumber += 1;
					break;
				}
			}
			// send LSAUpdate
			sendLSAUpdate(null);
		}
		//System.out.println("LSDStore after disconnect: \n" + lsd);
		return link;
	}

	public boolean handleCommand(String command) {
		if (command.startsWith("detect ")) {
			String[] cmdLine = command.split(" ");
			processDetect(cmdLine[1]);
		} else if (command.startsWith("disconnect ")) {
			String[] cmdLine = command.split(" ");
			processDisconnect(Short.parseShort(cmdLine[1]));
		} else if (command.startsWith("quit")) {
			processQuit();
		} else if (command.startsWith("attach ")) {
			String[] cmdLine = command.split(" ");
			processAttach(cmdLine[1], Integer.parseInt(cmdLine[2]), cmdLine[3], Short.parseShort(cmdLine[4]));
		} else if (command.equals("start")) {
			processStart();
		} else if (command.startsWith("connect ")) {
			String[] cmdLine = command.split(" ");
			processConnect(cmdLine[1], Integer.parseInt(cmdLine[2]), cmdLine[3], Short.parseShort(cmdLine[4]));
		} else if (command.equals("neighbors")) {
			// output neighbors
			processNeighbors();
		} else if (command.startsWith("wait")) {
			String[] cmdLine = command.split(" ");
			System.out.println("Wating for " + cmdLine[1] + "ms ...");
			try {
				Thread.sleep(Integer.parseInt(cmdLine[1]));
			} catch (Exception e) {

			}
		} else {
			// invalid command
			System.out.println("Invalid command: " + command);
			return false;
		}
		return true;
	}

	public void terminal() {
		try {
			InputStreamReader isReader = new InputStreamReader(System.in);
			BufferedReader br = new BufferedReader(isReader);
			System.out.print(">> ");
			String command = br.readLine();
			while (handleCommand(command)) {
				System.out.print(">> ");
				command = br.readLine();
			}
			isReader.close();
			br.close();
			System.exit(0);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void processInputFile(String inputFile) {
		try {
			BufferedReader br = new BufferedReader(new FileReader(inputFile));
			String line;
			while ((line = br.readLine()) != null) {
				handleCommand(line.trim());
			}
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void printPorts() {
		for (int i = 0; i < 4; i++) {
			if (ports[i] == null)
				continue;
			String router1 = "null";
			String router2 = "null";
			if (ports[i].router1 != null) {
				router1 = "" + ports[i].router1.processIPAddress + ":" + ports[i].router1.processPortNumber;
			}
			if (ports[i].router2 != null) {
				router2 = "" + ports[i].router2.processIPAddress + ":" + ports[i].router2.processPortNumber;
			}
			System.out.println("Router 1: " + router1 + " Router2: " + router2);
		}
	}

}
