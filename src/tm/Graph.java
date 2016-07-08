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

    private boolean dirty = true;  // only used for senders
  }

  public static class Edge {
    Vertex receiver;
    Vertex sender;
    long cost;
    EdgeStatus status = EdgeStatus.UNKNOWN;

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

    Vertex sender = new Vertex(name,user,isDummy,VertexType.SENDER);
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
      else
        edge.sender.dirty = true;
    }
    v.edges = Arrays.copyOf(v.edges, goodCount);
  }

  void removeImpossibleEdgesAndOrphans() {
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
    for (Vertex v : senders) removeBadEdges(v);

    removeOrphans();
  }

  // remove all vertices whose only edge is the self (nontrade) edge
  // MUST ONLY BE CALLED AFTER SCC, SO THAT THE SENDER AND RECEIVER OF THE ORPHAN
  // WILL **BOTH** ONLY HAVE A SINGLE EDGE
  private void removeOrphans() {
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

  static final long INFINITY = 10000000000000000L; // 10^16

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
          assert cost + c < INFINITY;
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

    if (hasBeenFullyShrunk) {
      findUnweightedMatches();
      return;
    }

    for (Vertex v : receivers) {
      v.match = null;
      v.price = 0;
    }
    for (Vertex v : senders) {
      v.match = null;
      if (v.dirty) {
        v.minimumInCost = Long.MAX_VALUE;
        for (Edge edge : v.edges)
          v.minimumInCost = Math.min(edge.cost,v.minimumInCost);
        v.dirty = false;
      }
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

  /////////////////////////////////////////////////////////////////

  boolean hasBeenFullyShrunk = false;

  void shrink(int level, boolean verbose) {
    assert level >= 0;

    reportStats("Original", verbose);

    removeImpossibleEdgesAndOrphans();
    reportStats("Shrink 0 (SCC)", verbose);
    if (level == 0) return;

    long startTime = System.currentTimeMillis();

    int factor = receivers.length+1;

    scaleUpEdgeCosts(factor);
    findRequiredEdgesAndShrink(verbose);
    removeImpossibleEdgesAndOrphans();
    reportStats("Shrink 1 (SCC)", verbose);
    if (verbose) System.out.println("Shrink 1 time = " + (System.currentTimeMillis() - startTime) + "ms");

    if (level > 1) {
      findForbiddenEdgesAndShrink(verbose);
      removeImpossibleEdgesAndOrphans();
      reportStats("Shrink 2 (SCC)", verbose);
      if (verbose) System.out.println("Shrink 2 time = " + (System.currentTimeMillis() - startTime) + "ms");
      hasBeenFullyShrunk = true;
    }
    scaleDownEdgeCosts(factor);
  }

  void scaleUpEdgeCosts(int factor) {
    for (Vertex v : senders) {
      v.dirty = true;
      for (Edge e : v.edges)
        e.cost *= factor;
    }
  }

  void scaleDownEdgeCosts(int factor) {
    for (Vertex v : senders) {
      v.dirty = true;
      for (Edge e : v.edges)
        e.cost /= factor;
    }
  }

  static enum EdgeStatus { UNKNOWN, REQUIRED, OPTIONAL, FORBIDDEN }
  // an edge is
  //   - REQUIRED if it is in *every* optimal solution
  //   - OPTIONAL if it is in some but not all optimal solutions
  //   - FORBIDDEN if it is in *no* optimal solutions
  //   - UNKNOWN if its status has not yet been determined

  // Identify which edges are REQUIRED
  // All all other edges from the same receiver or sender *must* be forbidden
  // so delete them
  // leaves the cost of all required edges incremented by 1
  void findRequiredEdgesAndShrink(boolean verbose) {
    int numRequired = receivers.length;
    long totalCost = 0;
    Edge[] requiredEdges = new Edge[numRequired];
    int run = 1;

    if (!verbose) System.out.print("Shrink (level 1) ");

    // Find initial solution. Temporaritly mark all chosen edges as REQUIRED
    // and bump their costs.
    findBestMatches();
    for (int i = 0; i < receivers.length; i++) {
      Vertex v = receivers[i];
      Edge e = v.edgeMap.get(v.match);
      totalCost += e.cost;
      requiredEdges[i] = e;
      e.status = EdgeStatus.REQUIRED;
      e.cost++;
      if (e.cost == e.sender.minimumInCost+1) e.sender.dirty = true;
    }
    reportStatsOrDot("Shrink 1."+run, verbose);

    // Find new solutions.  Because of the bumped costs, each new solution
    // will include as many new edges as possible.  Any previously REQUIRED
    // edge that is not included in one of these solutions is not actually
    // required after all, so mark it as OPTIONAL and unbump its cost.
    for (run = 2; numRequired > 0; run++) {
      findBestMatches();
      long currentCost = 0;
      HashSet<Edge> edgeSet = new HashSet<Edge>(receivers.length);
      for (Vertex v : receivers) {
        Edge e = v.edgeMap.get(v.match);
        edgeSet.add(e);
        currentCost += e.cost;
        if (e.status != EdgeStatus.REQUIRED) e.status = EdgeStatus.OPTIONAL;
      }
      if (currentCost == totalCost + numRequired) {  // no new edges were found
        reportStatsOrDot("Shrink 1."+run, verbose);
        break;
      }
      int count = 0;
      for (int i = 0; i < numRequired; i++) {
        Edge e = requiredEdges[i];
        if (edgeSet.contains(e)) {
          requiredEdges[count++] = e;
        }
        else {
          // this edge isn't required after all
          e.status = EdgeStatus.OPTIONAL;
          e.cost--;
          if (e.cost == e.sender.minimumInCost-1) e.sender.dirty = true;
        }
      }
      numRequired = count;
      reportStatsOrDot("Shrink 1."+run, verbose);
    }

    // at this point everything marked REQUIRED is accurate, which means we can
    // delete all other edges from that sender or to that receiver.  Those other
    // edges cannot have been marked OPTIONAL so must still be marked UNKNOWN.
    for (int i = 0; i < numRequired; i++) {
      Edge e = requiredEdges[i];
      markEdgesForbiddenIfNotRequired(e.receiver);
      markEdgesForbiddenIfNotRequired(e.sender);
    }
    for (Vertex v : receivers) removeEdges(v, EdgeStatus.FORBIDDEN);
    for (Vertex v : senders) removeEdges(v, EdgeStatus.FORBIDDEN);

    for (int i = 0; i < numRequired; i++) {
      Edge e = requiredEdges[i];
      assert e.receiver.edges.length == 1;
      assert e.sender.edges.length == 1;
    }

    if (verbose) reportStats("Shrink 1 complete", verbose);
    else System.out.println();
  }

  // must be called *after* findRequiredEdgesAndShrink
  // the edges still marked UNKNOWN are either OPTIONAL or FORBIDDEN
  // mark all the OPTIONAL ones, then anything left is FORBIDDEN and can be
  // removed
  void findForbiddenEdgesAndShrink(boolean verbose) {
    int V = receivers.length;
    int run = 1;

    // increment cost af all edges already marked OPTIONAL (REQUIRED
    // edges already incremented)
    for (Vertex v : receivers) {
      for (Edge e : v.edges) {
        if (e.status == EdgeStatus.OPTIONAL) {
          e.cost++;
          if (e.cost == e.sender.minimumInCost+1) e.sender.dirty = true;
        }
      }
    }

    if (!verbose) System.out.print("Shrink (level 2) ");

    // because the costs of REQUIRED/OPTIONAL edges are bumped,
    // each new solution will contain as many previously UNKNOWN
    // edges as possible.  Mark these new edges as OPTIONAL.
    // Stop when no new edges are found.
    for (int newEdges = 999; newEdges > 0; run++) {
      findBestMatches();
      newEdges = 0;
      HashSet<Edge> edgeSet = new HashSet<Edge>(V);
      for (Vertex v : receivers) {
        Edge e = v.edgeMap.get(v.match);
        if (e.status == EdgeStatus.UNKNOWN) {
          e.status = EdgeStatus.OPTIONAL;
          e.cost++;
          if (e.cost == e.sender.minimumInCost+1) e.sender.dirty = true;
          newEdges++;
        }
      }
      reportStatsOrDot("Shrink 2."+run, verbose);
    }

    // When no new odges are found, everything that is currently UNKNOWN
    // is actually FORBIDDEN, so remove them.
    for (Vertex v : receivers) removeEdges(v, EdgeStatus.UNKNOWN);
    for (Vertex v : senders) removeEdges(v, EdgeStatus.UNKNOWN);

    if (verbose) reportStats("Shrink level 2 complete", verbose);
    else System.out.println();
  }

  void markEdgesForbiddenIfNotRequired(Vertex v) {
    for (Edge e : v.edges) {
      assert e.status != EdgeStatus.OPTIONAL;
      if (e.status != EdgeStatus.REQUIRED) e.status = EdgeStatus.FORBIDDEN;
    }
  }

  void removeEdges(Vertex v, EdgeStatus statusToRemove) {
    int numToKeep = 0;
    for (Edge e : v.edges) {
      if (e.status == statusToRemove) {
        e.sender.dirty = true;
      }
      else {
        v.edges[numToKeep++] = e;
      }
    }
    v.edges = Arrays.copyOf(v.edges, numToKeep);
  }

  void reportStatsOrDot(String name, boolean verbose) {
    if (verbose) reportStats(name, verbose);
    else System.out.print(".");
  }

  void reportStats(String name, boolean verbose) {
    if (!verbose) return;

    int[] histogram = new int[3];
    int edgeCount = 0;
    for (Vertex v : receivers) {
      for (Edge e : v.edges) {
        histogram[e.status.ordinal()]++;
        edgeCount++;
      }
    }

    System.out.println(name +
      ": V=" + receivers.length +
      " E=" + edgeCount +
      " REQUIRED=" + histogram[1] +
      " OPTIONAL=" + histogram[2] +
      " UNKNOWN=" + histogram[0]);
  }

  /////////////////////////////////////////////////////////////////

  // simplified Ford-Fulkerson to find a perfect bipartite matching
  // under the assumption that a perfect matching exists
  // ignores weights!
  void findUnweightedMatches() {
    assert frozen;

    for (Vertex v : receivers) v.match = null;
    for (Vertex v : senders) {
      v.match = null;
      v.price = 0;  // hack: use the price fields to track "visited" in the dfs
    }

    // make some stacks for the dfs
    int n = receivers.length;
    Vertex[] receiverStack = new Vertex[n];
    int[] indexStack = new int[n];
    Vertex[] senderStack= new Vertex[n];
    int time = 0;

    for (Vertex v : receivers) {
      time++; // a vertex has been visited if its price == time

      // do an iterative dfs to find an augmenting path from v to
      // an unused sender
      int pos = 0;
      receiverStack[pos] = v;
      indexStack[pos] = 0;
      v.price = time;

      while (true) {
        Vertex receiver = receiverStack[pos];
        int i = indexStack[pos]++;
        if (i == receiver.edges.length) { // backtrack
          pos--;
        }
        else {
          Vertex sender = receiver.edges[i].sender;
          if (sender.price == time) continue; // already visited, skip it

          senderStack[pos] = sender;
          if (sender.match == null) break; // found the augmenting path

          sender.price = time; // mark as visited
          receiverStack[++pos] = sender.match;
          indexStack[pos] = 0;
        }
      }

      // update the edges according to the augmenting path
      for (int i = 0; i <= pos; i++) {
        Vertex receiver = receiverStack[i];
        Vertex sender = senderStack[i];
        receiver.match = sender;
        sender.match = receiver;
      }
    }

    // update all the matchCosts
    for (Vertex v : receivers) {
      long matchCost = v.edgeMap.get(v.match).cost;
      v.matchCost = v.match.matchCost = matchCost;
    }
  }

  ////////////////////////////////////////////////////////////////

  // DEBUGGING CODE
  void sanityCheck() {
    System.out.println("SANITY CHECK");
    assert(receivers.length == senders.length);
    int rcount = 0, scount = 0;
    for (Vertex v : receivers) rcount += v.edges.length;
    for (Vertex v : senders) scount += v.edges.length;
    if (rcount != scount) System.out.println("rcount=" + rcount +" scount="+scount);
    assert(rcount == scount);
    for (Vertex v : receivers) {
      for (Edge e : v.edges) assert(search(e, e.sender.edges));
    }
    for (Vertex v : senders) {
      for (Edge e : v.edges) assert(search(e, e.receiver.edges));
      assert(v.dirty || v.minimumInCost == calcMinCost(v.edges));
    }
  }
  boolean search(Edge edge, Edge[] edges) {
    for (Edge e : edges) if (e == edge) return true;
    return false;
  }
  long calcMinCost(Edge[] edges) {
    long min = Long.MAX_VALUE;
    for (Edge e : edges) min = Math.min(e.cost, min);
    return min;
  }
} // end Graph
