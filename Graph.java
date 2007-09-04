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

    List<Edge> edges = new ArrayList<Edge>();
    Edge[] EDGES;

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
    receivers.add(receiver);
    nameMap.put(name,receiver);
    
    Vertex sender = new Vertex(name+" sender",user,isDummy,VertexType.SENDER);
    senders.add(sender);
    receiver.twin = sender;
    sender.twin = receiver;

    return receiver;
  }

  public Edge addEdge(Vertex receiver,Vertex sender,long cost) {
    assert !frozen;
    Edge edge = new Edge(receiver,sender,cost);
    receiver.edges.add(edge);
    sender.edges.add(edge);
    sender.minimumInCost = Math.min(cost,sender.minimumInCost);
    return edge;
  }

  public Edge getEdge(Vertex receiver,Vertex sender) {
    for (Edge edge : receiver.edges) {
      if (edge.sender == sender) return edge;
    }
    return null;
  }

  boolean frozen = false;
  
  void freeze() {
    assert !frozen;
    
    RECEIVERS = receivers.toArray(new Vertex[0]);
    SENDERS = senders.toArray(new Vertex[0]);
    Edge[] tmp = new Edge[0];
    for (Vertex v : RECEIVERS) v.EDGES = v.edges.toArray(tmp);
    for (Vertex v : SENDERS) v.EDGES = v.edges.toArray(tmp);
    
    frozen = true;
  }

  List<Vertex> receivers = new ArrayList<Vertex>();
  List<Vertex> senders   = new ArrayList<Vertex>();
  Vertex[] RECEIVERS;
  Vertex[] SENDERS;

  List<Vertex> orphans = new ArrayList<Vertex>();

  private HashMap<String,Vertex> nameMap = new HashMap<String,Vertex>();

  void print() {
    assert frozen;
    for (Vertex v : RECEIVERS) {
      System.out.print(v.name + " :");
      for (Edge e : v.EDGES) {
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
    for (Edge edge : receiver.EDGES) {
      Vertex v = edge.sender.twin;
      if (v.mark != timestamp) visitReceivers(v);
    }
    finished.add(receiver.twin);
  }
  void visitSenders(Vertex sender) {
    assert sender.type == VertexType.SENDER;
    sender.mark = timestamp;
    for (Edge edge : sender.EDGES) {
      Vertex v = edge.receiver.twin;
      if (v.mark != timestamp) visitSenders(v);
    }
    sender.component = sender.twin.component = component;
  }

  Edge[] removeBadEdges(Edge[] edges) {
    int goodCount = 0;
    for (Edge edge : edges) {
      if (edge.receiver.component == edge.sender.component)
        goodCount++;
    }
    if (goodCount == edges.length) return edges;
    Edge[] goodEdges = new Edge[goodCount];
    
    goodCount = 0;
    for (Edge edge : edges) {
      if (edge.receiver.component == edge.sender.component)
        goodEdges[goodCount++] = edge;
    }
    return goodEdges;
  }

  void removeImpossibleEdges() {
    assert frozen;
    
    advanceTimestamp();
    finished = new ArrayList<Vertex>(RECEIVERS.length);
    
    // run strongly connected components and label all the components
    for (Vertex v : RECEIVERS)
      if (v.mark != timestamp) visitReceivers(v);
    Collections.reverse(finished);
    for (Vertex v : finished) {
      if (v.mark != timestamp) {
        component++;
        visitSenders(v);
      }
    }
    
    // now remove all edges between two different components
    for (Vertex v : RECEIVERS) {
      v.EDGES = removeBadEdges(v.EDGES);
    }
    for (Vertex v : SENDERS) {
      v.EDGES = removeBadEdges(v.EDGES);

      long save = v.minimumInCost;
      v.minimumInCost = Long.MAX_VALUE;
      for (Edge edge : v.EDGES)
        v.minimumInCost = Math.min(edge.cost,v.minimumInCost);
    }

    removeOrphans();
  }

  void removeOrphans() {
    int goodCount = 0;
    for (Vertex v : RECEIVERS) {
      if (v.EDGES.length > 1) goodCount++;
      else {
        assert v.EDGES.length == 1;
        assert v.EDGES[0].sender == v.twin;
        orphans.add(v);
      }
    }
    if (goodCount == RECEIVERS.length) return;
    
    Vertex[] receivers = new Vertex[goodCount];
    goodCount = 0;
    for (Vertex v : RECEIVERS) {
      if (v.EDGES.length > 1) receivers[goodCount++] = v;
    }
    RECEIVERS = receivers;
    Vertex[] senders = new Vertex[goodCount];
    goodCount = 0;
    for (Vertex v : SENDERS) {
      if (v.EDGES.length > 1) senders[goodCount++] = v;
    }
    SENDERS = senders;
  }
  
  //////////////////////////////////////////////////////////////////////

  Vertex sinkFrom;
  long sinkCost;

  static final long INFINITY = 100000000000000L; // 10^14

  void dijkstra() {
    sinkFrom = null;
    sinkCost = Long.MAX_VALUE;
    
    Heap heap = new Heap();
    for (Vertex v : SENDERS) {
      v.from = null;
      v.heapEntry = heap.insert(v, INFINITY);
    }
    for (Vertex v : RECEIVERS) {
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
        for (Edge e : vertex.EDGES) {
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

  List<List<Vertex>> findCycles() {
    assert frozen;
  
    for (Vertex v : RECEIVERS) {
      v.match = null;
      v.price = 0;
    }
    for (Vertex v : SENDERS) {
      v.match = null;
      v.price = v.minimumInCost;
    }

    for (int round = 0; round < RECEIVERS.length; round++) {
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
        for (Edge e : receiver.EDGES) {
          if (e.sender == sender) {
            receiver.matchCost = e.cost;
            break;
          }
        }

        sender = receiver.from;
      }

      // update the prices
      for (Vertex v : RECEIVERS) v.price += v.heapEntry.cost();
      for (Vertex v : SENDERS)   v.price += v.heapEntry.cost();
    }

    elideDummies();
    advanceTimestamp();
    List<List<Vertex>> cycles = new ArrayList<List<Vertex>>();
    
    for (Vertex vertex : RECEIVERS) {
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
    shuffle(RECEIVERS);
    for (Vertex v : RECEIVERS) shuffle(v.EDGES);

    // shuffle senders also?
    //  for (int i = 0; i < RECEIVERS.length; i++) SENDERS[i] = RECEIVERS[i].twin;
    //  for (Vertex v : SENDERS) shuffle(v.EDGES);
  }

  void elideDummies() {
    for (Vertex v : RECEIVERS) {
      if (v.isDummy) continue;

      while (v.match.isDummy) {
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
    for (Vertex v : RECEIVERS) {
      v.savedMatch = v.match;
      v.savedMatchCost = v.matchCost;
    }
    for (Vertex v : SENDERS) {
      v.savedMatch = v.match;
    }
  }
  void restoreMatches() {
    for (Vertex v : RECEIVERS) {
      v.match = v.savedMatch;
      v.matchCost = v.savedMatchCost;
    }
    for (Vertex v : SENDERS) {
      v.match = v.savedMatch;
    }
  }

} // end Graph
