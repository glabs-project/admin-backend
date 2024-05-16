package com.glabs.entities.user.services;


import com.glabs.models.User;
import com.glabs.payload.request.LoginRequest;
import com.glabs.payload.request.UpdateUserRequest;
import com.glabs.payload.response.JwtResponse;
import com.glabs.payload.response.MessageResponse;
import com.glabs.repositories.UserRepository;
import com.glabs.security.jwt.JwtUtils;
import com.glabs.security.services.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.RequestBody;

import java.beans.PropertyDescriptor;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    public ResponseEntity<?> signIn(@Valid @RequestBody LoginRequest loginRequest, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return sendBindingResult(bindingResult);
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new JwtResponse(jwt,
                userDetails.getId(),
                userDetails.getUsername(),
                roles));
    }

    public ResponseEntity<List<User>> getAll() {
        List<User> users = userRepository.findAll();
        return ResponseEntity.ok().body(users);
    }

    public ResponseEntity<?> getUserById(String id) {
        Optional<User> optionalUser = userRepository.findById(id);
        if (optionalUser.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().body(optionalUser.get());

    }

    public ResponseEntity<?> updateUser(String id, UpdateUserRequest updateUserRequest) {

        ResponseEntity<?> validationResponse = updateUserRequest.validateFields();
        if (validationResponse != null) {
            return validationResponse;
        }

        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        BeanUtils.copyProperties(updateUserRequest, user, getNullPropertyNames(updateUserRequest));

        userRepository.save(user);

        return ResponseEntity.ok().body(user);
    }

    public ResponseEntity<?> deleteUser(String id) {
        Optional<User> optionalUser = userRepository.findById(id);
        if (optionalUser.isPresent()) {
            userRepository.deleteById(id);
            return ResponseEntity.ok().body(new MessageResponse("id: " + id + " success delete"));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    private String[] getNullPropertyNames(UpdateUserRequest source) {
        final BeanWrapper src = new BeanWrapperImpl(source);
        Set<String> emptyNames = new HashSet<>();
        for (PropertyDescriptor pd : src.getPropertyDescriptors()) {
            if (src.getPropertyValue(pd.getName()) == null) {
                emptyNames.add(pd.getName());
            }
        }
        String[] result = new String[emptyNames.size()];
        return emptyNames.toArray(result);
    }

    private ResponseEntity<?> sendBindingResult(BindingResult bindingResult) {
        List<Map<String, String>> errors = bindingResult.getAllErrors().stream()
                .map(error -> {
                    Map<String, String> errorDetails = new HashMap<>();
                    if (error instanceof FieldError) {
                        FieldError fieldError = (FieldError) error;
                        errorDetails.put("field", fieldError.getField());
                    }
                    errorDetails.put("defaultMessage", error.getDefaultMessage());
                    return errorDetails;
                })
                .collect(Collectors.toList());

        return ResponseEntity.badRequest().body(errors);
    }
}
