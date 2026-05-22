package dev.sportdraft.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (HttpMethod.POST.matches(request.getMethod()) && request.getRequestURI().contains("/bids")) {
            String key = request.getHeader("Idempotency-Key");
            if (key == null || key.isBlank()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Idempotency-Key header is required");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
