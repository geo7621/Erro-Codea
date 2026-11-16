package com.obdstudio.app.core

/** The catalog is not a claim that a given adapter/vehicle can access these ECUs. */
data class VehicleModule(
    val id: String,
    val title: String,
    val description: String,
    val group: String
)

object ModuleCatalog {
    val all = listOf(
        VehicleModule("ECM", "Engine / Powertrain", "Emissions OBD-II, sensors and engine faults", "Powertrain"),
        VehicleModule("TCM", "Transmission", "Automatic gearbox and shift electronics", "Powertrain"),
        VehicleModule("AWD", "AWD / Transfer Case", "All-wheel drive and 4WD controls", "Powertrain"),
        VehicleModule("ABS", "ABS / Stability Control", "Brake, ABS, ESC and traction systems", "Chassis"),
        VehicleModule("EPS", "Power Steering", "Electric / hydraulic steering electronics", "Chassis"),
        VehicleModule("SUS", "Suspension", "Air suspension and ride height controls", "Chassis"),
        VehicleModule("SRS", "Airbag / SRS", "Restraint and safety modules", "Safety"),
        VehicleModule("BCM", "Body Control", "Body electronics, lighting and central locking", "Body"),
        VehicleModule("IC", "Instrument Cluster", "Dashboard electronics and instrumentation", "Body"),
        VehicleModule("HVAC", "Climate Control", "Heating, air conditioning and ventilation", "Body"),
        VehicleModule("IMM", "Immobilizer / Security", "Key authorization and security", "Body"),
        VehicleModule("PARK", "Parking Assistance", "Parking sensors and camera controllers", "Body"),
        VehicleModule("TPMS", "Tire Pressure", "Tire-pressure control", "Chassis"),
        VehicleModule("RAD", "Infotainment", "Audio, navigation and communications", "Body"),
        VehicleModule("BMS", "Battery Management", "High-voltage battery electronics", "Electric / Hybrid"),
        VehicleModule("INV", "Inverter / Motor", "Traction motor and inverter", "Electric / Hybrid"),
        VehicleModule("CHG", "Charging", "On-board charger and charging port", "Electric / Hybrid"),
        VehicleModule("GATE", "Diagnostic Gateway", "Gateway and network diagnostics", "Network")
    )
}
