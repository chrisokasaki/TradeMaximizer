# TradeMaximizer

Version 1.3c (dev)
Created by Chris Okasaki

## Contents

- [System Requirements](#system-requirements)
- [Introduction to TradeMaximizer](#introduction-to-trademaximizer)
- [A Small Example](#a-small-example)
- [Running TradeMaximizer](#running-trademaximizer)
- [Want List Basics](#want-list-basics)
- [Prioritizing Trades](#prioritizing-trades)
- [Protection from Duplicate Items](#protection-from-duplicate-items)
- [Controlling TradeMaximizer with Options](#controlling-trademaximizer-with-options)
- [Official Names](#official-names)
- [Compiling](#compiling)
- [License](#license)

## System Requirements

TradeMaximizer is implemented in Java, and should run on any machine with a Java Runtime Environment (JRE), 1.6 or later.  (Even ancient installations of 1.5 _might_ work, especially if you [compile](#compiling) manually.)

## Introduction to TradeMaximizer

TradeMaximizer supports multi-party trades in which each party offers up items for trade, and selects items that they wish to receive. The system then finds the largest set of items that can be traded simultaneously.

In general, the trades found by TradeMaximizer are not two-party swaps, where A receives an item from B, and B receives an item from A. Instead, trades will usually be composed of one or more larger cycles, in which each person sends an item to the previous person in the cycle and receives an item from the next person in the cycle.

The way such trades are usually run is as follows:

- One person (the _moderator_) announces the trade, and decides on basic details such as the schedule.
- Participants submit information on the items they wish to offer for trade.
- At some point, the trade closes to new entries, and the moderator publishes an official list of item names.
- Partipants review the offered items, and construct [want lists](#want-list-basics) that state, for each item, which items they would like to receive in return.
- The moderator collects all the want lists into a file, and runs TradeMaximizer (or other trade finding software) to find a valid set of trade cycles.
- The moderator publishes the trade cycles, and each participant sends their item to the previous person in the trade.  (Usually, trades will not be found for some of the items, in which case those participants simply keep their items.)

This style of trade was developed and popularized on [BoardGameGeek](http://www.boardgamegeek.com), where they are known as &ldquo;Math Trades&rdquo; and where the items being traded are typically board games and card games.  However, the idea can be used for any kinds of items.

TradeMaximizer improves on previous software for running such trades in two ways:

1. TradeMaximizer is guaranteed to find the maximum possible number of trades.  (Previous software usually found smaller numbers of trades.)
2. TradeMaximizer runs very quickly, finding results in mere seconds for trades involving over 1000 items.  (Previous software usually ran for hours or days or longer.)

## A Small Example

Here's an example of a very small trade involving six items.  The moderator has decided to use the numbers 1-6 as the official item names, and the participants have submitted the following [want lists](#want-list-basics):
```
  (Alice) 1 : 3 2 6
  (Betty) 2 : 1 6 4 3
  (Craig) 3 : 6 2
  (David) 4 : 2
  (Ethan) 5 : 1 2 3 4 6
  (Fiona) 6 : 1 2
```
The first want list states that user Alice is offering item 1 and wants one of items 3, 2, or 6 in return.  The other want lists read similarly.


The moderator runs TradeMaximizer, which finds the following five trades:
```
  (ALICE) 1 receives (CRAIG) 3
  (CRAIG) 3 receives (FIONA) 6
  (FIONA) 6 receives (ALICE) 1

  (BETTY) 2 receives (DAVID) 4
  (DAVID) 4 receives (BETTY) 2
```
Notice that Ethan's item 5 did not trade.

## Running TradeMaximizer

TradeMaximizer is designed to be run from the command line, rather than using a graphical interface.

TradeMaximizer is run using the command
<pre>
    java -jar tm.jar &lt; <i>wantlistfile</i>
</pre>
For example, if the want lists are saved in a file `wants.txt`, then you would say
```
    java -jar tm.jar < wants.txt
```
By default, the results are printed to the console.  If you want to save the results to a file instead, use the command
<pre>
    java -jar tm.jar &lt; <i>wantlistfile</i> &gt; <i>resultsfile</i>
</pre>
as in
```
    java -jar tm.jar < wants.txt > results.txt
```

For Windows users, there is a simple batch file that allows you to run TradeMaximizer with the command
```
    tm wants
```
or
```
    tm wants > results.txt
```
where the want lists are in the file `wants.txt`.

## Want List Basics

Each want list is written on a single (possibly very long) line. A typical want list is written as a list of item names separated by spaces, with a colon after the first item.
<pre>
  <i>item0</i> : <i>item1</i> <i>item2</i> ... <i>itemN</i>
</pre>
Such a list means that the owner of <tt><i>item0</i></tt> wants any one of the items
<tt><i>item1</i></tt> through <tt><i>itemN</i></tt>.

Note that a want list can be empty, as in
```
   123-OTHE :
```
which means that the owner of `123-OTH` no longer wants to trade the item.

Technically, the colon is optional (unless the moderator chooses the `REQUIRE-COLONS` option), but it is a good idea to include the colon as insurance against certain kinds of errors.

Individual item names are combinations of digits, letters, and dashes, such as `78` or `MANCALA` or `78-MANCALA`.  By default, TradeMaximizer is case insensitive, so `MANCALA`, `Mancala`, and `mancala` would all refer to the same item.

A username can be included at the beginning of the want list by
enclosing it in parentheses, as in
```
  (John Doe) 123-OTHE : 078-MANC 456-CHES
```
The username is optional (unless the moderator chooses `REQUIRE-USERNAMES`), but it makes the output more readable and also protects against certain kinds of errors.

## Prioritizing Trades

By default, TradeMaximizer does not use priorities. The moderator can choose to use priorities by specifying a priority scheme as an option (eg, `LINEAR-PRIORITIES`).  Priorities allow a user to express a preference for one item over another, even though both might be acceptable.

When using priorities, each wanted item in a want list is assigned a certain cost, where lower cost means higher priority. The system then uses cost as a tie-breaker among different ways of achieving the maximum number of trades. In particular, it finds the set of trades that has the minimum total cost, where total cost is the sum of the costs of all the individual items traded.

All priority schemes begin by finding the rank of each wanted item in a want list. The cost is then calculated as a function of rank.

- In `LINEAR-PRIORITIES`, cost = rank.
- In `TRIANGLE-PRIORITIES`, cost = 1+2+...+rank = rank*(rank+1)/2.
- In `SQUARE-PRIORITIES`, cost = rank\*rank.
- In `SCALED-PRIORITIES`, cost = 1 + (rank-1)\*2520/number of wants.

In the simplest case, rank is equal to the position of the item in the list. In other words, the first wanted item has rank 1, the second wanted item has rank 2, and so on.

The simple case can be altered in two ways. First, the moderator can set the <tt>SMALL-STEP=<i>num</i></tt> option. This sets how much the rank increases when you move from one position to the next. By default, the small-step value is 1, and it should be rare to want any small-step value except 1 or 0.

Second, the user can include a semicolon in a want list. A semicolon says &ldquo;increase the rank of the next item by the big-step value&rdquo;. (The big-step value is 9 by default, but can be set by the moderator using the <tt>BIG-STEP=<i>num</i></tt> option.)

For example, in the want list
```
  A : B C ; D
```
item B has rank 1, item C has rank 2, and item D has rank 12, assuming the small-step value is 1 and the big-step value is 9. Notice that the gap in rank between items C and D is the small-step value plus the big-step value, not just the big-step value. If the small-step and big-step values were 0 and 100, respectively, then item B would still have rank 1, but item C would also have rank 1 and item D would have rank 101.

Multiple semicolons in a row are allowed, as are semicolons before the first wanted item.

## Protection from Duplicate Items

It is common for more than one user to offer the same item for trade, and it is also common for a single user to offer more than one item. This raises the possibility of receiving more than one copy of a wanted item, when you really only wanted one.

For example, suppose you are involved in a trade of classic rock CDs, and three people have offered Pink Floyd's _Dark Side of the Moon_, which you would like to acquire.  If you have offered a single item, say Traffic's _Low Spark of High Heeled Boys_, then you can safely put all the copies of the Pink Floyd CD on your want list, as in
```
   123-LOW : 292-DARK 478-DARK 101-DARK 305-WHIT
```
However, if you are also offering Cream's _Disraeli Gears_, then you might not want to say
```
   123-LOW  : 292-DARK 478-DARK 101-DARK 305-WHIT
   124-DISR : 292-DARK 478-DARK 101-DARK 305-WHIT
```
because you could end up with two copies of _Dark Side of the Moon_, perhaps receiving 292-DARK for 123-LOW and 101-DARK for 124-DISR.  It some situations, this would be okay, but in other situations, you would vastly prefer not to get multiple copies of equivalent items.

In TradeMaximizer, you can protect against getting duplicates by using _dummy items_, as follows:

1. Add usernames to all of your want lists (if you haven't already).
 ```
    (musicfiend) 123-LOW  : 292-DARK 478-DARK 101-DARK 305-WHIT
    (musicfiend) 124-DISR : 292-DARK 478-DARK 101-DARK 305-WHIT
 ```

2. Create a dummy item, named &ldquo;%something&rdquo;, and add the set of duplicate items to the want list of the dummy item.
 ```
    (musicfiend) %PINK : 292-DARK 478-DARK 101-DARK
 ```

3. Now remove the duplicate items from your regular want lists, and add the dummy item instead.
 ```
    (musicfiend) 123-LOW  : %PINK 305-WHIT
    (musicfiend) 124-DISR : %PINK 305-WHIT
 ```

Now at most one of your regular items can &ldquo;win&rdquo; the dummy item, and the dummy item can win at most one of the duplicate items, so you will receive at most one of the duplicates.

Some things to note about dummy items:

- Dummy items can only be used if the trade moderator has selected the `ALLOW-DUMMIES` option.
- You must include a username tag in any want list that involves dummy items.
- You can create as many dummy items as you want.
- You cannot refer to anyone else's dummy items, nor can they refer to yours. (In fact, different users can have dummy items with the same name, without confusion.)
- You can refer to one of your dummy items in the want list of another dummy item.
- Dummy items are not included in the trade count, so they do not artificially inflate the number of trades.
- In a trade with priorities, keep in mind that priorities are ignored in the want list for a dummy item. However, the priority of the dummy item in the want list for one of your regular items is handled normally.
- Dummy items are elided from the trade output. For example, if 123-LOW received %PINK and %PINK received 292-DARK, then the output would say that 123-LOW received 292-DARK.

## Controlling TradeMaximizer with Options

TradeMaximizer has numerous options that can be used to configure its behavior.  Options are written inside the want list file itself, rather than using command-line switches, so that other parties can verify the trade using exactly the same options.

Options are written at the top of the file, on one or more lines beginning with the characters ``#!``.  (Note that lines beginning with just ``#`` are comments.)  For example, the following line declares two options, `ALLOW-DUMMIES` and `REQUIRE-COLONS`.
```
  #! ALLOW-DUMMIES REQUIRE-COLONS
```

Here are the options supported by TradeMaximizer:

- `LINEAR-PRIORITIES`: Use the 1,2,3,4,... priority scheme.
- `TRIANGLE-PRIORITIES`: Use the 1,3,6,10,... priority scheme.
- `SQUARE-PRIORITIES`: Use the 1,4,9,16,... priority scheme.
- `SCALED-PRIORITIES`: Use the scaled priority scheme, in which priorities are normalized into the range 1..2521.  If you are using this option, then you should also use `BIG-STEP=0`.
- `EXPLICIT-PRIORITIES`: Allow the user to annotate each wanted item with an explicit priority.  The annotated item is written <tt><i>itemname</i>=<i>priority</i></tt>.  If the annotation is missing, then the priority of the current item advances over the priority of the previous item as in linear priorities. For example, in the want list `A : B=15 C D=193` the three wanted items have priorities 15, 16, and 193 respectively.
- <tt>SMALL-STEP=<i>num</i></tt>: Adjust how priorities change between successive entries in a want list.  (The default value is 1.)
- <tt>BIG-STEP=<i>num</i></tt>: Adjust how priorities change for each semicolon in a want list.  (The default value is 9.)

- `ALLOW-DUMMIES`: Allow users to include dummy items to protect against getting duplicates.
- `REQUIRE-COLONS`: Make colons mandatory for every want list.
- `REQUIRE-USERNAMES`: Make usernames mandatory for every want list.

- <tt>ITERATIONS=<i>num</i></tt>: If set to a number larger than 1, then use randomization to find <i>num</i> different solutions, keeping the solution with the best sum-of-squares metric.  Note that all of the solutions will have the same number of trades and the same total cost.  (The default value is 1.)
- <tt>SEED=<i>num</i></tt>: Sets the seed for the random number generator to <tt><i>num</i></tt>, so that the results will be repeatable.  Only useful if `ITERATIONS` is set to a value greater than 1.

- `SHOW-MISSING`: Show items that appear in the [official names section](#official-names), but that do not have want lists.  (Ignored if there are no official names.)
- `HIDE-LOOPS`: Do not output the trade loops.
- `HIDE-SUMMARY`: Do not output the item summary.
- `HIDE-NONTRADES`: Do not include items that did not trade in the item summary.
- `HIDE-ERRORS`: Do not output error messages (except for fatal errors).
- `HIDE-REPEATS`: Do not output error messages when a want list includes the same item more than once.
- `HIDE-STATS`: Do not output the result statistics (other than the number of trades).

- `SORT-BY-ITEM`: Sort the item summary by item, instead of by username.
- `CASE-SENSITIVE`: Treat item names as case-sensitive instead of converting all lowercase letters to uppercase.

- <tt>NONTRADE-COST=<i>num</i></tt>: Adjust the cost of not trading an item from its default value of 1 billion to <tt><i>num</i></tt>. The net effect is to forbid trade loops whose average cost per item exceeds <tt><i>num</i></tt>. _Note that this means that you can end up with less than the maximum number of trades._

## Official Names

A moderator can guard against several kinds of errors by including a list of official item names at beginning of the want list file (after any options, but before the first real want list).  Official names are optional, but using official names protects against users misspelling their own items (a surprisingly common occurrence!), and also helps the system deal gracefully with missing want lists.  In addition, official item names help users validate their own want lists prior to submission.

Official items should be listed one per line, preceded by a line saying `!BEGIN-OFFICIAL-NAMES` and followed by a line saying `!END-OFFICIAL-NAMES`.  The whole file would then look like
<pre>
  #! <i>option</i> ... <i>option</i>

  !BEGIN-OFFICIAL-NAMES
  <i>item</i>
  ...
  <i>item</i>
  !END-OFFICIAL-NAMES

  <i>wantlist</i>
  ...
  <i>wantlist</i>
</pre>
When defining the official item names, the rest of the line after the item name is ignored.  It is common for moderators to publish a list of official item names, together with a brief description of each. You can simply copy-and-paste this text in between the `!BEGIN-OFFICIAL-NAMES` and `!END-OFFICIAL-NAMES` tags.  The only requirement is that the description (if any) should be separated from the item name a space or a colon (or both), as in
```
   123-LOW
   292-DARK Pink Floyd, Dark Side of the Moon
   305-WHIT: Beatles, The White Album
```
Note that official names do not apply to dummy items.

If official names are used, then the `SHOW-MISSING` option can be used to show which items do not have want lists.

## Compiling

If you want to compile TradeMaximizer from source, and you have `ant` installed,
simply run
```
  ant compile jar
```
To compile manually, follow this script (from the TradeMaximizer directory):
```
  mkdir classes
  echo Main-Class: tm.TradeMaximizer> classes/Manifest.txt
  cd src
  javac -d ../classes tm/*.java
  cd ../classes
  jar cfm tm.jar Manifest.txt tm/*.class
  mv tm.jar ..
  cd ..
```

Either way, test your installation with
```
  java -jar tm.jar < samples/ask.txt
```

## License

The MIT License

Copyright (c) 2007-2016 Chris Okasaki

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
