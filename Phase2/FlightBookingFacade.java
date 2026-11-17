public class FlightBookingFacade {
    private final SeatInventorySystem seatSystem;
    private final BaggagePricingEngine baggageEngine;
    private final CurrencyConverter currencyConverter;
    private final PaymentProcessor paymentProcessor;
    private final TicketIssuer ticketIssuer;
    public FlightBookingFacade() {
        this.seatSystem = new SeatInventorySystem();
        this.baggageEngine = new BaggagePricingEngine();
        this.currencyConverter = new CurrencyConverter();
        this.paymentProcessor = new PaymentProcessor();
        this.ticketIssuer = new TicketIssuer();
    }
    public String bookFlight(String passengerName,
                             String flightCode,
                             String seatNumber,
                             double baseFareUSD,
                             int bagCount,
                             double bagWeightKg,
                             String currency,
                             String cardNumber) {
        return bookFlight(passengerName, flightCode, seatNumber, baseFareUSD,
                bagCount, bagWeightKg, currency, cardNumber, "STANDARD");
    }

    public String bookFlight(String passengerName,
                             String flightCode,
                             String seatNumber,
                             double baseFareUSD,
                             int bagCount,
                             double bagWeightKg,
                             String currency,
                             String cardNumber,
                             String loyaltyTier) {
        boolean isSilver = "SILVER".equalsIgnoreCase(loyaltyTier);
        boolean isGold = "GOLD".equalsIgnoreCase(loyaltyTier);

        String lockToken = null;
        try {
            seatSystem.connectToReservationServer();
            lockToken = seatSystem.lockSeat(flightCode, seatNumber, passengerName);
            double baggageFeeUSD = 0.0;
            if (!isSilver && !isGold) {
                baggageFeeUSD = baggageEngine.calculateBaggageFeeUSD(bagCount, bagWeightKg);
            }
            double totalUSD = baseFareUSD + baggageFeeUSD;

            double convertedTotal = currencyConverter.convertFromUSD(totalUSD, currency);

            paymentProcessor.initializeGateway();
            if (isSilver) {
                paymentProcessor.applyPromoCode("SILVER-FREE-BAG");
            }
            String receiptId = paymentProcessor.chargeCard(cardNumber, convertedTotal, currency);

            return ticketIssuer.generateTicket(passengerName, flightCode, lockToken, receiptId);
        } catch (RuntimeException ex) {
            if (lockToken != null) {
                seatSystem.releaseSeatLock(lockToken);
            }
            System.out.println("[Facade] Booking failed: " + ex.getMessage());
            return "BOOKING_FAILED: " + ex.getMessage();
        } finally {
            seatSystem.disconnect();
        }
    }

}

