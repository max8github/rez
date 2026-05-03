package com.rezhub.reservation.orchestration;

import java.util.Map;

/**
 * Deterministic application-layer entry point for all booking operations.
 * Resolves BookingContext from origin, selects the correct BookingWorkflow, delegates.
 */
public class BookingApplicationService {

    private final BookingContextResolverAkka contextResolver;
    private final Map<String, BookingWorkflow> workflows;

    public BookingApplicationService(BookingContextResolverAkka contextResolver,
                                     CourtBookingWorkflow courtWorkflow) {
        this.contextResolver = contextResolver;
        this.workflows = Map.of(courtWorkflow.domain(), courtWorkflow);
    }

    public AvailabilityResult checkAvailability(OriginRequestContext origin, BookingIntent intent) {
        BookingContext context = contextResolver.resolve(origin);
        BookingWorkflow workflow = workflowFor(context);
        return workflow.checkAvailability(origin, context, intent);
    }

    public ReservationHandle book(OriginRequestContext origin, BookingIntent intent) {
        BookingContext context = contextResolver.resolve(origin);
        BookingWorkflow workflow = workflowFor(context);
        return workflow.book(origin, context, intent);
    }

    public void cancel(OriginRequestContext origin, CancelIntent intent) {
        BookingContext context = contextResolver.resolve(origin);
        BookingWorkflow workflow = workflowFor(context);
        workflow.cancel(origin, context, intent);
    }

    private BookingWorkflow workflowFor(BookingContext context) {
        BookingWorkflow workflow = workflows.get(context.bookingDomain());
        if (workflow == null) {
            throw new IllegalStateException("No workflow registered for domain: " + context.bookingDomain());
        }
        return workflow;
    }
}
