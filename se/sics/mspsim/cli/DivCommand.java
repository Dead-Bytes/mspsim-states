package se.sics.mspsim.cli;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import se.sics.mspsim.core.MSP430;
import se.sics.mspsim.util.ComponentRegistry;

public class DivCommand implements CommandBundle {
    private ComponentRegistry registry;

    public void setupCommands(ComponentRegistry registry, CommandHandler handler) {
        this.registry = registry;
        final MSP430 cpu = registry.getComponent(MSP430.class);
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

        // Add load command  
        handler.registerCommand("load", new BasicCommand("load CPU state from file", "<filename>") {
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
    }
}