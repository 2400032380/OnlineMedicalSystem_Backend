# Ayush Medicare Backend (Spring Boot)

This is a Spring Boot backend intended as a replacement for the Node backend.

## Requirements

- Java 17+
- Maven 3.9+

## Run Locally

1. Open terminal in this folder
2. Set environment variables (PowerShell):
   - `$env:PORT="5000"`
   - `$env:CORS_ORIGIN="http://localhost:5173"`
   - `$env:PAYMENT_PROVIDER="manual_upi"`
   - `$env:UPI_ID="yourupi@bank"`
3. Run:
   - `mvn spring-boot:run`

Backend runs on `http://localhost:5000`.

## Endpoints

- `GET /api/health`
- `GET /api/appointments`
- `POST /api/appointments`
- `PATCH /api/appointments/{id}/status`
- `GET /api/calls`
- `POST /api/calls/ring`
- `PATCH /api/calls/{id}/status`
- `GET /api/orders`
- `POST /api/orders`
- `PATCH /api/orders/{id}/status`
- `GET /api/lab-bookings`
- `POST /api/lab-bookings`
- `PATCH /api/lab-bookings/{id}/status`
- `GET /api/prescriptions`
- `POST /api/prescriptions`
- `PATCH /api/prescriptions/{id}/status`
- `POST /api/payments/create-order`
- `POST /api/payments/verify`

## Notes

- Payment mode currently implemented in Spring backend: `manual_upi`.
- Uses in-memory data stores (resets on restart).
- Socket.IO realtime call events are not included in this Spring version.
