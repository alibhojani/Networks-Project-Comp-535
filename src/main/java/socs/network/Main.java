package socs.network;

import socs.network.node.Router;
import socs.network.util.Configuration;

public class Main {
// github test with stefan
  public static void main(String[] args) {
    if (args.length != 1) {
      System.out.println("usage: program conf_path");
      System.exit(1);
    }
    Router r = new Router(new Configuration(args[0]));
    r.terminal();
  }
}
