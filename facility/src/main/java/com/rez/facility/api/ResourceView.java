//package com.rez.facility.api;
//
//import com.rez.facility.domain.Resource;
//import kalix.javasdk.annotations.Query;
//import kalix.javasdk.annotations.Subscribe;
//import kalix.javasdk.annotations.Table;
//import kalix.javasdk.annotations.ViewId;
//import kalix.javasdk.view.View;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.web.bind.annotation.GetMapping;
//
//import java.util.List;
//
//
//@ViewId("shopping-carts")
//@Table("shopping_carts")
//@Subscribe.EventSourcedEntity(value = ResourceEntity.class, ignoreUnknown = true)
//public class ResourceView extends View<Resource> {
//    private static final Logger log = LoggerFactory.getLogger(ResourceView.class);
//
//    @GetMapping("/shopping-carts")
//    @Query("""
//        SELECT * AS shoppingCarts
//          FROM shopping_carts
//      ORDER BY createdAt DESC
//         LIMIT 100
//        """)
//    public ShoppingCarts getShoppingCarts() {
//        return null;
//    }
//
//    @Override
//    public Resource emptyState() {
//        return Resource.initialize();
//    }
//
//    public UpdateEffect<Resource> on(ResourceEvent.ResourceCreated event) {
//        log.info("State: {}\n_Event: {}", viewState(), event);
//        return effects()
//                .updateState(viewState().on(event));
//    }
//
//    public UpdateEffect<Resource> on(ResourceEvent.ResourceCreated event) {
//        log.info("State: {}\n_Event: {}", viewState(), event);
//        return effects()
//                .updateState(viewState().on(event));
//    }
//
//    public UpdateEffect<Resource> on(ResourceEntity.RemovedLineItemEvent event) {
//        log.info("State: {}\n_Event: {}", viewState(), event);
//        return effects()
//                .updateState(viewState().on(event));
//    }
//
//    public UpdateEffect<Resource> on(ResourceEntity.CheckedOutEvent event) {
//        log.info("State: {}\n_Event: {}", viewState(), event);
//        return effects()
//                .updateState(viewState().on(event));
//    }
//
//    public record ShoppingCarts(List<Resource> shoppingCarts) {}
//}
