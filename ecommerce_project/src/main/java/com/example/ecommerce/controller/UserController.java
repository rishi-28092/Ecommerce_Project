package com.example.ecommerce.controller;

import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.OrderItem;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/user")
@SessionAttributes("cart")
public class UserController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private OrderRepository orderRepository;

    @ModelAttribute("cart")
    public List<Product> cart() {
        return new ArrayList<>();
    }

    @GetMapping("/dashboard")
    public String userDashboard(Model model, HttpSession session) {
        model.addAttribute("products", productRepository.findAll());
        return "user-dashboard";
    }

    @GetMapping("/add-to-cart/{id}")
    public String addToCart(@PathVariable Long id, @ModelAttribute("cart") List<Product> cart,
                            HttpSession session, RedirectAttributes redirectAttributes) {
        Product product = productRepository.findById(id).orElse(null);

        if (product != null) {
            Map<Long, Integer> reservedStock = (Map<Long, Integer>) session.getAttribute("reservedStock");
            if (reservedStock == null) reservedStock = new HashMap<>();

            int alreadyReserved = reservedStock.getOrDefault(product.getId(), 0);

            if (alreadyReserved < product.getStock()) {
                cart.add(product);
                reservedStock.put(product.getId(), alreadyReserved + 1);
                session.setAttribute("reservedStock", reservedStock);
                redirectAttributes.addFlashAttribute("success", "Product added to cart.");
            } else {
                redirectAttributes.addFlashAttribute("error", "Product is out of stock.");
            }
        }
        return "redirect:/user/dashboard";
    }

    @GetMapping("/cart")
    public String viewCart(@ModelAttribute("cart") List<Product> cart, Model model) {
        double total = cart.stream().mapToDouble(Product::getPrice).sum();
        model.addAttribute("cart", cart);
        model.addAttribute("total", total);
        return "cart";
    }

    @GetMapping("/cart/remove/{index}")
    public String removeItem(@PathVariable int index, @ModelAttribute("cart") List<Product> cart,
                             HttpSession session) {
        if (index >= 0 && index < cart.size()) {
            Product removedProduct = cart.remove(index);

            Map<Long, Integer> reservedStock = (Map<Long, Integer>) session.getAttribute("reservedStock");
            if (reservedStock == null) reservedStock = new HashMap<>();

            Long productId = removedProduct.getId();
            int reservedQty = reservedStock.getOrDefault(productId, 0);

            if (reservedQty > 0) reservedStock.put(productId, reservedQty - 1);

            session.setAttribute("reservedStock", reservedStock);
        }
        return "redirect:/user/cart";
    }

    @GetMapping("/checkout")
    public String checkout(@ModelAttribute("cart") List<Product> cart,
                           Model model, org.springframework.security.core.Authentication authentication,
                           SessionStatus sessionStatus) {
        if (cart.isEmpty()) {
            model.addAttribute("message", "Cart is empty!");
            return "redirect:/user/cart";
        }

        com.example.ecommerce.model.Order order = new Order();
        order.setUserEmail(authentication.getName());
        order.setOrderDate(LocalDateTime.now());
        double total = 0;

        for (Product p : cart) {
            Product dbProduct = productRepository.findById(p.getId()).orElse(null);
            if (dbProduct == null || dbProduct.getStock() < 1) {
                model.addAttribute("message", "Insufficient stock for: " + p.getName());
                return "redirect:/user/cart";
            }

            dbProduct.setStock(dbProduct.getStock() - 1);
            productRepository.save(dbProduct);

            OrderItem item = new OrderItem();
            item.setProduct(dbProduct);
            item.setPrice(dbProduct.getPrice());
            item.setQuantity(1);
            item.setOrder(order);
            total += dbProduct.getPrice();
            order.getItems().add(item);
        }

        order.setTotalAmount(total);
        orderRepository.save(order);

        sessionStatus.setComplete();

        emailService.sendOrderEmail(authentication.getName(), "Order Confirmation - E-commerce", "Thank you for your order! Order ID: " + order.getId());

        return "redirect:/user/orders";
    }

    @GetMapping("/orders")
    public String userOrders(Model model, org.springframework.security.core.Authentication authentication) {
        List<com.example.ecommerce.model.Order> orders = orderRepository.findByUserEmail(authentication.getName());
        model.addAttribute("orders", orders);
        return "order-histroy";
    }
}
