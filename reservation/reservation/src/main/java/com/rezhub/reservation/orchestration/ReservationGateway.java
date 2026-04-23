package com.rezhub.reservation.orchestration;

/**
 * Application-facing interface to the generic reservation core.
 *
 * submit() acknowledges receipt and returns a handle — booking outcome is delivered
 * asynchronously via the notification path (DelegatingServiceAction / NotificationSender).
 * cancel() dispatches a cancellation command and returns immediately.
 * get() is a synchronous query for reservation details by ID.
 */
public interface ReservationGateway {
    ReservationHandle submit(ReservationSubmission submission);
    void cancel(String reservationId);
    ReservationDetails get(String reservationId);
}
