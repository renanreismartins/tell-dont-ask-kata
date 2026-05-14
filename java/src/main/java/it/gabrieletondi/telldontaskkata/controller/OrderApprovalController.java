package it.gabrieletondi.telldontaskkata.controller;

import it.gabrieletondi.telldontaskkata.controller.exception.ApprovedOrderCannotBeRejectedException;
import it.gabrieletondi.telldontaskkata.controller.exception.RejectedOrderCannotBeApprovedException;
import it.gabrieletondi.telldontaskkata.controller.exception.ShippedOrdersCannotBeChangedException;
import it.gabrieletondi.telldontaskkata.controller.request.OrderApprovalRequest;
import it.gabrieletondi.telldontaskkata.domain.Order;
import it.gabrieletondi.telldontaskkata.domain.OrderStatus;
import it.gabrieletondi.telldontaskkata.repository.OrderRepository;

public class OrderApprovalController {
    private final OrderRepository orderRepository;

    public OrderApprovalController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public void run(OrderApprovalRequest request) {
        final Order order = orderRepository.getById(request.getOrderId());

        if (order.getStatus().equals(OrderStatus.SHIPPED)) {
            throw new ShippedOrdersCannotBeChangedException();
        }

        if (request.isApproved() && order.getStatus().equals(OrderStatus.REJECTED)) {
            throw new RejectedOrderCannotBeApprovedException();
        }

        if (!request.isApproved() && order.getStatus().equals(OrderStatus.APPROVED)) {
            throw new ApprovedOrderCannotBeRejectedException();
        }

        order.setStatus(request.isApproved() ? OrderStatus.APPROVED : OrderStatus.REJECTED);
        orderRepository.save(order);
    }
}
