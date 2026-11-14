package com.lifemate.expense.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmsWebhookRequestDTO {

    @NotBlank(message = "SMS sender is required")
    private String sender;

    @NotBlank(message = "SMS body is required")
    private String body;

    private Long accountId;
}