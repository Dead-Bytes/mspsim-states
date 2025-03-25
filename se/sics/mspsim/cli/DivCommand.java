package se.sics.mspsim.cli;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import se.sics.mspsim.chip.M25P80;
import se.sics.mspsim.core.MSP430;
import se.sics.mspsim.util.ComponentRegistry;
import se.sics.mspsim.util.MultiDataSource;
import se.sics.mspsim.util.DataSource;
import se.sics.mspsim.util.OperatingModeStatistics;
import se.sics.mspsim.core.TimeEvent;
import se.sics.mspsim.util.DivUtil;



public class DivCommand implements CommandBundle {
    private ComponentRegistry registry;
    private boolean isRunning = true;
    private DataSource[] sources;

    public void setupCommands(ComponentRegistry registry, CommandHandler handler) {
        this.registry = registry;
        final MSP430 cpu = registry.getComponent(MSP430.class);
        final M25P80 flash = registry.getComponent(M25P80.class);
        OperatingModeStatistics stats = new OperatingModeStatistics(cpu);
        // Add save command
        handler.registerCommand("save", new BasicCommand("save CPU state to file", "<filename>") {
            @Override
            public int executeCommand(CommandContext context) {
                if (context.getArgumentCount() != 1) {
                    context.err.println("Usage: save <filename>");
                    return 1;
                }
                String filename = context.getArgument(0);
                
                try (DataOutputStream out = new DataOutputStream(new FileOutputStream(filename))) {
                    // Save PC (Program Counter)
                    out.writeInt(cpu.getPC());
                    
                    // Save all 16 registers
                    for (int i = 0; i < 16; i++) {
                        out.writeInt(cpu.getRegister(i));
                    }
                    
                    context.out.println("CPU state saved to: " + filename);
                    context.out.println("PC: $" + cpu.getAddressAsString(cpu.getPC()));
                    return 0;
                } catch (IOException e) {
                    context.err.println("Error saving state: " + e.getMessage());
                    return 1;
                }
            }
        });

        handler.registerCommand("savefl", new BasicCommand("save CPU state to flash", "") {
            @Override
            public int executeCommand(CommandContext context) {
                
                context.out.println(flash.getStatus());
                
                DivUtil.save_state(context, cpu, flash);
                
                return 0;
            }
        });
        // //add loadfl command
        handler.registerCommand("loadfl", new BasicCommand("load CPU state from flash", "") {
            @Override
            public int executeCommand(CommandContext context) {
                int address = 0x1000;

                // Load PC (Program Counter)
                int pc = (flash.readByte(address) & 0xFF) |
                         ((flash.readByte(address + 1) & 0xFF) << 8) |
                         ((flash.readByte(address + 2) & 0xFF) << 16) |
                         ((flash.readByte(address + 3) & 0xFF) << 24);
                cpu.setPC(pc);
                address += 4;

                // Load all 16 registers
                for (int i = 0; i < 16; i++) {
                    int regValue = (flash.readByte(address) & 0xFF) |
                                   ((flash.readByte(address + 1) & 0xFF) << 8) |
                                   ((flash.readByte(address + 2) & 0xFF) << 16) |
                                   ((flash.readByte(address + 3) & 0xFF) << 24);
                    cpu.setRegister(i, regValue);
                    address += 4;
                }

                context.out.println("CPU state loaded from flash starting at address 0x1000");
                context.out.println("PC restored to: $" + cpu.getAddressAsString(pc));
                return 0;
            }
        });

        // Add load command  
        handler.registerCommand("load", new BasicCommand("load CPU state from file", "<inf>") {
            @Override
            public int executeCommand(CommandContext context) {
                if (context.getArgumentCount() != 1) {
                    context.err.println("Usage: load <filename>");
                    return 1;
                }
                String filename = context.getArgument(0);
                
                try (DataInputStream in = new DataInputStream(new FileInputStream(filename))) {
                    // First stop CPU if running
                    if (cpu.isRunning()) {
                        cpu.stop();
                    }

                    // Load PC (Program Counter) 
                    int pc = in.readInt();
                    cpu.setPC(pc);

                    // Load all 16 registers
                    for (int i = 0; i < 16; i++) {
                        int value = in.readInt();
                        cpu.setRegister(i, value);
                    }

                    context.out.println("CPU state loaded from: " + filename);
                    context.out.println("PC restored to: $" + cpu.getAddressAsString(pc));
                    return 0;
                } catch (IOException e) {
                    context.err.println("Error loading state: " + e.getMessage());
                    return 1;
                }
            }
        });

        handler.registerCommand("battery", new BasicCommand("show battery statistics", "") {
            @Override
            public int executeCommand(CommandContext context){
                context.out.println("Battery statistics");
                context.out.println("battery: " + cpu.getCPUPercent());
                return 0;
            }
        });


        // // add the command battery that will call the getDoubleValue in the OperateModeStatistics.java
        // handler.registerCommand("battery", new BasicCommand("show battery statistics", "<frequency>") {
        //     @Override
        //     public int executeCommand(CommandContext context) {
        //         if (context.getArgumentCount() != 1) {
        //             context.err.println("Usage: battery <frequency>");
        //             return 1;
        //         }

        //         double frequency = context.getArgumentAsDouble(0);
        //         if (frequency <= 0) {
        //             context.err.println("Frequency must be positive");
        //             return 1;
        //         }

        //         // Initialize data sources if not already done
        //         if (sources == null) {
        //             sources = new DataSource[] {
        //                 stats.getDataSource("CPU", 0),  // CPU mode 0
        //                 // stats.getMultiDataSource("Radio"),  // Radio modes
        //                 // stats.getMultiDataSource("Flash")   // Flash modes
        //             };
        //         }

        //         isRunning = true;
        //         context.out.println("Starting battery statistics at " + frequency + "Hz");
                
        //         // Schedule periodic updates
        //         cpu.scheduleTimeEventMillis(new TimeEvent(0) {
        //             @Override
        //             public void execute(long t) {
        //                 if (!isRunning) return;
                        
        //                 // Schedule next update
        //                 cpu.scheduleTimeEventMillis(this, 1000.0 / frequency);
                        
        //                 // Print values for each data source
        //                 for (int j = 0; j < sources.length; j++) {
        //                     if (j > 0) context.out.print(' ');
        //                     Object s = sources[j];
                            
        //                     if (s instanceof MultiDataSource) {
        //                         MultiDataSource ds = (MultiDataSource) s;
        //                         for (int k = 0, m = ds.getModeMax(); k <= m; k++) {
        //                             if (k > 0) context.out.print(' ');
        //                             double value = ds.getDoubleValue(k);
        //                             context.out.printf("%.2f", value);
        //                         }
        //                     } else if (s instanceof DataSource) {
        //                         double value = ((DataSource)s).getDoubleValue();
        //                         context.out.printf("%.2f", value);
        //                     }
        //                 }
        //                 context.out.println();
        //             }
        //         }, 1000.0 / frequency);
                
        //         return 0;
        //     }
        // });
        // handler.registerCommand("battery", new BasicCommand("show battery in double", "<filename>") {
        //     @Override
        //     public int executeCommand(CommandContext context) {
        //         PrintStream out = context.out;
        //         if (context.getArgumentCount() != 1) {
        //             context.err.println("need frequency to calculate battery");
        //             return 1;
        //         }
        //         double frequency = context.getArgumentAsDouble(0);
        //         cpu.scheduleTimeEventMillis(new TimeEvent(0) {

        //             @Override
        //             public void execute(long t) {
        //               if (isRunning) {
        //                 cpu.scheduleTimeEventMillis(this, 1000.0 / frequency);
        //                 for (int j = 0, n = sources.length; j < n; j++) {
        //                   Object s = sources[j];
        //                   if (j > 0) out.print(' ');
        //                   if (s instanceof MultiDataSource) {
        //                     MultiDataSource ds = (MultiDataSource) s;
        //                     for (int k = 0, m = ds.getModeMax(); k <= m; k++) {
        //                       if (k > 0) out.print(' ');
        //                       out.print(((int) (ds.getDoubleValue(k) * 100.0 + 0.5)) / 100.0);       
        //                     }
        //                   } else {
        //                     out.print( ((int) (((DataSource)s).getDoubleValue() * 100.0 + 0.5)) / 100.0);
        //                   }
        //                 }
        //                 out.println();
        //               }
        //             }
        //           }, 1000.0 / frequency);
        
        //     }
        // });
    }
}