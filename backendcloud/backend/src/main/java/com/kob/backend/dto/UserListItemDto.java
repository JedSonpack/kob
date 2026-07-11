package com.kob.backend.dto;

import com.kob.backend.pojo.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户列表项响应 DTO（审计任务 5.1）。
 *
 * <p>替代直接返回 User 实体，避免密码字段泄漏（原实现靠 setPassword("") 规避，审计 6.2）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserListItemDto {
    private Integer id;
    private String username;
    private String photo;
    private Integer rating;

    public static UserListItemDto from(User user) {
        return new UserListItemDto(user.getId(), user.getUsername(), user.getPhoto(), user.getRating());
    }
}
