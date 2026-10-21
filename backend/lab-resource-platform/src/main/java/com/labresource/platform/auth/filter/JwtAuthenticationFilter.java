
package com.labresource.platform.auth.filter;

import com.labresource.platform.auth.service.JwtService;
import com.labresource.platform.user.entity.Role;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.entity.UserRole;
import com.labresource.platform.user.repository.RoleRepository;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.user.repository.UserRoleRepository;
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
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader == null
                || !authorizationHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(7);

        try {
            Claims claims = jwtService.parseClaims(token);
            String email = claims.get("email", String.class);

            if (email != null
                    && SecurityContextHolder.getContext()
                    .getAuthentication() == null) {

                Optional<User> userOptional = userRepository.findByEmail(
                        email.trim().toLowerCase());

                if (userOptional.isPresent()) {
                    User user = userOptional.get();

                    List<GrantedAuthority> authorities =
                            userRoleRepository.findByUserId(user.getId())
                                    .stream()
                                    .map(UserRole::getRoleId)
                                    .map(roleRepository::findById)
                                    .flatMap(Optional::stream)
                                    .map(Role::getName)
                                    .map(roleName ->
                                            new SimpleGrantedAuthority(
                                                    "ROLE_" + roleName))
                                    .map(authority ->
                                            (GrantedAuthority) authority)
                                    .toList();

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    email,
                                    null,
                                    authorities);

                    SecurityContextHolder.getContext()
                            .setAuthentication(authentication);
                } else {
                    SecurityContextHolder.clearContext();
                }
            }

        } catch (JwtException | IllegalArgumentException exception) {
        exception.printStackTrace();
        SecurityContextHolder.clearContext();
    }

        filterChain.doFilter(request, response);
    }
}
