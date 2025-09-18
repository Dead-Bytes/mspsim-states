/**
 * Copyright (c) 2024, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Listener interface for instruction-level energy monitoring
 * Allows tracking of energy consumption per instruction execution
 *
 * Author: MSPSim Energy Extension
 */

package se.sics.mspsim.core;

import se.sics.mspsim.util.ProxySupport;

public interface InstructionEnergyListener {

    /**
     * Called before an instruction is executed
     * @param pc Program counter address
     * @param instruction Raw instruction word
     * @param mnemonic Instruction mnemonic (e.g., "MOV.W", "JEQ")
     * @param cycles Current CPU cycle count
     */
    void beforeInstruction(int pc, int instruction, String mnemonic, long cycles);

    /**
     * Called after an instruction is executed
     * @param pc Program counter address
     * @param instruction Raw instruction word
     * @param mnemonic Instruction mnemonic
     * @param cycles Current CPU cycle count
     * @param energyConsumed Energy consumed by this instruction in nanoJoules
     */
    void afterInstruction(int pc, int instruction, String mnemonic, long cycles, double energyConsumed);

    /**
     * Called when energy is critically low
     * @param remainingPercent Remaining energy as percentage (0-100)
     */
    void onEnergyLow(double remainingPercent);

    /**
     * Called when energy is completely depleted
     */
    void onEnergyDepleted();

    /**
     * Proxy implementation for managing multiple listeners
     */
    public static class Proxy extends ProxySupport<InstructionEnergyListener> implements InstructionEnergyListener {
        public static final Proxy INSTANCE = new Proxy();

        @Override
        public void beforeInstruction(int pc, int instruction, String mnemonic, long cycles) {
            InstructionEnergyListener[] listeners = this.listeners;
            for (InstructionEnergyListener listener : listeners) {
                listener.beforeInstruction(pc, instruction, mnemonic, cycles);
            }
        }

        @Override
        public void afterInstruction(int pc, int instruction, String mnemonic, long cycles, double energyConsumed) {
            InstructionEnergyListener[] listeners = this.listeners;
            for (InstructionEnergyListener listener : listeners) {
                listener.afterInstruction(pc, instruction, mnemonic, cycles, energyConsumed);
            }
        }

        @Override
        public void onEnergyLow(double remainingPercent) {
            InstructionEnergyListener[] listeners = this.listeners;
            for (InstructionEnergyListener listener : listeners) {
                listener.onEnergyLow(remainingPercent);
            }
        }

        @Override
        public void onEnergyDepleted() {
            InstructionEnergyListener[] listeners = this.listeners;
            for (InstructionEnergyListener listener : listeners) {
                listener.onEnergyDepleted();
            }
        }
    }
}