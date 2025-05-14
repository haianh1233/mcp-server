package io.condutkor.mcpserver;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class ErrorService {

    public Map<String, Object> createError(String message) {
        return createError(message, null);
    }

    public Map<String, Object> createError(String message, Exception e) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("isError", true);
        errorResponse.put("message", message + (e != null ? ": " + e.getMessage() : ""));
        
        // Add more detailed error information when an exception is available
        if (e != null) {
            errorResponse.put("exceptionType", e.getClass().getSimpleName());
            
            // Add stack trace for debugging in development environments
            if (isDebugMode()) {
                StackTraceElement[] stackTrace = e.getStackTrace();
                StringBuilder traceStr = new StringBuilder();
                for (int i = 0; i < Math.min(5, stackTrace.length); i++) {
                    traceStr.append(stackTrace[i].toString()).append("\n");
                }
                errorResponse.put("stackTrace", traceStr.toString());
            }
        }
        
        return errorResponse;
    }
    
    public Map<String, Object> createValidationError(String message, Map<String, String> validationErrors) {
        Map<String, Object> errorResponse = createError(message);
        errorResponse.put("validationErrors", validationErrors);
        return errorResponse;
    }
    
    public Map<String, Object> createAuthError(String message) {
        Map<String, Object> errorResponse = createError(message);
        errorResponse.put("errorType", "AuthenticationError");
        return errorResponse;
    }
    
    private boolean isDebugMode() {
        return System.getProperty("graphql.debug", "false").equalsIgnoreCase("true");
    }
}