package com.kob.backend.config;

import com.alibaba.fastjson.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolationException;

/**
 * 全局异常处理（审计任务 5.1）。
 *
 * <p>将参数校验异常转为统一的 JSON 错误响应，避免不稳定的 HTTP 500（审计 6.2）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<JSONObject> handleConstraintViolation(ConstraintViolationException e) {
        JSONObject resp = new JSONObject();
        resp.put("error_message", "参数非法");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<JSONObject> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        JSONObject resp = new JSONObject();
        resp.put("error_message", "参数非法");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
    }
}
