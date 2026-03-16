package com.govpay.govpay_backend.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// ── Base ──────────────────────────────────────────────────────────────────────

public class GovPayException extends RuntimeException {
    public GovPayException(String message) { super(message); }
    public GovPayException(String message, Throwable cause) { super(message, cause); }
}

// ── Auth ──────────────────────────────────────────────────────────────────────

class AuthExceptions {

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class UserAlreadyExistsException extends GovPayException {
        public UserAlreadyExistsException(String email) {
            super("User already exists with email: " + email);
        }
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public static class InvalidCredentialsException extends GovPayException {
        public InvalidCredentialsException() {
            super("Invalid email or password");
        }
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public static class InvalidTokenException extends GovPayException {
        public InvalidTokenException(String reason) {
            super("Invalid token: " + reason);
        }
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class AccountDisabledException extends GovPayException {
        public AccountDisabledException() {
            super("Account is disabled. Please contact support.");
        }
    }
}

// ── Wallet ────────────────────────────────────────────────────────────────────

class WalletExceptions {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class InsufficientBalanceException extends GovPayException {
        public InsufficientBalanceException() {
            super("Insufficient wallet balance for this transaction");
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class WalletNotFoundException extends GovPayException {
        public WalletNotFoundException(String userId) {
            super("Wallet not found for user: " + userId);
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class InvalidTransactionAmountException extends GovPayException {
        public InvalidTransactionAmountException(String reason) {
            super("Invalid transaction amount: " + reason);
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class DuplicateTransactionException extends GovPayException {
        public DuplicateTransactionException(String idempotencyKey) {
            super("Duplicate transaction detected: " + idempotencyKey);
        }
    }
}

// ── General ───────────────────────────────────────────────────────────────────

class GeneralExceptions {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class ResourceNotFoundException extends GovPayException {
        public ResourceNotFoundException(String resource, String id) {
            super(resource + " not found with id: " + id);
        }
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class AccessDeniedException extends GovPayException {
        public AccessDeniedException() {
            super("You do not have permission to perform this action");
        }
    }
}