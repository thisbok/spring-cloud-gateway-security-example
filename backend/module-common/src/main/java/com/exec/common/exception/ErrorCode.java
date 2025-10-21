package com.exec.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common Errors (1000~1999)
    INTERNAL_SERVER_ERROR(1000, "Internal server error occurred"),
    INVALID_REQUEST_PARAMETER(1001, "Invalid request parameter"),
    INVALID_REQUEST_FORMAT(1002, "Invalid request format"),
    RESOURCE_NOT_FOUND(1003, "Resource not found"),
    UNAUTHORIZED_ACCESS(1004, "Unauthorized access"),
    FORBIDDEN_ACCESS(1005, "Forbidden access"),
    DUPLICATE_RESOURCE(1006, "Duplicate resource"),

    // Authentication Errors (2000~2999)
    INVALID_API_KEY(2000, "Invalid API key"),
    INVALID_SECRET_KEY(2001, "Invalid secret key"),
    INVALID_SIGNATURE(2002, "Invalid signature"),
    EXPIRED_TOKEN(2003, "Token has expired"),
    INVALID_TOKEN(2004, "Invalid token"),
    MISSING_AUTHENTICATION(2005, "Missing authentication information"),
    API_KEY_NOT_FOUND(2006, "API key not found"),

    // Payment Errors (3000~3999)
    PAYMENT_NOT_FOUND(3000, "Payment not found"),
    PAYMENT_ALREADY_PROCESSED(3001, "Payment already processed"),
    INSUFFICIENT_BALANCE(3002, "Insufficient balance"),
    PAYMENT_CANCELLED(3003, "Payment cancelled"),
    PAYMENT_FAILED(3004, "Payment failed"),
    INVALID_PAYMENT_AMOUNT(3005, "Invalid payment amount"),

    // Rate Limiting Errors (4000~4999)
    RATE_LIMIT_EXCEEDED(4000, "Rate limit exceeded"),
    QUOTA_EXCEEDED(4001, "API quota exceeded"),
    CONCURRENT_REQUEST_LIMIT_EXCEEDED(4002, "Concurrent request limit exceeded"),

    // Webhook Errors (5000~5999)
    WEBHOOK_VERIFICATION_FAILED(5000, "Webhook verification failed"),
    WEBHOOK_PROCESSING_FAILED(5001, "Webhook processing failed"),
    WEBHOOK_TIMEOUT(5002, "Webhook timeout"),
    WEBHOOK_RETRY_EXCEEDED(5003, "Webhook retry attempts exceeded"),

    // Database Errors (6000~6999)
    DATABASE_CONNECTION_ERROR(6000, "Database connection error"),
    DATA_INTEGRITY_VIOLATION(6001, "Data integrity violation"),
    OPTIMISTIC_LOCK_FAILURE(6002, "Optimistic lock failure"),

    // External Service Errors (7000~7999)
    FIREBLOCKS_API_ERROR(7000, "Fireblocks API error"),
    FIREBLOCKS_CONNECTION_ERROR(7001, "Fireblocks connection error"),
    EXTERNAL_SERVICE_UNAVAILABLE(7002, "External service unavailable"),

    // Validation Errors (8000~8999)
    VALIDATION_FAILED(8000, "Validation failed"),
    MISSING_REQUIRED_FIELD(8001, "Missing required field"),
    FIELD_LENGTH_EXCEEDED(8002, "Field length exceeded"),
    INVALID_FIELD_FORMAT(8003, "Invalid field format"),
    INVALID_EMAIL_FORMAT(8004, "Invalid email format"),
    INVALID_PHONE_FORMAT(8005, "Invalid phone number format"),
    INVALID_INPUT_VALUE(8006, "Invalid input value");

    private final int code;
    private final String message;

    public String getFullMessage() {
        return String.format("[%d] %s", code, message);
    }

    /**
     * HTTP 상태 코드 매핑
     */
    public int getHttpStatusCode() {
        return switch (code / 1000) {
            case 1 -> switch (code) {
                case 1003 -> 404; // RESOURCE_NOT_FOUND
                case 1004 -> 401; // UNAUTHORIZED_ACCESS
                case 1005 -> 403; // FORBIDDEN_ACCESS
                case 1006 -> 409; // DUPLICATE_RESOURCE
                default -> 400;   // Bad Request
            };
            case 2 -> 401; // Authentication errors -> Unauthorized
            case 3 -> 400; // Payment errors -> Bad Request
            case 4 -> 429; // Rate limiting -> Too Many Requests
            case 5 -> 400; // Webhook errors -> Bad Request
            case 6 -> 500; // Database errors -> Internal Server Error
            case 7 -> 503; // External service errors -> Service Unavailable
            case 8 -> 400; // Validation errors -> Bad Request
            default -> 500; // Internal Server Error
        };
    }

    /**
     * 메시지 파라미터를 포함하여 포맷팅
     */
    public String getFormattedMessage(Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        return String.format(message.replace("{0}", "%s").replace("{1}", "%s").replace("{2}", "%s"), args);
    }
}