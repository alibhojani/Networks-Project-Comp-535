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
  protected LinkStateDatabase lsd;
  RouterDescription rd = new RouterDescription();
  Link[] ports = new Link[4]; //links (4 links)
    ServerSocket listenSocket = null;


  public Router(Configuration config) {
    String ip;
    try {
      ip = InetAddress.getLocalHost().getHostAddress();
    }
    catch (UnknownHostException e) {
      System.out.println ("IP address not assigned to Router due to the following error: " + e.toString());
      ip = "";
    }
    rd.processIPAddress = ip;
    rd.simulatedIPAddress = config.getString("socs.network.router.ip");
    rd.processPortNumber = assignPort();
      System.out.println (ip+' '+rd.processPortNumber);
    lsd = new LinkStateDatabase(rd);
       new Thread(new Runnable() {
              public void run() {
                  Socket clientSocket = null;
                  while (clientSocket==null) {
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

  private int assignPort () {
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

  /**
   * output the shortest path to the given destination ip
   * <p/>
   * format: source ip address  -> ip address -> ... -> destination ip
   *
   * @param destinationIP the ip adderss of the destination simulated router
   */
  private void processDetect(String destinationIP) {
        LinkedList<String> r = lsd.getShortestPath(destinationIP);
        String toPrint = "";
        if (r!= null) {
            int weight = -1;
            for (int i =0; i < r.size(); i++){
                if (i!=0) {

                    LinkedList<LinkDescription> l = lsd._store.get(r.get(i-1)).links;
                    for (int j = 0; j < l.size(); j++){
                        if (l.get(j).linkID.equals(r.get(i))) weight = l.get(j).tosMetrics; break;
                    }
                    toPrint = toPrint + " -> (" + weight + ")";

                }
                toPrint = toPrint + r.get(i);
            }
        System.out.println(toPrint);
        }
        else System.out.println ("Shortest Path Not Found");

  }

  /**
   * disconnect with the router identified by the given destination ip address
   * Notice: this command should trigger the synchronization of database
   *
   * @param portNumber the port number which the link attaches at
   */
  private void processDisconnect(short portNumber) {

  }

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * additionally, weight is the cost to transmitting data through the link
   * <p/>
   * NOTE: this command should not trigger link database synchronization
   */
   private void processAttach(String processIP, int processPort, String simulatedIP, short weight) {
       if (rd.processIPAddress.equals(processIP) && rd.processPortNumber == processPort) {
           return; // can't connect to ourself
       }
    int portFound = addRouterToPorts(processIP,processPort,simulatedIP);
     if (portFound!=-1) { //search the lsd's hashmap for lsa with rd2's simIP.
         LSA newLSA = new LSA();
         LSA newLSA2 = new LSA();
         newLSA.linkStateID = rd.simulatedIPAddress;
         newLSA2.linkStateID = simulatedIP;
         if (lsd._store.containsKey (rd.simulatedIPAddress)) {
             newLSA.lsaSeqNumber = lsd._store.get(rd.simulatedIPAddress).lsaSeqNumber +1;
             newLSA.links = (LinkedList<LinkDescription>) lsd._store.get(rd.simulatedIPAddress).links.clone();
         }
         if (lsd._store.containsKey (simulatedIP)) {
             newLSA2.lsaSeqNumber = lsd._store.get(simulatedIP).lsaSeqNumber +1;
             newLSA2.links = (LinkedList<LinkDescription>) lsd._store.get(simulatedIP).links.clone();
         }
         //create new LinkDescription:
         LinkDescription ld = new LinkDescription();
         ld.tosMetrics = weight;
         ld.portNum = portFound;
         ld.linkID = simulatedIP;
         //temp
         LinkDescription ld2 = new LinkDescription();
         ld2.tosMetrics = weight;
         ld2.portNum = -Integer.MAX_VALUE; //means that it is holding info about reverse connection
         ld2.linkID = rd.simulatedIPAddress;
         newLSA.links.add(ld);
         newLSA2.links.add(ld2);
         if (lsd._store.containsKey(rd.simulatedIPAddress)) newLSA.lsaSeqNumber = lsd._store.get(rd.simulatedIPAddress).lsaSeqNumber + 1;
         else newLSA.lsaSeqNumber = 0;
         if (lsd._store.containsKey(simulatedIP)) newLSA2.lsaSeqNumber = lsd._store.get(simulatedIP).lsaSeqNumber + 1;
         else newLSA2.lsaSeqNumber = 0;
         lsd._store.put(rd.simulatedIPAddress, newLSA);
         lsd._store.put (simulatedIP, newLSA2);

     }

  }

  /**
   * broadcast Hello to neighbors
   */
  private void processStart() {
      for (Link l : ports) {
          if (l == null) continue;
          String remoteIP = l.router2.processIPAddress;
          int remotePort = l.router2.processPortNumber;
          SOSPFPacket helloMsg = new SOSPFPacket();
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
        
        for (int i=0; i<4; i++) {
            if (ports[i] != null && ports[i].equals(l)) {
                return -1;
            }
        }
        for (int i = 0; i<4; i++) {
            if (ports[i] == null) {
                ports[i] = l;
                portFound = i;
                break;
            }
        }

        return portFound;
    }

    private boolean setRouterStatus(String simulatedIPAddress, RouterStatus newStatus) {
        boolean found = false;
        for (Link l : ports) {
            if (l == null) continue;
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
                    e.printStackTrace();
                    System.out.println("Failed to connect to " + remoteIP + ":" + remotePort);
                }
            }
        }).start();
    }

    private void processReceivedMessage(final SOSPFPacket message) {
        new Thread(new Runnable() {
            public void run() {
        if (message.sospfType == 0) {
            System.out.println("received HELLO from "+message.neighborID+";"); //TODO: message.srcIP?
            // HELLO message. Try setting status to TWO_WAY;
            if (!setRouterStatus(message.neighborID, RouterStatus.TWO_WAY)) {
                // router is not in ports, INIT then :)
                int result = addRouterToPorts(message.srcProcessIP,message.srcProcessPort, message.neighborID);
                if (result != -1) {
                    System.out.println("set " + message.neighborID + " state to INIT;");
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
                //printPorts();
                System.out.println("set " + message.neighborID + " state to TWO_WAY;");
            }
        } else if (message.sospfType == 1) {
            for (LSA lsa: message.lsaArray) {
                processLSA(lsa);
            }
            sendLSAUpdate(message.srcIP);
        }
            }
        }).start();
    }

    private void sendLSAUpdate (String forwardedFrom) { //forwardedFrom is null if sent from start
        final SOSPFPacket p = new SOSPFPacket();
        p.srcIP = rd.simulatedIPAddress;
        p.srcProcessIP = rd.processIPAddress;
        p.srcProcessPort = rd.processPortNumber;
        p.sospfType = 1;
        p.neighborID = rd.simulatedIPAddress;
        p.lsaArray = new Vector<LSA>();
        //p.lsaArray.add (lsd._store.get (rd.simulatedIPAddress));
        synchronized(lsd._store) {
            for (LSA lsa : lsd._store.values()) {
                if (lsa != null) p.lsaArray.add(lsa);
            }
        }

        for (final Link l : ports) {
            if (l != null) {
                if (forwardedFrom != null) {
                    if (!forwardedFrom.equals(l.router2.simulatedIPAddress)) {
                        p.dstIP = l.router2.simulatedIPAddress;
                        //send p through socket to all neighbours but forwardedFrom
                        sendMessage(p, l.router2.processIPAddress, l.router2.processPortNumber);
                    }
                } else {
                    p.dstIP = l.router2.simulatedIPAddress;
                    //send p through socket to all
                    sendMessage(p, l.router2.processIPAddress, l.router2.processPortNumber);
                }
            }
        }


    }

    private void processLSA (LSA lsa) {

       //if (lsa.linkStateID.equals(rd.simulatedIPAddress)) lsa.lsaSeqNumber += 1;
        synchronized(lsd._store) {
            if (lsd._store.get(lsa.linkStateID) == null) {
                lsd._store.put (lsa.linkStateID, lsa); //add new
            }

            else {
                if (lsd._store.get(lsa.linkStateID).lsaSeqNumber < lsa.lsaSeqNumber) {
                    for (int i=0; i<lsd._store.get(lsa.linkStateID).links.size(); i++){
                        if (!lsa.links.contains(lsd._store.get(lsa.linkStateID).links.get(i))) lsa.links.add(lsd._store.get(lsa.linkStateID).links.get(i));
                    }
                    lsd._store.put (lsa.linkStateID, lsa); //update
                }
            }
        }
    }
  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * additionally, weight is the cost to transmitting data through the link
   * <p/>
   * This command does trigger the link database synchronization
   */
  private void processConnect(String processIP, short processPort,
                              String simulatedIP, short weight) {

  }

  /**
   * output the neighbors of the routers
   */
  private void processNeighbors() {
      synchronized(lsd._store) {
        for (LSA lsa :lsd._store.values()) {
            System.out.println (lsa.linkStateID);
            System.out.println (lsa.links.toString());
        }
      }
  }

  /**
   * disconnect with all neighbors and quit the program
   */
  private void processQuit() {

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
          processAttach(cmdLine[1], Integer.parseInt(cmdLine[2]),
                  cmdLine[3], Short.parseShort(cmdLine[4]));
        } else if (command.equals("start")) {
          processStart();
        } else if (command.equals("connect ")) {
          String[] cmdLine = command.split(" ");
          processConnect(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3], Short.parseShort(cmdLine[4]));
        } else if (command.equals("neighbors")) {
          //output neighbors
          processNeighbors();
        } else if (command.startsWith("wait")) {
          String[] cmdLine = command.split(" ");
          System.out.println("Wating for "+cmdLine[1]+"ms ...");
          try {
            Thread.sleep(Integer.parseInt(cmdLine[1]));
          } catch (Exception e) {
              
          }
        } else {
          //invalid command
          System.out.println("Invalid command: "+command);
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
        for (int i = 0; i<4; i++) {
            if (ports[i] == null) continue;
            String router1 = "null";
            String router2 = "null";
            if (ports[i].router1 != null) {
                router1 = ""+ports[i].router1.processIPAddress+":"+ports[i].router1.processPortNumber;
            }
            if (ports[i].router2 != null) {
                router2 = ""+ports[i].router2.processIPAddress+":"+ports[i].router2.processPortNumber;
            }
            System.out.println("Router 1: "+router1+" Router2: "+router2);
        }
  }

}
