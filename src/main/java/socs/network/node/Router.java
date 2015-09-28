package socs.network.node;

import socs.network.util.Configuration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
//import java.net.SocketAddress;
//import java.net.UnknownHostException;
//import java.net.ServerSocket;



public class Router {
  public static final int INITIAL_PORT = 50000;
  protected LinkStateDatabase lsd;
  RouterDescription rd = new RouterDescription();
  Link[] ports = new Link[4]; //links (4 links)


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
  }

  private int assignPort () {
      int port = INITIAL_PORT;
      ServerSocket serverSocket = null;
      while (serverSocket == null) {
          try {
              serverSocket = new ServerSocket(port);
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
  private void processAttach(String processIP, short processPort, String simulatedIP, short weight) {

     RouterDescription rd2 = new RouterDescription();
     rd2.simulatedIPAddress = simulatedIP;
     rd2.processPortNumber = processPort;
     rd2.processIPAddress = processIP;
     //TODO: rd2 router status?

     Link l = new Link(rd, rd2);

     int portFound = -1;

     for (int i = 0; i<4; i++) {
        if (ports[i] == null) {
            ports[i] = l;
            portFound = i;
            break;
        }
     }

     if (portFound < 0) System.out.println ("All links occupied, routers not attached");
     //else

  }

  /**
   * broadcast Hello to neighbors
   */
  private void processStart() {

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

  }

  /**
   * disconnect with all neighbors and quit the program
   */
  private void processQuit() {

  }

  public void terminal() {
    try {
      InputStreamReader isReader = new InputStreamReader(System.in);
      BufferedReader br = new BufferedReader(isReader);
      System.out.print(">> ");
      String command = br.readLine();
      while (true) {
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
          processAttach(cmdLine[1], Short.parseShort(cmdLine[2]),
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
        } else {
          //invalid command
          break;
        }
        System.out.print(">> ");
        command = br.readLine();
      }
      isReader.close();
      br.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
