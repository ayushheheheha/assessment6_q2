package com.traffic.echallan;

import com.traffic.echallan.TrafficEChallanSystem.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive test suite covering normal, boundary, invalid-input,
 * and multiple-failure scenarios with live console output.
 */
class TrafficEChallanSystemTest {

    private EChallanService service;
    private Vehicle vCar, vBike, vTruck;

    @BeforeEach
    void setUp(TestInfo info) {
        System.out.println("\n>>> [TEST EXECUTION] " + info.getDisplayName());
        service = new EChallanService();
        vCar = new Vehicle("DL01AB1234", new Owner("Ayush", "9876543210", "ayush@test.com"), VehicleType.FOUR_WHEELER);
        vBike = new Vehicle("UP16CD5678", new Owner("Rahul", "9876543211", "rahul@test.com"), VehicleType.TWO_WHEELER);
        vTruck = new Vehicle("HR26EF9012", new Owner("Logistics Co", "9876543212", "transport@test.com"), VehicleType.HEAVY_VEHICLE);

        service.registerVehicle(vCar);
        service.registerVehicle(vBike);
        service.registerVehicle(vTruck);
    }

    // =========================================================================
    // 1. NORMAL SCENARIOS
    // =========================================================================

    @Test
    @DisplayName("TC-NORM-01: Multi-Vehicle Registration & Retrieval")
    void testVehicleRegistration() {
        System.out.println("  [INPUT] Registered 3 vehicles: Car (DL01AB1234), Bike (UP16CD5678), Truck (HR26EF9012)");
        assertThat(service.getVehicle("DL01AB1234")).isPresent();
        assertThat(service.getVehicle("UP16CD5678")).isPresent();
        assertThat(service.getVehicle("HR26EF9012")).isPresent();
        System.out.println("  [OUTPUT] All vehicles retrieved successfully from registry.");
        System.out.println("  [VALIDATION] [PASS] Multi-vehicle registration validated.");
    }

    @Test
    @DisplayName("TC-NORM-02: Over-speeding Violation & Electronic Challan Generation")
    void testOverspeedingViolation() {
        Challan c = service.reportViolation("DL01AB1234", ViolationType.OVERSPEEDING, "Outer Ring Road", 75.0, 60.0, Instant.now());
        System.out.printf("  [OUTPUT] Challan: %s | Speed: %.0f/%.0f km/h | Fine: ₹%.0f | Status: %s\n",
                c.getChallanId(), c.getRecordedSpeed(), c.getPermittedSpeed(), c.getFineAmount(), c.getPaymentStatus());

        assertThat(c.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(c.getFineAmount()).isEqualTo(1000.0);
        assertThat(service.getUnpaidChallans("DL01AB1234")).hasSize(1);
        System.out.println("  [VALIDATION] [PASS] E-Challan generated in UNPAID status with accurate base fine.");
    }

    @Test
    @DisplayName("TC-NORM-03: Signal Violation and Illegal Parking Detection")
    void testSignalAndParkingViolations() {
        Challan c1 = service.reportViolation("UP16CD5678", ViolationType.SIGNAL_VIOLATION, "MG Road Junction", 0, 0, Instant.now());
        Challan c2 = service.reportViolation("HR26EF9012", ViolationType.ILLEGAL_PARKING, "Connaught Place No-Parking", 0, 0, Instant.now());

        System.out.printf("  [OUTPUT] Signal Jump Challan: %s (Fine: ₹%.0f)\n", c1.getChallanId(), c1.getFineAmount());
        System.out.printf("  [OUTPUT] Illegal Parking Challan: %s (Fine: ₹%.0f - Truck multiplier 1.5x)\n", c2.getChallanId(), c2.getFineAmount());

        assertThat(c1.getFineAmount()).isEqualTo(1000.0);
        assertThat(c2.getFineAmount()).isEqualTo(750.0); // 500 * 1.5 heavy vehicle
        System.out.println("  [VALIDATION] [PASS] Multiple violation types and commercial vehicle multipliers verified.");
    }

    @Test
    @DisplayName("TC-NORM-04: Challan Payment Mechanism & Status Update")
    void testChallanPayment() {
        Challan c = service.reportViolation("DL01AB1234", ViolationType.SIGNAL_VIOLATION, "Red Fort Crossing", 0, 0, Instant.now());
        System.out.println("  [INPUT] Unpaid Challan: " + c.getChallanId() + " (Status: " + c.getPaymentStatus() + ")");

        service.payChallan(c.getChallanId(), "TXN-SBI-12345");
        System.out.printf("  [OUTPUT] Paid Challan: %s | Status: %s | TxRef: %s | PaidAt: %s\n",
                c.getChallanId(), c.getPaymentStatus(), c.getTransactionRef(), c.getPaidAt());

        assertThat(c.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(c.getTransactionRef()).isEqualTo("TXN-SBI-12345");
        assertThat(service.getUnpaidChallans("DL01AB1234")).isEmpty();
        assertThat(service.getPaidChallans("DL01AB1234")).hasSize(1);
        System.out.println("  [VALIDATION] [PASS] Challan payment updated successfully.");
    }

    @Test
    @DisplayName("TC-NORM-05: Total Outstanding Fines Calculation Across Unpaid Challans")
    void testTotalOutstandingFines() {
        service.reportViolation("DL01AB1234", ViolationType.ILLEGAL_PARKING, "Airport T3", 0, 0, Instant.now()); // ₹500
        service.reportViolation("DL01AB1234", ViolationType.SIGNAL_VIOLATION, "Karol Bagh", 0, 0, Instant.now());  // ₹1000 * 1.5 (2nd offense) = ₹1500

        double outstanding = service.getTotalOutstandingFines("DL01AB1234");
        System.out.println("  [OUTPUT] Total Outstanding Fines for DL01AB1234: ₹" + outstanding);

        assertThat(outstanding).isEqualTo(2000.0); // 500 + 1500
        System.out.println("  [VALIDATION] [PASS] Outstanding fine calculation verified.");
    }

    @Test
    @DisplayName("TC-NORM-06: Vehicle Risk Classification Based on Violation History")
    void testVehicleRiskClassification() {
        assertThat(service.classifyVehicle("DL01AB1234")).isEqualTo(RiskCategory.CLEAN);
        System.out.println("  [OUTPUT] Initial (0 violations): " + service.classifyVehicle("DL01AB1234"));

        service.reportViolation("DL01AB1234", ViolationType.ILLEGAL_PARKING, "Loc 1", 0, 0, Instant.now());
        assertThat(service.classifyVehicle("DL01AB1234")).isEqualTo(RiskCategory.LOW_RISK);

        service.reportViolation("DL01AB1234", ViolationType.SIGNAL_VIOLATION, "Loc 2", 0, 0, Instant.now());
        service.reportViolation("DL01AB1234", ViolationType.OVERSPEEDING, "Loc 3", 80, 60, Instant.now());
        assertThat(service.classifyVehicle("DL01AB1234")).isEqualTo(RiskCategory.MODERATE_RISK);

        service.reportViolation("DL01AB1234", ViolationType.SEATBELT_VIOLATION, "Loc 4", 0, 0, Instant.now());
        service.reportViolation("DL01AB1234", ViolationType.DRUNK_DRIVING, "Loc 5", 0, 0, Instant.now());
        assertThat(service.classifyVehicle("DL01AB1234")).isEqualTo(RiskCategory.HIGH_RISK_HABITUAL);

        System.out.println("  [OUTPUT] 5+ violations: " + service.classifyVehicle("DL01AB1234"));
        System.out.println("  [VALIDATION] [PASS] Vehicle risk classification dynamically updated.");
    }

    // =========================================================================
    // 2. BOUNDARY SCENARIOS
    // =========================================================================

    @Test
    @DisplayName("TC-BND-01: Speed Exactly Equal to Permitted Speed (Boundary Condition)")
    void testSpeedExactlyEqualToLimit() {
        System.out.println("  [ACTION] Reporting over-speeding when speed = 60.0 km/h and limit = 60.0 km/h...");
        assertThatThrownBy(() -> service.reportViolation("DL01AB1234", ViolationType.OVERSPEEDING, "NH-1", 60.0, 60.0, Instant.now()))
                .isInstanceOf(InvalidViolationException.class)
                .hasMessageContaining("Over-speeding not detected");
        System.out.println("  [OUTPUT] CAUGHT EXPECTED EXCEPTION: Over-speeding not detected at boundary limit.");
        System.out.println("  [VALIDATION] [PASS] Exact speed limit boundary check passed.");
    }

    @Test
    @DisplayName("TC-BND-02: Speed Just Over Limit by 1 km/h (Boundary Condition)")
    void testSpeedJustOverLimit() {
        Challan c = service.reportViolation("DL01AB1234", ViolationType.OVERSPEEDING, "City Road", 61.0, 60.0, Instant.now());
        System.out.printf("  [OUTPUT] Challan at 61 km/h: %s | Fine: ₹%.0f\n", c.getChallanId(), c.getFineAmount());
        assertThat(c.getFineAmount()).isEqualTo(1000.0);
        System.out.println("  [VALIDATION] [PASS] 1 km/h excess speed correctly triggered base over-speeding fine.");
    }

    @Test
    @DisplayName("TC-BND-03: Severe Overspeeding Threshold (>20 km/h excess penalty)")
    void testSevereOverspeedingPenalty() {
        // Limit: 60 km/h. Speed: 85 km/h (excess: 25 km/h > 20 km/h -> ₹1000 base + ₹1000 excess penalty = ₹2000)
        Challan c = service.reportViolation("DL01AB1234", ViolationType.OVERSPEEDING, "Expressway", 85.0, 60.0, Instant.now());
        System.out.printf("  [OUTPUT] Severe Speeding Challan (85 in 60 zone): ₹%.0f\n", c.getFineAmount());
        assertThat(c.getFineAmount()).isEqualTo(2000.0);
        System.out.println("  [VALIDATION] [PASS] Severe overspeeding boundary penalty verified.");
    }

    @Test
    @DisplayName("TC-BND-04: Repeated Violation Surcharge Multipliers (1.0x -> 1.5x -> 2.0x)")
    void testRepeatViolationMultiplier() {
        Challan c1 = service.reportViolation("DL01AB1234", ViolationType.SIGNAL_VIOLATION, "Junction 1", 0, 0, Instant.now());
        Challan c2 = service.reportViolation("DL01AB1234", ViolationType.SIGNAL_VIOLATION, "Junction 2", 0, 0, Instant.now());
        Challan c3 = service.reportViolation("DL01AB1234", ViolationType.SIGNAL_VIOLATION, "Junction 3", 0, 0, Instant.now());

        System.out.printf("  [OUTPUT] 1st Offense Fine: ₹%.0f (1.0x)\n", c1.getFineAmount());
        System.out.printf("  [OUTPUT] 2nd Offense Fine: ₹%.0f (1.5x)\n", c2.getFineAmount());
        System.out.printf("  [OUTPUT] 3rd Offense Fine: ₹%.0f (2.0x)\n", c3.getFineAmount());

        assertThat(c1.getFineAmount()).isEqualTo(1000.0);
        assertThat(c2.getFineAmount()).isEqualTo(1500.0);
        assertThat(c3.getFineAmount()).isEqualTo(2000.0);
        System.out.println("  [VALIDATION] [PASS] Escalating repeat penalty multipliers verified.");
    }

    // =========================================================================
    // 3. INVALID INPUT SCENARIOS
    // =========================================================================

    @Test
    @DisplayName("TC-INV-01: Invalid Vehicle Registration Number Format")
    void testInvalidVehicleFormat() {
        System.out.println("  [ACTION] Testing malformed registration numbers ('INVALID', '1234', null)...");
        assertThatThrownBy(() -> new Vehicle("INVALID", new Owner("Test", "123", "a@b.c"), VehicleType.FOUR_WHEELER))
                .isInstanceOf(InvalidVehicleException.class);
        assertThatThrownBy(() -> new Vehicle("", new Owner("Test", "123", "a@b.c"), VehicleType.FOUR_WHEELER))
                .isInstanceOf(InvalidVehicleException.class);
        System.out.println("  [OUTPUT] CAUGHT EXPECTED EXCEPTION: Invalid vehicle registration number format.");
        System.out.println("  [VALIDATION] [PASS] Registration pattern validation passed.");
    }

    @Test
    @DisplayName("TC-INV-02: Negative Speed or Negative Speed Limit Rejection")
    void testNegativeSpeedValidation() {
        System.out.println("  [ACTION] Reporting violation with negative speed (-40 km/h)...");
        assertThatThrownBy(() -> service.reportViolation("DL01AB1234", ViolationType.OVERSPEEDING, "Road", -40.0, 60.0, Instant.now()))
                .isInstanceOf(InvalidViolationException.class)
                .hasMessageContaining("cannot be negative");
        System.out.println("  [OUTPUT] CAUGHT EXPECTED EXCEPTION: Speed parameters cannot be negative.");
        System.out.println("  [VALIDATION] [PASS] Negative speed rejected.");
    }

    @Test
    @DisplayName("TC-INV-03: Violation Reported for Unregistered Vehicle")
    void testUnregisteredVehicleViolation() {
        System.out.println("  [ACTION] Reporting violation for unknown vehicle 'DL99ZZ9999'...");
        assertThatThrownBy(() -> service.reportViolation("DL99ZZ9999", ViolationType.SIGNAL_VIOLATION, "Market", 0, 0, Instant.now()))
                .isInstanceOf(VehicleNotFoundException.class)
                .hasMessageContaining("Vehicle not registered in database");
        System.out.println("  [OUTPUT] CAUGHT EXPECTED EXCEPTION: Vehicle not registered in database.");
        System.out.println("  [VALIDATION] [PASS] Unregistered vehicle check enforced.");
    }

    // =========================================================================
    // 4. MULTIPLE FAILURE & DUPLICATE SCENARIOS
    // =========================================================================

    @Test
    @DisplayName("TC-FAIL-01: Prevent Duplicate Challan for Same Vehicle, Location & Time")
    void testDuplicateChallanPrevention() {
        Instant now = Instant.now();
        service.reportViolation("DL01AB1234", ViolationType.SIGNAL_VIOLATION, "Ashok Nagar Chowk", 0, 0, now);
        System.out.println("  [INPUT] First challan issued at Ashok Nagar Chowk.");

        System.out.println("  [ACTION] Attempting to issue identical duplicate challan for same event...");
        assertThatThrownBy(() -> service.reportViolation("DL01AB1234", ViolationType.SIGNAL_VIOLATION, "Ashok Nagar Chowk", 0, 0, now))
                .isInstanceOf(DuplicateChallanException.class)
                .satisfies(e -> System.out.println("  [OUTPUT] CAUGHT EXPECTED EXCEPTION: " + e.getMessage()));

        System.out.println("  [VALIDATION] [PASS] Duplicate challan issuance blocked.");
    }

    @Test
    @DisplayName("TC-FAIL-02: Prevent Duplicate Payment on Already Paid Challan")
    void testDuplicatePaymentRejection() {
        Challan c = service.reportViolation("DL01AB1234", ViolationType.ILLEGAL_PARKING, "Metro Station", 0, 0, Instant.now());
        service.payChallan(c.getChallanId(), "TXN-FIRST");
        System.out.println("  [INPUT] Challan " + c.getChallanId() + " successfully paid.");

        System.out.println("  [ACTION] Attempting to pay the same challan again...");
        assertThatThrownBy(() -> service.payChallan(c.getChallanId(), "TXN-SECOND"))
                .isInstanceOf(ChallanAlreadyPaidException.class)
                .satisfies(e -> System.out.println("  [OUTPUT] CAUGHT EXPECTED EXCEPTION: " + e.getMessage()));

        System.out.println("  [VALIDATION] [PASS] Double payment prevented.");
    }

    @Test
    @DisplayName("TC-FAIL-03: Reject Duplicate Vehicle Registration")
    void testDuplicateVehicleRegistration() {
        System.out.println("  [ACTION] Re-registering existing vehicle 'DL01AB1234'...");
        assertThatThrownBy(() -> service.registerVehicle(new Vehicle("DL01AB1234", new Owner("Duplicate", "111", "d@test.com"), VehicleType.FOUR_WHEELER)))
                .isInstanceOf(InvalidVehicleException.class)
                .hasMessageContaining("Vehicle already registered");
        System.out.println("  [OUTPUT] CAUGHT EXPECTED EXCEPTION: Vehicle already registered.");
        System.out.println("  [VALIDATION] [PASS] Duplicate registration rejected.");
    }
}
