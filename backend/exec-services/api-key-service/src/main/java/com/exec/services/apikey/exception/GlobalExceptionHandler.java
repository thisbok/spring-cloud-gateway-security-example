package com.exec.services.apikey.exception;

import com.exec.common.dto.ApiResponse;
import com.exec.common.exception.BusinessException;
import com.exec.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {

        log.warn("Business exception occurred: {}", e.getMessage());

        return ApiResponse.error(e.getErrorCode().getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationException(Exception e) {

        log.warn("Validation exception occurred: {}", e.getMessage());

        String errorMessage;
        if (e instanceof MethodArgumentNotValidException validException) {
            errorMessage = validException.getBindingResult().getFieldErrors().stream()
                    .map(this::formatFieldError)
                    .collect(Collectors.joining(", "));
        } else if (e instanceof BindException bindException) {
            errorMessage = bindException.getBindingResult().getFieldErrors().stream()
                    .map(this::formatFieldError)
                    .collect(Collectors.joining(", "));
        } else {
            errorMessage = "Validation failed";
        }

        return ApiResponse.error(errorMessage);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException e) {

        log.warn("Illegal argument exception occurred: {}", e.getMessage());

        return ApiResponse.error(
                ErrorCode.INVALID_REQUEST_PARAMETER.getMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleGenericException(Exception e) {

        log.error("Unexpected exception occurred", e);

        return ApiResponse.error(
                ErrorCode.INTERNAL_SERVER_ERROR.getMessage()
        );
    }

    private String formatFieldError(FieldError fieldError) {
        return String.format("%s: %s", fieldError.getField(), fieldError.getDefaultMessage());
    }
}