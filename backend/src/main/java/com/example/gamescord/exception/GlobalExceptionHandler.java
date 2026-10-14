package com.example.gamescord.exception;

import com.example.gamescord.payment.infrastructure.toss.TossPaymentRejectedException;
import com.example.gamescord.payment.infrastructure.toss.TossPaymentUnknownException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.FieldError;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.authentication.BadCredentialsException; // Import this

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ex.getMessage());
    }


    @ExceptionHandler(IllegalStateException.class)

    public ResponseEntity<String> handleIllegalStateException(IllegalStateException ex) {

        return ResponseEntity

                .status(HttpStatus.BAD_REQUEST)

                .body(ex.getMessage());

    }

    // 토스가 승인을 거절함: 결제가 일어나지 않았으므로 클라이언트 요청 실패로 응답
    @ExceptionHandler(TossPaymentRejectedException.class)
    public ResponseEntity<String> handleTossPaymentRejected(TossPaymentRejectedException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ex.getMessage());
    }

    // 토스 응답을 받지 못함: 결제됐을 수 있으므로 실패로 단정하지 않고, 대사가 확정할 때까지 재결제를 유도하지 않음
    @ExceptionHandler(TossPaymentUnknownException.class)
    public ResponseEntity<String> handleTossPaymentUnknown(TossPaymentUnknownException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body("결제 결과를 확인 중입니다. 잠시 후 코인 잔액을 확인해 주세요. (다시 결제하지 마세요)");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<String> handleBadCredentialsException(BadCredentialsException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED) // 401
                .body(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errors);
    }
}
