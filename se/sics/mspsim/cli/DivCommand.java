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

public class DivCommand implements CommandBundle {
    private ComponentRegistry registry;
    private boolean isRunning = true;

    public void setupCommands(ComponentRegistry registry, CommandHandler handler) {
        this.registry = registry;
        final MSP430 cpu = registry.getComponent(MSP430.class);
        final M25P80 flash = registry.getComponent(M25P80.class);
        // OperatingModeStatistics stats = new OperatingModeStatistics(cpu);
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
                
                
                int address = 0x1000;
                
                // Save PC (Program Counter)
                flash.writeByte(address, cpu.getPC() & 0xFF);
                flash.writeByte(address + 1, (cpu.getPC() >> 8) & 0xFF);
                flash.writeByte(address + 2, (cpu.getPC() >> 16) & 0xFF);
                flash.writeByte(address + 3, (cpu.getPC() >> 24) & 0xFF);
                address += 4;
                
                // Save all 16 registers
                for (int i = 0; i < 16; i++) {
                    int regValue = cpu.getRegister(i);
                    flash.writeByte(address, regValue & 0xFF);
                    flash.writeByte(address + 1, (regValue >> 8) & 0xFF);
                    flash.writeByte(address + 2, (regValue >> 16) & 0xFF);
                    flash.writeByte(address + 3, (regValue >> 24) & 0xFF);
                    address += 4;
                }
                
                context.out.println("CPU state saved to flash starting at address 0x1000");
                context.out.println("PC: $" + cpu.getAddressAsString(cpu.getPC()));
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