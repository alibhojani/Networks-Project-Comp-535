package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

public class LinkStateDatabase {

  //linkID => LSAInstance
  HashMap<String, LSA> _store = new HashMap<String, LSA>();

  private RouterDescription rd = null;

  public LinkStateDatabase(RouterDescription routerDescription) {
    rd = routerDescription;
    LSA l = initLinkStateDatabase();
    _store.put(l.linkStateID, l);
  }

  /**
   * output the shortest path from this router to the destination with the given IP address
   */
  LinkedList<String> getShortestPath(String destinationIP) {
    ArrayList<String> q = new ArrayList<String>();
    HashMap<String, Integer> dist = new HashMap<String, Integer>();
    HashMap<String, String> prev = new HashMap<String, String>();

    for (LSA l : _store.values()) {
      for (LinkDescription ld : _store.get(l.linkStateID).links) {
        if (!dist.containsKey(ld.linkID)) dist.put(ld.linkID, Integer.MAX_VALUE);
        if (!prev.containsKey(ld.linkID)) prev.put(ld.linkID, null);
        if (!q.contains(ld.linkID)) q.add(ld.linkID);
      }
      if (!q.contains(l.linkStateID)) q.add(l.linkStateID);
    }

    dist.put(rd.simulatedIPAddress, 0);
    prev.put(rd.simulatedIPAddress, null);

    while (!q.isEmpty()) {
      //get node u with min dist to source
      Integer currentMin = Integer.MAX_VALUE;
      String u = null;
      for (int i = 0; i < q.size(); i++) {
        if (dist.get(q.get(i)) <= currentMin) {
          u = q.get(i);
          currentMin = dist.get(u);
        }
      }

      if (u != null) {
        if (u.equals(destinationIP)) {
          String p = destinationIP;
          LinkedList<String> toPrint = new LinkedList<String>();
            while (!p.equals(rd.simulatedIPAddress)) {
            toPrint.addFirst(p);
            p = prev.get(p);

          }

          toPrint.addFirst(p);

          return toPrint;

        }
      }
      q.remove(u);

      LSA uLSA = _store.get(u);
      for (LinkDescription ld : uLSA.links) {
        int alt = dist.get(u) + ld.tosMetrics;
        if (alt < dist.get(ld.linkID)) {
          dist.put(ld.linkID, alt);
          prev.put(ld.linkID, u);
        }
      }
    }
    return null;
  }
  //initialize the linkstate database by adding an entry about the router itself
  private LSA initLinkStateDatabase() {
    LSA lsa = new LSA();
    lsa.linkStateID = rd.simulatedIPAddress;
    lsa.lsaSeqNumber = Integer.MIN_VALUE;
    LinkDescription ld = new LinkDescription();
    ld.linkID = rd.simulatedIPAddress;
    ld.portNum = -1; //index in Link[] ports array
    ld.tosMetrics = 0; //weight of all links
    lsa.links.add(ld);
    return lsa;
  }


  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (LSA lsa: _store.values()) {
      sb.append(lsa.linkStateID).append("(" + lsa.lsaSeqNumber + ")").append(":\t");
      for (LinkDescription ld : lsa.links) {
        sb.append(ld.linkID).append(",")//.append(ld.portNum).append(","). //TODO: FIX
                .append(ld.tosMetrics).append("\t");
      }
      sb.append("\n");
    }
    return sb.toString();
  }

}
