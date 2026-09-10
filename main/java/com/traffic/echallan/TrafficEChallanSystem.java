package com.traffic.echallan;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real-Time Traffic Violation and E-Challan Management System.
 */
public class TrafficEChallanSystem {

    // =========================================================================
    // ENUMS & DOMAIN MODELS
    // =========================================================================

    public enum VehicleType {
        TWO_WHEELER(1.0), FOUR_WHEELER(1.0), COMMERCIAL(1.2), HEAVY_VEHICLE(1.5);
        public final double fineMultiplier;
        VehicleType(double m) { this.fineMultiplier = m; }
    }

    public enum ViolationType {
        OVERSPEEDING(1000.0, "Over-speeding violation"),
        SIGNAL_VIOLATION(1000.0, "Red light / signal jumping"),
        ILLEGAL_PARKING(500.0, "Parking in unauthorized / no-parking zone"),
        DRUNK_DRIVING(5000.0, "Driving under influence of alcohol"),
        SEATBELT_VIOLATION(500.0, "Driving without seatbelt / helmet");

        public final double baseFine;
        public final String description;
        ViolationType(double fine, String desc) { this.baseFine = fine; this.description = desc; }
    }

    public enum PaymentStatus { UNPAID, PAID }

    public enum RiskCategory {
        CLEAN("0 violations - Excellent driving record"),
        LOW_RISK("1-2 violations - Caution advised"),
        MODERATE_RISK("3-4 violations - Frequent offender"),
        HIGH_RISK_HABITUAL("5+ violations - Habitual traffic offender / License suspension risk");

        public final String description;
        RiskCategory(String desc) { this.description = desc; }
    }

    public record Owner(String name, String phone, String email) {
        public Owner {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Owner name cannot be blank");
            if (phone == null || phone.isBlank()) throw new IllegalArgumentException("Owner contact phone cannot be blank");
        }
    }

    public record Vehicle(String regNumber, Owner owner, VehicleType type) {
        public Vehicle {
            if (regNumber == null || !regNumber.trim().toUpperCase().matches("^[A-Z]{2}[0-9]{2}[A-Z0-9]{1,3}[0-9]{4}$")) {
                throw new InvalidVehicleException("Invalid vehicle registration number format: " + regNumber);
            }
            Objects.requireNonNull(owner, "Vehicle owner cannot be null");
            Objects.requireNonNull(type, "Vehicle type cannot be null");
        }
    }

    public static class Challan {
        private final String challanId, vehicleNumber;
        private final ViolationType violationType;
        private final String location;
        private final Instant timestamp;
        private final double recordedSpeed, permittedSpeed, fineAmount;
        private PaymentStatus paymentStatus = PaymentStatus.UNPAID;
        private Instant paidAt;
        private String transactionRef;

        public Challan(String id, String regNo, ViolationType type, String loc, Instant time, double speed, double limit, double fine) {
            this.challanId = id; this.vehicleNumber = regNo; this.violationType = type;
            this.location = loc; this.timestamp = time; this.recordedSpeed = speed;
            this.permittedSpeed = limit; this.fineAmount = fine;
        }

        public String getChallanId() { return challanId; }
        public String getVehicleNumber() { return vehicleNumber; }
        public ViolationType getViolationType() { return violationType; }
        public String getLocation() { return location; }
        public Instant getTimestamp() { return timestamp; }
        public double getRecordedSpeed() { return recordedSpeed; }
        public double getPermittedSpeed() { return permittedSpeed; }
        public double getFineAmount() { return fineAmount; }
        public synchronized PaymentStatus getPaymentStatus() { return paymentStatus; }
        public synchronized Instant getPaidAt() { return paidAt; }
        public synchronized String getTransactionRef() { return transactionRef; }

        public synchronized void markPaid(String txRef) {
            if (this.paymentStatus == PaymentStatus.PAID) {
                throw new ChallanAlreadyPaidException("Challan '" + challanId + "' has already been paid on " + paidAt);
            }
            this.paymentStatus = PaymentStatus.PAID;
            this.paidAt = Instant.now();
            this.transactionRef = txRef;
        }

        @Override
        public String toString() {
            return String.format("Challan[%s | %s | %s | Fine: ₹%.0f | %s]", challanId, vehicleNumber, violationType, fineAmount, paymentStatus);
        }
    }

    // =========================================================================
    // CUSTOM EXCEPTIONS
    // =========================================================================

    public static class ChallanException extends RuntimeException { public ChallanException(String m) { super(m); } }
    public static class InvalidVehicleException extends ChallanException { public InvalidVehicleException(String m) { super(m); } }
    public static class VehicleNotFoundException extends ChallanException { public VehicleNotFoundException(String m) { super(m); } }
    public static class DuplicateChallanException extends ChallanException { public DuplicateChallanException(String m) { super(m); } }
    public static class ChallanAlreadyPaidException extends ChallanException { public ChallanAlreadyPaidException(String m) { super(m); } }
    public static class ChallanNotFoundException extends ChallanException { public ChallanNotFoundException(String m) { super(m); } }
    public static class InvalidViolationException extends ChallanException { public InvalidViolationException(String m) { super(m); } }

    // =========================================================================
    // CORE E-CHALLAN SERVICE
    // =========================================================================

    public static class EChallanService {
        private final Map<String, Vehicle> vehicleRegistry = new ConcurrentHashMap<>();
        private final Map<String, Challan> challanRegistry = new ConcurrentHashMap<>();
        private final Map<String, List<Challan>> vehicleChallans = new ConcurrentHashMap<>();

        public void registerVehicle(Vehicle v) {
            if (v == null) throw new InvalidVehicleException("Vehicle cannot be null");
            if (vehicleRegistry.containsKey(v.regNumber().toUpperCase())) {
                throw new InvalidVehicleException("Vehicle already registered: " + v.regNumber());
            }
            vehicleRegistry.put(v.regNumber().toUpperCase(), v);
            vehicleChallans.putIfAbsent(v.regNumber().toUpperCase(), new ArrayList<>());
        }

        public Optional<Vehicle> getVehicle(String regNo) {
            return Optional.ofNullable(regNo != null ? vehicleRegistry.get(regNo.trim().toUpperCase()) : null);
        }

        public synchronized Challan reportViolation(String regNo, ViolationType type, String location, double speed, double speedLimit, Instant timestamp) {
            if (regNo == null || regNo.isBlank()) throw new InvalidVehicleException("Vehicle registration number is required");
            String normalizedReg = regNo.trim().toUpperCase();
            Vehicle vehicle = vehicleRegistry.get(normalizedReg);
            if (vehicle == null) throw new VehicleNotFoundException("Vehicle not registered in database: " + normalizedReg);

            if (type == null) throw new InvalidViolationException("Violation type cannot be null");
            if (location == null || location.isBlank()) throw new InvalidViolationException("Violation location is required");
            if (speed < 0 || speedLimit < 0) throw new InvalidViolationException("Speed parameters cannot be negative");

            if (type == ViolationType.OVERSPEEDING && speed <= speedLimit) {
                throw new InvalidViolationException(String.format("Over-speeding not detected: Speed (%.1f km/h) did not exceed limit (%.1f km/h)", speed, speedLimit));
            }

            Instant eventTime = timestamp != null ? timestamp : Instant.now();

            // Prevent duplicate challan for same vehicle, violation type, location within 60 seconds
            List<Challan> history = vehicleChallans.getOrDefault(normalizedReg, Collections.emptyList());
            for (Challan c : history) {
                if (c.getViolationType() == type && c.getLocation().equalsIgnoreCase(location)
                        && Math.abs(c.getTimestamp().toEpochMilli() - eventTime.toEpochMilli()) < 60_000) {
                    throw new DuplicateChallanException(String.format("Duplicate violation detected for vehicle %s at %s. Challan ID '%s' already issued.", normalizedReg, location, c.getChallanId()));
                }
            }

            // Calculate Fine Amount based on Severity + Repeat Offense Multiplier
            double fine = calculateFine(vehicle, type, speed, speedLimit, history.size());
            String challanId = "CH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            Challan challan = new Challan(challanId, normalizedReg, type, location, eventTime, speed, speedLimit, fine);

            challanRegistry.put(challanId, challan);
            vehicleChallans.get(normalizedReg).add(challan);
            return challan;
        }

        public synchronized void payChallan(String challanId, String txRef) {
            if (challanId == null) throw new ChallanNotFoundException("Challan ID cannot be null");
            Challan challan = challanRegistry.get(challanId);
            if (challan == null) throw new ChallanNotFoundException("Challan not found with ID: " + challanId);
            if (txRef == null || txRef.isBlank()) throw new IllegalArgumentException("Transaction reference is required for payment");
            challan.markPaid(txRef);
        }

        public List<Challan> getUnpaidChallans(String regNo) {
            return getHistory(regNo).stream().filter(c -> c.getPaymentStatus() == PaymentStatus.UNPAID).toList();
        }

        public List<Challan> getPaidChallans(String regNo) {
            return getHistory(regNo).stream().filter(c -> c.getPaymentStatus() == PaymentStatus.PAID).toList();
        }

        public double getTotalOutstandingFines(String regNo) {
            return getUnpaidChallans(regNo).stream().mapToDouble(Challan::getFineAmount).sum();
        }

        public List<Challan> getHistory(String regNo) {
            String norm = regNo != null ? regNo.trim().toUpperCase() : "";
            if (!vehicleRegistry.containsKey(norm)) throw new VehicleNotFoundException("Vehicle not found: " + regNo);
            return Collections.unmodifiableList(vehicleChallans.getOrDefault(norm, Collections.emptyList()));
        }

        public RiskCategory classifyVehicle(String regNo) {
            int count = getHistory(regNo).size();
            if (count == 0) return RiskCategory.CLEAN;
            if (count <= 2) return RiskCategory.LOW_RISK;
            if (count <= 4) return RiskCategory.MODERATE_RISK;
            return RiskCategory.HIGH_RISK_HABITUAL;
        }

        private double calculateFine(Vehicle vehicle, ViolationType type, double speed, double limit, int previousViolations) {
            double base = type.baseFine;
            // Over-speeding severity: extra fine if speed excess > 20 km/h
            if (type == ViolationType.OVERSPEEDING && (speed - limit) > 20.0) {
                base += 1000.0;
            }
            // Vehicle type modifier
            base *= vehicle.type().fineMultiplier;
            // Repeat violations penalty: 1st violation = 1.0x, 2nd = 1.5x, 3rd+ = 2.0x
            double repeatMultiplier = previousViolations == 0 ? 1.0 : (previousViolations == 1 ? 1.5 : 2.0);
            return Math.round(base * repeatMultiplier);
        }
    }

    // =========================================================================
    // MAIN DEMONSTRATION RUNNER
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("=== REAL-TIME TRAFFIC VIOLATION & E-CHALLAN SYSTEM ===");
        EChallanService service = new EChallanService();

        Vehicle v1 = new Vehicle("DL01AB1234", new Owner("Ayush", "9876543210", "ayush@test.com"), VehicleType.FOUR_WHEELER);
        service.registerVehicle(v1);
        System.out.println("Registered Vehicle: " + v1.regNumber());

        // 1. Overspeeding violation
        Challan c1 = service.reportViolation("DL01AB1234", ViolationType.OVERSPEEDING, "Highway NH-48", 85, 60, Instant.now());
        System.out.printf("Challan Issued: %s | Fine: ₹%.0f | Status: %s\n", c1.getChallanId(), c1.getFineAmount(), c1.getPaymentStatus());

        // 2. Repeat violation (Signal Jump) with repeat penalty
        Challan c2 = service.reportViolation("DL01AB1234", ViolationType.SIGNAL_VIOLATION, "Connaught Circle", 0, 0, Instant.now());
        System.out.printf("Repeat Challan Issued: %s | Fine: ₹%.0f (50%% repeat penalty applied)\n", c2.getChallanId(), c2.getFineAmount());

        System.out.printf("Outstanding Fines: ₹%.0f | Vehicle Risk: %s\n",
                service.getTotalOutstandingFines("DL01AB1234"), service.classifyVehicle("DL01AB1234"));

        // 3. Payment
        service.payChallan(c1.getChallanId(), "TXN-998877");
        System.out.printf("Challan %s Paid! Remaining Outstanding: ₹%.0f\n", c1.getChallanId(), service.getTotalOutstandingFines("DL01AB1234"));
        System.out.println("System demonstration executed successfully.");
    }
}
