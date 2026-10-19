package com.codewithsavage.majipoa.admin.customer;

import com.codewithsavage.majipoa.Customer.repository.CustomerRepository;
import com.codewithsavage.majipoa.shared.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class BlockedCustomerFilter extends OncePerRequestFilter {

    private final UserRepository userRepo;
    private final CustomerRepository customerRepo;
    private final CustomerBlockRepository blockRepo;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !"POST".equals(request.getMethod())
                || !(path.startsWith("/api/orders") || path.startsWith("/api/cart"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            boolean blocked = userRepo.findByGoogleSub(auth.getName())
                    .flatMap(u -> customerRepo.findByUserId(u.getId()))
                    .map(c -> blockRepo.existsById(c.getCustomerId()))
                    .orElse(false);
            if (blocked) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Your account is blocked. Please contact support.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}