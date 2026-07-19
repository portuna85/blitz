package com.blitz.config.auth;

import com.blitz.config.auth.dto.OAuthAttributes;
import com.blitz.config.auth.dto.SessionUser;
import com.blitz.domain.user.User;
import com.blitz.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpSession;
import java.util.Collections;

@RequiredArgsConstructor
@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private final UserRepository userRepository;
    private final HttpSession httpSession;
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String userNameAttributeName = userRequest.getClientRegistration().getProviderDetails()
                .getUserInfoEndpoint().getUserNameAttributeName();

        OAuthAttributes attributes = OAuthAttributes.of(registrationId, userNameAttributeName, oAuth2User.getAttributes());

        User user = saveOrUpdate(attributes);
        httpSession.setAttribute(SessionUser.SESSION_ATTRIBUTE, new SessionUser(user));

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority(user.getRoleKey())),
                attributes.getAttributes(),
                attributes.getNameAttributeKey());
    }


    private User saveOrUpdate(OAuthAttributes attributes) {
        return userRepository.findByProviderAndProviderId(attributes.getProvider(), attributes.getProviderId())
                .map(user -> saveUpdatedUser(user, attributes))
                .orElseGet(() -> saveNewUser(attributes));
    }

    private User saveNewUser(OAuthAttributes attributes) {
        try {
            return userRepository.save(attributes.toEntity());
        } catch (DataIntegrityViolationException concurrentInsert) {
            return userRepository.findByProviderAndProviderId(attributes.getProvider(), attributes.getProviderId())
                    .map(user -> saveUpdatedUser(user, attributes))
                    .orElseThrow(() -> concurrentInsert);
        }
    }

    private User saveUpdatedUser(User user, OAuthAttributes attributes) {
        return userRepository.save(user.update(
                attributes.getName(),
                attributes.getEmail(),
                attributes.getPicture()));
    }
}
