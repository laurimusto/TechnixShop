package com.security;


import com.repository.UserRepository;
import com.service.AuthService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final AuthService authService;
    private final AccessTokenDenylist accessTokenDenylist;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(AuthService authService, AccessTokenDenylist accessTokenDenylist, UserRepository userRepository) {
        this.authService = authService;
        this.accessTokenDenylist = accessTokenDenylist;
        this.userRepository = userRepository;
    }

    // /logout handles its own token parsing: rejecting an expired access token here would
    // stop the request before the controller could revoke the refresh token and clear the cookie.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().equals(request.getContextPath() + "/logout");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = header.substring(7);
            Claims claims = authService.parseToken(token);

            if (accessTokenDenylist.isRevoked(claims.getId())) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Token has been revoked");
                return;
            }

            String userId = claims.getSubject();

            // A password reset ends every session: tokens issued before the change are rejected,
            // even though their signature and expiry are still valid.
            Instant issuedAt = claims.getIssuedAt().toInstant();
            boolean issuedBeforePasswordChange = userRepository.findPasswordChangedAtByUserId(Long.parseLong(userId))
                    .map(issuedAt::isBefore)
                    .orElse(false);
            if (issuedBeforePasswordChange) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Session ended after password change");
                return;
            }

            String userRole = claims.get("role", String.class);

            GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + userRole);
            List<GrantedAuthority> authorities = List.of(authority);

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(auth);

            filterChain.doFilter(request, response);

        } catch (JwtException | IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid or expired token");
        }
    }
}
