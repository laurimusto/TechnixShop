package com.service;

import com.dto.user.MeResponseDTO;
import com.dto.user.ProfileDTO;
import com.dto.user.ProfileMapper;
import com.entity.User;
import com.exception.ResourceNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    public UserService(UserRepository userRepository, BCryptPasswordEncoder bCryptPasswordEncoder) {
        this.userRepository = userRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
    }

    @Transactional(readOnly = true)
    public MeResponseDTO getMe(Long userId) {
        return ProfileMapper.toMeResponse(findUser(userId));
    }

    // @Transactional: the loaded User is "managed", so the changes are saved on commit without calling save()
    @Transactional
    public MeResponseDTO updateProfile(Long userId, ProfileDTO profile) {
        User user = findUser(userId);
        ProfileMapper.applyTo(profile, user);
        return ProfileMapper.toMeResponse(user);
    }

    // the id comes from a valid JWT, but the account may have been deleted since it was issued
    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
