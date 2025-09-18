/**
 * Copyright (c) 2007, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the Institute nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * This file is part of MSPSim.
 *
 * -----------------------------------------------------------------
 *
 * MSP430
 *
 * Author  : Joakim Eriksson
 * Created : Sun Oct 21 22:00:00 2007
 */

package se.sics.mspsim.core;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import se.sics.mspsim.chip.M25P80;
import se.sics.mspsim.profiler.SimpleProfiler;
import se.sics.mspsim.util.ArrayUtils;
import se.sics.mspsim.util.ComponentRegistry;
import se.sics.mspsim.util.MapTable;
import se.sics.mspsim.util.EnergyConfig;


public class MSP430 extends MSP430Core {
  int DEBUGGING_LEVEL = 0;

  private int[] execCounter;
  private int[] trace;
  private int tracePos;
  
  private boolean debug = false;
  private boolean running = false;
  private boolean isBreaking = false;
  private double rate = 2.0;

  // Debug time - measure cycles
  private long lastCycles = 0;
  private long lastCpuCycles = 0;
  private long time;
  private long nextSleep = 0;
  private long nextOut = 0;
  
  private double lastCPUPercent = 0d;

  private DisAsm disAsm;

  private long executionStartTime = 0;
  private long totalExecutionTime = 0;
  private boolean timingActive = false;
  private int numberOfCheckpoints = 0;
  public String outFile= "";
  private String strategy = "";

  // Energy monitoring
  private InstructionEnergyMonitor energyMonitor;

  private SimEventListener[] simEventListeners;

  // Add this field to track checkpoint events
  private boolean checkpointPending = false;

  /**
   * Creates a new <code>MSP430</code> instance.
   *
   */
  public MSP430(int type, ComponentRegistry registry, MSP430Config config) {
    super(type, registry, config);
    disAsm = new DisAsm();

    // Initialize energy monitoring
    initializeEnergyMonitoring();
  }

  /**
   * Initialize energy monitoring system
   */
  private void initializeEnergyMonitoring() {
    try {
      EnergyConfig config = EnergyConfig.loadFromEnv();
      energyMonitor = new InstructionEnergyMonitor(config);
      setEnergyListener(energyMonitor);
      System.out.println("Energy monitoring initialized with " + config.getTotalEnergyNanoJoules() + " nJ total energy");
    } catch (Exception e) {
      System.err.println("Failed to initialize energy monitoring: " + e.getMessage());
      energyMonitor = null;
    }
  }

  public double getCPUPercent() {
    // Use energy monitor if available, otherwise fall back to old system
    if (energyMonitor != null) {
      return energyMonitor.getBatteryPercentage();
    }
    return lastCPUPercent;
  }

  public DisAsm getDisAsm() {
    return disAsm;
  }

  public void cpuloop() throws EmulationException {
    if (isRunning()) {
      throw new IllegalStateException("already running");
    }
    setRunning(true);
    try {
        startTiming();
        // ??? - power-up  should be executed?!
        time = System.currentTimeMillis();
        run();
    } finally {
        pauseTiming();
        setRunning(false);
    }
  }

  int batchCycles = 0;
  // // Globals
  double batteryPercent = 0; // proxy for energy
  double prevBatteryPercent = batteryPercent;
  boolean usePeriodic = true;
  long nextSampleCycle = 0;
  long samplingInterval = 1000; // will adapt based on battery
  double highThreshold = 50.0;   // Above this: periodic, maybe skip
  double mediumThreshold = 40.0; // Between: periodic, no skip
  double lowThreshold = 20.0;    // Below: JIT
  private void run() throws EmulationException {
    try {
        while (!isStopping) {
            // First execute some instructions to accumulate cycles
            int pc = emulateOP(-1);
            if (pc >= 0) {
                if (execCounter != null) {
                    execCounter[pc]++;
                }
                if (trace != null) {
                    trace[tracePos++] = pc;
                    if (tracePos >= trace.length) {
                        tracePos = 0;
                    }
                }
                // Debug information
                if (debug) {
                    if (servicedInterrupt >= 0) {
                        disAsm.disassemble(pc, memory, reg, servicedInterrupt);
                    } else {
                        disAsm.disassemble(pc, memory, reg);
                    }
                }
            }

            if (strategy.equals("proposed")){
              // // Check CPU metrics periodically
              if (cycles > nextOut) {
                  // This call updates lastCPUPercent
                  printCPUSpeed(reg[PC]);
                  nextOut = cycles + 1000;
                  // double cpuPercent = getCPUPercent();
                  // System.out.println("Saving state to flash: " + cpuPercent + "%");
                  // saveStateToFlash();
                  // Now check the CPU percentage for battery simulation
                  double cpuPercent = getCPUPercent();
                  if (cpuPercent > highThreshold) {
                      batchCycles +=1000; // Reset batch cycles if battery is sufficient
                      System.out.println("Battery sufficient: " + cpuPercent + "%");
                  } else if (cpuPercent < highThreshold && cpuPercent > lowThreshold) {
                      if (batchCycles > 25000) {
                          System.out.println("Saving state to flash: " + cpuPercent + "%");
                          saveStateToFlash();
                          batchCycles = 0;
                      }
                      batchCycles += 1000; // Accumulate cycles for potential save
                  } else if (cpuPercent < lowThreshold && cpuPercent > 0) {
                      System.out.println("Saving state to flash: " + cpuPercent + "%");
                      saveStateToFlash();
                  }
                }

            } else if (strategy.equals("jit")) {
              if (cycles > nextOut) {
                // This call updates lastCPUPercent
                printCPUSpeed(reg[PC]);
                nextOut = cycles + 100; // change this value for sensing battery at different time.
                double cpuPercent = getCPUPercent();
                if (cpuPercent <20) {
                  System.out.println("Battery critical: " + cpuPercent + "%, saving state to flash");
                  saveStateToFlash();
                }
            }
            }else if (strategy.equals("periodic")){
              if (cycles > nextOut) {
                // This call updates lastCPUPercent
                printCPUSpeed(reg[PC]);
                nextOut = cycles + 1000; // change this value for sensing battery at different time.
                double cpuPercent = getCPUPercent();
                saveStateToFlash();
            }
            } else if (strategy.equals("adaptive")){
              if (cycles >  nextOut) {
              if (cycles > nextSampleCycle) {
                  printCPUSpeed(reg[PC]);
                  nextOut = cycles + 1000; 
                  prevBatteryPercent = batteryPercent;
                  batteryPercent = getCPUPercent();
                  double deltaBattery = batteryPercent - prevBatteryPercent;
                  // Update checkpointing mode
                  if (batteryPercent > highThreshold) {
                      usePeriodic = true;
                      // Checkpoint skipping
                      if (deltaBattery >= 0) {
                          System.out.println("Battery stable/increasing (" + batteryPercent + "%), skipping checkpoint");
                          // Skip checkpoint
                      } else {
                          System.out.println("Battery dropping slowly, doing periodic checkpoint");
                          saveStateToFlash(); // Not skipping
                      }
                  } else if (batteryPercent > mediumThreshold) {
                      usePeriodic = true;
                      System.out.println("Medium battery (" + batteryPercent + "%), saving checkpoint");
                      saveStateToFlash();
                  } else if (batteryPercent <= lowThreshold) {
                      usePeriodic = false;
                      System.out.println("Low battery (" + batteryPercent + "%), switching to JIT");
                      saveStateToFlash();
                  }
                  // Adaptive sampling interval (simple version)
                  if (batteryPercent > 70) {
                      samplingInterval = 2000; // sample less frequently
                  } else if (batteryPercent > 40) {
                      samplingInterval = 1000;
                  } else {
                      samplingInterval = 500;  // sample more frequently
                  }
                  nextSampleCycle = cycles + samplingInterval;
              }
            }

            } else if (strategy.equals("none")) {
              // No strategy, just run normally
              if (cycles > nextOut) {
                printCPUSpeed(reg[PC]);
                nextOut = cycles + 1000; // change this value for sensing battery at different time.
              }
            }
            

        }
        isStopping = isBreaking = false;
    } catch (Exception e) {
        System.err.println("Error in CPU execution: " + e.getMessage());
    }
}

private void startTiming() {
    if (!timingActive) {
        executionStartTime = System.nanoTime();
        timingActive = true;
    }
  }

  private void pauseTiming() {
    if (timingActive) {
        totalExecutionTime += System.nanoTime() - executionStartTime;
        timingActive = false;
    }
  }

  public long getExecutionTime() {
    long currentTotal = totalExecutionTime;
    if (timingActive) {
        // Add current active session time if timing is ongoing
        currentTotal += (System.nanoTime() - executionStartTime);
    }
    return currentTotal / 1_000_000; // Convert nanoseconds to milliseconds
  }

  private void saveStateToFlash() {
    M25P80 flash = registry.getComponent(M25P80.class, "xmem");
    if (flash == null) {
        System.err.println("Error: Flash component not available");
        return;
    }

    

    // Create saves directory if it doesn't exist
    File saveDir = new File("saves");
    if (!saveDir.exists()) {
        if (!saveDir.mkdirs()) {
            System.err.println("Error: Could not create saves directory");
            return;
        }
    }

    DataOutputStream out = null;
    try {
        // Save basic CPU state to flash memory
        try {
            // Save Program Counter (PC)
            flash.writeByte(0, (reg[PC] >> 8) & 0xFF);
            flash.writeByte(1, reg[PC] & 0xFF);

            // Save Status Register (SR)
            flash.writeByte(2, (reg[SR] >> 8) & 0xFF);
            flash.writeByte(3, reg[SR] & 0xFF);

            // Save general purpose registers (R4-R15)
            int addr = 4;
            for (int i = 4; i <= 15; i++) {
                flash.writeByte(addr++, (reg[i] >> 8) & 0xFF);
                flash.writeByte(addr++, reg[i] & 0xFF);
            }

            // Set flag indicating state was saved
            memory[0xFFFF] = 0x02;
            System.out.println("CPU state saved to flash successfully");
            checkpointPending = true; // <-- Add this line
        } catch (Exception e) {
            System.err.println("Error writing to flash memory: " + e.getMessage());
            e.printStackTrace();
        }

        // Save to binary file as backup
        String filename = String.format("saves/cpu_state_%08X.bin", reg[PC]);
        out = new DataOutputStream(new FileOutputStream(filename));
        
        // Save PC (Program Counter)
        out.writeInt(reg[PC]);
        
        // Save SR (Status Register)
        out.writeInt(reg[SR]);
        
        // Save all 16 registers
        for (int i = 0; i < 16; i++) {
            out.writeInt(reg[i]);
        }
        
        // Save RAM content
        // Define RAM boundaries - typically from 0x0200 to 0x09FF for MSP430
        // Adjust these values based on your specific MSP430 model
        final int RAM_START = 0x0200;
        final int RAM_END = 0x09FF;
        
        // First write RAM size
        out.writeInt(RAM_END - RAM_START + 1);
        
        // Write RAM start address
        out.writeInt(RAM_START);
        
        // Write RAM contents
        for (int i = RAM_START; i <= RAM_END; i++) {
            out.writeByte(memory[i] & 0xFF);
        }
        
        // Save peripheral registers state if needed
        // This is model-specific, so we'll include a count of peripherals
        out.writeInt(0); // For now, no peripherals saved
        
        // Save interrupt state
        out.writeBoolean(interruptsEnabled);
        out.writeInt(interruptMax);
        out.writeInt(servicedInterrupt);
        
        System.out.println("CPU state and RAM saved to: " + filename);
        System.out.println("PC: 0x" + Integer.toHexString(reg[PC]));
        System.out.println("RAM: " + (RAM_END - RAM_START + 1) + " bytes saved");
    } catch (IOException e) {
        System.err.println("Error saving state to file: " + e.getMessage());
        e.printStackTrace();
    } catch (Exception e) {
        System.err.println("Unexpected error during state save: " + e.getMessage());
        e.printStackTrace();
    } finally {
        // Close resources properly
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                System.err.println("Error closing output stream: " + e.getMessage());
            }
        }
        
        // Log completion status
        System.out.println("State save operation completed");
        numberOfCheckpoints++;
    }
}

  /* Use stepInstructions or stepMicros instead */
  @Deprecated public long step() throws EmulationException {
    return stepMicros(1, 1);
  }

  public long stepInstructions(int count) throws EmulationException {
    if (isRunning()) {
      throw new IllegalStateException("step not possible when CPU is running");
    }
    setRunning(true);
    try {
    while (count > 0 && !isStopping) {
      int pc = emulateOP(-1);
      if (pc >= 0) {
        count--;
        if (execCounter != null) {
          execCounter[pc]++;
        }
        if (trace != null) {
  	  trace[tracePos++] = pc;
          if (tracePos >= trace.length) {
            tracePos = 0;
          }
        }

        // -------------------------------------------------------------------
        // Debug information
        // -------------------------------------------------------------------
        if (debug) {
            if (servicedInterrupt >= 0) {
                disAsm.disassemble(pc, memory, reg, servicedInterrupt);
            } else {
                disAsm.disassemble(pc, memory, reg);
            }
        }
      }
    }
    } finally { 
        setRunning(false);
    }
    isStopping = isBreaking = false;
    return cycles;
  }
  
  /* this represents the micros time that was "promised" last time */
  /* NOTE: this is a delta compared to "current micros" 
   */
  long lastReturnedMicros;
  long lastMicrosCycles;
  boolean microClockReady = false;

  /* when DCO has changed speed, this method will be called */
  protected void dcoReset() {
      microClockReady = false;
  }
  
  /* 
   * Perform a single step (even if in LPM) but no longer than to maxCycles + 1 instr
   * Note: jumpMicros just jump the clock until that time
   * executeMicros also check eventQ, etc and executes instructions
   */
  long maxCycles = 0;
  public long stepMicros(long jumpMicros, long executeMicros) throws EmulationException {
    if (isRunning()) {
      throw new IllegalStateException("step not possible when CPU is running");
    }

    if (jumpMicros < 0) {
      throw new IllegalArgumentException("Can not jump a negative time: " + jumpMicros);
    }
    /* quick hack - if microdelta == 0 => ensure that we have correct zery cycles
     */
    if (!microClockReady) {
      lastMicrosCycles = maxCycles;
    }
    
    // Note: will be reset during DCO-syncs... => problems ???
    lastMicrosDelta += jumpMicros;

    if (microClockReady) {
    /* check that we did not miss any events (by comparing with last return value) */
    maxCycles = lastMicrosCycles + (lastMicrosDelta * dcoFrq) / 1000000;
    if (cpuOff) {
      if(maxCycles > nextEventCycles) {
        /* back this time again... */
        lastMicrosDelta -= jumpMicros;
        printEventQueues(System.out);
        throw new IllegalArgumentException("Jumping to a time that is further than possible in LPM maxCycles:" + 
            maxCycles + " cycles: " + cycles + " nextEventCycles: " + nextEventCycles);
      }
    } else if (maxCycles > cycles) {
      /* back this time again... */
      lastMicrosDelta -= jumpMicros;
      throw new IllegalArgumentException("Jumping to a time that is further than possible not LPM maxCycles:" + 
          maxCycles + " cycles: " + cycles);
    }

    }
    microClockReady = true;

    /* run until this cycle time */
    maxCycles = lastMicrosCycles + ((lastMicrosDelta + executeMicros) * dcoFrq) / 1000000;
    /*System.out.println("Current cycles: " + cycles + " additional micros: " + (jumpMicros) +
          " exec micros: " + executeMicros + " => Execute until cycles: " + maxCycles);*/


    while (cycles < maxCycles || (cpuOff && (nextEventCycles < cycles))) {
        int pc = emulateOP(maxCycles);
        if (pc >= 0) {
            if (execCounter != null) {
                execCounter[pc]++;
            }
            if (trace != null) {
              if (tracePos >= trace.length) {
                tracePos = 0;
              }
              trace[tracePos++] = pc;
            }
            // -------------------------------------------------------------------
            // Debug information
            // -------------------------------------------------------------------
            if (debug) {
              if (servicedInterrupt >= 0) {
                disAsm.disassemble(pc, memory, reg, servicedInterrupt);
              } else {
                disAsm.disassemble(pc, memory, reg);
              }
            }
        }

        if (isStopping) {
            isStopping = false;
            if (cycles < maxCycles || (cpuOff && (nextEventCycles < cycles))) {
                // Did not complete the execution cycle
                lastMicrosDelta -= jumpMicros;
            }
            if (isBreaking) {
                pauseTiming();
                isBreaking = false;
                throw new BreakpointException();
            }
            lastReturnedMicros = 0;
            return 0;
        }
    }

    if (cpuOff && !(interruptsEnabled && servicedInterrupt == -1 && interruptMax >= 0)) {
      lastReturnedMicros = (1000000 * (nextEventCycles - cycles)) / dcoFrq;
    } else {
      lastReturnedMicros = 0;
    }
    
    if(cycles < maxCycles) {
      throw new RuntimeException("cycles < maxCycles : " + cycles + " < " + maxCycles);
    }
    if(lastReturnedMicros < 0) {
      throw new RuntimeException("lastReturnedMicros < 0 : " + lastReturnedMicros);
    }

    return lastReturnedMicros;
  }

  public void stop() {
      pauseTiming();
      isStopping = true;
  }

  public void triggBreakpoint() {
      isBreaking = true;
      stop();
  }

  public int getDCOFrequency() {
    return dcoFrq;
  }
  public int getExecCount(int address) {
    if (execCounter != null) {
      return execCounter[address];
    }
    return 0;
  }

  public void setMonitorExec(boolean mon) {
    if (mon) {
      if (execCounter == null) {
	execCounter = new int[MAX_MEM];
      }
    } else {
      execCounter = null;
    }
  }

  public void setTrace(int size) {
      if (size == 0) {
	  trace = null;
      } else {
	  trace = new int[size];
      }
      tracePos = 0;
  }
  
  public int getBackTrace(int pos) {
      int tPos = tracePos - pos - 1;
      if (tPos < 0) {
	  tPos += trace.length;
      }
      return trace[tPos];
  }
  
  public int getTraceSize() {
      return trace == null ? 0 : trace.length;
  }
    private final java.util.Random rng = new java.util.Random(12345L); // Fixed seed for reproducible battery simulation
    private java.io.PrintWriter batteryLogWriter;
    private long startTime;
    private boolean batteryLoggingInitialized = false;// Track if recharge has happened
    private double baselineLevel = 100.0; // Start with a reasonable baseline battery level
    private boolean failureTriggered = false;

private void printCPUSpeed(int pc) {
    // Initialize battery logging if not already done
    if (!batteryLoggingInitialized) {
        initBatteryLogging();
        batteryLoggingInitialized = true;
    }

    int td = (int)(System.currentTimeMillis() - time);
    long cd = (cycles - lastCycles);
    long cpud = (cpuCycles - lastCpuCycles);

    if (td == 0 || cd == 0) return;

    double currentLevel;

    // Use real energy monitoring if available
    if (energyMonitor != null) {
        currentLevel = energyMonitor.getBatteryPercentage();

        // Check if energy is depleted
        if (energyMonitor.isBatteryDepleted()) {
            System.out.println("ENERGY DEPLETED: System shutdown due to battery exhaustion");
            stop();
            return;
        }
    } else {
        // Fallback to old random battery simulation
        currentLevel = lastCPUPercent;

        // === Core battery behavior ===
        // Random decay between 0.2 and 1.5
        double decay = 0.2 + rng.nextDouble() * (1.5 - 0.2);

        // One-time forced failure between 70,000 and 100,000 cycles
        if (!failureTriggered && cycles >= 70000 && cycles <= 100000) {
            decay += 50 + rng.nextDouble() * 30;  // 50–80%
            failureTriggered = true;
            System.out.println("BATTERY FAILURE: Forced drop triggered");
        }

        // Random heavy drain (8% chance)
        if (rng.nextDouble() < 0.08) {
            decay += 5 + rng.nextDouble() * 7;  // 5–12%
        }

        // Recharge logic
        double recharge = 0;
        if (currentLevel < 40 && rng.nextDouble() < 0.3) {
            recharge = 5 + rng.nextDouble() * 15;  // 5–20%
        } else if (rng.nextDouble() < 0.08) {
            recharge = 2 + rng.nextDouble() * 8;   // 2–10%
        }

        // Add small random noise (simulate jitter)
        double noise = rng.nextGaussian() * 0.7;

        currentLevel = currentLevel - decay + recharge + noise;

        // Clamp value
        if (currentLevel < 0.0) currentLevel = 0.0;
        if (currentLevel > 100.0) currentLevel = 100.0;
    }

    // Critical threshold triggers checkpoint
    if (currentLevel < 15.0 && !checkpointPending) {
        checkpointPending = true;
        System.out.println("BATTERY ALERT: Critical level, checkpoint pending");
    }

    lastCPUPercent = currentLevel;

    // Logging
    if (batteryLogWriter != null) {
        batteryLogWriter.println(
            String.format("%.2f,%d,%b", currentLevel, cycles, checkpointPending)
        );
        batteryLogWriter.flush();
    }

    checkpointPending = false;

    time = System.currentTimeMillis();
    lastCycles = cycles;
    lastCpuCycles = cpuCycles;
}

      private void initBatteryLogging() {
        try {
        // Create outputs directory if it doesn't exist
        java.io.File outputDir = new java.io.File("./outputs");
        if (!outputDir.exists()) {
          outputDir.mkdirs();
        }
        
        // Use fixed filename for consistent output
        String logFileName = "./outputs/battery_log_" + strategy + "_" + System.currentTimeMillis() + ".csv";
        batteryLogWriter = new java.io.PrintWriter(new java.io.FileWriter(logFileName));
        batteryLogWriter.println("BatteryLevel,CPUCycles,Checkpoint");
        startTime = System.currentTimeMillis();
        lastCPUPercent = baselineLevel; // Initialize with baseline
        batteryLoggingInitialized = true;
        outFile = logFileName; // Store the output file name
        System.out.println("Battery logging started to: " + logFileName);
        } catch (java.io.IOException e) {
        System.err.println("Failed to initialize battery logging: " + e.getMessage());
        }
      }

      // Add method to close logging resources properly
      public void shutdown() {
    pauseTiming(); // Ensure timing is stopped

    // Shutdown energy monitoring
    if (energyMonitor != null) {
        energyMonitor.shutdown();
    }

    if (batteryLogWriter != null) {
      try {
        // Add execution time to log before closing
        batteryLogWriter.println("Total Execution Time (ms): " + getExecutionTime());
        batteryLogWriter.close();
        System.out.println("Battery logging completed" + 
            (outFile.isEmpty() ? "" : " saved to " + outFile));
        System.out.println("Total number of checkpoints "+ numberOfCheckpoints);
        System.out.println("Total simulation execution time: " + getExecutionTime() + " ms");
      } catch (Exception e) {
        System.err.println("Error closing battery log: " + e.getMessage());
      }
    } else {
      System.out.println("Total simulation execution time: " + getExecutionTime() + " ms");
    }
  }

  public void generateTrace(PrintStream out) {
    if (profiler != null && out != null) {
      profiler.printStackTrace(out);
    }
  }
  
  public boolean getDebug() {
    return debug;
  }

  public void setDebug(boolean db) {
    debug = db;
  }

  public void setMap(MapTable map) {
    this.map = map;
    /* When we got the map table we can also profile! */
    if (profiler == null) {
      setProfiler(new SimpleProfiler());
      profiler.setCPU(this);
    }
  }

  private void setRunning(boolean running) {
    if (this.running != running) {
      this.running = running;
      if (running) {
          isStopping = false;
          isBreaking = false;
      }
      SimEventListener[] listeners = this.simEventListeners;
      if (listeners != null) {
        SimEvent.Type type = running ? SimEvent.Type.START : SimEvent.Type.STOP;
        SimEvent event = new SimEvent(type);
        for(SimEventListener l : listeners) {
          l.simChanged(event);
        }
      }
    }
  }

  public boolean isRunning() {
    return running;
  }

  public void setStrategy(String strategy) {
    this.strategy = strategy;
    System.out.println("Strategy set to: " + strategy);
  }

  public void setHighThreshold(double highThreshold) {
    this.highThreshold = highThreshold;
  }
  public void setMediumThreshold(double mediumThreshold) {
    this.mediumThreshold = mediumThreshold;
  }
  public void setLowThreshold(double lowThreshold) {
    this.lowThreshold = lowThreshold;
  }

  public double getExecutionRate() {
    return rate;
  }

  public void setExecutionRate(double rate) {
    this.rate = rate;
  }

  public synchronized void addSimEventListener(SimEventListener l) {
    simEventListeners = ArrayUtils.add(SimEventListener.class, simEventListeners, l);
  }

  public synchronized void removeSimEventListener(SimEventListener l) {
    simEventListeners = ArrayUtils.remove(simEventListeners, l);
  }

}
