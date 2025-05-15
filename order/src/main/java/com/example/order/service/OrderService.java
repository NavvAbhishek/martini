package com.example.order.service;

import com.example.inventory.dto.InventoryDTO;
import com.example.order.common.ErrorOrderResponse;
import com.example.order.common.OrderResponse;
import com.example.order.common.SuccessOrderResponse;
import com.example.order.dto.OrderDTO;
import com.example.order.model.Orders;
import com.example.order.repo.OrderRepo;
import com.example.product.dto.ProductDTO;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

@Service
@Transactional
public class OrderService {
    private final WebClient inventoryWebClient;
    private final WebClient productWebClient;

    @Autowired
    private OrderRepo orderRepo;

    @Autowired
    private ModelMapper modelMapper;

    public OrderService(WebClient inventoryWebClient,WebClient productWebClient, OrderRepo orderRepo, ModelMapper modelMapper) {
        this.inventoryWebClient = inventoryWebClient;
        this.productWebClient = productWebClient;
        this.orderRepo = orderRepo;
        this.modelMapper = modelMapper;
    }

    public List<OrderDTO> getAllOrders(){
        List<Orders> orderList = orderRepo.findAll();
        return modelMapper.map(orderList, new TypeToken<List<OrderDTO>>(){}.getType());
    }

    public OrderResponse saveOrder(OrderDTO orderDTO){

         Integer itemId = orderDTO.getItemId();

         try{
             InventoryDTO inventoryResponse = inventoryWebClient.get()
                     .uri(uriBuilder -> uriBuilder.path("/item/{itemId}").build(itemId))
                     .retrieve()
                     .bodyToMono(InventoryDTO.class)
                     .block();

             Integer productId = inventoryResponse.getProductId();

             ProductDTO productResponse = productWebClient.get()
                     .uri(uriBuilder -> uriBuilder.path("/product/{productId}").build(productId))
                     .retrieve()
                     .bodyToMono(ProductDTO.class)
                     .block();

             assert inventoryResponse != null;
             assert productResponse != null;
             
             if( inventoryResponse.getQuantity() > 0){
                 if(productResponse.getForSale() == 1){
                     orderRepo.save(modelMapper.map(orderDTO, Orders.class));
                 }
                 else{
                     return new ErrorOrderResponse("item is not for sale. Please try later") ;
                 }
                 return new SuccessOrderResponse(orderDTO);
             }
             else{
                 return new ErrorOrderResponse("item not available. Please try later") ;
             }
         }
         catch (WebClientResponseException e){
             if(e.getStatusCode().is5xxServerError()){
                 return new ErrorOrderResponse("item not found") ;
             }
         }

        return null;
    }

    public OrderDTO updateOrder(OrderDTO orderDTO){
        orderRepo.save(modelMapper.map(orderDTO, Orders.class));
        return orderDTO;
    }

    public String deleteOrder(Integer orderId){
        orderRepo.deleteById(orderId);
        return "Order deleted...";
    }

    public OrderDTO getOrderById (Integer orderId){
        Orders order = orderRepo.getOrderById(orderId);
        return modelMapper.map(order, OrderDTO.class);
    }
}
