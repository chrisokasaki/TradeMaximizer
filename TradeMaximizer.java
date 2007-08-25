// TradeMaximizer.java
// Created by Chris Okasaki (cokasaki)
// Version 1.1: 29 August 2007

import java.io.*;
import java.util.*;

public class TradeMaximizer {
  public static void main(String[] args) { new TradeMaximizer().run(); }
  
  final String version = "Version 1.1: 29 August 2007";

  void run() {
    System.out.println("TradeMaximizer " + version);
    
    List< String[] > wantLists = readWantLists();
    if (wantLists == null) return;
    if (options.size() > 0) {
      System.out.print("Options:");
      for (String option : options) System.out.print(" "+option);
      System.out.println();
    }
    System.out.println();

    buildGraph(wantLists);
    if (showErrors && errors.size() > 0) {
      Collections.sort(errors);
      System.out.println("ERRORS:");
      for (String error : errors) System.out.println(error);
      System.out.println();
    }

    long startTime = System.currentTimeMillis();
    findMatches();
    long stopTime = System.currentTimeMillis();
    displayMatches();

    if (showElapsedTime)
      System.out.println("Elapsed time = " + (stopTime-startTime) + "ms");
  }

  boolean caseSensitive = false;
  boolean requireColons = false;
  boolean requireUsernames = false;
  boolean showErrors = true;
  boolean showRepeats = true;
  boolean showLoops = true;
  boolean showSummary = true;
  boolean showNonTrades = true;
  boolean showStats = true;
  boolean sortByItem = false;
  boolean allowDummies = false;
  boolean showElapsedTime = false;

  static final int NO_PRIORITIES = 0;
  static final int LINEAR_PRIORITIES = 1;
  static final int TRIANGLE_PRIORITIES = 2;
  static final int SQUARE_PRIORITIES = 3;

  int priorityScheme = NO_PRIORITIES;
  int smallStep = 1;
  int bigStep = 9;
  long nonTradeCost = 1000000000L; // 1 billion
  
  //////////////////////////////////////////////////////////////////////
  
  List< String > options = new ArrayList< String >();
  
  List< String[] > readWantLists() {
    try {
      BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
      List< String[] > wantLists = new ArrayList< String[] >();

      for (int lineNumber = 1;;lineNumber++) {
        String line = in.readLine();
        if (line == null) return wantLists;

        line = line.trim();
        if (line.length() == 0) continue; // skip blank link
        if (line.matches("#!.*")) { // declare options
          if (wantLists.size() > 0)
            fatalError("Options (#!...) cannot be declared after first real want list", lineNumber);
          for (String option : line.toUpperCase().substring(2).trim().split("\\s+")) {
            if (option.equals("CASE-SENSITIVE"))
              caseSensitive = true;
            else if (option.equals("REQUIRE-COLONS"))
              requireColons = true;
            else if (option.equals("REQUIRE-USERNAMES"))
              requireUsernames = true;
            else if (option.equals("HIDE-ERRORS"))
              showErrors = false;
            else if (option.equals("HIDE-REPEATS"))
              showRepeats = false;
            else if (option.equals("HIDE-LOOPS"))
              showLoops = false;
            else if (option.equals("HIDE-SUMMARY"))
              showSummary = false;
            else if (option.equals("HIDE-NONTRADES"))
              showNonTrades = false;
            else if (option.equals("HIDE-STATS"))
              showStats = false;
            else if (option.equals("SORT-BY-ITEM"))
              sortByItem = true;
            else if (option.equals("ALLOW-DUMMIES"))
              allowDummies = true;
            else if (option.equals("SHOW-ELAPSED-TIME"))
              showElapsedTime = true;
            else if (option.equals("LINEAR-PRIORITIES"))
              priorityScheme = LINEAR_PRIORITIES;
            else if (option.equals("TRIANGLE-PRIORITIES"))
              priorityScheme = TRIANGLE_PRIORITIES;
            else if (option.equals("SQUARE-PRIORITIES"))
              priorityScheme = SQUARE_PRIORITIES;
            else if (option.startsWith("SMALL-STEP=")) {
              String num = option.substring(11);
              if (!num.matches("\\d+"))
                fatalError("SMALL-STEP argument must be a non-negative integer",lineNumber);
              smallStep = Integer.parseInt(num);
            }
            else if (option.startsWith("BIG-STEP=")) {
              String num = option.substring(9);
              if (!num.matches("\\d+"))
                fatalError("BIG-STEP argument must be a non-negative integer",lineNumber);
              bigStep = Integer.parseInt(num);
            }
            else if (option.startsWith("NONTRADE-COST=")) {
              String num = option.substring(14);
              if (!num.matches("[1-9]\\d*"))
                fatalError("NONTRADE-COST argument must be a positive integer",lineNumber);
              nonTradeCost = Long.parseLong(num);
            }
            else
              fatalError("Unknown option \""+option+"\"",lineNumber);

            options.add(option);
          }
          continue;
        }
        if (line.matches("#.*")) continue; // skip comment line
        if (line.indexOf("#") != -1)
          fatalError("Comments (#...) cannot be used after beginning of line",lineNumber);

        // check parens for user name
        if (line.indexOf("(") == -1 && requireUsernames)
          fatalError("Missing username with REQUIRE-USERNAMES selected",lineNumber);
        if (line.charAt(0) == '(') {
          if (line.lastIndexOf("(") > 0)
            fatalError("Cannot have more than one '(' per line",lineNumber);
          int close = line.indexOf(")");
          if (close == -1)
            fatalError("Missing ')' in username",lineNumber);
          if (close == line.length()-1)
            fatalError("Username cannot appear on a line by itself",lineNumber);
          if (line.lastIndexOf(")") > close)
            fatalError("Cannot have more than one ')' per line",lineNumber);
          if (close == 1)
            fatalError("Cannot have empty parentheses",lineNumber);

          // temporarily replace spaces in username with #'s
          if (line.indexOf(" ") < close) {
            line = line.substring(0,close+1).replaceAll(" ","#")+" "
                    + line.substring(close+1);
          }
        }
        else if (line.indexOf("(") > 0)
          fatalError("Username can only be used at the front of a want list",lineNumber);
        else if (line.indexOf(")") > 0)
          fatalError("Bad ')' on a line that does not have a '('",lineNumber);

          
        // check semicolons
        line = line.replaceAll(";"," ; ");
        int semiPos = line.indexOf(";");
        if (semiPos != -1) {
          if (semiPos < line.indexOf(":"))
            fatalError("Semicolon cannot appear before colon",lineNumber);
          String before = line.substring(0,semiPos).trim();
          if (before.length() == 0 || before.charAt(before.length()-1) == ')')
            fatalError("Semicolon cannot appear before first item on line", lineNumber);
        }
        
        // check and remove colon
        int colonPos = line.indexOf(":");
        if (colonPos != -1) {
          if (line.lastIndexOf(":") != colonPos)
            fatalError("Cannot have more that one colon on a line",lineNumber);
          String header = line.substring(0,colonPos).trim();
          if (!header.matches("(.*\\)\\s+)?[^(\\s)]\\S*"))
            fatalError("Must have exactly one item before a colon (:)",lineNumber);
          line = line.replaceFirst(":"," "); // remove colon
        }
        else if (requireColons) {
          fatalError("Missing colon with REQUIRE-COLONS selected",lineNumber);
        }

        if (!caseSensitive) line = line.toUpperCase();
        wantLists.add(line.trim().split("\\s+"));
      }
    }
    catch(Exception e) {
      fatalError(e.getMessage());
      return null;
    }
  }

  void fatalError(String msg) {
    System.out.println();
    System.out.println("FATAL ERROR: " + msg);
    System.exit(1);
  }
  void fatalError(String msg,int lineNumber) {
    fatalError(msg + " (line " + lineNumber + ")");
  }
  
  //////////////////////////////////////////////////////////////////////

  List< String > errors = new ArrayList< String >();

  final long INFINITY = 100000000000000L; // 10^14
  // final long NOTRADE  = 1000000000L; // replaced by nonTradeCost
  final long UNIT     = 1L;

  List< String > names;
  List< String > users;
  List< Long > cheapestWantCost;
  List< Boolean > dummy;
  
  int ITEMS; // the number of items being traded (including dummy items)
  int DUMMY_ITEMS; // the number of dummy items
  int[][] wants; // wants[i][j] is the jth item wanted by item i
  long[][] wantCost;

  void buildGraph(List< String[] > wantLists) {
  
    users = new ArrayList< String >();
    names = new ArrayList< String >();
    cheapestWantCost = new ArrayList< Long >();
    dummy = new ArrayList< Boolean >();
    
    HashMap< String,Integer > nameMap = new HashMap< String,Integer >();
    HashMap< String,Integer > unknowns = new HashMap< String,Integer >();
    
    ArrayList< ArrayList< Integer > > wants = new ArrayList< ArrayList< Integer > >();
    ArrayList< ArrayList< Long > > wantCost = new ArrayList< ArrayList< Long > >();

    // create the nodes
    for (int i = 0; i < wantLists.size(); i++) {
      String[] list = wantLists.get(i);
      assert list.length > 0;
      String name = list[0];
      String user = null;
      int offset = 0;
      if (name.charAt(0) == '(') {
        user = name.replaceAll("#"," "); // restore spaces in username
        // remove username from list
        list = Arrays.copyOfRange(list,1,list.length);
        wantLists.set(i,list);
        assert list.length > 1;
        name = list[0];
      }
      boolean isDummy = (name.charAt(0) == '%');
      if (isDummy) {
        if (user == null)
          errors.add("**** Dummy item " + name + " declared without a username.");
        else if (!allowDummies)
          errors.add("**** Dummy items not allowed. ("+name+")");
        else {
          name += " for user " + user;
          list[0] = name;
        }
      }
      if (nameMap.containsKey(name)) {
        errors.add("**** Item " + name + " has multiple want lists--ignoring all but first.  (Sometimes the result of an accidental line break in the middle of a want list.)");
        wantLists.set(i, null);
      }
      else {
        int node = ITEMS++;
        if (isDummy) DUMMY_ITEMS++;
        nameMap.put(name,node);

        if (user != null && !isDummy) { // add user name to display name
          name = sortByItem ? name + " " + user
                            : user + " " + name;
        }
        names.add(name);
        users.add(user);
        dummy.add(isDummy);
        cheapestWantCost.add(nonTradeCost);
        wants.add(new ArrayList< Integer >());
        wantCost.add(new ArrayList< Long >());

        if (!isDummy) width = Math.max(width, name.length());
      }
    }

    // create the edges
    for (String[] list : wantLists) {
      if (list == null) continue; // skip the duplicate lists
      String fromName = list[0];
      int fromNode = nameMap.get(fromName);

      // add the "no-trade" edge to itself
      wants.get(fromNode).add(fromNode);
      wantCost.get(fromNode).add(nonTradeCost);

      long position = 1;
      for (int i = 1; i < list.length; i++) {
        String toName = list[i];
        if (toName.equals(";")) {
          position += bigStep;
          continue;
        }
        if (toName.charAt(0) == '%') {
          if (users.get(fromNode) == null) {
            errors.add("**** Dummy item " + toName + " used in want list for item " + fromName + ", which does not have a username.");
            continue;
          }

          toName += " for user " + users.get(fromNode); 
        }
        int toNode = nameMap.containsKey(toName) ? nameMap.get(toName) : -1;
        if (toNode == -1) {
          int occurrences = unknowns.containsKey(toName) ? unknowns.get(toName) : 0;
          unknowns.put(toName,occurrences + 1);
        }
        else if (fromNode == toNode) {
          errors.add("**** Item " + toName + " appears in its own want list.");
        }
        else if (wants.get(fromNode).indexOf(toNode) != -1) {
          if (showRepeats)
            errors.add("**** Item " + toName + " is repeated in want list for " + fromName + ".");
        }
        else if (users.get(fromNode) != null &&
                 users.get(toNode) != null &&
                 users.get(fromNode).equals(users.get(toNode)) &&
                 !dummy.get(toNode)) {
          errors.add("**** Item "+names.get(fromNode)+" contains item "+names.get(toNode)+" from the same user ("+users.get(fromNode)+")");
        }
        else {
          wants.get(fromNode).add(toNode);
          long cost = UNIT;
          switch (priorityScheme) {
            case LINEAR_PRIORITIES:   cost = position; break;
            case TRIANGLE_PRIORITIES: cost = position*(position+1)/2; break;
            case SQUARE_PRIORITIES:   cost = position*position; break;
          }

          // all edges out of a dummy node have the same cost
          if (dummy.get(fromNode)) cost = nonTradeCost;
          
          wantCost.get(fromNode).add(cost);
          cheapestWantCost.set(toNode,Math.min(cost,cheapestWantCost.get(toNode)));
          position += smallStep;
        }
      }
    }

    for (Map.Entry< String,Integer > entry : unknowns.entrySet()) {
      String item = entry.getKey();
      int occurrences = entry.getValue();
      String plural = occurrences == 1 ? "" : "s";
      errors.add("**** Unknown item " + item + " (" + occurrences + " occurrence" + plural + ")");
    }

    this.wants = new int[ITEMS][];
    this.wantCost = new long[ITEMS][];

    for (int i = 0; i < ITEMS; i++) {
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
  Heap.Entry[] heapEntry;
  int[] from;

  int sinkFrom;
  long sinkCost;
  long[] matchCost;

  void dijkstra() {
    Arrays.fill(from,NONE);

    sinkFrom = NONE;
    sinkCost = INFINITY;
    
    Heap heap = new Heap();
    for (int i = ITEMS; i < 2*ITEMS; i++)
      heapEntry[i] = heap.insert(i,INFINITY);
    for (int i = 0; i < ITEMS; i++)
      heapEntry[i] = heap.insert(i, (match[i] == NONE) ? 0 : INFINITY);

    while (!heap.isEmpty()) {
      Heap.Entry minEntry = heap.extractMin();
      int id = minEntry.id();
      long cost = minEntry.cost();
      if (cost == INFINITY) break; // everything left is unreachable
      if (id < ITEMS) {
        for (int j = 0; j < wants[id].length; j++) {
          int other = wants[id][j] + ITEMS;
          if (other == match[id]) continue;
          long c = price[id] + wantCost[id][j] - price[other];
          if (cost + c < heapEntry[other].cost()) {
            from[other] = id;
            heapEntry[other].decreaseCost(cost + c);
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
        if (cost + c < heapEntry[other].cost()) {
          from[other] = id;
          heapEntry[other].decreaseCost(cost + c);
        }
      }
    }
  }

  void findMatches() {
    int V = 2*ITEMS;
    match = new int[V];
    price = new long[V];
    heapEntry  = new Heap.Entry[V];
    from = new int[V];
    matchCost = new long[ITEMS];
    
    Arrays.fill(match,NONE);
    for (int i = 0; i < ITEMS; i++) price[i+ITEMS] = cheapestWantCost.get(i);

    for (int round = 0; round < ITEMS; round++) {
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
        price[i] += heapEntry[i].cost();
      }
    }
  }

  //////////////////////////////////////////////////////////////////////
  
  void displayMatches() {
    int numTrades = 0;
    int numGroups = 0;
    int totalCost = 0;
    int sumOfSquares = 0;
    List< Integer > groupSizes = new ArrayList< Integer >();

    List< String > summary = new ArrayList< String >();
    List< String > loops = new ArrayList< String >();

    boolean[] used = new boolean[ITEMS];
    
    for (int i = 0; i < ITEMS; i++) {
      if (used[i] || dummy.get(i)) continue;
      if (match[i] == i+ITEMS) {
        if (showNonTrades) summary.add(pad(names.get(i)) + " does not trade");
      }
      else {
        int groupSize = 0;
        for (int j = i; !used[j]; ) {
          groupSize++;
          totalCost += matchCost[j];
          
          used[j] = true;
          assert match[j] != NONE;
          int fromItem = match[j] - ITEMS;
          while (dummy.get(fromItem)) fromItem = match[fromItem] - ITEMS;
          int toItem = match[j + ITEMS];
          while (dummy.get(toItem)) toItem = match[toItem + ITEMS];
          loops.add(pad(names.get(j)) + " receives " + names.get(fromItem));
          summary.add(pad(names.get(j)) + " receives " + pad(names.get(fromItem)) + " and sends to " + names.get(toItem));
          j = fromItem;
        }
        numGroups++;
        numTrades += groupSize;
        groupSizes.add(groupSize);
        sumOfSquares += groupSize*groupSize;
        
        loops.add("");
      }
    }

    if (showLoops) {
      System.out.println("TRADE LOOPS (" + numTrades + " total trades):");
      System.out.println();
      for (String item : loops) System.out.println(item);
    }
    
    if (showSummary) {
      Collections.sort(summary);
      System.out.println("ITEM SUMMARY (" + numTrades + " total trades):");
      System.out.println();
      for (String item : summary) System.out.println(item);
      System.out.println();
    }

    
    System.out.println("Num trades  = " + numTrades + " of " + (ITEMS-DUMMY_ITEMS) + " items");
    if (showStats) {
      System.out.println("Total cost  = " + totalCost);
      System.out.println("Num groups  = " + numGroups);
      System.out.print("Group sizes =");
      Collections.sort(groupSizes);
      Collections.reverse(groupSizes);
      for (int groupSize : groupSizes) System.out.print(" " + groupSize);
      System.out.println();
      System.out.println("Sum squares = " + sumOfSquares);
    }
  }

  int width = 1;
  String pad(String name) {
    while (name.length() < width) name += " ";
    return name;
  }

} // end TradeMaximizer
