// TradeMaximizer.java
// Created by Chris Okasaki (cokasaki)
// Version 1.0 (15 August 2007): initial release

import java.io.*;
import java.util.*;

public class TradeMaximizer {
  public static void main(String[] args) { new TradeMaximizer().run(); }
  
  final String version = "Version 1.0: 15 August 2007";

  void run() {
    System.out.println("TradeMaximizer " + version);
    System.out.println();
    
    List< String[] > wantLists = readWantLists();
    if (wantLists == null) return;

    buildGraph(wantLists);
    if (errors.size() > 0) {
      Collections.sort(errors);
      System.out.println("ERRORS:");
      for (String err : errors) System.out.println(err);
      System.out.println();
    }

    findMatches();
    displayMatches();
  }

  //////////////////////////////////////////////////////////////////////
  
  List< String[] > readWantLists() {
    try {
      BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
      List< String[] > wantLists = new ArrayList< String[] >();
    
      for (;;) {
        String line = in.readLine();
        if (line == null) return wantLists;
        if (line.matches("\\s*(#.*)?")) continue; // blank line or comment line
        wantLists.add(line.trim().split("\\s+"));
      }
    }
    catch(Exception e) {
      System.out.println("Error reading want lists: " + e.getMessage());
      System.exit(1);
      return null;
    }
  }

  //////////////////////////////////////////////////////////////////////

  List< String > errors = new ArrayList< String >();

  final long INFINITY = 1000000000000L;
  final long NOTRADE  = 1000000L;
  final long UNIT     = 1L;

  int ITEMS; // the number of items being traded
  String[] names;
  int[][] wants; // wants[i][j] is the jth item wanted by item i
  long[][] wantCost;
  long[] cheapestWantCost;

  void buildGraph(List< String[] > wantLists) {
  
    ArrayList< String > nameList = new ArrayList< String >();
    HashMap< String,Integer > nameMap = new HashMap< String,Integer >();
    
    ArrayList< ArrayList< Integer > > wants = new ArrayList< ArrayList< Integer > >();
    ArrayList< ArrayList< Long > > wantCost = new ArrayList< ArrayList< Long > >();
    ArrayList< Long > cheapestWantCost = new ArrayList< Long >();

    // create the nodes
    for (int i = 0; i < wantLists.size(); i++) {
      String[] list = wantLists.get(i);
      assert list.length > 0;
      String name = list[0];
      if (nameMap.containsKey(name)) {
        errors.add("**** Item " + name + " has multiple want lists--ignoring all but first.  (Sometimes the result of an accidental line break in the middle of a want list.)");
        wantLists.set(i, null);
      }
      else {
        int node = ITEMS++;
        nameMap.put(name,node);
        nameList.add(name);
        cheapestWantCost.add(NOTRADE);
        wants.add(new ArrayList< Integer >());
        wantCost.add(new ArrayList< Long >());

        width = Math.max(width, name.length());
      }
    }

    // create the edges
    for (String[] list : wantLists) {
      if (list == null) continue; // skip the duplicate lists
      String fromName = list[0];
      int fromNode = nameMap.get(fromName);

      // add the "no-trade" edge to itself
      wants.get(fromNode).add(fromNode);
      wantCost.get(fromNode).add(NOTRADE);

      for (int i = 1; i < list.length; i++) {
        String toName = list[i];
        int toNode = nameMap.containsKey(toName) ? nameMap.get(toName) : -1;
        if (toNode == -1) {
          errors.add("**** Unknown item " + toName + " appears in want list for " + fromName + ".  (" + toName + " might be misspelled or its want list might be missing.)");
        }
        else if (fromNode == toNode) {
          errors.add("**** Item " + toName + " appears in its own want list.");
        }
        else if (wants.get(fromNode).indexOf(toNode) != -1) {
          errors.add("**** Item " + toName + " appears twice in want list for " + fromName + ".");
        }
        else {
          wants.get(fromNode).add(toNode);
          wantCost.get(fromNode).add(UNIT);
          cheapestWantCost.set(toNode,UNIT);
        }
      }
    }

    this.names = new String[ITEMS];
    this.wants = new int[ITEMS][];
    this.wantCost = new long[ITEMS][];
    this.cheapestWantCost = new long[ITEMS];

    for (int i = 0; i < ITEMS; i++) {
      this.names[i] = nameList.get(i);
      this.cheapestWantCost[i] = cheapestWantCost.get(i).longValue();
      
      this.wants[i] = new int[wants.get(i).size()];
      this.wantCost[i] = new long[wantCost.get(i).size()];
      for (int j = 0; j < wants.get(i).size(); j++) {
        this.wants[i][j] = wants.get(i).get(j);
        this.wantCost[i][j] = wantCost.get(i).get(j);
      }
    }
  }

  //////////////////////////////////////////////////////////////////////
  
  int NONE = -1;
  int[] match;
  long[] price;
  Heap[] heap;
  int[] from;

  int sinkFrom;
  long sinkCost;
  long[] matchCost;

  void dijkstra() {
    clearHeap();
    Arrays.fill(heap,null);
    Arrays.fill(from,NONE);
    sinkFrom = NONE;
    sinkCost = INFINITY;

    for (int i = ITEMS; i < 2*ITEMS; i++)
      heap[i] = insert(i,INFINITY);
    for (int i = 0; i < ITEMS; i++)
      heap[i] = insert(i, (match[i] == NONE) ? 0 : INFINITY);

    while (!isEmpty()) {
      Heap h = extractMin();
      int id = h.id;
      long cost = h.cost;
      if (cost == INFINITY) break; // everything left is unreachable
      if (id < ITEMS) {
        for (int j = 0; j < wants[id].length; j++) {
          int other = wants[id][j] + ITEMS;
          if (other == match[id]) continue;
          long c = price[id] + wantCost[id][j] - price[other];
          if (cost + c < heap[other].cost) {
            from[other] = id;
            decreaseCost(heap[other],cost + c);
          }
        }
      }
      // id >= ITEMS
      else if (match[id] == NONE) {
        if (cost < sinkCost) {
          sinkFrom = id;
          sinkCost = cost;
        }
      }
      else {
        int other = match[id];
        long c = price[id] - matchCost[other] - price[other];
        assert c >= 0;
        if (cost + c < heap[other].cost) {
          from[other] = id;
          decreaseCost(heap[other],cost + c);
        }
      }
    }
  }

  void findMatches() {
    int V = 2*ITEMS;
    match = new int[V];
    price = new long[V];
    heap  = new Heap[V];
    from = new int[V];
    matchCost = new long[ITEMS];
    
    Arrays.fill(match,NONE);
    for (int i = 0; i < ITEMS; i++) price[i+ITEMS] = cheapestWantCost[i];

    for (int round = 0; round < V; round++) {
      dijkstra();

      // update the matching
      int right = sinkFrom;
      while (right != NONE) {
        int left = from[right];

        // unlink right and left from current matches
        if (match[right] != NONE) match[match[right]] = NONE;
        if (match[left] != NONE) match[match[left]] = NONE;
        
        match[right] = left;
        match[left] = right;

        // update matchCost
        int pos = 0;
        while (wants[left][pos] != right-ITEMS) pos++;
        matchCost[left] = wantCost[left][pos];

        right = from[left];
      }

      // update the prices
      for (int i = 0; i < V; i++) {
        // if (heap[i].cost < INFINITY) price[i] += heap[i].cost;
        price[i] += heap[i].cost;
      }
    }
  }

  //////////////////////////////////////////////////////////////////////
  
  void displayMatches() {
    int countTrades = 0;
    for (int i = 0; i < ITEMS; i++)
      if (match[i] != i+ITEMS) countTrades++;

    System.out.println("FOUND " + countTrades + " TRADES");
    System.out.println();
    System.out.println("TRADE LOOPS:");
    System.out.println();
    List< String > summary = new ArrayList< String >();

    boolean[] used = new boolean[ITEMS];
    
    for (int i = 0; i < ITEMS; i++) {
      if (used[i]) continue;
      if (match[i] == i+ITEMS) {
        summary.add(pad(names[i]) + " does not trade");
      }
      else {
        for (int j = i; !used[j]; j = match[j] - ITEMS) {
          used[j] = true;
          assert match[j] != NONE;
          int other = match[j] - ITEMS;
          System.out.println(pad(names[j]) + " receives " + names[other]);
          summary.add(pad(names[j]) + " receives " + names[other] + " and sends to " + names[match[j + ITEMS]]);
        }
        System.out.println();
      }
    }

    Collections.sort(summary);
    System.out.println("ITEM SUMMARY:");
    System.out.println();
    for (String item : summary) System.out.println(item);

    System.out.println();
    System.out.println(countTrades + " TRADES");

    // long totalCost = 0;
    // for (long cost : matchCost) totalCost += cost;
    // System.out.println("Total Cost = " + totalCost);
  }

  int width = 1;
  String pad(String name) {
    while (name.length() < width) name += " ";
    return name;
  }

  //////////////////////////////////////////////////////////////////////
  // priority queues implemented as skew heaps
  
  static class Heap {
    int id;
    long cost;
    Heap left,right,parent;
    Heap(int id,long cost) {
      this.id = id;
      this.cost = cost;
    }
  }
  
  static Heap root = null;
  
  static void clearHeap() { root = null; }
  static boolean isEmpty() { return root==null; }
  
  static Heap merge(Heap a,Heap b) {
    if (a == null) return b;
    if (b == null) return a;
    if (b.cost < a.cost) { Heap tmp = a; a = b; b = tmp; }
    { Heap tmp = a.left; a.left = a.right; a.right = tmp; }
    Heap left = merge(a.left,b);
    if (left != null) left.parent = a;
    a.left = left;
    return a;
  }
  
  static Heap insert(int id,long cost) {
    Heap h = new Heap(id,cost);
    root = merge(h,root);
    root.parent = null;
    return h;
  }
  
  static void decreaseCost(Heap h,long cost) {
    assert cost < h.cost;
    h.cost = cost;
    if (h == root || cost >= h.parent.cost) return;
    if (h == h.parent.left) h.parent.left = null;
    else {
      assert h == h.parent.right;
      h.parent.right = null;
    }
    h.parent = null;
    root = merge(root,h);
    root.parent = null;
  }
  
  static Heap extractMin() {
    Heap min = root;
    root = merge(root.left,root.right);
    if (root != null) root.parent = null;
    return min;
  }

}