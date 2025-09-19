/**
 * Copyright (c) 2024, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Energy Harvesting Module for MSPSim
 * Simulates solar energy harvesting during block execution
 * Integrates with block-level energy monitoring for realistic IoT scenarios
 *
 * Author: MSPSim Energy Extension
 */

package se.sics.mspsim.util;

import java.io.*;
import java.util.*;

public class EnergyHarvester {

    // Solar panel specifications
    private double panelAreaM2;           // Solar panel area in square meters
    private double panelEfficiency;       // Panel efficiency (0.0-1.0)
    private double converterEfficiency;   // DC-DC converter efficiency (0.0-1.0)

    // Environmental parameters
    private double currentIrradianceWM2;  // Current solar irradiance (W/m²)
    private double timeAcceleration;      // Time acceleration factor for simulation

    // Energy harvesting tracking
    private double totalHarvestedNJ;      // Total energy harvested since start
    private double harvestingRateNJPerMS; // Current harvesting rate per millisecond
    private long lastUpdateTimeMS;        // Last time we updated harvesting

    // Block-level harvesting
    private boolean harvestingEnabled;
    private String harvestingProfile;     // "static", "dynamic", "realistic"

    public EnergyHarvester() {
        // Default small IoT solar panel
        this.panelAreaM2 = 0.001;         // 1 cm² panel (typical for IoT devices)
        this.panelEfficiency = 0.20;      // 20% efficiency (typical silicon)
        this.converterEfficiency = 0.85;  // 85% converter efficiency

        // Default conditions
        this.currentIrradianceWM2 = 500.0; // Moderate indoor lighting
        this.timeAcceleration = 1.0;       // Real-time simulation

        // Initialize tracking
        this.totalHarvestedNJ = 0.0;
        this.lastUpdateTimeMS = System.currentTimeMillis();
        this.harvestingEnabled = true;
        this.harvestingProfile = "static";

        updateHarvestingRate();

        System.out.println("Energy Harvester initialized:");
        System.out.println("  Panel: " + (panelAreaM2 * 10000) + " cm², " + (panelEfficiency * 100) + "% efficiency");
        System.out.println("  Current irradiance: " + currentIrradianceWM2 + " W/m²");
        System.out.println("  Harvesting rate: " + (harvestingRateNJPerMS * 1000) + " nJ/second");
    }

    /**
     * Create harvester from environment variables
     */
    public static EnergyHarvester createFromEnv() {
        EnergyHarvester harvester = new EnergyHarvester();

        String panelAreaStr = System.getenv("HARVESTER_PANEL_AREA_CM2");
        if (panelAreaStr != null) {
            try {
                double areaCM2 = Double.parseDouble(panelAreaStr);
                harvester.panelAreaM2 = areaCM2 / 10000.0; // Convert cm² to m²
            } catch (NumberFormatException e) {
                System.err.println("Invalid HARVESTER_PANEL_AREA_CM2: " + panelAreaStr);
            }
        }

        String efficiencyStr = System.getenv("HARVESTER_PANEL_EFFICIENCY");
        if (efficiencyStr != null) {
            try {
                harvester.panelEfficiency = Double.parseDouble(efficiencyStr) / 100.0;
            } catch (NumberFormatException e) {
                System.err.println("Invalid HARVESTER_PANEL_EFFICIENCY: " + efficiencyStr);
            }
        }

        String irradianceStr = System.getenv("HARVESTER_IRRADIANCE_WM2");
        if (irradianceStr != null) {
            try {
                harvester.currentIrradianceWM2 = Double.parseDouble(irradianceStr);
            } catch (NumberFormatException e) {
                System.err.println("Invalid HARVESTER_IRRADIANCE_WM2: " + irradianceStr);
            }
        }

        String profileStr = System.getenv("HARVESTER_PROFILE");
        if (profileStr != null) {
            harvester.harvestingProfile = profileStr;
        }

        harvester.updateHarvestingRate();
        return harvester;
    }

    /**
     * Update the current harvesting rate based on environmental conditions
     */
    private void updateHarvestingRate() {
        // Power = Irradiance × Area × Panel_Efficiency × Converter_Efficiency
        double powerWatts = currentIrradianceWM2 * panelAreaM2 * panelEfficiency * converterEfficiency;

        // Convert to nanojoules per millisecond
        // 1 Watt = 1 Joule/second = 1,000,000,000 nJ/second = 1,000,000 nJ/ms
        harvestingRateNJPerMS = powerWatts * 1_000_000.0 * timeAcceleration;
    }

    /**
     * Calculate energy that will be harvested during a specific time interval
     */
    public double calculateHarvestedEnergyForDuration(long durationMS) {
        if (!harvestingEnabled) {
            return 0.0;
        }

        // For static profile, use current rate
        if ("static".equals(harvestingProfile)) {
            return harvestingRateNJPerMS * durationMS;
        }

        // For dynamic profile, simulate varying conditions
        if ("dynamic".equals(harvestingProfile)) {
            return calculateDynamicHarvesting(durationMS);
        }

        // For realistic profile, use time-of-day patterns
        if ("realistic".equals(harvestingProfile)) {
            return calculateRealisticHarvesting(durationMS);
        }

        return harvestingRateNJPerMS * durationMS;
    }

    /**
     * Simulate dynamic harvesting with random variations
     */
    private double calculateDynamicHarvesting(long durationMS) {
        double totalHarvested = 0.0;
        long stepSize = Math.max(1, durationMS / 100); // Sample every 1% of duration

        for (long t = 0; t < durationMS; t += stepSize) {
            // Add ±20% random variation to irradiance
            double variation = 0.8 + (Math.random() * 0.4); // 0.8 to 1.2
            double instantRate = harvestingRateNJPerMS * variation;
            totalHarvested += instantRate * Math.min(stepSize, durationMS - t);
        }

        return totalHarvested;
    }

    /**
     * Simulate realistic harvesting based on time of day
     */
    private double calculateRealisticHarvesting(long durationMS) {
        Calendar cal = Calendar.getInstance();
        int hourOfDay = cal.get(Calendar.HOUR_OF_DAY);

        // Simple daylight pattern: peak at noon, zero at night
        double daylightFactor;
        if (hourOfDay < 6 || hourOfDay > 20) {
            daylightFactor = 0.0; // Night time
        } else if (hourOfDay >= 11 && hourOfDay <= 13) {
            daylightFactor = 1.0; // Peak sunlight
        } else {
            // Dawn/dusk ramp
            double distanceFromNoon = Math.abs(12 - hourOfDay);
            daylightFactor = Math.max(0.0, 1.0 - (distanceFromNoon - 1.0) / 5.0);
        }

        return harvestingRateNJPerMS * daylightFactor * durationMS;
    }

    /**
     * Update harvester and return energy harvested since last update
     */
    public double updateAndGetHarvestedEnergy() {
        if (!harvestingEnabled) {
            return 0.0;
        }

        long currentTime = System.currentTimeMillis();
        long elapsedMS = currentTime - lastUpdateTimeMS;

        double harvested = calculateHarvestedEnergyForDuration(elapsedMS);
        totalHarvestedNJ += harvested;
        lastUpdateTimeMS = currentTime;

        return harvested;
    }

    /**
     * Predict energy balance for a block execution
     * Returns: { harvestedEnergy, netEnergyChange, isEnergyPositive }
     */
    public EnergyBalance predictEnergyBalanceForBlock(double blockEnergyConsumption, long blockExecutionTimeMS) {
        double harvestedEnergy = calculateHarvestedEnergyForDuration(blockExecutionTimeMS);
        double netEnergyChange = harvestedEnergy - blockEnergyConsumption;
        boolean isEnergyPositive = netEnergyChange > 0;

        return new EnergyBalance(harvestedEnergy, netEnergyChange, isEnergyPositive, blockExecutionTimeMS);
    }

    /**
     * Energy balance result for a block
     */
    public static class EnergyBalance {
        public final double harvestedEnergy;
        public final double netEnergyChange;
        public final boolean isEnergyPositive;
        public final long executionTimeMS;

        public EnergyBalance(double harvested, double netChange, boolean positive, long timeMS) {
            this.harvestedEnergy = harvested;
            this.netEnergyChange = netChange;
            this.isEnergyPositive = positive;
            this.executionTimeMS = timeMS;
        }

        @Override
        public String toString() {
            return String.format("EnergyBalance{harvested=%.2f nJ, net=%.2f nJ, positive=%s, time=%d ms}",
                               harvestedEnergy, netEnergyChange, isEnergyPositive, executionTimeMS);
        }
    }

    // Getters and setters
    public double getCurrentIrradiance() { return currentIrradianceWM2; }
    public void setCurrentIrradiance(double irradiance) {
        this.currentIrradianceWM2 = irradiance;
        updateHarvestingRate();
    }

    public double getPanelAreaCM2() { return panelAreaM2 * 10000; }
    public void setPanelAreaCM2(double areaCM2) {
        this.panelAreaM2 = areaCM2 / 10000.0;
        updateHarvestingRate();
    }

    public double getPanelEfficiency() { return panelEfficiency * 100; }
    public void setPanelEfficiency(double efficiency) {
        this.panelEfficiency = efficiency / 100.0;
        updateHarvestingRate();
    }

    public double getHarvestingRateNJPerSecond() { return harvestingRateNJPerMS * 1000; }
    public double getTotalHarvestedNJ() { return totalHarvestedNJ; }

    public boolean isHarvestingEnabled() { return harvestingEnabled; }
    public void setHarvestingEnabled(boolean enabled) { this.harvestingEnabled = enabled; }

    public String getHarvestingProfile() { return harvestingProfile; }
    public void setHarvestingProfile(String profile) { this.harvestingProfile = profile; }

    /**
     * Get harvester status string
     */
    public String getStatusString() {
        return String.format("Harvester: %.1f cm² panel, %.0f W/m² irradiance, %.2f nJ/s rate, %.2f nJ total",
                           getPanelAreaCM2(), currentIrradianceWM2, getHarvestingRateNJPerSecond(), totalHarvestedNJ);
    }
}