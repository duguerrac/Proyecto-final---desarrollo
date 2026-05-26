package com.smartlogistics.identity.infrastructure.adapter.out.jpa;

import com.smartlogistics.identity.domain.model.Role;
import com.smartlogistics.identity.domain.model.User;

public class UserMapper {

    private UserMapper() {}

    public static UserEntity toEntity(User domain) {
        return new UserEntity(
                domain.getId(),
                domain.getUsername(),
                domain.getPasswordHash(),
                domain.getRole().name(),
                domain.getCreatedAt()
        );
    }

    public static User toDomain(UserEntity entity) {
        return new User(
                entity.getId(),
                entity.getUsername(),
                entity.getPasswordHash(),
                Role.valueOf(entity.getRole()),
                entity.getCreatedAt()
        );
    }
}
