@echo off
del classes\*.class
javac -d classes TradeMaximizer.java
cd classes
jar cfm tm.jar Manifest.txt *.class
move tm.jar ..
cd ..
