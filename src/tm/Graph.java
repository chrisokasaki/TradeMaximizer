package tm;
import java.util.*;

public class Graph {

  static enum VertexType { RECEIVER, SENDER }

  public static class Vertex {
    String name;
    String user;
    boolean isDummy;
    VertexType type;

    Vertex(String name, String user, boolean isDummy, VertexType type) {
      this.name = name;
      this.user = user;
      this.isDummy = isDummy;
      this.type = type;
    }

    private HashMap<Vertex,Edge> edgeMap = new HashMap<Vertex,Edge>();
    private List<Edge> edgeList = new ArrayList<Edge>(); // used while building, null when frozen
    Edge[] edges; //not valid until the graph is frozen

    // internal data for graph algorithms
    private long minimumInCost = Long.MAX_VALUE; // only kept in the senders
    Vertex twin;
    private int mark = 0; // used for marking as visited in dfs and dijkstra
    Vertex match = null;
    long matchCost = 0;
    private Vertex from = null;
    private long price = 0;
    private Heap.Entry heapEntry = null;
    private int component = 0;
    boolean used = false;

    Vertex savedMatch = null;
    long savedMatchCost = 0;
  }

  public static class Edge {
    Vertex receiver;
    Vertex sender;
    long cost;

    Edge(Vertex receiver,Vertex sender,long cost) {
      assert receiver.type == VertexType.RECEIVER;
      assert sender.type == VertexType.SENDER;
      this.receiver = receiver;
      this.sender = sender;
      this.cost = cost;
    }

    private Edge() {} // hide default constructor
  }

  public Vertex getVertex(String name) {
    // returns null if name is undefined
    return nameMap.get(name);
  }

  public Vertex addVertex(String name,String user,boolean isDummy) {
    assert !frozen;
    assert getVertex(name) == null;
    Vertex receiver = new Vertex(name,user,isDummy,VertexType.RECEIVER);
    receiverList.add(receiver);
    nameMap.put(name,receiver);

    Vertex sender = new Vertex(name+" sender",user,isDummy,VertexType.SENDER);
    senderList.add(sender);
    receiver.twin = sender;
    sender.twin = receiver;

    return receiver;
  }

  public Edge addEdge(Vertex receiver,Vertex sender,long cost) {
    assert !frozen;
    Edge edge = new Edge(receiver,sender,cost);
    receiver.edgeMap.put(sender,edge);
    sender.edgeMap.put(receiver,edge);
    receiver.edgeList.add(edge);
    sender.edgeList.add(edge);
    sender.minimumInCost = Math.min(cost,sender.minimumInCost);
    return edge;
  }

  public Edge getEdge(Vertex receiver,Vertex sender) {
    return receiver.edgeMap.get(sender);
  }

  boolean frozen = false;

  void freeze() {
    assert !frozen;

    receivers = receiverList.toArray(new Vertex[0]);
    senders = senderList.toArray(new Vertex[0]);
    receiverList = null;
    senderList = null;

    Edge[] tmp = new Edge[0];
    for (Vertex v : receivers) {
      v.edges = v.edgeList.toArray(tmp);
      v.edgeList = null;
    }
    for (Vertex v : senders) {
      v.edges = v.edgeList.toArray(tmp);
      v.edgeList = null;
    }

    frozen = true;
  }

  // receiverList/senderList are only valid while building the graph, null when frozen
  // receivers/senders only valid once the graph is frozen
  List<Vertex> receiverList = new ArrayList<Vertex>();
  List<Vertex> senderList   = new ArrayList<Vertex>();
  Vertex[] receivers;
  Vertex[] senders;

  List<Vertex> orphans = new ArrayList<Vertex>();

  private HashMap<String,Vertex> nameMap = new HashMap<String,Vertex>();

  void print() {
    assert frozen;
    for (Vertex v : receivers) {
      System.out.print(v.name + " :");
      for (Edge e : v.edges) {
        if (e.sender != e.receiver.twin)
          System.out.print(" " + e.sender.name);
      }
      System.out.println();
    }
  }

  private int timestamp = 0;
  private void advanceTimestamp() { timestamp++; }
  private int component = 0;

  private List<Vertex> finished;

  void visitReceivers(Vertex receiver) {
    assert receiver.type == VertexType.RECEIVER;
    receiver.mark = timestamp;
    for (Edge edge : receiver.edges) {
      Vertex v = edge.sender.twin;
      if (v.mark != timestamp) visitReceivers(v);
    }
    finished.add(receiver.twin);
  }
  void visitSenders(Vertex sender) {
    assert sender.type == VertexType.SENDER;
    sender.mark = timestamp;
    for (Edge edge : sender.edges) {
      Vertex v = edge.receiver.twin;
      if (v.mark != timestamp) visitSenders(v);
    }
    sender.component = sender.twin.component = component;
  }

  void removeBadEdges(Vertex v) {
    int goodCount = 0;
    for (Edge edge : v.edges) {
      if (edge.receiver.component == edge.sender.component)
        v.edges[goodCount++] = edge;
    }
    v.edges = Arrays.copyOf(v.edges, goodCount);
  }

  void removeImpossibleEdges() {
    assert frozen;

    advanceTimestamp();
    finished = new ArrayList<Vertex>(receivers.length);

    // run strongly connected components and label all the components
    for (Vertex v : receivers)
      if (v.mark != timestamp) visitReceivers(v);
    Collections.reverse(finished);
    for (Vertex v : finished) {
      if (v.mark != timestamp) {
        component++;
        visitSenders(v);
      }
    }

    // now remove all edges between two different components
    for (Vertex v : receivers) removeBadEdges(v);
    for (Vertex v : senders) {
      removeBadEdges(v);

      v.minimumInCost = Long.MAX_VALUE;
      for (Edge edge : v.edges)
        v.minimumInCost = Math.min(edge.cost,v.minimumInCost);
    }

    removeOrphans();
  }

  // remove all vertices whose only edge is the self (nontrade) edge
  // MUST ONLY BE CALLED AFTER SCC, SO THAT THE SENDER AND RECEIVER OF THE ORPHAN
  // WILL **BOTH** ONLY HAVE A SINGLE EDGE
  void removeOrphans() {
    int rCount = 0;
    for (Vertex v : receivers) {
      if (v.edges.length > 1 || v.edges[0].sender != v.twin) {
        receivers[rCount++] = v;
      }
      else {
        assert v.edges.length == 1;
        orphans.add(v);
      }
    }
    if (rCount == receivers.length) return;
    receivers = Arrays.copyOf(receivers, rCount);

    int sCount = 0;
    for (Vertex v : senders) {
      if (v.edges.length > 1 || v.edges[0].receiver != v.twin) {
        senders[sCount++] = v;
      }
    }
    senders = Arrays.copyOf(senders, sCount);
    assert rCount == sCount;
  }

  //////////////////////////////////////////////////////////////////////

  Vertex sinkFrom;
  long sinkCost;

  static final long INFINITY = 100000000000000L; // 10^14

  void dijkstra() {
    sinkFrom = null;
    sinkCost = Long.MAX_VALUE;

    Heap heap = new Heap();
    for (Vertex v : senders) {
      v.from = null;
      v.heapEntry = heap.insert(v, INFINITY);
    }
    for (Vertex v : receivers) {
      v.from = null;
      long cost = v.match == null ? 0 : INFINITY;
      v.heapEntry = heap.insert(v, cost);
    }

    while (!heap.isEmpty()) {
      Heap.Entry minEntry = heap.extractMin();
      Vertex vertex = minEntry.vertex();
      long cost = minEntry.cost();
      if (cost == INFINITY) break; // everything left is unreachable
      if (vertex.type == VertexType.RECEIVER) {
        for (Edge e : vertex.edges) {
          Vertex other = e.sender;
          if (other == vertex.match) continue;
          long c = vertex.price + e.cost - other.price;
          assert c >= 0;
          if (cost + c < other.heapEntry.cost()) {
            other.heapEntry.decreaseCost(cost + c);
            other.from = vertex;
          }
        }
      }
      else if (vertex.match == null) { // unmatched sender
        if (cost < sinkCost) {
          sinkFrom = vertex;
          sinkCost = cost;
        }
      }
      else { // matched sender
        Vertex other = vertex.match;
        long c = vertex.price - other.matchCost - other.price;
        assert c >= 0;
        if (cost + c < other.heapEntry.cost()) {
          other.heapEntry.decreaseCost(cost + c);
          other.from = vertex;
        }
      }
    }
  } // end dijkstra

  void findBestMatches() {
    assert frozen;

    for (Vertex v : receivers) {
      v.match = null;
      v.price = 0;
    }
    for (Vertex v : senders) {
      v.match = null;
      v.price = v.minimumInCost;
    }

    for (int round = 0; round < receivers.length; round++) {
      dijkstra();

      // update the matching
      Vertex sender = sinkFrom;
      assert sender != null;
      while (sender != null) {
        Vertex receiver = sender.from;

        // unlink sender and receiver from current matches
        if (sender.match != null) sender.match.match = null;
        if (receiver.match != null) receiver.match.match = null;

        sender.match = receiver;
        receiver.match = sender;

        // update matchCost
        for (Edge e : receiver.edges) {
          if (e.sender == sender) {
            receiver.matchCost = e.cost;
            break;
          }
        }

        sender = receiver.from;
      }

      // update the prices
      for (Vertex v : receivers) v.price += v.heapEntry.cost();
      for (Vertex v : senders)   v.price += v.heapEntry.cost();
    }
  }

  List<List<Vertex>> findCycles() {
    findBestMatches();
    elideDummies();
    advanceTimestamp();
    List<List<Vertex>> cycles = new ArrayList<List<Vertex>>();

    for (Vertex vertex : receivers) {
      if (vertex.mark == timestamp || vertex.match == vertex.twin) continue;

      List<Vertex> cycle = new ArrayList<Vertex>();
      Vertex v = vertex;
      while (v.mark != timestamp) {
        v.mark = timestamp;
        cycle.add(v);
        v = v.match.twin;
      }
      cycles.add(cycle);
    }
    return cycles;
  } // end findCycles

  //////////////////////////////////////////////////////////////////////

  private Random random = new Random();

  void setSeed(long seed) { random.setSeed(seed); }

  <T> void shuffle(T[] a) {
    for (int i = a.length; i > 1; i--) {
      int j = random.nextInt(i);
      T tmp = a[j];
      a[j] = a[i-1];
      a[i-1] = tmp;
    }
  }

  void shuffle() {
    shuffle(receivers);
    for (Vertex v : receivers) shuffle(v.edges);
  }

  void elideDummies() {
    for (Vertex v : receivers) {
      while (v.match.isDummy && v.match != v.twin) {
        Vertex dummySender = v.match;
        Vertex nextSender = dummySender.twin.match;
        v.match = nextSender;
        nextSender.match = v;
        dummySender.match = dummySender.twin;
        dummySender.twin.match = dummySender;
      }
    }
  }

  void saveMatches() {
    for (Vertex v : receivers) {
      v.savedMatch = v.match;
      v.savedMatchCost = v.matchCost;
    }
    for (Vertex v : senders) {
      v.savedMatch = v.match;
    }
  }
  void restoreMatches() {
    for (Vertex v : receivers) {
      v.match = v.savedMatch;
      v.matchCost = v.savedMatchCost;
    }
    for (Vertex v : senders) {
      v.match = v.savedMatch;
    }
  }

} // end Graph
