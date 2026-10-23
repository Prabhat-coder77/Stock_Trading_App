package com.trading.userservice.service;

import com.trading.userservice.dto.AuthResponse;
import com.trading.userservice.dto.LoginRequest;
import com.trading.userservice.dto.RegisterRequest;
import com.trading.userservice.dto.UserResponse;
import com.trading.userservice.entity.User;
import com.trading.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String USER_REGISTERED_TOPIC = "user.registered";


    public AuthResponse register(RegisterRequest request){
        log.info("Registering User: {}",request.getEmail());

        if(userRepository.existsByEmail(request.getEmail())){
           throw new RuntimeException("Email already registered" +request.getEmail());
        }
        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setWalletBalance(
                request.getInitialDeposit() != null
                        ? request.getInitialDeposit() : BigDecimal.valueOf(10000) );
        User saveUser = userRepository.save(user);
        log.info("Saved User: {}",saveUser.getId());

        // publish user.registered Event
        Map<String, Object> userRegisteredEvent = new HashMap<>();
        userRegisteredEvent.put("userId",saveUser.getId());
        userRegisteredEvent.put("email",saveUser.getEmail());
        userRegisteredEvent.put("firstName",saveUser.getFirstName());
        userRegisteredEvent.put("lastName",saveUser.getLastName());
        userRegisteredEvent.put("walletBalance",saveUser.getWalletBalance());

        kafkaTemplate.send(USER_REGISTERED_TOPIC, String.valueOf(saveUser.getId()),userRegisteredEvent);

        String token = generateAccessToken(saveUser.getId(),saveUser.getEmail());
        String refreshToken = generateRefreshToken(saveUser.getId());
        return buildAuthResponse(saveUser,token,refreshToken);
    }

    /*
     Login
    */
    public AuthResponse login(LoginRequest request){
        log.info("Login attempt: {}",request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(()-> new RuntimeException("User not found" +request.getEmail()));

        if(!passwordEncoder.matches(request.getPassword(),user.getPassword())){
            throw new RuntimeException("Wrong password");
        }
        log.info("Login Successfully: {}",user.getId());

        String token = generateAccessToken(user.getId(),user.getEmail());
        String refreshToken = generateRefreshToken(user.getId());
        return buildAuthResponse(user,token,refreshToken);
    }

    /**
     * Get User By Id
     * called By other Services via feign
     * @param userId
     * @return
     */
    public UserResponse getUserById(String userId){
        User user = userRepository.findById(userId)
                .orElseThrow(()-> new RuntimeException("User Not Found "+ userId));
        return mapToResponse(user);
    }

    /**
     *Add Funds To Wallet
     * @param userId
     * @param amount
     * @return
     */
    public UserResponse addFunds(String userId, BigDecimal amount){
        User user = userRepository.findById(userId)
                .orElseThrow(()-> new RuntimeException("User Not Found "+ userId));
        user.setWalletBalance(user.getWalletBalance().add(amount));
        User save = userRepository.save(user);

        log.info("Funds Added: {} to the user: {}", amount, userId);
        return mapToResponse(save);
    }

    /**
     * Deduct amount from wallet - called Buy On order
     * @param userId
     * @param amount
     * @return
     */
    public UserResponse deductFunds(String userId,BigDecimal amount){
        User user = userRepository.findById(userId)
                .orElseThrow(()-> new RuntimeException("User Not Found "+ userId));
        if(user.getWalletBalance().compareTo(amount)<0){
            throw new RuntimeException("Insufficient funds");
        }
        user.setWalletBalance(user.getWalletBalance().subtract(amount));
        User save = userRepository.save(user);
        log.info("Funds deducted: {} to the user: {}", amount, userId);
        return mapToResponse(save);
    }

    /**
     * Credit Funds
     * @param userId
     * @param amount
     * @return
     */
    public UserResponse creditFunds(String userId,BigDecimal amount){
        User user = userRepository.findById(userId)
                .orElseThrow(()-> new RuntimeException("User Not Found "+ userId));
        if(user.getWalletBalance().compareTo(amount)<0){
            throw new RuntimeException("Insufficient funds");
        }
        user.setWalletBalance(user.getWalletBalance().add(amount));
        User save = userRepository.save(user);
        log.info("Funds Credited: {} to the user: {}", amount, userId);
        return mapToResponse(save);
    }

    private AuthResponse buildAuthResponse(User user, String token, String refreshToken){
        AuthResponse response = new AuthResponse();

    }

   private UserResponse mapToResponse(User user){
       UserResponse response = new UserResponse();
       response.setId(user.getId());
       response.setEmail(user.getEmail());
       response.setFirstName(user.getFirstName());
       response.setLastName(user.getLastName());
       response.setWalletBalance(user.getWalletBalance());
       response.setStatus(user.getStatus());
       response.setCreatedAt(user.getCreatedAt());

       return response;

   }








}
