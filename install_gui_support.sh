#!/bin/bash

echo "🖥️  Installing GUI support for MSPSim..."
echo

echo "📦 Current Java installation:"
java -version
echo

echo "📋 Installing OpenJDK with GUI support..."
echo "This will add the GUI libraries needed for MSPSim's interface"

# Install the full OpenJDK JRE (not headless)
echo "sudo apt-get update"
echo "sudo apt-get install -y openjdk-11-jre"

echo
echo "🔧 Alternative: Install required GUI libraries manually:"
echo "sudo apt-get install -y libxtst6 libxrender1 libxi6 libxtst6"

echo
echo "🎯 After installation, you can run:"
echo "  ant runsort          # With GUI"
echo "  ant runsort-headless # Without GUI (console only)"

echo
echo "🖼️  The GUI will show:"
echo "  - Real-time energy consumption graphs"
echo "  - Battery level indicators"
echo "  - Serial output windows"
echo "  - CPU state visualization"
echo "  - Memory usage displays"

echo
echo "⚡ Your energy integration will work in both modes:"
echo "  - GUI mode: Visual energy monitoring"
echo "  - Headless mode: CSV logs only"

echo
echo "🔋 Run this command to install GUI support:"
echo "sudo apt-get update && sudo apt-get install -y openjdk-11-jre"