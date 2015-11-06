package socs.network.node;

public class Link {

  RouterDescription router1;
  RouterDescription router2;

  public Link(RouterDescription r1, RouterDescription r2) {
    router1 = r1;
    router2 = r2;
  }
  
  public boolean equals(Link l2) {
    if (router1 != null && router2 != null && l2 != null && l2.router1 != null && l2.router2 != null) {
      return router1.equals(l2.router1) && router2.equals(l2.router2);
    } else {
      return false;
    }
  }
}
