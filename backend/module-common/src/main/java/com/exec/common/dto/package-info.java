/**
 * 공통 DTO 패키지
 * <p>
 * 이 패키지는 시스템 전체에서 사용되는 공통 데이터 전송 객체 (DTO) 들을 포함합니다.
 * <p>
 * 주요 클래스들:
 * <p>
 * - {@link com.exec.common.dto.ApiResponse} : 표준 API 응답 구조
 * - {@link com.exec.common.dto.ErrorResponse} : 에러 응답 구조
 * - {@link com.exec.common.dto.ValidationErrorResponse} : 유효성 검사 에러 응답 구조
 *
 * <h3>사용 예시:</h3>
 *
 * <h4>1. 성공 응답</h4>
 * <pre>{@code
 * @GetMapping("/users/{id}")
 * public ApiResponse<UserDto> getUser(@PathVariable Long id) {
 *     UserDto user = userService.findById(id);
 *     return ApiResponse.success("request-123", user);
 * }
 * }</pre>
 *
 * <h4>2. 에러 응답</h4>
 * <pre>{@code
 * @ExceptionHandler(BusinessException.class)
 * public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
 *     ErrorResponse error = ErrorResponse.badRequest("request-123", e.getMessage());
 *     return ResponseEntity.badRequest().body(error);
 * }
 * }</pre>
 *
 * <h4>3. 유효성 검사 에러 응답</h4>
 * <pre>{@code
 * @ExceptionHandler(MethodArgumentNotValidException.class)
 * public ResponseEntity<ValidationErrorResponse> handleValidationException(
 *         MethodArgumentNotValidException e) {
 *     List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
 *         .map(error -> FieldError.of(
 *             error.getField(),
 *             error.getRejectedValue(),
 *             error.getDefaultMessage()
 *         ))
 *         .collect(Collectors.toList());
 *
 *     ValidationErrorResponse response = ValidationErrorResponse.of(
 *         "request-123", "Validation failed", fieldErrors);
 *
 *     return ResponseEntity.badRequest().body(response);
 * }
 * }</pre>
 *
 * <h4>4. HTTP 상태별 편의 메서드 사용</h4>
 * <pre>{@code
 * // 401 Unauthorized
 * ErrorResponse unauthorized = ErrorResponse.unauthorized(requestId, "Invalid API key");
 *
 * // 403 Forbidden
 * ErrorResponse forbidden = ErrorResponse.forbidden(requestId, "Access denied");
 *
 * // 404 Not Found
 * ErrorResponse notFound = ErrorResponse.notFound(requestId, "User not found");
 *
 * // 500 Internal Server Error
 * ErrorResponse serverError = ErrorResponse.internalServerError(requestId, "Database connection failed");
 * }</pre>
 *
 * @since 1.0.0
 */
package com.exec.common.dto;