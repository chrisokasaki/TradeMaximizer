@echo off
del *.class
javac TradeMaximizer.java
jar cfm tm.jar Manifest.txt *.class
