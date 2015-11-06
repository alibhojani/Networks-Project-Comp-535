package socs.network.node;

public class RouterDescription {
  //used to socket communication
  String processIPAddress;
  int processPortNumber;
  //used to identify the router in the simulated network space
  String simulatedIPAddress;
  //status of the router
  RouterStatus status;
  
  public boolean equals(RouterDescription r2) {
    if (processIPAddress != null && simulatedIPAddress != null && status != null &&
    r2 != null && r2.processIPAddress != null && r2.simulatedIPAddress != null &&
    r2.status != null) {
      return processIPAddress.equals(r2.processIPAddress) && 
      processPortNumber==r2.processPortNumber &&
      simulatedIPAddress.equals(r2.simulatedIPAddress) &&
      status == r2.status;
    } else {
      return false;
    }
  }
}