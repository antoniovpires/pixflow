package com.pixflow.web;

import com.pixflow.account.AccountInUseException;
import com.pixflow.account.AccountNotFoundException;
import com.pixflow.account.InsufficientBalanceException;
import com.pixflow.account.InvalidAccountException;
import com.pixflow.pixkey.PixKeyAlreadyExistsException;
import com.pixflow.pixkey.PixKeyInUseException;
import com.pixflow.auth.EmailAlreadyUsedException;
import com.pixflow.auth.InvalidCredentialsException;
import com.pixflow.pixkey.PixKeyNotFoundException;
import com.pixflow.transfer.IdempotencyKeyReuseException;
import com.pixflow.transfer.InvalidTransferException;
import com.pixflow.transfer.TransferConflictException;
import com.pixflow.user.UserNotFoundException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler({AccountNotFoundException.class, PixKeyNotFoundException.class, UserNotFoundException.class})
  ProblemDetail handleNotFound(RuntimeException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler({
      InsufficientBalanceException.class,
      InvalidTransferException.class,
      InvalidAccountException.class,
      IdempotencyKeyReuseException.class})
  ProblemDetail handleUnprocessable(RuntimeException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
  }

  @ExceptionHandler({
      TransferConflictException.class,
      AccountInUseException.class,
      EmailAlreadyUsedException.class,
      PixKeyAlreadyExistsException.class,
      PixKeyInUseException.class})
  ProblemDetail handleConflict(RuntimeException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  ProblemDetail handleInvalidCredentials(InvalidCredentialsException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
    Map<String, String> errors = new LinkedHashMap<>();
    e.getBindingResult().getFieldErrors()
        .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
    problem.setProperty("errors", errors);
    return handleExceptionInternal(e, problem, headers, HttpStatus.BAD_REQUEST, request);
  }
}
