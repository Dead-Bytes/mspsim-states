package se.sics.mspsim.util;

import se.sics.mspsim.chip.M25P80;
import se.sics.mspsim.cli.CommandContext;
import se.sics.mspsim.core.MSP430;

public class DivUtil {
    public static void save_state(CommandContext context, MSP430 cpu ,M25P80 flash) {
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

    }
}
