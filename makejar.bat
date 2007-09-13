@echo off
del classes\*.class
javac -target 1.5 -d classes TradeMaximizer.java
cd classes
jar cfm tm.jar Manifest.txt *.class
move tm.jar ..
cd ..
