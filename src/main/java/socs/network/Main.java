package socs.network;

import socs.network.node.Router;
import socs.network.util.Configuration;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class Main {

  public static void main(String[] args) {

    if (args.length == 1) {
      Router r = new Router(new Configuration(args[0]));
      r.terminal();
    } else if (args.length == 2) {
      Router r = new Router(new Configuration(args[0]));
      r.processInputFile(args[1]);
    } else {
      System.out.println("usage: program conf_path input_file");
      System.exit(1);
    }
  }
}
